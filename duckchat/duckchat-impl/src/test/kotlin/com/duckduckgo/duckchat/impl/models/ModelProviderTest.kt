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

package com.duckduckgo.duckchat.impl.models

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelProviderTest {

    @Test
    fun whenIdHasMetaLlamaSlashPrefixThenMeta() {
        assertEquals(ModelProvider.META, ModelProvider.from("meta-llama/Llama-3.3-70B-Instruct", providerString = "anything"))
    }

    @Test
    fun whenIdHasMetaLlamaUnderscorePrefixThenMeta() {
        assertEquals(ModelProvider.META, ModelProvider.from("meta-llama_Llama-3.3", providerString = null))
    }

    @Test
    fun whenProviderIsAzureThenMeta() {
        assertEquals(ModelProvider.META, ModelProvider.from("some-id", providerString = "azure"))
    }

    @Test
    fun whenIdHasMistralAiSlashPrefixThenMistral() {
        assertEquals(ModelProvider.MISTRAL, ModelProvider.from("mistralai/Mistral-Small-24B", providerString = "openai"))
    }

    @Test
    fun whenIdHasMistralAiUnderscorePrefixThenMistral() {
        assertEquals(ModelProvider.MISTRAL, ModelProvider.from("mistralai_Mistral-Small", providerString = null))
    }

    @Test
    fun whenIdContainsGptOssThenOss() {
        assertEquals(ModelProvider.OSS, ModelProvider.from("openai/gpt-oss-120b", providerString = "openai"))
    }

    @Test
    fun whenProviderIsAnthropicAndIdNotOverriddenThenAnthropic() {
        assertEquals(ModelProvider.ANTHROPIC, ModelProvider.from("claude-3-5-sonnet", providerString = "anthropic"))
    }

    @Test
    fun whenProviderIsOpenAiAndIdNotOverriddenThenOpenAi() {
        assertEquals(ModelProvider.OPENAI, ModelProvider.from("gpt-5-mini", providerString = "openai"))
    }

    @Test
    fun whenProviderIsNullAndIdHasNoOverrideThenUnknown() {
        assertEquals(ModelProvider.UNKNOWN, ModelProvider.from("gpt-5-mini", providerString = null))
    }

    @Test
    fun whenProviderIsUnknownStringThenUnknown() {
        assertEquals(ModelProvider.UNKNOWN, ModelProvider.from("some-id", providerString = "perplexity"))
    }

    @Test
    fun whenIdPrefixMatchesThenTakesPriorityOverProviderString() {
        assertEquals(ModelProvider.META, ModelProvider.from("meta-llama/Llama-3", providerString = "openai"))
        assertEquals(ModelProvider.OSS, ModelProvider.from("openai/gpt-oss-20b", providerString = "openai"))
    }
}
