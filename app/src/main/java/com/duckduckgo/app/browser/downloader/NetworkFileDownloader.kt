/*
 * Copyright (c) 2018 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser.downloader

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager.*
import android.webkit.CookieManager
import androidx.core.net.toUri
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.downloader.FileDownloader.PendingFileDownload
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject


class NetworkFileDownloader @Inject constructor(private val context: Context, private val filenameExtractor: FilenameExtractor) {

    fun download(pendingDownload: PendingFileDownload, callback: FileDownloader.FileDownloadListener, fileService: DownloadFileService) {

        if (!downloadManagerAvailable()) {
            callback.downloadFailed(context.getString(R.string.downloadManagerDisabled), DownloadFailReason.DownloadManagerDisabled)
            return
        }

        val downloadCall = fileService.downloadFile(pendingDownload.url)
        downloadCall?.enqueue(object : Callback<ResponseBody?> {
            override fun onResponse(call: Call<ResponseBody?>?, response: Response<ResponseBody?>) {
                if (response.isSuccessful) {
                    Timber.d("We have the body for the file ${response.body()}")

                    val guessedFileName = if (response.body() != null) {
                        if (response.body()!!.contentType() != null) {
                            val mimeType = response.body()!!.contentType()!!.type + "/" + response.body()!!.contentType()!!.subtype
                            val updatedPendingDownload = pendingDownload.copy(mimeType = mimeType)
                            filenameExtractor.extract(updatedPendingDownload)
                        } else {
                            filenameExtractor.extract(pendingDownload)
                        }
                    } else {
                        filenameExtractor.extract(pendingDownload)
                    }

                    val request = DownloadManager.Request(pendingDownload.url.toUri()).apply {
                        allowScanningByMediaScanner()
                        addRequestHeader("User-Agent", pendingDownload.userAgent)
                        addRequestHeader("Cookie", CookieManager.getInstance().getCookie(pendingDownload.url))
                        setMimeType(pendingDownload.mimeType)
                        setDestinationInExternalPublicDir(pendingDownload.subfolder, guessedFileName)
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    }
                    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager?
                    manager?.enqueue(request)
                    callback.downloadStartedNetworkFile()

                } else {
                    Timber.d("Connection failed ${response.errorBody()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody?>?, t: Throwable) {
                Timber.d("Connection failed ${t.localizedMessage}")
            }
        })

    }

    private fun downloadManagerAvailable(): Boolean {
        return when (context.packageManager.getApplicationEnabledSetting(DOWNLOAD_MANAGER_PACKAGE)) {
            COMPONENT_ENABLED_STATE_DISABLED -> false
            COMPONENT_ENABLED_STATE_DISABLED_USER -> false
            COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            else -> true
        }
    }

    private fun saveToDisk(body: ResponseBody, file: File) {
        try {
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(file)
            val data = ByteArray(4096)
            var count: Int
            var progress = 0
            val fileSize = body.contentLength()

            Timber.d("File Size $fileSize")

            while (inputStream.read(data).also { count = it } != -1) {
                outputStream.write(data, 0, count)
                progress += count
                val pairs = Pair(progress, fileSize)
                Timber.d("Progress: " + progress + "/" + fileSize + " >>>> " + progress.toFloat() / fileSize)
            }
            outputStream.flush()
            Timber.d("Download complete")
            inputStream.close()
            outputStream.close()
            return
        } catch (e: IOException) {
            Timber.d("Failed to save the file!")
            return
        }

    }

    companion object {
        private const val DOWNLOAD_MANAGER_PACKAGE = "com.android.providers.downloads"
    }
}
