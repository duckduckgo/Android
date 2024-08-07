package com.duckduckgo.subscriptions.impl.feedback

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
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
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.FeedbackFragmentState.FeedbackAction
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.FeedbackFragmentState.FeedbackCategory
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.FeedbackFragmentState.FeedbackGeneral
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.FeedbackFragmentState.FeedbackSubCategory
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.FeedbackFragmentState.FeedbackSubmit
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.FeedbackMetadata
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackVpnSubCategory.BROWSER_CRASH_FREEZE
import com.duckduckgo.subscriptions.impl.feedback.pixels.PrivacyProUnifiedFeedbackPixelSender
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionFeedbackViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var pixelSender: PrivacyProUnifiedFeedbackPixelSender

    @Mock
    private lateinit var customMetadataProvider: FeedbackCustomMetadataProvider
    private lateinit var viewModel: SubscriptionFeedbackViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        viewModel = SubscriptionFeedbackViewModel(
            pixelSender,
            customMetadataProvider,
        )
    }

    @Test
    fun whenAllowUserToChooseFeedbackTypeThenShowGeneralScreenAndEmitImpression() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseFeedbackType()

            expectMostRecentItem().assertViewStateMoveForward(
                expectedPreviousFragmentState = null,
                expectedCurrentFragmentState = FeedbackGeneral,
                FeedbackMetadata(
                    source = DDG_SETTINGS,
                ),
            )

            cancelAndConsumeRemainingEvents()
            verify(pixelSender).reportPproFeedbackGeneralScreenShown()
        }
    }

    @Test
    fun whenFeedbackIsOpenedFromPproThenShowActionScreenAndEmitImpression() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = SUBSCRIPTION_SETTINGS)

            expectMostRecentItem().assertViewStateMoveForward(
                expectedPreviousFragmentState = null,
                expectedCurrentFragmentState = FeedbackAction,
                FeedbackMetadata(
                    source = SUBSCRIPTION_SETTINGS,
                ),
            )

            cancelAndConsumeRemainingEvents()
            verify(pixelSender).reportPproFeedbackActionsScreenShown(
                mapOf(
                    "source" to "ppro",
                ),
            )
        }
    }

    @Test
    fun whenFeedbackIsOpenedFromVPNThenShowActionScreenAndEmitImpression() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = VPN_MANAGEMENT)

            expectMostRecentItem().assertViewStateMoveForward(
                expectedPreviousFragmentState = null,
                expectedCurrentFragmentState = FeedbackAction,
                FeedbackMetadata(
                    source = VPN_MANAGEMENT,
                ),
            )

            cancelAndConsumeRemainingEvents()
            verify(pixelSender).reportPproFeedbackActionsScreenShown(
                mapOf(
                    "source" to "vpn",
                ),
            )
        }
    }

    @Test
    fun whenFeedbackIsOpenedFromVPNExclusionListThenShowActionScreenAndEmitImpression() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = VPN_EXCLUDED_APPS)

            expectMostRecentItem().assertViewStateMoveForward(
                expectedPreviousFragmentState = null,
                expectedCurrentFragmentState = FeedbackAction,
                FeedbackMetadata(
                    source = VPN_EXCLUDED_APPS,
                ),
            )

            cancelAndConsumeRemainingEvents()
            verify(pixelSender).reportPproFeedbackActionsScreenShown(
                mapOf(
                    "source" to "vpnExcludedApps",
                ),
            )
        }
    }

    @Test
    fun whenFeedbackIsForSpecificVPNExcludedAppThenShowSubmitScreenAndEmitImpression() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToReportAppIssue(appName = "test", appPackageName = "com.test")

            expectMostRecentItem().assertViewStateMoveForward(
                expectedPreviousFragmentState = null,
                expectedCurrentFragmentState = FeedbackSubmit(
                    R.string.feedbackSubCategoryVpnOtherApps,
                ),
                FeedbackMetadata(
                    source = VPN_EXCLUDED_APPS,
                    reportType = REPORT_PROBLEM,
                    category = VPN,
                    subCategory = SubscriptionFeedbackVpnSubCategory.ISSUES_WITH_APPS_OR_WEBSITES,
                    appName = "test",
                    appPackageName = "com.test",
                ),
            )

            cancelAndConsumeRemainingEvents()
            verify(pixelSender).reportPproFeedbackSubmitScreenShown(
                mapOf(
                    "source" to "vpnExcludedApps",
                    "reportType" to "reportIssue",
                    "category" to "vpn",
                    "subcategory" to "issueWithAppOrWebsite",
                ),
            )
        }
    }

    @Test
    fun whenRequestFeatureIsSelectedFromActionScreenThenShowSubmitScreenAndEmitImpression() =
        runTest {
            viewModel.viewState().test {
                viewModel.allowUserToChooseReportType(source = SUBSCRIPTION_SETTINGS)
                viewModel.onReportTypeSelected(reportType = REQUEST_FEATURE)

                expectMostRecentItem().assertViewStateMoveForward(
                    expectedPreviousFragmentState = FeedbackAction,
                    expectedCurrentFragmentState = FeedbackSubmit(R.string.feedbackActionFeatureRequest),
                    FeedbackMetadata(
                        source = SUBSCRIPTION_SETTINGS,
                        reportType = REQUEST_FEATURE,
                    ),
                )

                cancelAndConsumeRemainingEvents()
                verify(pixelSender).reportPproFeedbackSubmitScreenShown(
                    mapOf(
                        "source" to "ppro",
                        "reportType" to "requestFeature",
                        "category" to "",
                        "subcategory" to "",
                    ),
                )
            }
        }

    @Test
    fun whenGeneralFeedbackIsSelectedFromActionScreenThenShowSubmitScreenAndEmitImpression() =
        runTest {
            viewModel.viewState().test {
                viewModel.allowUserToChooseReportType(source = VPN_MANAGEMENT)
                viewModel.onReportTypeSelected(reportType = GENERAL_FEEDBACK)

                expectMostRecentItem().assertViewStateMoveForward(
                    expectedPreviousFragmentState = FeedbackAction,
                    expectedCurrentFragmentState = FeedbackSubmit(R.string.feedbackActionGeneralFeedback),
                    FeedbackMetadata(
                        source = VPN_MANAGEMENT,
                        reportType = GENERAL_FEEDBACK,
                    ),
                )

                cancelAndConsumeRemainingEvents()
                verify(pixelSender).reportPproFeedbackSubmitScreenShown(
                    mapOf(
                        "source" to "vpn",
                        "reportType" to "general",
                        "category" to "",
                        "subcategory" to "",
                    ),
                )
            }
        }

    @Test
    fun whenReportProblemIsSelectedFromActionScreenViaAppSettingsThenShowCategoryScreenAndEmitImpression() =
        runTest {
            viewModel.viewState().test {
                viewModel.allowUserToChooseReportType(source = DDG_SETTINGS)
                viewModel.onReportTypeSelected(reportType = REPORT_PROBLEM)

                expectMostRecentItem().assertViewStateMoveForward(
                    expectedPreviousFragmentState = FeedbackAction,
                    expectedCurrentFragmentState = FeedbackCategory(R.string.feedbackActionReportIssue),
                    FeedbackMetadata(
                        source = DDG_SETTINGS,
                        reportType = REPORT_PROBLEM,
                    ),
                )

                cancelAndConsumeRemainingEvents()
                verify(pixelSender).reportPproFeedbackCategoryScreenShown(
                    mapOf(
                        "source" to "settings",
                        "reportType" to "reportIssue",
                    ),
                )
            }
        }

    @Test
    fun whenReportProblemIsSelectedViaPproThenShowSubsSubCategoryScreenAndEmitImpression() =
        runTest {
            viewModel.viewState().test {
                viewModel.allowUserToChooseReportType(source = SUBSCRIPTION_SETTINGS)
                viewModel.onReportTypeSelected(reportType = REPORT_PROBLEM)

                expectMostRecentItem().assertViewStateMoveForward(
                    expectedPreviousFragmentState = FeedbackAction,
                    expectedCurrentFragmentState = FeedbackSubCategory(R.string.feedbackCategorySubscription),
                    FeedbackMetadata(
                        source = SUBSCRIPTION_SETTINGS,
                        reportType = REPORT_PROBLEM,
                        category = SUBS_AND_PAYMENTS, // Automatically set category
                    ),
                )

                cancelAndConsumeRemainingEvents()
                verify(pixelSender).reportPproFeedbackSubcategoryScreenShown(
                    mapOf(
                        "source" to "ppro",
                        "reportType" to "reportIssue",
                        "category" to "subscription",
                    ),
                )
            }
        }

    @Test
    fun whenReportProblemIsSelectedViaVPNThenShowVpnSubCategoryScreenAndEmitImpression() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = VPN_MANAGEMENT)
            viewModel.onReportTypeSelected(reportType = REPORT_PROBLEM)

            expectMostRecentItem().assertViewStateMoveForward(
                expectedPreviousFragmentState = FeedbackAction,
                expectedCurrentFragmentState = FeedbackSubCategory(R.string.feedbackCategoryVpn),
                FeedbackMetadata(
                    source = VPN_MANAGEMENT,
                    reportType = REPORT_PROBLEM,
                    category = VPN, // Automatically set category
                ),
            )

            cancelAndConsumeRemainingEvents()
            verify(pixelSender).reportPproFeedbackSubcategoryScreenShown(
                mapOf(
                    "source" to "vpn",
                    "reportType" to "reportIssue",
                    "category" to "vpn",
                ),
            )
        }
    }

    @Test
    fun whenReportProblemIsSelectedViaVPNExclusionThenShowVPNSubCategoryScreenAndEmitImpression() =
        runTest {
            viewModel.viewState().test {
                viewModel.allowUserToChooseReportType(source = VPN_EXCLUDED_APPS)
                viewModel.onReportTypeSelected(reportType = REPORT_PROBLEM)

                expectMostRecentItem().assertViewStateMoveForward(
                    expectedPreviousFragmentState = FeedbackAction,
                    expectedCurrentFragmentState = FeedbackSubCategory(R.string.feedbackCategoryVpn),
                    FeedbackMetadata(
                        source = VPN_EXCLUDED_APPS,
                        reportType = REPORT_PROBLEM,
                        category = VPN, // Automatically set category
                    ),
                )

                cancelAndConsumeRemainingEvents()
                verify(pixelSender).reportPproFeedbackSubcategoryScreenShown(
                    mapOf(
                        "source" to "vpnExcludedApps",
                        "reportType" to "reportIssue",
                        "category" to "vpn",
                    ),
                )
            }
        }

    @Test
    fun whenCategoriesArePassedAsParamThenStringValuesShouldAlignSpec() = runTest {
        assertEquals("subscription", SUBS_AND_PAYMENTS.asParams())
        assertEquals("vpn", VPN.asParams())
        assertEquals("pir", PIR.asParams())
        assertEquals("itr", ITR.asParams())
    }

    @Test
    fun whenSubCategoriesArePassedAsParamThenStringValuesShouldAlignSpec() = runTest {
        assertEquals("otp", SubscriptionFeedbackSubsSubCategory.ONE_TIME_PASSWORD.asParams())
        assertEquals("somethingElse", SubscriptionFeedbackSubsSubCategory.OTHER.asParams())

        assertEquals(
            "failsToConnect",
            SubscriptionFeedbackVpnSubCategory.FAILS_TO_CONNECT.asParams(),
        )
        assertEquals("tooSlow", SubscriptionFeedbackVpnSubCategory.SLOW_CONNECTION.asParams())
        assertEquals(
            "issueWithAppOrWebsite",
            SubscriptionFeedbackVpnSubCategory.ISSUES_WITH_APPS_OR_WEBSITES.asParams(),
        )
        assertEquals(
            "cantConnectToLocalDevice",
            SubscriptionFeedbackVpnSubCategory.CANNOT_CONNECT_TO_LOCAL_DEVICE.asParams(),
        )
        assertEquals(
            "appCrashesOrFreezes",
            SubscriptionFeedbackVpnSubCategory.BROWSER_CRASH_FREEZE.asParams(),
        )
        assertEquals("somethingElse", SubscriptionFeedbackVpnSubCategory.OTHER.asParams())

        assertEquals(
            "nothingOnSpecificSite",
            SubscriptionFeedbackPirSubCategory.INFO_NOT_ON_SPECIFIC_SITE.asParams(),
        )
        assertEquals("notMe", SubscriptionFeedbackPirSubCategory.RECORDS_NOT_ON_USER.asParams())
        assertEquals("scanStuck", SubscriptionFeedbackPirSubCategory.SCAN_STUCK.asParams())
        assertEquals("removalStuck", SubscriptionFeedbackPirSubCategory.REMOVAL_STUCK.asParams())
        assertEquals("somethingElse", SubscriptionFeedbackPirSubCategory.OTHER.asParams())

        assertEquals("accessCode", SubscriptionFeedbackItrSubCategory.ACCESS_CODE_ISSUE.asParams())
        assertEquals(
            "cantContactAdvisor",
            SubscriptionFeedbackItrSubCategory.CANT_CONTACT_ADVISOR.asParams(),
        )
        assertEquals("advisorUnhelpful", SubscriptionFeedbackItrSubCategory.UNHELPFUL.asParams())
        assertEquals("somethingElse", SubscriptionFeedbackItrSubCategory.OTHER.asParams())
    }

    @Test
    fun whenVpnSubcategorySelectedThenShowSubmitScreenAndEmitImpression() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = VPN_MANAGEMENT)
            viewModel.onReportTypeSelected(reportType = REPORT_PROBLEM)
            viewModel.onSubcategorySelected(BROWSER_CRASH_FREEZE)

            expectMostRecentItem().assertViewStateMoveForward(
                expectedPreviousFragmentState = FeedbackSubCategory(R.string.feedbackCategoryVpn),
                expectedCurrentFragmentState = FeedbackSubmit(R.string.feedbackSubCategoryVpnCrash),
                FeedbackMetadata(
                    source = VPN_MANAGEMENT,
                    reportType = REPORT_PROBLEM,
                    category = VPN,
                    subCategory = BROWSER_CRASH_FREEZE,
                ),
            )

            cancelAndConsumeRemainingEvents()
            verify(pixelSender).reportPproFeedbackSubmitScreenShown(
                mapOf(
                    "source" to "vpn",
                    "reportType" to "reportIssue",
                    "category" to "vpn",
                    "subcategory" to "appCrashesOrFreezes",
                ),
            )
        }
    }

    @Test
    fun whenSubsSubcategorySelectedThenShowSubmitScreenAndEmitImpression() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = SUBSCRIPTION_SETTINGS)
            viewModel.onReportTypeSelected(reportType = REPORT_PROBLEM)
            viewModel.onSubcategorySelected(SubscriptionFeedbackSubsSubCategory.ONE_TIME_PASSWORD)

            expectMostRecentItem().assertViewStateMoveForward(
                expectedPreviousFragmentState = FeedbackSubCategory(R.string.feedbackCategorySubscription),
                expectedCurrentFragmentState = FeedbackSubmit(R.string.feedbackSubCategorySubsOtp),
                FeedbackMetadata(
                    source = SUBSCRIPTION_SETTINGS,
                    reportType = REPORT_PROBLEM,
                    category = SUBS_AND_PAYMENTS,
                    subCategory = SubscriptionFeedbackSubsSubCategory.ONE_TIME_PASSWORD,
                ),
            )

            cancelAndConsumeRemainingEvents()
            verify(pixelSender).reportPproFeedbackSubmitScreenShown(
                mapOf(
                    "source" to "ppro",
                    "reportType" to "reportIssue",
                    "category" to "subscription",
                    "subcategory" to "otp",
                ),
            )
        }
    }

    @Test
    fun whenPproIsSelectedFromGeneralScreenThenShowActionScreenAndEmitImpression() = runTest {
        viewModel.viewState().test {
            viewModel.onProFeedbackSelected()

            expectMostRecentItem().assertViewStateMoveForward(
                expectedPreviousFragmentState = null,
                expectedCurrentFragmentState = FeedbackAction,
                FeedbackMetadata(
                    source = DDG_SETTINGS,
                ),
            )

            cancelAndConsumeRemainingEvents()
            verify(pixelSender).reportPproFeedbackActionsScreenShown(
                mapOf(
                    "source" to "settings",
                ),
            )
        }
    }

    @Test
    fun whenCategorySelectedIsPIRThenShowPIRSubcategoryScreenAndImpression() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = DDG_SETTINGS)
            viewModel.onReportTypeSelected(reportType = REPORT_PROBLEM)
            viewModel.onCategorySelected(category = PIR)

            expectMostRecentItem().assertViewStateMoveForward(
                expectedPreviousFragmentState = FeedbackCategory(R.string.feedbackActionReportIssue),
                expectedCurrentFragmentState = FeedbackSubCategory(R.string.feedbackCategoryPir),
                FeedbackMetadata(
                    source = DDG_SETTINGS,
                    reportType = REPORT_PROBLEM,
                    category = PIR,
                ),
            )

            cancelAndConsumeRemainingEvents()
            verify(pixelSender).reportPproFeedbackSubcategoryScreenShown(
                mapOf(
                    "source" to "settings",
                    "reportType" to "reportIssue",
                    "category" to "pir",
                ),
            )
        }
    }

    @Test
    fun whenCategorySelectedIsITRThenShowSubcategoryScreenAndImpression() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = DDG_SETTINGS)
            viewModel.onReportTypeSelected(reportType = REPORT_PROBLEM)
            viewModel.onCategorySelected(category = ITR)

            expectMostRecentItem().assertViewStateMoveForward(
                expectedPreviousFragmentState = FeedbackCategory(R.string.feedbackActionReportIssue),
                expectedCurrentFragmentState = FeedbackSubCategory(R.string.feedbackCategoryItr),
                FeedbackMetadata(
                    source = DDG_SETTINGS,
                    reportType = REPORT_PROBLEM,
                    category = ITR,
                ),
            )

            cancelAndConsumeRemainingEvents()
            verify(pixelSender).reportPproFeedbackSubcategoryScreenShown(
                mapOf(
                    "source" to "settings",
                    "reportType" to "reportIssue",
                    "category" to "itr",
                ),
            )
        }
    }

    @Test
    fun whenFaqLinkOpenedReportIssueThenEmitClickPixels() = runTest {
        viewModel.allowUserToReportAppIssue("test", "com.test")
        viewModel.onFaqOpenedFromSubmit()

        verify(pixelSender).reportPproFeedbackSubmitScreenFaqClicked(
            mapOf(
                "source" to "vpnExcludedApps",
                "reportType" to "reportIssue",
                "category" to "vpn",
                "subcategory" to "issueWithAppOrWebsite",
            ),
        )
    }

    @Test
    fun whenMoveBackFromActionScreenComingFromGeneralThenUpdateViewState() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseFeedbackType() // Show General
            viewModel.onProFeedbackSelected() // Show Action

            viewModel.handleBackInFlow()

            expectMostRecentItem().assertViewStateMoveBack(
                expectedPreviousFragmentState = null,
                expectedCurrentFragmentState = FeedbackGeneral, // Back to general
                FeedbackMetadata(
                    source = DDG_SETTINGS,
                ),
            )
        }
    }

    @Test
    fun whenMoveBackFromSubmitViaRequestFeatureActionThenUpdateViewState() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = SUBSCRIPTION_SETTINGS) // Show action
            viewModel.onReportTypeSelected(REQUEST_FEATURE) // Show submit

            viewModel.handleBackInFlow()

            expectMostRecentItem().assertViewStateMoveBack(
                expectedPreviousFragmentState = null,
                expectedCurrentFragmentState = FeedbackAction, // Back to action
                FeedbackMetadata(
                    source = SUBSCRIPTION_SETTINGS,
                ),
            )
        }
    }

    @Test
    fun whenMoveBackFromSubmitViaGeneralFeedbackActionThenUpdateViewState() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = SUBSCRIPTION_SETTINGS) // Show action
            viewModel.onReportTypeSelected(GENERAL_FEEDBACK) // Show submit

            viewModel.handleBackInFlow()

            expectMostRecentItem().assertViewStateMoveBack(
                expectedPreviousFragmentState = null,
                expectedCurrentFragmentState = FeedbackAction, // Back to action
                FeedbackMetadata(
                    source = SUBSCRIPTION_SETTINGS,
                ),
            )
        }
    }

    @Test
    fun whenMoveBackFromSubCategoryActionViaPproThenUpdateViewState() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = SUBSCRIPTION_SETTINGS) // Show action
            viewModel.onReportTypeSelected(REPORT_PROBLEM) // Show subcategory

            viewModel.handleBackInFlow()

            expectMostRecentItem().assertViewStateMoveBack(
                expectedPreviousFragmentState = null,
                expectedCurrentFragmentState = FeedbackAction, // Back to action
                FeedbackMetadata(
                    source = SUBSCRIPTION_SETTINGS,
                    category = SUBS_AND_PAYMENTS, // Retain category
                ),
            )
        }
    }

    @Test
    fun whenMoveBackFromSubCategoryActionViaVPNThenUpdateViewState() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = VPN_MANAGEMENT) // Show action
            viewModel.onReportTypeSelected(REPORT_PROBLEM) // Show subcategory

            viewModel.handleBackInFlow()

            expectMostRecentItem().assertViewStateMoveBack(
                expectedPreviousFragmentState = null,
                expectedCurrentFragmentState = FeedbackAction, // Back to action
                FeedbackMetadata(
                    source = VPN_MANAGEMENT,
                    category = VPN, // Retain category
                ),
            )
        }
    }

    @Test
    fun whenMoveBackFromCategoryActionThenUpdateViewState() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseFeedbackType() // Show general
            viewModel.onProFeedbackSelected() // show action
            viewModel.onReportTypeSelected(REPORT_PROBLEM) // Show category

            viewModel.handleBackInFlow()

            expectMostRecentItem().assertViewStateMoveBack(
                expectedPreviousFragmentState = FeedbackGeneral,
                expectedCurrentFragmentState = FeedbackAction, // Back to action
                FeedbackMetadata(
                    source = DDG_SETTINGS,
                ),
            )
        }
    }

    @Test
    fun whenMoveBackFromSubCategoryActionViaSettingsAndVPNCategoryThenUpdateViewState() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseFeedbackType() // Show general
            viewModel.onProFeedbackSelected() // show action
            viewModel.onReportTypeSelected(REPORT_PROBLEM) // Show category
            viewModel.onCategorySelected(VPN) // Show subcategory

            viewModel.handleBackInFlow()

            expectMostRecentItem().assertViewStateMoveBack(
                expectedPreviousFragmentState = FeedbackAction,
                expectedCurrentFragmentState = FeedbackCategory(R.string.feedbackActionReportIssue), // Back to category
                FeedbackMetadata(
                    source = DDG_SETTINGS,
                    reportType = REPORT_PROBLEM,
                ),
            )
        }
    }

    @Test
    fun whenMoveBackFromSubCategoryActionViaSettingsAndPProCategoryThenUpdateViewState() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseFeedbackType() // Show general
            viewModel.onProFeedbackSelected() // show action
            viewModel.onReportTypeSelected(REPORT_PROBLEM) // Show category
            viewModel.onCategorySelected(SUBS_AND_PAYMENTS) // Show subcategory

            viewModel.handleBackInFlow()

            expectMostRecentItem().assertViewStateMoveBack(
                expectedPreviousFragmentState = FeedbackAction,
                expectedCurrentFragmentState = FeedbackCategory(R.string.feedbackActionReportIssue), // Back to category
                FeedbackMetadata(
                    source = DDG_SETTINGS,
                    reportType = REPORT_PROBLEM,
                ),
            )
        }
    }

    @Test
    fun whenMoveBackFromSubCategoryActionViaSettingsAndPIRCategoryThenUpdateViewState() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseFeedbackType() // Show general
            viewModel.onProFeedbackSelected() // show action
            viewModel.onReportTypeSelected(REPORT_PROBLEM) // Show category
            viewModel.onCategorySelected(PIR) // Show subcategory

            viewModel.handleBackInFlow()

            expectMostRecentItem().assertViewStateMoveBack(
                expectedPreviousFragmentState = FeedbackAction,
                expectedCurrentFragmentState = FeedbackCategory(R.string.feedbackActionReportIssue), // Back to category
                FeedbackMetadata(
                    source = DDG_SETTINGS,
                    reportType = REPORT_PROBLEM,
                ),
            )
        }
    }

    @Test
    fun whenMoveBackFromSubCategoryActionViaSettingsAndITRCategoryThenUpdateViewState() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseFeedbackType() // Show general
            viewModel.onProFeedbackSelected() // show action
            viewModel.onReportTypeSelected(REPORT_PROBLEM) // Show category
            viewModel.onCategorySelected(ITR) // Show subcategory

            viewModel.handleBackInFlow()

            expectMostRecentItem().assertViewStateMoveBack(
                expectedPreviousFragmentState = FeedbackAction,
                expectedCurrentFragmentState = FeedbackCategory(R.string.feedbackActionReportIssue), // Back to category
                FeedbackMetadata(
                    source = DDG_SETTINGS,
                    reportType = REPORT_PROBLEM,
                ),
            )
        }
    }

    @Test
    fun whenMoveBackFromSubmitActionViaSettingsAndPProCategoryThenUpdateViewState() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseFeedbackType() // Show general
            viewModel.onProFeedbackSelected() // show action
            viewModel.onReportTypeSelected(REPORT_PROBLEM) // Show category
            viewModel.onCategorySelected(SUBS_AND_PAYMENTS) // Show subcategory
            viewModel.onSubcategorySelected(SubscriptionFeedbackSubsSubCategory.ONE_TIME_PASSWORD) // Show submit

            viewModel.handleBackInFlow()

            expectMostRecentItem().assertViewStateMoveBack(
                expectedPreviousFragmentState = FeedbackCategory(R.string.feedbackActionReportIssue),
                expectedCurrentFragmentState = FeedbackSubCategory(R.string.feedbackCategorySubscription), // Back to subcategory
                FeedbackMetadata(
                    source = DDG_SETTINGS,
                    reportType = REPORT_PROBLEM,
                    category = SUBS_AND_PAYMENTS,
                ),
            )
        }
    }

    @Test
    fun whenMoveBackFromSubmitActionViaVPNThenUpdateViewState() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = VPN_MANAGEMENT) // Show action
            viewModel.onReportTypeSelected(REPORT_PROBLEM) // Show subcategory
            viewModel.onSubcategorySelected(BROWSER_CRASH_FREEZE) // Show submit

            viewModel.handleBackInFlow()

            expectMostRecentItem().assertViewStateMoveBack(
                expectedPreviousFragmentState = FeedbackAction,
                expectedCurrentFragmentState = FeedbackSubCategory(R.string.feedbackCategoryVpn),
                FeedbackMetadata(
                    source = VPN_MANAGEMENT,
                    reportType = REPORT_PROBLEM,
                    category = VPN, // Retain category
                ),
            )
        }
    }

    @Test
    fun whenMoveBackFromSubmitActionViaPproThenUpdateViewState() = runTest {
        viewModel.viewState().test {
            viewModel.allowUserToChooseReportType(source = SUBSCRIPTION_SETTINGS) // Show action
            viewModel.onReportTypeSelected(REPORT_PROBLEM) // Show subcategory
            viewModel.onSubcategorySelected(SubscriptionFeedbackSubsSubCategory.ONE_TIME_PASSWORD) // Show submit

            viewModel.handleBackInFlow()

            expectMostRecentItem().assertViewStateMoveBack(
                expectedPreviousFragmentState = FeedbackAction,
                expectedCurrentFragmentState = FeedbackSubCategory(R.string.feedbackCategorySubscription),
                FeedbackMetadata(
                    source = SUBSCRIPTION_SETTINGS,
                    reportType = REPORT_PROBLEM,
                    category = SUBS_AND_PAYMENTS, // Retain category
                ),
            )
        }
    }

    @Test
    fun whenFeatureRequestSubmittedTheSendFeatureRequestPixel() = runTest {
        viewModel.allowUserToChooseFeedbackType() // Show general
        viewModel.onProFeedbackSelected() // show action
        viewModel.onReportTypeSelected(REQUEST_FEATURE) // Show submit
        viewModel.onSubmitFeedback("Test")

        verify(pixelSender).sendPproFeatureRequest(
            mapOf(
                "source" to "settings",
                "description" to "Test",
            ),
        )
    }

    @Test
    fun whenGeneralFeedbackSubmittedTheSendGeneralFeedbackPixel() = runTest {
        viewModel.allowUserToChooseFeedbackType() // Show general
        viewModel.onProFeedbackSelected() // show action
        viewModel.onReportTypeSelected(GENERAL_FEEDBACK) // Show submit
        viewModel.onSubmitFeedback("Test")

        verify(pixelSender).sendPproGeneralFeedback(
            mapOf(
                "source" to "settings",
                "description" to "Test",
            ),
        )
    }

    @Test
    fun whenSubsIssueSubmittedTheSendReportIssuePixel() = runTest {
        whenever(customMetadataProvider.getCustomMetadata(SUBS_AND_PAYMENTS)).thenReturn("testMetadata")
        viewModel.allowUserToChooseReportType(SUBSCRIPTION_SETTINGS)
        viewModel.onReportTypeSelected(REPORT_PROBLEM)
        viewModel.onSubcategorySelected(SubscriptionFeedbackSubsSubCategory.OTHER)
        viewModel.onSubmitFeedback("Test")

        verify(pixelSender).sendPproReportIssue(
            mapOf(
                "source" to "ppro",
                "category" to "subscription",
                "subcategory" to "somethingElse",
                "description" to "Test",
                "customMetadata" to "testMetadata",
                "appName" to "",
                "appPackage" to "",
            ),
        )
    }

    @Test
    fun whenVpnIssueSubmittedTheSendReportIssuePixel() = runTest {
        whenever(customMetadataProvider.getCustomMetadata(VPN)).thenReturn("testMetadata")
        viewModel.allowUserToChooseReportType(VPN_MANAGEMENT)
        viewModel.onReportTypeSelected(REPORT_PROBLEM)
        viewModel.onSubcategorySelected(SubscriptionFeedbackVpnSubCategory.CANNOT_CONNECT_TO_LOCAL_DEVICE)
        viewModel.onSubmitFeedback("Test")

        verify(pixelSender).sendPproReportIssue(
            mapOf(
                "source" to "vpn",
                "category" to "vpn",
                "subcategory" to "cantConnectToLocalDevice",
                "description" to "Test",
                "customMetadata" to "testMetadata",
                "appName" to "",
                "appPackage" to "",
            ),
        )
    }

    @Test
    fun whenAppSpecificVpnIssueSubmittedTheSendReportIssuePixel() = runTest {
        whenever(customMetadataProvider.getCustomMetadata(VPN)).thenReturn("testMetadata")
        viewModel.allowUserToReportAppIssue("test", "com.test")
        viewModel.onSubmitFeedback("Test")

        verify(pixelSender).sendPproReportIssue(
            mapOf(
                "source" to "vpnExcludedApps",
                "category" to "vpn",
                "subcategory" to "issueWithAppOrWebsite",
                "description" to "Test",
                "customMetadata" to "testMetadata",
                "appName" to "test",
                "appPackage" to "com.test",
            ),
        )
    }

    @Test
    fun whenPIRIssueSubmittedTheSendReportIssuePixel() = runTest {
        whenever(customMetadataProvider.getCustomMetadata(PIR)).thenReturn("")
        viewModel.allowUserToChooseReportType(DDG_SETTINGS)
        viewModel.onReportTypeSelected(REPORT_PROBLEM)
        viewModel.onCategorySelected(PIR)
        viewModel.onSubcategorySelected(SubscriptionFeedbackPirSubCategory.REMOVAL_STUCK)
        viewModel.onSubmitFeedback("Test")

        verify(pixelSender).sendPproReportIssue(
            mapOf(
                "source" to "settings",
                "category" to "pir",
                "subcategory" to "removalStuck",
                "description" to "Test",
                "customMetadata" to "",
                "appName" to "",
                "appPackage" to "",
            ),
        )
    }

    @Test
    fun whenITRIssueSubmittedTheSendReportIssuePixel() = runTest {
        whenever(customMetadataProvider.getCustomMetadata(ITR)).thenReturn("")
        viewModel.allowUserToChooseReportType(DDG_SETTINGS)
        viewModel.onReportTypeSelected(REPORT_PROBLEM)
        viewModel.onCategorySelected(ITR)
        viewModel.onSubcategorySelected(SubscriptionFeedbackItrSubCategory.UNHELPFUL)
        viewModel.onSubmitFeedback("Test")

        verify(pixelSender).sendPproReportIssue(
            mapOf(
                "source" to "settings",
                "category" to "itr",
                "subcategory" to "advisorUnhelpful",
                "description" to "Test",
                "customMetadata" to "",
                "appName" to "",
                "appPackage" to "",
            ),
        )
    }

    private fun SubscriptionFeedbackViewModel.ViewState.assertViewStateMoveForward(
        expectedPreviousFragmentState: SubscriptionFeedbackViewModel.FeedbackFragmentState?,
        expectedCurrentFragmentState: SubscriptionFeedbackViewModel.FeedbackFragmentState,
        expectedFeedbackMetadata: FeedbackMetadata,
    ) {
        assertTrue(isForward)
        assertEquals(expectedPreviousFragmentState, previousFragmentState)
        assertEquals(expectedCurrentFragmentState, currentFragmentState)
        assertEquals(
            expectedFeedbackMetadata,
            feedbackMetadata,
        )
    }

    private fun SubscriptionFeedbackViewModel.ViewState.assertViewStateMoveBack(
        expectedPreviousFragmentState: SubscriptionFeedbackViewModel.FeedbackFragmentState?,
        expectedCurrentFragmentState: SubscriptionFeedbackViewModel.FeedbackFragmentState,
        expectedFeedbackMetadata: FeedbackMetadata,
    ) {
        assertFalse(isForward)
        assertEquals(expectedPreviousFragmentState, previousFragmentState)
        assertEquals(expectedCurrentFragmentState, currentFragmentState)
        assertEquals(
            expectedFeedbackMetadata,
            feedbackMetadata,
        )
    }
}
