//
// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
//

import Foundation
import Shared
import UIKit

/// iOS side of the shared `PlatformServices`.
final class PlatformServicesImpl: NSObject, PlatformServices {
    func signingExpiryEpochMs() -> Int64 {
        guard let date = ProvisioningProfile.expirationDate() else { return 0 }
        return Int64(date.timeIntervalSince1970 * 1000)
    }

    func openSideStore() -> Bool {
        // SideStore first, AltStore as a fallback; both declared in LSApplicationQueriesSchemes.
        for scheme in ["sidestore://", "altstore://"] {
            guard let url = URL(string: scheme) else { continue }
            if UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
                return true
            }
        }
        return false
    }
}

/// Reads the provisioning profile SideStore/AltStore embedded when signing the app.
///
/// `embedded.mobileprovision` is a CMS-signed plist; the XML plist is stored in clear inside it,
/// so it can be read without verifying the signature (we only need the expiry date).
enum ProvisioningProfile {
    static func expirationDate() -> Date? {
        guard
            let url = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
            let data = try? Data(contentsOf: url),
            let start = data.range(of: Data("<?xml".utf8)),
            let end = data.range(of: Data("</plist>".utf8), in: start.lowerBound..<data.endIndex)
        else { return nil }
        let plistData = data.subdata(in: start.lowerBound..<end.upperBound)
        guard
            let plist = try? PropertyListSerialization.propertyList(from: plistData, format: nil) as? [String: Any]
        else { return nil }
        return plist["ExpirationDate"] as? Date
    }
}
