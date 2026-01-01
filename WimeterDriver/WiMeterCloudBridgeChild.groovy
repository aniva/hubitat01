/**
 * WiMeter Child Device
 *
 * v4.11 - Refactored state variables to standard camelCase (e.g., powerRealTimeKw).
 * v4.10 - Fixed 'html_tile' metadata and stabilized power calculation.
 * v4.9 - Added standard 'PowerMeter' & 'EnergyMeter' capabilities.
 * v4.8 - Implemented "Safe Mode" HTML tile generation via 'apiStatus'.
 * v4.7 - Added 'power' attribute bridge to mirror real-time usage.
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
        attribute "htmlIcon", "string" // CamelCase
        
        // NEW: Officially defined
        attribute "htmlTile", "string" // CamelCase

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
}

def driverVersion() { return "4.11" }

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
                // Construct CamelCase Attribute Name: BaseType + Suffix + UnitSuffix
                // e.g., power + RealTime + Kw -> powerRealTimeKw
                def attrName = "${res.baseType}${res.suffix}${res.unitSuffix}"
                sendEvent(name: attrName, value: res.value, unit: res.unit)
                
                if (attrName == "powerRealTimeKw") {
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
        powerVal = 0.0
    }

    def cardColor = "#7f8c8d"
    if (powerVal >= 6.0) cardColor = "#c0392b"
    else if (powerVal >= 3.0) cardColor = "#f1c40f"
    else if (powerVal >= 1.0) cardColor = "#27ae60"

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

// Logic duplicated for Child context (identical to parent now)
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

def getIntervalSuffix(interval) {
    // Deprecated in favor of closure above, but kept if other legacy methods call it
    // Not used in new logic.
}