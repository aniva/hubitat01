# WiMeter Cloud Bridge for Hubitat

**Version:** v4.8  
**Author:** Andrei Ivanov (aniva)  
**License:** Open Source

## Overview
This driver connects your WiMeter energy monitor to Hubitat Elevation. It polls the WiMeter Cloud API to retrieve real-time power (W/kW), energy (kWh), and cost ($) data for your main location and all individual appliances.

It uses a **Parent-Child** architecture:
* **Parent Device:** Represents the main location (e.g., "Andrei's House").
* **Child Devices:** Automatically created for every appliance found in your WiMeter account (e.g., "Boiler", "A/C").

---

## 1. Installation (Hubitat Package Manager)
The recommended way to install and keep this driver up to date is via **Hubitat Package Manager (HPM)**.

### Option A: If the driver is in the official HPM Repository
1.  Open **Hubitat Package Manager**.
2.  Select **Install**.
3.  Select **Search by Keywords** and type `WiMeter` (or search by tag `Energy`).
4.  Select **WiMeter Cloud Bridge** and click **Install**.

### Option B: If adding Manually (Custom Repository)
If the package is not yet listed in the main repository, you can add it directly using the manifest URL:

1.  Open **Hubitat Package Manager**.
2.  Select **Settings** (the gear icon or "Package Manager Settings").
3.  Click **Add a Custom Repository**.
4.  Paste this URL into the field:
    `https://raw.githubusercontent.com/aniva/hubitat01/master/WimeterDriver/packageManifest.json`
5.  Click **Save**.
6.  Go back to the main menu and select **Install**.
7.  Select **Search by Name** (or Keywords) and search for `WiMeter`.
8.  Select **WiMeter Cloud Bridge** and click **Install**.

---

## 2. Configuration
After installing the driver, open the **WiMeter Cloud Bridge** device page and scroll to the **Preferences** section to configure the connection.

### Required Settings
1.  **WiMeter API URL:** Paste your personal API key URL here (e.g., `https://wimeter.net/v1/pubmatrix?key=...`).
2.  **Target Location Name:** Enter the exact name of the location as it appears in your WiMeter dashboard (e.g., `Andrei's House`).
3.  **Polling Interval:** Select how often Hubitat should check for new data.
    * *Recommended:* **5 Minutes**.
    * *Options:* Manual, 1 Minute, 5 Minutes, 15 Minutes, 30 Minutes.
4.  **Enable Debug Logging:** Turn this **On** if you are troubleshooting connection issues. (Automatically turns off after 30 minutes).

*(Click **Save Preferences** after making changes)*

---

## 3. Dashboard Setup: The "Live Status" Card
This driver generates a pre-formatted, color-coded HTML tile (Green/Yellow/Red) that displays real-time power usage.

### How to Add the Tile
1.  Open your Hubitat **Dashboard**.
2.  Click **Add Tile (+)**.
3.  **Pick a Device:** Select your `WiMeter Cloud Bridge` (or any Child Device).
4.  **Pick a Template:** Select **Attribute**.
5.  **Pick an Attribute:** Select **`apiStatus`**.
    * *Note: We use `apiStatus` because it reliably displays the HTML graphic without caching issues.*
6.  **Click Add Tile.**

---

## 4. Optional: Bridging to a "Virtual Omni Sensor"
If you prefer to use standard Hubitat "Power Meter" tiles or integrate with other apps (like HomeKit bridges), you can sync the data to a standard **Virtual Omni Sensor**.

### Step 1: Create the Virtual Device
1.  Go to **Devices** -> **Add Virtual Device**.
2.  **Device Name:** "House Power Bridge" (or similar).
3.  **Type:** **`Virtual Omni Sensor`**.
4.  Click **Save Device**.

### Step 2: Create the Sync Rule (Rule Machine)
Since standard dashboards cannot read custom attributes like `location_power_real-time_w` directly, we use Rule Machine to push the value to the Virtual Sensor.

1.  Open **Rule Machine** -> **Create New Rule**.
2.  **Name:** "Sync House Power Meter".
3.  **Select Trigger Events:**
    * **Capability:** `Custom Attribute`.
    * **Device:** `WiMeter Cloud Bridge`.
    * **Attribute:** `location_power_real-time_w` (Note: Use `_w`, NOT `_kw`).
    * **Comparison:** `*changed*`.
4.  **Select Actions to Run:**
    * **Action Type:** `Run Custom Action`.
    * **Select Capability:** `Sensor` (or Actuator/Custom).
    * **Select Device:** "House Power Bridge" (Your Virtual Omni Sensor).
    * **Select Custom Command:** **`setPower`**.
    * **Parameter 1:**
        * **Type:** `Decimal` (or Number).
        * **Value:** `%value%` (This passes the watts reading).
5.  Click **Done**.

**Result:** Your "Virtual Omni Sensor" will now show the correct power reading in standard apps, and you can use the standard "Power Meter" template on dashboards.