/**
 * PC Sleep and Wake-on-LAN Controller
 *
 * Description: Multi-target PC deep sleep and Wake-on-LAN controller for Hubitat Elevation.
 * Controls Windows 11 (S3 Sleep) and Linux (S5 Graceful Shutdown / S3 Suspend) targets
 * over authenticated HTTP REST micro-agents, and wakes them using native Wake-on-LAN (Magic Packet).
 *
 * Features:
 * - Direct Switch capability ("Silent Night" / "Sleep Lab" mode).
 * - Individual and group sleep/wake controls (ai-box, tv-box).
 * - Pre-flight safety interlock reporting (e.g. 3D printer running, SSH active).
 * - Async HTTP calls (non-blocking for Hubitat thread safety).
 * - Live presence detection via network ping.
 * - Optional PiKVM v2 ATX pulse fallback.
 *
 * Author: Aniva
 * Version: 1.0.0
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "1.0.0"

metadata {
    definition(name: "PC Sleep and Wake Controller", namespace: "aniva", author: "Aniva") {
        capability "Switch"
        capability "Actuator"
        capability "Refresh"
        capability "Initialize"

        // Group commands
        command "sleepBoth"
        command "wakeBoth"

        // Target-specific commands
        command "sleepAiBox"
        command "wakeAiBox"
        command "sleepTvBox"
        command "wakeTvBox"

        // Advanced / Fallback
        command "pulsePikvmAtx"

        // State attributes
        attribute "aiBoxPresence", "string"    // "online", "sleeping", "shutdown", "offline", "interlock_tripped"
        attribute "tvBoxPresence", "string"    // "online", "sleeping", "offline"
        attribute "safetyStatus", "string"     // "Clear", or details of tripped interlock (e.g. "3D print active")
        attribute "lastAction", "string"       // Human readable timestamp of last action
        attribute "_version", "string"
    }

    preferences {
        input(
            name: "about",
            type: "paragraph",
            element: "paragraph",
            title: "",
            description: """
            <div style='display: flex; align-items: center; justify-content: space-between; padding: 10px; border: 1px solid #e0e0e0; border-radius: 5px; background: #fafafa; margin-bottom: 10px;'>
                <div>
                    <div style='font-weight: bold; font-size: 1.1em; color: #1e293b;'>PC Sleep and Wake Controller</div>
                    <div style='font-size: 0.8em; color: #64748b;'>Multi-Host Silent Night Automation v${driverVersion()}</div>
                </div>
                <div style='text-align: right; font-size: 0.8em; line-height: 1.4;'>
                    <a href='https://github.com/aniva/hubitat01/tree/master/PCSleepController' target='_blank' style='color: #0275d8; text-decoration: none;'>View on GitHub</a><br>
                    <a href='https://paypal.me/AndreiIvanov420' target='_blank' style='color: #0275d8; text-decoration: none;'>Support Dev</a>
                </div>
            </div>"""
        )

        input "switchBehavior", "enum",
            title: "Switch Action Mapping",
            options: [
                "sleepOn": "Switch ON = Deep Sleep (Silent Night), Switch OFF = Wake (Normal)",
                "wakeOn": "Switch ON = Wake (Normal), Switch OFF = Deep Sleep (Silent Night)"
            ],
            defaultValue: "sleepOn",
            required: true

        // --- Target 1: Linux Server (e.g. ai-box) ---
        input "aiBoxEnabled", "bool", title: "Enable Target 1 (Linux) Control", defaultValue: true
        input "aiBoxIp", "string", title: "Target 1 IP Address", defaultValue: "192.168.1.50"
        input "aiBoxPort", "number", title: "Target 1 Agent Port", defaultValue: 8099
        input "aiBoxMac", "string", title: "Target 1 MAC Address (for WoL)", defaultValue: "AA:BB:CC:DD:EE:01"
        input "aiBoxToken", "password", title: "Target 1 Shared Secret Token", defaultValue: ""

        // --- Target 2: Windows PC (e.g. tv-box) ---
        input "tvBoxEnabled", "bool", title: "Enable Target 2 (Windows) Control", defaultValue: true
        input "tvBoxIp", "string", title: "Target 2 IP Address", defaultValue: "192.168.1.60"
        input "tvBoxPort", "number", title: "Target 2 Agent Port", defaultValue: 8099
        input "tvBoxMac", "string", title: "Target 2 MAC Address (for WoL)", defaultValue: "AA:BB:CC:DD:EE:02"
        input "tvBoxToken", "password", title: "Target 2 Shared Secret Token", defaultValue: ""

        // --- Advanced: PiKVM v2 Fallback ---
        input "pikvmEnabled", "bool", title: "Enable PiKVM v2 ATX Pulse Fallback (Linux)", defaultValue: false
        input "pikvmIp", "string", title: "PiKVM IP Address", defaultValue: "192.168.1.70"
        input "pikvmUser", "string", title: "PiKVM Username", defaultValue: "admin"
        input "pikvmPass", "password", title: "PiKVM Password"

        // --- Health & Logging ---
        input "pingInterval", "enum",
            title: "Presence Ping Interval",
            options: [
                "0": "Disabled",
                "1": "Every 1 minute",
                "5": "Every 5 minutes",
                "15": "Every 15 minutes"
            ],
            defaultValue: "5"

        input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: true
        input "txtEnable", "bool", title: "Enable Description Text", defaultValue: true
    }
}

static String driverVersion() { return DRIVER_VERSION }

def installed() {
    logInfo "Installing PC Sleep and Wake Controller v${driverVersion()}..."
    initialize()
}

def updated() {
    logInfo "Updating preferences..."
    unschedule()
    initialize()
}

def initialize() {
    sendEvent(name: "_version", value: driverVersion())
    sendEvent(name: "safetyStatus", value: "Clear")
    
    if (device.currentValue("switch") == null) {
        sendEvent(name: "switch", value: (switchBehavior == "wakeOn" ? "on" : "off"))
    }

    setupSchedule()
    refresh()
}

def setupSchedule() {
    unschedule(refresh)
    String interval = pingInterval ?: "5"
    switch (interval) {
        case "1":
            runEvery1Minute(refresh)
            break
        case "5":
            runEvery5Minutes(refresh)
            break
        case "15":
            runEvery15Minutes(refresh)
            break
        default:
            logDebug "Presence ping schedule disabled."
            break
    }
}

// --- Standard Switch Capabilities ---

def on() {
    if (switchBehavior == "wakeOn") {
        wakeBoth()
    } else {
        sleepBoth()
    }
}

def off() {
    if (switchBehavior == "wakeOn") {
        sleepBoth()
    } else {
        wakeBoth()
    }
}

// --- High-Level Sleep / Wake Operations ---

def sleepBoth() {
    logInfo "Executing Sleep Both command..."
    updateSwitchEvent("sleep")
    if (aiBoxEnabled) sleepAiBox()
    if (tvBoxEnabled) sleepTvBox()
}

def wakeBoth() {
    logInfo "Executing Wake Both command..."
    updateSwitchEvent("wake")
    if (aiBoxEnabled) wakeAiBox()
    if (tvBoxEnabled) wakeTvBox()
}

private void updateSwitchEvent(String action) {
    String swVal = (action == "sleep") ? 
        (switchBehavior == "wakeOn" ? "off" : "on") : 
        (switchBehavior == "wakeOn" ? "on" : "off")
    
    sendEvent(name: "switch", value: swVal, descriptionText: "PC Sleep Controller set to ${action.toUpperCase()}")
    sendEvent(name: "lastAction", value: "${action.capitalize()} triggered at ${new Date().format('yyyy-MM-dd HH:mm:ss')}")
}

// --- Target 1: ai-box ---

def sleepAiBox() {
    if (!aiBoxIp) {
        logWarn "ai-box IP is not configured."
        return
    }
    logInfo "Requesting graceful sleep/shutdown for ai-box (${aiBoxIp}:${aiBoxPort})..."
    
    Map params = [
        uri: "http://${aiBoxIp}:${aiBoxPort}/sleep",
        headers: [
            "Content-Type": "application/json",
            "Authorization": "Bearer ${aiBoxToken ?: ''}"
        ],
        body: [
            "client": "hubitat",
            "action": "shutdown_s5",
            "timestamp": now()
        ],
        timeout: 10
    ]
    
    asynchttpPost("handleAiBoxSleepResponse", params, [target: "ai-box"])
}

def handleAiBoxSleepResponse(response, data) {
    int status = response.status
    if (status == 200) {
        logInfo "ai-box accepted sleep request. Shutting down..."
        sendEvent(name: "aiBoxPresence", value: "shutdown", descriptionText: "ai-box shutting down gracefully")
        sendEvent(name: "safetyStatus", value: "Clear")
    } else if (status == 409) {
        String msg = response.errorData ?: response.data ?: "Interlock active"
        logWarn "ai-box REFUSED sleep command: Safety interlock tripped! (${msg})"
        sendEvent(name: "safetyStatus", value: "ai-box Interlock: ${msg}")
        sendEvent(name: "aiBoxPresence", value: "interlock_tripped")
    } else if (status == 401 || status == 403) {
        logWarn "ai-box sleep rejected: Authentication failed (Status ${status}). Check shared secret token."
        sendEvent(name: "safetyStatus", value: "ai-box Auth Error (${status})")
    } else {
        logWarn "ai-box sleep request returned HTTP ${status}: ${response.errorMessage ?: 'Unknown error'}"
    }
}

def wakeAiBox() {
    if (!aiBoxMac) {
        logWarn "ai-box MAC address is not configured."
        return
    }
    logInfo "Sending Wake-on-LAN Magic Packet to ai-box (${aiBoxMac})..."
    sendWoL(aiBoxMac)
    sendEvent(name: "aiBoxPresence", value: "waking", descriptionText: "Sent WoL Magic Packet to ai-box")
    
    if (pikvmEnabled && pikvmIp) {
        logDebug "PiKVM fallback is active; scheduling verification ping in 30s..."
        runIn(30, verifyAiBoxWake)
    }
}

def verifyAiBoxWake() {
    boolean online = checkPing(aiBoxIp)
    if (!online && pikvmEnabled) {
        logWarn "ai-box did not wake via WoL within 30s. Triggering PiKVM ATX pulse fallback..."
        pulsePikvmAtx()
    }
}

// --- Target 2: tv-box ---

def sleepTvBox() {
    if (!tvBoxIp) {
        logWarn "tv-box IP is not configured."
        return
    }
    logInfo "Requesting S3 deep sleep for tv-box (${tvBoxIp}:${tvBoxPort})..."
    
    Map params = [
        uri: "http://${tvBoxIp}:${tvBoxPort}/sleep",
        headers: [
            "Content-Type": "application/json",
            "Authorization": "Bearer ${tvBoxToken ?: ''}"
        ],
        body: [
            "client": "hubitat",
            "action": "suspend_s3",
            "timestamp": now()
        ],
        timeout: 10
    ]
    
    asynchttpPost("handleTvBoxSleepResponse", params, [target: "tv-box"])
}

def handleTvBoxSleepResponse(response, data) {
    int status = response.status
    if (status == 200) {
        logInfo "tv-box accepted sleep request. Entering S3 Standby..."
        sendEvent(name: "tvBoxPresence", value: "sleeping", descriptionText: "tv-box entering S3 Standby")
    } else if (status == 401 || status == 403) {
        logWarn "tv-box sleep rejected: Authentication failed (Status ${status}). Check shared secret token."
    } else {
        logWarn "tv-box sleep request returned HTTP ${status}: ${response.errorMessage ?: 'Unknown error'}"
    }
}

def wakeTvBox() {
    if (!tvBoxMac) {
        logWarn "tv-box MAC address is not configured."
        return
    }
    logInfo "Sending Wake-on-LAN Magic Packet to tv-box (${tvBoxMac})..."
    sendWoL(tvBoxMac)
    sendEvent(name: "tvBoxPresence", value: "waking", descriptionText: "Sent WoL Magic Packet to tv-box")
}

// --- Wake-on-LAN Protocol Helper ---

private void sendWoL(String macAddress) {
    if (!macAddress) return
    String cleanMac = macAddress.replaceAll("[: -]", "").toUpperCase()
    if (cleanMac.length() != 12) {
        logWarn "Invalid MAC address: ${macAddress}. Expected 12 hexadecimal characters."
        return
    }
    
    try {
        def hubAction = new hubitat.device.HubAction(
            "wake on lan ${cleanMac}",
            hubitat.device.HubAction.Type.LAN
        )
        sendHubCommand(hubAction)
        logDebug "Dispatched HubAction WoL packet to ${cleanMac}"
    } catch (Exception e) {
        logWarn "Failed to dispatch WoL command: ${e.message}"
    }
}

// --- PiKVM v2 REST ATX Pulse Fallback ---

def pulsePikvmAtx() {
    if (!pikvmIp) {
        logWarn "PiKVM IP not set."
        return
    }
    logInfo "Executing PiKVM ATX power button pulse at https://${pikvmIp}/api/atx/power..."
    
    Map headers = ["Content-Type": "application/json"]
    if (pikvmUser && pikvmPass) {
        String auth = "${pikvmUser}:${pikvmPass}".bytes.encodeBase64().toString()
        headers["Authorization"] = "Basic ${auth}"
    }
    
    Map params = [
        uri: "https://${pikvmIp}/api/atx/power",
        headers: headers,
        body: ["action": "pulse"],
        ignoreSSLIssues: true,
        timeout: 10
    ]
    
    asynchttpPost("handlePikvmResponse", params)
}

def handlePikvmResponse(response, data) {
    if (response.status == 200) {
        logInfo "PiKVM ATX power pulse successfully dispatched."
    } else {
        logWarn "PiKVM ATX pulse returned HTTP ${response.status}: ${response.errorMessage ?: 'Error'}"
    }
}

// --- Health Check / Presence Refresh ---

def refresh() {
    logDebug "Refreshing presence states for configured PCs..."
    
    if (aiBoxEnabled && aiBoxIp) {
        boolean aiOnline = checkPing(aiBoxIp)
        String current = device.currentValue("aiBoxPresence")
        if (aiOnline) {
            sendEvent(name: "aiBoxPresence", value: "online")
        } else if (current != "shutdown" && current != "sleeping") {
            sendEvent(name: "aiBoxPresence", value: "offline")
        }
    }
    
    if (tvBoxEnabled && tvBoxIp) {
        boolean tvOnline = checkPing(tvBoxIp)
        String current = device.currentValue("tvBoxPresence")
        if (tvOnline) {
            sendEvent(name: "tvBoxPresence", value: "online")
        } else if (current != "sleeping") {
            sendEvent(name: "tvBoxPresence", value: "offline")
        }
    }
}

private boolean checkPing(String ip) {
    if (!ip) return false
    try {
        hubitat.helper.NetworkUtils.PingData pingData = hubitat.helper.NetworkUtils.ping(ip, 1)
        return (pingData != null && pingData.packetsReceived > 0)
    } catch (Exception e) {
        logDebug "Ping check exception for ${ip}: ${e.message}"
        return false
    }
}

// --- Logging Helpers ---

private void logInfo(msg) {
    if (txtEnable) log.info "[PC-Sleep] ${msg}"
}

private void logDebug(msg) {
    if (logEnable) log.debug "[PC-Sleep] ${msg}"
}

private void logWarn(msg) {
    log.warn "[PC-Sleep] ${msg}"
}
