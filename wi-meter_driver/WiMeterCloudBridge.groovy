/**
 * WiMeter Cloud Bridge
 * Author: Gemini (Hubitat Architect Persona)
 * * Description: Polls WiMeter JSON, creates child devices, and converts units to Hubitat Standards.
 * - kW/W -> converted to Watts (power)
 * - kWh/Wh -> converted to kWh (energy)
 * - $ -> stored as 'cost' attribute
 */

metadata {
    definition (name: "WiMeter Cloud Bridge", namespace: "user", author: "Gemini") {
        capability "Refresh"
        capability "Sensor"
        
        attribute "lastUpdate", "string"
        attribute "apiStatus", "string"
    }

    preferences {
        input "apiUrl", "text", title: "WiMeter API URL", required: true, description: "Paste the full pubmatrix URL here"
        input "pollInterval", "enum", title: "Polling Interval", options: ["Manual", "1 Minute", "5 Minutes", "15 Minutes", "30 Minutes"], defaultValue: "5 Minutes"
        input "debugMode", "bool", title: "Enable Debug Logging", defaultValue: false
    }
}

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

def refresh() {
    if (!apiUrl) {
        logError "API URL is missing in preferences."
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
                processData(resp.data.devices)
            } else {
                logError "API returned error or invalid format: ${resp.data}"
                sendEvent(name: "apiStatus", value: "Error")
            }
        }
    } catch (e) {
        logError "Failed to poll WiMeter: ${e.message}"
        sendEvent(name: "apiStatus", value: "Connection Failed")
    }
}

def processData(devices) {
    devices.each { item ->
        // Create a unique Device Network ID (DNI) based on name and unit type
        // e.g., WIMETER_Range_kitchen_Energy
        def cleanName = item.name.replaceAll("[^a-zA-Z0-9]", "")
        def type = determineType(item.unit)
        def dni = "WIMETER_${cleanName}_${type}"
        
        def child = getChildDevice(dni)
        
        // 1. Create Child if missing
        if (!child) {
            if (debugMode) log.debug "Creating child device: ${dni}"
            // Use 'Generic Component' drivers which are built-in to Hubitat
            String driverName = "Generic Component Omni Sensor" // Fallback
            if (type == "Power") driverName = "Generic Component Power Meter"
            if (type == "Energy") driverName = "Generic Component Energy Meter"
            
            try {
                addChildDevice("hubitat", driverName, dni, [name: "WiMeter - ${item.name} (${type})", isComponent: true])
                child = getChildDevice(dni)
            } catch (e) {
                logError "Could not create child device. Ensure 'Generic Component' drivers are enabled. Error: ${e}"
            }
        }
        
        // 2. Update Child
        if (child) {
            updateChildDevice(child, item, type)
        }
    }
}

def determineType(unit) {
    if (unit == "kW" || unit == "W") return "Power"
    if (unit == "kWh" || unit == "Wh") return "Energy"
    if (unit == "\$") return "Cost"
    return "Sensor"
}

def updateChildDevice(child, item, type) {
    def value = item.reading.toFloat()
    
    // Convert units to Hubitat Standards
    if (item.unit == "kW") {
        // Hubitat expects Watts for 'power'
        value = value * 1000
        child.parse([[name: "power", value: value, unit: "W", descriptionText: "${child.displayName} power is ${value} W"]])
    } 
    else if (item.unit == "W") {
        child.parse([[name: "power", value: value, unit: "W", descriptionText: "${child.displayName} power is ${value} W"]])
    }
    else if (item.unit == "Wh") {
        // Hubitat expects kWh for 'energy'
        value = value / 1000
        child.parse([[name: "energy", value: value, unit: "kWh", descriptionText: "${child.displayName} energy is ${value} kWh"]])
    }
    else if (item.unit == "kWh") {
        child.parse([[name: "energy", value: value, unit: "kWh", descriptionText: "${child.displayName} energy is ${value} kWh"]])
    }
    else {
        // Generic fallback for Cost ($) or others
        child.parse([[name: "variable", value: value, unit: item.unit, descriptionText: "${child.displayName} is ${value} ${item.unit}"]])
    }
}

def logsOff() {
    device.updateSetting("debugMode", [value:"false", type:"bool"])
    log.info "Debug logging disabled automatically."
}

def logError(msg) {
    log.error msg
}