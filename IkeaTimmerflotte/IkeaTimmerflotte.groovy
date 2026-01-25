/**
 * IKEA TIMMERFLOTTE Matter Sensor
 *
 * Description:
 * Dedicated driver for IKEA TIMMERFLOTTE (Matter).
 * Features: Auto-detection, robust Endpoint targeting, Aniva styling.
 *
 * Author: Aniva
 * Date: 2026-01-24
 */

import groovy.transform.Field
import hubitat.device.HubAction
import hubitat.device.Protocol

@Field static final String DRIVER_VERSION = "1.0.12"

metadata {
    definition (name: "IKEA TIMMERFLOTTE Matter Sensor", namespace: "aniva", author: "Aniva") {
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "Battery"
        capability "Sensor"
        capability "Configuration"
        capability "Refresh"
        capability "Initialize"

        attribute "_version", "string"

        // PRIMARY FINGERPRINT
        fingerprint endpointId: "01", inClusters: "0003,001D,0402", outClusters: "", model: "TIMMERFLOTTE temp/hmd sensor", manufacturer: "IKEA of Sweden", controllerType: "MAT"
        
        // SECONDARY FINGERPRINT
        fingerprint endpointId: "01", inClusters: "0003,0004,001D,0402,0405,002F", outClusters: "", model: "TIMMERFLOTTE", manufacturer: "IKEA of Sweden"
    }

    preferences {
        // --- DRIVER INFO HEADER ---
        input name: "about", type: "paragraph", element: "paragraph", title: "", description: """
        <div style='display: flex; align-items: center; justify-content: space-between; padding: 10px; border: 1px solid #e0e0e0; border-radius: 5px; background: #fafafa; margin-bottom: 10px;'>
            <div style='display: flex; align-items: center;'>
                <img src='https://raw.githubusercontent.com/aniva/hubitat01/master/IkeaTimmerflotte/images/timmerflotte.png' 
                     style='height: 50px; width: 50px; object-fit: contain; margin-right: 15px;' 
                     onerror="this.src='https://raw.githubusercontent.com/hubitat/HubitatPublic/master/examples/drivers/icons/sensor.png'">
                <div>
                    <div style='font-weight: bold; font-size: 1.1em; color: #333;'>IKEA TIMMERFLOTTE</div>
                    <div style='font-size: 0.8em; color: #888;'>Matter Sensor v${driverVersion()}</div>
                </div>
            </div>
            <div style='text-align: right; font-size: 0.8em; line-height: 1.4;'>
                <a href='https://github.com/aniva/hubitat01/tree/master/IkeaTimmerflotte' target='_blank' style='color: #0275d8; text-decoration: none;'>View on GitHub</a><br>
                <a href='https://paypal.me/AndreiIvanov420' target='_blank' style='color: #0275d8; text-decoration: none;'>Support Dev</a>
            </div>
        </div>"""
        
        input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: true
        input "txtEnable", "bool", title: "Enable Description Text", defaultValue: true
        input "tempOffset", "decimal", title: "Temperature Offset", defaultValue: 0.0
        input "humOffset", "decimal", title: "Humidity Offset", defaultValue: 0.0
    }
}

def driverVersion() { return DRIVER_VERSION }

void logsOff() {
    log.warn "Debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

void installed() {
    log.info "Installing..."
    initialize()
}

void updated() {
    log.info "Updated..."
    // Note: We do NOT call configure() here automatically to avoid flooding network on simple preference changes.
    // User should hit Configure manually if they want to re-subscribe.
    initialize()
}

void initialize() {
    sendEvent(name: "_version", value: driverVersion())
    
    // FORCE LOG: This will print regardless of logEnable settings
    log.info "${device.displayName} initialized (Driver v${driverVersion()})"
    
    if (logEnable) runIn(1800, logsOff)
    
    // FIX: Refresh on startup to ensure we have data if hub rebooted
    refresh()
}

void configure() {
    // Ensure version state is updated
    initialize()
    
    if (logEnable) log.info "--- Configure: Sending cleanSubscribe commands ---"
    
    List<Map<String,String>> paths = []
    
    // Endpoint 01: Temperature
    paths.add(matter.attributePath(0x01, 0x0402, 0x0000)) 
    // Endpoint 02: Humidity
    paths.add(matter.attributePath(0x02, 0x0405, 0x0000)) 
    // Endpoint 00: Battery
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C)) 

    // FIX: Changed 0xFFFF (18h) to 3600 (1h) for Max Interval
    // This forces a heartbeat every hour even if values don't change.
    String cmd = matter.cleanSubscribe(1, 3600, paths)
    
    if (logEnable) log.debug "Sending Matter Subscribe CMD: ${cmd}"
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
    
    log.warn "Configuration sent. Press sensor button to wake device."
}

void refresh() {
    if (logEnable) log.info "Refreshing..."
    
    List<Map<String,String>> paths = []
    paths.add(matter.attributePath(0x01, 0x0402, 0x0000))
    paths.add(matter.attributePath(0x02, 0x0405, 0x0000))
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C))
    
    String cmd = matter.readAttributes(paths)
    if (logEnable) log.debug "Sending Matter Read CMD: ${cmd}"
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

void parse(String description) {
    // LOG EVERYTHING: Runs before parsing to capture all incoming traffic
    if (logEnable) log.debug "RAW DATA: ${description}"
     
    Map descMap = matter.parseDescriptionAsMap(description)
    if (logEnable && descMap) log.debug "PARSED MAP: ${descMap}"
    
    if (descMap) {
        Integer ep = descMap.endpoint ? Integer.parseInt(descMap.endpoint, 16) : null
        Integer cluster = descMap.cluster ? Integer.parseInt(descMap.cluster, 16) : null
        Integer attrId = descMap.attrId ? Integer.parseInt(descMap.attrId, 16) : null
        
        if (ep == null || cluster == null) return

        // Temperature (EP 01, Cluster 0402)
        if (ep == 0x01 && cluster == 0x0402 && attrId == 0x0000) {
            def rawValue = Integer.parseInt(descMap.value, 16)
            if (rawValue > 32767) rawValue -= 65536
            def finalVal = (rawValue / 100.0) + (tempOffset ?: 0.0)
            finalVal = Math.round(finalVal * 100) / 100
            
            String unit = location.temperatureScale == "F" ? "°F" : "°C"
            if (unit == "°F") finalVal = (finalVal * 1.8) + 32 
            
            if (txtEnable) log.info "Temperature: ${finalVal}${unit}"
            sendEvent(name: "temperature", value: finalVal, unit: unit, descriptionText: "Temperature is ${finalVal}${unit}")
        }
        
        // Humidity (EP 02, Cluster 0405)
        else if (ep == 0x02 && cluster == 0x0405 && attrId == 0x0000) {
            def rawValue = Integer.parseInt(descMap.value, 16)
            def finalVal = (rawValue / 100.0) + (humOffset ?: 0.0)
            finalVal = Math.round(finalVal * 100) / 100
            
            if (txtEnable) log.info "Humidity: ${finalVal}%"
            sendEvent(name: "humidity", value: finalVal, unit: "%", descriptionText: "Humidity is ${finalVal}%")
        }

        // Battery (EP 00, Cluster 002F)
        else if (ep == 0x00 && cluster == 0x002F && attrId == 0x000C) {
             def rawValue = Integer.parseInt(descMap.value, 16)
             def finalVal = Math.round(rawValue / 2)
             if (finalVal > 100) finalVal = 100
             
             if (txtEnable) log.info "Battery: ${finalVal}%"
             sendEvent(name: "battery", value: finalVal, unit: "%", descriptionText: "Battery is ${finalVal}%")
        }
    }
}