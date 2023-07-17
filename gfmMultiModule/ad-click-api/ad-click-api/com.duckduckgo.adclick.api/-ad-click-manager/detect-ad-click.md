//[ad-click-api](../../../index.md)/[com.duckduckgo.adclick.api](../index.md)/[AdClickManager](index.md)/[detectAdClick](detect-ad-click.md)

# detectAdClick

[jvm]\
abstract fun [detectAdClick](detect-ad-click.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?, isMainFrame: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html))

Detects and registers the eTLD+1 if an ad link was clicked. It takes as parameters: optional [url](detect-ad-click.md) - The requested url, null if no url was requested. mandatory [isMainFrame](detect-ad-click.md) - True if the request is for mainframe, false otherwise.
