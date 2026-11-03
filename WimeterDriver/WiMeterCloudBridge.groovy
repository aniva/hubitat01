/**
 * WiMeter Cloud Bridge (Parent)
 *
 * v4.30 - Asynchronous HTTP polling (asynchttpGet), Hubitat PowerMeter (Watts) & EnergyMeter (kWh)
 *         standard compliance, optional cost subtitle, customizable tile styling with legacy fallback,
 *         and resetAllData implementation.
 * v4.22 - UI Simplification: Switched to Static Color Legend to avoid render sync issues.
 * v4.21 - UX Improvements: Table refresh optimization.
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "4.30"

metadata {
    definition (name: "WiMeter Cloud Bridge", namespace: "aniva", author: "aniva", importUrl: "https://raw.githubusercontent.com/aniva/hubitat01/master/WimeterDriver/WiMeterCloudBridge.groovy", version: "4.30") {
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
        attribute "powerLevel", "string"
        
        // PARENT ATTRIBUTES
        attribute "power", "number" 
        attribute "energy", "number"
        attribute "locationPowerRealTimeKw", "number"
        attribute "locationPowerRealTimeW", "number"
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
        // --- ANIVA STANDARD HEADER ---
        input name: "about", type: "paragraph", element: "paragraph", title: "", description: """
        <div style='display: flex; align-items: center; justify-content: space-between; padding: 10px; border: 1px solid #e0e0e0; border-radius: 5px; background: #fafafa; margin-bottom: 10px;'>
            <div style='display: flex; align-items: center;'>
                <img src='https://raw.githubusercontent.com/aniva/hubitat01/master/WimeterDriver/images/wimeter_device.png' 
                     style='height: 60px; width: 60px; object-fit: contain; margin-right: 15px;' 
                     onerror="this.src='https://raw.githubusercontent.com/hubitat/HubitatPublic/master/examples/drivers/icons/energy-meter.png'">
                <div>
                    <div style='font-weight: bold; font-size: 1.1em; color: #333;'>WiMeter Cloud Bridge</div>
                    <div style='font-size: 0.8em; color: #888;'>Driver v${driverVersion()}</div>
                </div>
            </div>
            <div style='text-align: right; font-size: 0.8em; line-height: 1.4;'>
                <a href='https://github.com/aniva/hubitat01/tree/master/WimeterDriver' target='_blank' style='color: #0275d8; text-decoration: none;'>View GitHub</a><br>
                <a href='https://paypal.me/AndreiIvanov420' target='_blank' style='color: #0275d8; text-decoration: none;'>Support Dev</a>
            </div>
        </div>"""

        // --- API CONFIGURATION ---
        input "apiBaseUrl", "text", title: "API Base URL", defaultValue: "https://wimeter.net/v1/pubmatrix?key=", required: true
        input "apiKey", "text", title: "Public Key", description: "Found in WiMeter: Main Page -> Top Right -> Account -> Public Key", required: true
        input "targetLocation", "text", title: "Target Location Name", required: true, description: "Matches the location attribute in API"
        
        input "pollInterval", "enum", title: "Polling Interval", options: ["Manual", "1 Minute", "5 Minutes", "15 Minutes", "30 Minutes"], defaultValue: "5 Minutes", required: true
        input "debugMode", "bool", title: "Enable Debug Logging (Auto-off in 30 min)", defaultValue: false
        
        // --- STATIC LEGEND ---
        input "headerTile", "paragraph", title: "", description: """
        <div style="background-color:#f0f0f0; padding: 10px; border-radius:5px; margin-top:10px;">
            <b style="color:#333;">Dashboard Tile Legend</b>
            <div style="font-size:12px; margin-top:5px; color:#555;">The tile background color changes based on the thresholds configured below.</div>
            <table style="width:100%; font-size:12px; margin-top:8px; border-collapse:collapse;">
                <tr style="border-bottom:1px solid #ddd;"><th style="text-align:left;">State</th><th style="text-align:left;">Meaning</th><th style="text-align:left;">Color Preview</th></tr>
                <tr><td><b>High</b></td><td>Heavy Usage</td><td><span style="color:white; background-color:#c0392b; padding:2px 5px; border-radius:3px;">Red</span></td></tr>
                <tr><td><b>Medium</b></td><td>Moderate Usage</td><td><span style="color:black; background-color:#f1c40f; padding:2px 5px; border-radius:3px;">Yellow</span></td></tr>
                <tr><td><b>Active</b></td><td>Normal Usage</td><td><span style="color:white; background-color:#27ae60; padding:2px 5px; border-radius:3px;">Green</span></td></tr>
                <tr><td><b>Idle</b></td><td>Low Usage</td><td><span style="color:white; background-color:#7f8c8d; padding:2px 5px; border-radius:3px;">Grey</span></td></tr>
                <tr><td><b>Offline</b></td><td>No Connection</td><td><span style="color:white; background-color:#000000; padding:2px 5px; border-radius:3px;">Black</span></td></tr>
            </table>
        </div>
        """
        
        input "threshActive", "decimal", title: "Active Power Threshold [kW]", defaultValue: 1.0, required: true
        input "threshMed", "decimal", title: "Medium Power Threshold [kW]", defaultValue: 3.0, required: true
        input "threshHigh", "decimal", title: "High Power Threshold [kW]", defaultValue: 6.0, required: true

        // --- TILE APPEARANCE & LAYOUT (REVERSIBLE) ---
        input "tileLayout", "enum", title: "Dashboard Tile Layout Style", options: ["legacy": "Custom Dashboard Layout (Current)", "centered": "Clean Box Layout"], defaultValue: "legacy", required: false
        input "showCostOnTile", "bool", title: "Show Daily Cost on Tile (if available)", defaultValue: false
    }
}

def driverVersion() { return DRIVER_VERSION }

def installed() { 
    initialize()
}

def updated() { 
    initialize()
}

def initialize() {
    log.warn "${device.displayName} initialized (Driver v${driverVersion()})"
    sendEvent(name: "_version", value: driverVersion())
    unschedule()
    
    if(debugMode) runIn(1800, logsOff)
    
    childDevices.each { child ->
        if (child.hasCommand("updateVersion")) child.updateVersion(driverVersion())
    }

    def interval = pollInterval ?: "5 Minutes"
    switch(interval) {
        case "1 Minute": runEvery1Minute(refresh); break
        case "5 Minutes": runEvery5Minutes(refresh); break
        case "15 Minutes": runEvery15Minutes(refresh); break
        case "30 Minutes": runEvery30Minutes(refresh); break
        case "Manual": break;
        default: runEvery5Minutes(refresh); break
    }
    
    runIn(2, refresh)
}

def resetAllData() {
    log.warn "Resetting all data on ${device.displayName}..."
    sendEvent(name: "power", value: 0, unit: "W")
    sendEvent(name: "locationPowerRealTimeW", value: 0, unit: "W")
    sendEvent(name: "locationPowerRealTimeKw", value: 0, unit: "kW")
    sendEvent(name: "powerLevel", value: "Idle")
    sendEvent(name: "apiStatus", value: "Offline")
    state.clear()
    updateHtmlTile(0, 0, false)
    
    childDevices.each { child ->
        if (child.hasCommand("resetData")) child.resetData()
    }
}

def recreateChildDevices() {
    log.warn "Wiping and recreating child devices..."
    getChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
    refresh()
}

def refresh() {
    if (debugMode) log.debug "Refreshing v${driverVersion()} (async)..."

    if (!apiBaseUrl || !apiKey || !targetLocation) {
        logError "Missing API Configuration."
        handleOffline("Missing Config")
        return
    }
    
    def fullUrl = "${apiBaseUrl}${apiKey}"
    def params = [
        uri: fullUrl,
        contentType: 'application/json',
        timeout: 10,
        ignoreSSLIssues: true
    ]

    try {
        asynchttpGet("httpCallback", params)
    } catch (Exception e) {
        logError "asynchttpGet Failed to schedule: ${e.message}"
        handleOffline("Schedule Failed")
    }
}

void httpCallback(response, data) {
    if (debugMode) log.debug "Async HTTP callback received (status: ${response?.status})"
    
    if (!response || response.hasError()) {
        def status = response?.status ?: "Unknown"
        def errMsg = response?.errorMessage ?: "HTTP Request Error"
        logError "API HTTP Error: ${status} - ${errMsg}"
        handleOffline("HTTP Error: ${status}")
        return
    }

    try {
        if (response.data) {
            def parsed = parseJson(response.data)
            if (parsed instanceof Map && parsed.containsKey("ret") && parsed.ret == 0) {
                logError "API returned 'ret:0'. Key may be invalid."
                handleOffline("Invalid API Key")
                return
            }
            sendEvent(name: "apiStatus", value: "Online")
            sendEvent(name: "lastUpdate", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
            processData(parsed)
        } else {
            logError "API returned empty data."
            handleOffline("Empty Response")
        }
    } catch (Exception e) {
        logError "Parsing Response Failed: ${e.message}"
        handleOffline("Invalid Response/Data")
    }
}

def handleOffline(String reason) {
    if (debugMode) log.warn "Setting device OFFLINE. Reason: ${reason}"
    
    sendEvent(name: "apiStatus", value: "Offline")
    sendEvent(name: "powerLevel", value: "Offline")
    sendEvent(name: "power", value: 0, unit: "W") 
    updateHtmlTile(0, 0, true)
    
    childDevices.each { child ->
        if (child.hasCommand("setOffline")) child.setOffline()
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
                
                // Hubitat PowerMeter capability standard: Watts (W)
                if (attrName == "locationPowerRealTimeW") {
                     sendEvent(name: "power", value: res.value, unit: "W")
                }
                
                // Hubitat EnergyMeter capability standard: kWh
                if (attrName == "locationPowerPerDayKwh") {
                     sendEvent(name: "energy", value: res.value, unit: "kWh")
                }
            }
        }
    }

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
        powerVal = 0.0
    }

    def costVal = 0.0
    try {
        def costItem = items.find { (it.unit == "\$" || it.unit == '$') && it.interval == 86400 }
        if (costItem && costItem.reading != null) {
            costVal = costItem.reading.toBigDecimal().round(2)
        }
    } catch (e) {
        costVal = 0.0
    }

    updateHtmlTile(powerVal, costVal, false)
}

def updateChildDevice(name, items) {
    def cleanName = name.replaceAll("'s", "").replaceAll("'S", "").replaceAll("/", "").trim()
    def dni = "WIMETER_CHILD_${cleanName.replaceAll("[^a-zA-Z0-9]", "")}"
    
    def child = getChildDevice(dni)
    if (!child) {
        try {
            addChildDevice("aniva", "WiMeter Child Device", dni, [name: name, isComponent: true])
            child = getChildDevice(dni)
            child.updateVersion(driverVersion())
        } catch (e) {
            logError "Failed to create child '${name}'. Error: ${e}"
            return
        }
    }
    child.parseItems(items)
}

def calculateValueAndSuffix(item) {
    if (item.reading == null) return []

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

void updateHtmlTile(powerValKw, costVal, boolean isOffline) {
    def tHigh = settings?.threshHigh != null ? settings.threshHigh.toBigDecimal() : 6.0
    def tMed = settings?.threshMed != null ? settings.threshMed.toBigDecimal() : 3.0
    def tActive = settings?.threshActive != null ? settings.threshActive.toBigDecimal() : 1.0

    String cRed = "#c0392b"
    String cYellow = "#f1c40f"
    String cGreen = "#27ae60"
    String cGrey = "#7f8c8d"
    String cBlack = "#000000"
    
    def cardColor = cGrey
    def levelText = "Idle"
    
    String topLabel = "Power"
    String mainValue = "${powerValKw}"
    String unitSuffix = "kW"

    if (isOffline) {
        cardColor = cBlack
        levelText = "Offline"
        topLabel = "Status"
        mainValue = "OFFLINE"
        unitSuffix = ""
    } else {
        if (powerValKw >= tHigh) {
            cardColor = cRed
            levelText = "High"
        } else if (powerValKw >= tMed) {
            cardColor = cYellow
            levelText = "Medium"
        } else if (powerValKw >= tActive) {
            cardColor = cGreen
            levelText = "Active"
        }
    }
    
    sendEvent(name: "powerLevel", value: levelText)
    
    boolean useCentered = (settings?.tileLayout == "centered")
    boolean showCost = (settings?.showCostOnTile == true) && (costVal != null && costVal > 0)
    String costHtml = showCost ? "<div style='font-size:0.65rem; opacity:0.85; margin-top:2px;'>\$${costVal}/day</div>" : ""

    String html = ""
    if (useCentered) {
        html = """
        <div style='
            box-sizing: border-box;
            width: 100% !important; 
            height: 100% !important;
            padding: 6px;
            background-color: ${cardColor}; 
            color: white;
            display: flex; 
            flex-direction: column; 
            align-items: center; 
            justify-content: center;
            border-radius: 6px;
            text-align: center;
        '>
            <div style='font-size:0.75rem; text-transform:uppercase; opacity:0.9;'>${topLabel}</div>
            <div style='font-size:1.4rem; font-weight:bold; line-height:1.1;'>${mainValue} <span style='font-size:0.6em'>${unitSuffix}</span></div>
            ${costHtml}
        </div>
        """
    } else {
        // EXACT Legacy Tuned Margins (preserved for Dashboard compatibility)
        html = """
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
            <div style='font-size:0.8rem; text-transform:uppercase; opacity:0.9; margin-bottom:0px;'>${topLabel}</div>
            <div style='font-size:1.5rem; font-weight:bold; line-height:1.1;'>${mainValue} <span style='font-size:0.6em'>${unitSuffix}</span></div>
            ${costHtml}
        </div>
        """
    }
    
    sendEvent(name: "htmlTile", value: html)
}

def logsOff() { device.updateSetting("debugMode", [value:"false", type:"bool"]) }
def logError(msg) { log.error msg }
