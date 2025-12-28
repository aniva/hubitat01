/**
 * WiMeter Child Device
 *
 * v4.9 - Added standard 'PowerMeter' & 'EnergyMeter' capabilities. Fixes device icon (shows Lightning Bolt instead of '?') and enables selection in standard energy apps.
 * v4.8 - Implemented "Safe Mode" HTML tile generation via 'apiStatus' to prevent dashboard caching issues and device page layout breaks.
 * v4.7 - Added 'power' attribute bridge to mirror real-time usage for standard dashboard templates.
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
        attribute "html_icon", "string"

        // EXPLICIT ATTRIBUTES
        attribute "power_real-time_kw", "number"
        attribute "power_real-time_w", "number"
        
        // DASHBOARD BRIDGE
        attribute "power", "number" 
        
        attribute "power_per_day_kwh", "number"
        attribute "power_per_week_kwh", "number"
        attribute "power_per_month_kwh", "number"
        attribute "power_per_period_kwh", "number"
        
        attribute "cost_real-time_\$", "number"
        attribute "cost_per_day_\$", "number"
        attribute "cost_per_week_\$", "number"
        attribute "cost_per_month_\$", "number"
        attribute "cost_per_period_\$", "number"
    }
}

def driverVersion() { return "4.9" }

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
            sendEvent(name: "html_icon", value: "<img src='${firstItem.url}' style='height:40px;'>")
        }
    }

    items.each { item ->
        def results = calculateValueAndSuffix(item)
        results.each { res ->
            if (res.baseType) {
                def attrName = "${res.baseType}${res.suffix}${res.unitSuffix}"
                sendEvent(name: attrName, value: res.value, unit: res.unit)
                
                // DASHBOARD BRIDGE: Mirror to 'power'
                if (attrName == "power_real-time_kw") {
                     sendEvent(name: "power", value: res.value, unit: "kW")
                }
            }
        }
    }
    
    if (device.currentValue("_version") != driverVersion()) {
        sendEvent(name: "_version", value: driverVersion())
    }
}

// Logic duplicated for Child context
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