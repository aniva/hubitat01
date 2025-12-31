/**
 *  Vindstyrka Air Quality Tile
 *
 *  Copyright 2025 Aniva
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
metadata {
    definition (name: "Vindstyrka Air Quality Tile", namespace: "aniva", author: "Aniva") {
        capability "Sensor"
        capability "Actuator"

        attribute "html_tile", "string"
        attribute "pm25", "number"
        attribute "voc", "number"
        attribute "trend", "string"
        attribute "airQualityState", "string"

        command "updateAirQuality", [[name:"pm25", type: "NUMBER", description: "Current PM2.5"], [name:"voc", type: "NUMBER", description: "Current VOC Index"]]
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

void updateAirQuality(BigDecimal pm25, BigDecimal voc) {
    if (logEnable) log.debug "Vindstyrka Tile: Received update - PM2.5: ${pm25}, VOC: ${voc}"
    
    String trend = calculateTrend(pm25)
    String state = determineState(pm25, voc)
    
    sendEvent(name: "pm25", value: pm25)
    sendEvent(name: "voc", value: voc)
    sendEvent(name: "trend", value: trend)
    sendEvent(name: "airQualityState", value: state)
    
    String tileHtml = generateHtml(pm25, voc, trend, state)
    sendEvent(name: "html_tile", value: tileHtml)
}

String calculateTrend(BigDecimal currentPm25) {
    BigDecimal prevPm25 = device.currentValue("pm25") ?: 0
    if (currentPm25 > prevPm25 + 2) return "up"
    if (currentPm25 < prevPm25 - 2) return "down"
    return "flat"
}

String determineState(BigDecimal pm25, BigDecimal voc) {
    if (pm25 > 25 || voc > 250) return "poor"
    if (pm25 > 10 || voc > 100) return "fair"
    return "good"
}

String generateHtml(BigDecimal pm25, BigDecimal voc, String trend, String state) {
    String color = "#4CAF50" // Green
    String statusText = "GOOD"
    
    if (state == "fair") { color = "#FFC107"; statusText = "FAIR" } // Amber
    if (state == "poor") { color = "#F44336"; statusText = "POOR" } // Red
    
    String arrow = "&rarr;"
    if (trend == "up") arrow = "&nearr;"
    if (trend == "down") arrow = "&searr;"
    
    return """
    <div style='width:100%;height:100%;background-color:${color};color:white;display:flex;flex-direction:column;justify-content:center;align-items:center;font-family:Roboto,sans-serif;border-radius:4px;text-shadow:1px 1px 2px rgba(0,0,0,0.3);'>
        <div style='font-size:2em;font-weight:bold;line-height:1.1;'>${statusText} <span style='font-size:0.7em'>${arrow}</span></div>
        <div style='font-size:0.9em;margin-top:8px;opacity:0.9;'>PM2.5: <b>${pm25}</b> | VOC: <b>${voc}</b></div>
    </div>
    """
}