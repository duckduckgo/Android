//[ad-click-api](../../../index.md)/[com.duckduckgo.adclick.api](../index.md)/[AdClickManager](index.md)

# AdClickManager

[jvm]\
interface [AdClickManager](index.md)

Public interface for the Ad Click feature which helps to measure ad conversions only when they are required

## Functions

| Name | Summary |
|---|---|
| [clearAll](clear-all.md) | [jvm]<br>abstract fun [clearAll](clear-all.md)()<br>Removes any data related to ad management that is kept in memory. |
| [clearAllExpiredAsync](clear-all-expired-async.md) | [jvm]<br>abstract fun [clearAllExpiredAsync](clear-all-expired-async.md)()<br>Removes any data related to ad management that is kept in memory. This is used asynchronously. |
| [clearTabId](clear-tab-id.md) | [jvm]<br>abstract fun [clearTabId](clear-tab-id.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>Removes any data kept in memory for the specified tab. It takes as parameters: mandatory [tabId](clear-tab-id.md) - The id of the active tab. |
| [detectAdClick](detect-ad-click.md) | [jvm]<br>abstract fun [detectAdClick](detect-ad-click.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, isMainFrame: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html))<br>Detects and registers the eTLD+1 if an ad link was clicked. It takes as parameters: optional [url](detect-ad-click.md) - The requested url, null if no url was requested. mandatory [isMainFrame](detect-ad-click.md) - True if the request is for mainframe, false otherwise. |
| [detectAdDomain](detect-ad-domain.md) | [jvm]<br>abstract fun [detectAdDomain](detect-ad-domain.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>Detects and saves in memory the ad eTLD+1 domain from the url. It takes as parameters: mandatory [url](detect-ad-domain.md) - The requested url. |
| [isExemption](is-exemption.md) | [jvm]<br>abstract fun [isExemption](is-exemption.md)(documentUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Detects if there is an existing exemption based on the document url and the url requested. It takes as parameters: mandatory [documentUrl](is-exemption.md) - The initially requested url, potentially leading to the advertiser page. mandatory [url](is-exemption.md) - The requested url, potentially a tracker used for ad attribution. |
| [setActiveTabId](set-active-tab-id.md) | [jvm]<br>abstract fun [setActiveTabId](set-active-tab-id.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, sourceTabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, sourceTabUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null)<br>Sets the active tab. It takes as parameters: mandatory [tabId](set-active-tab-id.md) - The id of the current visible tab. optional [url](set-active-tab-id.md) - The url loaded in the current tab, null if no url was loaded (empty tab). optional [sourceTabId](set-active-tab-id.md) - The id of the tab from which the current tab was opened, null if a new tab option was used. optional [sourceTabUrl](set-active-tab-id.md) - The url loaded in the tab from which the current tab was opened, null if a new tab option was used. |
