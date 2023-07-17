//[downloads-api](../../../index.md)/[com.duckduckgo.downloads.api](../index.md)/[FileDownloader](index.md)

# FileDownloader

[androidJvm]\
interface [FileDownloader](index.md)

Interface for the starting point of a download.

## Types

| Name | Summary |
|---|---|
| [PendingFileDownload](-pending-file-download/index.md) | [androidJvm]<br>data class [PendingFileDownload](-pending-file-download/index.md)(val url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val contentDisposition: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, val mimeType: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, val subfolder: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val directory: [File](https://developer.android.com/reference/kotlin/java/io/File.html) = Environment.getExternalStoragePublicDirectory(subfolder), val isUrlCompressed: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false) : [Serializable](https://developer.android.com/reference/kotlin/java/io/Serializable.html)<br>Data class for pending download. |

## Functions

| Name | Summary |
|---|---|
| [enqueueDownload](enqueue-download.md) | [androidJvm]<br>@[AnyThread](https://developer.android.com/reference/kotlin/androidx/annotation/AnyThread.html)<br>abstract fun [enqueueDownload](enqueue-download.md)(pending: [FileDownloader.PendingFileDownload](-pending-file-download/index.md))<br>Starts a download. Takes as parameters a [PendingFileDownload](-pending-file-download/index.md) containing all details about the file to be downloaded. |
