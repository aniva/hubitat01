/**
 * IKEA PARASOLL Matter Sensor
 *
 * Device: IKEA PARASOLL (Contact Sensor via DIRIGERA)
 * Protocol: Matter (Bridged Zigbee)
 *
 * Author: Aniva
 * License: Apache 2.0
 * Support: https://paypal.me/AndreiIvanov420
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "1.0.0"

metadata {
    definition (name: "IKEA PARASOLL Matter Sensor", namespace: "aniva", author: "Aniva") {
        capability "Configuration"
        capability "Battery"
        capability "ContactSensor"
        capability "Sensor"
        capability "Refresh"
        capability "Initialize"

        // Fingerprint: Matches generic Matter Contact Sensors bridged from IKEA
        fingerprint deviceId: "0015", vendor: "IKEA of Sweden", model: "PARASOLL Door/Window Sensor", deviceJoinName: "IKEA PARASOLL"
    }

preferences {
        // --- DRIVER INFO HEADER (Aniva Standard) ---
        input name: "about", type: "paragraph", element: "paragraph", title: "", description: """
        <div style='display: flex; align-items: center; justify-content: space-between; padding: 10px; border: 1px solid #e0e0e0; border-radius: 5px; background: #fafafa; margin-bottom: 10px;'>
            <div style='display: flex; align-items: center;'>
                <img src='https://raw.githubusercontent.com/aniva/hubitat01/refs/heads/master/IkeaParasoll/images/ikeaparasoll.png' 
                     style='height: 50px; width: 50px; min-width: 50px; object-fit: contain; margin-right: 15px;'
                     onerror="this.src='https://raw.githubusercontent.com/hubitat/HubitatPublic/master/examples/drivers/icons/contact-sensor.png'">
                <div>
                    <div style='font-weight: bold; font-size: 1.1em; color: #333;'>IKEA PARASOLL</div>
                    <div style='font-size: 0.8em; color: #888;'>Matter Sensor v${driverVersion()}</div>
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

// --- Versioning Helper ---
def driverVersion() { return DRIVER_VERSION }

// --- Lifecycle Methods ---

void installed() {
    logInfo("Installed")
    initialize()
}

void initialize() {
    state.driverVersion = driverVersion()
    sendEvent(name: "_version", value: driverVersion())
    logInfo("Initializing ${device.displayName} (Driver v${driverVersion()})")
    
    if (logEnable) runIn(1800, logsOff)
}

void updated() {
    logInfo("Updated")
    initialize()
}

// --- Matter Parsing ---

void parse(String description) {
    def descMap = matter.parseDescriptionAsMap(description)
    logDebug("Parsed: ${descMap}")

    if (descMap.cluster == "0045" && descMap.attrId == "0000") {
        // Cluster 0x0045: Boolean State (Contact Sensor)
        // Value 1 (True) = Contact Detected (Closed)
        // Value 0 (False) = No Contact (Open)
        handleContactEvent(descMap.value)
    } 
    else if (descMap.cluster == "002F" && descMap.attrId == "000C") {
        // Cluster 0x002F: Power Source
        // Attribute 0x000C: BatPercentRemaining (0-200 scale, representing 0-100% in 0.5% steps)
        handleBatteryEvent(descMap.value)
    }
}

void handleContactEvent(String valueHex) {
    int val = Integer.parseInt(valueHex, 16)
    // Default Matter: 1 = Closed, 0 = Open
    String state = (val == 1) ? "closed" : "open"
    
    // Handle user preference to reverse logic
    if (reverseContact) {
        state = (state == "closed") ? "open" : "closed"
    }

    sendEvent(name: "contact", value: state, descriptionText: "Contact is ${state}")
    logInfo("Contact is ${state}")
}

void handleBatteryEvent(String valueHex) {
    int rawValue = Integer.parseInt(valueHex, 16)
    // Matter provides battery in 0.5% increments (e.g., 200 = 100%)
    int percent = (int)(rawValue / 2)
    
    if (percent > 100) percent = 100
    if (percent < 0) percent = 0
    
    sendEvent(name: "battery", value: percent, unit: "%", descriptionText: "Battery is ${percent}%")
    logInfo("Battery is ${percent}%")
}

// --- Matter Commands ---

void refresh() {
    logDebug("Refreshing values...")
    // Poll Boolean State (0045) and Power Source (002F)
    List<String> cmds = []
    cmds.add(matter.readAttribute("0045", "0000")) // Read Contact
    cmds.add(matter.readAttribute("002F", "000C")) // Read Battery
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.MATTER))
}

void configure() {
    logInfo("Configuring Matter subscriptions...")
    List<String> cmds = []
    
    // Subscribe to Boolean State (Contact) - Cluster 0045, Attr 0000
    cmds.add(matter.subscribe("0045", "0000"))
    
    // Subscribe to Power Source (Battery) - Cluster 002F, Attr 000C
    cmds.add(matter.subscribe("002F", "000C"))
    
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.MATTER))
}

// --- Logging Helpers ---

void logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "${device.displayName}: Debug logging auto-disabled"
}

void logDebug(String msg) {
    if (logEnable) log.debug "${device.displayName}: ${msg}"
}

void logInfo(String msg) {
    if (txtEnable) log.info "${device.displayName}: ${msg}"
}