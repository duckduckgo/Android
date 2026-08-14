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
import androidx.core.view.doOnAttach
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStatePublisher
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.ActivityEditPromptBinding
import com.duckduckgo.duckchat.impl.helper.EditPromptResult
import com.duckduckgo.duckchat.impl.helper.EditPromptSessionStore
import com.duckduckgo.duckchat.impl.ui.NativeInputModeWidgetViewModel
import com.duckduckgo.navigation.api.getActivityParams
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(EditPromptScreenParams::class)
class EditPromptActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var editPromptSessionStore: EditPromptSessionStore

    @Inject
    lateinit var nativeInputStatePublisher: NativeInputStatePublisher

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivityEditPromptBinding by viewBinding()

    private val params by lazy { intent.getActivityParams(EditPromptScreenParams::class.java) }

    private var submitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.MISC)
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }

        setContentView(binding.root)
        with(binding.includeToolbar.toolbar) {
            setupToolbar(this)
            title = getString(R.string.duck_ai_edit_prompt_title)
            setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
            navigationContentDescription = getString(com.duckduckgo.mobile.android.R.string.cancel)
            setNavigationOnClickListener { finish() }
        }

        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }

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
            // configureForEdit's own widget setup runs on attach; focusing before that leaves the
            // field not yet focusable. Queuing after it (registered later, so it runs after) fixes it.
            asView().doOnAttach { focusInput(this@EditPromptActivity) }
        }

        // The store's CompletableDeferred fans out to any number of awaiters, so this doesn't race
        // submit()/onDestroy()'s own resolve() calls — it just closes the screen if the session gets
        // resolved from outside it, e.g. a cancelEdit fired while the FE's own timeout expired.
        lifecycleScope.launch {
            editPromptSessionStore.await(sessionId)
            if (!isFinishing && !isDestroyed) finish()
        }
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        // editPromptContent is a ScrollView (so a squeezed keyboard scrolls the card into view
        // instead of clipping it) — use the scrollable variant so the last item still clears
        // the nav bar/IME rather than the static-content padding.
        edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.editPromptContent)
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

    /**
     * Every way out of this screen — toolbar close, system back, predictive back — routes through
     * finish(), so resolving here gets the cancel across the JS bridge while the edit screen still
     * covers the web view. Waiting for onDestroy() would land the reply after the exit animation,
     * making the frontend's leave-edit re-render visible on the transcript the user is back on.
     */
    override fun finish() {
        params?.sessionId?.let { editPromptSessionStore.resolve(it, EditPromptResult.Cancelled) }
        super.finish()
    }

    override fun onDestroy() {
        params?.sessionId?.let { sessionId ->
            // Backstop for teardowns that never went through a cancel gesture.
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
