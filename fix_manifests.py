import zipfile
import json
import os

# --- The Corrected Manifest Data ---
manifests = {
    "VindstyrkaTile/packageManifest.json": {
        "packageName": "Vindstyrka Air Quality Tile",
        "author": "Aniva",
        "version": "2.3.0",
        "minimumHEVersion": "2.3.0",
        "dateReleased": "2026-01-17",
        "documentationLink": "https://github.com/aniva/hubitat01/blob/master/VindstyrkaTile/README.md",
        "licenseFile": "https://raw.githubusercontent.com/aniva/hubitat01/master/LICENSE",
        "payPalUrl": "https://paypal.me/AndreiIvanov420",
        "releaseNotes": "v2.3.0 - Stable Release. Updated manifest for HPM 1.9.x compatibility. Includes unified status table and configurable thresholds.",
        "drivers": [
            {
                "id": "vindstyrka-tile-driver",
                "name": "Vindstyrka Air Quality Tile",
                "namespace": "aniva",
                "location": "https://raw.githubusercontent.com/aniva/hubitat01/master/VindstyrkaTile/AirQualityTile.groovy",
                "required": True
            }
        ]
    },
    "IkeaTimmerflotte/packageManifest.json": {
        "packageName": "IKEA TIMMERFLOTTE Matter Sensor",
        "author": "Aniva",
        "version": "1.0.11",
        "minimumHEVersion": "2.3.6",
        "dateReleased": "2026-01-02",
        "documentationLink": "https://github.com/aniva/hubitat01/blob/master/IkeaTimmerflotte/README.md",
        "licenseFile": "https://raw.githubusercontent.com/aniva/hubitat01/master/LICENSE",
        "payPalUrl": "https://paypal.me/AndreiIvanov420",
        "releaseNotes": "v1.0.11 - Updated Release of dedicated Matter driver for IKEA TIMMERFLOTTE Temp & Humidity sensor.",
        "drivers": [
            {
                "id": "c62f84b6-71d3-4a12-9c12-321345678912",
                "name": "IKEA TIMMERFLOTTE Matter Sensor",
                "namespace": "aniva",
                "location": "https://raw.githubusercontent.com/aniva/hubitat01/master/IkeaTimmerflotte/IkeaTimmerflotte.groovy",
                "required": True
            }
        ]
    },
    "WimeterDriver/packageManifest.json": {
        "packageName": "WiMeter Cloud Bridge",
        "author": "Aniva",
        "version": "4.15",
        "minimumHEVersion": "2.3.0",
        "dateReleased": "2026-01-01",
        "documentationLink": "https://github.com/aniva/hubitat01/blob/master/WimeterDriver/README.md",
        "licenseFile": "https://raw.githubusercontent.com/aniva/hubitat01/master/LICENSE",
        "payPalUrl": "https://paypal.me/AndreiIvanov420",
        "releaseNotes": "Major v4.15 Cumulative Update. \n\n1. ATTRIBUTE REFACTOR: Standardized all state variables to CamelCase. \n2. DASHBOARD TILE: Added configurable 'Live Status' HTML tile. \n3. AUTOMATION: Added 'powerLevel' attribute.",
        "drivers": [
            {
                "id": "92d05738-9572-4d04-9549-044738734960",
                "name": "WiMeter Cloud Bridge",
                "namespace": "aniva",
                "location": "https://raw.githubusercontent.com/aniva/hubitat01/master/WimeterDriver/WiMeterCloudBridge.groovy",
                "required": True
            },
            {
                "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
                "name": "WiMeter Child Device",
                "namespace": "aniva",
                "location": "https://raw.githubusercontent.com/aniva/hubitat01/master/WimeterDriver/WiMeterCloudBridgeChild.groovy",
                "required": True
            }
        ]
    },
    "OpenWeatherMap/packageManifest.json": {
        "packageName": "OpenWeatherMap-Alerts (Icon Fix)",
        "author": "Scottma61 (Original) / Aniva (Patch)",
        "version": "0.7.2",
        "minimumHEVersion": "2.3.0",
        "dateReleased": "2025-01-01",
        "documentationLink": "https://github.com/aniva/hubitat01/blob/master/OpenWeatherMap/README.md",
        "licenseFile": "https://raw.githubusercontent.com/aniva/hubitat01/master/LICENSE",
        "payPalUrl": "https://paypal.me/AndreiIvanov420",
        "releaseNotes": "Fixed broken weather icons by replacing dead TinyURL link with HubitatCommunity GitHub source.",
        "description": "Patched version of OpenWeatherMap-Alerts driver to fix broken image links.",
        "drivers": [
            {
                "id": "98234-owm-alerts-fix",
                "name": "OpenWeatherMap-Alerts Weather Driver",
                "namespace": "Matthew",
                "location": "https://raw.githubusercontent.com/aniva/hubitat01/master/OpenWeatherMap/OpenWeatherMap.groovy",
                "required": True
            }
        ]
    },
    "IkeaParasoll/packageManifest.json": {
        "packageName": "IKEA PARASOLL Matter Zigbee Sensor",
        "author": "Aniva",
        "version": "2.2.0",
        "minimumHEVersion": "2.3.0",
        "dateReleased": "2026-01-09",
        "documentationLink": "https://github.com/aniva/hubitat01/blob/master/IkeaParasoll/README.md",
        "licenseFile": "https://raw.githubusercontent.com/aniva/hubitat01/master/LICENSE",
        "payPalUrl": "https://paypal.me/AndreiIvanov420",
        "releaseNotes": "Initial Release: Dedicated Matter driver for IKEA PARASOLL Door/Window sensor.",
        "drivers": [
            {
                "id": "55d97475-1833-5527-0162-8901366f8257",
                "name": "IKEA PARASOLL Matter Zigbee Sensor",
                "namespace": "aniva",
                "location": "https://raw.githubusercontent.com/aniva/hubitat01/master/IkeaParasoll/IkeaParasoll.groovy",
                "required": True
            }
        ]
    },
    "VirtualBattery/packageManifest.json": {
        "packageName": "Virtual Smart Battery (Mutable)",
        "author": "Aniva",
        "version": "2.1.6",
        "minimumHEVersion": "2.2.0",
        "dateReleased": "2026-01-05",
        "documentationLink": "https://github.com/aniva/hubitat01/blob/master/VirtualBattery/README.md",
        "licenseFile": "https://raw.githubusercontent.com/aniva/hubitat01/master/LICENSE",
        "payPalUrl": "https://paypal.me/AndreiIvanov420",
        "releaseNotes": "v2.1.6: Major Overhaul! Transformed into a 'Smart Simulator' with gradual battery drain, 'Time Machine' date selection for past installs, and adaptive learning that adjusts to your real battery usage. Includes new 'setSourceStatus' command for Rule Machine and fixed UI rendering.",
        "drivers": [
            {
                "id": "94c86364-0722-4416-9051-7890255e7146",
                "name": "Virtual Mutable Battery",
                "namespace": "aniva",
                "location": "https://raw.githubusercontent.com/aniva/hubitat01/master/VirtualBattery/VirtualBattery.groovy",
                "required": True
            }
        ]
    },
    "DirigeraBridge/packageManifest.json": {
        "packageName": "IKEA DIRIGERA Bridge",
        "author": "Aniva",
        "version": "1.1.0",
        "minimumHEVersion": "2.3.0",
        "dateReleased": "2026-01-10",
        "documentationLink": "https://github.com/aniva/hubitat01/blob/master/DirigeraBridge/README.md",
        "licenseFile": "https://raw.githubusercontent.com/aniva/hubitat01/master/LICENSE",
        "payPalUrl": "https://paypal.me/AndreiIvanov420",
        "releaseNotes": "Initial Release: Custom Matter Bridge driver for IKEA DIRIGERA Hub.",
        "drivers": [
            {
                "id": "9102845d-523c-48b8-8259-165839201234",
                "name": "IKEA DIRIGERA Bridge",
                "namespace": "aniva",
                "location": "https://raw.githubusercontent.com/aniva/hubitat01/master/DirigeraBridge/DirigeraBridge.groovy",
                "required": True
            }
        ]
    }
}

# --- Create the Zip File ---
zip_filename = "manifests_fixed.zip"
print(f"Generating {zip_filename}...")

with zipfile.ZipFile(zip_filename, 'w', zipfile.ZIP_DEFLATED) as zipf:
    for file_path, data in manifests.items():
        # JSON formatted string
        json_str = json.dumps(data, indent=2)
        # Write to zip with correct folder structure
        zipf.writestr(file_path, json_str)
        print(f"  - Added: {file_path}")

print("\nDone! Unpack 'manifests_fixed.zip' into your project root.")