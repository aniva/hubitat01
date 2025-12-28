/**
 * WiMeter Child Device
 * v4.5 - Unit Suffixes & Clean States
 */

metadata {
    definition (name: "WiMeter Child Device", namespace: "aniva", author: "aniva") {
        capability "Sensor"
        capability "Initialize"
        capability "Refresh"

        attribute "_version", "string"
        attribute "icon", "string"
        attribute "html_icon", "string"

        // Explicit Attributes (Updated with Suffixes)
        attribute "power_real-time_kw", "number"
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

def driverVersion() { return "4.5" }

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
        def result = calculateValueAndSuffix(item)
        if (result.baseType) {
            // Updated Naming Scheme with Suffix
            def attrName = "${result.baseType}${result.suffix}${result.unitSuffix}"
            sendEvent(name: attrName, value: result.value, unit: result.unit)
        }
    }
    
    if (device.currentValue("_version") != driverVersion()) {
        sendEvent(name: "_version", value: driverVersion())
    }
}

// Logic duplicated for Child context
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
        
        // Determine unit suffix
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