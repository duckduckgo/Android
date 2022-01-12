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

package com.duckduckgo.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream

object FileUtilities {

    fun loadText(classLoader: ClassLoader, resourceName: String): String = readResource(classLoader, resourceName).use { it.readText() }

    fun readBytes(classLoader: ClassLoader, resourceName: String): ByteArray {
        return loadResource(classLoader, resourceName).use { it.readBytes() }
    }

    private fun readResource(classLoader: ClassLoader, resourceName: String): BufferedReader {
        return classLoader.getResource(resourceName).openStream().bufferedReader()
    }

    fun loadResource(classLoader: ClassLoader, resourceName: String): InputStream {
        return classLoader.getResource(resourceName).openStream()
    }

    fun getJsonObjectFromFile(classLoader: ClassLoader, filename: String): JSONObject {
        val json = loadText(classLoader, filename)
        return JSONObject(json)
    }
}
