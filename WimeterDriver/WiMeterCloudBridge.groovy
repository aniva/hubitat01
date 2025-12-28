/**
 * WiMeter Cloud Bridge (Parent)
 * v4.6 - Added Watt Support (_w)
 */

metadata {
    definition (name: "WiMeter Cloud Bridge", namespace: "aniva", author: "aniva", importUrl: "https://raw.githubusercontent.com/aniva/hubitat01/master/WimeterDriver/WiMeterCloudBridge.groovy", version: "4.6") {
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
        
        // Parent Attributes (House)
        attribute "location_power_real-time_kw", "number"
        attribute "location_power_real-time_w", "number" // NEW
        
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

def driverVersion() { return "4.6" }

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
        // Logic changed: calculateValueAndSuffix now returns a LIST of results
        // to support both kW and W for the same item.
        results.each { res ->
            if (res.baseType) {
                def attrName = "location_${res.baseType}${res.suffix}${res.unitSuffix}"
                sendEvent(name: attrName, value: res.value, unit: res.unit)
            }
        }
    }
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

/**
 * UPDATED LOGIC: Returns a List of Maps to support dual outputs (kW and W)
 */
def calculateValueAndSuffix(item) {
    def rawVal = item.reading.toFloat()
    def rawUnit = item.unit ? item.unit.trim() : ""
    def interval = (item.interval != null) ? item.interval.toInteger() : 0
    
    def results = []

    // 1. COST Logic
    if (rawUnit == "\$" || rawUnit == '$') {
        def suffix = getIntervalSuffix(interval)
        results << [value: rawVal.round(2), unit: "\$", baseType: "cost", suffix: suffix, unitSuffix: "_\$"]
    } 
    // 2. POWER/ENERGY Logic
    else if (rawUnit.contains("W") || rawUnit.contains("kW") || rawUnit.contains("Wh") || rawUnit.contains("kWh")) {
        
        def suffix = getIntervalSuffix(interval)
        
        if (interval == 0) {
            // REAL-TIME: Generate BOTH kW and W
            
            // Calculate kW Value
            def val_kW = 0.0
            if (rawUnit == "W" || rawUnit == "Wh") val_kW = (rawVal / 1000).round(3)
            else val_kW = rawVal.round(3)
            
            // Calculate W Value
            def val_W = 0.0
            if (rawUnit == "kW" || rawUnit == "kWh") val_W = (rawVal * 1000).round(1)
            else val_W = rawVal.round(1)

            // Add kW entry
            results << [value: val_kW, unit: "kW", baseType: "power", suffix: suffix, unitSuffix: "_kw"]
            // Add W entry
            results << [value: val_W, unit: "W", baseType: "power", suffix: suffix, unitSuffix: "_w"]
            
        } else {
            // INTERVAL (Energy): Only kWh (as before)
            def val_kWh = 0.0
            if (rawUnit == "W" || rawUnit == "Wh") val_kWh = (rawVal / 1000).round(3)
            else val_kWh = rawVal.round(3)
            
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