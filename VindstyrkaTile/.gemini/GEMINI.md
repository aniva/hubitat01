# IKEA VINDSTYRKA Air Quality Monitor - Project Context
**Project:** Air Quality Dashboard Tile & Automation Logic
**Platform:** Hubitat Elevation
**Status:** Stable (v2.3)
**Repository:** `aniva/hubitat01` -> Folder `VindstyrkaTile`

## 1. Project Overview
This project enhances the **IKEA VINDSTYRKA** Zigbee Air Quality Sensor. While the sensor pairs natively with Hubitat, the raw data is noisy and lacks visual "At-a-glance" dashboard elements.

**Goals:**
1.  **Visual Dashboard Tile:** A minimalist, "Nuclear" style CSS tile showing PM2.5 & VOC levels, color-coded by health status (Green/Yellow/Red).
2.  **Advanced Trend Analysis:** Use Linear Regression (Slope) to determine if air quality is improving or worsening, ignoring transient spikes.
3.  **Configurable Logic:** Allow users to define their own "Fair" and "Poor" thresholds based on WHO or personal preference.
4.  **Unified Status View:** A crash-proof "Unified Status Table" in the driver details to show live data alongside reference thresholds.

## 2. Architecture
The solution uses a **"Bridge" Pattern** to separate raw device data from dashboard presentation.

### A. The Virtual Driver (`AirQualityTile.groovy`)
* **Role:** Logic & Presentation Layer.
* **Attributes:**
    * `html_tile`: The dashboard-ready HTML string (WiMeter Style).
    * `status_table`: A complete HTML table showing Current Values + Legend (displayed on Device Page).
    * `trend`: Calculated slope direction ("up", "down", "flat").
* **Logic:**
    * **Linear Regression:** Calculates the slope of data points over a configurable window (e.g., 30 mins) to determine trend.
    * **Threshold Evaluation:** Compares current values against user-defined preferences to set state (Good/Fair/Poor).

### B. The Logic Layer (Rule Machine)
* **Role:** The Bridge.
* **Trigger:** PM2.5 or VOC changes on the *Physical* IKEA Sensor.
* **Action:** Copies values to local variables -> Sends command `updateAirQuality(pm, voc)` to the *Virtual* Tile.
* **Benefit:** Allows the Virtual Driver to remain pure logic/display without managing Zigbee bindings.

## 3. Key Features & Requirements

### 1. Advanced Trend Calculation (Regression)
* **Method:** Least Squares Linear Regression.
* **Logic:** Instead of comparing Point A to Point B (which is susceptible to spikes), the driver calculates the *Slope* of all data points within the window.
* **Window:** Configurable via Preference `trendWindow` (Default: 30 minutes).

### 2. Configurable Thresholds (Preferences)
Users can define specific triggers for "Fair" and "Poor" air quality states.
* **Default (WHO/Sensirion):**
    * **PM2.5:** Fair > 15, Poor > 35 (WHO 24h Mean).
    * **VOC Index:** Fair > 150, Poor > 300 (Sensirion Index).
* **Customization:** Exposed as inputs in the driver Preferences.

### 3. Unified Status Table (UI Stability)
* **Problem Solved:** Calling `device.currentValue` inside `preferences` caused UI hangs ("Spinning Wheel") on new devices.
* **Solution:** The "Live Data" table was moved to a dedicated attribute (`status_table`) in the "Current States" column.
* **Content:** The table dynamically combines:
    * **Row 1:** Current Live Readings (PM2.5 & VOC).
    * **Row 2+:** The Reference Legend (updated dynamically based on user Threshold Preferences).

### 4. Visual Design (The Tile)
* **Style:** Full-bleed "WiMeter" aesthetic.
* **Header:** Small uppercase "PM2.5 | VOC".
* **Body:** Large Values + Trend Arrow (e.g., `4 | 120 ↗`).
* **Color:** Dynamic background (Green/Yellow/Red) based on worst-case attribute.

## 4. Deployment Structure
* **File:** `VindstyrkaTile/AirQualityTile.groovy` (Driver Code).
* **File:** `VindstyrkaTile/README.md` (Instructions for Variable Bridge).
* **File:** `VindstyrkaTile/packageManifest.json` (HPM Integration).
* **Root:** `repository.json` updated to include the new package.

---

## 5. Version History
* **v1.0 - v1.3:** Basic Point-to-Point trend logic.
* **v1.4:** Introduced Linear Regression (Slope Analysis).
* **v1.6:** Adopted "WiMeter" visual style.
* **v1.9:** Added Live Data table (caused UI hangs).
* **v2.3 (Current):** Stable Release.
    * Moved Live Table to `status_table` attribute (Crash-proof).
    * Added configurable Threshold Preferences.
    * Unified Legend and Live Data into single view.