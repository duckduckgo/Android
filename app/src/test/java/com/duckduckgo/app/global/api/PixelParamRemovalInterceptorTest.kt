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

package com.duckduckgo.app.global.api

import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PixelParamRemovalInterceptorTest {
    private lateinit var pixelRemovalInterceptor: PixelParamRemovalInterceptor

    @Before
    fun setup() {
        pixelRemovalInterceptor = PixelParamRemovalInterceptor(
            pixelsPlugin = object : PluginPoint<PixelParamRemovalPlugin> {
                override fun getPlugins(): Collection<PixelParamRemovalPlugin> {
                    return listOf(
                        object : PixelParamRemovalPlugin {
                            override fun names(): List<Pair<String, Set<PixelParameter>>> {
                                return testPixels
                            }
                        },
                    )
                }
            },
        )
    }

    @Test
    fun whenSendPixelRedactAppVersion() {
        testPixels.filter { it.second == PixelParameter.removeVersion() }.map { it.first }.forEach { pixelName ->
            val pixelUrl = String.format(PIXEL_TEMPLATE, pixelName)
            val interceptedUrl = pixelRemovalInterceptor.intercept(FakeChain(pixelUrl)).request.url
            assertNotNull(interceptedUrl.queryParameter("atb"))
            assertNull(interceptedUrl.queryParameter("appVersion"))
        }
    }

    @Test
    fun whenSendPixelRedactAtb() {
        testPixels.filter { it.second == PixelParameter.removeAtb() }.map { it.first }.forEach { pixelName ->
            val pixelUrl = String.format(PIXEL_TEMPLATE, pixelName)
            val interceptedUrl = pixelRemovalInterceptor.intercept(FakeChain(pixelUrl)).request.url
            assertNull(interceptedUrl.queryParameter("atb"))
            assertNotNull(interceptedUrl.queryParameter("appVersion"))
        }
    }

    @Test
    fun whenSendPixelRedactAtbAndAppVersion() {
        testPixels.filter { it.second.containsAll(PixelParameter.removeAll()) }
            .map { it.first }
            .forEach { pixelName ->
                val pixelUrl = String.format(PIXEL_TEMPLATE, pixelName)
                val interceptedUrl = pixelRemovalInterceptor.intercept(FakeChain(pixelUrl)).request.url
                assertNull(interceptedUrl.queryParameter("atb"))
                assertNull(interceptedUrl.queryParameter("appVersion"))
            }
    }

    companion object {
        private const val PIXEL_TEMPLATE = "https://improving.duckduckgo.com/t/%s_android_phone?atb=v255-7zu&appVersion=5.74.0&os_version=1.0&test=1"
        private val testPixels = listOf(
            "atb_and_version_redacted" to PixelParameter.removeAll(),
            "atb_redacted" to PixelParameter.removeAtb(),
            "version_redacted" to PixelParameter.removeVersion(),
        )
    }
}
