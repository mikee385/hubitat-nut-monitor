/**
 *  NUT Monitor App
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
 
String getVersionNum() { return "7.0.0" }
String getVersionLabel() { return "NUT Monitor, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "NUT Monitor",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Listens for events from a UPS monitor and shutdown controller (UPSMON)",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-nut-monitor/master/hubitat/nut-monitor-app.groovy"
)

preferences {
    page(name: "settings", title: "NUT Monitor", install: true, uninstall: true) {
        section {
            input name: "nutServerHost", type: "string", title: "NUT server hostname", description: "IP or hostname of NUT server", required: true
            input name: "nutServerPort", type: "number", title: "NUT server port number", description: "Port number of NUT server", required: true, defaultValue: 3493, range: "1..65535"
            input name: "upsName", type: "string", title: "UPS Name:", required: true
        }
        section("Hub Security") {
            input "hubitatUsername", "string", title: "Hubitat Username", description: "(blank if none)", required: false
	        input "hubitatPassword", "password", title: "Hubitat Password", description: "(blank if none)", required: false
        }
        section("Shutdown Hub") {
            input "shutdownWithUps", "bool", title: "Shutdown hub when UPS is shut down?", required: true, defaultValue: true
        }
        section("Offline") {
            input "offlineDuration", "number", title: "Minimum time before offline (in minutes)", required: true, defaultValue: 2
        }
        section("Monitor Alerts") {
            input "alertNUTStarted", "bool", title: "Alert when NUT Monitor is started?", required: true, defaultValue: true
            input "alertNUTStopped", "bool", title: "Alert when NUT Monitor is stopped?", required: true, defaultValue: true
        }
        section("Shutdown Alerts") {
            input "alertShutdown", "bool", title: "Alert when UPS is shutting down?", required: true, defaultValue: true
        }
        section("Power Alerts") {
            input "alertPowerMains", "bool", title: "Alert when UPS is on mains power?", required: true, defaultValue: true
            input "alertPowerBattery", "bool", title: "Alert when UPS is on battery power?", required: true, defaultValue: true
            input "alertPowerUnknown", "bool", title: "Alert when UPS power is unknown?", required: true, defaultValue: true
        }
        section("Battery Alerts") {
            input "alertBatteryGood", "bool", title: "Alert when UPS battery is good?", required: true, defaultValue: true
            input "alertBatteryLow", "bool", title: "Alert when UPS battery is low?", required: true, defaultValue: true
            input "alertBatteryReplace", "bool", title: "Alert when UPS battery needs to be replaced?", required: true, defaultValue: true
            input "alertBatteryUnknown", "bool", title: "Alert when UPS battery is unknown?", required: true, defaultValue: true
        }
        section {
            input "personToNotify", "capability.notification", title: "Person to Notify", multiple: false, required: true
            input name: "enableDebugLog", type: "bool", title: "Enable debug logging?", defaultValue: false
            label title: "Assign a name", required: true
        }
    }
}

mappings {
    path("/status/:status") {
        action: [
            GET: "urlHandler_status"
        ]
    }
}

def installed() {
    initialize()
}

def uninstalled() {
    for (device in getChildDevices()) {
        deleteChildDevice(device.deviceNetworkId)
    }
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    subscribe(location, "systemStart", systemStart)
    
    // Child Device
    def child = childDevice()
    
    subscribe(child, "powerSource.mains", powerMains)
    subscribe(child, "powerSource.battery", powerBattery)
    subscribe(child, "powerSource.unknown", powerUnknown)
    
    subscribe(child, "battery.good", batteryGood)
    subscribe(child, "battery.low", batteryLow)
    subscribe(child, "battery.replace", batteryReplace)
    subscribe(child, "battery.unknown", batteryUnknown)
    
    subscribe(child, "shutdown.active", shutdown)
    
    child.updateProperties(nutServerHost, nutServerPort, upsName)
    child.sendEvent(name: "shutdown", value: "inactive")
    child.refresh()
    
    // URLs
    if(!state.accessToken) {
        createAccessToken()
    }
    state.statusUrl = "${getFullLocalApiServerUrl()}/status/\$1?access_token=$state.accessToken"
}

def systemStart(evt) {
    initialize()
}

def childDevice() {
    def childID = "nutMonitor:" + app.getId()
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("mikee385", "NUT Monitor", childID, 1234, [label: upsName, isComponent: true])
    }
    return child
}

def start() {
    log.info "NUT Monitor has been started!"
    if (alertNUTStarted) {
        personToNotify.deviceNotification("NUT Monitor has been started!")
    }
    
    unschedule("offline")
    refresh()
}

def stop() {
    log.info "NUT Monitor has been stopped..."
    if (alertNUTStopped) {
        personToNotify.deviceNotification("NUT Monitor has been stopped...")
    }
    
    runIn(60*offlineDuration, offline)
}

def offline() {
    def child = childDevice()
    child.sendEvent(name: "powerSource", value: "unknown")
    child.sendEvent(name: "battery", value: "unknown")
}

def refresh() {
    def child = childDevice()
    child.refresh()
}

def powerMains(evt) {
    def child = childDevice()
    
    log.info "${child} is on mains power!"
    if (alertPowerMains) {
        personToNotify.deviceNotification("${child} is on mains power!")
    }
}

def powerBattery(evt) {
    def child = childDevice()
    
    log.info "${child} is on battery power!"
    if (alertPowerBattery) {
        personToNotify.deviceNotification("${child} is on battery power!")
    }
}

def powerUnknown(evt) {
    def child = childDevice()
    
    log.info "${child} power is unknown!"
    if (alertPowerUnknown) {
        personToNotify.deviceNotification("${child} power is unknown!")
    }
}

def batteryGood(evt) {
    def child = childDevice()
    
    log.info "${child} battery is good!"
    if (alertBatteryGood) {
        personToNotify.deviceNotification("${child} battery is good!")
    }
}

def batteryLow(evt) {
    def child = childDevice()
    
    log.info "${child} battery is low!"
    if (alertBatteryLow) {
        personToNotify.deviceNotification("${child} battery is low!")
    }
}

def batteryReplace(evt) {
    def child = childDevice()
    
    log.info "${child} battery needs to be replaced!"
    if (alertBatteryReplace) {
        personToNotify.deviceNotification("${child} battery needs to be replaced!"
    }
}

def batteryUnknown(evt) {
    def child = childDevice()
    
    log.info "${child} battery is unknown!"
    if (alertBatteryUnknown) {
        personToNotify.deviceNotification("${child} battery is unknown!")
    }
}

def shutdown(evt) {
    def child = childDevice()
    
    log.warn "${child} is shutting down..."
    if (alertShutdown) {
        personToNotify.deviceNotification("${child} is shutting down...")
    }
        
    if (shutdownWithUps) {
        runIn(15, shutdownHub)
    }
    
    child.sendEvent(name: "powerSource", value: "unknown")
    child.sendEvent(name: "battery", value: "unknown")
}

def getLoginCookie() {
    def cookie = ""
    
    if (hubitatUsername && hubitatPassword) {
        httpPost([
            uri: "http://127.0.0.1:8080",
            path: "/login",
            query: [
                loginRedirect: "/"
            ],
            body: [
                username: hubitatUsername,
                password: hubitatPassword,
                submit: "Login"
            ]
        ]) { 
            resp -> cookie = resp?.headers?.'Set-Cookie'?.split(';')?.getAt(0)
        }
    }
    
    return cookie
}

def shutdownHub() {
    def cookie = getLoginCookie()
    
    httpPost([
        uri: "http://127.0.0.1:8080",
        path: "/hub/shutdown",
        headers: [
			"Cookie": cookie
        ]
    ]) {
		resp -> 
	}
}

def urlHandler_status() {
    logDebug("urlHandler_status: received ${params.status}")
    
    def child = childDevice()
    
    if (params.status == "start") {
        start()
    } else if (params.status == "stop") {
        stop()
    } else if (params.status == "refresh") {
        refresh()
    } else if (params.status == "mains") {
        child.sendEvent(name: "powerSource", value: "mains")
    } else if (params.status == "battery") {
        child.sendEvent(name: "powerSource", value: "battery")
    } else if (params.status == "low") {
        child.sendEvent(name: "battery", value: "low")
    } else if (params.status == "replace") {
        child.sendEvent(name: "battery", value: "replace")
    } else if (params.status == "unknown") {
        child.sendEvent(name: "powerSource", value: "unknown")
        child.sendEvent(name: "battery", value: "unknown")
    } else if (params.status == "shutdown") {
        child.sendEvent(name: "shutdown", value: "active") 
    } else {
        log.warn "Unknown status: ${params.status}"
    }
}

def logDebug(msg) {
    if (enableDebugLog) {
        log.debug msg
    }
}