# IKEA TIMMERFLOTTE Matter Sensor - Project Context

**Project:** Native Matter/Thread Driver for Hubitat
**Device:** IKEA TIMMERFLOTTE (Temperature & Humidity)
**Platform:** Hubitat Elevation
**Author:** Aniva
**License:** Apache 2.0
**Support:** [PayPal](https://paypal.me/AndreiIvanov420) | [GitHub](https://github.com/aniva)

## 1. Project Overview
This project provides a native Hubitat driver for the IKEA TIMMERFLOTTE sensor. Unlike Zigbee drivers, this relies on **Matter over Thread**, requiring specific handling of IPv6 routing and the Hubitat `matter` helper class.

## 2. Coding & Driver Standards (Aniva Standard)

### A. Version Handling
* **Static Field:** The driver must define a static version string for code readability.
* **State Variable:** On `initialize()` or `updated()`, this version must be written to `state.driverVersion` to facilitate upgrades and debug tracing.
* **Logging:** The driver must log the version number on initialization.

```groovy
import groovy.transform.Field
@Field static final String DRIVER_VERSION = "0.1.0"

void initialize() {
    state.driverVersion = DRIVER_VERSION
    logInfo("Initializing ${device.displayName} (Driver v${DRIVER_VERSION})")
}
```

### B. Logging Conventions
* **Standard Inputs:** `logEnable` (Debug) and `txtEnable` (Description/Info).
* **Auto-Disable:** Debug logging must automatically turn off after **30 minutes**.
* **Format:** `${device.displayName}: ${msg}`.

```groovy
// Standard Logging Preference Inputs
input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true

// Helper Methods
void logDebug(String msg) {
    if (logEnable) log.debug "${device.displayName}: ${msg}"
}

void logInfo(String msg) {
    if (txtEnable) log.info "${device.displayName}: ${msg}"
}

// In updated() method
if (logEnable) runIn(1800, logsOff)

void logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "${device.displayName}: Debug logging auto-disabled"
}
```

### C. HPM Manifest Requirements
* **Author Field:** Must be strictly set to `"Aniva"`.
* **Documentation Link:** Point to the GitHub README or specific file.
* **Paypal:** Include the standard donation link in the manifest JSON.

## 3. Device Specific Implementation Details

### A. Matter Architecture & Endpoints
The driver must use the `hubitat.matter.Matter` class. Raw hex parsing should be avoided unless necessary.

* **Endpoint 0x00:** Root / Battery / System
* **Endpoint 0x01:** Temperature Measurement
* **Endpoint 0x02:** Relative Humidity

```groovy
// Example Subscriptions
matter.subscribe(0x0402, 0x0000, [endpointId: "01"]) // Temperature
matter.subscribe(0x0405, 0x0000, [endpointId: "02"]) // Humidity
```

### B. Device Network IDs (DNI)
* **Assignment:** DNI is assigned by the Matter Fabric (Hubitat) during pairing.
* **Rule:** **Do not** manually overwrite the DNI in the driver code.
* **Fingerprinting:** Must match the specific IKEA Model ID to ensure correct driver selection during pairing.

```groovy
fingerprint endpointId: "01",
    inClusters: "0003,0004,001D,0402",
    outClusters: "",
    model: "TIMMERFLOTTE",
    vendor: "IKEA of Sweden",
    deviceJoinName: "IKEA TIMMERFLOTTE Matter Sensor"
```

### C. Network & Routing (IPv6)
* **Requirement:** The driver assumes the Hubitat hub can route to the device's IPv6 Unique Local Address (ULA) via the Border Router (DIRIGERA).
* **Troubleshooting:** If the device pairs but does not respond to commands, the issue is almost always lack of IPv6 Static Routes or RA (Router Advertisements) on the main LAN router.

## 4. Documentation Standards

### A. Image Formatting
* Use HTML `<img>` tags for screenshots in READMEs to control size.
* Standard width: `60%`.

### B. Code Block Safety
* Use single quotes (`````) or placeholders for triple backticks when writing documentation about code blocks to prevent rendering errors.

## 5. Deployment Structure
* **File:** `IkeaTimmerflotte.groovy` (Driver Code).
* **File:** `README.md` (User Guide & pairing instructions).
* **File:** `packageManifest.json` (HPM Integration).