/**
 * Vindstyrka Air Quality Tile
 * v1.6 - WiMeter Style & Refined Thresholds
 */
metadata {
    definition (name: "Vindstyrka Air Quality Tile", namespace: "aniva", author: "Aniva") {
        capability "Sensor"
        capability "Actuator"
        capability "Initialize"

        attribute "_version", "string"
        attribute "html_tile", "string"
        attribute "pm25", "number"
        attribute "voc", "number"
        attribute "trend", "string"
        attribute "airQualityState", "string"

        command "updateAirQuality", [[name:"pm25", type: "NUMBER"], [name:"voc", type: "NUMBER"]]
        command "reload"
        command "clearHistory"
    }

    preferences {
        input name: "trendWindow", type: "number", title: "Trend Window (Minutes)", description: "Analyze the slope of the last N minutes.", defaultValue: 30, range: "5..360"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def driverVersion() { return "1.6" }

def installed() { initialize() }

def updated() { 
    initialize() 
    pruneHistory()
}

def initialize() {
    sendEvent(name: "_version", value: driverVersion())
    if (state.history == null) state.history = []
    log.info "Vindstyrka Tile driver initialized (v${driverVersion()})"
}

def reload() { initialize() }

def clearHistory() {
    state.history = []
    log.warn "Trend history cleared."
}

void updateAirQuality(BigDecimal pm25, BigDecimal voc) {
    long now = new Date().getTime()
    
    // 1. Add History
    if (state.history == null) state.history = []
    state.history.add([t: now, p: pm25, v: voc])
    
    // 2. Prune
    pruneHistory()
    
    // 3. Calculate Trend
    String trend = calculateSlopeTrend(now)
    
    // 4. Update Attributes & Determine Color State
    String stateStr = determineState(pm25, voc)
    
    sendEvent(name: "pm25", value: pm25)
    sendEvent(name: "voc", value: voc)
    sendEvent(name: "trend", value: trend)
    sendEvent(name: "airQualityState", value: stateStr)
    
    if (logEnable) log.debug "Update v${driverVersion()}: PM=${pm25}, VOC=${voc}, Trend=${trend}"
    
    // 5. Generate Tile (WiMeter Style)
    String tileHtml = generateHtml(pm25, voc, trend, stateStr)
    sendEvent(name: "html_tile", value: tileHtml)
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
    // WHO Guidelines (Approx 24h mean) & Sensirion Index
    // Green: PM < 15, VOC < 150
    // Yellow: PM < 35, VOC < 300
    // Red: Anything higher
    
    if (pm25 > 35 || voc > 300) return "poor"
    if (pm25 > 15 || voc > 150) return "fair"
    return "good"
}

String generateHtml(BigDecimal pm25, BigDecimal voc, String trend, String state) {
    String cardColor = "#27ae60" // Green
    if (state == "fair") cardColor = "#f1c40f" // Yellow
    if (state == "poor") cardColor = "#c0392b" // Red
    
    String arrow = "&rarr;"
    if (trend == "up") arrow = "&nearr;"
    if (trend == "down") arrow = "&searr;"
    
    // WiMeter CSS Style
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