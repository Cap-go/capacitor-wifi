import Foundation

#if canImport(AccessorySetupKit) && canImport(WiFiInfrastructure)
import AccessorySetupKit
import WiFiInfrastructure

@available(iOS 26.2, *)
enum WifiNetworkSharingHelper {
    struct ShareRequest {
        let bluetoothIdentifier: String?
        let accessoryIdentifier: String?
        let requestAuthorization: Bool
        let askToShare: Bool
    }

    struct ShareResponse {
        var started = false
        var authorizationState: String?
        var askToShareState: String?
    }

    static func shareNetwork(_ request: ShareRequest) async throws -> ShareResponse {
        let accessory = try await findAccessory(
            bluetoothIdentifier: request.bluetoothIdentifier,
            accessoryIdentifier: request.accessoryIdentifier
        )

        let controller = try await WINetworkSharingController(for: accessory)
        var response = ShareResponse()

        if request.requestAuthorization {
            let state = try await controller.requestAuthorization()
            response.authorizationState = mapAuthorizationState(state)
        }

        if request.askToShare {
            let state = try await controller.askToShare()
            response.askToShareState = mapAskToShareState(state)
            response.started = true
        } else if request.requestAuthorization {
            response.started = true
        }

        return response
    }

    private static func findAccessory(
        bluetoothIdentifier: String?,
        accessoryIdentifier: String?
    ) async throws -> ASAccessory {
        guard bluetoothIdentifier != nil || accessoryIdentifier != nil else {
            throw WifiNetworkSharingError.missingAccessoryIdentifier
        }

        let session = ASAccessorySession()
        let accessories = try await activateAndFetchAccessories(session: session)
        defer { session.invalidate() }

        if let bluetoothIdentifier,
           let accessory = accessories.first(where: { matchesIdentifier($0, identifier: bluetoothIdentifier) }) {
            return accessory
        }

        if let accessoryIdentifier,
           let accessory = accessories.first(where: { matchesIdentifier($0, identifier: accessoryIdentifier) }) {
            return accessory
        }

        throw WifiNetworkSharingError.accessoryNotFound
    }

    private static func matchesIdentifier(_ accessory: ASAccessory, identifier: String) -> Bool {
        if let bluetoothIdentifier = accessory.bluetoothIdentifier?.uuidString,
           bluetoothIdentifier.caseInsensitiveCompare(identifier) == .orderedSame {
            return true
        }

        return accessory.displayName == identifier
    }

    private static func activateAndFetchAccessories(session: ASAccessorySession) async throws -> [ASAccessory] {
        try await withCheckedThrowingContinuation { continuation in
            var didResume = false
            session.activate(on: DispatchQueue.main) { event in
                guard !didResume else { return }

                switch event.eventType {
                case .activated:
                    didResume = true
                    continuation.resume(returning: session.accessories)
                case .accessoryAdded, .accessoryChanged:
                    if !session.accessories.isEmpty {
                        didResume = true
                        continuation.resume(returning: session.accessories)
                    }
                default:
                    break
                }
            }
        }
    }

    private static func mapAuthorizationState(_ state: WINetworkSharingController.AuthorizationState) -> String {
        switch state {
        case .automatic, .askToShare:
            return "authorized"
        case .denied:
            return "denied"
        case .undetermined:
            return "notDetermined"
        @unknown default:
            return "unsupported"
        }
    }

    private static func mapAskToShareState(_ state: WINetworkSharingAskToShareState) -> String {
        switch state {
        case .approved:
            return "shared"
        case .denied:
            return "declined"
        case .undetermined:
            return "unsupported"
        @unknown default:
            return "unsupported"
        }
    }
}

enum WifiNetworkSharingError: LocalizedError {
    case unsupported
    case missingAccessoryIdentifier
    case accessoryNotFound

    var errorDescription: String? {
        switch self {
        case .unsupported:
            return "Wi-Fi Infrastructure sharing requires iOS 26.2 or later with AccessorySetupKit and the Wi-Fi Infrastructure entitlement."
        case .missingAccessoryIdentifier:
            return "bluetoothIdentifier or accessoryIdentifier is required for iOS Wi-Fi sharing."
        case .accessoryNotFound:
            return "No paired AccessorySetupKit accessory matched the provided identifier."
        }
    }
}
#endif
