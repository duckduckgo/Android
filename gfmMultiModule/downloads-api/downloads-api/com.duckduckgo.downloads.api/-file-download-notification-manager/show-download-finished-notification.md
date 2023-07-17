//[downloads-api](../../../index.md)/[com.duckduckgo.downloads.api](../index.md)/[FileDownloadNotificationManager](index.md)/[showDownloadFinishedNotification](show-download-finished-notification.md)

# showDownloadFinishedNotification

[androidJvm]\

@[AnyThread](https://developer.android.com/reference/kotlin/androidx/annotation/AnyThread.html)

abstract fun [showDownloadFinishedNotification](show-download-finished-notification.md)(downloadId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html), file: [File](https://developer.android.com/reference/kotlin/java/io/File.html), mimeType: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?)

Call this method to show the &quot;download complete&quot; notification. Takes as parameters the [downloadId](show-download-finished-notification.md), the downloaded [file](show-download-finished-notification.md) and optionally the file [mimeType](show-download-finished-notification.md) Safe to call from any thread.
