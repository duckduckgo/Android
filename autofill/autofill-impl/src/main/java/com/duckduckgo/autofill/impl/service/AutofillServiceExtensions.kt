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

package com.duckduckgo.autofill.impl.service

import com.duckduckgo.autofill.impl.service.AutofillFieldType.UNKNOWN

fun List<AutofillRootNode>.findBestFillableNode(): AutofillRootNode? {
    return this.firstNotNullOfOrNull { rootNode ->
        val focusedDetectedField = rootNode.parsedAutofillFields
            .firstOrNull { field ->
                field.originalNode.isFocused && field.type != UNKNOWN
            }
        if (focusedDetectedField != null) {
            return@firstNotNullOfOrNull rootNode
        }

        val firstDetectedField = rootNode.parsedAutofillFields.firstOrNull { field -> field.type != UNKNOWN }
        if (firstDetectedField != null) {
            return@firstNotNullOfOrNull rootNode
        }
        return@firstNotNullOfOrNull null
    }
}
