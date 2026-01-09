# GEMINI.md - Project Context & Coding Standards

**Project:** Virtual Mutable Battery Driver
**Platform:** Hubitat Elevation
**Author:** Aniva
**License:** Apache 2.0
**Support:** [PayPal](https://paypal.me/AndreiIvanov420) | [GitHub](https://github.com/aniva)

## 1. Project Overview: Virtual Mutable Battery
This driver is designed to bridge devices that report battery status vaguely (e.g., "High", "OK", "Low") into a standard percentage-based format that Hubitat dashboards can understand.

**Problem Solved:** Many cameras or cloud devices do not report a numeric battery level (0-100%), preventing them from using standard Hubitat battery tiles.
**Solution:** This virtual driver acts as a proxy. Rule Machine logic translates the source device's status (e.g., "Low") into a numeric value (e.g., "15%") and sets it on this driver using the `setBattery()` command. This allows the use of standard, color-changing battery tiles on dashboards.

### Key Features
* **Rule Machine Compatible:** Explicitly exposes the `Actuator` capability so it appears in Rule Machine action lists.
* **Custom Command:** `setBattery(level)` allows direct percentage assignment.
* **Aniva Standard Styling:** Includes the standardized HTML header and version tracking.

## 2. Coding & Driver Standards (Aniva Standard)

All drivers must adhere to the **"Aniva Standard Pattern"** for consistency in updates, debugging, and UI presentation.

### A. Naming & Attribute Standards (STRICT)
* **Variable Naming:** Use **camelCase** for all state variables and methods.
* **Capabilities:** Must include `Actuator` to ensuring visibility in Rule Machine.
* **Attributes:** Use standard system attributes where possible (e.g., `battery`).

### B. The "Version & Identity" Pattern
Every driver must implement the following 5 strict requirements:

1.  **Static Variable:** Define the version in a static field at the top of the script.
2.  **Accessor Function:** Create a `driverVersion()` function that returns this static field.
3.  **State Tracking:** In `initialize()`, write this version to `state.driverVersion`.
4.  **Logging:** Log the version immediately upon initialization.
5.  **UI Header:** Display the version dynamically inside a styled HTML paragraph block in `preferences`.

**Standard Implementation Template:**

```groovy
import groovy.transform.Field

// 1. Static Variable
@Field static final String DRIVER_VERSION = "1.0.0"

metadata {
    definition (name: "My Device Name", namespace: "aniva", author: "Aniva") {
        capability "Actuator" // Critical for Rule Machine visibility
        // ... other capabilities ...
    }

    preferences {
        // 5. UI Header (Standard Paragraph Block)
        input name: "about", type: "paragraph", element: "paragraph", title: "", description: """
        <div style='display: flex; align-items: center; justify-content: space-between; padding: 10px; border: 1px solid #e0e0e0; border-radius: 5px; background: #fafafa; margin-bottom: 10px;'>
            <div style='display: flex; align-items: center;'>
                <img src='https://raw.githubusercontent.com/aniva/hubitat01/master/MyProject/icon.png' 
                     style='height: 50px; width: 50px; object-fit: contain; margin-right: 15px;'>
                <div>
                    <div style='font-weight: bold; font-size: 1.1em; color: #333;'>DEVICE NAME</div>
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

// 2. Accessor Function
def driverVersion() { return DRIVER_VERSION }

void initialize() {
    // 3. State Tracking
    state.driverVersion = driverVersion()
    sendEvent(name: "_version", value: driverVersion())

    // 4. Log upon Init
    logInfo("Initializing ${device.displayName} (Driver v${driverVersion()})")
    
    if (logEnable) runIn(1800, logsOff)
}

// Standard Logging Helpers
void logInfo(String msg) {
    if (txtEnable) log.info "${device.displayName}: ${msg}"
}

void logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "${device.displayName}: Debug logging auto-disabled"
}
```

## 3. Installation Guide

1.  **Hubitat Package Manager (HPM):**
    * Search for "Aniva" in the HPM store.
    * Select **"Virtual Mutable Battery"** for installation.

2.  **Manual Install:**
    * Go to **Drivers Code** in Hubitat.
    * Click **New Driver**.
    * Paste the content of `VirtualBattery.groovy`.
    * Click **Save**.