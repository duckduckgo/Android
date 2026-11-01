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

package com.duckduckgo.lint.ui

import java.io.File

/**
 * Single place deciding where Compose hosts (ComposeView, setContent) are allowed
 * while Compose adoption is limited to the Phase 2 scenarios.
 */
internal object ComposeHostingPolicy {

    private val DESIGN_SYSTEM_MODULES = setOf("design-system", "design-system-internal")
    private const val INTERNAL_MODULE_SUFFIX = "-internal"
    private const val INTERNAL_SOURCE_SET_SEGMENT = "/src/internal/"

    fun isComposeHostAllowed(projectName: String, file: File): Boolean {
        if (projectName in DESIGN_SYSTEM_MODULES) return true
        if (projectName.endsWith(INTERNAL_MODULE_SUFFIX)) return true
        return file.path.replace(File.separatorChar, '/').contains(INTERNAL_SOURCE_SET_SEGMENT)
    }
}
