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
 
String getVersionNum() { return "4.6.0" }
String getVersionLabel() { return "NUT Monitor, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library

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
            input name: "nutServerHost", type: "string", description: "IP or hostname of NUT server", title: "NUT server hostname", required: true
            input name: "nutServerPort", type: "number", description: "Port number of NUT server", title: "NUT server port number", required: true, defaultValue: 3493, range: "1..65535"
            input name: "upsName", type: "string", title: "UPS Name:", required: true
        }
        section("Shutdown Hub") {
            input "shutdownWithUps", "bool", title: "Shutdown hub when UPS is shut down?", required: true, defaultValue: true
        }
        section("Alerts") {
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
    state.shuttingDown = false
    
    // Child Device
    def child = childDevice()
    child.updateProperties(nutServerHost, nutServerPort, upsName)
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
        
    if (!state.shuttingDown && shutdownWithUps) {
        state.shuttingDown = true
        runIn(15, shutdownHub)
    }
    
    child.sendEvent(name: "powerSource", value: "unknown")
}

def shutdownHub() {
    httpPost("http://127.0.0.1:8080/hub/shutdown", "") { resp -> }
}

def urlHandler_status() {
    logDebug("urlHandler_status: received ${params.status}")
    
    if (params.status == "refresh") {
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