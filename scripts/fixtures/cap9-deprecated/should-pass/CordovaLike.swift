import Capacitor

public class CordovaLike: CAPPlugin, CAPBridgedPlugin {
    @objc func demo(_ call: CAPPluginCall) {
        let note = "CAPNotifications would be deprecated"
        _ = note
    }
}
