//[downloads-api](../../../index.md)/[com.duckduckgo.downloads.api](../index.md)/[FileDownloadNotificationManager](index.md)/[showDownloadFailedNotification](show-download-failed-notification.md)

# showDownloadFailedNotification

[androidJvm]\

@[AnyThread](https://developer.android.com/reference/kotlin/androidx/annotation/AnyThread.html)

abstract fun [showDownloadFailedNotification](show-download-failed-notification.md)(downloadId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)?)

Call this method to show the &quot;download failed&quot; notification. Takes as parameters the [downloadId](show-download-failed-notification.md) and the download [url](show-download-failed-notification.md). Safe to call from any thread.
