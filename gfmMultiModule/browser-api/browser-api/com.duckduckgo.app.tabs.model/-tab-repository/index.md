//[browser-api](../../../index.md)/[com.duckduckgo.app.tabs.model](../index.md)/[TabRepository](index.md)

# TabRepository

[androidJvm]\
interface [TabRepository](index.md)

## Properties

| Name | Summary |
|---|---|
| [childClosedTabs](child-closed-tabs.md) | [androidJvm]<br>abstract val [childClosedTabs](child-closed-tabs.md): SharedFlow&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt; |
| [flowDeletableTabs](flow-deletable-tabs.md) | [androidJvm]<br>abstract val [flowDeletableTabs](flow-deletable-tabs.md): Flow&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[TabEntity](../-tab-entity/index.md)&gt;&gt; |
| [flowTabs](flow-tabs.md) | [androidJvm]<br>abstract val [flowTabs](flow-tabs.md): Flow&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[TabEntity](../-tab-entity/index.md)&gt;&gt; |
| [liveSelectedTab](live-selected-tab.md) | [androidJvm]<br>abstract val [liveSelectedTab](live-selected-tab.md): [LiveData](https://developer.android.com/reference/kotlin/androidx/lifecycle/LiveData.html)&lt;[TabEntity](../-tab-entity/index.md)&gt; |
| [liveTabs](live-tabs.md) | [androidJvm]<br>abstract val [liveTabs](live-tabs.md): [LiveData](https://developer.android.com/reference/kotlin/androidx/lifecycle/LiveData.html)&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[TabEntity](../-tab-entity/index.md)&gt;&gt; |

## Functions

| Name | Summary |
|---|---|
| [add](add.md) | [androidJvm]<br>abstract suspend fun [add](add.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, skipHome: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [addDefaultTab](add-default-tab.md) | [androidJvm]<br>abstract suspend fun [addDefaultTab](add-default-tab.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [addFromSourceTab](add-from-source-tab.md) | [androidJvm]<br>abstract suspend fun [addFromSourceTab](add-from-source-tab.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, skipHome: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false, sourceTabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [addNewTabAfterExistingTab](add-new-tab-after-existing-tab.md) | [androidJvm]<br>abstract suspend fun [addNewTabAfterExistingTab](add-new-tab-after-existing-tab.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
| [delete](delete.md) | [androidJvm]<br>abstract suspend fun [delete](delete.md)(tab: [TabEntity](../-tab-entity/index.md)) |
| [deleteAll](delete-all.md) | [androidJvm]<br>abstract suspend fun [deleteAll](delete-all.md)() |
| [deleteTabAndSelectSource](delete-tab-and-select-source.md) | [androidJvm]<br>abstract suspend fun [deleteTabAndSelectSource](delete-tab-and-select-source.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
| [markDeletable](mark-deletable.md) | [androidJvm]<br>abstract suspend fun [markDeletable](mark-deletable.md)(tab: [TabEntity](../-tab-entity/index.md)) |
| [purgeDeletableTabs](purge-deletable-tabs.md) | [androidJvm]<br>abstract suspend fun [purgeDeletableTabs](purge-deletable-tabs.md)()<br>Deletes from the DB all tabs that are marked as &quot;deletable&quot; |
| [retrieveSiteData](retrieve-site-data.md) | [androidJvm]<br>abstract fun [retrieveSiteData](retrieve-site-data.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [MutableLiveData](https://developer.android.com/reference/kotlin/androidx/lifecycle/MutableLiveData.html)&lt;[Site](../../com.duckduckgo.app.global.model/-site/index.md)&gt; |
| [select](select.md) | [androidJvm]<br>abstract suspend fun [select](select.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
| [selectByUrlOrNewTab](select-by-url-or-new-tab.md) | [androidJvm]<br>abstract suspend fun [selectByUrlOrNewTab](select-by-url-or-new-tab.md)(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) |
| [undoDeletable](undo-deletable.md) | [androidJvm]<br>abstract suspend fun [undoDeletable](undo-deletable.md)(tab: [TabEntity](../-tab-entity/index.md)) |
| [update](update.md) | [androidJvm]<br>abstract suspend fun [update](update.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), site: [Site](../../com.duckduckgo.app.global.model/-site/index.md)?) |
| [updateTabFavicon](update-tab-favicon.md) | [androidJvm]<br>abstract fun [updateTabFavicon](update-tab-favicon.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), fileName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?) |
| [updateTabPreviewImage](update-tab-preview-image.md) | [androidJvm]<br>abstract fun [updateTabPreviewImage](update-tab-preview-image.md)(tabId: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), fileName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?) |
