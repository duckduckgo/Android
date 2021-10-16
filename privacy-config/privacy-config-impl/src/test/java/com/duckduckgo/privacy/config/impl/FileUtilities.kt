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

package com.duckduckgo.privacy.config.impl

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream

object FileUtilities {

    fun loadText(resourceName: String): String = readResource(resourceName).use { it.readText() }

    private fun readResource(resourceName: String): BufferedReader {
        return javaClass.classLoader!!.getResource(resourceName).openStream().bufferedReader()
    }

    fun loadResource(resourceName: String): InputStream {
        return javaClass.classLoader!!.getResource(resourceName).openStream()
    }

    fun getJsonObjectFromFile(filename: String): JSONObject {
        val json = loadText(filename)
        return JSONObject(json)
    }
}
