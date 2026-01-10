/**
 * IKEA DIRIGERA Bridge (Custom Parent)
 *
 * Replaces the built-in "Generic Matter Bridge" to allow custom child drivers.
 * Acts as a "Dispatcher," routing Matter events to the correct child device.
 *
 * Author: Aniva
 */

metadata {
    definition (name: "IKEA DIRIGERA Bridge", namespace: "aniva", author: "Aniva") {
        capability "Initialize"
        capability "Refresh"
        capability "Configuration"
        
        // This is the magic fingerprint for the Dirigera Hub
        fingerprint deviceId: "0010", vendor: "IKEA of Sweden", model: "DIRIGERA Hub", controllerType: "MAT"
    }
}

void installed() { initialize() }
void updated() { initialize() }

void initialize() {
    log.info "Initializing Dirigera Bridge..."
    // Ensure we are subscribed to the bridge's system events if needed
    refresh()
}

// --- THE DISPATCHER (The Heart of the Bridge) ---

void parse(String description) {
    // 1. Decode the Raw Matter Message
    Map descMap = matter.parseDescriptionAsMap(description)
    
    if (logEnable) log.debug "BRIDGE Received: ${descMap}"

    // 2. Extract the Endpoint (The "Address" of the child sensor)
    // Matter endpoints are usually hex strings in the map (e.g. "01", "17")
    String endpointId = descMap.endpointId
    
    if (!endpointId) {
        // Some messages are for the hub itself (Endpoint 00)
        return 
    }

    // 3. Find the Child Device
    // The DNI (Device Network ID) is typically "HubID-EndpointID" (e.g. "M3012-17")
    // We need to construct it carefully.
    String childDni = "${device.deviceNetworkId}-${endpointId}"
    
    def child = getChildDevice(childDni)
    
    // 4. If Child Exists, Forward the Data
    if (child) {
        // We convert the raw map into a nice Event List for the component driver
        List events = convertToEvents(descMap)
        if (events) {
            child.parse(events) // <-- We FORCE the data into the child
        }
    } else {
        log.warn "Bridge received data for unknown child: ${childDni}. Run 'Refresh' to discover devices."
    }
}

// --- TRANSLATOR (Raw Matter -> Hubitat Events) ---
List convertToEvents(Map map) {
    List events = []
    
    // CONTACT SENSOR (Cluster 0045, Attribute 0000)
    if (map.cluster == "0045" && map.attrId == "0000") {
        String val = map.value
        String status = (val == "00" || val == "0") ? "open" : "closed"
        events.add([name: "contact", value: status])
    }
    
    // BATTERY (Cluster 002F, Attribute 000C)
    else if (map.cluster == "002F" && map.attrId == "000C") {
        int raw = Integer.parseInt(map.value, 16)
        // Pass RAW value to child; let child do the /2 math
        events.add([name: "battery", value: raw])
    }
    
    return events
}

// --- CHILD MANAGEMENT ---

// This method is called when you click "Refresh"
void refresh() {
    log.info "Bridge Refreshing..."
    
    // In a full implementation, we would query the Endpoints list here.
    // For now, we rely on the fact that the children are already created.
}

// This allows the Child to ask for a refresh (called via parent.componentRefresh)
void componentRefresh(def childDevice) {
    log.info "Child ${childDevice} asked for refresh"
    
    // Extract Endpoint from Child DNI
    String ep = childDevice.deviceNetworkId.split("-").last()
    
    // Send Read Attribute Commands to the Matter Network
    List<String> cmds = []
    cmds.add(matter.readAttribute(ep, "0045", "0000")) // Contact
    cmds.add(matter.readAttribute(ep, "002F", "000C")) // Battery
    
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.MATTER))
}