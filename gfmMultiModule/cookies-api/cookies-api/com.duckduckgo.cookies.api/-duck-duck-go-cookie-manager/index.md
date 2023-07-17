//[cookies-api](../../../index.md)/[com.duckduckgo.cookies.api](../index.md)/[DuckDuckGoCookieManager](index.md)

# DuckDuckGoCookieManager

[androidJvm]\
interface [DuckDuckGoCookieManager](index.md)

Public interface for DuckDuckGoCookieManager

## Functions

| Name | Summary |
|---|---|
| [flush](flush.md) | [androidJvm]<br>abstract fun [flush](flush.md)()<br>This method calls the flush method from the Cookie Manager |
| [removeExternalCookies](remove-external-cookies.md) | [androidJvm]<br>abstract suspend fun [removeExternalCookies](remove-external-cookies.md)()<br>This method deletes all the cookies that are not related with DDG settings or fireproofed websites Note: The Fire Button does not delete the user's DuckDuckGo search settings, which are saved as cookies. Removing these cookies would reset them and have undesired consequences, i.e. changing the theme, default language, etc. These cookies are not stored in a personally identifiable way. For example, the large size setting is stored as 's=l.' More info in https://duckduckgo.com/privacy |
