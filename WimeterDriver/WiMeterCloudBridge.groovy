/**
 * WiMeter Cloud Bridge (Parent)
 *
 * v4.15 - Added 'powerLevel' attribute (High/Medium/Active/Idle) for easier automation logic.
 * v4.14 - Added "Visual CSS" labels to Preferences.
 * v4.13 - Removed color selectors (hardcoded Traffic Light scheme).
 */

metadata {
    definition (name: "WiMeter Cloud Bridge", namespace: "aniva", author: "aniva", importUrl: "https://raw.githubusercontent.com/aniva/hubitat01/master/WimeterDriver/WiMeterCloudBridge.groovy", version: "4.15") {
        capability "PowerMeter" 
        capability "EnergyMeter"
        capability "Refresh"
        capability "Initialize"
        capability "Sensor"
        
        command "recreateChildDevices"
        command "resetAllData" 
        
        attribute "apiStatus", "string"
        attribute "lastUpdate", "string"
        attribute "location", "string"
        attribute "_version", "string"
        attribute "icon", "string"
        attribute "htmlIcon", "string"
        attribute "htmlTile", "string"
        
        // NEW: Semantic State for Rules
        attribute "powerLevel", "string"
        
        // PARENT ATTRIBUTES
        attribute "locationPowerRealTimeKw", "number"
        attribute "locationPowerRealTimeW", "number"
        attribute "power", "number" 
        
        attribute "locationPowerPerDayKwh", "number"
        attribute "locationPowerPerWeekKwh", "number"
        attribute "locationPowerPerMonthKwh", "number"
        attribute "locationPowerPerPeriodKwh", "number"
        
        attribute "locationCostRealTime", "number"
        attribute "locationCostPerDay", "number"
        attribute "locationCostPerWeek", "number"
        attribute "locationCostPerMonth", "number"
        attribute "locationCostPerPeriod", "number"
    }
    
    preferences {
        input "apiUrl", "text", title: "WiMeter API URL", required: true
        input "targetLocation", "text", title: "Target Location Name", required: true, description: "Matches the location attribute in API"
        input "pollInterval", "enum", title: "Polling Interval", options: ["Manual", "1 Minute", "5 Minutes", "15 Minutes", "30 Minutes"], defaultValue: "5 Minutes"
        input "debugMode", "bool", title: "Enable Debug Logging", defaultValue: false
        
        // TILE CONFIGURATION (Visual CSS Labels)
        input "headerTile", "text", title: "<b>Dashboard Tile Thresholds</b>", description: "Set the power levels (kW) that trigger status color changes.", displayDuringSetup: false, type: "paragraph", element: "paragraph"
        
        input "threshHigh", "decimal", title: "High Power <span style='background-color:#c0392b; color:white; padding:1px 4px; border-radius:3px; font-weight:bold; font-size:0.9em;'>RED</span> [kW]", defaultValue: 6.0, required: true
        input "threshMed", "decimal", title: "Medium Power <span style='background-color:#f1c40f; color:white; padding:1px 4px; border-radius:3px; font-weight:bold; font-size:0.9em; text-shadow: 0px 0px 2px black;'>YELLOW</span> [kW]", defaultValue: 3.0, required: true
        input "threshActive", "decimal", title: "Active Power <span style='background-color:#27ae60; color:white; padding:1px 4px; border-radius:3px; font-weight:bold; font-size:0.9em;'>GREEN</span> [kW]", defaultValue: 1.0, required: true
    }
}

def driverVersion() { return "4.15" }

def installed() { initialize() }

def updated() { 
    initialize()
    if(debugMode) runIn(1800, logsOff)
}

def initialize() {
    sendEvent(name: "_version", value: driverVersion())
    unschedule()
    switch(pollInterval) {
        case "1 Minute": runEvery1Minute(refresh); break
        case "5 Minutes": runEvery5Minutes(refresh); break
        case "15 Minutes": runEvery15Minutes(refresh); break
        case "30 Minutes": runEvery30Minutes(refresh); break
        case "Manual": break;
        default: runEvery5Minutes(refresh); break
    }
}

def resetAllData() {
    log.warn "Resetting all data..."
}

def recreateChildDevices() {
    log.warn "Wiping and recreating child devices..."
    getChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
    refresh()
}

def refresh() {
    if (debugMode) log.debug "Refreshing v${driverVersion()}..."

    if (!apiUrl || !targetLocation) {
        logError "Missing API URL or Target Location."
        return
    }
    
    def params = [uri: apiUrl, contentType: 'application/json', timeout: 10]

    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                sendEvent(name: "apiStatus", value: "Online")
                sendEvent(name: "lastUpdate", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
                processData(resp.data)
            } else {
                logError "API Error: ${resp.status}"
                sendEvent(name: "apiStatus", value: "Error")
            }
        }
    } catch (e) {
        logError "Connection Failed: ${e.message}"
        sendEvent(name: "apiStatus", value: "Connection Failed")
    }
}

def processData(data) {
    def listToProcess = []
    if (data instanceof List) {
        listToProcess = data
    } else if (data instanceof Map) {
        if (data.containsKey("devices") && data.devices instanceof List) {
             listToProcess = data.devices
        } else if (data.containsKey("name")) {
             listToProcess = [data]
        } else {
             listToProcess = data.values()
        }
    }

    def locationDevices = listToProcess.findAll { it.location && it.location.trim() == targetLocation.trim() }
    def groupedDevices = locationDevices.groupBy { it.name }

    groupedDevices.each { name, items ->
        def cleanItems = items.collect { new HashMap(it) }

        if (name.trim() == targetLocation.trim()) {
            updateParentState(cleanItems)
        } else {
            updateChildDevice(name, cleanItems)
        }
    }
}

def updateParentState(items) {
    sendEvent(name: "location", value: targetLocation)
    
    def firstItem = items.find { it.url }
    if (firstItem) {
        sendEvent(name: "icon", value: firstItem.url)
        sendEvent(name: "htmlIcon", value: "<img src='${firstItem.url}' style='height:40px;'>")
    }

    items.each { item ->
        def results = calculateValueAndSuffix(item) 
        results.each { res ->
            if (res.baseType) {
                def attrName = "location${res.baseType.capitalize()}${res.suffix}${res.unitSuffix}"
                sendEvent(name: attrName, value: res.value, unit: res.unit)
                if (attrName == "locationPowerRealTimeKw") {
                     sendEvent(name: "power", value: res.value, unit: "kW")
                }
            }
        }
    }

    // --- HTML TILE GENERATION & POWER LEVEL ---
    def powerVal = 0.0
    try {
        def powerItem = items.find { it.unit == "kW" || it.unit == "W" }
        if (powerItem && powerItem.reading != null) {
            def rawVal = powerItem.reading.toBigDecimal()
            if (powerItem.unit == "W") {
                powerVal = (rawVal / 1000).toFloat().round(2)
            } else {
                powerVal = rawVal.toFloat().round(2)
            }
        }
    } catch (e) {
        log.warn "Tile calculation error (defaulting to 0): ${e}"
        powerVal = 0.0
    }

    // Threshold Logic (Defaults: 1.0, 3.0, 6.0)
    def tHigh = settings.threshHigh != null ? settings.threshHigh.toBigDecimal() : 6.0
    def tMed = settings.threshMed != null ? settings.threshMed.toBigDecimal() : 3.0
    def tActive = settings.threshActive != null ? settings.threshActive.toBigDecimal() : 1.0
    
    // Hardcoded Colors
    def cRed = "#c0392b"
    def cYellow = "#f1c40f"
    def cGreen = "#27ae60"
    def cGrey = "#7f8c8d"

    def cardColor = cGrey
    def levelText = "Idle"
    
    if (powerVal >= tHigh) {
        cardColor = cRed
        levelText = "High"
    } else if (powerVal >= tMed) {
        cardColor = cYellow
        levelText = "Medium"
    } else if (powerVal >= tActive) {
        cardColor = cGreen
        levelText = "Active"
    } else {
        cardColor = cGrey
        levelText = "Idle"
    }
    
    // Send the Power Level Event
    sendEvent(name: "powerLevel", value: levelText)

    def tileHtml = """
    <div style='
        width: 95% !important; 
        height: 85% !important;
        margin-top: 5% !important;
        margin-left: 0% !important;
		margin-right: -40% !important;
        margin-bottom: 15% !important;
        background-color: ${cardColor}; 
        color: white;
        display: flex; 
        flex-direction: column; 
        align-items: center; 
        justify-content: center;
        border-radius: 5px;
    '>
        <div style='font-size:0.8rem; text-transform:uppercase; opacity:0.9; margin-bottom:0px;'>Power</div>
        <div style='font-size:1.5rem; font-weight:bold; line-height:1.1;'>${powerVal} <span style='font-size:0.6em'>kW</span></div>
    </div>
    """
    
    sendEvent(name: "htmlTile", value: tileHtml)
}

def updateChildDevice(name, items) {
    def cleanName = name.replaceAll("'s", "").replaceAll("'S", "").replaceAll("/", "").trim()
    def dni = "WIMETER_CHILD_${cleanName.replaceAll("[^a-zA-Z0-9]", "")}"
    
    def child = getChildDevice(dni)
    if (!child) {
        try {
            addChildDevice("aniva", "WiMeter Child Device", dni, [name: name, isComponent: true])
            child = getChildDevice(dni)
        } catch (e) {
            logError "Failed to create child '${name}'. Error: ${e}"
            return
        }
    }
    child.parseItems(items)
}

def calculateValueAndSuffix(item) {
    def rawVal = item.reading.toFloat()
    def rawUnit = item.unit ? item.unit.trim() : ""
    def interval = (item.interval != null) ? item.interval.toInteger() : 0
    
    def results = []

    def getSuffix = { i ->
        if (i == 0) return "RealTime"
        else if (i == 86400) return "PerDay"
        else if (i == 604800) return "PerWeek"
        else if (i >= 2419200 && i <= 2678400) return "PerMonth"
        else return "PerPeriod"
    }

    if (rawUnit == "\$" || rawUnit == '$') {
        def suffix = getSuffix(interval)
        results << [value: rawVal.round(2), unit: "\$", baseType: "cost", suffix: suffix, unitSuffix: ""]
    } 
    else if (rawUnit.contains("W") || rawUnit.contains("kW") || rawUnit.contains("Wh") || rawUnit.contains("kWh")) {
        def suffix = getSuffix(interval)
        if (interval == 0) {
            def val_kW = (rawUnit == "W" || rawUnit == "Wh") ? (rawVal / 1000).round(3) : rawVal.round(3)
            def val_W = (rawUnit == "kW" || rawUnit == "kWh") ? (rawVal * 1000).round(1) : rawVal.round(1)

            results << [value: val_kW, unit: "kW", baseType: "power", suffix: suffix, unitSuffix: "Kw"]
            results << [value: val_W, unit: "W", baseType: "power", suffix: suffix, unitSuffix: "W"]
        } else {
            def val_kWh = (rawUnit == "W" || rawUnit == "Wh") ? (rawVal / 1000).round(3) : rawVal.round(3)
            results << [value: val_kWh, unit: "kWh", baseType: "power", suffix: suffix, unitSuffix: "Kwh"]
        }
    }
    return results
}

def logsOff() { device.updateSetting("debugMode", [value:"false", type:"bool"]) }
def logError(msg) { log.error msg }