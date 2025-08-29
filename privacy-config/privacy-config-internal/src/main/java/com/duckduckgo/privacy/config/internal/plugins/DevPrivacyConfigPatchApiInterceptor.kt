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

package com.duckduckgo.privacy.config.internal.plugins

import android.content.Context
import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PRIVACY_REMOTE_CONFIG_URL
import com.duckduckgo.privacy.config.internal.BuildConfig
import com.fasterxml.jackson.databind.ObjectMapper
import com.flipkart.zjsonpatch.JsonPatch
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import logcat.LogPriority.ERROR
import logcat.logcat
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ApiInterceptorPlugin::class,
)
class DevPrivacyConfigPatchApiInterceptor @Inject constructor(
    private val context: Context,
) : ApiInterceptorPlugin, Interceptor {

    private val objectMapper = ObjectMapper()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.toString() == PRIVACY_REMOTE_CONFIG_URL) {
            val response = chain.proceed(request)

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val modifiedJson = patchPrivacyConfigResponse(responseBody)
                    val mediaType = response.body?.contentType() ?: "application/json".toMediaType()
                    return response.newBuilder()
                        .body(modifiedJson.toResponseBody(mediaType))
                        .build()
                }
            }

            return response
        }

        return chain.proceed(chain.request())
    }

    private fun patchPrivacyConfigResponse(originalJson: String): String {
        return try {
            val configPatches = BuildConfig.CONFIG_PATCHES
            if (configPatches.isBlank()) {
                return originalJson
            }

            var jsonNode = objectMapper.readTree(originalJson)
            val patchFileNames = configPatches.split(',').filter { it.isNotBlank() }

            logcat { "Applying ${patchFileNames.size} config patches: $patchFileNames" }

            patchFileNames.forEach { patchFileName ->
                try {
                    val patchJson = loadPatchFromAssets(patchFileName)
                    if (patchJson != null) {
                        val patchArray = objectMapper.readTree(patchJson)
                        jsonNode = JsonPatch.apply(patchArray, jsonNode)
                        logcat { "Successfully applied patch: $patchFileName" }
                    } else {
                        logcat(ERROR) { "Failed to load patch file: $patchFileName" }
                    }
                } catch (e: Exception) {
                    logcat(ERROR) { "Failed to apply patch $patchFileName: ${e.message}" }
                }
            }

            objectMapper.writeValueAsString(jsonNode)
        } catch (e: Exception) {
            logcat(ERROR) { "Failed to patch privacy config response: ${e.message}" }
            originalJson
        }
    }

    private fun loadPatchFromAssets(fileName: String): String? {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            logcat { "Failed to load patch file from assets: $fileName - ${e.message}" }
            null
        }
    }

    override fun getInterceptor(): Interceptor {
        return this
    }
}
