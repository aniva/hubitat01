# GEMINI.md - Project Context & Coding Standards

**Project:** Virtual Mutable Battery Driver
**Platform:** Hubitat Elevation
**Author:** Aniva
**License:** Apache 2.0
**Support:** [PayPal](https://paypal.me/AndreiIvanov420) | [GitHub](https://github.com/aniva)

## 1. Project Overview: Virtual Mutable Battery
This driver is designed to bridge devices that report battery status vaguely (e.g., "High", "OK", "Low") into a standard percentage-based format that Hubitat dashboards can understand.

**Problem Solved:** Many cameras or cloud devices do not report a numeric battery level (0-100%), preventing them from using standard Hubitat battery tiles.
**Solution:** This virtual driver acts as a proxy. Rule Machine logic translates the source device's status (e.g., "Low") into a numeric value (e.g., "15%") and sets it on this driver using the `setBattery()` command. This allows the use of standard, color-changing battery tiles on dashboards.

### Key Features
* **Rule Machine Compatible:** Explicitly exposes the `Actuator` capability so it appears in Rule Machine action lists.
* **Custom Command:** `setBattery(level)` allows direct percentage assignment.
* **Aniva Standard Styling:** Includes the standardized HTML header and version tracking.

