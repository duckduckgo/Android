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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.duckchat.impl.store.SelectedModel
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.api.model.Entitlement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealDuckAiModelManagerTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val modelsService: DuckAiModelsService = mock()
    private val dataStore: DuckChatDataStore = mock()
    private val subscriptions: Subscriptions = mock()
    private val duckAiHostProvider: DuckAiHostProvider = mock()

    private val entitlementFlow = MutableSharedFlow<Set<Entitlement>>()

    private lateinit var testee: RealDuckAiModelManager

    @Before
    fun setUp() {
        whenever(subscriptions.getEntitlements()).thenReturn(entitlementFlow)
        whenever(duckAiHostProvider.getHost()).thenReturn("duck.ai")
    }

    private fun createManager(): RealDuckAiModelManager {
        return RealDuckAiModelManager(
            modelsService = modelsService,
            dataStore = dataStore,
            subscriptions = subscriptions,
            duckAiHostProvider = duckAiHostProvider,
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
    fun whenFetchModelsThenModelsResolvedAndStateUpdated() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any())).thenReturn(
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
        whenever(modelsService.getModels(any())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", accessTier = emptyList(), entityHasAccess = true)),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertTrue(testee.modelState.value.models[0].isAccessible)
    }

    @Test
    fun whenEmptyAccessTierAndNoEntityAccessThenModelNotAccessible() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", accessTier = emptyList(), entityHasAccess = false)),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertFalse(testee.modelState.value.models[0].isAccessible)
    }

    @Test
    fun whenNonEmptyAccessTierAndTierMatchesThenAccessible() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))
        whenever(modelsService.getModels(any())).thenReturn(
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
        whenever(modelsService.getModels(any())).thenReturn(
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
        whenever(modelsService.getModels(any())).thenReturn(
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
        whenever(modelsService.getModels(any())).thenReturn(
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
    }

    @Test
    fun whenSelectedModelStillAccessibleThenSelectionPreserved() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("id", "model"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))
        whenever(modelsService.getModels(any())).thenReturn(
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
        whenever(modelsService.getModels(any())).thenReturn(
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
        verify(dataStore).setSelectedModel(SelectedModel("id2", "id2"))
    }

    @Test
    fun whenSelectedModelRemovedThenFallsBackToFirstAccessible() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("removed", "removed"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any())).thenReturn(
            AIChatModelsResponse(
                listOf(remoteModel("id", accessTier = listOf("free"), entityHasAccess = true)),
            ),
        )

        testee = createManager()
        testee.fetchModels()

        assertEquals("id", testee.modelState.value.selectedModelId)
        verify(dataStore).setSelectedModel(SelectedModel("id", "id"))
    }

    @Test
    fun whenNoAccessibleModelsThenSelectedModelIdIsNull() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any())).thenReturn(
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
        whenever(modelsService.getModels(any())).thenThrow(RuntimeException("Network error"))

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
        whenever(modelsService.getModels(any())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.FREE, testee.modelState.value.userTier)
    }

    @Test
    fun whenSubscriptionActiveWithDuckAiPlusThenTierIsPlus() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))
        whenever(modelsService.getModels(any())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.PLUS, testee.modelState.value.userTier)
    }

    @Test
    fun whenSubscriptionActiveWithDuckAiProThenTierIsPro() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "pro", product = "Duck.ai"))))
        whenever(modelsService.getModels(any())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.PRO, testee.modelState.value.userTier)
    }

    @Test
    fun whenSubscriptionActiveWithoutDuckAiPlusThenTierIsFree() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "subscriber", product = "Network Protection"))))
        whenever(modelsService.getModels(any())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.FREE, testee.modelState.value.userTier)
    }

    @Test
    fun whenDuckAiEntitlementHasUnknownTierNameThenTierIsFree() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "premium", product = "Duck.ai"))))
        whenever(modelsService.getModels(any())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.FREE, testee.modelState.value.userTier)
    }

    @Test
    fun whenSubscriptionActiveWithEmptyEntitlementsThenTierIsFree() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(emptySet()))
        whenever(modelsService.getModels(any())).thenReturn(AIChatModelsResponse(emptyList()))

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
        whenever(modelsService.getModels(any())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.PRO, testee.modelState.value.userTier)
    }

    @Test
    fun whenSubscriptionExpiredThenTierIsFree() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.EXPIRED)
        whenever(modelsService.getModels(any())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.FREE, testee.modelState.value.userTier)
    }

    @Test
    fun whenSubscriptionInGracePeriodWithDuckAiPlusThenTierIsPlus() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.GRACE_PERIOD)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(Entitlement(name = "plus", product = "Duck.ai"))))
        whenever(modelsService.getModels(any())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.PLUS, testee.modelState.value.userTier)
    }

    @Test
    fun whenSubscriptionStatusThrowsThenTierDefaultsToFree() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenThrow(RuntimeException("Error"))
        whenever(modelsService.getModels(any())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        testee.fetchModels()

        assertEquals(UserTier.FREE, testee.modelState.value.userTier)
    }

    @Test
    fun whenFetchModelsThenProviderResolvedFromRemoteFields() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any())).thenReturn(
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
    fun whenEntitlementsChangeThenModelsFetched() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any())).thenReturn(
            AIChatModelsResponse(listOf(remoteModel("id", accessTier = listOf("free"), entityHasAccess = true))),
        )

        testee = createManager()

        entitlementFlow.emit(setOf(Entitlement(name = "plus", product = "Duck.ai")))

        assertEquals(1, testee.modelState.value.models.size)
    }

    @Test
    fun whenModelHasSupportedFileTypesThenResolvedModel() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any())).thenReturn(
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
        whenever(modelsService.getModels(any())).thenReturn(
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
        whenever(modelsService.getModels(any())).thenReturn(
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
        whenever(modelsService.getModels(any())).thenReturn(
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
        whenever(modelsService.getModels(any())).thenReturn(
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
        whenever(modelsService.getModels(any())).thenReturn(
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
        whenever(modelsService.getModels(any())).thenReturn(
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
    )

    @Test
    fun whenRemoteHasUnknownReasoningEffortThenUnknownDropped() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(null)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any())).thenReturn(
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
    fun whenRestoreCachedSelectionThrowsThenEntitlementCollectorStillStarts() = runTest {
        whenever(dataStore.getSelectedModel()).thenThrow(RuntimeException("disk error"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any())).thenReturn(AIChatModelsResponse(emptyList()))

        testee = createManager()
        entitlementFlow.emit(emptySet())

        verify(modelsService).getModels(any())
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
        whenever(modelsService.getModels(any())).thenReturn(
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
    fun whenFetchAndSelectedModelDoesNotSupportPersistedModeThenStaleClearedFromStateAndStore() = runTest {
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(ReasoningMode.EXTENDED_REASONING.rawValue)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any())).thenReturn(
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

        assertNull(testee.modelState.value.selectedReasoningMode)
        verify(dataStore).setSelectedReasoningMode(null)
    }

    @Test
    fun whenSelectModelClearsStaleReasoningWhenNewModelLacksSupport() = runTest {
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

        assertNull(testee.modelState.value.selectedReasoningMode)
        verify(dataStore).setSelectedReasoningMode(null)
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
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any())).thenReturn(
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
        whenever(modelsService.getModels(any())).thenReturn(
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

        testee.selectReasoningMode(ReasoningMode.FAST)

        assertNull(testee.modelState.value.selectedReasoningMode)
        verify(dataStore, never()).setSelectedReasoningMode(ReasoningMode.FAST.rawValue)
    }

    @Test
    fun whenGetResolvedReasoningEffortAndPersistedSupportedThenReturnsRaw() = runTest {
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(ReasoningMode.REASONING.rawValue)
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any())).thenReturn(
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
    fun whenGetResolvedReasoningEffortAndNoPersistedThenReturnsNull() = runTest {
        whenever(dataStore.getSelectedReasoningMode()).thenReturn(null)
        whenever(dataStore.getSelectedModel()).thenReturn(SelectedModel("m", "M"))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.INACTIVE)
        whenever(modelsService.getModels(any())).thenReturn(
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

        assertNull(testee.getResolvedReasoningEffort())
    }
}
