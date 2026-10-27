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

enum class QuizSection {
    Buttons,
    InputsAndSelection,
    Lists,
    StatusAndLoading,
    Dialogs,
    Messaging,
}

enum class QuestionId(val section: QuizSection) {
    BrandButton(QuizSection.Buttons),
    PrimaryButton(QuizSection.Buttons),
    SecondaryButton(QuizSection.Buttons),
    DestructivePrimaryButton(QuizSection.Buttons),
    GhostButton(QuizSection.Buttons),
    DestructiveGhostSecondaryButton(QuizSection.Buttons),
    DestructiveGhostButton(QuizSection.Buttons),
    DestructiveGhostAltButton(QuizSection.Buttons),
    IconButton(QuizSection.InputsAndSelection),
    Switch(QuizSection.InputsAndSelection),
    RadioButton(QuizSection.InputsAndSelection),
    Checkbox(QuizSection.InputsAndSelection),
    Slider(QuizSection.InputsAndSelection),
    TextField(QuizSection.InputsAndSelection),
    OneLineListItem(QuizSection.Lists),
    TwoLineListItem(QuizSection.Lists),
    SettingsListItem(QuizSection.Lists),
    SectionHeader(QuizSection.Lists),
    StatusIndicator(QuizSection.StatusAndLoading),
    InfoPanel(QuizSection.StatusAndLoading),
    ProgressSpinner(QuizSection.StatusAndLoading),
    SkeletonListHeader(QuizSection.StatusAndLoading),
    TextDialog(QuizSection.Dialogs),
    SingleChoiceDialog(QuizSection.Dialogs),
    StackedDialog(QuizSection.Dialogs),
    ActionBottomSheetWithTitle(QuizSection.Dialogs),
    PromoBottomSheetWithImage(QuizSection.Dialogs),
    Snackbar(QuizSection.Messaging),
    SmallMessage(QuizSection.Messaging),
    MediumMessage(QuizSection.Messaging),
    BigSingleActionMessage(QuizSection.Messaging),
    BigTwoActionsMessage(QuizSection.Messaging),
    PromoSingleActionMessage(QuizSection.Messaging),
}

enum class GuessResult {
    Unanswered,
    Correct,
    Incorrect,
}

data class QuizQuestion(
    val id: QuestionId,
    val section: QuizSection,
)

data class QuestionPresentation(
    val question: QuizQuestion,
    val composeOnTop: Boolean,
)

typealias QuizSessionSavedState = List<Any>
