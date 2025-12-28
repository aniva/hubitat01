/**
 * WiMeter Cloud Bridge (Parent)
 *
 * v4.9 - Added 'PowerMeter' capability for correct icons and standard app integration.
 * v4.8 - Implemented "Safe Mode" HTML tile generation via 'apiStatus' for "Live Status" dashboard cards.
 * v4.7 - Added 'power' attribute bridge to support standard dashboard color templates.
 */

metadata {
    definition (name: "WiMeter Cloud Bridge", namespace: "aniva", author: "aniva", importUrl: "https://raw.githubusercontent.com/aniva/hubitat01/master/WimeterDriver/WiMeterCloudBridge.groovy", version: "4.7") {
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
        attribute "html_icon", "string"
        
        // PARENT ATTRIBUTES
        attribute "location_power_real-time_kw", "number"
        attribute "location_power_real-time_w", "number"
        
        // DASHBOARD BRIDGE (Standard Power Attribute for Colors)
        attribute "power", "number" 
        
        attribute "location_power_per_day_kwh", "number"
        attribute "location_power_per_week_kwh", "number"
        attribute "location_power_per_month_kwh", "number"
        attribute "location_power_per_period_kwh", "number"
        
        attribute "location_cost_real-time_\$", "number"
        attribute "location_cost_per_day_\$", "number"
        attribute "location_cost_per_week_\$", "number"
        attribute "location_cost_per_month_\$", "number"
        attribute "location_cost_per_period_\$", "number"
    }
    
    preferences {
        input "apiUrl", "text", title: "WiMeter API URL", required: true
        input "targetLocation", "text", title: "Target Location Name", required: true, description: "this is content of location attribute, e.g. Andrei's House"
        input "pollInterval", "enum", title: "Polling Interval", options: ["Manual", "1 Minute", "5 Minutes", "15 Minutes", "30 Minutes"], defaultValue: "5 Minutes"
        input "debugMode", "bool", title: "Enable Debug Logging", defaultValue: false
    }
}

def driverVersion() { return "4.9" }

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
        sendEvent(name: "html_icon", value: "<img src='${firstItem.url}' style='height:40px;'>")
    }

    items.each { item ->
        def results = calculateValueAndSuffix(item) 
        results.each { res ->
            if (res.baseType) {
                def attrName = "location_${res.baseType}${res.suffix}${res.unitSuffix}"
                sendEvent(name: attrName, value: res.value, unit: res.unit)
                
                // DASHBOARD BRIDGE: Mirror to 'power' for templates
                if (attrName == "location_power_real-time_kw") {
                     sendEvent(name: "power", value: res.value, unit: "kW")
                }
            }
        }
    }

    // 2. DASHBOARD TILE GENERATION (HTML)
    def powerVal = items.find { it.unit == "kW" || it.unit == "W" }?.reading?.toFloat() ?: 0
    // Normalize to kW for color logic
    if (items.find { it.unit == "W" }) powerVal = powerVal / 1000

    def cardColor = "#7f8c8d" // Grey (< 1kW)
    if (powerVal >= 6.0) cardColor = "#c0392b" // Red
    else if (powerVal >= 3.0) cardColor = "#f1c40f" // Yellow
    else if (powerVal >= 1.0) cardColor = "#27ae60" // Green

    /* * DASHBOARD BRIDGE: 'apiStatus' Hijack
     * We use the standard 'apiStatus' attribute to carry this HTML payload because
     * custom attributes (e.g., 'html_tile') often fail to appear in the Hubitat
     * Dashboard "Attribute" dropdown list due to caching bugs. 'apiStatus' is 
     * always available and renders the HTML correctly.
     */
    def tileHtml = """
    <div style='
        width: 100%; 
        height: 100%;
        background-color: ${cardColor}; 
        color: white;
        display: flex; 
        flex-direction: column; 
        align-items: center; 
        justify-content: center;
        border-radius: 4px;
    '>
        <div style='font-size:0.75rem; text-transform:uppercase; opacity:0.9; margin-bottom:0px;'>House Pwr</div>
        <div style='font-size:1.4rem; font-weight:bold; line-height:1.1;'>${powerVal} <span style='font-size:0.6em'>kW</span></div>
    </div>
    """
    
    sendEvent(name: "apiStatus", value: tileHtml)
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

    if (rawUnit == "\$" || rawUnit == '$') {
        def suffix = getIntervalSuffix(interval)
        results << [value: rawVal.round(2), unit: "\$", baseType: "cost", suffix: suffix, unitSuffix: "_\$"]
    } 
    else if (rawUnit.contains("W") || rawUnit.contains("kW") || rawUnit.contains("Wh") || rawUnit.contains("kWh")) {
        def suffix = getIntervalSuffix(interval)
        if (interval == 0) {
            def val_kW = (rawUnit == "W" || rawUnit == "Wh") ? (rawVal / 1000).round(3) : rawVal.round(3)
            def val_W = (rawUnit == "kW" || rawUnit == "kWh") ? (rawVal * 1000).round(1) : rawVal.round(1)

            results << [value: val_kW, unit: "kW", baseType: "power", suffix: suffix, unitSuffix: "_kw"]
            results << [value: val_W, unit: "W", baseType: "power", suffix: suffix, unitSuffix: "_w"]
        } else {
            def val_kWh = (rawUnit == "W" || rawUnit == "Wh") ? (rawVal / 1000).round(3) : rawVal.round(3)
            results << [value: val_kWh, unit: "kWh", baseType: "power", suffix: suffix, unitSuffix: "_kwh"]
        }
    }
    return results
}

def getIntervalSuffix(interval) {
    if (interval == 0) return "_real-time"
    else if (interval == 86400) return "_per_day"
    else if (interval == 604800) return "_per_week"
    else if (interval >= 2419200 && interval <= 2678400) return "_per_month"
    else return "_per_period"
}

def logsOff() { device.updateSetting("debugMode", [value:"false", type:"bool"]) }
def logError(msg) { log.error msg }