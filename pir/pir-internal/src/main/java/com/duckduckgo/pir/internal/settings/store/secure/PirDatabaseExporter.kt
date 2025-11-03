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

package com.duckduckgo.pir.internal.settings.store.secure

import android.content.Context
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.store.PirDatabase
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import java.io.File
import javax.inject.Inject

/**
 * Utility interface for exporting the encrypted PIR database to a plaintext format.
 * Useful for debugging and data inspection.
 */
interface PirDatabaseExporter {
    /**
     * Exports the encrypted database to a plaintext (unencrypted) SQLite database
     * on the device's external storage.
     *
     * You can pull it using "adb pull /storage/emulated/0/Android/data/com.duckduckgo.mobile.android.debug/files/pir_decrypted.db ~/Desktop/"
     * and open it in a SQLite viewer for inspection.
     */
    suspend fun exportToPlaintext()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = PirDatabaseExporter::class,
)
class PirRealDatabaseExporter @Inject constructor(
    private val pirDatabase: PirDatabase,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context,
) : PirDatabaseExporter {

    override suspend fun exportToPlaintext() = withContext(dispatcherProvider.io()) {
        exportDecryptedDatabase()
    }

    private fun exportDecryptedDatabase() {
        try {
            // Get the writable database instance
            val db = pirDatabase.openHelper.writableDatabase

            // Define output path - using external files directory for accessibility
            val outputFile = File(context.getExternalFilesDir(null), "pir_decrypted.db")

            // Delete existing file if present
            if (outputFile.exists()) {
                outputFile.delete()
                logcat { "PIR-DB: Deleted existing decrypted database file" }
            }

            val outputPath = outputFile.absolutePath
            logcat { "PIR-DB: Exporting decrypted database to: $outputPath" }

            // Attach a plaintext database (no encryption key)
            db.query("ATTACH DATABASE ? AS plaintext KEY ''", arrayOf(outputPath)).use { cursor ->
                cursor.moveToFirst()
            }

            logcat { "PIR-DB: Attached plaintext database" }

            // Export the encrypted database to the plaintext one
            db.query("SELECT sqlcipher_export('plaintext')").use { cursor ->
                if (cursor.moveToFirst()) {
                    val result = cursor.getString(0)
                    logcat { "PIR-DB: Export result: $result" }
                }
            }

            logcat { "PIR-DB: Database exported successfully" }

            // Detach the plaintext database
            db.query("DETACH DATABASE plaintext").use { cursor ->
                cursor.moveToFirst()
            }

            logcat { "PIR-DB: Detached plaintext database" }

            // Verify the file was created and has content
            if (outputFile.exists() && outputFile.length() > 0) {
                logcat { "PIR-DB: Successfully exported decrypted database (${outputFile.length()} bytes)" }
                logcat { "PIR-DB: File location: ${outputFile.absolutePath}" }
            } else {
                logcat(ERROR) { "PIR-DB: Export file was not created or is empty" }
            }
        } catch (e: Exception) {
            logcat(ERROR) { "PIR-DB: Error exporting database: ${e.asLog()}" }
        }
    }
}
