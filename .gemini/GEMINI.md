# GEMINI.md - Aniva Hubitat Development Standards

**Author:** Aniva
**Platform:** Hubitat Elevation
**License:** Apache 2.0
**Support:** [PayPal](https://paypal.me/AndreiIvanov420) | [GitHub](https://github.com/aniva)

## 1. Global AI Generation Rules (Strict)
* **Code Blocks in Markdown:** When generating documentation (like `README.md`), **never** use triple backticks inside a code block. Always substitute internal code fences with **triple single quotes (`'''`)** to prevent rendering errors.
* **CamelCase:** Always use `camelCase` for variable names, methods, and attributes unless a specific API requires otherwise.

## 2. The "Aniva Standard" Pattern
All drivers must adhere to these structural requirements for consistency.

### A. Version & Identity
Every driver must implement the following to ensure users can track updates and verify installation:
1.  **Static Field:** `@Field static final String DRIVER_VERSION = "x.y.z"` at the top.
2.  **Accessor:** `def driverVersion() { return DRIVER_VERSION }`
3.  **State Tracking:** In `initialize()`, write this to `state.driverVersion` and/or `sendEvent(name: "_version"...)`.
4.  **Initialization Log:** Log the version immediately upon `initialize()` using `logInfo`.
5.  **UI Header:** The `preferences` block must use the **Map-based Input Syntax** and the **CSS Background Image** technique for icons (to prevent SVG rendering issues).

**Standard Header Implementation:**

```groovy
    preferences {
        // Aniva Standard Header (CSS Background Method)
        input(
            name: 'about',
            type: 'paragraph',
            element: 'paragraph',
            title: '',
            description: ```
            <div style="min-height:55px; background:transparent url('data:image/svg+xml;base64,PHN2Zy...') no-repeat left center; background-size:50px 50px; border: 1px solid #e0e0e0; border-radius: 5px; background-color: #fafafa; padding: 10px 10px 10px 80px;">
                <div style="font-weight: bold; font-size: 1.1em; color: #333;">Device Name</div>
                <div style="font-size: 0.8em; color: #888;">Driver v``` + driverVersion() + ```</div>
                <div style="font-size: 0.8em; margin-top: 5px;">
                    <a href="https://github.com/aniva/hubitat01" target="_blank">View GitHub</a> | 
                    <a href="https://paypal.me/AndreiIvanov420" target="_blank">Support Dev</a>
                </div>
            </div>
            ```
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
```

### B. Standard Logging
* **Inputs:** Must include `logEnable` (Debug) and `txtEnable` (Description Text).
* **Auto-Disable:** Debug logging must automatically turn off after **30 minutes**.
* **Helpers:** Use `logDebug()` and `logInfo()` helpers to keep code clean.

### C. Reliability (The "Safe Parse")
To prevent silent failures, all `parse()` methods and complex logic blocks must be wrapped in `try/catch`:

```groovy
void parse(String description) {
    try {
        // ... Logic ...
    } catch (e) {
        log.error "FATAL CRASH IN PARSE: ${e}"
    }
}
```

### D. Capabilities
* **Actuator:** Must be included in all drivers to ensure visibility in Rule Machine.
* **Initialize:** Must be included to handle hub reboots and state setup.

## 3. HPM Manifest Standards
* **Author:** Must be strictly set to `"Aniva"`.
* **Documentation:** Link to the specific project `README.md`.

## 4. Parent/Child Driver Standards
Rules specific to drivers that spawn component devices (e.g., Dirigera Bridge, WiMeter).

### A. The "LazyMap" Serialization Fix
* **Problem:** Hubitat's `LazyMap` object (from `slurper`) fails to serialize when passed from Parent -> Child driver, causing child data to be empty.
* **Fix:** The Parent driver **MUST** convert the data to a standard Java `HashMap` before calling `child.parseItems()` or passing data to children.

### B. Initialization & Management
* **Version Tracking:** Both Parent and Child drivers must maintain a `_version` attribute to verify the code update was applied successfully.
* **Maintenance Command:** Parent must include a command `recreateChildDevices` to allow users to purge old/messy attributes if the naming convention or data structure changes.