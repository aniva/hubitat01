/**
 * WiMeter Child Device
 *
 * v4.15 - Added 'powerLevel' attribute (High/Medium/Active/Idle).
 * v4.14 - Added "Visual CSS" labels to Preferences.
 * v4.13 - Removed color selectors. Updated defaults (0.4/1/2 kW).
 */

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
        
        // NEW: Semantic State
        attribute "powerLevel", "string"

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
    }
    
    preferences {
        // TILE CONFIGURATION (Visual CSS Labels)
        input "headerTile", "text", title: "<b>Dashboard Tile Thresholds</b>", description: "Set the power levels (kW) that trigger status color changes.", displayDuringSetup: false, type: "paragraph", element: "paragraph"
        
        input "threshHigh", "decimal", title: "High Power <span style='background-color:#c0392b; color:white; padding:1px 4px; border-radius:3px; font-weight:bold; font-size:0.9em;'>RED</span> [kW]", defaultValue: 2.0, required: true
        input "threshMed", "decimal", title: "Medium Power <span style='background-color:#f1c40f; color:white; padding:1px 4px; border-radius:3px; font-weight:bold; font-size:0.9em; text-shadow: 0px 0px 2px black;'>YELLOW</span> [kW]", defaultValue: 1.0, required: true
        input "threshActive", "decimal", title: "Active Power <span style='background-color:#27ae60; color:white; padding:1px 4px; border-radius:3px; font-weight:bold; font-size:0.9em;'>GREEN</span> [kW]", defaultValue: 0.4, required: true
    }
}

def driverVersion() { return "4.15" }

def installed() { initialize() }

def updated() { initialize() }

def initialize() {
    sendEvent(name: "_version", value: driverVersion())
}

def refresh() { parent?.refresh() }

def parseItems(items) {
    def firstItem = items.find { it.url }
    if (firstItem) {
        if (device.currentValue("icon") != firstItem.url) {
            sendEvent(name: "icon", value: firstItem.url)
            sendEvent(name: "htmlIcon", value: "<img src='${firstItem.url}' style='height:40px;'>")
        }
    }

    items.each { item ->
        def results = calculateValueAndSuffix(item)
        results.each { res ->
            if (res.baseType) {
                def attrName = "${res.baseType}${res.suffix}${res.unitSuffix}"
                sendEvent(name: attrName, value: res.value, unit: res.unit)
                if (attrName == "powerRealTimeKw") {
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
        powerVal = 0.0
    }

    // Threshold Logic (Defaults: 0.4, 1.0, 2.0)
    def tHigh = settings.threshHigh != null ? settings.threshHigh.toBigDecimal() : 2.0
    def tMed = settings.threshMed != null ? settings.threshMed.toBigDecimal() : 1.0
    def tActive = settings.threshActive != null ? settings.threshActive.toBigDecimal() : 0.4
    
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
    
    if (device.currentValue("_version") != driverVersion()) {
        sendEvent(name: "_version", value: driverVersion())
    }
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