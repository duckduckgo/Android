/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.build_time.reporter

import com.duckduckgo.build_time.BuildTimePlugin
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.reflect.TypeOf
import java.io.PrintStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class PixelReporter(
    private val ext: BuildTimePluginExtension,
    override val out: PrintStream = System.out,
    override val delimiter: String = " | "
) : BuildTimeReporter {
    override fun report(input: BuildTimeReport) {
        val sentPixel = System.getenv("SEND_BUILD_TIME")
        if (true == sentPixel?.toBoolean()) {
            sendBuildTime(input.buildDuration)
        }

        ConsoleReporter(ext).report(input)
    }

    private fun sendBuildTime(duration: Long) {
        val client = HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder()
            .header("User-Agent", UA)
            .uri(URI.create("$PIXEL/$ANDROID_BUILD_TIME_PIXEL?buildTime=${duration}&atb=${String()}"))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val extra = (ext as ExtensionAware).extensions.getByType(object : TypeOf<Map<String, Any>>() {})
        if (response.statusCode() != 200) {
            (extra[BuildTimePlugin.LOGGER_KEY] as Logger).warn(
                "Error sending build times:\n{}",
                response.body()
            )
        } else {
            (extra[BuildTimePlugin.LOGGER_KEY] as Logger).info("Build time duration sent in pixel `{}`: {}s", ANDROID_BUILD_TIME_PIXEL, duration)
        }
    }

    companion object {
        private const val PIXEL = "https://improving.duckduckgo.com/t"
        private const val ANDROID_BUILD_TIME_PIXEL = "m_build_time_android"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/64.0.3282.137 Mobile Safari/537.36"
    }
}
