//[ad-click-api](../../../index.md)/[com.duckduckgo.adclick.api](../index.md)/[AdClickManager](index.md)/[setActiveTabId](set-active-tab-id.md)

# setActiveTabId

[jvm]\
abstract fun [setActiveTabId](set-active-tab-id.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, sourceTabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, sourceTabUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null)

Sets the active tab. It takes as parameters: mandatory [tabId](set-active-tab-id.md) - The id of the current visible tab. optional [url](set-active-tab-id.md) - The url loaded in the current tab, null if no url was loaded (empty tab). optional [sourceTabId](set-active-tab-id.md) - The id of the tab from which the current tab was opened, null if a new tab option was used. optional [sourceTabUrl](set-active-tab-id.md) - The url loaded in the tab from which the current tab was opened, null if a new tab option was used.
