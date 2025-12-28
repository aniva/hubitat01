/**
 * WiMeter Cloud Bridge (Single Location - Clean Raw Names)
 * v2.5 - Fixed A/C Handling (Concatenate slashes)
 */

metadata {
    definition (name: "WiMeter Cloud Bridge", namespace: "aniva", author: "aniva", importUrl: "https://raw.githubusercontent.com/aniva/hubitat01/master/WimeterDriver/WiMeterCloudBridge.groovy", version: "2.5") {
        capability "Refresh"
        capability "Sensor"
        
        attribute "lastUpdate", "string"
        attribute "apiStatus", "string"
        attribute "location", "string"
        
        command "clearAllStates"
    }
    
    preferences {
        input "apiUrl", "text", title: "WiMeter API URL", required: true
        input "targetLocation", "text", title: "Target Location Name", required: true, description: "e.g. Andrei's House"
        input "pollInterval", "enum", title: "Polling Interval", options: ["Manual", "1 Minute", "5 Minutes", "15 Minutes", "30 Minutes"], defaultValue: "5 Minutes"
        input "debugMode", "bool", title: "Enable Debug Logging", defaultValue: false
    }
}

def driverVersion() { return "2.5" }

def installed() { initialize() }

def updated() { 
    initialize()
    if(debugMode) runIn(1800, logsOff)
}

def initialize() {
    unschedule()
    switch(pollInterval) {
        case "1 Minute": runEvery1Minute(refresh); break
        case "5 Minutes": runEvery5Minutes(refresh); break
        case "15 Minutes": runEvery15Minutes(refresh); break
        case "30 Minutes": runEvery30Minutes(refresh); break
    }
}

def clearAllStates() {
    log.warn "Clearing all current states..."
    device.currentStates.each { 
        device.deleteCurrentState(it.name) 
    }
    log.info "All states cleared."
}

def refresh() {
    log.info "WiMeter Driver v${driverVersion()} | Refresh initiated at: ${new Date()}"

    if (!apiUrl || !targetLocation) {
        logError "Missing API URL or Target Location."
        return
    }
    
    if (debugMode) log.debug "Polling WiMeter API..."
    
    def params = [
        uri: apiUrl,
        contentType: 'application/json',
        timeout: 10
    ]

    try {
        httpGet(params) { resp ->
            if (resp.data && resp.data.ret == 1) {
                sendEvent(name: "apiStatus", value: "Online")
                sendEvent(name: "lastUpdate", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
                processLocationData(resp.data.devices)
            } else {
                logError "API returned error: ${resp.data}"
                sendEvent(name: "apiStatus", value: "Error")
            }
        }
    } catch (e) {
        logError "Failed to poll WiMeter: ${e.message}"
        sendEvent(name: "apiStatus", value: "Connection Failed")
    }
}

def processLocationData(devices) {
    // Set Main Location
    sendEvent(name: "location", value: targetLocation, isStateChange: true)

    devices.each { item ->
        // Filter by Location
        if (item.location.trim() != targetLocation.trim()) return

        // 1. Calculate Values
        def result = calculateValue(item)
        
        // 2. Format Name
        def nameStr = item.name.trim()
        
        // Remove 's (Specific request: "Andrei's" -> "Andrei")
        nameStr = nameStr.replaceAll("'s", "")
        nameStr = nameStr.replaceAll("'S", "")
        
        // NEW: Remove forward slashes so they concatenate ("A/C" -> "AC")
        nameStr = nameStr.replaceAll("/", "")

        // Determine suffix (cost/kw/kwh)
        def safeUnit = result.unit == "\$" ? "cost" : result.unit
        
        // Construct Raw Name: lower case, spaces to underscores, remove special chars
        def rawName = "${nameStr}_${safeUnit}"
                        .toLowerCase()
                        .replaceAll("[^a-z0-9]", "_") // Replace non-alphanumeric with _
                        .replaceAll("_+", "_")         // Remove duplicate underscores (e.g. __)
                        .replaceAll("_+\$", "")        // Remove trailing underscores

        // 3. Send Event (Only ONE event per metric to avoid duplicates)
        if (debugMode) log.debug "Setting: '${rawName}' = ${result.value}"
        
        sendEvent(name: rawName, value: result.value, unit: result.unit, isStateChange: true)
    }
}

def calculateValue(item) {
    def rawVal = item.reading.toFloat()
    def val = 0.0
    def unitStr = item.unit
    
    if (item.unit == "kW") {
        val = rawVal.round(3) 
        unitStr = "kW"
    } 
    else if (item.unit == "W") {
        val = (rawVal / 1000).round(3) 
        unitStr = "kW"
    }
    else if (item.unit == "Wh") {
        val = (rawVal / 1000).round(3) 
        unitStr = "kWh"
    }
    else if (item.unit == "kWh") {
        val = rawVal.round(3)
        unitStr = "kWh"
    }
    else if (item.unit == "\$") {
        val = rawVal.round(2)
        unitStr = "\$"
    }
    
    return [value: val, unit: unitStr]
}

def logsOff() {
    device.updateSetting("debugMode", [value:"false", type:"bool"])
    log.info "Debug logging disabled automatically."
}

def logError(msg) {
    log.error msg
}