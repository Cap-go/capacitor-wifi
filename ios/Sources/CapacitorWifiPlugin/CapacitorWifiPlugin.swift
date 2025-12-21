import Foundation
import Capacitor
import NetworkExtension
import SystemConfiguration.CaptiveNetwork
import CoreLocation

@objc(CapacitorWifiPlugin)
public class CapacitorWifiPlugin: CAPPlugin, CAPBridgedPlugin {
    private let pluginVersion: String = "8.1.1"
    public let identifier = "CapacitorWifiPlugin"
    public let jsName = "CapacitorWifi"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "addNetwork", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "connect", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "disconnect", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getAvailableNetworks", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getIpAddress", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getRssi", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getSsid", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isEnabled", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startScan", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPluginVersion", returnType: CAPPluginReturnPromise)
    ]

    private var hotspotManager: NEHotspotConfigurationManager?

    override public func load() {
        hotspotManager = NEHotspotConfigurationManager.shared
    }

    @objc func addNetwork(_ call: CAPPluginCall) {
        guard let ssid = call.getString("ssid") else {
            call.reject("SSID is required")
            return
        }

        let password = call.getString("password")

        let configuration: NEHotspotConfiguration
        if let password = password, !password.isEmpty {
            configuration = NEHotspotConfiguration(ssid: ssid, passphrase: password, isWEP: false)
        } else {
            configuration = NEHotspotConfiguration(ssid: ssid)
        }

        configuration.joinOnce = false

        hotspotManager?.apply(configuration) { error in
            if let error = error {
                call.reject("Failed to add network: \(error.localizedDescription)", nil, error)
            } else {
                call.resolve()
            }
        }
    }

    @objc func connect(_ call: CAPPluginCall) {
        guard let ssid = call.getString("ssid") else {
            call.reject("SSID is required")
            return
        }

        let password = call.getString("password")

        let configuration: NEHotspotConfiguration
        if let password = password, !password.isEmpty {
            configuration = NEHotspotConfiguration(ssid: ssid, passphrase: password, isWEP: false)
        } else {
            configuration = NEHotspotConfiguration(ssid: ssid)
        }

        configuration.joinOnce = false

        hotspotManager?.apply(configuration) { error in
            if let error = error {
                call.reject("Failed to connect: \(error.localizedDescription)", nil, error)
            } else {
                call.resolve()
            }
        }
    }

    @objc func disconnect(_ call: CAPPluginCall) {
        let ssid = call.getString("ssid")

        if let ssid = ssid {
            hotspotManager?.removeConfiguration(forSSID: ssid)
        } else {
            // Disconnect from current network by getting current SSID
            if let currentSSID = getCurrentSSID() {
                hotspotManager?.removeConfiguration(forSSID: currentSSID)
            }
        }

        call.resolve()
    }

    @objc func getAvailableNetworks(_ call: CAPPluginCall) {
        call.reject("Not supported on iOS")
    }

    @objc func getIpAddress(_ call: CAPPluginCall) {
        var address: String?
        var ifaddr: UnsafeMutablePointer<ifaddrs>?

        guard getifaddrs(&ifaddr) == 0, let firstAddr = ifaddr else {
            call.reject("Failed to get IP address")
            return
        }

        defer { freeifaddrs(ifaddr) }

        for ptr in sequence(first: firstAddr, next: { $0.pointee.ifa_next }) {
            let interface = ptr.pointee
            let addrFamily = interface.ifa_addr.pointee.sa_family

            if addrFamily == UInt8(AF_INET) || addrFamily == UInt8(AF_INET6) {
                let name = String(cString: interface.ifa_name)
                if name == "en0" {
                    var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                    getnameinfo(interface.ifa_addr, socklen_t(interface.ifa_addr.pointee.sa_len),
                                &hostname, socklen_t(hostname.count),
                                nil, socklen_t(0), NI_NUMERICHOST)
                    address = String(cString: hostname)
                    break
                }
            }
        }

        if let address = address {
            call.resolve(["ipAddress": address])
        } else {
            call.reject("No IP address found")
        }
    }

    @objc func getRssi(_ call: CAPPluginCall) {
        call.reject("Not supported on iOS")
    }

    @objc func getSsid(_ call: CAPPluginCall) {
        if let ssid = getCurrentSSID() {
            call.resolve(["ssid": ssid])
        } else {
            call.reject("Failed to get SSID")
        }
    }

    @objc func isEnabled(_ call: CAPPluginCall) {
        call.reject("Not supported on iOS")
    }

    @objc func startScan(_ call: CAPPluginCall) {
        call.reject("Not supported on iOS")
    }

    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        let status = getLocationPermissionStatus()
        call.resolve(["location": status])
    }

    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        // On iOS, location permission is automatically requested when accessing WiFi info
        // For iOS 13+, location permission is required to access SSID
        let status = getLocationPermissionStatus()
        call.resolve(["location": status])
    }

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve(["version": self.pluginVersion])
    }

    // MARK: - Helper Methods

    private func getCurrentSSID() -> String? {
        var currentSSID: String?
        NEHotspotNetwork.fetchCurrent { network in
            currentSSID = network?.ssid
        }
        return currentSSID
    }

    private func getLocationPermissionStatus() -> String {
        switch CLLocationManager.authorizationStatus() {
        case .authorizedAlways, .authorizedWhenInUse:
            return "granted"
        case .denied, .restricted:
            return "denied"
        case .notDetermined:
            return "prompt"
        @unknown default:
            return "prompt"
        }
    }
}
