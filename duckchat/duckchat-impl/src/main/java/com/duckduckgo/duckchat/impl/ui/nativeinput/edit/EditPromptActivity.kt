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

package com.duckduckgo.duckchat.impl.ui.nativeinput.edit

import android.os.Bundle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStatePublisher
import com.duckduckgo.duckchat.impl.databinding.ActivityEditPromptBinding
import com.duckduckgo.duckchat.impl.helper.EditPromptResult
import com.duckduckgo.duckchat.impl.helper.EditPromptSessionStore
import com.duckduckgo.duckchat.impl.ui.NativeInputModeWidgetViewModel
import com.duckduckgo.navigation.api.getActivityParams
import org.json.JSONArray
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(EditPromptScreenParams::class)
class EditPromptActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var editPromptSessionStore: EditPromptSessionStore

    @Inject
    lateinit var nativeInputStatePublisher: NativeInputStatePublisher

    private val binding: ActivityEditPromptBinding by viewBinding()

    private val params by lazy { intent.getActivityParams(EditPromptScreenParams::class.java) }

    private var submitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val sessionId = params?.sessionId
        val payload = sessionId?.let { editPromptSessionStore.payload(it) }
        if (sessionId == null || payload == null) {
            // Nothing to edit: the session died with the process or was already resolved.
            finish()
            return
        }

        with(binding.editPromptInput) {
            configureForEdit(sessionId)
            adoptEditAttachments(payload.images, payload.files)
            text = payload.prompt
            onChatSent = { submit(sessionId) }
            hideMainButtons()
            focusInput(this@EditPromptActivity)
        }
        binding.editPromptCancel.setOnClickListener { finish() }
    }

    private fun submit(sessionId: String) {
        submitted = true
        editPromptSessionStore.resolve(
            sessionId,
            EditPromptResult.Submitted(
                prompt = binding.editPromptInput.text,
                images = binding.editPromptInput.getImageAttachmentsJson().toSubmittedImages(),
                files = binding.editPromptInput.getFileAttachmentsJson().toSubmittedFiles(),
            ),
        )
        finish()
    }

    override fun onDestroy() {
        params?.sessionId?.let { sessionId ->
            if (!submitted && !isChangingConfigurations) editPromptSessionStore.resolve(sessionId, EditPromptResult.Cancelled)
            nativeInputStatePublisher.clearTab(NativeInputModeWidgetViewModel.editStateKey(sessionId))
        }
        super.onDestroy()
    }

    // Mirrors the shape AttachmentViewModel.getImageAttachmentsJson() produces: {"data", "format"}.
    private fun JSONArray?.toSubmittedImages(): List<SubmittedImage> {
        if (this == null) return emptyList()
        return (0 until length()).map { index ->
            val json = getJSONObject(index)
            SubmittedImage(data = json.getString("data"), format = json.getString("format"))
        }
    }

    // Mirrors the shape AttachmentViewModel.getFileAttachmentsJson() produces: {"data", "fileName", "mimeType"}.
    private fun JSONArray?.toSubmittedFiles(): List<SubmittedFile> {
        if (this == null) return emptyList()
        return (0 until length()).map { index ->
            val json = getJSONObject(index)
            SubmittedFile(
                data = json.getString("data"),
                fileName = json.getString("fileName"),
                mimeType = json.getString("mimeType"),
            )
        }
    }
}
