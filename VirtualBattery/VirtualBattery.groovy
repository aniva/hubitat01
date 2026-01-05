/**
 * Virtual Mutable Battery
 *
 * Project: Virtual Mutable Battery Driver
 * Platform: Hubitat Elevation
 *
 * Author: Aniva
 * License: Apache 2.0
 * Support: https://paypal.me/AndreiIvanov420
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "1.0.0"

metadata {
    definition (name: "Virtual Mutable Battery", namespace: "aniva", author: "Aniva") {
        capability "Actuator"      // Critical: Makes custom commands visible in Rule Machine
        capability "Battery"       // Standard: Allows use with standard Dashboard Battery Tiles
        capability "Sensor"
        
        // Custom command to allow Rule Machine to write the battery level
        command "setBattery", [[name: "level", type: "NUMBER", description: "Battery Level (0-100)"]]
    }

    preferences {
        // --- DRIVER INFO HEADER (Aniva Standard) ---
        input name: "about", type: "paragraph", element: "paragraph", title: "", description: """
        <div style='display: flex; align-items: center; justify-content: space-between; padding: 10px; border: 1px solid #e0e0e0; border-radius: 5px; background: #fafafa; margin-bottom: 10px;'>
            <div style='display: flex; align-items: center;'>
                <img src='https://raw.githubusercontent.com/hubitat/HubitatPublic/master/examples/drivers/icons/battery.png' 
                     style='height: 50px; width: 50px; object-fit: contain; margin-right: 15px;'
                     onerror="this.src='https://raw.githubusercontent.com/hubitat/HubitatPublic/master/examples/drivers/icons/sensor.png'">
                <div>
                    <div style='font-weight: bold; font-size: 1.1em; color: #333;'>Virtual Mutable Battery</div>
                    <div style='font-size: 0.8em; color: #888;'>Driver v${driverVersion()}</div>
                </div>
            </div>
            <div style='text-align: right; font-size: 0.8em; line-height: 1.4;'>
                <a href='https://github.com/aniva/hubitat01' target='_blank' style='color: #0275d8; text-decoration: none;'>View on GitHub</a><br>
                <a href='https://paypal.me/AndreiIvanov420' target='_blank' style='color: #0275d8; text-decoration: none;'>Support Dev</a>
            </div>
        </div>"""
        
        input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: true
        input "txtEnable", "bool", title: "Enable Description Text", defaultValue: true
    }
}

// --- Versioning Helper ---
def driverVersion() { return DRIVER_VERSION }

// --- Lifecycle Methods ---

void installed() {
    logInfo("Installed")
    // Initialize with a safe default so the tile isn't empty
    setBattery(100)
    initialize()
}

void initialize() {
    // Write version to state for tracking
    state.driverVersion = driverVersion()
    sendEvent(name: "_version", value: driverVersion())
    
    logInfo("Initializing ${device.displayName} (Driver v${driverVersion()})")
    
    // Auto-disable debug logging after 30 minutes
    if (logEnable) runIn(1800, logsOff)
}

void updated() {
    logInfo("Updated")
    initialize()
}

// --- Custom Commands ---

void setBattery(level) {
    // Basic validation to keep tiles happy
    Integer val = level as Integer
    if (val < 0) val = 0
    if (val > 100) val = 100
    
    logDebug("Setting battery to ${val}%")
    sendEvent(name: "battery", value: val, unit: "%", descriptionText: "Battery set to ${val}%")
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