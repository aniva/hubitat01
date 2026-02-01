# WiMeter Hubitat Driver - Development Context
**Last Updated:** v4.15 (January 1, 2026)
**Project:** WiMeter Cloud Bridge for Hubitat Elevation

## 1. Project Overview
This project consists of a **Parent Driver** and a **Child Driver** that bridge the WiMeter Energy Monitoring API to Hubitat.
* **Goal:** Poll the WiMeter JSON API, parse energy/power/cost data, and display it in Hubitat with a specific, "clean" naming convention.
* **Architecture:** **Hybrid Model**.
    * **The House (Parent):** The main location (e.g., "Andrei's House") data is displayed directly on the Parent Device.
    * **Appliances (Children):** Distinct devices (e.g., "Boiler", "A/C") are automatically created as Child Devices.

## 2. API Data Structure
The driver consumes a JSON response that may be formatted as a List or a Map.
WiMeter base URL end point is: https://wimeter.net/v1/pubmatrix?key=\<Public Key\>, where key comes into driver config from WiMeter Account page.
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
            "interval": 86400, // interval 86400 = Per Day
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

### Interval Logic (Suffix)
The 'interval' field (seconds) determines the middle part of the attribute name:

| Interval (Sec) | CamelCase Suffix |
| :--- | :--- |
| 0 | 'RealTime' |
| 86400 | 'PerDay' |
| 604800 | 'PerWeek' |
| 2419200 - 2678400 | 'PerMonth' |
| Other | 'PerPeriod' |

### Unit Logic (Suffix)

| Source Unit | Converted Unit | Suffix | Example |
| :--- | :--- | :--- | :--- |
| W, kW (Interval 0) | **kW** | 'Kw' | 'powerRealTimeKw' |
| W, kW (Interval 0) | **W** | 'W' | 'powerRealTimeW' |
| Wh, kWh (Interval > 0) | **kWh** | 'Kwh' | 'powerPerDayKwh' |
| $, Dollars | **$** | (None) | 'costPerDay' |

## 3. Technical Constraints & Fixes
If modifying this driver, strictly adhere to these fixes established during v1.0 - v4.11 development:

### A. The "Clean State" Rule
* **Requirement:** Do **NOT** set missing attributes to '-1'.
* **Reason:** Users prefer a clean interface. If the API does not report "Real-time Cost" for a specific device, that attribute should simply not exist on the device page.
* **Implementation:** The driver must calculate valid attributes and 'sendEvent' only for what is present in the current payload.

## 4. Driver Structure

### Parent Driver ('WiMeterCloudBridge.groovy')
* **Role:** Polling, HTTP GET, Preferences, Child Management.
* **Capabilities:** 'Refresh', 'Initialize', 'Sensor', 'PowerMeter', 'EnergyMeter'.
* **Key Command:** 'resetAllData' (Manual wipe), 'recreateChildDevices'.
* **Logic:**
    * Filters incoming JSON by 'targetLocation'.
    * Groups items by 'name'.
    * If 'name == targetLocation' -> Update Parent Attributes.
    * If 'name != targetLocation' -> Update Child Device.

### Child Driver ('WiMeterCloudBridgeChild.groovy')
* **Role:** Display container for appliances.
* **Capabilities:** 'Refresh', 'Initialize', 'Sensor', 'PowerMeter', 'EnergyMeter'.
* **Logic:**
    * 'parseItems(items)': Receives 'HashMap' from parent.
    * Updates 'icon' and 'htmlIcon' from URL.
    * Generates 'htmlTile' for dashboards.

## 5. Deployment (HPM)
* **Status:** **Merged into Official HPM Repository.**
* **Manifest:** 'packageManifest.json' lists both drivers.
* **Versioning:** Version numbers in the JSON manifest must match the 'driverVersion()' in the Groovy code.

## 6. Dashboard HTML Tile Architecture (v4.15)
The driver generates a specialized HTML block (exposed via the 'htmlTile' attribute) to provide a "Live Status" card on dashboards.

### Visual Components
1.  **Device Name:** Provided by Dashboard native label.
2.  **Card Title:** Fixed string ("Power" for children, "House Pwr" for parent).
3.  **Color Logic:** Background color changes based on thresholds (Configurable via Preferences).
4.  **Semantic State:** Exposes 'powerLevel' (High, Medium, Active, Idle) for Rule Machine logic.

### Logic Implementation (Updated v4.15)
Colors are hardcoded to Traffic Light standards. Thresholds are configurable with **Visual CSS Labels**.
Logic calculates 'powerLevel' string alongside color.

**Defaults (Parent):** Active: 1kW, Med: 3kW, High: 6kW
**Defaults (Child):** Active: 0.4kW, Med: 1kW, High: 2kW

**Code Snippet (Logic):**
```groovy
    // ... Threshold Setup ...
    def cardColor = cGrey
    def levelText = "Idle"
    
    if (powerVal >= tHigh) {
        cardColor = cRed
        levelText = "High"
    } else if (powerVal >= tMed) {
        cardColor = cYellow
        levelText = "Medium"
    } else if (powerVal >= tActive) {
        cardColor = cGreen
        levelText = "Active"
    } else {
        cardColor = cGrey
        levelText = "Idle"
    }
    
    sendEvent(name: "powerLevel", value: levelText)
```

---

## 7. Future Roadmap / To-Do (v4.15+)

1.  **[COMPLETED] Standardize State Variables (CamelCase)**
    * **Status:** Done in v4.11.

2.  **[CLOSED] Fix Persistent "Unknown" (?) Device Icons**
    * **Conclusion:** Cannot be set via driver code without changing capabilities to "Outlet/Switch", which causes Voice Assistant issues.
    * **Workaround:** User must manually click the '?' icon and filter for "flash" to set the system icon.

3.  **[COMPLETED] Configurable Power Tile Thresholds**
    * **Status:** Done in v4.14. (Visual CSS added).

4.  **[COMPLETED] Standardize Power Level Attribute**
    * **Goal:** Add 'powerLevel' string attribute to simplify Rule Machine logic (e.g., "High", "Idle").
    * **Status:** Done in v4.15.