//[downloads-api](../../../../index.md)/[com.duckduckgo.downloads.api](../../index.md)/[FileDownloader](../index.md)/[PendingFileDownload](index.md)

# PendingFileDownload

[androidJvm]\
data class [PendingFileDownload](index.md)(val url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val contentDisposition: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, val mimeType: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, val subfolder: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val directory: [File](https://developer.android.com/reference/kotlin/java/io/File.html) = Environment.getExternalStoragePublicDirectory(subfolder), val isUrlCompressed: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false) : [Serializable](https://developer.android.com/reference/kotlin/java/io/Serializable.html)

Data class for pending download.

## Constructors

| | |
|---|---|
| [PendingFileDownload](-pending-file-download.md) | [androidJvm]<br>constructor(url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), contentDisposition: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, mimeType: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null, subfolder: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), directory: [File](https://developer.android.com/reference/kotlin/java/io/File.html) = Environment.getExternalStoragePublicDirectory(subfolder), isUrlCompressed: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false) |

## Properties

| Name | Summary |
|---|---|
| [contentDisposition](content-disposition.md) | [androidJvm]<br>val [contentDisposition](content-disposition.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null |
| [directory](directory.md) | [androidJvm]<br>val [directory](directory.md): [File](https://developer.android.com/reference/kotlin/java/io/File.html) |
| [isUrlCompressed](is-url-compressed.md) | [androidJvm]<br>val [isUrlCompressed](is-url-compressed.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false |
| [mimeType](mime-type.md) | [androidJvm]<br>val [mimeType](mime-type.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null |
| [subfolder](subfolder.md) | [androidJvm]<br>val [subfolder](subfolder.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [url](url.md) | [androidJvm]<br>val [url](url.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
