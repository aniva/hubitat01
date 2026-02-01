/**
 * WiMeter Cloud Bridge (Parent)
 *
 * v4.16 - Added "Offline" state (Black Tile) & Aniva Header.
 * v4.15 - Added 'powerLevel' attribute, (High/Medium/Active/Idle) for easier automation logic.
 * v4.14 - Added "Visual CSS" labels to Preferences.
 * v4.13 - Removed color selectors (hardcoded Traffic Light scheme).
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "4.16"

metadata {
    definition (name: "WiMeter Cloud Bridge", namespace: "aniva", author: "aniva", importUrl: "https://raw.githubusercontent.com/aniva/hubitat01/master/WimeterDriver/WiMeterCloudBridge.groovy", version: "4.16") {
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
        
        // Semantic State
        attribute "powerLevel", "string" // High, Medium, Active, Idle, Offline
        
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

        input "username", "text", title: "WiMeter Username", required: true
        input "password", "password", title: "WiMeter Password", required: true
        input "pollInterval", "enum", title: "Poll Interval", options: ["1 min", "5 min", "15 min", "30 min", "1 hour"], defaultValue: "5 min", required: true
        
        input "headerTile", "paragraph", title: "", description: """
        <div style="background-color:#f0f0f0; padding: 10px; border-radius:5px; margin-top:10px;">
            <b style="color:#333;">Dashboard Tile Logic (Location)</b>
            <table style="width:100%; font-size:12px; margin-top:5px; border-collapse:collapse;">
                <tr style="border-bottom:1px solid #ddd;"><th style="text-align:left;">State</th><th style="text-align:left;">Threshold</th><th style="text-align:left;">Color</th></tr>
                <tr><td><b>High</b></td><td>> 6.0 kW</td><td><span style="color:white; background-color:#B71C1C; padding:2px 5px; border-radius:3px;">Red</span></td></tr>
                <tr><td><b>Medium</b></td><td>> 3.0 kW</td><td><span style="color:black; background-color:#FFD600; padding:2px 5px; border-radius:3px;">Yellow</span></td></tr>
                <tr><td><b>Active</b></td><td>> 1.0 kW</td><td><span style="color:white; background-color:#2E7D32; padding:2px 5px; border-radius:3px;">Green</span></td></tr>
                <tr><td><b>Idle</b></td><td>< 1.0 kW</td><td><span style="color:white; background-color:#424242; padding:2px 5px; border-radius:3px;">Grey</span></td></tr>
                <tr><td><b>Offline</b></td><td>No Data</td><td><span style="color:white; background-color:#000000; padding:2px 5px; border-radius:3px;">Black</span></td></tr>
            </table>
        </div>
        """
        
        input "tileThresholdActive", "decimal", title: "<span style='color:#2E7D32; font-weight:bold;'>Active Threshold (kW)</span>", description: "Default: 1.0", defaultValue: 1.0
        input "tileThresholdMed", "decimal", title: "<span style='color:#F9A825; font-weight:bold;'>Medium Threshold (kW)</span>", description: "Default: 3.0", defaultValue: 3.0
        input "tileThresholdHigh", "decimal", title: "<span style='color:#B71C1C; font-weight:bold;'>High Threshold (kW)</span>", description: "Default: 6.0", defaultValue: 6.0
        
        input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: true
    }
}

def driverVersion() { return DRIVER_VERSION }

def installed() {
    log.info "Installing..."
    initialize()
}

def updated() {
    log.info "Updated..."
    initialize()
}

def initialize() {
    sendEvent(name: "_version", value: driverVersion())
    unschedule()
    
    // Set Version on Children too
    childDevices.each { child ->
        child.updateVersion(driverVersion())
    }

    if (pollInterval) {
        def minutes = pollInterval.split(" ")[0].toInteger()
        if (pollInterval.contains("hour")) minutes = 60
        
        if (minutes == 1) runEvery1Minute(refresh)
        else if (minutes == 5) runEvery5Minutes(refresh)
        else if (minutes == 15) runEvery15Minutes(refresh)
        else if (minutes == 30) runEvery30Minutes(refresh)
        else runEvery1Hour(refresh)
    } else {
        runEvery5Minutes(refresh)
    }
}

def refresh() {
    if (logEnable) log.debug "Polling WiMeter API..."
    
    def params = [
        uri: "https://wimeter.net/api/v1/get_data", 
        body: [
            username: username,
            password: password
        ],
        requestContentType: "application/json",
        contentType: "application/json",
        timeout: 20 // 20s timeout
    ]

    try {
        httpPost(params) { response ->
            if (response.status == 200) {
                if (response.data.ret == 1) {
                    sendEvent(name: "apiStatus", value: "Online")
                    sendEvent(name: "lastUpdate", value: new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone))
                    parseData(response.data.devices)
                } else {
                    log.error "API Error: ${response.data.msg}"
                    handleOffline("API Error: " + response.data.msg)
                }
            } else {
                log.error "HTTP Error: ${response.status}"
                handleOffline("HTTP Error: " + response.status)
            }
        }
    } catch (e) {
        log.error "Connection Failed: ${e}"
        handleOffline("Connection Failed")
    }
}

// FORCE OFFLINE STATE
def handleOffline(String reason) {
    sendEvent(name: "apiStatus", value: "Offline")
    sendEvent(name: "powerLevel", value: "Offline")
    sendEvent(name: "power", value: 0) // Force 0W
    
    // Update Tile to Black with "OFFLINE" text
    updateHtmlTile(0, 0, true) 
    
    // Propagate to Children
    childDevices.each { child ->
        child.setOffline()
    }
}

def parseData(devices) {
    def mergedDevices = [:]
    
    devices.each { item ->
        def name = item.name
        if (!mergedDevices[name]) mergedDevices[name] = []
        mergedDevices[name] << item
    }

    mergedDevices.each { name, items ->
        def isParent = false
        if (items[0].location == name) isParent = true
        
        if (isParent) {
             updateParent(name, items)
        } else {
             updateChild(name, items)
        }
    }
}

def updateParent(name, items) {
    if (device.currentValue("location") != name) {
        sendEvent(name: "location", value: name)
        device.setName(name)
    }

    def iconItem = items.find { it.url }
    if (iconItem) {
        def cleanUrl = iconItem.url.replace("\\/", "/")
        if (device.currentValue("icon") != cleanUrl) {
            sendEvent(name: "icon", value: cleanUrl)
            String html = "<img src='${cleanUrl}' style='width:50px; height:50px;'>"
            sendEvent(name: "htmlIcon", value: html)
        }
    }

    def powerW = 0
    items.each { item ->
        def results = processItem(item)
        results.each { res ->
            def attrName = "location" + res.baseType.capitalize() + res.suffix
            if (res.unitSuffix) attrName += res.unitSuffix
            
            if (attrName == "locationPowerRealTimeW") {
                sendEvent(name: "power", value: res.value, unit: "W")
                powerW = res.value
            }
            
            sendEvent(name: attrName, value: res.value, unit: res.unit)
        }
    }
    
    updateHtmlTile(powerW, 0, false)
}

def updateChild(name, items) {
    def dni = "wimeter-" + name.replaceAll("\\s", "").toLowerCase()
    def child = childDevices.find { it.deviceNetworkId == dni }
    
    if (!child) {
        if (logEnable) log.info "Creating Child Device: ${name}"
        try {
            child = addChildDevice("aniva", "WiMeter Child Device", dni, [name: name, isComponent: false])
            child.label = name 
            child.updateVersion(driverVersion())
        } catch (e) {
            log.error "Failed to create child: ${e}"
            return
        }
    }
    child.parseItems(items)
}

def processItem(item) {
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
            results << [value: rawVal.round(2), unit: "kWh", baseType: "power", suffix: suffix, unitSuffix: "Kwh"]
        }
    }
    return results
}

// --- HTML TILE GENERATION (Restored User's CSS) ---
void updateHtmlTile(powerValW, costVal, boolean isOffline) {
    def powerVal = (powerValW / 1000).round(2) 
    
    def tActive = (tileThresholdActive != null) ? tileThresholdActive : 1.0
    def tMed = (tileThresholdMed != null) ? tileThresholdMed : 3.0
    def tHigh = (tileThresholdHigh != null) ? tileThresholdHigh : 6.0
    
    String cRed = "#B71C1C"
    String cYellow = "#FFD600"
    String cGreen = "#2E7D32"
    String cGrey = "#424242"
    String cBlack = "#000000"
    
    def cardColor = cGrey
    def levelText = "Idle"
    
    // Logic Variables for CSS
    String topLabel = "Power"
    String mainValue = "${powerVal}"
    String unitSuffix = "kW"

    if (isOffline) {
        cardColor = cBlack
        levelText = "Offline"
        topLabel = "Status"
        mainValue = "OFFLINE"
        unitSuffix = ""
    } else {
        if (powerVal >= tHigh) {
            cardColor = cRed
            levelText = "High"
        } else if (powerVal >= tMed) {
            cardColor = cYellow
            levelText = "Medium"
        } else if (powerVal >= tActive) {
            cardColor = cGreen
            levelText = "Active"
        }
    }
    
    sendEvent(name: "powerLevel", value: levelText)
    
    // --- EXACT USER CSS BLOCK ---
    String html = """
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
    </div>
    """
    
    sendEvent(name: "htmlTile", value: html)
}

// --- COMMANDS ---

void recreateChildDevices() {
    log.warn "Deleting all children..."
    childDevices.each { deleteChildDevice(it.deviceNetworkId) }
    log.warn "Children deleted. Refreshing to recreate..."
    refresh()
}

void resetAllData() {
    log.warn "Resetting local data..."
}