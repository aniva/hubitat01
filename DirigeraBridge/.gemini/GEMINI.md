# GEMINI.md - Project Context

**Project:** IKEA DIRIGERA Matter Bridge
**Type:** Parent Driver (Dispatcher)
**Standard:** Adheres to [Root Standards](../../.gemini/GEMINI.md)

## 1. Project Overview
This driver acts as a "Gatekeeper" for the IKEA DIRIGERA Hub. It bypasses Hubitat's generic Matter filters to enable unsupported Zigbee devices (like PARASOLL).

## 2. Architecture Logic
* **Role:** Dispatcher. It receives raw Matter traffic, decodes it, and routes it to Child Devices based on Endpoint ID.
* **Endpoint Logic:** It must check both `descMap.endpointId` AND `descMap.endpoint` to catch all message types.
* **DNI Formation:** Child DNI = `${device.deviceNetworkId}-${endpointId}`.

## 3. Specific Handling
* **Integer vs Hex:** IKEA Matter clusters sometimes report as Integer (69) and sometimes as Hex Strings ("0045"). The `convertToEvents` method handles both.
* **Child Management:** It does not create children automatically; it warns if data arrives for an unknown child, prompting the user to run a Refresh or Pair.