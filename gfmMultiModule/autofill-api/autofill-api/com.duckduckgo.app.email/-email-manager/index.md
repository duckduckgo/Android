//[autofill-api](../../../index.md)/[com.duckduckgo.app.email](../index.md)/[EmailManager](index.md)

# EmailManager

[androidJvm]\
interface [EmailManager](index.md)

Provides ability to store and retrieve data related to the duck address feature such as personal username, if signed in, next alias etc...

## Functions

| Name | Summary |
|---|---|
| [getAlias](get-alias.md) | [androidJvm]<br>abstract fun [getAlias](get-alias.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?<br>Get the next available private duck address alias |
| [getCohort](get-cohort.md) | [androidJvm]<br>abstract fun [getCohort](get-cohort.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>Get the cohort |
| [getEmailAddress](get-email-address.md) | [androidJvm]<br>abstract fun [getEmailAddress](get-email-address.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?<br>Get the user's full, personal duck address |
| [getLastUsedDate](get-last-used-date.md) | [androidJvm]<br>abstract fun [getLastUsedDate](get-last-used-date.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>Return last used date |
| [getToken](get-token.md) | [androidJvm]<br>abstract fun [getToken](get-token.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?<br>Get the stored auth token |
| [getUserData](get-user-data.md) | [androidJvm]<br>abstract fun [getUserData](get-user-data.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>Get the user's duck address data in a format that can be passed to JS |
| [isEmailFeatureSupported](is-email-feature-supported.md) | [androidJvm]<br>abstract fun [isEmailFeatureSupported](is-email-feature-supported.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Determines if duck address can be used on this device |
| [isSignedIn](is-signed-in.md) | [androidJvm]<br>abstract fun [isSignedIn](is-signed-in.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Indicates if the user is signed in or not |
| [setNewLastUsedDate](set-new-last-used-date.md) | [androidJvm]<br>abstract fun [setNewLastUsedDate](set-new-last-used-date.md)()<br>Updates the last used date |
| [signedInFlow](signed-in-flow.md) | [androidJvm]<br>abstract fun [signedInFlow](signed-in-flow.md)(): StateFlow&lt;[Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)&gt;<br>Indicates if the user is signed in or not. This is a flow so that it can be observed. |
| [signOut](sign-out.md) | [androidJvm]<br>abstract fun [signOut](sign-out.md)()<br>Signs out of using duck addresses on this device |
| [storeCredentials](store-credentials.md) | [androidJvm]<br>abstract fun [storeCredentials](store-credentials.md)(token: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), username: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), cohort: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>Store the credentials for the user's duck address |
