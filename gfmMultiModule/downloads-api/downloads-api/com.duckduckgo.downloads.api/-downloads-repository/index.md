//[downloads-api](../../../index.md)/[com.duckduckgo.downloads.api](../index.md)/[DownloadsRepository](index.md)

# DownloadsRepository

[androidJvm]\
interface [DownloadsRepository](index.md)

## Functions

| Name | Summary |
|---|---|
| [delete](delete.md) | [androidJvm]<br>abstract suspend fun [delete](delete.md)(downloadId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))<br>abstract suspend fun [delete](delete.md)(downloadIdList: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)&gt;) |
| [deleteAll](delete-all.md) | [androidJvm]<br>abstract suspend fun [deleteAll](delete-all.md)() |
| [getDownloadItem](get-download-item.md) | [androidJvm]<br>abstract suspend fun [getDownloadItem](get-download-item.md)(downloadId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)): [DownloadItem](../../com.duckduckgo.downloads.api.model/-download-item/index.md)? |
| [getDownloads](get-downloads.md) | [androidJvm]<br>abstract suspend fun [getDownloads](get-downloads.md)(): [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[DownloadItem](../../com.duckduckgo.downloads.api.model/-download-item/index.md)&gt; |
| [getDownloadsAsFlow](get-downloads-as-flow.md) | [androidJvm]<br>abstract fun [getDownloadsAsFlow](get-downloads-as-flow.md)(): Flow&lt;[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[DownloadItem](../../com.duckduckgo.downloads.api.model/-download-item/index.md)&gt;&gt; |
| [insert](insert.md) | [androidJvm]<br>abstract suspend fun [insert](insert.md)(downloadItem: [DownloadItem](../../com.duckduckgo.downloads.api.model/-download-item/index.md)): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |
| [insertAll](insert-all.md) | [androidJvm]<br>abstract suspend fun [insertAll](insert-all.md)(downloadItems: [List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)&lt;[DownloadItem](../../com.duckduckgo.downloads.api.model/-download-item/index.md)&gt;) |
| [update](update.md) | [androidJvm]<br>abstract suspend fun [update](update.md)(downloadId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html), downloadStatus: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), contentLength: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html))<br>abstract suspend fun [update](update.md)(fileName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), downloadStatus: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), contentLength: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html)) |
