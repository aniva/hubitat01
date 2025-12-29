# WiMeter Hubitat Driver - Development Context
**Last Updated:** v4.5 (December 28, 2025)
**Project:** WiMeter Cloud Bridge for Hubitat Elevation

## 1. Project Overview
This project consists of a **Parent Driver** and a **Child Driver** that bridge the WiMeter Energy Monitoring API to Hubitat.
* **Goal:** Poll the WiMeter JSON API, parse energy/power/cost data, and display it in Hubitat with a specific, "clean" naming convention.
* **Architecture:** **Hybrid Model**.
    * **The House (Parent):** The main location (e.g., "Andrei's House") data is displayed directly on the Parent Device.
    * **Appliances (Children):** Distinct devices (e.g., "Boiler", "A/C") are automatically created as Child Devices.

## 2. API Data Structure
The driver consumes a JSON response that may be formatted as a List or a Map.
**Key API Constraint:** The API splits data across multiple packets. You might get "Power" in one packet and "Cost" in another. The driver must **merge** these based on the device name.

### JSON Sample
```json
{
    "ret": 1,
    "msg": "",
    "devices": [
        {
            "name": "Andrei's House",
            "reading": 2.51,
            "unit": "kW",
            "interval": 0, // interval 0 = Real-time Reading
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/home1.png",
            "location": "Andrei's House"
        },
        {
            "name": "Andrei's House",
            "reading": 8.3,
            "unit": "$",
            "interval": 86400, // interval 604800 = Per Day
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/home1.png",
            "location": "Andrei's House"
        },
        {
            "name": "Boiler",
            "reading": 17.2,
            "unit": "$",
            "interval": 604800, // interval 604800 = Per Week
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/electric_boiler1.png",
            "location": "Andrei's House"
        },
        {
            "name": "Range kitchen",
            "reading": 0,
            "unit": "W",
            "interval": 0, 
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/stove1.png",
            "location": "Andrei's House"
        },
        {
            "name": "Range kitchen",
            "reading": 1.7,
            "unit": "$",
            "interval": 604800,
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/stove1.png",
            "location": "Andrei's House"
        },
        {
            "name": "Boiler",
            "reading": 0,
            "unit": "W",
            "interval": 0,
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/electric_boiler1.png",
            "location": "Andrei's House"
        },
        {
            "name": "Dishwasher",
            "reading": 2.592,
            "unit": "W",
            "interval": 0,
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/dishwasher.png",
            "location": "Andrei's House"
        },
        {
            "name": "Dishwasher",
            "reading": 1.1,
            "unit": "$",
            "interval": 604800,
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/dishwasher.png",
            "location": "Andrei's House"
        },
        {
            "name": "A\/C",
            "reading": 59,
            "unit": "W",
            "interval": 0,
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/heater_cooler.png",
            "location": "Andrei's House"
        },
        {
            "name": "A\/C",
            "reading": 1.7,
            "unit": "$",
            "interval": 604800,
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/heater_cooler.png",
            "location": "Andrei's House"
        },
        {
            "name": "Dryer",
            "reading": 0,
            "unit": "W",
            "interval": 0,
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/dryer.png",
            "location": "Andrei's House"
        },
        {
            "name": "Dryer",
            "reading": 1,
            "unit": "$",
            "interval": 604800,
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/dryer.png",
            "location": "Andrei's House"
        },
        {
            "name": "Washer",
            "reading": 4.452,
            "unit": "W",
            "interval": 0,
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/washer1.png",
            "location": "Andrei's House"
        },
        {
            "name": "Washer",
            "reading": 0.6,
            "unit": "$",
            "interval": 604800,
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/washer1.png",
            "location": "Andrei's House"
        },
        {
            "name": "A\/C",
            "reading": 1.82,
            "unit": "kWh",
            "interval": 86400,
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/heater_cooler.png",
            "location": "Andrei's House"
        },
        {
            "name": "Heaters",
            "reading": 27.5,
            "unit": "$",
            "interval": 604800,
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/electric_heater.png",
            "location": "Andrei's House"
        },
        {
            "name": "Heaters",
            "reading": 2.16,
            "unit": "kW",
            "interval": 0,
            "url": "https:\/\/wimeter.net\/res\/images\/icons\/electric_heater.png",
            "location": "Andrei's House"
        }
    ]
}
```

## 3. Naming & Attribute Standards (STRICT)
We do not use standard Hubitat capabilities like `capability "PowerMeter"` because they enforce generic attribute names (like `power`). We require granular, unit-suffixed names.

### Attribute Naming Convention
* **Format:** `[type]_[interval]_[unit]`
* **Case:** All Lowercase.
* **Special Chars:** `/` removed (A/C -> ac). Spaces replaced by underscores. `$` replaced by `dollars`.

### Interval Logic
The `interval` field (seconds) determines the middle part of the attribute name:

| Interval (Sec) | Suffix Name |
| :--- | :--- |
| 0 | `_real-time` |
| 86400 | `_per_day` |
| 604800 | `_per_week` |
| 2419200 - 2678400 | `_per_month` |
| Other | `_per_period` |

### Unit Suffix Logic

| Source Unit | Converted Unit | Suffix |
| :--- | :--- | :--- |
| W, kW (Interval 0) | **kW** | `_kw` |
| Wh, kWh (Interval > 0) | **kWh** | `_kwh` |
| $, Dollars | **$** | `_dollars` (or `_$`) |

**Examples:**
* `location_power_real-time_kw` (Parent Device)
* `power_per_day_kwh` (Child Device)
* `cost_per_week_\$` (Child Device)

## 4. Technical Constraints & Fixes
If modifying this driver, strictly adhere to these fixes established during v1.0 - v4.5 development:

### A. The "Clean State" Rule
* **Requirement:** Do **NOT** set missing attributes to `-1`.
* **Reason:** Users prefer a clean interface. If the API does not report "Real-time Cost" for a specific device, that attribute should simply not exist on the device page.
* **Implementation:** The driver must calculate valid attributes and `sendEvent` only for what is present in the current payload.

### B. The "LazyMap" Serialization Fix
* **Problem:** Hubitat's `LazyMap` object (from `slurper`) fails to serialize when passed from Parent -> Child driver, causing child data to be empty.
* **Fix:** The Parent driver **MUST** convert the data to a standard Java `HashMap` before calling `child.parseItems()`.

```groovy
// Parent Driver: processData()
def cleanItems = items.collect { new HashMap(it) }
```

### C. Initialization & Versioning
* **Requirement:** Both drivers must include `capability "Initialize"`.
* **Requirement:** Both drivers must maintain a `_version` attribute (e.g., `4.5`) to verify the code update was applied successfully.
* **Requirement:** Parent must include command `recreateChildDevices` to allow users to purge old/messy attributes if the naming convention changes.

## 5. Driver Structure

### Parent Driver (`WiMeterCloudBridge.groovy`)
* **Role:** Polling, HTTP GET, Preferences, Child Management.
* **Capabilities:** `Refresh`, `Initialize`, `Sensor`.
* **Key Command:** `resetAllData` (Manual wipe), `recreateChildDevices`.
* **Logic:**
    * Filters incoming JSON by `targetLocation`.
    * Groups items by `name`.
    * If `name == targetLocation` -> Update Parent Attributes (`location_...`).
    * If `name != targetLocation` -> Update Child Device.

### Child Driver (`WiMeterChildDevice.groovy`)
* **Role:** Display container for appliances.
* **Capabilities:** `Refresh`, `Initialize`, `Sensor`.
* **Logic:**
    * `parseItems(items)`: Receives `HashMap` from parent.
    * Updates `icon` and `html_icon` from URL.
    * Calculates suffix and sends events.

## 6. Deployment (HPM)
* **Status:** **Merged into Official HPM Repository.**
* **Manifest:** `packageManifest.json` lists both drivers.
* **Versioning:** Version numbers in the JSON manifest must match the `driverVersion()` in the Groovy code.

## 7. Future Roadmap / To-Do (v4.11+)

1.  **Standardize State Variables (CamelCase)**
    * **Goal:** Refactor variable naming from underscores (`power_real_time`) to standard camelCase (`powerRealTime`) to ensure consistency between raw names and Hubitat's variable display.
    * **Impact:** Will require wiping/recreating child devices to remove old attribute names.

2.  **Fix Persistent "Unknown" (?) Device Icons**
    * **Issue:** Even with `capability "PowerMeter"`, child devices like "Boiler" or "Range" often display a generic `?` icon instead of a Lightning Bolt or Plug.
    * **Investigation:** Determine if Hubitat requires a specific "Device Type" definition or if the icon is cached at the platform level.

3.  **Dynamic Titles for HTML Tiles**
    * **Issue:** Currently, child device tiles (green/red cards) hardcode the title as "POWER".
    * **Fix:** Pass the dynamic device name (e.g., "Boiler", "Washer") from the API response into the HTML generation logic so the tile says "BOILER PWR" instead of generic "POWER".

4. **Driver configuration for power tile threshols**
    * **Issue:** Currently kW thresholds for tile are hardcoded in the driveres, depending on the treshold tile changes colour, low power - grey, highest power red
    * **Fix** make thresholds configurable in driver properties for parent and child drivers, bonus, allow color selection there too 