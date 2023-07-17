//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.ui](../index.md)/[OpenVpnReportBreakageFrom](index.md)

# OpenVpnReportBreakageFrom

data class [OpenVpnReportBreakageFrom](index.md)(val launchFrom: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val breakageCategories: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[AppBreakageCategory](../-app-breakage-category/index.md)&gt;) : [GlobalActivityStarter.ActivityParams](../../../../navigation-api/navigation-api/com.duckduckgo.navigation.api/-global-activity-starter/-activity-params/index.md)

Model that represents the VPN Report Breakage Screen

#### Parameters

androidJvm

| | |
|---|---|
| launchFrom | string that identifies the origin that launches the vpn breakage report screen |
| breakageCategories | list of breakage categories you'd like to be displayed in the breakage form to be filled by the user |

## Constructors

| | |
|---|---|
| [OpenVpnReportBreakageFrom](-open-vpn-report-breakage-from.md) | [androidJvm]<br>constructor(launchFrom: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), breakageCategories: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[AppBreakageCategory](../-app-breakage-category/index.md)&gt;) |

## Properties

| Name | Summary |
|---|---|
| [breakageCategories](breakage-categories.md) | [androidJvm]<br>val [breakageCategories](breakage-categories.md): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[AppBreakageCategory](../-app-breakage-category/index.md)&gt; |
| [launchFrom](launch-from.md) | [androidJvm]<br>val [launchFrom](launch-from.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
