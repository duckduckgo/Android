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

package com.duckduckgo.duckchat.internal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.promo.data.PromoRecord
import com.duckduckgo.duckchat.impl.promo.data.PromoTerms
import com.duckduckgo.duckchat.internal.DuckAiPromoCampaignDevViewModel.Command
import com.duckduckgo.duckchat.internal.DuckAiPromoCampaignDevViewModel.ViewState
import com.duckduckgo.duckchat.internal.databinding.ActivityDuckAiPromoCampaignDevBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class DuckAiPromoCampaignDevActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivityDuckAiPromoCampaignDevBinding by viewBinding()
    private val viewModel: DuckAiPromoCampaignDevViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        configureEdgeToEdgeInsets()
        setupToolbar(binding.toolbar)
        title = getString(R.string.devSettingsDuckAiPromoCampaignTitle)

        binding.seedPendingPromo.setOnClickListener { viewModel.onSeedPendingPromoClicked(binding.campaignId.text) }
        binding.seedExpiredPromo.setOnClickListener { viewModel.onSeedExpiredPromoClicked(binding.campaignId.text) }
        binding.dismissOffer.setOnClickListener { viewModel.onDismissOfferClicked(binding.campaignId.text) }
        binding.activatePendingPromo.setOnClickListener { viewModel.onActivatePendingPromoClicked() }
        binding.endPromo.setOnClickListener { viewModel.onEndPromoClicked() }
        binding.forceEndPromo.setOnClickListener { viewModel.onForceEndPromoClicked() }
        binding.invalidatePromo.setOnClickListener { viewModel.onInvalidatePromoClicked() }
        binding.clearPromoRecord.setOnClickListener { viewModel.onClearPromoRecordClicked() }

        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { renderView(it) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.appBar)
        edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.contentScrollView)
    }

    private fun renderView(viewState: ViewState) {
        binding.promoRecord.setSecondaryText(
            when {
                viewState.isPromoRecordCorrupted -> getString(R.string.devSettingsDuckAiPromoCampaignRecordCorrupted)
                viewState.promoRecord != null -> viewState.promoRecord.describe()
                else -> getString(R.string.devSettingsDuckAiPromoCampaignNone)
            },
        )
    }

    private fun PromoRecord.describe(): String {
        val none = getString(R.string.devSettingsDuckAiPromoCampaignNone)
        return getString(
            R.string.devSettingsDuckAiPromoCampaignRecordValue,
            campaignId ?: none,
            promoId ?: none,
            terms?.describe() ?: none,
            dismissedCampaignId ?: none,
            lastRedeemAttemptMillis?.let(::formatDateTime) ?: none,
        )
    }

    private fun PromoTerms.describe(): String = getString(
        R.string.devSettingsDuckAiPromoCampaignTermsValue,
        multiplier,
        formatDateTime(expiresAtMillis),
    )

    private fun formatDateTime(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(millis))

    private fun processCommand(command: Command) {
        when (command) {
            is Command.ShowMessage -> Toast.makeText(this, getString(command.messageResId), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, DuckAiPromoCampaignDevActivity::class.java)
    }
}
