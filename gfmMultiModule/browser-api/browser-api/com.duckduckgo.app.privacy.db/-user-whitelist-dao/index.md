//[browser-api](../../../index.md)/[com.duckduckgo.app.privacy.db](../index.md)/[UserWhitelistDao](index.md)

# UserWhitelistDao

[androidJvm]\
abstract class [UserWhitelistDao](index.md)

## Constructors

| | |
|---|---|
| [UserWhitelistDao](-user-whitelist-dao.md) | [androidJvm]<br>constructor() |

## Functions

| Name | Summary |
|---|---|
| [all](all.md) | [androidJvm]<br>abstract fun [all](all.md)(): [LiveData](https://developer.android.com/reference/kotlin/androidx/lifecycle/LiveData.html)&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[UserWhitelistedDomain](../../com.duckduckgo.app.privacy.model/-user-whitelisted-domain/index.md)&gt;&gt; |
| [allDomainsFlow](all-domains-flow.md) | [androidJvm]<br>abstract fun [allDomainsFlow](all-domains-flow.md)(): Flow&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;&gt; |
| [contains](contains.md) | [androidJvm]<br>abstract fun [contains](contains.md)(domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [delete](delete.md) | [androidJvm]<br>abstract fun [delete](delete.md)(domain: [UserWhitelistedDomain](../../com.duckduckgo.app.privacy.model/-user-whitelisted-domain/index.md))<br>fun [delete](delete.md)(domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
| [insert](insert.md) | [androidJvm]<br>abstract fun [insert](insert.md)(domain: [UserWhitelistedDomain](../../com.duckduckgo.app.privacy.model/-user-whitelisted-domain/index.md))<br>fun [insert](insert.md)(domain: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
