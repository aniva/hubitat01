/**
 * Virtual Smart Battery (Mutable)
 *
 * Description: A virtual driver that simulates battery drain for devices that only report binary "OK/Low" status.
 * Features:
 * - Simulates gradual percentage drop over time.
 * - "Time Machine": Select a past installation date to instantly recalculate current depletion.
 * - "Learns" actual battery life by tracking OK -> Low cycles.
 * - Configurable steps and thresholds.
 * - Aniva Standard UI (Fixed CSS Background).
 *
 * Author: Aniva
 * Version: 2.1.6
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "2.1.6"

metadata {
    definition (name: "Virtual Smart Battery", namespace: "aniva", author: "Aniva") {
        capability "Battery"
        capability "Actuator"
        capability "Initialize"
        capability "Refresh"
        
        // Primary command for Rule Machine (OK vs Low)
        command "setSourceStatus", [[name:"status", type: "STRING", description: "Status from real device (OK or Low)"]]
        
        // Manual override/maintenance
        command "resetBatteryReplacementDate"
        command "setBattery", [[name:"percentage", type: "NUMBER"]]
        
        // Tracking attributes
        attribute "cycleState", "string" // "Charging", "Discharging", "Low"
        attribute "estimatedDays", "number"
        attribute "batteryInstalled", "string"
        attribute "_version", "string"
    }

    preferences {
        // ANIVA STANDARD HEADER (CSS Background Method)
        input(
            name: 'about', 
            type: 'paragraph', 
            element: 'paragraph', 
            title: '', 
            description: '''
            <div style="min-height:55px; background:transparent url('data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgd2lkdGg9IjQ4cHgiIGhlaWdodD0iNDhweCI+PHBhdGggZD0iTTAgMGgyNHYyNEgweiIgZmlsbD0ibm9uZSIvPjxwYXRoIGQ9Ik0xNS42NyA0SDE0VjJoLTR2Mkg4LjMzQzcuNiA0IDcgNC42IDcgNS4zM3YxNS4zM0M3IDIxLjQgNy42IDIyIDguMzMgMjJoNy4zM2MuNzQgMCAxLjM0LS42IDEuMzQtMS4zM1Y1LjMzQzE3IDQuNiAxNi40IDQgMTUuNjcgNHoiIGZpbGw9IiM0Q0FGNTAiLz48L3N2Zz4=') no-repeat left center; background-size:50px 50px; border: 1px solid #e0e0e0; border-radius: 5px; background-color: #fafafa; padding: 10px 10px 10px 80px;">
                <div style="font-weight: bold; font-size: 1.1em; color: #333;">Virtual Smart Battery</div>
                <div style="font-size: 0.8em; color: #888;">Simulator v''' + driverVersion() + '''</div>
                <div style="font-size: 0.8em; margin-top: 5px;">
                    <a href="https://github.com/aniva/hubitat01/tree/master/VirtualBattery" target="_blank">View GitHub</a> | 
                    <a href="https://paypal.me/AndreiIvanov420" target="_blank">Support Dev</a>
                </div>
            </div>
            '''
        )

        input(
            name: 'batteryInstalledDate', 
            type: 'date', 
            title: 'Last Replacement Date', 
            description: 'Select date to simulate drain from past', 
            required: false
        )

        input(
            name: 'expectedLife', 
            type: 'number', 
            title: 'Expected Battery Life (Days)', 
            defaultValue: 90, 
            required: true
        )

        input(
            name: 'lowThreshold', 
            type: 'number', 
            title: 'Low Battery Threshold (%)', 
            description: "Level to jump to when 'Low' is reported", 
            defaultValue: 15, 
            range: '1..50'
        )

        input(
            name: 'simSteps', 
            type: 'number', 
            title: 'Decrement Intervals (Steps)', 
            description: 'How many times to drop % during lifetime (5-20)', 
            defaultValue: 10, 
            range: '5..20'
        )
        
        input(
            name: 'useLearned', 
            type: 'bool', 
            title: 'Use Learned Lifetime?', 
            description: 'If enabled, driver uses historical average instead of Expected Life', 
            defaultValue: false
        )

        input(
            name: 'maxCycles', 
            type: 'number', 
            title: 'Learning Memory (Cycles)', 
            defaultValue: 3, 
            range: '1..10'
        )
        
        input(
            name: 'logEnable', 
            type: 'bool', 
            title: 'Enable Debug Logging', 
            defaultValue: true
        )

        input(
            name: 'txtEnable', 
            type: 'bool', 
            title: 'Enable Description Text', 
            defaultValue: true
        )
    }
}

// --- VERSIONING & IDENTITY ---

def driverVersion() { return DRIVER_VERSION }

void initialize() {
    state.driverVersion = driverVersion()
    sendEvent(name: "_version", value: driverVersion())
    
    if (!state.cycleHistory) state.cycleHistory = []
    
    logInfo("Initializing ${device.displayName} (Driver v${driverVersion()})")
    
    if (logEnable) runIn(1800, logsOff)
    
    // Recalculate state on init
    refresh()
}

void updated() {
    refresh()
}

// --- PRIMARY COMMANDS ---

void refresh() {
    state.driverVersion = driverVersion()
    logDebug("Refresh called. Recalculating state based on installed date...")
    
    // 1. Determine Start Date
    Date startDate = new Date() // Default to now
    if (batteryInstalledDate) {
        try {
            startDate = Date.parse("yyyy-MM-dd", batteryInstalledDate)
        } catch (e) {
            log.warn "Failed to parse installed date preference: ${e}"
        }
    }
    
    // Update attribute to match preference
    String dateStr = startDate.format("yyyy-MM-dd", location.timeZone)
    if (device.currentValue("batteryInstalled") != dateStr) {
        sendEvent(name: "batteryInstalled", value: dateStr)
    }
    state.cycleStart = startDate.time
    
    // 2. Determine Duration Target
    long targetDays = expectedLife
    if (useLearned && state.avgLifetimeDays) {
        targetDays = state.avgLifetimeDays as long
        logInfo("Using Learned Lifetime: ${targetDays} days")
    }
    sendEvent(name: "estimatedDays", value: targetDays)
    
    // 3. Calculate Simulation Parameters
    setupSimulationConfig(targetDays)
    
    // 4. Calculate Current Position in Time
    long nowTime = new Date().time
    long elapsedMillis = nowTime - startDate.time
    double elapsedDays = elapsedMillis / (1000.0 * 60 * 60 * 24) // Force double math
    
    if (elapsedDays < 0) elapsedDays = 0 // Future date protection
    
    logDebug("Elapsed time since install: ${elapsedDays.round(2)} days. Target: ${targetDays} days.")
    
    // 5. Calculate Expected Level
    int maxLevel = 100
    int minLevel = lowThreshold as int
    int capacityRange = maxLevel - minLevel
    
    if (elapsedDays >= targetDays) {
        // Time expired
        if (device.currentValue("cycleState") != "Low") {
            logInfo("Time expired based on installation date. Setting to Low Threshold.")
            sendEvent(name: "battery", value: minLevel, unit: "%") // Added unit
            sendEvent(name: "cycleState", value: "Discharging") 
        }
    } else {
        // Calculate % drop
        double percentConsumed = elapsedDays / (double)targetDays
        int dropAmount = (capacityRange * percentConsumed).toInteger()
        int newLevel = maxLevel - dropAmount
        
        // Safety floor
        if (newLevel <= minLevel) newLevel = minLevel + 1
        
        logInfo("Recalculated Battery: ${newLevel}% based on ${elapsedDays.round(1)}/${targetDays} days.")
        sendEvent(name: "battery", value: newLevel, unit: "%") // Added unit
        sendEvent(name: "cycleState", value: "Discharging")
        
        // Restart Scheduler
        runIn(state.simInterval, decrementBattery)
    }
}

void setSourceStatus(String status) {
    String cleanStatus = status.trim().toLowerCase()
    boolean isLow = ["low", "bad", "replace", "critical"].any { cleanStatus.contains(it) }
    
    if (isLow) {
        handleLowBattery()
    } else {
        if (device.currentValue("cycleState") == "Low" || device.currentValue("cycleState") == null) {
            resetBatteryReplacementDate()
        } else {
            if (logEnable) log.debug "Received OK status, but cycle is already active. Ignoring."
        }
    }
}

void resetBatteryReplacementDate() {
    logInfo("Battery Reset triggered. Updating date to Today.")
    
    // 1. Update the Date Input Preference to Today
    String today = new Date().format("yyyy-MM-dd", location.timeZone)
    device.updateSetting("batteryInstalledDate", [value: today, type: "date"])
    
    // 2. IMPORTANT: Force logic reset immediately so we don't wait for refresh
    state.cycleStart = new Date().time
    sendEvent(name: "batteryInstalled", value: today)
    sendEvent(name: "battery", value: 100, unit: "%") // Added unit
    sendEvent(name: "cycleState", value: "Discharging")
    
    // 3. Trigger full refresh to start scheduler
    runIn(1, refresh)
}

void setBattery(BigDecimal level) {
    logInfo("Manual Battery Override: ${level}%")
    sendEvent(name: "battery", value: level.toInteger(), unit: "%") // Added unit
}

// --- LOGIC ENGINE ---

void handleLowBattery() {
    if (device.currentValue("cycleState") == "Low") return // Already low
    
    logInfo("Low Battery Reported by Source! Ending Cycle.")
    sendEvent(name: "cycleState", value: "Low")
    sendEvent(name: "battery", value: lowThreshold, unit: "%") // Added unit
    
    // Stop simulation
    unschedule("decrementBattery")
    
    // Learn!
    calculateLearning()
}

void calculateLearning() {
    if (!state.cycleStart) return
    
    long endTime = now()
    long durationMillis = endTime - state.cycleStart
    BigDecimal days = (durationMillis / (1000 * 60 * 60 * 24)).setScale(1, BigDecimal.ROUND_HALF_UP)
    
    logInfo("Cycle finished. Actual Duration: ${days} days.")
    
    List history = state.cycleHistory ?: []
    history.add(days)
    
    int limit = (maxCycles ?: 3) as int
    if (history.size() > limit) history = history.takeRight(limit)
    state.cycleHistory = history
    
    BigDecimal sum = 0
    history.each { sum += it }
    BigDecimal avg = (sum / history.size()).setScale(0, BigDecimal.ROUND_HALF_UP)
    
    state.avgLifetimeDays = avg
    logInfo("Updated Learning Logic. New Average Lifetime: ${avg} days (History: ${history})")
}

// --- SIMULATION SCHEDULER ---

void setupSimulationConfig(long daysDuration) {
    int steps = (simSteps ?: 10) as int
    if (steps < 5) steps = 5
    if (steps > 20) steps = 20
    
    // Logic: Calculate drop per step
    int targetMin = (lowThreshold as int) + 5
    int totalDrop = 100 - targetMin
    int dropPerStep = (totalDrop / steps).toInteger()
    
    // Calculate Time Interval (in seconds) between drops
    long totalSeconds = daysDuration * 24 * 60 * 60
    long secondsPerStep = (totalSeconds / steps).toLong()
    if (secondsPerStep < 60) secondsPerStep = 60 
    
    state.dropAmount = dropPerStep
    state.simInterval = secondsPerStep
}

void decrementBattery() {
    int current = device.currentValue("battery") ?: 100
    int drop = state.dropAmount ?: 5
    int nextLevel = current - drop
    
    int floor = (lowThreshold as int) + 1
    if (nextLevel < floor) nextLevel = floor
    
    if (nextLevel != current) {
        logInfo("Simulating drain... Battery now ${nextLevel}%")
        sendEvent(name: "battery", value: nextLevel, unit: "%") // Added unit
        
        if (nextLevel > floor) {
            runIn(state.simInterval, decrementBattery)
        } else {
            logInfo("Simulation floor reached. Waiting for real device to report 'Low'.")
        }
    }
}

// --- LOGGING HELPERS ---

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