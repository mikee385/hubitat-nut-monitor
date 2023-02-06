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
 
String getVersionNum() { return "4.3.0" }
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
            input "alertConnected", "bool", title: "Alert when UPS is connected?", required: true, defaultValue: true
            input "alertDisconnected", "bool", title: "Alert when UPS is disconnected?", required: true, defaultValue: true
            input "alertMains", "bool", title: "Alert when UPS power is restored?", required: true, defaultValue: true
            input "alertBattery", "bool", title: "Alert when UPS power is on battery?", required: true, defaultValue: true
            input "alertShutdown", "bool", title: "Alert when UPS is shut down?", required: true, defaultValue: true
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
    state.shuttingDown = false
    
    // Child Device
    def child = childDevice()
    child.updateProperties(nutServerHost, nutServerPort, upsName)
    child.refresh()
    
    subscribe(location, "systemStart", systemStart)

    // URLs
    if(!state.accessToken) {
        createAccessToken()
    }
    state.statusUrl = "${getFullLocalApiServerUrl()}/status/\$1?access_token=$state.accessToken"
}

def childDevice() {
    def childID = "nutMonitor:" + app.getId()
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("mikee385", "NUT Monitor", childID, 1234, [label: upsName, isComponent: true])
    }
    return child
}

def systemStart(evt) {
    initialize()
}

def connected() {
    log.info "${upsName} has connected!"
    if (alertConnected) {
        personToNotify.deviceNotification("${upsName} has connected!")
    }
    
    childDevice().refresh() 
}

def disconnected() {
    log.info "${upsName} has disconnected!"
    if (alertDisconnected) {
        personToNotify.deviceNotification("${upsName} has disconnected!")
    }
    
    childDevice().sendEvent(name: "powerSource", value: "unknown")
}

def mains() {
    log.info "${upsName} power is on mains!"
    if (alertMains) {
        personToNotify.deviceNotification("${upsName} power is on mains!")
    }
    
    childDevice().sendEvent(name: "powerSource", value: "mains")
}

def battery() {
    log.info "${upsName} is on battery!"
    if (alertBattery) {
        personToNotify.deviceNotification("${upsName} is on battery!")
    }
    
    childDevice().sendEvent(name: "powerSource", value: "battery")
}

def shutdown() {
    log.warn "${upsName} is shutting down..."
    if (alertShutdown) {
        personToNotify.deviceNotification("${upsName} is shutting down...")
    }
        
    if (!state.shuttingDown && shutdownWithUps) {
        state.shuttingDown = true
        runIn(15, shutdownHub)
    }
    
    childDevice().sendEvent(name: "powerSource", value: "unknown")
}

def shutdownHub() {
    httpPost("http://127.0.0.1:8080/hub/shutdown", "") { resp -> }
}

def urlHandler_status() {
    logDebug("urlHandler_status: received ${params.status}")
    
    if (params.status == "connected") {
        connected()
    } else if (params.status == "disconnected") {
        disconnected()
    } else if (params.status == "mains") {
        mains()
    } else if (params.status == "battery") {
        battery()
    } else if (params.status == "shutdown") {
        shutdown()
    } else {
        log.warn "Unknown status: ${params.status}"
    }
}