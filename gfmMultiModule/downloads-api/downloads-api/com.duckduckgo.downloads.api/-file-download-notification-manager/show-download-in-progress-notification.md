//[downloads-api](../../../index.md)/[com.duckduckgo.downloads.api](../index.md)/[FileDownloadNotificationManager](index.md)/[showDownloadInProgressNotification](show-download-in-progress-notification.md)

# showDownloadInProgressNotification

[androidJvm]\

@[AnyThread](https://developer.android.com/reference/kotlin/androidx/annotation/AnyThread.html)

abstract fun [showDownloadInProgressNotification](show-download-in-progress-notification.md)(downloadId: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html), filename: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), progress: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = 0)

Show an &quot;in progress&quot; notification for the file being downloaded. It can be called multiple times with the [progress](show-download-in-progress-notification.md) of the download, and receives the [downloadId](show-download-in-progress-notification.md) and the [filename](show-download-in-progress-notification.md). Although it is safe to call this method from any thread, if called too frequently, it is recommended to call it on a background thread.
