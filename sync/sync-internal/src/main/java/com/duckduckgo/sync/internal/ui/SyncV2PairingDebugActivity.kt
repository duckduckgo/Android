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

package com.duckduckgo.sync.internal.ui

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.clipboard.ClipboardInteractor
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.CustomAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.exchange.v2.Role
import com.duckduckgo.sync.internal.databinding.ActivitySyncV2PairingDebugBinding
import com.duckduckgo.sync.internal.databinding.DialogSyncDebugLogFilterBinding
import com.duckduckgo.sync.internal.databinding.ItemSyncDebugLogFilterOptionBinding
import com.duckduckgo.sync.internal.ui.SyncV2PairingDebugViewModel.ConfirmationRequest
import com.duckduckgo.sync.internal.ui.SyncV2PairingDebugViewModel.TerminalReached
import com.duckduckgo.sync.internal.ui.SyncV2PairingDebugViewModel.ViewState
import com.google.zxing.BarcodeFormat.QR_CODE
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class SyncV2PairingDebugActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var clipboardInteractor: ClipboardInteractor

    private val binding: ActivitySyncV2PairingDebugBinding by viewBinding()
    private val viewModel: SyncV2PairingDebugViewModel by bindViewModel()

    private val logAdapter = SyncV2PairingLogAdapter(
        onCopyJson = { json -> copyToClipboard("Raw JSON", json) },
    )

    private var activeConfirmationDialog: DaxAlertDialog? = null
    private var selectedProtocolVersion: ExchangeProtocolVersion.V2? = null
    private var activeCategories: Set<LogRow.Category> = LogRow.Category.entries.toSet()
    private var renderedRows: List<LogRow>? = null
    private var renderedCategories: Set<LogRow.Category>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        configureLogList()
        configureEdgeToEdgeInsets()
        setupToolbar(binding.includeToolbar.toolbar)
        configureListeners()
        observeViewState()
        observeConfirmations()
        observeTerminals()
        observeToasts()
    }

    private fun configureLogList() {
        binding.logRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.logRecyclerView.adapter = logAdapter
        binding.logRecyclerView.addItemDecoration(AlternatingRowBackgroundDecoration(this))
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.contentScrollView)
    }

    private fun configureListeners() {
        binding.runScanButton.setOnClickListener { startScanFromInput() }
        binding.pasteAndGoButton.setOnClickListener { pasteAndStartScan() }
        binding.runPresentButton.setOnClickListener {
            if (!viewModel.canStartAsPresenter()) {
                Toast.makeText(
                    this,
                    "Sign in to a sync account first — without one there's no recovery code to share. Set sync up in Sync Settings.",
                    Toast.LENGTH_LONG,
                ).show()
                return@setOnClickListener
            }
            viewModel.onRunPresentClicked()
        }
        binding.clearPastedUrlButton.setOnClickListener { binding.pastedUrlInput.text = "" }
        binding.cancelButton.setOnClickListener { viewModel.onCancelClicked() }
        binding.clearLogButton.setOnClickListener {
            logAdapter.clearExpansionState()
            viewModel.onClearLogClicked()
        }
        binding.filterLogButton.setOnClickListener { showFilterLogDialog() }
        binding.autoApproveSetting.quietlySetIsChecked(newCheckedState = true) { _, enabled ->
            viewModel.onAutoApproveToggled(enabled)
        }
        binding.protocolVersionSetting.setOnClickListener { showProtocolVersionDialog() }
        binding.changeProtocolVersionButton.setOnClickListener { showProtocolVersionDialog() }

        binding.signInOutButton.setOnClickListener { viewModel.onSignInOutClicked() }
    }

    @Suppress("DEPRECATION") // Options don't need to be localized in the debug screen
    private fun showProtocolVersionDialog() {
        RadioListAlertDialogBuilder(this)
            .setTitle("Protocol version")
            .setMessage("Force the advertised exchange protocol version. Applies to the next session.")
            .setOptions(
                PROTOCOL_VERSION_OPTIONS.map(viewModel::labelFor),
                PROTOCOL_VERSION_OPTIONS.indexOf(selectedProtocolVersion) + 1,
            )
            .setPositiveButton(com.duckduckgo.mobile.android.R.string.dialogSave)
            .setNegativeButton(android.R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        viewModel.onProtocolOverrideSelected(PROTOCOL_VERSION_OPTIONS[selectedItem - 1])
                    }
                },
            )
            .setCancelable(true)
            .show()
    }

    private fun showFilterLogDialog() {
        val dialogBinding = DialogSyncDebugLogFilterBinding.inflate(layoutInflater)

        val categoryCheckBoxes = LogRow.Category.entries.associateWith { category ->
            val checkBox = ItemSyncDebugLogFilterOptionBinding.inflate(layoutInflater, dialogBinding.categoryContainer, true).root
            checkBox.text = category.label
            checkBox.isChecked = category in activeCategories
            checkBox
        }
        categoryCheckBoxes.values.forEach { checkBox ->
            checkBox.setOnCheckedChangeListener { _, _ ->
                dialogBinding.selectAllCheckBox.isChecked = categoryCheckBoxes.values.all { it.isChecked }
            }
        }

        dialogBinding.selectAllCheckBox.isChecked = categoryCheckBoxes.values.all { it.isChecked }
        dialogBinding.selectAllCheckBox.setOnClickListener {
            val checked = dialogBinding.selectAllCheckBox.isChecked
            categoryCheckBoxes.values.forEach { it.isChecked = checked }
        }

        CustomAlertDialogBuilder(this)
            .setTitle("Filter log")
            .setPositiveButton(android.R.string.ok)
            .setNegativeButton(android.R.string.cancel)
            .setView(dialogBinding)
            .addEventListener(
                object : CustomAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onLogFilterChanged(categoryCheckBoxes.filterValues { it.isChecked }.keys)
                    }
                },
            )
            .show()
    }

    private fun observeViewState() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { render(it) }
            .launchIn(lifecycleScope)
    }

    private fun observeConfirmations() {
        viewModel.confirmations()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { showConfirmationDialog(it) }
            .launchIn(lifecycleScope)
    }

    private fun observeTerminals() {
        viewModel.terminals()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { showTerminalDialog(it) }
            .launchIn(lifecycleScope)
    }

    private fun observeToasts() {
        viewModel.toasts()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
            .launchIn(lifecycleScope)
    }

    private fun showTerminalDialog(terminal: TerminalReached) {
        // Auto-dismiss any open confirmation prompt — the session ended before the user
        // could respond, so the prompt is no longer relevant.
        activeConfirmationDialog?.dismiss()
        activeConfirmationDialog = null
        TextAlertDialogBuilder(this)
            .setTitle(terminal.title)
            .setMessage(terminal.message)
            .setPositiveButton(android.R.string.ok)
            .show()
    }

    private fun showConfirmationDialog(request: ConfirmationRequest) {
        // Replace any prior confirmation dialog (shouldn't happen, but defensive).
        activeConfirmationDialog?.dismiss()
        val peer = request.peerName?.takeIf { it.isNotBlank() } ?: "the other device"
        val (title, message) = when (request.role) {
            Role.Host -> "Confirm pairing (Host)" to "Allow $peer to join your sync & backup?"
            Role.Joiner -> "Confirm pairing (Joiner)" to "Sync your data with $peer?"
        }
        val dialog = TextAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok)
            .setNegativeButton(android.R.string.cancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        activeConfirmationDialog = null
                        viewModel.onConfirmationApproved(request.role)
                    }

                    override fun onNegativeButtonClicked() {
                        activeConfirmationDialog = null
                        viewModel.onConfirmationDenied(request.role)
                    }

                    override fun onDialogDismissed() {
                        activeConfirmationDialog = null
                    }
                },
            )
            .build()
        activeConfirmationDialog = dialog
        dialog.show()
    }

    private fun render(state: ViewState) {
        renderAccountStatus(state.accountStatus)
        renderProtocolVersion(state)
        binding.currentStateTextView.text = state.currentStateLabel
        val code = state.linkingCode
        if (code != null) {
            binding.linkingCodeSectionHeader.visibility = View.VISIBLE
            binding.linkingCodeTextView.visibility = View.VISIBLE
            // Bare URL only — no "Linking code:" prefix — so long-press-select copies cleanly.
            binding.linkingCodeTextView.text = code
            binding.linkingCodeTextView.setOnClickListener { copyToClipboard("Linking code", code) }
            binding.copyLinkingCodeButton.visibility = View.VISIBLE
            binding.copyLinkingCodeButton.setOnClickListener { copyToClipboard("Linking code", code) }
            renderQrFor(code)
            // First time we see this code, push to clipboard automatically so the user can
            // paste straight into the Scanner device without a manual Copy tap.
            if (autoCopiedLinkingCode != code) {
                autoCopiedLinkingCode = code
                copyToClipboard("Linking code", code)
            }
        } else {
            binding.linkingCodeSectionHeader.visibility = View.GONE
            binding.linkingCodeTextView.visibility = View.GONE
            binding.copyLinkingCodeButton.visibility = View.GONE
            binding.linkingCodeQrImage.visibility = View.GONE
            binding.linkingCodeQrImage.setImageBitmap(null)
            renderedQrFor = null
            autoCopiedLinkingCode = null
        }
        renderLog(state)
    }

    private fun renderLog(state: ViewState) {
        activeCategories = state.activeCategories
        if (state.rows === renderedRows && state.activeCategories === renderedCategories) return
        renderedRows = state.rows
        renderedCategories = state.activeCategories
        val visibleRows = state.visibleRows
        logAdapter.submitList(visibleRows)
        binding.logFilterSummaryTextView.text = if (state.activeCategories.size == LogRow.Category.entries.size) {
            "Showing all ${state.rows.size} rows"
        } else {
            "Showing ${visibleRows.size} of ${state.rows.size} rows " +
                "(${state.activeCategories.joinToString { it.label }})"
        }
    }

    private var autoCopiedLinkingCode: String? = null

    /**
     * Encode [code] as a QR bitmap on a background thread (zxing is CPU-bound) and show it in
     * [linkingCodeQrImage]. Skips work if we already rendered this exact code (render() runs
     * on every event flow update — we don't want to regenerate the QR each time).
     */
    @SuppressLint("AvoidComputationUsage")
    private fun renderQrFor(code: String) {
        if (renderedQrFor == code) return
        renderedQrFor = code
        binding.linkingCodeQrImage.visibility = View.VISIBLE
        lifecycleScope.launchWhenStarted {
            val bitmap = withContext(dispatcherProvider.computation()) {
                runCatching { BarcodeEncoder().encodeBitmap(code, QR_CODE, QR_SIZE_PX, QR_SIZE_PX) }
                    .getOrNull()
            }
            if (renderedQrFor == code) binding.linkingCodeQrImage.setImageBitmap(bitmap)
        }
    }

    private var renderedQrFor: String? = null

    private fun copyToClipboard(label: String, value: String) {
        val systemNotificationShown = clipboardInteractor.copyToClipboard(value, isSensitive = false)
        if (!systemNotificationShown) {
            Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
        }
    }

    /** Shared handler for the "Start as Scanner" button — uses whatever is in the input field. */
    private fun startScanFromInput() {
        val pasted = binding.pastedUrlInput.text.trim()
        if (pasted.isEmpty()) {
            Toast.makeText(
                this,
                "Paste the Presenter's linking code into the V2 exchange URL field first.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        viewModel.onRunScanClicked(pasted)
        // Clear the input now that we've handed it off — keeps the field tidy and avoids
        // the user accidentally re-running with a stale code.
        binding.pastedUrlInput.text = ""
        // Dismiss the keyboard so the log + state are visible without scrolling.
        hideKeyboard()
    }

    /**
     * One-tap: read clipboard, fill the input (for visible feedback), then fire start-scan.
     * Skips the keyboard / paste-menu round-trip entirely.
     */
    private fun pasteAndStartScan() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, "Clipboard is empty — copy the Presenter's linking code first.", Toast.LENGTH_LONG).show()
            return
        }
        // Surface what was pasted so the user can see it before it disappears (input is then
        // cleared by startScanFromInput).
        binding.pastedUrlInput.text = text
        startScanFromInput()
    }

    private fun renderProtocolVersion(state: ViewState) {
        selectedProtocolVersion = state.protocolOverride
        binding.protocolVersionSetting.setSecondaryText(viewModel.labelFor(state.protocolOverride))
    }

    private fun renderAccountStatus(status: SyncV2PairingDebugViewModel.AccountStatus) {
        binding.statusSignedIn.setSecondaryText(
            if (status.signedIn) "Yes · user_id ${status.userId ?: "(unknown)"}" else "No",
        )
        binding.signInOutButton.text = if (status.signedIn) "Sign out" else "Sign in"
        binding.statusThirdPartyCredential.setSecondaryText(
            when {
                !status.signedIn -> "(not signed in)"
                status.thirdPartyCredentialCreated -> "Yes"
                else -> "Not created; needed before pairing with 3party peers"
            },
        )
    }

    private companion object {
        const val QR_SIZE_PX = 600

        private val PROTOCOL_VERSION_OPTIONS = listOf(
            null, // Default
            ExchangeProtocolVersion.V2_0,
            ExchangeProtocolVersion.V2_1,
        )
    }
}
