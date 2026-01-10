/**
 * IKEA PARASOLL Zigbee Sensor (Component)
 *
 * Device: IKEA PARASOLL (Child Device of Matter Bridge)
 * Description: Component driver for IKEA Zigbee sensors accessed via Dirigera Matter Bridge.
 * Includes battery correction (/2) and logic reversal.
 *
 * Author: Aniva
 * Version: 2.2.0
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "2.2.0"

metadata {
    // Renamed as requested to reflect physical hardware
    definition (name: "IKEA PARASOLL Zigbee Sensor", namespace: "aniva", author: "Aniva") {
        capability "Configuration"
        capability "Battery"
        capability "ContactSensor"
        capability "Sensor"
        capability "Refresh"
        capability "Initialize"
    }

    preferences {
        input name: "about", type: "paragraph", element: "paragraph", title: "", description: """
        <div style='display: flex; align-items: center; justify-content: space-between; padding: 10px; border: 1px solid #e0e0e0; border-radius: 5px; background: #fafafa; margin-bottom: 10px;'>
            <div style='display: flex; align-items: center;'>
                <img src='https://raw.githubusercontent.com/aniva/hubitat01/refs/heads/master/IkeaParasoll/images/ikeaparasoll.png' 
                     style='height: 50px; width: 50px; min-width: 50px; object-fit: contain; margin-right: 15px;'
                     onerror="this.src='https://raw.githubusercontent.com/hubitat/HubitatPublic/master/examples/drivers/icons/contact-sensor.png'">
                <div>
                    <div style='font-weight: bold; font-size: 1.1em; color: #333;'>IKEA PARASOLL</div>
                    <div style='font-size: 0.8em; color: #888;'>Zigbee Component v${driverVersion()}</div>
                </div>
            </div>
            <div style='text-align: right; font-size: 0.8em; line-height: 1.4;'>
                <a href='https://github.com/aniva/hubitat01/tree/master/IkeaParasoll' target='_blank' style='color: #0275d8; text-decoration: none;'>View on GitHub</a><br>
                <a href='https://paypal.me/AndreiIvanov420' target='_blank' style='color: #0275d8; text-decoration: none;'>Support Dev</a>
            </div>
        </div>"""
        
        input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: true
        input "txtEnable", "bool", title: "Enable Description Text", defaultValue: true
        input "reverseContact", "bool", title: "Reverse Open/Close Logic", defaultValue: false
    }
}

def driverVersion() { return DRIVER_VERSION }

void installed() { initialize() }

void initialize() {
    sendEvent(name: "_version", value: driverVersion())
    logInfo("Initializing (v${driverVersion()})")
    if (logEnable) runIn(1800, logsOff)
}

void updated() { 
    initialize() 
}

// --- COMPONENT PARSING (SAFE MODE) ---
def parse(description) {
    try {
        if (logEnable) log.debug "Received from Bridge: ${description}"
        
        if (description instanceof List) {
            description.each { event -> handleBridgeEvent(event) }
        }
        else if (description instanceof Map) {
            handleBridgeEvent(description)
        }
        else {
            if (logEnable) log.warn "Ignored unknown data format: ${description}"
        }
    } catch (e) {
        log.error "FATAL CRASH IN PARSE: ${e}"
    }
}

void handleBridgeEvent(Map event) {
    String name = event.name
    def value = event.value
    String descriptionText = event.descriptionText ?: "${name} is ${value}"
    
    // 1. BATTERY FIX (Divide by 2 for IKEA Matter)
    if (name == "battery") {
        int raw = value as Integer
        int percent = (int)(raw / 2)
        if (percent > 100) percent = 100
        if (percent < 0) percent = 0
        
        sendEvent(name: "battery", value: percent, unit: "%", descriptionText: "Battery is ${percent}%")
        if (txtEnable) log.info "Battery is ${percent}% (Raw: ${raw})"
    }
    // 2. CONTACT LOGIC (Optional Reversal)
    else if (name == "contact") {
        String state = value
        
        if (reverseContact) {
            state = (state == "closed") ? "open" : "closed"
            descriptionText = "Contact is ${state} (Reversed)"
        }
        
        sendEvent(name: "contact", value: state, descriptionText: descriptionText)
        if (txtEnable) log.info "${descriptionText}"
    }
    // 3. PASS-THROUGH
    else {
        sendEvent(event)
    }
}

// --- COMMANDS ---

void refresh() {
    if (logEnable) log.debug "Requesting Refresh from Parent Bridge..."
    try {
        parent?.componentRefresh(this.device)
    } catch (e) {
        log.warn "Failed to request refresh from parent: ${e}"
    }
}

void configure() {
    refresh()
}

// --- HELPERS ---
void logsOff() { device.updateSetting("logEnable", [value: "false", type: "bool"]) }
void logDebug(String msg) { if (logEnable) log.debug "${device.displayName}: ${msg}" }
void logInfo(String msg) { if (txtEnable) log.info "${device.displayName}: ${msg}" }