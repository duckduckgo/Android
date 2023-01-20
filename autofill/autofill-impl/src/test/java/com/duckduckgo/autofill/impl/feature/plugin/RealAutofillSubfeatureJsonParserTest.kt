/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.feature.plugin

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.feature.AutofillSubfeature
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName.AccessCredentialManagement
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName.InjectCredentials
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealAutofillSubfeatureJsonParserTest {

    private lateinit var testee: RealAutofillSubfeatureJsonParser

    @Test
    fun whenNoSubfeatureInJsonAndNoPluginsThenDoesNotCrash() {
        configureWithPlugins(emptyList())
        testee.processSubfeatures(JSONObject())
    }

    @Test
    fun whenSubfeatureInJsonAndNoPluginsThenDoesNotCrash() {
        configureWithPlugins(emptyList())
        val config = """
            {
                "accessCredentialManagement": {
                    "state": "enabled"
                }
            }
        """.asJson()
        testee.processSubfeatures(config)
    }

    @Test
    fun whenNoSubfeaturesInJsonThenPluginDoesNotProcessConfig() {
        val testPlugin = TestAutofillSubfeaturePlugin(AccessCredentialManagement)
        configureWithPlugins(listOf(testPlugin))
        testee.processSubfeatures(JSONObject())
        assertFalse(testPlugin.processed)
    }

    @Test
    fun whenOnlyPluginMatchesFeatureKeyThenProcessesConfig() {
        val testPlugin = TestAutofillSubfeaturePlugin(AccessCredentialManagement)
        configureWithPlugins(listOf(testPlugin))
        val config = """
            {
                "accessCredentialManagement": {
                    "state": "enabled"
                }
            }
        """.asJson()
        testee.processSubfeatures(config)
        assertTrue(testPlugin.processed)
    }

    @Test
    fun whenOnlyPluginDoesNotMatchFeatureKeyThenDoesNotProcessesIt() {
        val testPlugin = TestAutofillSubfeaturePlugin(AccessCredentialManagement)
        configureWithPlugins(listOf(testPlugin))
        val config = """
            {
                "notAMatchingFeature": {
                    "state": "enabled"
                }
            }
        """.asJson()
        testee.processSubfeatures(config)
        assertFalse(testPlugin.processed)
    }

    @Test
    fun whenMultiplePluginsWithOneMatchThenCorrectOneProcessesIt() {
        val testPlugin1 = TestAutofillSubfeaturePlugin(AccessCredentialManagement)
        val testPlugin2 = TestAutofillSubfeaturePlugin(InjectCredentials)
        configureWithPlugins(listOf(testPlugin1, testPlugin2))
        val config = """
            {
                "${AccessCredentialManagement.value}": {
                    "state": "enabled"
                },
                "notAMatchingFeature": {
                    "state": "enabled"
                }
            }
        """.asJson()
        testee.processSubfeatures(config)
        assertTrue(testPlugin1.processed)
        assertFalse(testPlugin2.processed)
    }

    private fun configureWithPlugins(plugins: List<AutofillSubfeaturePlugin>) {
        testee = RealAutofillSubfeatureJsonParser(plugins.toSet())
    }

    private fun String.asJson(): JSONObject = JSONObject(this)

    private class TestAutofillSubfeaturePlugin(val name: AutofillSubfeature) : AutofillSubfeaturePlugin {
        var processed = false

        override fun store(rawJson: String): Boolean {
            processed = true
            return true
        }

        override val settingName: AutofillSubfeature
            get() = name
    }
}
