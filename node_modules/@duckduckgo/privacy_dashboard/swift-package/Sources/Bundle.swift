
import Foundation

#if os(iOS)
import PrivacyDashboard_resources_for_ios
#elseif os(macOS)
import PrivacyDashboard_resources_for_macos
#endif

public extension Bundle {
    static var privacyDashboardURL: URL? {
        platformResourcesBundle?.url(forResource: "popup", withExtension: "html", subdirectory: "assets/html")
    }
    
    private static var platformResourcesBundle: Bundle? {
#if os(iOS)
        return Bundle.privacyDashboardIOSResourcesBundle
#elseif os(macOS)
        return Bundle.privacyDashboardMacOSResourcesBundle
#else
        return nil
#endif
    }
}
