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

package com.duckduckgo.app.dev.settings.quiz

data class QuestionSpec(
    val id: QuestionId,
    val title: String,
    val viewApiName: String,
    val composeApiName: String,
    val caveat: String? = null,
    val credits: List<Credit>,
)

/** [component] is null for the main component, otherwise the sub-part (e.g. its dropdown) built by [name]. */
data class Credit(val name: String, val component: String? = null)

object ComposeQuizRegistry {
    val questions: List<QuestionSpec> = listOf(
        QuestionSpec(QuestionId.BrandButton, "Brand button", "DaxButtonBrand", "DaxBrandButton", credits = listOf(Credit("Kyriakos"))),
        QuestionSpec(QuestionId.PrimaryButton, "Primary button", "DaxButtonPrimary", "DaxPrimaryButton", credits = listOf(Credit("Mike"))),
        QuestionSpec(QuestionId.SecondaryButton, "Secondary button", "DaxButtonSecondary", "DaxSecondaryButton", credits = listOf(Credit("Mike"))),
        QuestionSpec(
            QuestionId.DestructivePrimaryButton,
            "Destructive primary button",
            "DaxButtonDestructive",
            "DaxDestructivePrimaryButton",
            credits = listOf(Credit("Mike")),
        ),
        QuestionSpec(QuestionId.GhostButton, "Ghost button", "DaxButtonGhost", "DaxGhostButton", credits = listOf(Credit("Mike"))),
        QuestionSpec(
            QuestionId.DestructiveGhostSecondaryButton,
            "Destructive ghost secondary button",
            "DaxButtonDestructiveSecondary",
            "DaxDestructiveGhostSecondaryButton",
            credits = listOf(Credit("Mike")),
        ),
        QuestionSpec(
            QuestionId.DestructiveGhostButton,
            "Destructive ghost button",
            "DaxButtonGhostDestructive",
            "DaxDestructiveGhostButton",
            credits = listOf(Credit("Mike")),
        ),
        QuestionSpec(
            QuestionId.DestructiveGhostAltButton,
            "Destructive ghost alt button",
            "DaxButtonGhostAlt",
            "DaxDestructiveGhostAltButton",
            credits = listOf(Credit("Mike")),
        ),
        QuestionSpec(QuestionId.IconButton, "Icon button", "DaxIconButton", "DaxIconButton", credits = listOf(Credit("Mike and Gerard"))),
        QuestionSpec(QuestionId.Switch, "Switch", "DaxSwitch", "DaxSwitch", credits = listOf(Credit("Catalin"))),
        QuestionSpec(QuestionId.RadioButton, "Radio button", "RadioButton", "DaxRadioButton", credits = listOf(Credit("Domen"))),
        QuestionSpec(
            QuestionId.Checkbox,
            "Checkbox",
            "CheckBox",
            "DaxCheckbox",
            "This compares platform View CheckBox vs ADS Compose DaxCheckbox.",
            credits = listOf(Credit("Domen")),
        ),
        QuestionSpec(
            QuestionId.Slider,
            "Slider",
            "Slider",
            "DaxSlider",
            "This compares Material Slider vs ADS Compose DaxSlider.",
            credits = listOf(Credit("Ondrej")),
        ),
        QuestionSpec(QuestionId.TextField, "Text field", "DaxTextInput", "DaxTextField", credits = listOf(Credit("Domen"))),
        QuestionSpec(
            QuestionId.OneLineListItem,
            "One-line list item",
            "OneLineListItem",
            "DaxOneLineListItem",
            credits = listOf(Credit("Mike"), Credit("Gerard", "Dropdown")),
        ),
        QuestionSpec(
            QuestionId.TwoLineListItem,
            "Two-line list item",
            "TwoLineListItem",
            "DaxTwoLineListItem",
            credits = listOf(Credit("Mike"), Credit("Gerard", "Dropdown")),
        ),
        QuestionSpec(QuestionId.SettingsListItem, "Settings list item", "SettingsListItem", "DaxSettingsListItem", credits = listOf(Credit("Mike"))),
        QuestionSpec(QuestionId.SectionHeader, "Section header", "SectionHeaderListItem", "DaxSectionHeader", credits = listOf(Credit("Catalin"))),
        QuestionSpec(QuestionId.StatusIndicator, "Status indicator", "StatusIndicatorView", "DaxStatusIndicator", credits = listOf(Credit("Gerard"))),
        QuestionSpec(QuestionId.InfoPanel, "Info panel", "InfoPanel", "DaxInfoPanel", credits = listOf(Credit("Catalin"))),
        QuestionSpec(
            QuestionId.ProgressSpinner,
            "Progress spinner",
            "CircularProgressIndicator",
            "DaxProgressSpinner",
            "This compares Material CircularProgressIndicator vs ADS Compose DaxProgressSpinner.",
            credits = listOf(Credit("Domen")),
        ),
        QuestionSpec(
            QuestionId.SkeletonListHeader,
            "Skeleton list/header",
            "SkeletonView + ShimmerFrameLayout",
            "DaxSkeletonListItem + DaxSkeletonSectionHeader",
            credits = listOf(Credit("Gerard")),
        ),
        QuestionSpec(QuestionId.TextDialog, "Text alert dialog", "TextAlertDialogBuilder", "DaxTextAlertDialog", credits = listOf(Credit("Ondrej"))),
        QuestionSpec(
            QuestionId.SingleChoiceDialog,
            "Single choice dialog",
            "RadioListAlertDialogBuilder",
            "DaxAlertDialog + DaxRadioOptions",
            credits = listOf(Credit("Ondrej")),
        ),
        QuestionSpec(
            QuestionId.StackedDialog,
            "Stacked dialog",
            "StackedAlertDialogBuilder",
            "DaxAlertDialog + DaxPrimaryStackedButtons",
            credits = listOf(Credit("Ondrej")),
        ),
        QuestionSpec(
            QuestionId.ActionBottomSheetWithTitle,
            "Action bottom sheet with title and icons",
            "ActionBottomSheetDialog",
            "DaxActionBottomSheetDialog",
            credits = listOf(Credit("Gerard")),
        ),
        QuestionSpec(
            QuestionId.PromoBottomSheetWithImage,
            "Promo bottom sheet with image",
            "PromoBottomSheetDialog",
            "DaxPromoBottomSheetDialog",
            credits = listOf(Credit("Catalin")),
        ),
        QuestionSpec(QuestionId.Snackbar, "Snackbar", "Snackbar", "DaxSnackbar", credits = listOf(Credit("Ondrej"))),
        QuestionSpec(QuestionId.SmallMessage, "Small remote message", "MessageCta", "DaxSmallMessage", credits = listOf(Credit("Catalin"))),
        QuestionSpec(QuestionId.MediumMessage, "Medium remote message", "MessageCta", "DaxMediumMessage", credits = listOf(Credit("Catalin"))),
        QuestionSpec(
            QuestionId.BigSingleActionMessage,
            "Big single action remote message",
            "MessageCta",
            "DaxBigSingleActionMessage",
            credits = listOf(Credit("Catalin")),
        ),
        QuestionSpec(
            QuestionId.BigTwoActionsMessage,
            "Big two actions remote message",
            "MessageCta",
            "DaxBigTwoActionsMessage",
            credits = listOf(Credit("Catalin")),
        ),
        QuestionSpec(
            QuestionId.PromoSingleActionMessage,
            "Promo single action remote message",
            "MessageCta",
            "DaxPromoSingleActionMessage",
            credits = listOf(Credit("Catalin")),
        ),
    )

    private val questionsById: Map<QuestionId, QuestionSpec> = questions.associateBy(QuestionSpec::id)
    private val renderers: Map<QuestionId, QuizRenderer> =
        buttonQuizRenderers + inputQuizRenderers + listQuizRenderers + statusQuizRenderers + dialogQuizRenderers + messagingQuizRenderers

    init {
        require(questionsById.size == QuestionId.entries.size) { "Quiz question metadata must cover each question ID exactly once" }
        require(renderers.keys == questionsById.keys) { "Quiz renderers must cover the same IDs as question metadata" }
    }

    fun question(id: QuestionId): QuestionSpec = questionsById.getValue(id)

    fun renderer(id: QuestionId): QuizRenderer = renderers[id]
        ?: throw IllegalArgumentException("No renderer registered for $id")
}
