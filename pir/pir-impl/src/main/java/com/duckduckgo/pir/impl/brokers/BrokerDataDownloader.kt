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
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.service.DbpService.PirJsonBroker
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import okio.FileSystem.Companion.SYSTEM
import okio.Path.Companion.toPath
import okio.buffer
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

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
    private val pirPixelSender: PirPixelSender,
) : BrokerDataDownloader {

    private val brokerAdapter by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .add(StepsAsStringAdapter())
            .build()
            .adapter(PirJsonBroker::class.java)
    }

    override suspend fun downloadBrokerData(brokersToUpdate: List<String>) {
        withContext(dispatcherProvider.io()) {
            if (brokersToUpdate.isNotEmpty()) {
                logcat { "PIR-update: Starting to download broker data" }
                val extractFolder = File(context.filesDir, "unzipped-broker-json")
                val zipFile = File.createTempFile("broker-data", ".zip", context.cacheDir)
                try {
                    dbpService.getBrokerJsonFiles().use { body ->
                        body.byteStream().use { input ->
                            zipFile.outputStream().use { input.copyTo(it) }
                        }
                    }
                    extractJsonFilesFromZip(zipFile, extractFolder)
                    processBrokerJsonFiles(extractFolder, brokersToUpdate)
                } finally {
                    zipFile.delete()
                    if (extractFolder.exists()) {
                        SYSTEM.deleteRecursively(extractFolder.path.toPath())
                    }
                }
                logcat { "PIR-update: Done downloading broker data" }
            }
        }
    }

    private fun extractJsonFilesFromZip(
        zipFile: File,
        outputDir: File,
    ) {
        logcat { "PIR-update: Extracting data from $zipFile" }
        outputDir.mkdirs()
        val outputDirCanonical = outputDir.canonicalPath
        ZipInputStream(zipFile.inputStream().buffered()).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".json")) {
                    val outputFile = File(outputDir, entry.name)
                    if (outputFile.canonicalPath.startsWith(outputDirCanonical + File.separator)) {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).buffered().use { outputStream ->
                            zipInputStream.copyTo(outputStream)
                        }
                    } else {
                        logcat(ERROR) { "PIR-update: Skipping ZIP entry with invalid path: ${entry.name}" }
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

        val brokersToUpdateSet = brokersToUpdate.toHashSet()
        directory.listFiles()?.firstOrNull()?.listFiles { file ->
            file.extension == "json" && file.name in brokersToUpdateSet
        }?.forEach { jsonFile ->
            logcat { "PIR-update: Processing data from ${jsonFile.name}" }
            val broker = runCatching { jsonFile.source().buffer().use { brokerAdapter.fromJson(it) } }
                .onFailure { logcat(ERROR) { "PIR-update: Failed to parse ${jsonFile.name}: $it" } }
                .getOrNull()
            if (broker != null) {
                try {
                    pirRepository.updateBrokerData(jsonFile.name, broker)
                    pirPixelSender.reportUpdateBrokerJsonSuccess(
                        brokerJsonFileName = jsonFile.name,
                        removedAtMs = broker.removedAt ?: 0L,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    pirPixelSender.reportUpdateBrokerJsonFailure(
                        brokerJsonFileName = jsonFile.name,
                        removedAtMs = broker.removedAt ?: 0L,
                    )
                }
            } else {
                pirPixelSender.reportUpdateBrokerJsonFailure(
                    brokerJsonFileName = jsonFile.name,
                    removedAtMs = 0L,
                )
            }
        }

        logcat { "PIR-update: Stored all new broker data" }
    }
}
