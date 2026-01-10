# GEMINI.md - Project Context

**Project:** IKEA PARASOLL Zigbee Sensor
**Type:** Component Child Driver
**Parent:** IKEA DIRIGERA Bridge
**Standard:** Adheres to [Root Standards](../../.gemini/GEMINI.md)

## 1. Project Overview
A dedicated child driver for the PARASOLL sensor. It relies entirely on the Parent Bridge for data; it does not poll the device directly.

## 2. Key Logic
* **Battery Normalization:** IKEA reports battery on a 0-200 scale. This driver divides the value by 2 to get standard 0-100%.
* **Logic Reversal:** Includes a preference `reverseContact` to swap Open/Closed states for specific mounting scenarios.
* **Passive Parsing:** The `parse()` method accepts a `List` of events from the parent, not a raw string.