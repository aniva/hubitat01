# Hubitat Project Context
**User:** Aniva
**Platform:** Hubitat Elevation (C-8 Pro)
**Repository:** `aniva/hubitat01`

---

## 1. Project: IKEA VINDSTYRKA Tile
**Folder:** `VindstyrkaTile`
**Status:** Stable (v2.3)
**Goals:** Visual dashboard tile, spike smoothing, and automation triggers for air quality.

### Architecture
* **Driver:** `AirQualityTile.groovy` (Virtual)
* **Logic:** Rule Machine Bridge (Physical Sensor -> Variable -> Virtual Tile)
* **Features:** Linear Regression Trends, WHO Thresholds, Crash-proof Status Table.

### Recent Changes
* [x] Implemented Linear Regression (Slope) for trends.
* [x] Moved Live Data to `status_table` to fix UI hangs.
* [x] Added configurable "Fair/Poor" thresholds.

---

## 2. Project: OpenWeatherMap-Alerts (Icon Fix)
**Folder:** `OpenWeatherMap`
**Status:** Stable (v0.7.2 Patch)
**Goals:** Restore broken weather icons caused by dead TinyURL service.

### Solution
* **Code:** Updated driver default to point to `HubitatCommunity/WeatherIcons` GitHub repo.
* **Manual Fix:** Documented steps to update existing devices via "Preferences".

---

## 3. Project: WiMeter Cloud Bridge
**Folder:** `WimeterDriver`
**Status:** Stable (Base Driver)
**Goals:** Integrate WiMeter Energy Monitoring solution with Hubitat.

### Current State
* **Driver:** Cloud Bridge Integration Driver.
* **Function:** Fetches energy data from WiMeter cloud.

### Development To-Do List
1.  See [project context](../WimeterDriver/.gemini/GEMINI.md).