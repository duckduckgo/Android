//[cookies-api](../../../index.md)/[com.duckduckgo.cookies.api](../index.md)/[DuckDuckGoCookieManager](index.md)/[removeExternalCookies](remove-external-cookies.md)

# removeExternalCookies

[androidJvm]\
abstract suspend fun [removeExternalCookies](remove-external-cookies.md)()

This method deletes all the cookies that are not related with DDG settings or fireproofed websites Note: The Fire Button does not delete the user's DuckDuckGo search settings, which are saved as cookies. Removing these cookies would reset them and have undesired consequences, i.e. changing the theme, default language, etc. These cookies are not stored in a personally identifiable way. For example, the large size setting is stored as 's=l.' More info in https://duckduckgo.com/privacy
