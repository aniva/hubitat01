# IKEA PARASOLL Matter Sensor

A native, local driver for the **IKEA PARASOLL** Door/Window sensor when bridged via the **IKEA DIRIGERA** hub using **Matter**.

## The Problem
When you pair an IKEA PARASOLL sensor to the IKEA DIRIGERA hub and then bridge it to Hubitat via Matter:
* Hubitat often detects it as a generic "Matter Contact Sensor."
* The battery levels may report incorrectly (Matter uses a 0-200 scale, while Hubitat expects 0-100%).
* It lacks custom icons and version tracking.

## The Solution
**IkeaParasoll** is a dedicated driver that:
1.  **Corrects Battery Logic:** Automatically converts the Matter 0-200 raw value into a precise 0-100% percentage.
2.  **Supports Inversion:** Includes a preference to reverse "Open/Close" logic if needed.
3.  **Aniva Standard:** Features the standard status header, version tracking, and custom icon.

## Installation

### Method 1: Hubitat Package Manager (Recommended)
1.  Open **HPM**.
2.  Search for **"IkeaParasoll"** (by Aniva).
3.  Install.

### Method 2: Manual Install
1.  Copy the code from `IkeaParasoll.groovy`.
2.  In Hubitat, go to **Drivers Code** -> **New Driver**.
3.  Paste and Save.

## How to Add Device to Hubitat

**Important:** You do not pair the PARASOLL sensor directly to Hubitat. You pair it to the **IKEA DIRIGERA Hub**, and the Hub passes it to Hubitat via Matter.

### Step 1: Pair Sensor to IKEA DIRIGERA
1.  Open the **IKEA Home Smart** app.
2.  Follow the standard instructions to add the PARASOLL sensor to your IKEA system.

### Step 2: Sync with Hubitat
* **Scenario A: Your DIRIGERA Hub is ALREADY connected to Hubitat via Matter.**
    * The new sensor should appear in your Hubitat "Devices" list automatically within a minute.
    * *Troubleshooting:* If it doesn't appear, go to your **Matter Bridge** device in Hubitat (the device representing the Dirigera Hub) and click **Refresh** or **Initialize**.

* **Scenario B: This is your FIRST time connecting IKEA to Hubitat.**
    1.  In the IKEA App, go to **User Profile** (icon) -> **Integrations** -> **Matter Bridge**.
    2.  Enable the bridge to generate a **QR Code**.
    3.  In Hubitat, go to **Devices** -> **Add Device** -> **Matter**.
    4.  Scan the IKEA QR code. This will add the Hub and ALL your Zigbee devices (including the Parasoll) to Hubitat at once.

### Step 3: Assign the Driver
Hubitat usually selects a "Generic Matter Contact Sensor" driver by default. To use this custom driver:

1.  Go to the **Device Page** of your new Parasoll sensor in Hubitat.
2.  Scroll down to **Device Information**.
3.  Under **Type**, search for and select: **IKEA PARASOLL Matter Sensor** (by Aniva).
4.  Click **Save Device**.
5.  Click the **Configure** button at the top of the page to sync settings.

## Features
* **Battery Precision:** Converts Matter's 0.5% steps into readable integer percentages.
* **Logic Reversal:** Toggle "Reverse Open/Close Logic" in preferences if your mounting position requires it.
* **Local Speed:** Runs entirely locally over your LAN (via IPv6 Thread/Matter).

## Support

If you find this driver useful, consider supporting the development:

* [PayPal Support](https://paypal.me/AndreiIvanov420)
* [GitHub Repository](https://github.com/aniva)

---
**Author:** Aniva
**License:** Apache 2.0