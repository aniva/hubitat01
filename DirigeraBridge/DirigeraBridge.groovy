/**
 * IKEA DIRIGERA Bridge
 *
 * Description: Custom Matter Bridge driver that bypasses standard filters to allow
 * custom child drivers (like IKEA PARASOLL) to function correctly.
 * Acts as a dispatcher, routing raw Matter events to specific endpoints.
 *
 * Author: Aniva
 * License: Apache 2.0
 * Support: https://paypal.me/AndreiIvanov420
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "1.0.0"

metadata {
    definition (name: "IKEA DIRIGERA Bridge", namespace: "aniva", author: "Aniva") {
        capability "Initialize"
        capability "Refresh"
        capability "Configuration"
        
        // Fingerprint for the IKEA DIRIGERA Hub (Matter Gateway)
        fingerprint deviceId: "0010", vendor: "IKEA of Sweden", model: "DIRIGERA Hub", controllerType: "MAT"
    }

    preferences {
        // --- DRIVER INFO HEADER (Aniva Standard) ---
        input name: "about", type: "paragraph", element: "paragraph", title: "", description: """
        <div style='display: flex; align-items: center; justify-content: space-between; padding: 10px; border: 1px solid #e0e0e0; border-radius: 5px; background: #fafafa; margin-bottom: 10px;'>
            <div style='display: flex; align-items: center;'>
                <img src='https://raw.githubusercontent.com/aniva/hubitat01/refs/heads/master/DirigeraBridge/images/dirigera.png' 
                     style='height: 50px; width: 50px; min-width: 50px; object-fit: contain; margin-right: 15px;'
                     onerror="this.src='https://raw.githubusercontent.com/hubitat/HubitatPublic/master/examples/drivers/icons/hub.png'">
                <div>
                    <div style='font-weight: bold; font-size: 1.1em; color: #333;'>IKEA DIRIGERA</div>
                    <div style='font-size: 0.8em; color: #888;'>Custom Matter Bridge v${driverVersion()}</div>
                </div>
            </div>
            <div style='text-align: right; font-size: 0.8em; line-height: 1.4;'>
                <a href='https://raw.githubusercontent.com/aniva/hubitat01/refs/heads/master/DirigeraBridge/README.md' target='_blank' style='color: #0275d8; text-decoration: none;'>View README</a><br>
                <a href='https://paypal.me/AndreiIvanov420' target='_blank' style='color: #0275d8; text-decoration: none;'>Support Dev</a>
            </div>
        </div>"""
        
        input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: true
        input "txtEnable", "bool", title: "Enable Description Text", defaultValue: true
    }
}

def driverVersion() { return DRIVER_VERSION }

void installed() {
    logInfo("Installed")
    initialize()
}

void updated() {
    logInfo("Updated")
    initialize()
}

void initialize() {
    state.driverVersion = driverVersion()
    logInfo("Initializing Bridge...")
    // In a full implementation, we might query the endpoint list here
}

// --- THE DISPATCHER ---
// Parses raw Matter messages from the Hub and routes them to children
void parse(String description) {
    Map descMap = matter.parseDescriptionAsMap(description)
    
    if (logEnable) log.debug "BRIDGE Received Raw: ${descMap}"

    // 1. Extract Endpoint ID (Target Device)
    String endpointId = descMap.endpointId
    if (!endpointId) return // Ignore messages for the Bridge itself (Endpoint 00) or system messages

    // 2. Construct Child Device Network ID (DNI)
    // Standard Format: "BridgeDNI-EndpointID" (e.g., M3012-17)
    String childDni = "${device.deviceNetworkId}-${endpointId}"
    
    // 3. Find Child Device
    def childDevice = getChildDevice(childDni)
    
    if (childDevice) {
        // 4. Translate & Forward
        List events = convertToEvents(descMap)
        if (events && events.size() > 0) {
            if (logEnable) log.debug "Dispatching to ${childDevice.displayName}: ${events}"
            childDevice.parse(events)
        }
    } else {
        // Optional: If you want to auto-create missing devices, code goes here.
        // For now, we just warn.
        if (logEnable) log.warn "Data received for unknown child: ${childDni}. (Run 'Refresh' or Re-pair to discover)."
    }
}

// --- TRANSLATOR (Matter -> Event List) ---
List convertToEvents(Map map) {
    List events = []
    
    // Contact Sensor (Cluster 0045, Attribute 0000)
    if (map.cluster == "0045" && map.attrId == "0000") {
        String val = map.value
        // Normalize 00/01 to open/closed
        String status = (val == "00" || val == "0") ? "open" : "closed"
        events.add([name: "contact", value: status])
    }
    
    // Battery (Cluster 002F, Attribute 000C)
    else if (map.cluster == "002F" && map.attrId == "000C") {
        // Convert Hex String to Integer
        int rawValue = Integer.parseInt(map.value, 16)
        // Send RAW value to child (Child handles the /2 math)
        events.add([name: "battery", value: rawValue])
    }
    
    return events
}

// --- COMMANDS ---

void refresh() {
    logInfo("Refreshing Bridge...")
    // This refreshes the bridge itself.
    // To refresh children, iterate through them or wait for them to call componentRefresh.
}

void configure() {
    logInfo("Configuring Bridge...")
    refresh()
}

// --- CHILD INTERFACE ---
// Called by Child Drivers (v2.1+) when 'Refresh' is clicked on the child page
void componentRefresh(def childDevice) {
    if (txtEnable) log.info "Refresh requested by child: ${childDevice.displayName}"
    
    String dni = childDevice.deviceNetworkId
    if (!dni || !dni.contains("-")) return
    
    String ep = dni.split("-").last() // Extract "17" from "M3012-17"
    
    // Send Read Attribute commands to the Matter Network for this specific endpoint
    List<String> cmds = []
    cmds.add(matter.readAttribute(ep, "0045", "0000")) // Contact
    cmds.add(matter.readAttribute(ep, "002F", "000C")) // Battery
    
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.MATTER))
}

// --- HELPERS ---

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