/**
 * WiMeter Cloud Bridge (Parent)
 *
 * v4.11 - Refactored state variables to standard camelCase (e.g., locationPowerRealTimeKw) for consistency.
 * v4.10 - Fixed 'html_tile' metadata and stabilized power calculation.
 * v4.9 - Added 'PowerMeter' capability for correct icons and standard app integration.
 * v4.8 - Implemented "Safe Mode" HTML tile generation via 'apiStatus'.
 * v4.7 - Added 'power' attribute bridge to support standard dashboard color templates.
 */

metadata {
    definition (name: "WiMeter Cloud Bridge", namespace: "aniva", author: "aniva", importUrl: "https://raw.githubusercontent.com/aniva/hubitat01/master/WimeterDriver/WiMeterCloudBridge.groovy", version: "4.11") {
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
        attribute "htmlIcon", "string" // CamelCase update
        
        // Officially defined so Dashboard can see it
        attribute "htmlTile", "string" // CamelCase update (formerly html_tile)
        
        // PARENT ATTRIBUTES (CamelCase Refactor)
        attribute "locationPowerRealTimeKw", "number"
        attribute "locationPowerRealTimeW", "number"
        
        // DASHBOARD BRIDGE
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
        input "targetLocation", "text", title: "Target Location Name", required: true, description: "Matches the location attribute in API, e.g. Andrei's House"
        input "pollInterval", "enum", title: "Polling Interval", options: ["Manual", "1 Minute", "5 Minutes", "15 Minutes", "30 Minutes"], defaultValue: "5 Minutes"
        input "debugMode", "bool", title: "Enable Debug Logging", defaultValue: false
    }
}

def driverVersion() { return "4.11" }

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
    // Implementation can be added if needed to clear states
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
                // Construct CamelCase Attribute Name: location + BaseType + Suffix + UnitSuffix
                // e.g., location + Power + RealTime + Kw -> locationPowerRealTimeKw
                def attrName = "location${res.baseType.capitalize()}${res.suffix}${res.unitSuffix}"
                
                sendEvent(name: attrName, value: res.value, unit: res.unit)
                
                if (attrName == "locationPowerRealTimeKw") {
                     sendEvent(name: "power", value: res.value, unit: "kW")
                }
            }
        }
    }

    // --- HTML TILE GENERATION ---
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

    def cardColor = "#7f8c8d" // Grey
    if (powerVal >= 6.0) cardColor = "#c0392b" // Red
    else if (powerVal >= 3.0) cardColor = "#f1c40f" // Yellow
    else if (powerVal >= 1.0) cardColor = "#27ae60" // Green

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

// Shared logic for calculating values and CamelCase suffixes
def calculateValueAndSuffix(item) {
    def rawVal = item.reading.toFloat()
    def rawUnit = item.unit ? item.unit.trim() : ""
    def interval = (item.interval != null) ? item.interval.toInteger() : 0
    
    def results = []

    // Helper to get CamelCase suffix based on interval
    def getSuffix = { i ->
        if (i == 0) return "RealTime"
        else if (i == 86400) return "PerDay"
        else if (i == 604800) return "PerWeek"
        else if (i >= 2419200 && i <= 2678400) return "PerMonth"
        else return "PerPeriod"
    }

    if (rawUnit == "\$" || rawUnit == '$') {
        def suffix = getSuffix(interval)
        // baseType: cost, unitSuffix: "" (empty for cleaner variable like costRealTime)
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