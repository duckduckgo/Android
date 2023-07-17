//[downloads-api](../../../index.md)/[com.duckduckgo.downloads.api](../index.md)/[DownloadCommand](index.md)

# DownloadCommand

sealed class [DownloadCommand](index.md)

Specific download commands used to display messages during various download stages.

#### Inheritors

| |
|---|
| [ShowDownloadStartedMessage](-show-download-started-message/index.md) |
| [ShowDownloadSuccessMessage](-show-download-success-message/index.md) |
| [ShowDownloadFailedMessage](-show-download-failed-message/index.md) |

## Types

| Name | Summary |
|---|---|
| [ShowDownloadFailedMessage](-show-download-failed-message/index.md) | [androidJvm]<br>class [ShowDownloadFailedMessage](-show-download-failed-message/index.md)(@[StringRes](https://developer.android.com/reference/kotlin/androidx/annotation/StringRes.html)val messageId: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)) : [DownloadCommand](index.md) |
| [ShowDownloadStartedMessage](-show-download-started-message/index.md) | [androidJvm]<br>class [ShowDownloadStartedMessage](-show-download-started-message/index.md)(@[StringRes](https://developer.android.com/reference/kotlin/androidx/annotation/StringRes.html)val messageId: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), val fileName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)) : [DownloadCommand](index.md) |
| [ShowDownloadSuccessMessage](-show-download-success-message/index.md) | [androidJvm]<br>class [ShowDownloadSuccessMessage](-show-download-success-message/index.md)(@[StringRes](https://developer.android.com/reference/kotlin/androidx/annotation/StringRes.html)val messageId: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html), val fileName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val filePath: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), val mimeType: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null) : [DownloadCommand](index.md) |

## Properties

| Name | Summary |
|---|---|
| [messageId](message-id.md) | [androidJvm]<br>val [messageId](message-id.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
