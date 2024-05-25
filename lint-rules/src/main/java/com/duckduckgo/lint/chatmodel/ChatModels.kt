/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.lint.chatmodel

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel
import kotlin.LazyThreadSafetyMode.SYNCHRONIZED

/**
 * Switch chat models from the command line
 *
 * Defaults to Ollama with llama3
 *
 * ./gradlew lintFix --continue -Dcom.duckduckgo.lint.model=openai -Dcom.duckduckgo.lint.openai.key=MY_API_KEY
 *
 */
object ChatModels {

    val chatModel: ChatLanguageModel by lazy(SYNCHRONIZED) {
        val model = System.getProperty("com.duckduckgo.lint.model")
        when (model) {
            "openai" -> getOpenAiModel()
            "gemini" -> getGeminiModel()
            else -> getOllamaModel()
        }
    }

    private fun getOllamaModel() = OllamaChatModel.builder().modelName("llama3").temperature(0.0).seed(0)
        /**
         * Ollama binds 127.0.0.1 port 11434 by default. Change the bind address with the OLLAMA_HOST environment variable
         * See https://github.com/ollama/ollama/blob/main/docs/faq.md
         */
        .baseUrl("http://127.0.0.1:11434").build()

    private fun getOpenAiModel() =
        OpenAiChatModel.builder()
            .apiKey(System.getProperty("com.duckduckgo.lint.openai.key"))
            .modelName(System.getProperty("com.duckduckgo.lint.openai.model"))
            .temperature(0.0)
            .seed(0)
            .build()

    private fun getGeminiModel() = VertexAiGeminiChatModel.builder()
        .temperature(0.0f)
        .project(System.getProperty("com.duckduckgo.lint.gemini.project"))
        .location(System.getProperty("com.duckduckgo.lint.gemini.location"))
        .modelName(System.getProperty("com.duckduckgo.lint.gemini.model"))
        .build()
}
