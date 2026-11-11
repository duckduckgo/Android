/*
 * Copyright (c) 2026 DuckDuckGo
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

import com.duckduckgo.pir.impl.di.PirModule
import com.squareup.moshi.Moshi
import okio.source
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.File

/**
 * The bundled broker JSONs ship with this app version, so every one of them has to pass the same validation that
 * gates remote broker JSON updates. Otherwise the bundled fallback would silently store no data for those brokers.
 */
class BundledBrokerJsonsValidationTest {

    private val validator = RealBrokerStepsValidator(PirModule().providePirMoshi(Moshi.Builder().build()))
    private val processor = RealBrokerJsonProcessor(mock(), mock(), mock(), validator)

    @Test
    fun allBundledBrokerJsonsPassValidation() {
        val bundledFiles = File("src/main/assets/brokers").listFiles { file -> file.extension == "json" }.orEmpty()
        assertTrue("No bundled broker JSONs found", bundledFiles.isNotEmpty())

        val failures = bundledFiles.mapNotNull { file ->
            val broker = processor.parseBroker(file.source()) ?: return@mapNotNull "${file.name}: ${BrokerJsonUpdateFailureReason.INVALID_JSON}"
            validator.validate(broker.steps)?.let { reason -> "${file.name}: $reason" }
        }

        assertTrue("Bundled broker JSONs failing validation:\n${failures.joinToString("\n")}", failures.isEmpty())
    }
}
