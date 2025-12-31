# IKEA VINDSTYRKA Air Quality Monitor - Project Context
**Project:** Air Quality Dashboard Tile & Automation Logic
**Platform:** Hubitat Elevation
**Status:** In Development (v0.1)

## 1. Project Overview
This project enhances the **IKEA VINDSTYRKA** Zigbee Air Quality Sensor. While the sensor pairs natively with Hubitat, the raw data is noisy and lacks visual "At-a-glance" dashboard elements.

**Goals:**
1.  **Visual Dashboard Tile:** Create a custom HTML tile that displays PM2.5 and VOC levels with color-coded health status (Green/Yellow/Red) and trend arrows (↗ ↘).
2.  **Spike Smoothing:** Implement logic to ignore transient spikes (e.g., lighting a match) and only react to sustained poor air quality.
3.  **Automation:** Trigger ventilation systems only when "Sustained Poor Air" is detected.

## 2. Hardware Scope
* **Sensors:** 2x IKEA VINDSTYRKA (Living Room, Basement/3D Printer Area).
* **Actuators:** Zigbee-controlled Ventilation Fans.
* **Hub:** Hubitat Elevation (C-8 Pro).

## 3. Architecture
This project does not replace the device driver. It sits "on top" of it.

### A. The Virtual Driver (`AirQualityTile.groovy`)
* **Role:** Display Proxy.
* **Capabilities:** `Sensor`, `Actuator`.
* **Attributes:**
    * `html_tile` (The HTML string for the dashboard).
    * `pm25` & `voc` (Current smoothed values).
    * `trend` (Direction of change: up/down/flat).
* **Commands:** `updateAirQuality(pm25, voc)` -> Calculates trends and generates the HTML.

### B. The Logic Layer (Rule Machine)
* **Role:** The "Brain."
* **Trigger:** PM2.5 or VOC changes on the *physical* device.
* **Filtering:**
    * **Immediate:** Update Dashboard Tile (so users see the spike).
    * **Delayed (5 min):** If levels stay above threshold for 5 minutes -> Turn on Fan.
* **Thresholds (Draft):**
    * **PM2.5:** > 25 (Poor)
    * **VOC Index:** > 250 (Poor)

## 4. Visual Design (The Tile)
The `html_tile` will use the same "Nuclear CSS" full-bleed style as the WiMeter project.

**Layout:**
* **Background:** Dynamic Color (Green/Yellow/Red).
* **Center:** Status Text ("GOOD", "FAIR", "POOR") + Trend Arrow.
* **Footer:** Small row showing raw numbers ("PM2.5: 12 | VOC: 150").

## 5. Deployment Plan
* **Repository:** `aniva/hubitat01` -> Folder `VindstyrkaTile`.
* **HPM:** Add to existing `repository.json` as a new package "Vindstyrka Air Quality Tile".

---

## 6. Development To-Do List
1.  [ ] Create `AirQualityTile.groovy` driver (Virtual Device).
2.  [ ] Refine CSS for the HTML tile (ensure it fits standard dashboard grid).
3.  [ ] Implement "Trend" logic (compare new value vs. current state).
4.  [ ] Define exact Color Thresholds (WHO vs. IKEA standards).
5.  [ ] Create `packageManifest.json` for HPM.