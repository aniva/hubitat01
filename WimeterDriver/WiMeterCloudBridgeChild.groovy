/**
 * WiMeter Child Device
 * v4.6 - Added Watt Support (_w)
 */

metadata {
    definition (name: "WiMeter Child Device", namespace: "aniva", author: "aniva") {
        capability "Sensor"
        capability "Initialize"
        capability "Refresh"

        attribute "_version", "string"
        attribute "icon", "string"
        attribute "html_icon", "string"

        // Explicit Attributes
        attribute "power_real-time_kw", "number"
        attribute "power_real-time_w", "number" // NEW
        
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

def driverVersion() { return "4.6" }

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
            }
        }
    }
    
    if (device.currentValue("_version") != driverVersion()) {
        sendEvent(name: "_version", value: driverVersion())
    }
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