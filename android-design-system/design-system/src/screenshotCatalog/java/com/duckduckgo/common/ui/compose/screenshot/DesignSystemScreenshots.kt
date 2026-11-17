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

package com.duckduckgo.common.ui.compose.screenshot

import com.duckduckgo.common.ui.compose.DaxStatusIndicatorScreenshot
import com.duckduckgo.common.ui.compose.appbars.DaxSearchTopAppBarScreenshot
import com.duckduckgo.common.ui.compose.appbars.DaxTopAppBarScreenshot
import com.duckduckgo.common.ui.compose.button.DaxBrandButtonScreenshot
import com.duckduckgo.common.ui.compose.button.DaxButtonScreenshot
import com.duckduckgo.common.ui.compose.button.DaxDestructiveGhostAltButtonScreenshot
import com.duckduckgo.common.ui.compose.button.DaxDestructiveGhostButtonScreenshot
import com.duckduckgo.common.ui.compose.button.DaxDestructiveGhostSecondaryButtonScreenshot
import com.duckduckgo.common.ui.compose.button.DaxDestructivePrimaryButtonScreenshot
import com.duckduckgo.common.ui.compose.button.DaxGhostButtonScreenshot
import com.duckduckgo.common.ui.compose.button.DaxIconButtonScreenshot
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButtonScreenshot
import com.duckduckgo.common.ui.compose.button.DaxSecondaryButtonScreenshot
import com.duckduckgo.common.ui.compose.cards.DaxCardScreenshot
import com.duckduckgo.common.ui.compose.cards.DaxSurfaceScreenshot
import com.duckduckgo.common.ui.compose.checkbox.DaxCheckboxScreenshot
import com.duckduckgo.common.ui.compose.contextmenu.DaxContextMenuScreenshot
import com.duckduckgo.common.ui.compose.contextmenu.DaxDefaultContextMenuItemScreenshot
import com.duckduckgo.common.ui.compose.contextmenu.DaxIconContextMenuItemScreenshot
import com.duckduckgo.common.ui.compose.contextmenu.DaxInsetContextMenuItemScreenshot
import com.duckduckgo.common.ui.compose.dialog.DaxAlertDialogScreenshot
import com.duckduckgo.common.ui.compose.dialog.DaxDestructiveStackedButtonsScreenshot
import com.duckduckgo.common.ui.compose.dialog.DaxPrimaryStackedButtonsScreenshot
import com.duckduckgo.common.ui.compose.dialog.DaxRadioOptionsScreenshot
import com.duckduckgo.common.ui.compose.dialog.DaxTextAlertDialogScreenshot
import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDividerScreenshot
import com.duckduckgo.common.ui.compose.divider.DaxVerticalDividerScreenshot
import com.duckduckgo.common.ui.compose.layout.DaxScaffoldScreenshot
import com.duckduckgo.common.ui.compose.listitem.DaxOneLineListItemScreenshot
import com.duckduckgo.common.ui.compose.listitem.DaxSectionHeaderScreenshot
import com.duckduckgo.common.ui.compose.listitem.DaxSettingsListItemScreenshot
import com.duckduckgo.common.ui.compose.listitem.DaxTwoLineListItemScreenshot
import com.duckduckgo.common.ui.compose.message.DaxAppTPBannerScreenshot
import com.duckduckgo.common.ui.compose.message.DaxBannerMessageScreenshot
import com.duckduckgo.common.ui.compose.message.DaxNotifyMeScreenshot
import com.duckduckgo.common.ui.compose.message.remote.DaxBigSingleActionMessageScreenshot
import com.duckduckgo.common.ui.compose.message.remote.DaxBigTwoActionsMessageScreenshot
import com.duckduckgo.common.ui.compose.message.remote.DaxMediumMessageScreenshot
import com.duckduckgo.common.ui.compose.message.remote.DaxPromoSingleActionMessageScreenshot
import com.duckduckgo.common.ui.compose.message.remote.DaxSmallMessageScreenshot
import com.duckduckgo.common.ui.compose.panel.DaxAlertPanelScreenshot
import com.duckduckgo.common.ui.compose.panel.DaxInfoPanelScreenshot
import com.duckduckgo.common.ui.compose.pill.DaxPillScreenshot
import com.duckduckgo.common.ui.compose.progress.DaxProgressSpinnerScreenshot
import com.duckduckgo.common.ui.compose.radiobutton.DaxRadioButtonScreenshot
import com.duckduckgo.common.ui.compose.sheets.DaxActionBottomSheetDialogScreenshot
import com.duckduckgo.common.ui.compose.sheets.DaxBottomSheetDialogScreenshot
import com.duckduckgo.common.ui.compose.sheets.DaxPromoBottomSheetDialogScreenshot
import com.duckduckgo.common.ui.compose.skeleton.DaxSkeletonListItemScreenshot
import com.duckduckgo.common.ui.compose.skeleton.DaxSkeletonSectionHeaderScreenshot
import com.duckduckgo.common.ui.compose.slider.DaxSliderScreenshot
import com.duckduckgo.common.ui.compose.snackbar.DaxSnackbarScreenshot
import com.duckduckgo.common.ui.compose.switch.DaxSwitchScreenshot
import com.duckduckgo.common.ui.compose.template.DaxPageHeaderScreenshot
import com.duckduckgo.common.ui.compose.text.DaxTextScreenshot
import com.duckduckgo.common.ui.compose.textfield.DaxSecureTextFieldScreenshot
import com.duckduckgo.common.ui.compose.textfield.DaxTextFieldScreenshot

/** Every design system screenshot. Each screenshot testing library renders all of them. */
val designSystemScreenshots: List<ScreenshotCase> = listOf(
    DaxActionBottomSheetDialogScreenshot,
    DaxAlertDialogScreenshot,
    DaxAlertPanelScreenshot,
    DaxAppTPBannerScreenshot,
    DaxBannerMessageScreenshot,
    DaxBigSingleActionMessageScreenshot,
    DaxBigTwoActionsMessageScreenshot,
    DaxBottomSheetDialogScreenshot,
    DaxBrandButtonScreenshot,
    DaxButtonScreenshot,
    DaxCardScreenshot,
    DaxCheckboxScreenshot,
    DaxContextMenuScreenshot,
    DaxDefaultContextMenuItemScreenshot,
    DaxDestructiveGhostAltButtonScreenshot,
    DaxDestructiveGhostButtonScreenshot,
    DaxDestructiveGhostSecondaryButtonScreenshot,
    DaxDestructivePrimaryButtonScreenshot,
    DaxDestructiveStackedButtonsScreenshot,
    DaxGhostButtonScreenshot,
    DaxHorizontalDividerScreenshot,
    DaxIconButtonScreenshot,
    DaxIconContextMenuItemScreenshot,
    DaxInfoPanelScreenshot,
    DaxInsetContextMenuItemScreenshot,
    DaxMediumMessageScreenshot,
    DaxNotifyMeScreenshot,
    DaxOneLineListItemScreenshot,
    DaxPageHeaderScreenshot,
    DaxPillScreenshot,
    DaxPrimaryButtonScreenshot,
    DaxPrimaryStackedButtonsScreenshot,
    DaxProgressSpinnerScreenshot,
    DaxPromoBottomSheetDialogScreenshot,
    DaxPromoSingleActionMessageScreenshot,
    DaxRadioButtonScreenshot,
    DaxRadioOptionsScreenshot,
    DaxScaffoldScreenshot,
    DaxSearchTopAppBarScreenshot,
    DaxSecondaryButtonScreenshot,
    DaxSectionHeaderScreenshot,
    DaxSecureTextFieldScreenshot,
    DaxSettingsListItemScreenshot,
    DaxSkeletonListItemScreenshot,
    DaxSkeletonSectionHeaderScreenshot,
    DaxSliderScreenshot,
    DaxSmallMessageScreenshot,
    DaxSnackbarScreenshot,
    DaxStatusIndicatorScreenshot,
    DaxSurfaceScreenshot,
    DaxSwitchScreenshot,
    DaxTextScreenshot,
    DaxTextAlertDialogScreenshot,
    DaxTextFieldScreenshot,
    DaxTopAppBarScreenshot,
    DaxTwoLineListItemScreenshot,
    DaxVerticalDividerScreenshot,
)
