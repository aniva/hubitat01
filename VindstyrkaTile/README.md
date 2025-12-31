# IKEA VINDSTYRKA Air Quality Tile

**Status:** In Development (v0.1)

## Overview
This project enhances the [IKEA VINDSTYRKA](https://www.ikea.com/us/en/p/vindstyrka-air-quality-sensor-smart-60498233/) Zigbee Air Quality Sensor integration for Hubitat Elevation.
The VINDSTYRKA is a smart air quality monitor that tracks **PM2.5**, **tVOC**, **Temperature**, and **Humidity**. While the sensor pairs natively, this project adds a visual layer and logic to handle data noise.

**Key Goals:**
1.  **Visual Dashboard Tile:** A custom HTML tile displaying PM2.5 and VOC levels with health status colors (Green/Yellow/Red).
2.  **Trend Indicators:** Visual arrows (↗ ↘) showing the direction of air quality change.
3.  **Spike Smoothing:** Logic to differentiate between transient spikes (e.g., lighting a match) and sustained poor air quality.

## Architecture
This solution uses a "Proxy" driver approach:
*   **Physical Device:** The IKEA VINDSTYRKA sensor. Recommended driver: [Dan Danache's IKEA Zigbee Drivers](https://community.hubitat.com/t/release-ikea-zigbee-drivers/123853/537) ([Source](https://codeberg.org/dan-danache/hubitat)).
*   **Virtual Driver (`AirQualityTile.groovy`):** Receives data, calculates trends, and generates the `html_tile` attribute.
*   **Rule Machine:** Acts as the "Brain" to filter triggers and update the virtual driver.

## Installation

### Option 1: Hubitat Package Manager (HPM)
*   *Coming Soon:* This package will be available in the repository as "Vindstyrka Air Quality Tile".

### Option 2: Manual Install
1.  Open **Drivers Code** in your Hubitat interface.
2.  Create a New Driver.
3.  Paste the code from `AirQualityTile.groovy`.
4.  Save.

## Setup & Usage

### 1. Create the Virtual Device
1.  Go to **Devices** -> **Add Device**.
2.  Choose **Virtual**.
3.  **Device Name:** e.g., "Living Room Air Tile".
4.  **Type:** Select `Vindstyrka Air Quality Tile` (Namespace: `aniva`).
5.  Save Device.

### 2. Connect via Rule Machine
Create a rule to push data from the physical sensor to the tile:
*   **Trigger:** `PM2.5` or `VOC Index` *changed* on the physical IKEA device.
*   **Action:** Run Custom Action on the Virtual Device:
    *   Command: `updateAirQuality`
    *   Parameters: `current PM2.5`, `current VOC`

## Dashboard Configuration
1.  Add a tile to your Hubitat Dashboard.
2.  Select the Virtual Device created above.
3.  Select the `Attribute` template.
4.  Choose the `html_tile` attribute.

## Thresholds
The tile uses the following logic for visual status (Draft):
*   **PM2.5:** > 25 (Poor)
*   **VOC Index:** > 250 (Poor)

## Credits
*   **Author:** Aniva
*   **Platform:** Hubitat Elevation
