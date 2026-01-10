# GEMINI.md - Project Context & Coding Standards

**Project:** Hubitat Drivers for IKEA DIRIGERA Hub & Zigbee Sensors
**Platform:** Hubitat Elevation
**Author:** Aniva
**License:** Apache 2.0
**Support:** [PayPal](https://paypal.me/AndreiIvanov420) | [GitHub](https://github.com/aniva)

## 1. Project Overview

This project enables full functionality for IKEA Zigbee sensors (like PARASOLL) when connected via the IKEA DIRIGERA Hub (Matter Bridge) to Hubitat Elevation.

**The Problem:**
Hubitat's built-in "Generic Matter Bridge" driver is restrictive; it filters out unknown child devices and does not handle IKEA's specific battery reporting (0-200 scale) or logic quirks.

**The Solution:**
We implement a **Custom Parent/Child Architecture**:
1.  **Parent Driver ("IKEA DIRIGERA Bridge"):** Acts as a raw "Dispatcher." It bypasses standard filters, decodes raw Matter messages (Integer vs Hex), and routes events to the correct Child Device Network ID (DNI).
2.  **Component Driver ("IKEA PARASOLL Zigbee Sensor"):** A dedicated child driver that receives raw data, corrects the battery percentage (Value / 2), and allows for logic reversal (Open/Close swap).

## 2. Coding & Driver Standards (Aniva Standard)

All drivers in this project must adhere to the **"Aniva Standard Pattern"** for consistency, reliability, and ease of debugging.

### A. AI Generation Rules (Markdown)
* **Code Blocks:** When generating Markdown files (like README.md) that contain code examples, **never** use triple backticks inside the block. Substitute internal code fences with **triple single quotes (`'''`)** to prevent rendering errors.

### B. Naming & Attribute Standards
* **Variable Naming:** Use **camelCase** for all state variables and methods.
* **Capabilities:** Must include `Actuator` to ensure visibility in Rule Machine.
* **Attributes:** Use standard system attributes (e.g., `battery`, `contact`) wherever possible.

### C. The "Version & Identity" Pattern
Every driver must implement these 5 strict requirements to ensure users can track updates:

1.  **Static Variable:** Define the version in a `@Field static final String` at the top.
2.  **Accessor Function:** Create a `driverVersion()` function returning this field.
3.  **State Tracking:** In `initialize()`, write this version to `state.driverVersion` and `device.currentValue("_version")`.
4.  **Logging:** Log the version immediately upon initialization (using `logInfo`).
5.  **UI Header:** Display the version dynamically inside a styled HTML paragraph block in `preferences`.

**Standard Implementation Template:**

```groovy
import groovy.transform.Field

@Field static final String DRIVER_VERSION = "1.0.0"

metadata {
    definition (name: "My Device", namespace: "aniva", author: "Aniva") {
        capability "Initialize"
        capability "Actuator"
    }

    preferences {
        // Aniva Standard Header
        input name: "about", type: "paragraph", element: "paragraph", title: "", description: """
        <div style='display: flex; align-items: center;...'>
            ... (Standard HTML Block with Logo & Links) ...
            <div style='font-size: 0.8em; color: #888;'>Driver v${DRIVER_VERSION}</div>
        </div>"""
        
        input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: true
        input "txtEnable", "bool", title: "Enable Description Text", defaultValue: true
    }
}

void initialize() {
    logInfo("Initializing Driver (v${DRIVER_VERSION})")
    
    // State Tracking
    state.driverVersion = DRIVER_VERSION
    if (device.currentValue("_version") != DRIVER_VERSION) {
        sendEvent(name: "_version", value: DRIVER_VERSION)
    }
    
    // Auto-disable debug after 30 mins
    if (logEnable) runIn(1800, logsOff)
}
```

### D. Reliability Standards (The "Safe Parse" Pattern)
To prevent "Silent Failures" where a driver crashes without logging an error (e.g., due to missing Map keys or type mismatches), all `parse()` methods **must** be wrapped in a `try/catch` block.

**Requirements:**
1.  **Wrap Logic:** Enclose the main logic in `try { ... } catch (e) { ... }`.
2.  **Fatal Logging:** The `catch` block must log the error at `error` level with the prefix `FATAL CRASH IN PARSE:`.
3.  **Robust Math:** When parsing Matter maps, always handle both Integer (direct) and String (Hex) values for Clusters/Attributes to avoid `MissingMethodException`.

**Safe Parse Template:**

```groovy
void parse(String description) {
    try {
        Map descMap = matter.parseDescriptionAsMap(description)
        
        // 1. Robust ID Check
        String endpointId = descMap.endpointId ?: descMap.endpoint
        if (!endpointId) return

        // 2. Logic...
        
    } catch (e) {
        log.error "FATAL CRASH IN PARSE: ${e}"
    }
}
```