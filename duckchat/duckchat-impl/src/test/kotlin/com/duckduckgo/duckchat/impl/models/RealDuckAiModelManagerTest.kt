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

package com.duckduckgo.duckchat.impl.models

import android.webkit.CookieManager
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.DuckAiUsageLimitsRepository
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.OverLimitNewChatDefault
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageLimitsSnapshot
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageNotice
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageNoticeId
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageWindow
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.duckchat.impl.store.SelectedModel
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeStorage
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.api.model.Entitlement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.coroutines.Continuation

@OptIn(ExperimentalCoroutinesApi::class)
class RealDuckAiModelManagerTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val modelsService: DuckAiModelsService = mock()
    private val dataStore: DuckChatDataStore = mock()
    private val subscriptions: Subscriptions = mock()
    private val duckAiHostProvider: DuckAiHostProvider = mock()
    private val cookiesManager: CookieManagerProvider = mock()

    private val entitlementFlow = MutableSharedFlow<Set<Entitlement>>()
    private val subscriptionStatusFlow = MutableStateFlow(SubscriptionStatus.UNKNOWN)
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java, ioDispatcher = coroutineRule.testDispatcher)
    private val duckChatPixels: DuckChatPixels = mock()
    private val usageLimits = MutableStateFlow<UsageLimitsSnapshot?>(null)
    private val usageLimitsRepository: DuckAiUsageLimitsRepository = mock {
        whenever(it.usageLimits(any())).thenReturn(usageLimits)
    }
    private val userDefaultSetting = MutableStateFlow<DuckAiBridgeSettingEntity?>(null)
    private val settings: DuckAiBridgeSettingsDao = mock {
        whenever(it.observe(USER_DEFAULT_KEY)).thenReturn(userDefaultSetting)
    }
    private val storage: DuckAiBridgeStorage = mock { whenever(it.settings).thenReturn(settings) }
    private val storageProvider: BrowserModeDataProvider<DuckAiBridgeStorage> = mock {
        whenever(it.forMode(BrowserMode.REGULAR)).thenReturn(storage)
    }
    private val userDefaultModelRepository = spy(UserDefaultModelRepository(lazy { storageProvider }, duckChatFeature))
    private val currentTimeProvider: CurrentTimeProvider = mock {
        whenever(it.currentTimeMillis()).thenReturn(NOW_MILLIS)
    }

    private lateinit var testee: RealDuckAiModelManager

    @Before
    fun setUp() {
        duckChatFeature.duckAiNativeStorage().setRawStoredState(Toggle.State(enable = true))
        duckChatFeature.userDefaultModel().setRawStoredState(Toggle.State(enable = false))
        whenever(subscriptions.getEntitlements()).thenReturn(entitlementFlow)
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(subscriptionStatusFlow)
        whenever(duckAiHostProvider.getHost()).thenReturn("duck.ai")
        subscriptions.stub { onBlocking { isEligible() }.thenReturn(true) }
        dataStore.stub { onBlocking { hasClearedPinnedDefaultModel() }.thenReturn(true) }
    }

    private fun createManager(): RealDuckAiModelManager {
        return RealDuckAiModelManager(
            modelsService = modelsService,
            dataStore = dataStore,
            subscriptions = subscriptions,
            duckAiHostProvider = duckAiHostProvider,
            duckChatFeature = lazy { duckChatFeature },
            duckChatPixels = lazy { duckChatPixels },
            cookiesManager = cookiesManager,
            usageLimitsRepository = usageLimitsRepository,
            userDefaultModelRepository = userDefaultModelRepository,
            currentTimeProvider = currentTimeProvider,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun whenCachedSelectionExistsThenStateRestoredOnInit() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("id", "model"))

        testee = createManager()

        assertEquals("id", testee.modelState.value.selectedModelId)
        assertEquals("model", testee.modelState.value.selectedModelShortName)
    }

    @Test
    fun whenNoCachedSelectionThenStateRemainsDefault() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)

        testee = createManager()

        assertNull(testee.modelState.value.selectedModelId)
        assertNull(testee.modelState.value.selectedModelShortName)
    }

    @Test
    fun whenPinnedDefaultModelPersistedThenClearedOnceAndNotRestored() = runTest {
        whenever(dataStore.hasClearedPinnedDefaultModel()).thenReturn(false)
        // Second value stands in for the store having been cleared by the migration.
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("gpt-5.4-mini", "5.4-mini"), null)

        testee = createManager()

        verify(dataStore).setSelectedModel(null)
        verify(dataStore).setClearedPinnedDefaultModel()
        assertNull(testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenPersistedModelIsNotThePinnedDefaultThenSelectionKept() = runTest {
        whenever(dataStore.hasClearedPinnedDefaultModel()).thenReturn(false)
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("claude-haiku-4-5", "Haiku 4.5"))

        testee = createManager()

        verify(dataStore, never()).setSelectedModel(null)
        verify(dataStore).setClearedPinnedDefaultModel()
        assertEquals("claude-haiku-4-5", testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenPinnedDefaultAlreadyClearedThenLaterPickOfThatModelSurvives() = runTest {
        whenever(dataStore.hasClearedPinnedDefaultModel()).thenReturn(true)
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("gpt-5.4-mini", "5.4-mini"))

        testee = createManager()

        verify(dataStore, never()).setSelectedModel(any())
        assertEquals("gpt-5.4-mini", testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenManagerCreatedWithoutConsumersThenModelsNotFetched() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)

        testee = createManager()
        entitlementFlow.emit(emptySet())

        verify(modelsService, never()).getModels(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun whenFetchModelsCalledRepeatedlyThenSubscriptionChangeRefetchesOnce() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()
        testee.fetchModels()
        entitlementFlow.emit(emptySet())

        entitlementFlow.emit(setOf(Entitlement(name = "plus", product = "Duck.ai")))

        verify(modelsService, times(3)).getModels(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun whenFetchModelsThenModelsResolvedAndStateUpdated() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("id1", accessTier = listOf("free"), entityHasAccess = true),
                    remoteModel("id2", accessTier = listOf("plus", "pro"), entityHasAccess = false),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val state = testee.modelState.value
        assertEquals(2, state.models.size)
        assertEquals(UserTier.FREE, state.userTier)
        assertTrue(state.models[0].isAccessible)
        assertFalse(state.models[1].isAccessible)
    }

    @Test
    fun whenEmptyAccessTierThenFallsBackToEntityHasAccess() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", accessTier = emptyList(), entityHasAccess = true)),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertTrue(testee.modelState.value.models[0].isAccessible)
    }

    @Test
    fun whenNonEmptyAccessTierAndTierMatchesThenAccessible() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", accessTier = listOf("plus", "pro"), entityHasAccess = false)),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertTrue(testee.modelState.value.models[0].isAccessible)
    }

    @Test
    fun whenNonEmptyAccessTierAndTierDoesNotMatchThenNotAccessibleDespiteEntityHasAccess() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", accessTier = listOf("plus", "pro"), entityHasAccess = true)),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertFalse(testee.modelState.value.models[0].isAccessible)
    }

    @Test
    fun whenDisplayNameNullThenFallsBackToName() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", displayName = null, shortName = null)),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val model = testee.modelState.value.models[0]
        assertEquals("id", model.displayName)
        assertEquals("id", model.shortName)
    }

    @Test
    fun whenNoSelectionPersistedThenFirstAccessibleModelSelected() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("id1", accessTier = listOf("plus"), entityHasAccess = false),
                    remoteModel("id2", accessTier = listOf("free"), entityHasAccess = true),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("id2", testee.modelState.value.selectedModelId)
        verify(dataStore, never()).setSelectedModel(any())
    }

    @Test
    fun whenNoSelectionPersistedAndListReorderedThenDefaultFollowsNewFirstAccessible() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("id1", accessTier = listOf("free"), entityHasAccess = true),
                    remoteModel("id2", accessTier = listOf("free"), entityHasAccess = true),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("id1", testee.modelState.value.selectedModelId)

        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("id2", accessTier = listOf("free"), entityHasAccess = true),
                    remoteModel("id1", accessTier = listOf("free"), entityHasAccess = true),
                ),
            ),
        )

        testee.fetchModels()

        assertEquals("id2", testee.modelState.value.selectedModelId)
        verify(dataStore, never()).setSelectedModel(any())
    }

    @Test
    fun whenUserPickedModelAndListReorderedThenPickPreserved() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("id2", "model2"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("id1", accessTier = listOf("free"), entityHasAccess = true),
                    remoteModel("id2", accessTier = listOf("free"), entityHasAccess = true),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("id2", testee.modelState.value.selectedModelId)
        verify(dataStore, never()).setSelectedModel(any())
    }

    @Test
    fun whenSelectedModelStillAccessibleThenSelectionPreserved() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("id", "model"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", accessTier = listOf("plus"), entityHasAccess = true)),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("id", testee.modelState.value.selectedModelId)
        verify(dataStore, never()).setSelectedModel(any())
    }

    @Test
    fun whenSelectedModelNoLongerAccessibleThenFallsBackToFirstAccessible() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("id1", "model1"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("id1", accessTier = listOf("plus"), entityHasAccess = false),
                    remoteModel("id2", accessTier = listOf("free"), entityHasAccess = true),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("id2", testee.modelState.value.selectedModelId)
        verify(dataStore).setSelectedModel(null)
    }

    @Test
    fun whenSelectedModelRemovedThenFallsBackToFirstAccessible() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("removed", "removed"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", accessTier = listOf("free"), entityHasAccess = true)),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("id", testee.modelState.value.selectedModelId)
        verify(dataStore).setSelectedModel(null)
    }

    @Test
    fun whenNoAccessibleModelsThenSelectedModelIdIsNull() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", accessTier = listOf("plus"), entityHasAccess = false)),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertNull(testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenFetchModelsFailsThenStateUnchanged() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("Network error"))

        testee = createManager()
        testee.fetchModels()

        assertTrue(testee.modelState.value.models.isEmpty())
    }

    @Test
    fun whenSelectModelThenStateUpdatedAndPersisted() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)

        testee = createManager()

        val model = AIChatModel("id", "model", "Model", "M", listOf("plus"), true)
        testee.selectModel(model)

        assertEquals("id", testee.modelState.value.selectedModelId)
        assertEquals("M", testee.modelState.value.selectedModelShortName)
        verify(dataStore).setSelectedModel(SelectedModel("id", "M"))
    }

    @Test
    fun whenResetModelForNewChatsThenPersistedPickNotRestoredOnInit() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("id", "model"))

        testee = createManager()

        assertEquals("gpt-6-luna", testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenResetModelForNewChatsAndModelsNotFetchedThenFreeTierDefaultOnFast() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)

        testee = createManager()

        assertEquals("gpt-6-luna", testee.getSelectedModelId())
        assertEquals(ReasoningMode.FAST, testee.modelState.value.selectedReasoningMode)
        assertTrue(testee.modelState.value.models.isEmpty())
    }

    @Test
    fun whenResetModelForNewChatsAndModelsNotFetchedForPaidTierThenPaidTierDefault() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))

        testee = createManager()

        assertEquals("gpt-5.6-terra", testee.getSelectedModelId())
        assertEquals(ReasoningMode.FAST, testee.modelState.value.selectedReasoningMode)
    }

    @Test
    fun whenResetModelForNewChatsAndFetchFailsThenTierDefaultKept() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("Network error"))

        testee = createManager()
        testee.fetchModels()

        assertEquals("gpt-6-luna", testee.getSelectedModelId())
        assertEquals(ReasoningMode.FAST, testee.modelState.value.selectedReasoningMode)
    }

    @Test
    fun whenResetModelForNewChatsAndFetchSucceedsThenHardcodedDefaultReplaced() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(listOf(remoteModel("other", supportedReasoningEffort = listOf("low")))),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("other", testee.getSelectedModelId())
        assertEquals(ReasoningMode.REASONING, testee.modelState.value.selectedReasoningMode)
    }

    @Test
    fun whenResetModelForNewChatsAndRefetchFailsAfterLoadThenLoadedSelectionKept() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull()))
            .thenReturn(AIChatModelsResponse(listOf(remoteModel("other", supportedReasoningEffort = listOf("low")))))
            .thenThrow(RuntimeException("Network error"))

        testee = createManager()
        testee.fetchModels()
        testee.fetchModels()

        assertEquals("other", testee.getSelectedModelId())
        assertEquals(ReasoningMode.REASONING, testee.modelState.value.selectedReasoningMode)
        assertEquals(listOf("other"), testee.modelState.value.models.map { it.id })
    }

    @Test
    fun whenResetModelForNewChatsOffAndFetchFailsThenNoHardcodedDefault() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("Network error"))

        testee = createManager()
        testee.fetchModels()

        assertNull(testee.getSelectedModelId())
        assertNull(testee.modelState.value.selectedReasoningMode)
    }

    @Test
    fun whenResetModelForNewChatsOffAndFetchFailsThenCachedPickKept() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("id", "model"))
        whenever(dataStore.getSelectedReasoningMode()).thenReturn("reasoning")
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("Network error"))

        testee = createManager()
        testee.fetchModels()

        assertEquals("id", testee.getSelectedModelId())
        assertEquals(ReasoningMode.REASONING, testee.modelState.value.selectedReasoningMode)
    }

    @Test
    fun whenResetModelForNewChatsThenFetchSelectsTierDefaultOnFastAndKeepsPersistedPick() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("picked", "Picked"))
        whenever(dataStore.getSelectedProvider()).thenReturn("ANTHROPIC")
        whenever(dataStore.getSelectedReasoningMode()).thenReturn("reasoning")
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("picked", provider = "anthropic"),
                    remoteModel("gpt-6-luna", supportedReasoningEffort = listOf("low", "none")),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("gpt-6-luna", testee.modelState.value.selectedModelId)
        assertEquals(ReasoningMode.FAST, testee.modelState.value.selectedReasoningMode)
        verify(dataStore, never()).setSelectedModel(anyOrNull())
        verify(dataStore, never()).setSelectedReasoningMode(anyOrNull())
    }

    @Test
    fun whenResetModelForNewChatsAndTierChangesThenDefaultFollowsTier() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("gpt-6-luna", accessTier = listOf("free", "plus", "pro")),
                    remoteModel("gpt-5.6-terra", accessTier = listOf("plus", "pro"), entityHasAccess = false),
                ),
            ),
        )
        testee = createManager()
        testee.fetchModels()
        assertEquals("gpt-6-luna", testee.modelState.value.selectedModelId)

        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))
        testee.fetchModels()

        assertEquals("gpt-5.6-terra", testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenResetModelForNewChatsThenPicksDoNotChangeOrPersistTheGlobalSelection() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        testee = createManager()

        testee.selectModel(AIChatModel("id", "model", "Model", "M", listOf("free"), true))
        testee.selectProvider(ModelProvider.ANTHROPIC)
        testee.selectReasoningMode(ReasoningMode.REASONING)

        assertEquals("gpt-6-luna", testee.modelState.value.selectedModelId)
        assertEquals(ReasoningMode.FAST, testee.modelState.value.selectedReasoningMode)
        verify(dataStore, never()).setSelectedModel(anyOrNull())
        verify(dataStore, never()).setSelectedProvider(anyOrNull())
        verify(dataStore, never()).setSelectedReasoningMode(anyOrNull())
    }

    @Test
    fun whenResetModelForNewChatsAndServerDefaultSentThenNewChatsStartOnIt() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            serverDefaultResponse(RemoteNewChatDefault(modelId = "server-model", reasoningEffort = "high")),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("server-model", testee.modelState.value.selectedModelId)
        assertEquals(ReasoningMode.EXTENDED_REASONING, testee.modelState.value.selectedReasoningMode)
        assertEquals("high", testee.getResolvedReasoningEffort())
        assertEquals(NewChatDefault("server-model", ReasoningMode.EXTENDED_REASONING), testee.modelState.value.newChatDefault)
        verify(dataStore, never()).setSelectedModel(anyOrNull())
        verify(dataStore, never()).setSelectedReasoningMode(anyOrNull())
    }

    @Test
    fun whenResetModelForNewChatsAndServerDefaultAbsentThenTierDefaultOnFast() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(serverDefaultResponse(null))

        testee = createManager()
        testee.fetchModels()

        assertEquals("gpt-6-luna", testee.modelState.value.selectedModelId)
        assertEquals(ReasoningMode.FAST, testee.modelState.value.selectedReasoningMode)
        assertEquals(NewChatDefault("gpt-6-luna", ReasoningMode.FAST), testee.modelState.value.newChatDefault)
    }

    @Test
    fun whenResetModelForNewChatsAndServerModelMissingThenTierDefaultOnFastNotTheServerMode() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            serverDefaultResponse(RemoteNewChatDefault(modelId = "gone", reasoningEffort = "low")),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("gpt-6-luna", testee.modelState.value.selectedModelId)
        assertEquals(ReasoningMode.FAST, testee.modelState.value.selectedReasoningMode)
        assertEquals(NewChatDefault("gone", ReasoningMode.REASONING), testee.modelState.value.newChatDefault)
    }

    @Test
    fun whenResetModelForNewChatsAndModelsNotFetchedThenNewChatDefaultIsTheHardcodedOne() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)

        testee = createManager()

        assertEquals(NewChatDefault("gpt-6-luna", ReasoningMode.FAST), testee.modelState.value.newChatDefault)
    }

    @Test
    fun whenResetModelForNewChatsOffThenServerDefaultChangesNothing() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(dataStore.getSelectedReasoningMode()).thenReturn("extended_reasoning")
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        val serverDefaults = listOf(
            RemoteNewChatDefault(modelId = "server-model", reasoningEffort = "none"),
            RemoteNewChatDefault(modelId = "gpt-6-luna", reasoningEffort = "low"),
            RemoteNewChatDefault(modelId = null, reasoningEffort = null),
        )

        val (baselineState, baselineWrites) = flagOffFetch(serverDefaultResponse(null))

        assertEquals("first", baselineState.selectedModelId)
        serverDefaults.forEach { serverDefault ->
            val (state, writes) = flagOffFetch(serverDefaultResponse(serverDefault))

            assertEquals(baselineState, state)
            assertEquals(baselineWrites, writes)
            assertNull(state.newChatDefault)
        }
    }

    @Test
    fun whenResetModelForNewChatsTurnedOffThenNextFetchClearsNewChatDefaultAndRestoresTheOldSelection() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            serverDefaultResponse(RemoteNewChatDefault(modelId = "server-model", reasoningEffort = "none")),
        )
        testee = createManager()
        testee.fetchModels()
        assertEquals("server-model", testee.modelState.value.selectedModelId)

        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = false))
        testee.fetchModels()

        assertEquals("first", testee.modelState.value.selectedModelId)
        assertNull(testee.modelState.value.newChatDefault)
    }

    @Test
    fun whenUserDefaultChangesOrIsDeletedThenOnlyUnpickedNewChatsUpdateWithoutFetching() = runTest {
        duckChatFeature.userDefaultModel().setRawStoredState(Toggle.State(enable = true))
        givenResetModelForNewChatsWithServerDefault()
        testee.fetchModels()
        runCurrent()
        assertEquals("server-model", testee.getSelectedModelId())
        val pickedTab = NativeInputState.zero().copy(newChatModelId = "server-model", newChatReasoningMode = "extended_reasoning")

        userDefaultSetting.value = userDefaultEntry("gpt-6-luna")
        runCurrent()

        assertEquals(NewChatDefault("gpt-6-luna", ReasoningMode.FAST, UserDefaultOutcome.APPLIED), testee.modelState.value.newChatDefault)
        assertEquals("gpt-6-luna", testee.getSelectedModelId())
        assertEquals("server-model", testee.modelState.value.forTab(pickedTab).selectedModelId)
        assertEquals(ReasoningMode.EXTENDED_REASONING, testee.modelState.value.forTab(pickedTab).selectedReasoningMode)

        userDefaultSetting.value = null
        runCurrent()

        assertEquals(NewChatDefault("server-model", ReasoningMode.FAST), testee.modelState.value.newChatDefault)
        assertEquals("server-model", testee.getSelectedModelId())
        verify(modelsService, times(1)).getModels(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun whenUserDefaultLoadedBeforeModelsThenHardcodedDefaultStandsUntilModelsLoad() = runTest {
        duckChatFeature.userDefaultModel().setRawStoredState(Toggle.State(enable = true))
        userDefaultSetting.value = userDefaultEntry("first")
        givenResetModelForNewChatsWithServerDefault()
        val response = CompletableDeferred<AIChatModelsResponse>()
        modelsService.stub { onBlocking { getModels(any(), anyOrNull(), anyOrNull()) } doSuspendableAnswer { response.await() } }

        val fetch = async { testee.fetchModels() }
        runCurrent()

        assertEquals(1, userDefaultSetting.subscriptionCount.value)
        assertEquals(NewChatDefault("gpt-6-luna", ReasoningMode.FAST), testee.modelState.value.newChatDefault)
        assertEquals("gpt-6-luna", testee.getSelectedModelId())

        response.complete(serverDefaultResponse(RemoteNewChatDefault(modelId = "server-model")))
        fetch.await()

        assertEquals("first", testee.getSelectedModelId())
        assertEquals(UserDefaultOutcome.APPLIED, testee.modelState.value.newChatDefault?.userDefaultOutcome)
    }

    @Test
    fun whenUserDefaultFlagOffThenStoredDefaultIgnoredAndOverLimitStillWins() = runTest {
        userDefaultSetting.value = userDefaultEntry("first")
        givenResetModelForNewChatsWithServerDefault()
        testee.fetchModels()
        runCurrent()
        assertEquals("server-model", testee.getSelectedModelId())

        usageLimits.value = overLimitSnapshot("gpt-6-luna", effort = "low")
        userDefaultSetting.value = userDefaultEntry("server-model")
        runCurrent()

        assertEquals(NewChatDefault("gpt-6-luna", ReasoningMode.REASONING), testee.modelState.value.newChatDefault)
        assertEquals("gpt-6-luna", testee.getSelectedModelId())
        verify(userDefaultModelRepository, never()).observe()
        verifyNoInteractions(storageProvider)
    }

    @Test
    fun whenUserDefaultFlagTurnsOffThenOutcomeClearsAndNextFetchDiscardsTheSetting() = runTest {
        duckChatFeature.userDefaultModel().setRawStoredState(Toggle.State(enable = true))
        userDefaultSetting.value = userDefaultEntry("first")
        givenResetModelForNewChatsWithServerDefault()
        testee.fetchModels()
        runCurrent()
        assertEquals("first", testee.getSelectedModelId())
        assertEquals(UserDefaultOutcome.APPLIED, testee.modelState.value.newChatDefault?.userDefaultOutcome)

        duckChatFeature.userDefaultModel().setRawStoredState(Toggle.State(enable = false))
        runCurrent()

        assertEquals("first", testee.getSelectedModelId())
        assertEquals(UserDefaultOutcome.NONE, testee.modelState.value.newChatDefault?.userDefaultOutcome)
        assertEquals(0, userDefaultSetting.subscriptionCount.value)

        testee.fetchModels()

        assertEquals(NewChatDefault("server-model", ReasoningMode.FAST), testee.modelState.value.newChatDefault)
        assertEquals("server-model", testee.getSelectedModelId())
        assertEquals(userDefaultEntry("first"), userDefaultSetting.value)
    }

    @Test
    fun whenResetModelForNewChatsAndOverLimitDefaultArrivesThenNewChatsStartOnIt() = runTest {
        givenResetModelForNewChatsWithServerDefault()
        testee.fetchModels()

        usageLimits.value = overLimitSnapshot("gpt-6-luna", effort = "low")

        assertEquals("gpt-6-luna", testee.modelState.value.selectedModelId)
        assertEquals(ReasoningMode.REASONING, testee.modelState.value.selectedReasoningMode)
        assertEquals(NewChatDefault("gpt-6-luna", ReasoningMode.REASONING), testee.modelState.value.newChatDefault)
        verify(usageLimitsRepository).usageLimits(BrowserMode.REGULAR)
        verify(dataStore, never()).setSelectedModel(anyOrNull())
    }

    @Test
    fun whenResetModelForNewChatsAndOverLimitDefaultClearedThenBackToServerDefault() = runTest {
        givenResetModelForNewChatsWithServerDefault()
        testee.fetchModels()
        usageLimits.value = overLimitSnapshot("gpt-6-luna")

        usageLimits.value = null

        assertEquals("server-model", testee.modelState.value.selectedModelId)
        assertEquals(NewChatDefault("server-model", ReasoningMode.FAST), testee.modelState.value.newChatDefault)
    }

    @Test
    fun whenResetModelForNewChatsAndOverLimitResetHasPassedThenNextFetchIsBackOnServerDefault() = runTest {
        givenResetModelForNewChatsWithServerDefault()
        testee.fetchModels()
        usageLimits.value = overLimitSnapshot("gpt-6-luna", resetsAtMillis = NOW_MILLIS + 1_000)
        assertEquals("gpt-6-luna", testee.modelState.value.selectedModelId)

        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(NOW_MILLIS + 1_000)
        testee.fetchModels()

        assertEquals("server-model", testee.modelState.value.selectedModelId)
        assertEquals(NewChatDefault("server-model", ReasoningMode.FAST), testee.modelState.value.newChatDefault)
    }

    @Test
    fun whenResetModelForNewChatsAndOverLimitModelUnusableThenServerDefault() = runTest {
        givenResetModelForNewChatsWithServerDefault()
        testee.fetchModels()

        usageLimits.value = overLimitSnapshot("not-a-model")

        assertEquals("server-model", testee.modelState.value.selectedModelId)
        assertEquals(NewChatDefault("server-model", ReasoningMode.FAST), testee.modelState.value.newChatDefault)
    }

    @Test
    fun whenResetModelForNewChatsAndOverLimitDefaultSentBeforeModelsLoadThenAppliedOnFetch() = runTest {
        usageLimits.value = overLimitSnapshot("first")
        givenResetModelForNewChatsWithServerDefault()
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenThrow(RuntimeException("offline"))

        testee.fetchModels()
        assertEquals(NewChatDefault("gpt-6-luna", ReasoningMode.FAST), testee.modelState.value.newChatDefault)

        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            serverDefaultResponse(RemoteNewChatDefault(modelId = "server-model")),
        )
        testee.fetchModels()
        assertEquals("first", testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenResetModelForNewChatsAndOverLimitDefaultThenModelSourceIsDefaultUnlessPicked() = runTest {
        givenResetModelForNewChatsWithServerDefault()
        testee.fetchModels()
        usageLimits.value = overLimitSnapshot("gpt-6-luna", effort = "low")
        val state = testee.modelState.value

        assertEquals(ModelSource.DEFAULT, modelSourceFor(true, state, null, "gpt-6-luna", "low"))
        assertEquals(ModelSource.PICKED, modelSourceFor(true, state, null, "server-model", "none"))
    }

    @Test
    fun whenResetModelForNewChatsOffThenOverLimitDefaultIgnored() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("server-model", "server-model"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(serverDefaultResponse(null))
        usageLimits.value = overLimitSnapshot("gpt-6-luna")

        testee = createManager()
        testee.fetchModels()

        assertEquals("server-model", testee.modelState.value.selectedModelId)
        assertNull(testee.modelState.value.newChatDefault)
        verify(usageLimitsRepository, never()).usageLimits(any())
        verify(dataStore, never()).setSelectedModel(anyOrNull())
    }

    private suspend fun givenResetModelForNewChatsWithServerDefault() {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            serverDefaultResponse(RemoteNewChatDefault(modelId = "server-model")),
        )
        testee = createManager()
    }

    private fun overLimitSnapshot(
        modelId: String,
        effort: String? = null,
        resetsAtMillis: Long = NOW_MILLIS + 60_000,
    ) = UsageLimitsSnapshot(
        notice = UsageNotice(UsageNoticeId.DAILY_REACHED, UsageWindow.DAILY, 100, resetsAtMillis, reached = true, dismissible = false),
        cta = null,
        newChatDefault = OverLimitNewChatDefault(modelId, effort, resetsAtMillis),
    )

    private fun userDefaultEntry(modelId: String) = DuckAiBridgeSettingEntity(key = USER_DEFAULT_KEY, value = "\"$modelId\"")

    /** The first accessible model differs from both the tier default and the server's model, so a leak would show. */
    private fun serverDefaultResponse(newChatDefault: RemoteNewChatDefault?) = AIChatModelsResponse(
        models = listOf(
            remoteModel("first", supportedReasoningEffort = listOf("low")),
            remoteModel("gpt-6-luna", supportedReasoningEffort = listOf("none", "low")),
            remoteModel("server-model", supportedReasoningEffort = listOf("none", "low", "high")),
        ),
        newChatDefault = newChatDefault,
    )

    /** The state a flag-off manager publishes for [response], and every write it made to [DuckChatDataStore] on the way. */
    private suspend fun flagOffFetch(response: AIChatModelsResponse): Pair<ModelState, List<Pair<String, List<Any?>>>> {
        clearInvocations(dataStore)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(response)
        testee = createManager()
        testee.fetchModels()
        val writes = mockingDetails(dataStore).invocations
            .filter { it.method.name.startsWith("set") }
            .map { invocation -> invocation.method.name to invocation.arguments.filterNot { it is Continuation<*> } }
        return testee.modelState.value to writes
    }

    @Test
    fun whenGetSelectedModelIdThenReturnsCurrentStateValue() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("id", "model"))

        testee = createManager()

        assertEquals("id", testee.getSelectedModelId())
    }

    @Test
    fun whenNoSelectionThenGetSelectedModelIdReturnsNull() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)

        testee = createManager()

        assertNull(testee.getSelectedModelId())
    }

    @Test
    fun whenSubscriptionInactiveThenTierIsFree() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.FREE, testee.modelState.value.userTier)
    }

    @Test
    fun whenSubscriptionActiveWithDuckAiPlusThenTierIsPlus() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.PLUS, testee.modelState.value.userTier)
    }

    @Test
    fun whenSubscriptionActiveWithDuckAiProThenTierIsPro() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "pro", product = "Duck.ai"))))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.PRO, testee.modelState.value.userTier)
    }

    @Test
    fun whenModelHasEmptyAccessTierAndEntityHasNoAccessThenModelIsFilteredOut() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("visible", accessTier = listOf("free"), entityHasAccess = true),
                    remoteModel("ghost", accessTier = emptyList(), entityHasAccess = false),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val ids = testee.modelState.value.models.map { it.id }
        assertEquals(listOf("visible"), ids)
    }

    @Test
    fun whenUserNotEligibleToPurchaseThenInaccessiblePaidModelsFilteredOut() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(subscriptions.isEligible()).thenReturn(false)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("free", accessTier = listOf("free"), entityHasAccess = true),
                    remoteModel("plus", accessTier = listOf("plus", "pro"), entityHasAccess = false),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val ids = testee.modelState.value.models.map { it.id }
        assertEquals(listOf("free"), ids)
    }

    @Test
    fun whenUserEligibleToPurchaseThenInaccessiblePaidModelsRetainedForUpsell() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("free", accessTier = listOf("free"), entityHasAccess = true),
                    remoteModel("plus", accessTier = listOf("plus", "pro"), entityHasAccess = false),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val ids = testee.modelState.value.models.map { it.id }
        assertEquals(listOf("free", "plus"), ids)
    }

    @Test
    fun whenUserNotEligibleToPurchaseThenAccessibleModelsStillShown() = runTest {
        // A subscribed user is "eligible" (isEligible() covers active subs), but even if eligibility
        // resolved false, models the user already has access to must never be filtered out.
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))
        whenever(subscriptions.isEligible()).thenReturn(false)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("free", accessTier = listOf("free", "plus", "pro"), entityHasAccess = true),
                    remoteModel("plus", accessTier = listOf("plus", "pro"), entityHasAccess = true),
                    remoteModel("pro", accessTier = listOf("pro"), entityHasAccess = false),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val ids = testee.modelState.value.models.map { it.id }
        assertEquals(listOf("free", "plus"), ids)
    }

    @Test
    fun whenEligibilityCheckThrowsThenFailsClosedAndInaccessiblePaidModelsFilteredOut() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(subscriptions.isEligible()).thenThrow(RuntimeException("Error"))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("free", accessTier = listOf("free"), entityHasAccess = true),
                    remoteModel("plus", accessTier = listOf("plus", "pro"), entityHasAccess = false),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val ids = testee.modelState.value.models.map { it.id }
        assertEquals(listOf("free"), ids)
    }

    @Test
    fun whenSubscriptionActiveWithoutDuckAiPlusThenTierIsFree() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "subscriber", product = "Network Protection"))))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.FREE, testee.modelState.value.userTier)
    }

    @Test
    fun whenDuckAiEntitlementHasUnknownTierNameThenTierIsFree() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "premium", product = "Duck.ai"))))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.FREE, testee.modelState.value.userTier)
    }

    @Test
    fun whenSubscriptionActiveWithEmptyEntitlementsThenTierIsFree() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(emptySet()))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.FREE, testee.modelState.value.userTier)
    }

    @Test
    fun whenMultipleEntitlementsThenDuckAiTierIsResolved() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(
            flowOf(
                setOf(
                    Entitlement(name = "subscriber", product = "Network Protection"),
                    Entitlement(name = "pro", product = "Duck.ai"),
                ),
            ),
        )
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.PRO, testee.modelState.value.userTier)
    }

    @Test
    fun whenSubscriptionExpiredThenTierIsFree() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.EXPIRED)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.FREE, testee.modelState.value.userTier)
    }

    @Test
    fun whenSubscriptionInGracePeriodWithDuckAiPlusThenTierIsPlus() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.GRACE_PERIOD)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.PLUS, testee.modelState.value.userTier)
    }

    @Test
    fun whenSubscriptionStatusThrowsThenTierDefaultsToFree() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenThrow(RuntimeException("Error"))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.FREE, testee.modelState.value.userTier)
    }

    @Test
    fun whenFetchModelsThenProviderResolvedFromRemoteFields() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("gpt-5-mini", provider = "openai"),
                    remoteModel("claude-3-5-sonnet", provider = "anthropic"),
                    remoteModel("meta-llama/Llama-3.3", provider = "openai"),
                    remoteModel("mistralai/Mistral-Small", provider = null),
                    remoteModel("openai/gpt-oss-120b", provider = "openai"),
                    remoteModel("some-other-model", provider = "perplexity"),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val byId = testee.modelState.value.models.associateBy { it.id }
        assertEquals(ModelProvider.OPENAI, byId.getValue("gpt-5-mini").provider)
        assertEquals(ModelProvider.ANTHROPIC, byId.getValue("claude-3-5-sonnet").provider)
        assertEquals(ModelProvider.META, byId.getValue("meta-llama/Llama-3.3").provider)
        assertEquals(ModelProvider.MISTRAL, byId.getValue("mistralai/Mistral-Small").provider)
        assertEquals(ModelProvider.OSS, byId.getValue("openai/gpt-oss-120b").provider)
        assertEquals(ModelProvider.UNKNOWN, byId.getValue("some-other-model").provider)
    }

    @Test
    fun whenAccessTokenExistsThenModelsFetchedWithBearerHeader() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        duckChatFeature.updatedPickers().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptions.getAccessToken()).thenReturn("token123")
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(listOf(remoteModel("id", accessTier = listOf("free"), entityHasAccess = true))),
        )

        testee = createManager()
        testee.fetchModels()

        verify(modelsService).getModels("https://duck.ai/duckchat/v1/models", "Bearer token123", null)
    }

    @Test
    fun whenNoAccessTokenThenModelsFetchedWithoutAuthorizationHeader() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        duckChatFeature.updatedPickers().setRawStoredState(Toggle.State(enable = true))
        whenever(subscriptions.getAccessToken()).thenReturn(null)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(listOf(remoteModel("id", accessTier = listOf("free"), entityHasAccess = true))),
        )

        testee = createManager()
        testee.fetchModels()

        verify(modelsService).getModels("https://duck.ai/duckchat/v1/models", null, null)
    }

    @Test
    fun whenAccessTokenLookupFailsThenModelsStillFetchedUnauthenticated() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        duckChatFeature.updatedPickers().setRawStoredState(Toggle.State(enable = true))
        subscriptions.stub { onBlocking { getAccessToken() }.thenThrow(RuntimeException("boom")) }
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(listOf(remoteModel("id", accessTier = listOf("free"), entityHasAccess = true))),
        )

        testee = createManager()
        testee.fetchModels()

        verify(modelsService).getModels("https://duck.ai/duckchat/v1/models", null, null)
        assertEquals(1, testee.modelState.value.models.size)
    }

    @Test
    fun whenUpdatedPickersDisabledThenAccessTokenNotSent() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        duckChatFeature.updatedPickers().setRawStoredState(Toggle.State(enable = false))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(listOf(remoteModel("id", accessTier = listOf("free"), entityHasAccess = true))),
        )

        testee = createManager()
        testee.fetchModels()

        verify(modelsService).getModels("https://duck.ai/duckchat/v1/models", null, null)
        verify(subscriptions, never()).getAccessToken()
    }

    @Test
    fun whenSubscriptionStatusChangesThenModelsRefetched() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(listOf(remoteModel("id", accessTier = listOf("free"), entityHasAccess = true))),
        )

        testee = createManager()
        testee.fetchModels()
        entitlementFlow.emit(emptySet())

        subscriptionStatusFlow.value = SubscriptionStatus.AUTO_RENEWABLE

        verify(modelsService, times(2)).getModels(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun whenModelHasKnownLabelThenLabelParsed() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("everyday", label = "EVERYDAY_USE"),
                    remoteModel("hungry", label = "USES_LIMITS_FASTER"),
                    remoteModel("plain", label = null),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val models = testee.modelState.value.models
        assertEquals(ModelLabel.EVERYDAY_USE, models.first { it.id == "everyday" }.label)
        assertEquals(ModelLabel.USES_LIMITS_FASTER, models.first { it.id == "hungry" }.label)
        assertNull(models.first { it.id == "plain" }.label)
    }

    @Test
    fun whenModelHasUnrecognisedLabelThenLabelIsUnknown() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(listOf(remoteModel("id", label = "BRAND_NEW_LABEL"))),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals(ModelLabel.UNKNOWN, testee.modelState.value.models[0].label)
    }

    @Test
    fun whenUpdatedPickersEnabledThenLabelledModelsSortFirstKeepingEndpointOrder() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        duckChatFeature.updatedPickers().setRawStoredState(Toggle.State(enable = true))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("plain1"),
                    remoteModel("labelled1", label = "EVERYDAY_USE"),
                    remoteModel("plain2"),
                    remoteModel("labelled2", label = "USES_LIMITS_FASTER"),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals(
            listOf("labelled1", "labelled2", "plain1", "plain2"),
            testee.modelState.value.models.map { it.id },
        )
    }

    @Test
    fun whenUpdatedPickersDisabledThenEndpointOrderKept() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        duckChatFeature.updatedPickers().setRawStoredState(Toggle.State(enable = false))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("plain"), remoteModel("labelled", label = "EVERYDAY_USE")),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals(listOf("plain", "labelled"), testee.modelState.value.models.map { it.id })
    }

    @Test
    fun whenLabelledModelSortsFirstThenDerivedDefaultStillFollowsEndpointOrder() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        duckChatFeature.updatedPickers().setRawStoredState(Toggle.State(enable = true))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("first-in-response"),
                    remoteModel("labelled", label = "EVERYDAY_USE"),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("first-in-response", testee.modelState.value.selectedModelId)
        assertEquals("labelled", testee.modelState.value.models[0].id)
    }

    @Test
    fun whenLabelIsUnrecognisedThenDebugPixelReportsIt() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(listOf(remoteModel("id", label = "EXTRA_PRIVACY"))),
        )

        testee = createManager()
        testee.fetchModels()

        verify(duckChatPixels).fireUnknownModelLabel("EXTRA_PRIVACY")
    }

    @Test
    fun whenSeveralLabelsAreUnrecognisedThenEachIsReportedOncePerResponse() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("a", label = "EXTRA_PRIVACY"),
                    remoteModel("b", label = "EXTRA_PRIVACY"),
                    remoteModel("c", label = "FASTEST_YET"),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        verify(duckChatPixels).fireUnknownModelLabel("EXTRA_PRIVACY")
        verify(duckChatPixels).fireUnknownModelLabel("FASTEST_YET")
    }

    @Test
    fun whenLabelIsRecognisedThenNoDebugPixel() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(listOf(remoteModel("id", label = "EVERYDAY_USE"))),
        )

        testee = createManager()
        testee.fetchModels()

        verify(duckChatPixels, never()).fireUnknownModelLabel(any())
    }

    @Test
    fun whenUnrecognisedLabelHasUnexpectedShapeThenItIsSanitisedBeforeReporting() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(listOf(remoteModel("id", label = "new label (beta)!"))),
        )

        testee = createManager()
        testee.fetchModels()

        verify(duckChatPixels).fireUnknownModelLabel("NEWLABELBETA")
    }

    @Test
    fun whenEntitlementsChangeThenModelsFetched() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(listOf(remoteModel("id", accessTier = listOf("free"), entityHasAccess = true))),
        )

        testee = createManager()
        testee.fetchModels()
        entitlementFlow.emit(emptySet())

        entitlementFlow.emit(setOf(Entitlement(name = "plus", product = "Duck.ai")))

        verify(modelsService, times(2)).getModels(any(), anyOrNull(), anyOrNull())
        assertEquals(1, testee.modelState.value.models.size)
    }

    @Test
    fun whenModelHasSupportedFileTypesThenResolvedModel() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("claude", supportedFileTypes = listOf("application/pdf")),
                    remoteModel("gpt", supportedFileTypes = null),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val byId = testee.modelState.value.models.associateBy { it.id }
        assertEquals(listOf("application/pdf"), byId.getValue("claude").supportedFileTypes)
        assertTrue(byId.getValue("claude").supportsFileUpload)
        assertTrue(byId.getValue("gpt").supportedFileTypes.isEmpty())
        assertFalse(byId.getValue("gpt").supportsFileUpload)
    }

    @Test
    fun whenAttachmentLimitsProvidedThenResolvedForFreeTier() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                models = listOf(remoteModel("id")),
                attachmentLimits = mapOf(
                    "free" to RemoteTierAttachmentLimits(
                        files = RemoteFileLimits(maxPerConversation = 3, maxFileSizeMB = 5, maxPagesPerFile = 8),
                        images = RemoteImageLimits(maxPerTurn = 3, maxPerConversation = 5, maxInputCharsWithAttachments = 4500),
                    ),
                    "plus" to RemoteTierAttachmentLimits(
                        files = RemoteFileLimits(maxPerConversation = 5, maxFileSizeMB = 25, maxPagesPerFile = 15),
                        images = RemoteImageLimits(maxPerTurn = 3, maxPerConversation = 10, maxInputCharsWithAttachments = 4500),
                    ),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val limits = testee.modelState.value.attachmentLimits
        assertEquals(3, limits.files.maxPerConversation)
        assertEquals(5L * 1024 * 1024, limits.files.maxFileSizeBytes)
        assertEquals(8, limits.files.maxPagesPerFile)
        assertEquals(3, limits.images.maxPerTurn)
        assertEquals(5, limits.images.maxPerConversation)
    }

    @Test
    fun whenAttachmentLimitsProvidedThenResolvedForPlusTier() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                models = listOf(remoteModel("id")),
                attachmentLimits = mapOf(
                    "free" to RemoteTierAttachmentLimits(
                        files = RemoteFileLimits(maxPerConversation = 3, maxFileSizeMB = 5, maxPagesPerFile = 8),
                        images = RemoteImageLimits(maxPerTurn = 3, maxPerConversation = 5),
                    ),
                    "plus" to RemoteTierAttachmentLimits(
                        files = RemoteFileLimits(maxPerConversation = 5, maxFileSizeMB = 25, maxPagesPerFile = 15),
                        images = RemoteImageLimits(maxPerTurn = 3, maxPerConversation = 10),
                    ),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val limits = testee.modelState.value.attachmentLimits
        assertEquals(5, limits.files.maxPerConversation)
        assertEquals(25L * 1024 * 1024, limits.files.maxFileSizeBytes)
        assertEquals(15, limits.files.maxPagesPerFile)
        assertEquals(10, limits.images.maxPerConversation)
    }

    @Test
    fun whenNoAttachmentLimitsThenDefaultsUsed() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(models = listOf(remoteModel("id"))),
        )

        testee = createManager()
        testee.fetchModels()

        val limits = testee.modelState.value.attachmentLimits
        assertEquals(FileLimits.DEFAULT_FILE_MAX_PER_CONVERSATION, limits.files.maxPerConversation)
        assertEquals(FileLimits.DEFAULT_FILE_MAX_SIZE_BYTES, limits.files.maxFileSizeBytes)
        assertEquals(ImageLimits.DEFAULT_IMAGE_MAX_PER_TURN, limits.images.maxPerTurn)
        assertEquals(ImageLimits.DEFAULT_IMAGE_MAX_PER_CONVERSATION, limits.images.maxPerConversation)
    }

    @Test
    fun whenAttachmentLimitsMissingTierThenDefaultsUsed() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                models = listOf(remoteModel("id")),
                attachmentLimits = mapOf(
                    "free" to RemoteTierAttachmentLimits(
                        images = RemoteImageLimits(maxPerTurn = 2),
                    ),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val limits = testee.modelState.value.attachmentLimits
        assertEquals(ImageLimits.DEFAULT_IMAGE_MAX_PER_TURN, limits.images.maxPerTurn)
        assertEquals(ImageLimits.DEFAULT_IMAGE_MAX_PER_CONVERSATION, limits.images.maxPerConversation)
        assertEquals(ImageLimits.DEFAULT_MAX_INPUT_CHARS_WITH_ATTACHMENTS, limits.images.maxInputCharsWithAttachments)
    }

    @Test
    fun whenModelSupportsImageUploadThenResolvedModelHasImageUploadEnabledAndNativeFormats() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", supportsImageUpload = true)),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val model = testee.modelState.value.models[0]
        assertTrue(model.supportsImageUpload)
        assertEquals(AIChatModel.NATIVE_SUPPORTED_IMAGE_FORMATS, model.supportedImageFormats)
    }

    @Test
    fun whenModelDoesNotSupportImageUploadThenResolvedModelHasImageUploadDisabledAndEmptyFormats() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", supportsImageUpload = false)),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val model = testee.modelState.value.models[0]
        assertFalse(model.supportsImageUpload)
        assertTrue(model.supportedImageFormats.isEmpty())
    }

    private fun remoteModel(
        id: String,
        displayName: String? = null,
        shortName: String? = null,
        accessTier: List<String> = listOf("free"),
        entityHasAccess: Boolean = true,
        provider: String? = null,
        supportsImageUpload: Boolean = false,
        supportedFileTypes: List<String>? = null,
        supportedReasoningEffort: List<String>? = null,
        reasoningEffortAccess: List<RemoteReasoningEffortAccess>? = null,
        supportedTools: List<String>? = null,
        label: String? = null,
    ) = RemoteAIChatModel(
        id = id,
        name = id,
        displayName = displayName,
        shortName = shortName,
        accessTier = accessTier,
        entityHasAccess = entityHasAccess,
        provider = provider,
        supportsImageUpload = supportsImageUpload,
        supportedFileTypes = supportedFileTypes,
        supportedReasoningEffort = supportedReasoningEffort,
        reasoningEffortAccess = reasoningEffortAccess,
        supportedTools = supportedTools,
        label = label,
    )

    @Test
    fun whenRemoteHasUnknownReasoningEffortThenUnknownDropped() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel(
                        "id",
                        accessTier = listOf("free"),
                        entityHasAccess = true,
                        supportedReasoningEffort = listOf("low", "mystery", "high"),
                    ),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val resolved = testee.modelState.value.models.single()
        assertEquals(listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH), resolved.supportedReasoningEfforts)
    }

    @Test
    fun whenRemoteReasoningEffortAccessMissingThenDomainListIsEmpty() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", reasoningEffortAccess = null)),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals(emptyList<ReasoningEffortAccess>(), testee.modelState.value.models.single().reasoningEffortAccess)
    }

    @Test
    fun whenRemoteReasoningEffortAccessHasUnknownIdThenUnknownDropped() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel(
                        "id",
                        reasoningEffortAccess = listOf(
                            RemoteReasoningEffortAccess(id = "low", accessTier = listOf("free", "plus", "pro"), entityHasAccess = true),
                            RemoteReasoningEffortAccess(id = "very_high", accessTier = listOf("pro"), entityHasAccess = false),
                        ),
                    ),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val efforts = testee.modelState.value.models.single().reasoningEffortAccess.map { it.effort }
        assertEquals(listOf(ReasoningEffort.LOW), efforts)
    }

    @Test
    fun whenRemoteReasoningEffortAccessResolvedAgainstPlusUserThenAccessibilityFollowsAccessTier() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel(
                        "id",
                        accessTier = listOf("free", "plus", "pro"),
                        entityHasAccess = true,
                        reasoningEffortAccess = listOf(
                            RemoteReasoningEffortAccess(id = "low", accessTier = listOf("free", "plus", "pro"), entityHasAccess = true),
                            RemoteReasoningEffortAccess(id = "medium", accessTier = listOf("pro"), entityHasAccess = false),
                        ),
                    ),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val access = testee.modelState.value.models.single().reasoningEffortAccess
        assertEquals(
            listOf(
                ReasoningEffortAccess(effort = ReasoningEffort.LOW, accessTier = listOf("free", "plus", "pro"), isAccessible = true),
                ReasoningEffortAccess(effort = ReasoningEffort.MEDIUM, accessTier = listOf("pro"), isAccessible = false),
            ),
            access,
        )
    }

    @Test
    fun whenModelInaccessibleButEffortAccessTierIncludesUserThenEffortIsAccessibleNotClampedByModel() = runTest {
        // Locks in Option A: per-effort isAccessible is resolved independently of model.isAccessible.
        // The "model takes precedence" rule lives at tap-handling time, not in the data layer.
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel(
                        "id",
                        accessTier = listOf("plus", "pro"),
                        entityHasAccess = false,
                        reasoningEffortAccess = listOf(
                            RemoteReasoningEffortAccess(id = "low", accessTier = listOf("free", "plus", "pro"), entityHasAccess = true),
                        ),
                    ),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val resolved = testee.modelState.value.models.single()
        assertEquals(false, resolved.isAccessible)
        assertEquals(true, resolved.reasoningEffortAccess.single().isAccessible)
    }

    @Test
    fun whenCachedReasoningModeExistsThenStateRestoredOnInit() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(ReasoningMode.REASONING.rawValue)

        testee = createManager()

        assertEquals(ReasoningMode.REASONING, testee.modelState.value.selectedReasoningMode)
    }

    @Test
    fun whenCachedReasoningModeIsUnparseableThenStoreClearedAndStateNull() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(dataStore.getSelectedReasoningMode()).thenReturn("garbage_value")

        testee = createManager()

        assertNull(testee.modelState.value.selectedReasoningMode)
        verify(dataStore).setSelectedReasoningMode(null)
    }

    @Test
    fun whenCachedReasoningModeExistsButNoCachedModelThenOrphanClearedFromStore() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(ReasoningMode.REASONING.rawValue)

        testee = createManager()

        assertNull(testee.modelState.value.selectedReasoningMode)
        assertNull(testee.modelState.value.selectedModelId)
        verify(dataStore).setSelectedReasoningMode(null)
    }

    @Test
    fun whenRestoreCachedSelectionThrowsThenModelsStillFetched() = runTest {
        whenever(dataStore.getSelectedModel()).thenThrow(RuntimeException("disk error"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        verify(modelsService).getModels(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun whenHostIsCustomThenCookiesSentWithRequest() = runTest {
        val cookieManager = mock<CookieManager>()
        whenever(duckAiHostProvider.getHost()).thenReturn("staging.duck.ai")
        whenever(duckAiHostProvider.isCustomHost()).thenReturn(true)
        whenever(cookiesManager.forMode(BrowserMode.REGULAR)).thenReturn(cookieManager)
        whenever(cookieManager.getCookie("https://staging.duck.ai/duckchat/v1/models")).thenReturn("dcs=abc")
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        verify(modelsService).getModels(eq("https://staging.duck.ai/duckchat/v1/models"), eq(null), eq("dcs=abc"))
    }

    @Test
    fun whenHostIsCustomAndNoCookieManagerAvailableThenModelsRequestedWithoutCookies() = runTest {
        whenever(duckAiHostProvider.getHost()).thenReturn("staging.duck.ai")
        whenever(duckAiHostProvider.isCustomHost()).thenReturn(true)
        whenever(cookiesManager.forMode(BrowserMode.REGULAR)).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        verify(modelsService).getModels(eq("https://staging.duck.ai/duckchat/v1/models"), eq(null), eq(null))
    }

    @Test
    fun whenHostIsNotCustomThenCookiesNotSentWithRequest() = runTest {
        val cookieManager = mock<CookieManager>()
        whenever(duckAiHostProvider.isCustomHost()).thenReturn(false)
        whenever(cookiesManager.forMode(BrowserMode.REGULAR)).thenReturn(cookieManager)
        whenever(cookieManager.getCookie(any())).thenReturn("dcs=abc")
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        verify(modelsService).getModels(eq("https://duck.ai/duckchat/v1/models"), eq(null), eq(null))
        verify(cookieManager, never()).getCookie(any())
    }

    @Test
    fun whenCachedReasoningModeIsNullThenStoreNotTouchedAndStateNull() = runTest {
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(null)
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))

        testee = createManager()

        assertNull(testee.modelState.value.selectedReasoningMode)
        verify(dataStore, never()).setSelectedReasoningMode(null)
    }

    @Test
    fun whenFetchAndSelectedModelSupportsPersistedModeThenAvailableModesPopulated() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(ReasoningMode.REASONING.rawValue)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel(
                        "m",
                        accessTier = listOf("free"),
                        entityHasAccess = true,
                        supportedReasoningEffort = listOf("none", "low"),
                    ),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        val state = testee.modelState.value
        assertEquals(2, state.availableReasoningModes.size)
        assertEquals(ReasoningMode.REASONING, state.selectedReasoningMode)
        verify(dataStore, never()).setSelectedReasoningMode(null)
    }

    @Test
    fun whenFetchAndSelectedModelDoesNotSupportPersistedModeThenStaleReplacedWithFirstAccessible() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(ReasoningMode.EXTENDED_REASONING.rawValue)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel(
                        "m",
                        accessTier = listOf("free"),
                        entityHasAccess = true,
                        supportedReasoningEffort = listOf("none", "low"),
                    ),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals(ReasoningMode.FAST, testee.modelState.value.selectedReasoningMode)
        verify(dataStore).setSelectedReasoningMode(ReasoningMode.FAST.rawValue)
    }

    @Test
    fun whenAccessibleModelHasAllReasoningEffortsGatedThenSelectedModeClearedAndNoEffortResolved() = runTest {
        // Edge case: model accessible to FREE user, but every supported reasoning effort is gated to PRO.
        // Expected: selectedReasoningMode is cleared, getResolvedReasoningEffort returns null (no effort submitted).
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(ReasoningMode.REASONING.rawValue)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel(
                        "m",
                        accessTier = listOf("free", "plus", "pro"),
                        entityHasAccess = true,
                        supportedReasoningEffort = listOf("none", "low"),
                        reasoningEffortAccess = listOf(
                            RemoteReasoningEffortAccess(id = "none", accessTier = listOf("pro"), entityHasAccess = false),
                            RemoteReasoningEffortAccess(id = "low", accessTier = listOf("pro"), entityHasAccess = false),
                        ),
                    ),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertNull(testee.modelState.value.selectedReasoningMode)
        verify(dataStore).setSelectedReasoningMode(null)
        assertNull(testee.getResolvedReasoningEffort())
    }

    @Test
    fun whenSelectModelReplacesStaleReasoningWithFirstAccessibleOnNewModel() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(ReasoningMode.FAST.rawValue)
        testee = createManager()

        val newModel = AIChatModel(
            id = "n",
            name = "N",
            displayName = "N",
            shortName = "N",
            accessTier = listOf("free"),
            isAccessible = true,
            supportedReasoningEfforts = listOf(ReasoningEffort.LOW),
        )

        testee.selectModel(newModel)

        assertEquals(ReasoningMode.REASONING, testee.modelState.value.selectedReasoningMode)
        verify(dataStore).setSelectedReasoningMode(ReasoningMode.REASONING.rawValue)
    }

    @Test
    fun whenSelectModelPreservesPersistedReasoningModeWhenSupportedByNewModel() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(ReasoningMode.FAST.rawValue)
        testee = createManager()

        val newModel = AIChatModel(
            id = "n",
            name = "N",
            displayName = "N",
            shortName = "N",
            accessTier = listOf("free"),
            isAccessible = true,
            supportedReasoningEfforts = listOf(ReasoningEffort.NONE, ReasoningEffort.LOW),
        )

        testee.selectModel(newModel)

        assertEquals(ReasoningMode.FAST, testee.modelState.value.selectedReasoningMode)
        verify(dataStore, never()).setSelectedReasoningMode(null)
    }

    @Test
    fun whenSelectReasoningModeAndSupportedThenPersistedAndStateUpdated() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(ReasoningMode.REASONING.rawValue)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel(
                        "m",
                        accessTier = listOf("free"),
                        entityHasAccess = true,
                        supportedReasoningEffort = listOf("none", "low"),
                    ),
                ),
            ),
        )
        testee = createManager()
        testee.fetchModels()

        testee.selectReasoningMode(ReasoningMode.FAST)

        assertEquals(ReasoningMode.FAST, testee.modelState.value.selectedReasoningMode)
        verify(dataStore).setSelectedReasoningMode(ReasoningMode.FAST.rawValue)
    }

    @Test
    fun whenSelectReasoningModeAndUnsupportedThenIgnored() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel(
                        "m",
                        accessTier = listOf("free"),
                        entityHasAccess = true,
                        supportedReasoningEffort = listOf("low"),
                    ),
                ),
            ),
        )
        testee = createManager()
        testee.fetchModels()
        // fetchModels auto-promotes the persisted-null state to REASONING (only accessible mode).
        assertEquals(ReasoningMode.REASONING, testee.modelState.value.selectedReasoningMode)

        testee.selectReasoningMode(ReasoningMode.FAST)

        // FAST isn't in available → refused, state stays REASONING.
        assertEquals(ReasoningMode.REASONING, testee.modelState.value.selectedReasoningMode)
        verify(dataStore, never()).setSelectedReasoningMode(ReasoningMode.FAST.rawValue)
    }

    @Test
    fun whenSetChatScopedReasoningModeThenStateUpdatedAndNotPersisted() = runTest {
        testee = createManager()

        testee.setChatScopedReasoningMode(ReasoningMode.REASONING)

        assertEquals(ReasoningMode.REASONING, testee.modelState.value.chatScopedReasoningMode)
        verify(dataStore, never()).setSelectedReasoningMode(any())
    }

    @Test
    fun whenSetChatScopedReasoningModeToNullThenStateClearedAndNotPersisted() = runTest {
        testee = createManager()
        testee.setChatScopedReasoningMode(ReasoningMode.REASONING)
        assertEquals(ReasoningMode.REASONING, testee.modelState.value.chatScopedReasoningMode)

        testee.setChatScopedReasoningMode(null)

        assertNull(testee.modelState.value.chatScopedReasoningMode)
        verify(dataStore, never()).setSelectedReasoningMode(any())
    }

    @Test
    fun whenFetchModelsThenChatScopedReasoningModeIsPreserved() = runTest {
        // fetchModels rebuilds modelState — must not silently drop in-session chat-scoped picks.
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", accessTier = listOf("free"), entityHasAccess = true)),
            ),
        )
        testee = createManager()
        testee.setChatScopedReasoningMode(ReasoningMode.REASONING)
        assertEquals(ReasoningMode.REASONING, testee.modelState.value.chatScopedReasoningMode)

        testee.fetchModels()

        assertEquals(ReasoningMode.REASONING, testee.modelState.value.chatScopedReasoningMode)
    }

    @Test
    fun whenSelectReasoningModeAndModeIsGatedThenIgnoredAndNotPersisted() = runTest {
        // Defends against direct calls bypassing the picker's tap handler: gated modes must not be persistable.
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(ReasoningMode.FAST.rawValue)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel(
                        "m",
                        accessTier = listOf("free", "plus", "pro"),
                        entityHasAccess = true,
                        supportedReasoningEffort = listOf("none", "low"),
                        reasoningEffortAccess = listOf(
                            RemoteReasoningEffortAccess(id = "none", accessTier = listOf("free", "plus", "pro"), entityHasAccess = true),
                            RemoteReasoningEffortAccess(id = "low", accessTier = listOf("pro"), entityHasAccess = false),
                        ),
                    ),
                ),
            ),
        )
        testee = createManager()
        testee.fetchModels()
        assertEquals(ReasoningMode.FAST, testee.modelState.value.selectedReasoningMode)

        testee.selectReasoningMode(ReasoningMode.REASONING)

        assertEquals(ReasoningMode.FAST, testee.modelState.value.selectedReasoningMode)
        verify(dataStore, never()).setSelectedReasoningMode(ReasoningMode.REASONING.rawValue)
    }

    @Test
    fun whenGetResolvedReasoningEffortAndPersistedSupportedThenReturnsRaw() = runTest {
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(ReasoningMode.REASONING.rawValue)
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel(
                        "m",
                        accessTier = listOf("free"),
                        entityHasAccess = true,
                        supportedReasoningEffort = listOf("low"),
                    ),
                ),
            ),
        )
        testee = createManager()
        testee.fetchModels()

        assertEquals("low", testee.getResolvedReasoningEffort())
    }

    @Test
    fun whenGetResolvedReasoningEffortAndNoPersistedThenReturnsAutoPromotedEffort() = runTest {
        // Mirrors validateSelection's first-launch behavior for models: with nothing persisted,
        // we auto-select the first accessible mode and submit its effort.
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(null)
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel(
                        "m",
                        accessTier = listOf("free"),
                        entityHasAccess = true,
                        supportedReasoningEffort = listOf("low"),
                    ),
                ),
            ),
        )
        testee = createManager()
        testee.fetchModels()

        assertEquals("low", testee.getResolvedReasoningEffort())
    }

    @Test
    fun whenProviderPersistedAndNoModelPickedThenAModelOfThatProviderIsSelected() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(dataStore.getSelectedProvider()).thenReturn(ModelProvider.ANTHROPIC.name)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("gpt", provider = "openai"),
                    remoteModel("claude", provider = "anthropic"),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("claude", testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenBothAModelAndAProviderArePersistedThenTheModelWins() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("gpt", "gpt"))
        whenever(dataStore.getSelectedProvider()).thenReturn(ModelProvider.ANTHROPIC.name)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("gpt", provider = "openai"),
                    remoteModel("claude", provider = "anthropic"),
                ),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("gpt", testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenPersistedProviderHasNoModelInTheResponseThenTheDefaultIsUsedAndThePreferenceKept() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(dataStore.getSelectedProvider()).thenReturn(ModelProvider.MISTRAL.name)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(listOf(remoteModel("gpt", provider = "openai"))),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("gpt", testee.modelState.value.selectedModelId)
        verify(dataStore, never()).setSelectedProvider(null)
    }

    @Test
    fun whenProviderSelectedThenItReplacesAnyPersistedModelPick() = runTest {
        givenProviderStore()
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(
            AIChatModelsResponse(
                listOf(
                    remoteModel("gpt", provider = "openai"),
                    remoteModel("claude", provider = "anthropic"),
                ),
            ),
        )
        testee = createManager()
        testee.fetchModels()

        testee.selectProvider(ModelProvider.ANTHROPIC)

        verify(dataStore).setSelectedProvider(ModelProvider.ANTHROPIC.name)
        verify(dataStore, atLeastOnce()).setSelectedModel(null)
        assertEquals("claude", testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenModelSelectedThenTheProviderPreferenceIsCleared() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        testee = createManager()

        testee.selectModel(
            AIChatModel(
                id = "gpt",
                name = "gpt",
                displayName = "gpt",
                shortName = "gpt",
                accessTier = listOf("free"),
                isAccessible = true,
                provider = ModelProvider.OPENAI,
            ),
        )

        verify(dataStore).setSelectedProvider(null)
    }

    @Test
    fun whenModelChosenDuringColdImageLaunchThenExplicitChoiceWins() = runTest {
        val response = CompletableDeferred<AIChatModelsResponse>()
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        modelsService.stub {
            onBlocking { getModels(any(), anyOrNull(), anyOrNull()) } doSuspendableAnswer { response.await() }
        }
        testee = createManager()
        val imageLaunch = async { testee.ensureImageCapableModelSelected() }
        testScheduler.runCurrent()
        testee.selectModel(
            AIChatModel("text", "text", "text", "text", listOf("free"), isAccessible = true),
        )
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("text", "text"))

        response.complete(
            AIChatModelsResponse(listOf(remoteModel("text"), remoteModel("image", supportedTools = listOf(Tool.IMAGE_GENERATION.rawValue)))),
        )

        assertFalse(imageLaunch.await())
        assertEquals("text", testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenColdModelFetchFailsThenImageGenerationIsUnavailable() = runTest {
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenAnswer { throw java.io.IOException("offline") }
        testee = createManager()

        assertFalse(testee.ensureImageCapableModelSelected())
        assertTrue(testee.modelState.value.models.isEmpty())
    }

    @Test
    fun whenSelectedModelSupportsImageGenerationThenEnsureImageCapableReturnsTrueWithoutSwitching() = runTest {
        givenModels(remoteModel("text"), remoteModel("image", supportedTools = listOf(Tool.IMAGE_GENERATION.rawValue)))
        testee.selectModel(testee.modelState.value.models.first { it.id == "image" })
        clearInvocations(dataStore)

        assertTrue(testee.ensureImageCapableModelSelected())

        verify(dataStore, never()).setSelectedModel(anyOrNull())
        assertEquals("image", testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenSelectedModelCannotGenerateImagesThenEnsureImageCapableSwitchesToAccessibleCapableModel() = runTest {
        givenModels(
            remoteModel("text"),
            remoteModel("locked", accessTier = listOf("plus"), supportedTools = listOf(Tool.IMAGE_GENERATION.rawValue)),
            remoteModel("image", supportedTools = listOf(Tool.IMAGE_GENERATION.rawValue)),
        )
        testee.selectModel(testee.modelState.value.models.first { it.id == "text" })

        assertTrue(testee.ensureImageCapableModelSelected())

        assertEquals("image", testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenNoAccessibleModelCanGenerateImagesThenEnsureImageCapableReturnsFalseWithoutSwitching() = runTest {
        givenModels(
            remoteModel("text"),
            remoteModel("locked", accessTier = listOf("plus"), supportedTools = listOf(Tool.IMAGE_GENERATION.rawValue)),
        )
        testee.selectModel(testee.modelState.value.models.first { it.id == "text" })

        assertFalse(testee.ensureImageCapableModelSelected())

        assertEquals("text", testee.modelState.value.selectedModelId)
    }

    @Test
    fun whenResetModelForNewChatsThenEnsureImageCapableOnlyReportsAnImageModelAndLeavesTheSelection() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        givenModels(remoteModel("text"), remoteModel("image", supportedTools = listOf(Tool.IMAGE_GENERATION.rawValue)))
        val selectedBefore = testee.modelState.value.selectedModelId
        clearInvocations(dataStore)

        assertTrue(testee.ensureImageCapableModelSelected())

        assertEquals(selectedBefore, testee.modelState.value.selectedModelId)
        verify(dataStore, never()).setSelectedModel(anyOrNull())
        verify(dataStore, never()).setSelectedProvider(anyOrNull())
    }

    @Test
    fun whenResetModelForNewChatsAndOnlyALockedModelCanGenerateImagesThenEnsureImageCapableReturnsFalseAndLeavesTheSelection() = runTest {
        duckChatFeature.resetModelForNewChats().setRawStoredState(Toggle.State(enable = true))
        givenModels(
            remoteModel("text"),
            remoteModel("locked", accessTier = listOf("plus"), supportedTools = listOf(Tool.IMAGE_GENERATION.rawValue)),
        )
        val selectedBefore = testee.modelState.value.selectedModelId
        clearInvocations(dataStore)

        assertFalse(testee.ensureImageCapableModelSelected())

        assertEquals(selectedBefore, testee.modelState.value.selectedModelId)
        verify(dataStore, never()).setSelectedModel(anyOrNull())
    }

    private suspend fun givenModels(vararg models: RemoteAIChatModel) {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any(), anyOrNull(), anyOrNull())).thenReturn(AIChatModelsResponse(models.toList()))
        testee = createManager()
        testee.fetchModels()
    }

    /** [DuckChatDataStore] is a mock, so the provider key has to remember what was written to it. */
    private fun givenProviderStore() {
        var stored: String? = null
        dataStore.stub {
            onBlocking { getSelectedProvider() }.thenAnswer { stored }
            onBlocking { setSelectedProvider(anyOrNull()) }.thenAnswer {
                stored = it.arguments[0] as String?
                Unit
            }
        }
    }

    private companion object {
        const val NOW_MILLIS = 1_000_000L
        const val USER_DEFAULT_KEY = "defaultDuckaiModel"
    }
}
