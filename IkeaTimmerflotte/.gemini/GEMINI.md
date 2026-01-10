# GEMINI.md - Project Context

**Project:** IKEA TIMMERFLOTTE Sensor
**Type:** Native Matter/Thread Driver
**Standard:** Adheres to [Root Standards](../../.gemini/GEMINI.md)

## 1. Project Overview
A standalone driver for the Timmerflotte Temperature/Humidity sensor using **Matter over Thread**.

## 2. Matter Implementation
* **Class:** Uses `hubitat.matter.Matter`.
* **Endpoints:**
    * `0x01`: Temperature Measurement (Cluster `0x0402`)
    * `0x02`: Relative Humidity (Cluster `0x0405`)
* **DNI:** Assigned automatically by Hubitat fabric. Do NOT overwrite.

## 3. Example Subscriptions

'''groovy
// Standard IKEA Timmerflotte Subscriptions
matter.subscribe(0x0402, 0x0000, [endpointId: "01"]) // Temperature
matter.subscribe(0x0405, 0x0000, [endpointId: "02"]) // Humidity
'''

## 4. Routing
* **IPv6 ULA:** Relies on the hub's ability to route IPv6 traffic via the Border Router. Connection issues are usually network/router configuration issues, not driver logic.