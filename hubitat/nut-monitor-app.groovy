/**
 *  NUT Monitor App
 *
 *  Copyright 2023 Michael Pierce
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
 
String getVersionNum() { return "5.0.0" }
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
        section("Alerts") {
            input "alertStarted", "bool", title: "Alert when NUT Monitor is started?", required: true, defaultValue: true
            input "alertStopped", "bool", title: "Alert when NUT Monitor is stopped?", required: true, defaultValue: true
            input "alertMains", "bool", title: "Alert when UPS is on mains power?", required: true, defaultValue: true
            input "alertBattery", "bool", title: "Alert when UPS is on battery power?", required: true, defaultValue: true
            input "alertUnknown", "bool", title: "Alert when UPS power is unknown?", required: true, defaultValue: true
            input "alertShutdown", "bool", title: "Alert when UPS is shutting down?", required: true, defaultValue: true
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
    if (alertStarted) {
        personToNotify.deviceNotification("NUT Monitor has been started!")
    }
    
    unschedule("offline")
    refresh()
}

def stop() {
    log.info "NUT Monitor has been stopped..."
    if (alertStopped) {
        personToNotify.deviceNotification("NUT Monitor has been stopped...")
    }
    
    runIn(60*offlineDuration, offline)
}

def offline() {
    unknown()
}

def refresh() {
    def child = childDevice()
    child.refresh()
}

def mains() {
    def child = childDevice()
    
    log.info "${child} is on mains power!"
    if (alertMains) {
        personToNotify.deviceNotification("${child} is on mains power!")
    }
    
    child.sendEvent(name: "powerSource", value: "mains")
}

def battery() {
    def child = childDevice()
    
    log.info "${child} is on battery power!"
    if (alertBattery) {
        personToNotify.deviceNotification("${child} is on battery power!")
    }
    
    child.sendEvent(name: "powerSource", value: "battery")
}

def unknown() {
    def child = childDevice()
    
    log.info "${child} power is unknown!"
    if (alertUnknown) {
        personToNotify.deviceNotification("${child} power is unknown!")
    }
    
    child.sendEvent(name: "powerSource", value: "unknown")
}

def shutdown() {
    def child = childDevice()
    
    log.warn "${child} is shutting down..."
    if (alertShutdown) {
        personToNotify.deviceNotification("${child} is shutting down...")
    }
        
    if (child.currentValue("shutdown") == "inactive" && shutdownWithUps) {
        child.sendEvent(name: "shutdown", value: "active")
        runIn(15, shutdownHub)
    }
    
    child.sendEvent(name: "powerSource", value: "unknown")
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
    
    if (params.status == "start") {
        start()
    } else if (params.status == "stop") {
        stop()
    } else if (params.status == "refresh") {
        refresh()
    } else if (params.status == "mains") {
        mains()
    } else if (params.status == "battery") {
        battery()
    } else if (params.status == "unknown") {
        unknown()
    } else if (params.status == "shutdown") {
        shutdown()
    } else {
        log.warn "Unknown status: ${params.status}"
    }
}

def logDebug(msg) {
    if (enableDebugLog) {
        log.debug msg
    }
}