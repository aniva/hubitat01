# Aniva's Hubitat Integration Projects

Welcome to my personal collection of custom drivers and integrations for the Hubitat Elevation platform.

## Projects

### 1. WiMeter Cloud Bridge Driver
A hybrid Parent/Child driver system that bridges the WiMeter Energy Monitoring API to Hubitat. It supports real-time power monitoring, cost calculation, and automatic appliance discovery.

* **Current Version:** v4.10
* **Key Features:** Real-time polling, HTML Dashboard tiles, and strict attribute naming (kW/kWh/$).
* **Documentation:** [Read full documentation and installation instructions](WimeterDriver/README.md)

### 2. IKEA VINDSTYRKA Air Quality Tile
This project enhances the IKEA VINDSTYRKA Zigbee Air Quality Sensor. While the sensor pairs natively with Hubitat, the raw data is noisy and lacks visual "At-a-glance" dashboard elements.

* **Currrent Version** v1.0
* **Key Features** Visual Dashboard Tile with health status colors, Trend Indicators, and Spike Smoothing logic.
* **Documentation**: [Read full documentation and installation instructions](VindstyrkaTile/README.md)