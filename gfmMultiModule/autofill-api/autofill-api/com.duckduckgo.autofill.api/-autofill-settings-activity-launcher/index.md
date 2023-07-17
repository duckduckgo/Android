//[autofill-api](../../../index.md)/[com.duckduckgo.autofill.api](../index.md)/[AutofillSettingsActivityLauncher](index.md)

# AutofillSettingsActivityLauncher

[androidJvm]\
interface [AutofillSettingsActivityLauncher](index.md)

Used to access an Intent which will launch the autofill settings activity The activity is implemented in the impl module and is otherwise inaccessible from outside this module.

## Functions

| Name | Summary |
|---|---|
| [intent](intent.md) | [androidJvm]<br>abstract fun [intent](intent.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html)): [Intent](https://developer.android.com/reference/kotlin/android/content/Intent.html)<br>Launch the Autofill management activity, which will show the full list of available credentials |
| [intentAlsoShowSuggestionsForSite](intent-also-show-suggestions-for-site.md) | [androidJvm]<br>abstract fun [intentAlsoShowSuggestionsForSite](intent-also-show-suggestions-for-site.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), currentUrl: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?): [Intent](https://developer.android.com/reference/kotlin/android/content/Intent.html)<br>Launch the Autofill management activity, which will show suggestions for the current url and the full list of available credentials |
| [intentDirectlyViewCredentials](intent-directly-view-credentials.md) | [androidJvm]<br>abstract fun [intentDirectlyViewCredentials](intent-directly-view-credentials.md)(context: [Context](https://developer.android.com/reference/kotlin/android/content/Context.html), loginCredentials: [LoginCredentials](../../com.duckduckgo.autofill.api.domain.app/-login-credentials/index.md)): [Intent](https://developer.android.com/reference/kotlin/android/content/Intent.html)<br>Launch the Autofill management activity, directly showing particular credentials |
