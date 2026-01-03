/**
 * IKEA TIMMERFLOTTE Matter Sensor
 *
 * Description:
 * A dedicated driver for the IKEA TIMMERFLOTTE Matter Temperature & Humidity Sensor.
 * Handles standard Matter clusters for Temp (0x0402), Humidity (0x0405), and Power (0x002F).
 *
 * Author: Aniva
 * Date: 2026-01-02
 */

import groovy.transform.Field

@Field static final String driverVersion = "1.0.1"

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

        // Standard Matter Fingerprint for Timmerflotte
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
        input "tempOffset", "decimal", title: "Temperature Offset", description: "Adjust temperature by this amount", defaultValue: 0.0
        input "humOffset", "decimal", title: "Humidity Offset", description: "Adjust humidity by this amount", defaultValue: 0.0
    }
}

// ... (Rest of logic remains identical to previous version)

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
    updateVersion()
    log.info "IKEA Timmerflotte Driver initialized (v${driverVersion})"
    if (logEnable) runIn(1800, logsOff)
}

void updateVersion() {
    if (state._version != driverVersion) {
        state._version = driverVersion
    }
}

void configure() {
    log.info "Configuring Matter subscriptions..."
    initialize()
    List<String> cmds = []
    cmds.add(subscribeToAttribute(0x0402, 0x0000)) 
    cmds.add(subscribeToAttribute(0x0405, 0x0000))
    cmds.add(subscribeToAttribute(0x002F, 0x000C))
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.MATTER))
}

void refresh() {
    log.info "Refreshing..."
    List<String> cmds = []
    cmds.add(readAttribute(0x0402, 0x0000))
    cmds.add(readAttribute(0x0405, 0x0000))
    cmds.add(readAttribute(0x002F, 0x000C))
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.MATTER))
}

String readAttribute(int cluster, int attribute) {
    return "he rattr ${device.deviceNetworkId} 01 ${Integer.toHexString(cluster)} ${Integer.toHexString(attribute)}"
}

String subscribeToAttribute(int cluster, int attribute) {
    return "he subscribe ${device.deviceNetworkId} 01 ${Integer.toHexString(cluster)} ${Integer.toHexString(attribute)} 0 120"
}

void parse(String description) {
    if (logEnable) log.debug "Parsing: ${description}"
    Map descMap = matter.parseDescriptionAsMap(description)
    
    if (descMap) {
        if (descMap.cluster == "0402" && descMap.attrId == "0000") {
            def rawValue = Integer.parseInt(descMap.value, 16)
            if (rawValue > 32767) rawValue -= 65536
            def finalVal = (rawValue / 100.0) + (tempOffset ?: 0.0)
            finalVal = Math.round(finalVal * 100) / 100
            sendEvent(name: "temperature", value: finalVal, unit: "°C", descriptionText: "Temperature is ${finalVal}°C")
        }
        else if (descMap.cluster == "0405" && descMap.attrId == "0000") {
            def rawValue = Integer.parseInt(descMap.value, 16)
            def finalVal = (rawValue / 100.0) + (humOffset ?: 0.0)
            finalVal = Math.round(finalVal * 100) / 100
            sendEvent(name: "humidity", value: finalVal, unit: "%", descriptionText: "Humidity is ${finalVal}%")
        }
        else if (descMap.cluster == "002F" && descMap.attrId == "000C") {
             def rawValue = Integer.parseInt(descMap.value, 16)
             def finalVal = Math.round(rawValue / 2)
             if (finalVal > 100) finalVal = 100
             sendEvent(name: "battery", value: finalVal, unit: "%", descriptionText: "Battery is ${finalVal}%")
        }
    }
}