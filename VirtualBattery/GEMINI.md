# Virtual Battery for Hubitat

Driver designed to be using in the Hubitat's Rule Machine to set this virtual driver battery level based on that device attribute value. For examle source device can report battery status as "high" or "ok" or "good" when battery is high and then report "low" and "critical" when battery is depelted, and this can be tranlated to levels set to this virtual battery, e.g. "good" would set to 100%, low would be set to 10%. This wil allow displaying battery tile on the HE dashboard that will stay green while battery is good and will turn red as soon as device reports low battery status. Essentially this enables stanard battery tile for hubitat for devices that do not support battery status reporting in percentage and or do not expose battery state propely.

## Features
* **Aniva Standard Styling:** Custom HTML header with version tracking and links.

## Architecture & Network Requirements

TBD

## Installation

1.  **Hubitat Package Manager (HPM):** Search for "aniva", this will bring all drivers in aniva's namespace and then select Virtual Battery for installation

2.  **Manual Install:**
    * Go to **Drivers Code** in Hubitat.
    * Click **New Driver**.
    * Paste the content of `VirtualBattery.groovy`.
    * Click **Save**.

## 3. Naming & Attribute Standards (STRICT)
State variable naming should use **camelCase** to align with Hubitat standards. 
This diver should use standard generic attribute name "battery" and setBattery(level) that can be used to set level (in %) from rule engine using generic Actuator device
')
# 2. Coding & Driver Standards (Aniva Standard)

All drivers must adhere to the **"Aniva Standard Pattern"** for consistency in updates, debugging, and UI presentation.

## Versioning

Versioning should be handled by defining a static field @Field static final String DRIVER_VERSION at the top of the code for easy readability. This version string is then written to state.driverVersion inside the initialize() method, ensuring that the installed version is always trackable within the device's state for debugging or upgrade logic. Finally, the driver logs this version number as an info message upon initialization (logInfo), while standard debug logging (logEnable) is designed to automatically disable itself after 30 minutes to maintain hub performance.

### A. The "Version & Identity" Pattern
Every driver must implement the following 5 strict requirements:

1.  **Static Variable:** Define the version in a static field at the top of the script.
2.  **Accessor Function:** Create a `driverVersion()` function that returns this static field (allows dynamic insertion into Strings/HTML).
3.  **State Tracking:** In `initialize()`, write this version to `state.driverVersion` (for update tracking).
4.  **Logging:** Log the version immediately upon initialization.
5.  **UI Header:** Display the version dynamically inside a styled HTML paragraph block in `preferences`.

**Standard Implementation Template:**

```groovy
import groovy.transform.Field

// 1. Static Variable
@Field static final String DRIVER_VERSION = "1.0.0"

metadata {
    definition (name: "My Device Name", namespace: "aniva", author: "Aniva") {
        // ... capabilities ...
    }

    preferences {
        // 5. UI Header (Standard Paragraph Block)
        // Must use ${driverVersion()} to dynamically show the version
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
    sendEvent(name: "_version", value: driverVersion()) // Optional: for dashboard display

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

## Support

If you find this driver useful, consider supporting the development:

* [PayPal Support](https://paypal.me/AndreiIvanov420)
* [GitHub Repository](https://github.com/aniva)

---
**Author:** Aniva
**License:** Apache 2.0