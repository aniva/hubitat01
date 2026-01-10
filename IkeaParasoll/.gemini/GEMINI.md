# GEMINI.md - Project Context & Coding Standards

**Project:** Hubitat Component Driver for IKEA PARASOLL Sensor
**Platform:** Hubitat Elevation
**Parent Driver:** IKEA DIRIGERA Bridge
**Author:** Aniva
**License:** Apache 2.0
**Support:** [PayPal](https://paypal.me/AndreiIvanov420) | [GitHub](https://github.com/aniva)

## 1. Project Overview

This project provides the **Component Child Driver** for the IKEA PARASOLL Door/Window sensor. It is designed to work exclusively as a child device of the **IKEA DIRIGERA Bridge**.

**The Problem:**
When paired via Matter, IKEA sensors send raw data that generic drivers often misinterpret. Specifically, the battery reports use a 0-200 scale (where 200 = 100%), and magnetic contact logic can vary based on mounting.

**The Solution:**
This driver acts as a "Business Logic Layer" for the raw data passed down by the Bridge:
1.  **Passive Reception:** It does not "poll" the device directly; it waits for `parse(List events)` calls from the Parent Bridge.
2.  **Battery Normalization:** It automatically divides incoming battery values by 2.
3.  **Logic Control:** It offers a user preference to reverse Open/Close states without physical remounting.

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
3.  **State Tracking:** In `initialize()`, write this version to `_version` event.
4.  **Logging:** Log the version immediately upon initialization (using `logInfo`).
5.  **UI Header:** Display the version dynamically inside a styled HTML paragraph block in `preferences`.

**Standard Implementation Template:**

```groovy
import groovy.transform.Field

@Field static final String DRIVER_VERSION = "2.2.0"

metadata {
    definition (name: "IKEA PARASOLL Zigbee Sensor", namespace: "aniva", author: "Aniva") {
        capability "Initialize"
        capability "Actuator"
        capability "ContactSensor"
    }

    preferences {
        // Aniva Standard Header
        input name: "about", type: "paragraph", element: "paragraph", title: "", description: """
        <div style='display: flex; align-items: center;...'>
            ... (Standard HTML Block with Logo & Links) ...
            <div style='font-size: 0.8em; color: #888;'>Component v${DRIVER_VERSION}</div>
        </div>"""
        
        input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: true
        input "txtEnable", "bool", title: "Enable Description Text", defaultValue: true
    }
}

void initialize() {
    sendEvent(name: "_version", value: DRIVER_VERSION)
    logInfo("Initializing (v${DRIVER_VERSION})")
    
    // Auto-disable debug after 30 mins
    if (logEnable) runIn(1800, logsOff)
}
```

### D. Reliability Standards (The "Safe Parse" Pattern)
To prevent "Silent Failures" where a driver crashes without logging an error (e.g., if the Bridge sends malformed data), the `parse()` method **must** be wrapped in a `try/catch` block.

**Requirements:**
1.  **Input Handling:** The `parse` method should accept `def description` to handle both Maps and Lists.
2.  **Wrap Logic:** Enclose the main logic in `try { ... } catch (e) { ... }`.
3.  **Fatal Logging:** The `catch` block must log the error at `error` level.

**Safe Parse Template:**

```groovy
def parse(description) {
    try {
        if (logEnable) log.debug "Received from Bridge: ${description}"
        
        if (description instanceof List) {
            description.each { event -> handleBridgeEvent(event) }
        } else if (description instanceof Map) {
            handleBridgeEvent(description)
        }
    } catch (e) {
        log.error "FATAL CRASH IN PARSE: ${e}"
    }
}
```