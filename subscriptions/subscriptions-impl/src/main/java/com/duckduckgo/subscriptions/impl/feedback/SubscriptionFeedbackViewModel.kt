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

package com.duckduckgo.subscriptions.impl.feedback

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.DDG_SETTINGS
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.SUBSCRIPTION_SETTINGS
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.VPN_EXCLUDED_APPS
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.VPN_MANAGEMENT
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.ITR
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.PIR
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.SUBS_AND_PAYMENTS
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.VPN
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackReportType.GENERAL_FEEDBACK
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackReportType.REPORT_PROBLEM
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackReportType.REQUEST_FEATURE
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackSubsSubCategory.ONE_TIME_PASSWORD
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackSubsSubCategory.OTHER
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.Command.FeedbackCompleted
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.FeedbackFragmentState.FeedbackAction
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.FeedbackFragmentState.FeedbackCategory
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.FeedbackFragmentState.FeedbackGeneral
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.FeedbackFragmentState.FeedbackSubCategory
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.FeedbackFragmentState.FeedbackSubmit
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackVpnSubCategory.BROWSER_CRASH_FREEZE
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackVpnSubCategory.CANNOT_CONNECT_TO_LOCAL_DEVICE
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackVpnSubCategory.FAILS_TO_CONNECT
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackVpnSubCategory.ISSUES_WITH_APPS_OR_WEBSITES
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackVpnSubCategory.SLOW_CONNECTION
import com.duckduckgo.subscriptions.impl.feedback.pixels.PrivacyProUnifiedFeedbackPixelSender
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SubscriptionFeedbackViewModel @Inject constructor(
    private val pixelSender: PrivacyProUnifiedFeedbackPixelSender,
    private val feedbackCustomMetadataProvider: FeedbackCustomMetadataProvider,
) : ViewModel() {
    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun viewState(): Flow<ViewState> = viewState.asStateFlow()
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    fun onProFeedbackSelected() {
        viewModelScope.launch {
            val previousFragmentState = viewState.value.currentFragmentState
            val newFragmentState = FeedbackAction

            viewState.emit(
                ViewState(
                    feedbackMetadata = viewState.value.feedbackMetadata,
                    currentFragmentState = newFragmentState,
                    previousFragmentState = previousFragmentState,
                    isForward = true,
                ),
            )

            emitImpressionPixels(newFragmentState, viewState.value.feedbackMetadata)
        }
    }

    fun onReportTypeSelected(reportType: SubscriptionFeedbackReportType) {
        viewModelScope.launch {
            val previousFragmentState = viewState.value.currentFragmentState
            var newMetadata = viewState.value.feedbackMetadata.copy(
                reportType = reportType,
            )

            val nextState = when (reportType) {
                REPORT_PROBLEM -> {
                    val source = newMetadata.source
                    when (source) {
                        SUBSCRIPTION_SETTINGS -> {
                            newMetadata = newMetadata.copy(category = SUBS_AND_PAYMENTS)
                            FeedbackSubCategory(newMetadata.category!!.asTitle())
                        }

                        VPN_MANAGEMENT, VPN_EXCLUDED_APPS -> {
                            newMetadata = newMetadata.copy(category = VPN)
                            FeedbackSubCategory(newMetadata.category!!.asTitle())
                        }

                        else -> {
                            FeedbackCategory(reportType.asTitle())
                        }
                    }
                }

                GENERAL_FEEDBACK -> FeedbackSubmit(reportType.asTitle())
                REQUEST_FEATURE -> FeedbackSubmit(reportType.asTitle())
            }

            viewState.emit(
                ViewState(
                    feedbackMetadata = newMetadata,
                    currentFragmentState = nextState,
                    previousFragmentState = previousFragmentState,
                    isForward = true,
                ),
            )

            emitImpressionPixels(nextState, newMetadata)
        }
    }

    fun onCategorySelected(category: SubscriptionFeedbackCategory) {
        viewModelScope.launch {
            val previousFragmentState = viewState.value.currentFragmentState
            val newMetadata = viewState.value.feedbackMetadata.copy(
                category = category,
            )

            val nextState = if (viewState.value.feedbackMetadata.reportType == REPORT_PROBLEM) {
                FeedbackSubCategory(category.asTitle())
            } else {
                FeedbackSubmit(category.asTitle())
            }

            viewState.emit(
                ViewState(
                    feedbackMetadata = newMetadata,
                    currentFragmentState = nextState,
                    previousFragmentState = previousFragmentState,
                    isForward = true,
                ),
            )

            emitImpressionPixels(nextState, newMetadata)
        }
    }

    fun onSubcategorySelected(subCategory: SubscriptionFeedbackSubCategory) {
        viewModelScope.launch {
            val previousFragmentState = viewState.value.currentFragmentState
            val newMetadata = viewState.value.feedbackMetadata.copy(
                subCategory = subCategory,
            )
            val newFragmentState = FeedbackSubmit(subCategory.asTitle())

            viewState.emit(
                ViewState(
                    feedbackMetadata = newMetadata,
                    currentFragmentState = newFragmentState,
                    previousFragmentState = previousFragmentState,
                    isForward = true,
                ),
            )
            emitImpressionPixels(newFragmentState, newMetadata)
        }
    }

    fun onSubmitFeedback(description: String) {
        viewModelScope.launch {
            val metadata = viewState.value.feedbackMetadata.copy(
                description = description,
            )

            when (metadata.reportType) {
                GENERAL_FEEDBACK -> sendGeneralFeedbackPixel(metadata)
                REQUEST_FEATURE -> sendFeatureRequestPixel(metadata)
                REPORT_PROBLEM -> sendReportIssuePixel(metadata)
                null -> {} // Do nothing
            }

            command.send(FeedbackCompleted)
        }
    }

    private suspend fun sendReportIssuePixel(metadata: FeedbackMetadata) {
        pixelSender.sendPproReportIssue(
            mapOf(
                PARAMS_KEY_SOURCE to metadata.source!!.asParams(),
                PARAMS_KEY_CATEGORY to metadata.category!!.asParams(),
                PARAMS_KEY_SUBCATEGORY to metadata.subCategory!!.asParams(),
                PARAMS_KEY_DESC to (metadata.description ?: ""),
                PARAMS_KEY_APP_NAME to (metadata.appName ?: ""),
                PARAMS_KEY_APP_PACKAGE to (metadata.appPackageName ?: ""),
                PARAMS_KEY_CUSTOM_METADATA to feedbackCustomMetadataProvider.getCustomMetadata(metadata.category),
            ),
        )
    }

    private fun sendFeatureRequestPixel(metadata: FeedbackMetadata) {
        pixelSender.sendPproFeatureRequest(
            mapOf(
                PARAMS_KEY_SOURCE to metadata.source!!.asParams(),
                PARAMS_KEY_DESC to (metadata.description ?: ""),
            ),
        )
    }

    private fun sendGeneralFeedbackPixel(metadata: FeedbackMetadata) {
        pixelSender.sendPproGeneralFeedback(
            mapOf(
                PARAMS_KEY_SOURCE to metadata.source!!.asParams(),
                PARAMS_KEY_DESC to (metadata.description ?: ""),
            ),
        )
    }

    fun onFaqOpenedFromSubmit() {
        viewModelScope.launch {
            val metadata = viewState.value.feedbackMetadata
            pixelSender.reportPproFeedbackSubmitScreenFaqClicked(
                mapOf(
                    PARAMS_KEY_SOURCE to metadata.source!!.asParams(),
                    PARAMS_KEY_REPORT_TYPE to metadata.reportType!!.asParams(),
                    PARAMS_KEY_CATEGORY to metadata.category!!.asParams(),
                    PARAMS_KEY_SUBCATEGORY to metadata.subCategory!!.asParams(),
                ),
            )
        }
    }

    fun allowUserToChooseFeedbackType() {
        viewModelScope.launch {
            val newFragmentState = FeedbackGeneral
            val feedbackMetadata = FeedbackMetadata(source = DDG_SETTINGS)
            viewState.emit(
                ViewState(
                    feedbackMetadata = feedbackMetadata,
                    currentFragmentState = newFragmentState,
                ),
            )
            emitImpressionPixels(newFragmentState, feedbackMetadata)
        }
    }

    fun allowUserToChooseReportType(source: PrivacyProFeedbackSource) {
        viewModelScope.launch {
            val metadata = FeedbackMetadata(source = source)
            val newFragmentState = FeedbackAction
            viewState.emit(
                ViewState(
                    feedbackMetadata = metadata,
                    currentFragmentState = newFragmentState,
                ),
            )

            emitImpressionPixels(newFragmentState, metadata)
        }
    }

    fun allowUserToReportAppIssue(
        appName: String,
        appPackageName: String,
    ) {
        viewModelScope.launch {
            val metadata = FeedbackMetadata(
                source = VPN_EXCLUDED_APPS,
                reportType = REPORT_PROBLEM,
                category = VPN,
                subCategory = ISSUES_WITH_APPS_OR_WEBSITES,
                appName = appName,
                appPackageName = appPackageName,
            )
            val newFragmentState = FeedbackSubmit(ISSUES_WITH_APPS_OR_WEBSITES.asTitle())
            viewState.emit(
                ViewState(
                    feedbackMetadata = metadata,
                    currentFragmentState = newFragmentState,
                ),
            )

            emitImpressionPixels(newFragmentState, metadata)
        }
    }

    fun shouldGoBackInFeedbackFlow(): Boolean = viewState.value.previousFragmentState != null

    fun handleBackInFlow() {
        viewModelScope.launch {
            val newState = viewState.value.previousFragmentState
            val currentFeedbackMetadata = viewState.value.feedbackMetadata

            if (newState != null) {
                when (newState) {
                    is FeedbackGeneral -> ViewState(
                        feedbackMetadata = currentFeedbackMetadata.copy(
                            reportType = null,
                        ),
                        currentFragmentState = newState,
                        previousFragmentState = null,
                        isForward = false,
                    )

                    is FeedbackAction -> ViewState(
                        feedbackMetadata = currentFeedbackMetadata.copy(
                            reportType = null,
                        ),
                        currentFragmentState = newState,
                        previousFragmentState = null,
                        isForward = false,
                    )

                    is FeedbackCategory -> ViewState(
                        feedbackMetadata = currentFeedbackMetadata.copy(
                            category = null,
                        ),
                        currentFragmentState = newState,
                        previousFragmentState = FeedbackAction,
                        isForward = false,
                    )

                    is FeedbackSubCategory -> {
                        val previousState =
                            if (currentFeedbackMetadata.reportType == REPORT_PROBLEM &&
                                (
                                    currentFeedbackMetadata.source == SUBSCRIPTION_SETTINGS ||
                                        currentFeedbackMetadata.source == VPN_MANAGEMENT ||
                                        currentFeedbackMetadata.source == VPN_EXCLUDED_APPS
                                    )
                            ) {
                                FeedbackAction
                            } else {
                                FeedbackCategory(currentFeedbackMetadata.reportType?.asTitle() ?: -1)
                            }
                        ViewState(
                            feedbackMetadata = currentFeedbackMetadata.copy(
                                subCategory = null,
                            ),
                            currentFragmentState = newState,
                            previousFragmentState = previousState,
                            isForward = false,
                        )
                    }

                    is FeedbackSubmit -> null // Not possible to go back to this page via back press
                }?.also {
                    viewState.emit(it)
                }
            }
        }
    }

    private fun SubscriptionFeedbackCategory.asTitle(): Int {
        return when (this) {
            SUBS_AND_PAYMENTS -> R.string.feedbackCategorySubscription
            VPN -> R.string.feedbackCategoryVpn
            PIR -> R.string.feedbackCategoryPir
            ITR -> R.string.feedbackCategoryItr
        }
    }

    private fun SubscriptionFeedbackReportType.asTitle(): Int {
        return when (this) {
            REPORT_PROBLEM -> R.string.feedbackActionReportIssue
            REQUEST_FEATURE -> R.string.feedbackActionFeatureRequest
            GENERAL_FEEDBACK -> R.string.feedbackActionGeneralFeedback
        }
    }

    private fun SubscriptionFeedbackSubCategory.asTitle(): Int {
        return when (this) {
            is SubscriptionFeedbackVpnSubCategory -> {
                when (this) {
                    FAILS_TO_CONNECT -> R.string.feedbackSubCategoryVpnConnection
                    SLOW_CONNECTION -> R.string.feedbackSubCategoryVpnSlow
                    ISSUES_WITH_APPS_OR_WEBSITES -> R.string.feedbackSubCategoryVpnOtherApps
                    CANNOT_CONNECT_TO_LOCAL_DEVICE -> R.string.feedbackSubCategoryVpnIot
                    BROWSER_CRASH_FREEZE -> R.string.feedbackSubCategoryVpnCrash
                    SubscriptionFeedbackVpnSubCategory.OTHER -> R.string.feedbackSubCategoryVpnOther
                }
            }

            is SubscriptionFeedbackSubsSubCategory -> {
                when (this) {
                    ONE_TIME_PASSWORD -> R.string.feedbackSubCategorySubsOtp
                    OTHER -> R.string.feedbackSubCategorySubsOther
                }
            }

            is SubscriptionFeedbackPirSubCategory -> {
                when (this) {
                    SubscriptionFeedbackPirSubCategory.INFO_NOT_ON_SPECIFIC_SITE -> R.string.feedbackSubCategoryPirNothingOnSpecificSite
                    SubscriptionFeedbackPirSubCategory.RECORDS_NOT_ON_USER -> R.string.feedbackSubCategoryPirNotMe
                    SubscriptionFeedbackPirSubCategory.SCAN_STUCK -> R.string.feedbackSubCategoryPirScanStuck
                    SubscriptionFeedbackPirSubCategory.REMOVAL_STUCK -> R.string.feedbackSubCategoryPirRemovalStuck
                    SubscriptionFeedbackPirSubCategory.OTHER -> R.string.feedbackSubCategoryPirOther
                }
            }

            is SubscriptionFeedbackItrSubCategory -> {
                when (this) {
                    SubscriptionFeedbackItrSubCategory.ACCESS_CODE_ISSUE -> R.string.feedbackSubCategoryItrAccessCode
                    SubscriptionFeedbackItrSubCategory.CANT_CONTACT_ADVISOR -> R.string.feedbackSubCategoryItrCantContactAdvisor
                    SubscriptionFeedbackItrSubCategory.UNHELPFUL -> R.string.feedbackSubCategoryItrAdvisorUnhelpful
                    SubscriptionFeedbackItrSubCategory.OTHER -> R.string.feedbackSubCategoryItrOther
                }
            }

            else -> {
                -1
            }
        }
    }

    private fun emitImpressionPixels(
        state: FeedbackFragmentState,
        metadata: FeedbackMetadata,
    ) {
        when (state) {
            is FeedbackGeneral -> pixelSender.reportPproFeedbackGeneralScreenShown()
            is FeedbackAction -> pixelSender.reportPproFeedbackActionsScreenShown(
                mapOf(PARAMS_KEY_SOURCE to metadata.source!!.asParams()),
            )

            is FeedbackCategory -> pixelSender.reportPproFeedbackCategoryScreenShown(
                mapOf(
                    PARAMS_KEY_SOURCE to metadata.source!!.asParams(),
                    PARAMS_KEY_REPORT_TYPE to metadata.reportType!!.asParams(),
                ),
            )

            is FeedbackSubCategory -> pixelSender.reportPproFeedbackSubcategoryScreenShown(
                mapOf(
                    PARAMS_KEY_SOURCE to metadata.source!!.asParams(),
                    PARAMS_KEY_REPORT_TYPE to metadata.reportType!!.asParams(),
                    PARAMS_KEY_CATEGORY to metadata.category!!.asParams(),
                ),
            )

            is FeedbackSubmit -> pixelSender.reportPproFeedbackSubmitScreenShown(
                mapOf(
                    PARAMS_KEY_SOURCE to metadata.source!!.asParams(),
                    PARAMS_KEY_REPORT_TYPE to metadata.reportType!!.asParams(),
                    PARAMS_KEY_CATEGORY to (metadata.category?.asParams() ?: ""),
                    PARAMS_KEY_SUBCATEGORY to (metadata.subCategory?.asParams() ?: ""),
                ),
            )
        }
    }

    sealed class Command {
        data object FeedbackCompleted : Command()
        data object HideKeyboard : Command()
    }

    internal data class ViewState(
        val feedbackMetadata: FeedbackMetadata = FeedbackMetadata(),
        val currentFragmentState: FeedbackFragmentState? = null,
        val previousFragmentState: FeedbackFragmentState? = null,
        val isForward: Boolean = true,
    )

    internal data class FeedbackMetadata(
        val source: PrivacyProFeedbackSource? = null,
        val reportType: SubscriptionFeedbackReportType? = null,
        val category: SubscriptionFeedbackCategory? = null,
        val subCategory: SubscriptionFeedbackSubCategory? = null,
        val appName: String? = null,
        val appPackageName: String? = null,
        val description: String? = null,
    )

    sealed class FeedbackFragmentState(@StringRes open val title: Int) {
        data object FeedbackGeneral : FeedbackFragmentState(R.string.feedbackTitle)
        data object FeedbackAction : FeedbackFragmentState(R.string.feedbackTitle)
        data class FeedbackCategory(@StringRes override val title: Int) :
            FeedbackFragmentState(title)

        data class FeedbackSubCategory(@StringRes override val title: Int) :
            FeedbackFragmentState(title)

        data class FeedbackSubmit(@StringRes override val title: Int) : FeedbackFragmentState(title)
    }

    companion object {
        private const val PARAMS_KEY_SOURCE = "source"
        private const val PARAMS_KEY_REPORT_TYPE = "reportType"
        private const val PARAMS_KEY_CATEGORY = "category"
        private const val PARAMS_KEY_SUBCATEGORY = "subcategory"
        private const val PARAMS_KEY_DESC = "description"
        private const val PARAMS_KEY_CUSTOM_METADATA = "customMetadata"
        private const val PARAMS_KEY_APP_NAME = "appName"
        private const val PARAMS_KEY_APP_PACKAGE = "appPackage"
    }
}
