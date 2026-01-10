## How to Add Device to Hubitat

**Important:** You do not pair the PARASOLL sensor directly to Hubitat. You pair it to the **IKEA DIRIGERA Hub**, and the Hub passes it to Hubitat via the existing Matter Bridge connection.

### Step 1: Pair Sensor to IKEA DIRIGERA
1.  Open the **IKEA Home Smart** app.
2.  Follow the standard instructions to add the PARASOLL sensor to your IKEA system.

### Step 2: Sync with Hubitat
* **If you ALREADY use IKEA devices (like Timmerflotte) in Hubitat:**
    * **STOP.** Do not scan any QR codes.
    * The new sensor will automatically appear in your Hubitat "Devices" list within 1-2 minutes.
    * *Troubleshooting:* If it does not appear, go to the device in Hubitat named **"Matter Bridge"** (or whatever you named your Dirigera Hub) and click the **Refresh** button. This forces Hubitat to ask IKEA for a list of new devices.

* **If this is your FIRST time connecting IKEA to Hubitat:**
    1.  In the IKEA App, go to **User Profile** (icon) -> **Integrations** -> **Matter Bridge**.
    2.  Enable the bridge to generate a **QR Code**.
    3.  In Hubitat, go to **Devices** -> **Add Device** -> **Matter**.
    4.  Scan the IKEA QR code. This will add the Hub and ALL your Zigbee devices to Hubitat at once.

### Step 3: Assign the Driver
Hubitat usually selects a "Generic Matter Contact Sensor" driver by default. To use this custom driver:

1.  Go to the **Device Page** of your new Parasoll sensor in Hubitat.
2.  Scroll down to **Device Information**.
3.  Under **Type**, search for and select: **IKEA PARASOLL Matter Sensor** (by Aniva).
4.  Click **Save Device**.
5.  Click the **Configure** button at the top of the page to sync settings.