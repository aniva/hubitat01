/**
 * WiMeter Cloud Bridge (Parent)
 * v4.5 - Unit Suffixes & Clean States
 */

metadata {
    definition (name: "WiMeter Cloud Bridge", namespace: "aniva", author: "aniva", importUrl: "https://raw.githubusercontent.com/aniva/hubitat01/master/WimeterDriver/WiMeterCloudBridge.groovy", version: "4.5") {
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
        
        // Parent Attributes (House) - updated with suffixes
        attribute "location_power_real-time_kw", "number"
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

def driverVersion() { return "4.5" }

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
    // Note: We cannot easily "unset" an attribute in Hubitat without deleting the device,
    // but we can set them to null or 0 if really needed. 
    // For now, this just logs, as the user prefers "Clean" (hidden) states.
    // The best way to "Clean" the UI is to use the "recreateChildDevices" command.
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
        def result = calculateValueAndSuffix(item) 
        if (result.baseType) {
            // Updated Naming Scheme with Suffix
            def attrName = "location_${result.baseType}${result.suffix}${result.unitSuffix}"
            sendEvent(name: attrName, value: result.value, unit: result.unit)
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

def calculateValueAndSuffix(item) {
    def rawVal = item.reading.toFloat()
    def unit = item.unit ? item.unit.trim() : ""
    def interval = (item.interval != null) ? item.interval.toInteger() : 0
    def baseType = ""
    def val = 0.0
    def unitSuffix = ""

    if (unit == "\$" || unit == '$') {
        baseType = "cost"
        val = rawVal.round(2)
        unit = "\$"
        unitSuffix = "_\$"
    } else if (unit.contains("W") || unit.contains("kW") || unit.contains("Wh") || unit.contains("kWh")) {
        baseType = "power"
        if (unit == "W" || unit == "Wh") val = (rawVal / 1000).round(3)
        else val = rawVal.round(3)
        
        // Determine if it is Energy (kWh) or Power (kW) based on interval/unit
        // Logic: if Interval > 0 it is technically Energy (kWh), if 0 it is Power (kW)
        if (interval == 0) {
            unit = "kW"
            unitSuffix = "_kw"
        } else {
            unit = "kWh"
            unitSuffix = "_kwh"
        }
    }

    def suffix = "_per_period"
    if (interval == 0) suffix = "_real-time"
    else if (interval == 86400) suffix = "_per_day"
    else if (interval == 604800) suffix = "_per_week"
    else if (interval >= 2419200 && interval <= 2678400) suffix = "_per_month"

    return [value: val, unit: unit, baseType: baseType, suffix: suffix, unitSuffix: unitSuffix]
}

def logsOff() { device.updateSetting("debugMode", [value:"false", type:"bool"]) }
def logError(msg) { log.error msg }