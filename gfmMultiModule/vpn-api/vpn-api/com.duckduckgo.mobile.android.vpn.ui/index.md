//[vpn-api](../../index.md)/[com.duckduckgo.mobile.android.vpn.ui](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [AppBreakageCategory](-app-breakage-category/index.md) | [androidJvm]<br>data class [AppBreakageCategory](-app-breakage-category/index.md)(val key: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val description: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) : [Serializable](https://developer.android.com/reference/kotlin/java/io/Serializable.html) |
| [OpenVpnBreakageCategoryWithBrokenApp](-open-vpn-breakage-category-with-broken-app/index.md) | [androidJvm]<br>data class [OpenVpnBreakageCategoryWithBrokenApp](-open-vpn-breakage-category-with-broken-app/index.md)(val launchFrom: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val appName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val appPackageId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val breakageCategories: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[AppBreakageCategory](-app-breakage-category/index.md)&gt;) : [GlobalActivityStarter.ActivityParams](../../../navigation-api/navigation-api/com.duckduckgo.navigation.api/-global-activity-starter/-activity-params/index.md)<br>Model that represents the VPN Report Breakage Category Screen |
| [OpenVpnReportBreakageFrom](-open-vpn-report-breakage-from/index.md) | [androidJvm]<br>data class [OpenVpnReportBreakageFrom](-open-vpn-report-breakage-from/index.md)(val launchFrom: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val breakageCategories: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[AppBreakageCategory](-app-breakage-category/index.md)&gt;) : [GlobalActivityStarter.ActivityParams](../../../navigation-api/navigation-api/com.duckduckgo.navigation.api/-global-activity-starter/-activity-params/index.md)<br>Model that represents the VPN Report Breakage Screen |
