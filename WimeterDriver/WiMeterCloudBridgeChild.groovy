/**
 * WiMeter Child Device
 *
 * v4.17 - FIX: Preferences Table now dynamically reads saved settings instead of showing hardcoded defaults.
 * v4.16 - Added "Offline" state (Black Tile) & Aniva Header.
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "4.17"

metadata {
    definition (name: "WiMeter Child Device", namespace: "aniva", author: "aniva") {
        capability "PowerMeter" 
        capability "EnergyMeter"
        capability "Sensor"
        capability "Initialize"
        capability "Refresh"

        attribute "_version", "string"
        attribute "icon", "string"
        attribute "htmlIcon", "string"
        attribute "htmlTile", "string"
        
        // Semantic State
        attribute "powerLevel", "string" // High, Medium, Active, Idle, Offline

        attribute "powerRealTimeKw", "number"
        attribute "powerRealTimeW", "number"
        attribute "power", "number" 
        
        attribute "powerPerDayKwh", "number"
        attribute "powerPerWeekKwh", "number"
        attribute "powerPerMonthKwh", "number"
        attribute "powerPerPeriodKwh", "number"
        
        attribute "costRealTime", "number"
        attribute "costPerDay", "number"
        attribute "costPerWeek", "number"
        attribute "costPerMonth", "number"
        attribute "costPerPeriod", "number"
        
        command "setOffline"
    }
    
    preferences {
        // --- DYNAMIC VARIABLES FOR TABLE ---
        // This reads the SAVED setting. If null, falls back to default.
        def tActive = settings?.tileThresholdActive != null ? settings.tileThresholdActive : 0.4
        def tMed = settings?.tileThresholdMed != null ? settings.tileThresholdMed : 1.0
        def tHigh = settings?.tileThresholdHigh != null ? settings.tileThresholdHigh : 2.0

        // --- ANIVA STANDARD HEADER ---
        input name: "about", type: "paragraph", element: "paragraph", title: "", description: """
        <div style='display: flex; align-items: center; justify-content: space-between; padding: 10px; border: 1px solid #e0e0e0; border-radius: 5px; background: #fafafa; margin-bottom: 10px;'>
            <div style='display: flex; align-items: center;'>
                <img src='https://raw.githubusercontent.com/aniva/hubitat01/master/WimeterDriver/images/wimeter_device.png' 
                     style='height: 60px; width: 60px; object-fit: contain; margin-right: 15px;' 
                     onerror="this.src='https://raw.githubusercontent.com/hubitat/HubitatPublic/master/examples/drivers/icons/energy-meter.png'">
                <div>
                    <div style='font-weight: bold; font-size: 1.1em; color: #333;'>WiMeter Child Device</div>
                    <div style='font-size: 0.8em; color: #888;'>Driver v${driverVersion()}</div>
                </div>
            </div>
        </div>"""
        
        // DYNAMIC TABLE (Uses ${tHigh} instead of hardcoded numbers)
        input "headerTile", "paragraph", title: "", description: """
        <div style="background-color:#f0f0f0; padding: 10px; border-radius:5px; margin-top:10px;">
            <b style="color:#333;">Dashboard Tile Logic (Appliance)</b>
            <table style="width:100%; font-size:12px; margin-top:5px; border-collapse:collapse;">
                <tr style="border-bottom:1px solid #ddd;"><th style="text-align:left;">State</th><th style="text-align:left;">Threshold</th><th style="text-align:left;">Color</th></tr>
                <tr><td><b>High</b></td><td>> ${tHigh} kW</td><td><span style="color:white; background-color:#B71C1C; padding:2px 5px; border-radius:3px;">Red</span></td></tr>
                <tr><td><b>Medium</b></td><td>> ${tMed} kW</td><td><span style="color:black; background-color:#FFD600; padding:2px 5px; border-radius:3px;">Yellow</span></td></tr>
                <tr><td><b>Active</b></td><td>> ${tActive} kW</td><td><span style="color:white; background-color:#2E7D32; padding:2px 5px; border-radius:3px;">Green</span></td></tr>
                <tr><td><b>Idle</b></td><td>< ${tActive} kW</td><td><span style="color:white; background-color:#424242; padding:2px 5px; border-radius:3px;">Grey</span></td></tr>
                <tr><td><b>Offline</b></td><td>No Data</td><td><span style="color:white; background-color:#000000; padding:2px 5px; border-radius:3px;">Black</span></td></tr>
            </table>
        </div>
        """
        
        input "tileThresholdActive", "decimal", title: "<span style='color:#2E7D32; font-weight:bold;'>Active Threshold (kW)</span>", description: "Default: 0.4", defaultValue: 0.4
        input "tileThresholdMed", "decimal", title: "<span style='color:#F9A825; font-weight:bold;'>Medium Threshold (kW)</span>", description: "Default: 1.0", defaultValue: 1.0
        input "tileThresholdHigh", "decimal", title: "<span style='color:#B71C1C; font-weight:bold;'>High Threshold (kW)</span>", description: "Default: 2.0", defaultValue: 2.0
    }
}

def driverVersion() { return DRIVER_VERSION }

void updateVersion(String ver) {
    sendEvent(name: "_version", value: ver)
}

void initialize() {
    sendEvent(name: "_version", value: driverVersion())
}

void refresh() {
    parent.refresh()
}

// Called by Parent when API fails
void setOffline() {
    sendEvent(name: "power", value: 0)
    sendEvent(name: "powerRealTimeW", value: 0)
    sendEvent(name: "powerRealTimeKw", value: 0)
    sendEvent(name: "powerLevel", value: "Offline")
    
    updateHtmlTile(0, 0, true) // isOffline = true
}

void parseItems(items) {
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
            def attrName = res.baseType == "cost" ? "cost" : "power"
            attrName += res.suffix
            if (res.unitSuffix) attrName += res.unitSuffix
            
            if (attrName == "powerRealTimeW") {
                sendEvent(name: "power", value: res.value, unit: "W")
                powerW = res.value
            }
            sendEvent(name: attrName, value: res.value, unit: res.unit)
        }
    }
    updateHtmlTile(powerW, 0, false)
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
    
    // FETCH SETTINGS AGAIN for live logic (safeguard)
    def tActive = (tileThresholdActive != null) ? tileThresholdActive : 0.4
    def tMed = (tileThresholdMed != null) ? tileThresholdMed : 1.0
    def tHigh = (tileThresholdHigh != null) ? tileThresholdHigh : 2.0
    
    String cRed = "#B71C1C"
    String cYellow = "#FFD600"
    String cGreen = "#2E7D32"
    String cGrey = "#424242"
    String cBlack = "#000000"
    
    def cardColor = cGrey
    def levelText = "Idle"
    
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