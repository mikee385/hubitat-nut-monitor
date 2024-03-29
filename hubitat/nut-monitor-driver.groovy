/**
 *  NUT Monitor Driver
 *
 *  Copyright 2024 Michael Pierce
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 
String getVersionNum() { return "7.1.0" }
String getVersionLabel() { return "NUT Monitor, version ${getVersionNum()} on ${getPlatform()}" }

 metadata {
    definition (
		name: "NUT Monitor", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-nut-monitor/master/hubitat/nut-monitor-driver.groovy"
	) {
        capability "PowerSource"
        capability "Refresh"
        capability "Sensor"
        capability "Telnet"
        
        attribute "batteryStatus", "enum", ["normal", "low", "replace", "unknown"]
        attribute "shutdown", "enum", ["active", "inactive"]
    }
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false, displayDuringSetup: true
    }
}

def installed() {
    logDebug("installed")
    
    sendEvent(name: "powerSource", value: "unknown")
    sendEvent(name: "batteryStatus", value: "unknown")
    sendEvent(name: "shutdown", value: "inactive")
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def updateProperties(nutServerHost, nutServerPort, upsName) {
    state.nutServerHost = nutServerHost
    state.nutServerPort = nutServerPort
    state.upsName = upsName
}

def refresh() {
    unschedule("terminateConnection")
    telnetClose()
    
    logDebug("refresh")
    
    try {
		telnetConnect([termChars:[10]], state.nutServerHost, state.nutServerPort.toInteger(), null, null)
		sendCommand("GET VAR ${state.upsName} ups.status")
        
	} catch (err) {
	    telnetClose()
		
		log.error "Refresh telnet connection error: ${err}"
		sendEvent(name: "powerSource", value: "unknown")
		sendEvent(name: "batteryStatus", value: "unknown")
	}
}

def terminateConnection() {
    telnetClose()
    
    log.error "No response from telnet command"
	sendEvent(name: "powerSource", value: "unknown")
	sendEvent(name: "batteryStatus", value: "unknown")
}	

def parse(String message) {
    unschedule("terminateConnection")
    
    logDebug("parse: ${message}")
    
    def online = false
    def onbatt = false
    def fsd = false
    def nocomm = false
    def lowbatt = false
    def replbatt = false
    
    def values = message.split(" ")
    if (values[0] == "VAR" && values[1] == state.upsName && values[2] == "ups.status") {
        for (int i = 3; i < values.size(); i++) {
            def status = values[i].replace("\"", "")
            if (status == "OL") {
                online = true
            } else if (status == "OB") {
                onbatt = true
            } else if (status == "FSD") {
                fsd = true
            } else if (status == "OFF") {
                nocomm = true
            } else if (status == "LB") {
                lowbatt = true
            } else if (status == "RB") {
                replbatt = true
            }
        }
        
        if (fsd) {
            logDebug("parse: status is FSD")
            sendEvent(name: "shutdown", value: "active")
        }
        
        if (nocomm) {
            logDebug("parse: status is OFF")
            sendEvent(name: "powerSource", value: "unknown")
            sendEvent(name: "batteryStatus", value: "unknown")
        } else {
            if (onbatt) {
                logDebug("parse: power status is OB")
                sendEvent(name: "powerSource", value: "battery")
            } else if (online) {
                logDebug("parse: power status is OL")
                sendEvent(name: "powerSource", value: "mains")
            } else {
                log.error "Unknown power status: ${message}"
                sendEvent(name: "powerSource", value: "unknown")
            }
            
            if (lowbatt) {
                logDebug("parse: battery status is LB")
                sendEvent(name: "batteryStatus", value: "low")
            } else if (replbatt) {
                logDebug("parse: battery status is RB")
                sendEvent(name: "batteryStatus", value: "replace")
            } else {
                logDebug("parse: no battery status, assuming normal")
                sendEvent(name: "batteryStatus", value: "normal")
            }
        } 
    } else {
        log.error "Unknown message: ${message}"
        sendEvent(name: "powerSource", value: "unknown")
        sendEvent(name: "batteryStatus", value: "unknown")
    }
    
    telnetClose()
}

def telnetStatus(String message) {
    unschedule("terminateConnection")
    
    if (message == "receive error: Stream is closed") {
        logDebug("telnetStatus: ${message}")
    } else {
        log.error "telnetStatus: ${message}"
        sendEvent(name: "powerSource", value: "unknown")
        sendEvent(name: "batteryStatus", value: "unknown")
	}
	
	telnetClose()
}

def sendCommand(cmd) {
	logDebug("sendCommand: ${cmd}")
	runIn(30, terminateConnection)
	return sendHubCommand(new hubitat.device.HubAction("${cmd}", hubitat.device.Protocol.TELNET))
}