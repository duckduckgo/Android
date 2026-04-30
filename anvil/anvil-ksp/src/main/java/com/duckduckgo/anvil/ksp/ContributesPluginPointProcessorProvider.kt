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

package com.duckduckgo.anvil.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Not registered via @AutoService yet because [ContributesActivePluginPointCodeGenerator] in
 * anvil-compiler generates code that uses @ContributesPluginPoint, and that Anvil-generated code
 * is not visible to KSP. Until ContributesActivePluginPoint is also ported to KSP, the Anvil
 * [ContributesPluginPointCodeGenerator] must remain active, and enabling this KSP processor
 * via AutoService would cause duplicate generation in modules that have both processors.
 *
 * To activate: add `@AutoService(SymbolProcessorProvider::class)` and delete the Anvil generator.
 */
class ContributesPluginPointProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ContributesPluginPointProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
    }
}
