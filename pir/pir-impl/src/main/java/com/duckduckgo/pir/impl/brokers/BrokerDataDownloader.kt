/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.brokers

import android.content.Context
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.service.DbpService.PirJsonBroker
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import okhttp3.ResponseBody
import okio.FileSystem.Companion.SYSTEM
import okio.Path.Companion.toPath

interface BrokerDataDownloader {
    /**
     * This method initiates downloaded of the actual broker json files for [brokersToUpdate]
     * and updates the stored broker data locally.
     *
     * @param brokersToUpdate - A list of broker json filenames whose etags have been updated
     */
    suspend fun downloadBrokerData(brokersToUpdate: List<String>)
}

@ContributesBinding(AppScope::class)
class RealBrokerDataDownloader @Inject constructor(
    private val dbpService: DbpService,
    private val dispatcherProvider: DispatcherProvider,
    private val pirRepository: PirRepository,
    private val context: Context,
) : BrokerDataDownloader {
    override suspend fun downloadBrokerData(brokersToUpdate: List<String>) {
        withContext(dispatcherProvider.io()) {
            if (brokersToUpdate.isNotEmpty()) {
                val extractFolder = File(context.filesDir, "unzipped-broker-json")
                extractJsonFilesFromResponse(dbpService.getBrokerJsonFiles(), extractFolder)
                processBrokerJsonFiles(extractFolder, brokersToUpdate)
                SYSTEM.deleteRecursively(extractFolder.path.toPath())
            }
        }
    }

    private fun extractJsonFilesFromResponse(
        responseBody: ResponseBody,
        outputDir: File,
    ) {
        logcat { "PIR-update: Extracting data from $responseBody" }
        ZipInputStream(responseBody.byteStream()).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                val outputFile = File(outputDir, entry.name)

                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    FileOutputStream(outputFile).use { outputStream ->
                        zipInputStream.copyTo(outputStream)
                    }
                }

                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
        }
        logcat { "PIR-update: Extraction complete" }
    }

    private suspend fun processBrokerJsonFiles(
        directory: File,
        brokersToUpdate: List<String>,
    ) {
        logcat { "PIR-update: Attempting to process all relevant json for $brokersToUpdate" }
        if (!directory.exists() || !directory.isDirectory) {
            logcat(ERROR) { "PIR-update: $directory is not the json directory" }
            return
        }

        val adapter = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .add(StepsAsStringAdapter())
            .build()
            .adapter(PirJsonBroker::class.java)

        directory.listFiles()?.get(0)?.listFiles { file ->
            file.extension == "json" && brokersToUpdate.contains(file.name)
        }?.forEach { jsonFile ->
            logcat { "PIR-update: Processing data from ${jsonFile.name}" }
            val content = jsonFile.readText() // Read JSON file as string
            val broker = adapter.fromJson(content)
            if (broker != null) {
                pirRepository.updateBrokerData(jsonFile.name, broker)
            }
        }

        logcat { "PIR-update: Stored all new broker data" }
    }
}
