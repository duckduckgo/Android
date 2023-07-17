//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.ui](../index.md)/[OpenVpnBreakageCategoryWithBrokenApp](index.md)

# OpenVpnBreakageCategoryWithBrokenApp

data class [OpenVpnBreakageCategoryWithBrokenApp](index.md)(val launchFrom: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val appName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val appPackageId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val breakageCategories: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[AppBreakageCategory](../-app-breakage-category/index.md)&gt;) : [GlobalActivityStarter.ActivityParams](../../../../navigation-api/navigation-api/com.duckduckgo.navigation.api/-global-activity-starter/-activity-params/index.md)

Model that represents the VPN Report Breakage Category Screen

#### Parameters

androidJvm

| | |
|---|---|
| launchFrom | string that identifies the origin that launches the vpn breakage report screen |
| breakageCategories | list of breakage categories you'd like to be displayed in the breakage form to be filled by the user |
| appName | is the name of the app the user reported as broken |
| appPackageId | is the package ID of the app the user reported as broken |

## Constructors

| | |
|---|---|
| [OpenVpnBreakageCategoryWithBrokenApp](-open-vpn-breakage-category-with-broken-app.md) | [androidJvm]<br>constructor(launchFrom: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), appName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), appPackageId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), breakageCategories: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[AppBreakageCategory](../-app-breakage-category/index.md)&gt;) |

## Properties

| Name | Summary |
|---|---|
| [appName](app-name.md) | [androidJvm]<br>val [appName](app-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [appPackageId](app-package-id.md) | [androidJvm]<br>val [appPackageId](app-package-id.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [breakageCategories](breakage-categories.md) | [androidJvm]<br>val [breakageCategories](breakage-categories.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[AppBreakageCategory](../-app-breakage-category/index.md)&gt; |
| [launchFrom](launch-from.md) | [androidJvm]<br>val [launchFrom](launch-from.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
