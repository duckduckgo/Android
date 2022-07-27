/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.autofill

import android.content.Context
import com.duckduckgo.app.browser.R
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.io.BufferedReader
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class FileBasedJavascriptInjector @Inject constructor() : JavascriptInjector {
    private lateinit var functions: String
    private lateinit var aliasFunctions: String
    private lateinit var signOutFunctions: String

    override fun getFunctionsJS(): String {
        if (!this::functions.isInitialized) {
            // this can be enabled to see more verbose output from the autofill JS; useful for debugging autofill-related issues
            val debugMode = false

            functions = loadJs(if (debugMode) "autofill-debug.js" else "autofill.js")
        }
        return functions
    }

    override fun getAliasFunctions(
        context: Context,
        alias: String?
    ): String {
        if (!this::aliasFunctions.isInitialized) {
            aliasFunctions = context.resources.openRawResource(R.raw.inject_alias).bufferedReader().use { it.readText() }
        }
        return aliasFunctions.replace("%s", alias.orEmpty())
    }

    override fun getSignOutFunctions(
        context: Context
    ): String {
        if (!this::signOutFunctions.isInitialized) {
            signOutFunctions = context.resources.openRawResource(R.raw.signout_autofill).bufferedReader().use { it.readText() }
        }
        return signOutFunctions
    }

    fun loadJs(resourceName: String): String = readResource(resourceName).use { it?.readText() }.orEmpty()

    private fun readResource(resourceName: String): BufferedReader? {
        return javaClass.classLoader?.getResource(resourceName)?.openStream()?.bufferedReader()
    }
}
