/**
 * IKEA DIRIGERA Bridge
 *
 * Description: Custom Matter Bridge driver that bypasses standard filters.
 * Fixed: Handles both 'endpoint' and 'endpointId' keys from map.
 *
 * Author: Aniva
 * Version: 1.0.9
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "1.0.9"

metadata {
    definition (name: "IKEA DIRIGERA Bridge", namespace: "aniva", author: "Aniva") {
        capability "Initialize"
        capability "Refresh"
        capability "Configuration"
        
        fingerprint deviceId: "0010", vendor: "IKEA of Sweden", model: "DIRIGERA Hub", controllerType: "MAT"
    }

    preferences {
        input name: "about", type: "paragraph", element: "paragraph", title: "", description: """
        <div style='display: flex; align-items: center; justify-content: space-between; padding: 10px; border: 1px solid #e0e0e0; border-radius: 5px; background: #fafafa; margin-bottom: 10px;'>
            <div style='display: flex; align-items: center;'>
                <img src='https://raw.githubusercontent.com/aniva/hubitat01/refs/heads/master/DirigeraBridge/images/dirigera.png' 
                     style='height: 50px; width: 50px; min-width: 50px; object-fit: contain; margin-right: 15px;'
                     onerror="this.src='https://raw.githubusercontent.com/hubitat/HubitatPublic/master/examples/drivers/icons/hub.png'">
                <div>
                    <div style='font-weight: bold; font-size: 1.1em; color: #333;'>IKEA DIRIGERA</div>
                    <div style='font-size: 0.8em; color: #888;'>Custom Matter Bridge v${DRIVER_VERSION}</div>
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

void installed() { initialize() }
void updated() { initialize() }

void initialize() {
    log.warn "ANIVA BRIDGE: Initializing Driver Version ${DRIVER_VERSION}"
    if (logEnable) log.debug "ANIVA BRIDGE: Debug Logging is ENABLED."
    
    state.driverVersion = DRIVER_VERSION
    if (device.currentValue("_version") != DRIVER_VERSION) {
        sendEvent(name: "_version", value: DRIVER_VERSION)
    }
}

void configure() {
    log.warn "ANIVA BRIDGE: Configure called"
    refresh()
}

// --- DISPATCHER ---

void parse(String description) {
    try {
        Map descMap = matter.parseDescriptionAsMap(description)
        
        if (logEnable) log.debug "STEP 1 - Raw Map: ${descMap}"
    
        // FIX: Check for 'endpoint' OR 'endpointId'
        String endpointId = descMap.endpointId ?: descMap.endpoint
        
        if (!endpointId) {
             // System messages often have no endpoint, so we skip them gracefully
             return 
        }
    
        // STEP 2: Calculate DNI
        String childDni = "${device.deviceNetworkId}-${endpointId}"
        def childDevice = getChildDevice(childDni)
        
        if (childDevice) {
            if (logEnable) log.debug "STEP 3 - Found Child: ${childDevice.displayName}"
            
            List events = convertToEvents(descMap)
            
            if (events && events.size() > 0) {
                if (logEnable) log.debug "STEP 4 - Dispatching to Child: ${events}"
                childDevice.parse(events)
            } else {
                if (logEnable) log.warn "STEP 4 - Failed: No events generated (Cluster: ${descMap.clusterInt})"
            }
        } else {
            if (logEnable) {
                log.warn "STEP 3 - FAIL: Child NOT found!"
                log.warn "   > Looking for DNI: '${childDni}'"
            }
        }
    } catch (e) {
        log.error "FATAL CRASH IN PARSE: ${e}"
    }
}

List convertToEvents(Map map) {
    List events = []
    try {
        int cluster = map.clusterInt ?: Integer.parseInt(map.cluster ?: "0", 16)
        int attr = map.attrInt ?: Integer.parseInt(map.attrId ?: "0", 16)
        
        // Contact Sensor: 0x0045
        if (cluster == 0x0045 && attr == 0x0000) {
            String val = map.value
            String status = (val == "00" || val == "0") ? "open" : "closed"
            events.add([name: "contact", value: status])
        }
        // Battery: 0x002F
        else if (cluster == 0x002F && attr == 0x000C) {
            int rawValue = Integer.parseInt(map.value, 16)
            events.add([name: "battery", value: rawValue])
        }
    } catch (e) {
        log.error "Error converting events: ${e}"
    }
    return events
}

void refresh() {
    log.info "Bridge Refreshing..."
}

void componentRefresh(def childDevice) {
    if (txtEnable) log.info "Refresh requested by child: ${childDevice.displayName}"
    
    try {
        String dni = childDevice.deviceNetworkId
        if (!dni || !dni.contains("-")) return
        
        String epHex = dni.split("-").last()
        int ep = Integer.parseInt(epHex, 16)
        
        List<String> cmds = []
        cmds.add(matter.readAttribute(ep, 0x0045, 0x0000)) 
        cmds.add(matter.readAttribute(ep, 0x002F, 0x000C)) 
        
        sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.MATTER))
    } catch (e) {
        log.error "Error in componentRefresh: ${e}"
    }
}