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

package com.duckduckgo.duckchat.impl.ui

import com.duckduckgo.duckchat.impl.ui.nativeinput.edit.AdoptedFile
import com.duckduckgo.duckchat.impl.ui.nativeinput.edit.AdoptedImage
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.hasPendingAdoptedAttachments
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputModeWidgetPendingAttachmentsTest {

    @Test
    fun `no pending attachments to apply when both lists are empty`() {
        assertFalse(hasPendingAdoptedAttachments(pendingImages = emptyList(), pendingFiles = emptyList()))
    }

    @Test
    fun `pending attachments to apply when an image is queued`() {
        val images = listOf(AdoptedImage(data = "base64", format = "png"))

        assertTrue(hasPendingAdoptedAttachments(pendingImages = images, pendingFiles = emptyList()))
    }

    @Test
    fun `pending attachments to apply when a file is queued`() {
        val files = listOf(AdoptedFile(data = "base64", fileName = "report.pdf", mimeType = "application/pdf"))

        assertTrue(hasPendingAdoptedAttachments(pendingImages = emptyList(), pendingFiles = files))
    }
}
