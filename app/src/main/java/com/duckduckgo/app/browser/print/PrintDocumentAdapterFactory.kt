/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

class PrintDocumentAdapterFactory {
    companion object {
        fun createPrintDocumentAdapter(
            printDocumentAdapter: PrintDocumentAdapter,
            onStartCallback: () -> Unit,
            onFinishCallback: () -> Unit,
        ): PrintDocumentAdapter {
            return object : PrintDocumentAdapter() {
                override fun onStart() {
                    printDocumentAdapter.onStart()
                    onStartCallback()
                }

                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?,
                ) {
                    printDocumentAdapter.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?,
                ) {
                    runCatching {
                        printDocumentAdapter.onWrite(pages, destination, cancellationSignal, callback)
                    }.onFailure { exception ->
                        logcat(ERROR) { "Failed to write document: ${exception.asLog()}" }
                        callback?.onWriteCancelled()
                    }
                }

                override fun onFinish() {
                    printDocumentAdapter.onFinish()
                    onFinishCallback()
                }
            }
        }
    }
}
