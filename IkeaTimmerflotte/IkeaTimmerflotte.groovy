/**
 * IKEA TIMMERFLOTTE Matter Sensor
 *
 * Description:
 * Dedicated driver for IKEA TIMMERFLOTTE.
 * Uses Matter Helper Library for robust Endpoint targeting (Temp:01, Hum:02, Bat:00).
 *
 * Author: Aniva
 * Date: 2026-01-03
 */

import groovy.transform.Field
import hubitat.device.HubAction
import hubitat.device.Protocol

@Field static final String driverVersion = "1.0.6"

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

        fingerprint endpointId: "01", inClusters: "0003,0004,001D,0402,0405,002F", outClusters: "", model: "TIMMERFLOTTE", manufacturer: "IKEA of Sweden"
    }

    preferences {
        // --- DRIVER INFO HEADER ---
        input name: "about", type: "paragraph", element: "paragraph", title: "", description: """
        <div style='display: flex; align-items: center; justify-content: space-between; padding: 10px; border: 1px solid #e0e0e0; border-radius: 5px; background: #fafafa; margin-bottom: 10px;'>
            <div style='display: flex; align-items: center;'>
                <img src='https://raw.githubusercontent.com/aniva/hubitat01/master/images/timmerflotte_icon.png' 
                     style='height: 50px; width: 50px; object-fit: contain; margin-right: 15px;' 
                     onerror="this.src='https://raw.githubusercontent.com/hubitat/HubitatPublic/master/examples/drivers/icons/sensor.png'">
                <div>
                    <div style='font-weight: bold; font-size: 1.1em; color: #333;'>IKEA TIMMERFLOTTE</div>
                    <div style='font-size: 0.8em; color: #888;'>Matter Sensor v${driverVersion}</div>
                </div>
            </div>
            <div style='text-align: right; font-size: 0.8em; line-height: 1.4;'>
                <a href='https://github.com/aniva/hubitat01' target='_blank' style='color: #0275d8; text-decoration: none;'>View on GitHub</a><br>
                <a href='https://paypal.me/AndreiIvanov420' target='_blank' style='color: #0275d8; text-decoration: none;'>Support Dev</a>
            </div>
        </div>"""
        
        input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: true
        input "txtEnable", "bool", title: "Enable Description Text", defaultValue: true
        input "tempOffset", "decimal", title: "Temperature Offset", defaultValue: 0.0
        input "humOffset", "decimal", title: "Humidity Offset", defaultValue: 0.0
    }
}

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
    initialize()
}

void initialize() {
    sendEvent(name: "_version", value: driverVersion)
    if (logEnable) runIn(1800, logsOff)
    configure() // Auto-configure on init
}

void configure() {
    if (logEnable) log.info "Configuring Matter subscriptions (Clean Subscribe)..."
    
    // Use the robust Matter Helper Library (Same as kkossev driver)
    List<Map<String,String>> paths = []
    
    // Endpoint 01: Temperature (0x0402)
    paths.add(matter.attributePath(0x01, 0x0402, 0x0000)) 
    
    // Endpoint 02: Humidity (0x0405)
    paths.add(matter.attributePath(0x02, 0x0405, 0x0000)) 
    
    // Endpoint 00: Battery (0x002F) - Root Node
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C)) 

    // cleanSubscribe handles the padding and hex formatting perfectly
    String cmd = matter.cleanSubscribe(1, 0xFFFF, paths)
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
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

void parse(String description) {
    if (logEnable) log.debug "Parsing: ${description}"
    Map descMap = matter.parseDescriptionAsMap(description)
    
    if (descMap) {
        Integer ep = Integer.parseInt(descMap.endpoint, 16)
        Integer cluster = Integer.parseInt(descMap.cluster, 16)
        Integer attrId = Integer.parseInt(descMap.attrId, 16)
        
        // Temperature (EP 01, Cluster 0402)
        if (ep == 0x01 && cluster == 0x0402 && attrId == 0x0000) {
            def rawValue = Integer.parseInt(descMap.value, 16)
            if (rawValue > 32767) rawValue -= 65536
            
            def finalVal = (rawValue / 100.0) + (tempOffset ?: 0.0)
            finalVal = Math.round(finalVal * 100) / 100
            
            String unit = location.temperatureScale == "F" ? "°F" : "°C"
            if (unit == "°F") finalVal = (finalVal * 1.8) + 32 // basic C to F conversion if needed
            
            sendEvent(name: "temperature", value: finalVal, unit: unit, descriptionText: "Temperature is ${finalVal}${unit}")
        }
        
        // Humidity (EP 02, Cluster 0405)
        else if (ep == 0x02 && cluster == 0x0405 && attrId == 0x0000) {
            def rawValue = Integer.parseInt(descMap.value, 16)
            def finalVal = (rawValue / 100.0) + (humOffset ?: 0.0)
            finalVal = Math.round(finalVal * 100) / 100
            sendEvent(name: "humidity", value: finalVal, unit: "%", descriptionText: "Humidity is ${finalVal}%")
        }

        // Battery (EP 00, Cluster 002F)
        else if (ep == 0x00 && cluster == 0x002F && attrId == 0x000C) {
             def rawValue = Integer.parseInt(descMap.value, 16)
             def finalVal = Math.round(rawValue / 2)
             if (finalVal > 100) finalVal = 100
             sendEvent(name: "battery", value: finalVal, unit: "%", descriptionText: "Battery is ${finalVal}%")
        }
    }
}