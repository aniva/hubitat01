# Hubitat Drivers & Integrations

A collection of custom drivers and tools for the **Hubitat Elevation** platform.

**Author:** [Aniva](https://github.com/aniva)  
**Support:** [PayPal.me/AndreiIvanov420](https://paypal.me/AndreiIvanov420)

---

## 📦 Installation via Hubitat Package Manager (HPM)

The easiest way to install and update these drivers is using **Hubitat Package Manager (HPM)**.

1.  Open the **Hubitat Package Manager** app on your hub.
2.  Select **Settings** (or "Package Manager Settings").
3.  Click **"Add a Custom Repository"**.
4.  Paste this URL:
    ```text
    [https://raw.githubusercontent.com/aniva/hubitat01/master/repository.json](https://raw.githubusercontent.com/aniva/hubitat01/master/repository.json)
    ```
5.  Click **Save**.
6.  Go back to the main menu and select **Install**.
7.  Search for the desired package (e.g., "Vindstyrka" or "OpenWeatherMap") and follow the prompts.

---

## 🛠️ Available Projects

### 1. [Vindstyrka Air Quality Tile](./VindstyrkaTile)
**Status:** Stable (v2.3)

A comprehensive integration for the **IKEA VINDSTYRKA** Zigbee Air Quality Sensor. It creates a beautiful dashboard tile and provides "smart" logic to filter sensor noise.

* **Features:**
    * **Visual Tile:** Full-bleed CSS tile with Green/Yellow/Red health status.
    * **Smart Trends:** Uses Linear Regression to show trend arrows (↗ ↘), filtering out momentary spikes.
    * **Unified Status:** Crash-proof driver table showing live data vs. WHO thresholds.
    * **Automation Ready:** Exposes `airQualityState` ("good", "fair", "poor") for triggering fans/purifiers.

### 2. [OpenWeatherMap-Alerts (Icon Fix)](./OpenWeatherMap)
**Status:** Stable (v0.7.2 Patch)

A patched version of the popular **OpenWeatherMap-Alerts** driver by Matthew (Scottma61).

* **The Fix:** Replaces the broken `tinyurl.com` icon hosting (which causes broken images on dashboards) with a reliable, direct GitHub link to the **HubitatCommunity/WeatherIcons** library.
* **Note:** This is purely a maintenance patch to restore functionality; all original logic credits belong to the original author.

### 3. [WiMeter Cloud Bridge](./WimeterDriver)
**Status:** Stable

A cloud integration driver for the **WiMeter** energy monitoring solution.

* **Features:** Bridges WiMeter cloud data into Hubitat for energy automation and dashboarding.

### 4. [IKEA TIMMERFLOTTE Matter Sensor](./IkeaTimmerflotte)
**Status:** Initial Release (v0.1.0)

A native, local driver for the **IKEA TIMMERFLOTTE** Temperature & Humidity sensor using **Matter over Thread**.

* **Features:**
    * **Matter Native:** Built using Hubitat's `matter` class for standard compliance and speed.
    * **Border Router Support:** Designed to bridge via standard Thread Border Routers (like DIRIGERA) using IPv6 ULA.
    * **Clean Data:** Reports precise Temperature and Relative Humidity.
    * **Auto-Discovery:** Seamless pairing with specific IKEA fingerprints to avoid "Generic Device" matching.
    * 
---

## ❤️ Support
If you find these drivers useful, you can support the development here:
[PayPal.me/AndreiIvanov420](https://paypal.me/AndreiIvanov420)