/**
 * Vindstyrka Air Quality Tile
 * v2.3 - Custom Thresholds & Unified Status Table
 */
metadata {
    definition (name: "Vindstyrka Air Quality Tile", namespace: "aniva", author: "Aniva") {
        capability "Sensor"
        capability "Actuator"
        capability "Initialize"
        capability "Refresh"

        attribute "_version", "string"
        attribute "html_tile", "string"       // For Dashboard
        attribute "status_table", "string"    // For Driver Page (Live + Legend)
        attribute "pm25", "number"
        attribute "voc", "number"
        attribute "trend", "string"
        attribute "airQualityState", "string"

        command "updateAirQuality", [[name:"pm25", type: "NUMBER"], [name:"voc", type: "NUMBER"]]
        command "reload"
        command "clearHistory"
    }

    preferences {
        section("<b>Threshold Configuration</b>") {
            input name: "limitPmFair", type: "number", title: "PM2.5 'Fair' Limit", description: "Above this is Fair (Default: 15)", defaultValue: 15
            input name: "limitPmPoor", type: "number", title: "PM2.5 'Poor' Limit", description: "Above this is Poor (Default: 35)", defaultValue: 35
            
            input name: "limitVocFair", type: "number", title: "VOC 'Fair' Limit", description: "Above this is Fair (Default: 150)", defaultValue: 150
            input name: "limitVocPoor", type: "number", title: "VOC 'Poor' Limit", description: "Above this is Poor (Default: 300)", defaultValue: 300
        }
        
        section("<b>Trend Logic</b>") {
            input name: "trendWindow", type: "number", title: "Trend Window (Minutes)", description: "Analyze the slope of the last N minutes.", defaultValue: 30, range: "5..360"
        }
        
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def driverVersion() { return "2.3" }

def installed() { initialize() }

def updated() { 
    initialize() 
    pruneHistory()
    // Trigger a refresh so the table updates with new threshold numbers immediately
    refresh()
}

def initialize() {
    sendEvent(name: "_version", value: driverVersion())
    if (state.history == null) state.history = []
    log.info "Vindstyrka Tile driver initialized (v${driverVersion()})"
}

def reload() { 
    log.info "Reloading driver logic..."
    refresh()
}

def refresh() {
    BigDecimal lastPm25 = device.currentValue("pm25")
    BigDecimal lastVoc = device.currentValue("voc")
    
    // Even if no data exists, we want to draw the table (with empty values) so the Legend appears
    if (lastPm25 == null) lastPm25 = 0
    if (lastVoc == null) lastVoc = 0
    
    updateAirQuality(lastPm25, lastVoc)
}

def clearHistory() {
    state.history = []
    log.warn "Trend history cleared."
}

void updateAirQuality(BigDecimal pm25, BigDecimal voc) {
    long now = new Date().getTime()
    
    // 1. History Management
    if (state.history == null) state.history = []
    
    def lastEntry = state.history ? state.history.last() : null
    boolean isDuplicate = lastEntry && (lastEntry.p == pm25) && (lastEntry.v == voc)
    
    if (!isDuplicate) {
        state.history.add([t: now, p: pm25, v: voc])
    }
    
    // 2. Prune
    pruneHistory()
    
    // 3. Calculate Trend
    String trend = calculateSlopeTrend(now)
    
    // 4. Update Attributes
    String stateStr = determineState(pm25, voc)
    
    sendEvent(name: "pm25", value: pm25)
    sendEvent(name: "voc", value: voc)
    sendEvent(name: "trend", value: trend)
    sendEvent(name: "airQualityState", value: stateStr)
    
    if (logEnable) log.debug "Update v${driverVersion()}: PM=${pm25}, VOC=${voc}, Trend=${trend}"
    
    // 5. Generate Visuals
    String tileHtml = generateHtml(pm25, voc, trend, stateStr)
    sendEvent(name: "html_tile", value: tileHtml)
    
    // Generate the Unified Table (Live + Legend)
    String statusTable = generateUnifiedTable(pm25, voc, stateStr)
    sendEvent(name: "status_table", value: statusTable)
}

void pruneHistory() {
    if (!state.history) return
    int windowMin = trendWindow ?: 30
    long cutoff = new Date().getTime() - (windowMin * 60 * 1000) - (5 * 60 * 1000)
    state.history = state.history.findAll { it.t > cutoff }
}

String calculateSlopeTrend(long now) {
    if (!state.history || state.history.size() < 3) return "flat"
    
    int windowMin = trendWindow ?: 30
    long startTime = now - (windowMin * 60 * 1000)
    
    def dataPoints = state.history.findAll { it.t >= startTime }
    if (dataPoints.size() < 2) return "flat"

    double slopePm = getSlope(dataPoints, "p")
    double slopeVoc = getSlope(dataPoints, "v")

    double projectedChangePm = slopePm * windowMin
    double projectedChangeVoc = slopeVoc * windowMin

    if (projectedChangePm > 2.0 || projectedChangeVoc > 10.0) return "up"
    if (projectedChangePm < -2.0 || projectedChangeVoc < -10.0) return "down"
    return "flat"
}

double getSlope(List data, String key) {
    def n = data.size()
    if (n < 2) return 0.0
    
    double sumX = 0.0
    double sumY = 0.0
    double sumXY = 0.0
    double sumXX = 0.0
    
    long firstTime = data[0].t
    
    data.each { pt ->
        double x = (pt.t - firstTime) / 60000.0 
        double y = pt[key].toDouble()
        sumX += x
        sumY += y
        sumXY += (x * y)
        sumXX += (x * x)
    }
    
    double denominator = (n * sumXX) - (sumX * sumX)
    if (denominator == 0) return 0.0
    return ((n * sumXY) - (sumX * sumY)) / denominator
}

String determineState(BigDecimal pm25, BigDecimal voc) {
    // Retrieve custom limits or use defaults
    def pPoor = limitPmPoor ?: 35
    def pFair = limitPmFair ?: 15
    def vPoor = limitVocPoor ?: 300
    def vFair = limitVocFair ?: 150

    if (pm25 > pPoor || voc > vPoor) return "poor"
    if (pm25 > pFair || voc > vFair) return "fair"
    return "good"
}

String generateHtml(BigDecimal pm25, BigDecimal voc, String trend, String state) {
    String cardColor = "#27ae60" // Green
    if (state == "fair") cardColor = "#f1c40f" // Yellow
    if (state == "poor") cardColor = "#c0392b" // Red
    
    String arrow = "&rarr;"
    if (trend == "up") arrow = "&nearr;"
    if (trend == "down") arrow = "&searr;"
    
    return """
    <div style='
        width: 95% !important; 
        height: 85% !important;
        margin-top: 5% !important;
        margin-left: 0% !important;
        margin-right: -40% !important;
        margin-bottom: 15% !important;
        background-color: ${cardColor}; 
        color: white;
        display: flex; 
        flex-direction: column; 
        align-items: center; 
        justify-content: center;
        border-radius: 5px;
        text-shadow: 1px 1px 2px rgba(0,0,0,0.2);
    '>
        <div style='font-size:0.8rem; text-transform:uppercase; opacity:0.9; margin-bottom:0px;'>PM2.5 | VOC</div>
        <div style='font-size:1.5rem; font-weight:bold; line-height:1.1;'>
            ${pm25} | ${voc} <span style='font-size:0.8em'>${arrow}</span>
        </div>
    </div>
    """
}

// Generates the Unified Table (Live Data + Dynamic Legend)
String generateUnifiedTable(BigDecimal pm25, BigDecimal voc, String state) {
    // Get Thresholds for display
    def pPoor = limitPmPoor ?: 35
    def pFair = limitPmFair ?: 15
    def vPoor = limitVocPoor ?: 300
    def vFair = limitVocFair ?: 150
    
    // Live Row Color
    def stateColor = "#7f8c8d"
    if (state == "good") stateColor = "#27ae60"
    if (state == "fair") stateColor = "#f1c40f"
    if (state == "poor") stateColor = "#c0392b"

    return """
    <table style='width:350px; font-size:12px; border-collapse:collapse; text-align:center; border:1px solid #ddd; font-family:Arial,sans-serif;'>
        <tr style='background-color:#f4f4f4; border-bottom:1px solid #ccc;'>
            <th style='padding:6px; text-align:left;'>STATUS</th>
            <th style='padding:6px;'>PM2.5</th>
            <th style='padding:6px;'>VOC</th>
        </tr>
        
        <tr style='background-color:#fff; font-weight:bold; border-bottom:2px solid #aaa;'>
            <td style='padding:8px; text-align:left; color:${stateColor}; font-size:1.1em;'>● ${state.toUpperCase()}</td>
            <td style='padding:8px; font-size:1.1em;'>${pm25}</td>
            <td style='padding:8px; font-size:1.1em;'>${voc}</td>
        </tr>

        <tr style='background-color:#fafafa; font-size:10px; color:#666;'>
            <td colspan='3' style='padding:4px; border-top:1px solid #eee;'><i>Reference Thresholds</i></td>
        </tr>

        <tr style='color:#666;'>
            <td style='padding:4px; text-align:left;'><span style='color:#27ae60;'>■</span> Good</td>
            <td>0 - ${pFair}</td>
            <td>0 - ${vFair}</td>
        </tr>
        <tr style='color:#666;'>
            <td style='padding:4px; text-align:left;'><span style='color:#f1c40f;'>■</span> Fair</td>
            <td>${pFair} - ${pPoor}</td>
            <td>${vFair} - ${vPoor}</td>
        </tr>
        <tr style='color:#666;'>
            <td style='padding:4px; text-align:left;'><span style='color:#c0392b;'>■</span> Poor</td>
            <td>&gt; ${pPoor}</td>
            <td>&gt; ${vPoor}</td>
        </tr>
    </table>
    """
}