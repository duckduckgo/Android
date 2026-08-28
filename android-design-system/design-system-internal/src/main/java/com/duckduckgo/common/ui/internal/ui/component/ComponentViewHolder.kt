/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.ui.internal.ui.component

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView
import coil3.compose.rememberAsyncImagePainter
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.duckduckgo.common.ui.compose.DaxAction
import com.duckduckgo.common.ui.compose.Status
import com.duckduckgo.common.ui.compose.appbars.DaxSearchTopAppBar
import com.duckduckgo.common.ui.compose.appbars.DaxTopAppBar
import com.duckduckgo.common.ui.compose.appbars.DaxTopAppBarNavigationIcon
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.cards.DaxCard
import com.duckduckgo.common.ui.compose.cards.DaxSurface
import com.duckduckgo.common.ui.compose.checkbox.DaxCheckbox
import com.duckduckgo.common.ui.compose.contextmenu.DaxContextMenu
import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider
import com.duckduckgo.common.ui.compose.divider.DaxVerticalDivider
import com.duckduckgo.common.ui.compose.layout.DaxScaffold
import com.duckduckgo.common.ui.compose.listitem.DaxListItemIconBackground
import com.duckduckgo.common.ui.compose.listitem.DaxListItemIconSize
import com.duckduckgo.common.ui.compose.listitem.DaxListItemTrailingIconSize
import com.duckduckgo.common.ui.compose.listitem.DaxOneLineListItem
import com.duckduckgo.common.ui.compose.listitem.DaxSettingsListItem
import com.duckduckgo.common.ui.compose.listitem.DaxTwoLineListItem
import com.duckduckgo.common.ui.compose.message.remote.DaxBigSingleActionMessage
import com.duckduckgo.common.ui.compose.message.remote.DaxBigTwoActionsMessage
import com.duckduckgo.common.ui.compose.message.remote.DaxMediumMessage
import com.duckduckgo.common.ui.compose.message.remote.DaxPromoSingleActionMessage
import com.duckduckgo.common.ui.compose.message.remote.DaxSmallMessage
import com.duckduckgo.common.ui.compose.panel.DaxAlertPanel
import com.duckduckgo.common.ui.compose.panel.DaxInfoPanel
import com.duckduckgo.common.ui.compose.progress.DaxProgressSpinner
import com.duckduckgo.common.ui.compose.radiobutton.DaxRadioButton
import com.duckduckgo.common.ui.compose.snackbar.DaxSnackbar
import com.duckduckgo.common.ui.compose.switch.DaxSwitch
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.internal.R
import com.duckduckgo.common.ui.internal.ui.setupThemedComposeView
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.MessageCta
import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.common.ui.view.MessageCta.MessageType.REMOTE_PROMO_MESSAGE
import com.duckduckgo.common.ui.view.PopupMenuItemView
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.common.ui.view.listitem.SectionHeaderListItem
import com.duckduckgo.common.ui.view.listitem.SettingsListItem
import com.duckduckgo.common.ui.view.listitem.TwoLineListItem
import com.duckduckgo.common.utils.extensions.html
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.TriangleEdgeTreatment
import com.google.android.material.snackbar.Snackbar
import com.duckduckgo.mobile.android.R as CommonR

sealed class ComponentViewHolder(val view: View) : RecyclerView.ViewHolder(view) {

    open fun bind(component: Component) {
        // Override in subclass if needed.
    }

    class ButtonComponentViewHolder(parent: ViewGroup) :
        ComponentViewHolder(inflate(parent, R.layout.component_buttons))

    class TopAppBarComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_top_app_bar)) {
        override fun bind(component: Component) {
            view.setupThemedComposeView(id = R.id.composeDaxTopAppBar, isDarkTheme = isDarkTheme) {
                val searchState = rememberTextFieldState()
                var searchActive by remember { mutableStateOf(false) }

                BackHandler(enabled = searchActive) { searchActive = false }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DaxSearchTopAppBar(
                        title = "Search bar",
                        navigationIcon = DaxTopAppBarNavigationIcon.Back { },
                        searchActive = searchActive,
                        searchState = searchState,
                        searchPlaceholder = "Search…",
                        onSearchBack = { searchActive = false },
                        actions = {
                            DaxIconButton(
                                onClick = { },
                                iconPainter = painterResource(CommonR.drawable.ic_ai_chat_24_solid_color),
                                contentDescription = "Duck.ai",
                            )
                            DaxIconButton(
                                onClick = { searchActive = true },
                                iconPainter = painterResource(CommonR.drawable.ic_find_search_24),
                                contentDescription = "Search",
                            )
                        },
                    )
                    DaxTopAppBar(
                        title = "Top bar",
                        shadow = true,
                        navigationIcon = DaxTopAppBarNavigationIcon.Close { },
                        actions = {
                            DaxIconButton(
                                onClick = { },
                                iconPainter = painterResource(CommonR.drawable.ic_add_24),
                                contentDescription = "Add",
                            )
                            DaxIconButton(
                                onClick = { },
                                iconPainter = painterResource(CommonR.drawable.ic_ai_chat_24_solid_color),
                                contentDescription = "Duck.ai",
                            )
                        },
                    )
                }
            }
        }
    }

    class SwitchComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_switch)) {
        override fun bind(component: Component) {
            view.setupThemedComposeView(id = R.id.compose_dax_switch_one, isDarkTheme = isDarkTheme) {
                var isChecked by remember { mutableStateOf(false) }

                DaxSwitch(
                    checked = isChecked,
                    onCheckedChange = { enabled ->
                        isChecked = enabled
                    },
                )
            }
            view.setupThemedComposeView(id = R.id.compose_dax_switch_two, isDarkTheme = isDarkTheme) {
                var isChecked by remember { mutableStateOf(true) }

                DaxSwitch(
                    checked = isChecked,
                    onCheckedChange = { enabled ->
                        isChecked = enabled
                    },
                )
            }
            view.setupThemedComposeView(id = R.id.compose_dax_switch_three, isDarkTheme = isDarkTheme) {
                DaxSwitch(checked = false, onCheckedChange = {}, enabled = false)
            }
            view.setupThemedComposeView(id = R.id.compose_dax_switch_four, isDarkTheme = isDarkTheme) {
                DaxSwitch(checked = true, onCheckedChange = {}, enabled = false)
            }
        }
    }

    class RadioButtonComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_radio_button)) {
        override fun bind(component: Component) {
            view.setupThemedComposeView(id = R.id.compose_dax_radio_button, isDarkTheme = isDarkTheme) {
                var indexSelected by remember { mutableIntStateOf(0) }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DaxRadioButton(
                        selected = indexSelected == 0,
                        onClick = { indexSelected = 0 },
                    )

                    DaxRadioButton(
                        selected = indexSelected == 1,
                        onClick = { indexSelected = 1 },
                    )

                    DaxRadioButton(selected = false, onClick = {}, enabled = false)

                    DaxRadioButton(selected = true, onClick = {}, enabled = false)
                }
            }
        }
    }

    class CheckboxComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_checkbox)) {
        override fun bind(component: Component) {
            view.setupThemedComposeView(id = R.id.compose_dax_checkbox_one, isDarkTheme = isDarkTheme) {
                var isChecked by remember { mutableStateOf(false) }

                DaxCheckbox(
                    checked = isChecked,
                    onCheckedChange = { enabled ->
                        isChecked = enabled
                    },
                )
            }
            view.setupThemedComposeView(id = R.id.compose_dax_checkbox_two, isDarkTheme = isDarkTheme) {
                var isChecked by remember { mutableStateOf(true) }

                DaxCheckbox(
                    checked = isChecked,
                    onCheckedChange = { enabled ->
                        isChecked = enabled
                    },
                )
            }
            view.setupThemedComposeView(id = R.id.compose_dax_checkbox_three, isDarkTheme = isDarkTheme) {
                DaxCheckbox(
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                )
            }
            view.setupThemedComposeView(id = R.id.compose_dax_checkbox_four, isDarkTheme = isDarkTheme) {
                DaxCheckbox(
                    checked = true,
                    enabled = false,
                    onCheckedChange = {},
                )
            }
            view.setupThemedComposeView(id = R.id.compose_dax_checkbox_five, isDarkTheme = isDarkTheme) {
                var isChecked by remember { mutableStateOf(false) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    DaxCheckbox(
                        checked = isChecked,
                        onCheckedChange = { enabled ->
                            isChecked = enabled
                        },
                    )
                    DaxText(text = stringResource(CommonR.string.text_dialog_checkbox))
                }
            }
        }
    }

    class SliderComponentViewHolder(parent: ViewGroup) :
        ComponentViewHolder(inflate(parent, R.layout.component_slider))

    class InfoPanelComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_info_panel)) {

        init {
            view.setupThemedComposeView(R.id.info_panel_tooltip_compose, isDarkTheme = isDarkTheme) {
                DaxInfoPanel(
                    body = "This is a Tooltip Compose Info Panel, interesting information can be shown here",
                )
            }

            view.setupThemedComposeView(R.id.info_panel_alert_compose, isDarkTheme = isDarkTheme) {
                DaxAlertPanel(
                    body = "This is an Alert Compose Info Panel, warning information can be shown here",
                )
            }

            view.setupThemedComposeView(R.id.info_panel_link_compose, isDarkTheme = isDarkTheme) {
                DaxInfoPanel(
                    body = buildAnnotatedString {
                        append("This info panel has a link. Visit ")
                        withLink(LinkAnnotation.Url("https://duckduckgo.com")) {
                            append("duckduckgo.com")
                        }
                        append(" to learn more.")
                    },
                )
            }
        }
    }

    class RemoteMessageComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_remote_message)) {
        override fun bind(component: Component) {
            val smallMessage = Message(title = "Small Message", subtitle = "Body text goes here. This component doesn't have buttons")
            val bigSingleMessage = Message(
                topIllustration = CommonR.drawable.ic_announce,
                title = "Big Single Message",
                subtitle = "Body text goes here. This component has one button",
                action = "Primary",
            )
            val bigTwoActionsMessage = Message(
                topIllustration = CommonR.drawable.ic_ddg_announce,
                title = "Big Two Actions Message",
                subtitle = "Body text goes here. This component has two buttons",
                action = "Primary",
                action2 = "Secondary",
            )

            val bigTwoActionsUpdateMessage = Message(
                topIllustration = CommonR.drawable.ic_app_update,
                title = "Big Two Actions Message",
                subtitle = "Body text goes here. This component has two buttons an showcases and app update",
                action = "Primary",
                action2 = "Secondary",
            )

            val mediumMessage = Message(
                topIllustration = CommonR.drawable.ic_critical_update,
                title = "Medium Message",
                subtitle = "Body text goes here. This component doesn't have buttons",
            )

            val promoSingleMessage = Message(
                middleIllustration = CommonR.drawable.promo_mac_and_windows,
                title = "Promo Single Action Message",
                subtitle = "Body text goes here. This component has one promo button and supports <b>bold</b> text",
                promoAction = "Promo Link",
                messageType = REMOTE_PROMO_MESSAGE,
            )

            view.findViewById<MessageCta>(R.id.small_remote_message).apply {
                setMessage(smallMessage)
            }

            view.findViewById<MessageCta>(R.id.medium_remote_message).apply {
                setMessage(mediumMessage)
            }

            view.findViewById<MessageCta>(R.id.big_single_remote_message).apply {
                setMessage(bigSingleMessage)
            }

            view.findViewById<MessageCta>(R.id.big_two_actions_remote_message).apply {
                setMessage(bigTwoActionsMessage)
            }

            view.findViewById<MessageCta>(R.id.big_two_actions_update_remote_message).apply {
                setMessage(bigTwoActionsUpdateMessage)
            }

            view.findViewById<MessageCta>(R.id.promo_single_remote_message).apply {
                setMessage(promoSingleMessage)
            }

            view.setupThemedComposeView(R.id.promo_single_remote_message_compose, isDarkTheme = isDarkTheme) {
                DaxPromoSingleActionMessage(
                    title = "Promo Single Action Message",
                    body = "Body text goes here. This component has one promo button and supports <b>bold</b> text",
                    illustration = painterResource(CommonR.drawable.promo_mac_and_windows),
                    illustrationContentDescription = null,
                    action = DaxAction(text = "Promo Link", onClick = {}),
                    onDismissed = {
                        view.findViewById<ComposeView>(R.id.promo_single_remote_message_compose).gone()
                    },
                    modifier = Modifier.padding(
                        start = dimensionResource(CommonR.dimen.keyline_4),
                        end = dimensionResource(CommonR.dimen.keyline_4),
                        bottom = dimensionResource(CommonR.dimen.keyline_4),
                    ),
                )
            }

            view.setupThemedComposeView(R.id.small_remote_message_compose, isDarkTheme = isDarkTheme) {
                DaxSmallMessage(
                    title = "Compose Small Message",
                    body = "Body text goes here. This component doesn't have buttons",
                    onDismissed = {
                        view.findViewById<ComposeView>(R.id.small_remote_message_compose).gone()
                    },
                    modifier = Modifier.padding(dimensionResource(CommonR.dimen.keyline_4)),
                )
            }

            view.setupThemedComposeView(R.id.medium_remote_message_compose, isDarkTheme = isDarkTheme) {
                DaxMediumMessage(
                    title = "Compose Medium Message",
                    body = "Body text goes here. This component doesn't have buttons",
                    topIllustration = painterResource(CommonR.drawable.ic_critical_update),
                    onDismissed = {
                        view.findViewById<ComposeView>(R.id.medium_remote_message_compose).gone()
                    },
                    modifier = Modifier.padding(dimensionResource(CommonR.dimen.keyline_4)),
                )
            }

            view.setupThemedComposeView(R.id.big_single_remote_message_compose, isDarkTheme = isDarkTheme) {
                DaxBigSingleActionMessage(
                    topIllustration = painterResource(CommonR.drawable.ic_announce),
                    title = "Compose Big Single Message",
                    body = "Body text goes here. This component has one button",
                    action = DaxAction(text = "Primary", onClick = {}),
                    onDismissed = {
                        view.findViewById<ComposeView>(R.id.big_single_remote_message_compose).gone()
                    },
                    modifier = Modifier.padding(dimensionResource(CommonR.dimen.keyline_4)),
                )
            }

            view.setupThemedComposeView(R.id.big_single_lottie_remote_message_compose, isDarkTheme = isDarkTheme) {
                DaxBigSingleActionMessage(
                    topIllustration = {
                        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.anim_password_keys))
                        val progress by animateLottieCompositionAsState(
                            composition = composition,
                        )
                        LottieAnimation(
                            composition = composition,
                            progress = { progress },
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .heightIn(max = 96.dp),
                        )
                    },
                    title = "Bring your passwords from Google to DuckDuckGo",
                    body = "Quickly and securely import your passwords to DuckDuckGo. Google may ask you to enter your password.",
                    action = DaxAction(text = "Import From Google", onClick = {}),
                    onDismissed = {
                        view.findViewById<ComposeView>(R.id.big_single_lottie_remote_message_compose).gone()
                    },
                    modifier = Modifier.padding(dimensionResource(CommonR.dimen.keyline_4)),
                )
            }

            view.setupThemedComposeView(R.id.big_two_actions_remote_message_compose, isDarkTheme = isDarkTheme) {
                DaxBigTwoActionsMessage(
                    topIllustration = painterResource(CommonR.drawable.ic_ddg_announce),
                    title = "Compose Big Two Actions",
                    body = "Body text goes here. This component has two buttons",
                    primaryAction = DaxAction(text = "Primary", onClick = {}),
                    secondaryAction = DaxAction(text = "Secondary", onClick = {}),
                    onDismissed = {
                        view.findViewById<ComposeView>(R.id.big_two_actions_remote_message_compose).gone()
                    },
                    modifier = Modifier.padding(dimensionResource(CommonR.dimen.keyline_4)),
                )
            }

            view.setupThemedComposeView(R.id.big_two_actions_update_remote_message_compose, isDarkTheme = isDarkTheme) {
                DaxBigTwoActionsMessage(
                    topIllustration = painterResource(CommonR.drawable.ic_app_update),
                    title = "Compose Big Two Actions",
                    body = "Body text goes here. This component has two buttons an showcases and app update",
                    primaryAction = DaxAction(text = "Primary", onClick = {}),
                    secondaryAction = DaxAction(text = "Secondary", onClick = {}),
                    onDismissed = {
                        view.findViewById<ComposeView>(R.id.big_two_actions_update_remote_message_compose).gone()
                    },
                    modifier = Modifier.padding(dimensionResource(CommonR.dimen.keyline_4)),
                )
            }

            view.setupThemedComposeView(R.id.big_two_actions_server_image_remote_message_compose, isDarkTheme = isDarkTheme) {
                DaxBigTwoActionsMessage(
                    topIllustration = rememberAsyncImagePainter(
                        model = "https://staticcdn.duckduckgo.com/remotemessaging/illustrations/image2.png",
                        error = painterResource(CommonR.drawable.ic_app_update),
                        fallback = painterResource(CommonR.drawable.ic_app_update),
                    ),
                    title = "Compose Remote Image",
                    body = "Body text goes here. This component has two buttons an showcases and app update",
                    primaryAction = DaxAction(text = "Primary", onClick = {}),
                    secondaryAction = DaxAction(text = "Secondary", onClick = {}),
                    onDismissed = {
                        view.findViewById<ComposeView>(R.id.big_two_actions_server_image_remote_message_compose).gone()
                    },
                    modifier = Modifier.padding(dimensionResource(CommonR.dimen.keyline_4)),
                )
            }
        }
    }

    class SearchBarComponentViewHolder(
        parent: ViewGroup,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_search_bar))

    class MenuItemComponentViewHolder(
        parent: ViewGroup,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_menu_item))

    class PopupMenuItemComponentViewHolder(
        parent: ViewGroup,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_popup_menu_item))

    class ContextMenuComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_context_menu)) {
        @SuppressLint("ShowToast")
        override fun bind(component: Component) {
            view.setupThemedComposeView(id = R.id.compose_context_menu_list_item, isDarkTheme = isDarkTheme) {
                val context = LocalContext.current
                Column {
                    var firstMenuExpanded by remember { mutableStateOf(false) }
                    DaxTwoLineListItem(
                        primaryText = "Bookmark this page",
                        secondaryText = "duckduckgo.com",
                        leadingContent = {
                            Icon(painterResource(CommonR.drawable.ic_globe_24), contentDescription = null)
                        },
                        trailingContent = {
                            DaxContextMenu(
                                anchor = {
                                    Icon(
                                        painter = painterResource(CommonR.drawable.ic_menu_vertical_24),
                                        contentDescription = "More options",
                                        onClick = { firstMenuExpanded = true },
                                    )
                                },
                                expanded = firstMenuExpanded,
                                onDismissRequest = { firstMenuExpanded = false },
                            ) {
                                DaxIconItem(
                                    text = "Bookmark",
                                    painterLeadingIcon = painterResource(CommonR.drawable.ic_bookmark_24),
                                    onClick = { Toast.makeText(context, "Bookmark pressed", Toast.LENGTH_SHORT).show() },
                                    showDivider = true,
                                )
                                DaxInsetItem(
                                    text = "Copy link",
                                    onClick = { Toast.makeText(context, "Copy link pressed", Toast.LENGTH_SHORT).show() },
                                    trailingIcon = { Icon(painterResource(CommonR.drawable.ic_copy_24), null) },
                                    showDivider = true,
                                )
                                DaxDefaultItem(text = "Unavailable", onClick = {}, enabled = false)
                            }
                        },
                        onClick = {},
                    )

                    var secondMenuExpanded by remember { mutableStateOf(false) }
                    DaxTwoLineListItem(
                        primaryText = "Open in new tab",
                        secondaryText = "example.com",
                        leadingContent = {
                            Icon(painterResource(CommonR.drawable.ic_globe_24), contentDescription = null)
                        },
                        trailingContent = {
                            DaxContextMenu(
                                anchor = {
                                    Icon(
                                        painter = painterResource(CommonR.drawable.ic_menu_vertical_24),
                                        contentDescription = "More options",
                                        onClick = { secondMenuExpanded = true },
                                    )
                                },
                                expanded = secondMenuExpanded,
                                onDismissRequest = { secondMenuExpanded = false },
                            ) {
                                DaxDefaultItem(
                                    text = "Share",
                                    onClick = { Toast.makeText(context, "Share pressed", Toast.LENGTH_SHORT).show() },
                                    showDivider = true,
                                )
                                DaxDefaultItem(
                                    text = "Delete",
                                    onClick = { Toast.makeText(context, "Delete pressed", Toast.LENGTH_SHORT).show() },
                                    isDestructive = true,
                                )
                            }
                        },
                        onClick = {},
                    )
                }
            }

            view.findViewById<TwoLineListItem>(R.id.xml_context_menu_list_item).setTrailingIconClickListener { anchor ->
                val popupMenu = PopupMenu(LayoutInflater.from(view.context), R.layout.popup_component_context_menu)
                val menuView = popupMenu.contentView
                popupMenu.apply {
                    onMenuItemClicked(menuView.findViewById(R.id.bookmark)) {
                        Toast.makeText(view.context, "Bookmark pressed", Toast.LENGTH_SHORT).show()
                    }
                    onMenuItemClicked(menuView.findViewById(R.id.copyLink)) {
                        Toast.makeText(view.context, "Copy link pressed", Toast.LENGTH_SHORT).show()
                    }
                    menuView.findViewById<PopupMenuItemView>(R.id.unavailable).setDisabled()
                    onMenuItemClicked(menuView.findViewById(R.id.delete)) {
                        Toast.makeText(view.context, "Delete pressed", Toast.LENGTH_SHORT).show()
                    }
                }
                popupMenu.show(view, anchor)
            }
        }
    }

    class HeaderSectionComponentViewHolder(
        parent: ViewGroup,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_section_header_item)) {
        override fun bind(component: Component) {
            view.findViewById<SectionHeaderListItem>(R.id.sectionHeaderItemTitle).apply {
                revertUpperCaseTitleText()
            }
            view.findViewById<SectionHeaderListItem>(R.id.sectionHeaderWithOverflow).apply {
                setOverflowMenuClickListener { Snackbar.make(view, "Overflow menu clicked", Snackbar.LENGTH_SHORT).show() }
                revertUpperCaseTitleText()
            }
        }
    }

    class OneLineListItemComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_one_line_item)) {
        override fun bind(component: Component) {
            view.findViewById<OneLineListItem>(R.id.oneLineListItem).apply {
                setClickListener { Snackbar.make(view, component.name, Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<OneLineListItem>(R.id.oneLineListItemWithSmallImage).apply {
                setClickListener { Snackbar.make(view, component.name, Snackbar.LENGTH_SHORT).show() }
                setLeadingIconClickListener { Snackbar.make(view, "Small Leading Icon clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<OneLineListItem>(R.id.oneLineListItemWithMediumImage).apply {
                setClickListener { Snackbar.make(view, component.name, Snackbar.LENGTH_SHORT).show() }
                setLeadingIconClickListener { Snackbar.make(view, "Medium Leading Icon clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<OneLineListItem>(R.id.oneLineListItemWithLargeImage).apply {
                setClickListener { Snackbar.make(view, component.name, Snackbar.LENGTH_SHORT).show() }
                setLeadingIconClickListener { Snackbar.make(view, "Large Leading Icon clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<OneLineListItem>(R.id.oneLineListItemWithExtraLargeImage).apply {
                setClickListener { Snackbar.make(view, component.name, Snackbar.LENGTH_SHORT).show() }
                setLeadingIconClickListener { Snackbar.make(view, "Extra Large Leading Icon clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<OneLineListItem>(R.id.oneLineListItemWithTrailingIcon).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setTrailingIconClickListener { Snackbar.make(view, "Overflow menu clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<OneLineListItem>(R.id.oneLineListItemWithTrailingIcon).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setTrailingIconClickListener { Snackbar.make(view, "Overflow menu clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<OneLineListItem>(R.id.oneLineListItemWithLeadingAndTrailingIcons).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setLeadingIconClickListener { Snackbar.make(view, "Leading Icon clicked", Snackbar.LENGTH_SHORT).show() }
                setTrailingIconClickListener { Snackbar.make(view, "Overflow menu clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<OneLineListItem>(R.id.oneLineListItemSwitch).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setLeadingIconClickListener { Snackbar.make(view, "Leading Icon clicked", Snackbar.LENGTH_SHORT).show() }
                setOnCheckedChangeListener { view, isChecked -> Snackbar.make(view, "Switch checked: $isChecked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<OneLineListItem>(R.id.oneLineListSwitchItemWithLeadingIcon).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setLeadingIconClickListener { Snackbar.make(view, "Leading Icon clicked", Snackbar.LENGTH_SHORT).show() }
                setOnCheckedChangeListener { view, isChecked -> Snackbar.make(view, "Switch checked: $isChecked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<OneLineListItem>(R.id.oneLineListItemDisabled).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                isEnabled = false
            }

            view.findViewById<OneLineListItem>(R.id.oneLineListItemCustomTextColor).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
            }
            view.findViewById<OneLineListItem>(R.id.oneLineListItemWithLongTextTruncated).apply {
                setPrimaryText(context.getString(CommonR.string.dax_one_line_list_item_html_primary_text).html(context))
            }

            val snackbar = ShowcaseSnackbar(view, component.name)
            val composeContent: List<Pair<Int, @Composable () -> Unit>> = listOf(
                R.id.composeOneLineSimple to { ComposeOneLineSimple(snackbar) },
                R.id.composeOneLineMediumImage to { ComposeOneLineMediumImage(snackbar) },
                R.id.composeOneLineMediumImageBg to { ComposeOneLineMediumImageBg() },
                R.id.composeOneLineLargeImage to { ComposeOneLineLargeImage(snackbar) },
                R.id.composeOneLineLargeImageBg to { ComposeOneLineLargeImageBg() },
                R.id.composeOneLineTrailingIcon to { ComposeOneLineTrailingIcon(snackbar) },
                R.id.composeOneLineTrailingTinted to { ComposeOneLineTrailingTinted() },
                R.id.composeOneLineLeadingTrailing to { ComposeOneLineLeadingTrailing(snackbar) },
                R.id.composeOneLineSwitch to { ComposeOneLineSwitch(snackbar) },
                R.id.composeOneLineSwitchRounded to { ComposeOneLineSwitchRounded(snackbar) },
                R.id.composeOneLineDisabled to { ComposeOneLineDisabled(snackbar) },
                R.id.composeOneLineDestructive to { ComposeOneLineDestructive(snackbar) },
                R.id.composeOneLineLongText to { ComposeOneLineLongText() },
                R.id.composeOneLineLongTextTruncated to { ComposeOneLineLongTextTruncated() },
                R.id.composeOneLineNewPill to { ComposeOneLineNewPill() },
                R.id.composeOneLineExtras to { ComposeOneLineExtras() },
            )
            composeContent.forEach { (id, content) ->
                view.setupThemedComposeView(id, isDarkTheme) { Column(modifier = Modifier.fillMaxWidth()) { content() } }
            }
        }
    }

    class TwoLineItemComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_two_line_item)) {
        override fun bind(component: Component) {
            view.findViewById<TwoLineListItem>(R.id.twoLineListItemWithoutImage).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineListItemWithImage).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setLeadingIconClickListener { Snackbar.make(view, "Leading Icon clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineListItemWithSmallImageAndTrailingIcon).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setLeadingIconClickListener { Snackbar.make(view, "Small Leading Icon clicked", Snackbar.LENGTH_SHORT).show() }
                setTrailingIconClickListener { Snackbar.make(view, "Overflow menu clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineListItemWithMediumImageAndTrailingIcon).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setLeadingIconClickListener { Snackbar.make(view, "Medium Leading Icon clicked", Snackbar.LENGTH_SHORT).show() }
                setTrailingIconClickListener { Snackbar.make(view, "Overflow menu clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineListItemWithLargeImageAndTrailingIcon).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setLeadingIconClickListener { Snackbar.make(view, "Large Leading Icon clicked", Snackbar.LENGTH_SHORT).show() }
                setTrailingIconClickListener { Snackbar.make(view, "Overflow menu clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineListItemWithExtraLargeImageAndTrailingIcon).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setLeadingIconClickListener { Snackbar.make(view, "Extra Large Leading Icon clicked", Snackbar.LENGTH_SHORT).show() }
                setTrailingIconClickListener { Snackbar.make(view, "Overflow menu clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineListItemWithTrailingIcon).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setTrailingIconClickListener { Snackbar.make(view, "Overflow menu clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineListItemWithBetaPill).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setTrailingIconClickListener { Snackbar.make(view, "Overflow menu clicked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineSwitchListItem).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setOnCheckedChangeListener { view, isChecked -> Snackbar.make(view, "Switch checked: $isChecked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineSwitchListItemWithImage).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setLeadingIconClickListener { Snackbar.make(view, "Leading Icon clicked", Snackbar.LENGTH_SHORT).show() }
                setOnCheckedChangeListener { view, isChecked -> Snackbar.make(view, "Switch checked: $isChecked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineSwitchListItemWithPill).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                setOnCheckedChangeListener { view, isChecked -> Snackbar.make(view, "Switch checked: $isChecked", Snackbar.LENGTH_SHORT).show() }
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineSwitchListItemWithDisabledSwitch).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                isEnabled = false
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineSwitchListItemWithDisabledSwitchEnabled).apply {
                setClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
                quietlySetIsChecked(true, null)
                isEnabled = false
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineSwitchListItemWithSwitchDisabledChecked).apply {
                quietlySetIsChecked(true, null)
            }

            view.findViewById<TwoLineListItem>(R.id.twoLineListItemWithHTMLTags).apply {
                setPrimaryText(context.getString(CommonR.string.dax_list_item_html_primary_text).html(context))
                setSecondaryText(context.getString(CommonR.string.dax_list_item_html_secondary_text).html(context))
            }

            val snackbar = ShowcaseSnackbar(view, component.name)
            val composeContent: List<Pair<Int, @Composable () -> Unit>> = listOf(
                R.id.composeTwoLinePlain to { ComposeTwoLinePlain(snackbar) },
                R.id.composeTwoLineImage to { ComposeTwoLineImage(snackbar) },
                R.id.composeTwoLineMediumTrailing to { ComposeTwoLineMediumTrailing(snackbar) },
                R.id.composeTwoLineMediumTrailingBg to { ComposeTwoLineMediumTrailingBg() },
                R.id.composeTwoLineLargeTrailing to { ComposeTwoLineLargeTrailing(snackbar) },
                R.id.composeTwoLineLargeTrailingBg to { ComposeTwoLineLargeTrailingBg() },
                R.id.composeTwoLineTrailing to { ComposeTwoLineTrailing(snackbar) },
                R.id.composeTwoLineSmallTrailing to { ComposeTwoLineSmallTrailing() },
                R.id.composeTwoLineMediumTrailingOnly to { ComposeTwoLineMediumTrailingOnly() },
                R.id.composeTwoLineBetaPill to { ComposeTwoLineBetaPill(snackbar) },
                R.id.composeTwoLineCircular to { ComposeTwoLineCircular() },
                R.id.composeTwoLineRounded to { ComposeTwoLineRounded() },
                R.id.composeTwoLineSwitch to { ComposeTwoLineSwitch(snackbar) },
                R.id.composeTwoLineSwitchImage to { ComposeTwoLineSwitchImage(snackbar) },
                R.id.composeTwoLineSwitchPill to { ComposeTwoLineSwitchPill(snackbar) },
                R.id.composeTwoLineSwitchTruncated to { ComposeTwoLineSwitchTruncated() },
                R.id.composeTwoLineDisabled to { ComposeTwoLineDisabled(snackbar) },
                R.id.composeTwoLineSwitchChecked to { ComposeTwoLineSwitchChecked(snackbar) },
                R.id.composeTwoLineSwitchDisabledChecked to { ComposeTwoLineSwitchDisabledChecked() },
                R.id.composeTwoLinePrimaryColor to { ComposeTwoLinePrimaryColor() },
                R.id.composeTwoLineSecondaryColor to { ComposeTwoLineSecondaryColor() },
                R.id.composeTwoLineHtml to { ComposeTwoLineHtml() },
                R.id.composeTwoLineExtras to { ComposeTwoLineExtras() },
            )
            composeContent.forEach { (id, content) ->
                view.setupThemedComposeView(id, isDarkTheme) { Column(modifier = Modifier.fillMaxWidth()) { content() } }
            }
        }
    }

    @SuppressLint("ShowToast")
    class SnackbarComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_snackbar)) {

        init {
            val container: FrameLayout = view.findViewById(R.id.snackbar_container)
            val snackbarView =
                Snackbar.make(container, "This is a Snackbar message", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Action") { Snackbar.make(container, "Action pressed", Snackbar.LENGTH_LONG).show() }
                    .view
            (snackbarView.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER

            container.addView(snackbarView)

            view.setupThemedComposeView(R.id.composeDaxSnackbar, isDarkTheme) {
                val context = LocalContext.current
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DaxSnackbar(
                        message = "This is a Snackbar message",
                    )
                    DaxSnackbar(
                        message = "This is a Snackbar message",
                        action = DaxAction(
                            text = "Action",
                            onClick = { Toast.makeText(context, "Action pressed", Toast.LENGTH_SHORT).show() },
                        ),
                    )
                    DaxSnackbar(
                        message = "This snackbar message is far too long to fit, so it is capped at two lines and " +
                            "truncated with an ellipsis rather than growing unbounded beside the action.",
                    )
                    DaxSnackbar(
                        message = "This snackbar message is far too long to fit, so it is capped at two lines and " +
                            "truncated with an ellipsis rather than growing unbounded beside the action.",
                        action = DaxAction(
                            text = "Action",
                            onClick = { Toast.makeText(context, "Action pressed", Toast.LENGTH_SHORT).show() },
                        ),
                    )
                }
            }
        }
    }

    class DividerComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_section_divider)) {
        override fun bind(component: Component) {
            view.setupThemedComposeView(
                id = R.id.compose_dax_horizontal_divider_full_width,
                isDarkTheme = isDarkTheme,
            ) {
                DaxHorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            view.setupThemedComposeView(
                id = R.id.compose_dax_horizontal_divider_inset,
                isDarkTheme = isDarkTheme,
            ) {
                DaxHorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            view.setupThemedComposeView(
                id = R.id.compose_dax_horizontal_divider_custom_margin,
                isDarkTheme = isDarkTheme,
            ) {
                DaxHorizontalDivider(
                    modifier = Modifier.padding(56.dp),
                )
            }
            view.setupThemedComposeView(
                id = R.id.compose_dax_vertical_divider,
                isDarkTheme = isDarkTheme,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    DaxIconButton(
                        onClick = {},
                        iconPainter = painterResource(CommonR.drawable.ic_union),
                        contentDescription = "Menu",
                    )

                    DaxVerticalDivider()

                    DaxIconButton(
                        onClick = {},
                        iconPainter = painterResource(CommonR.drawable.ic_union),
                        contentDescription = "Menu",
                    )
                }
            }
        }
    }

    class CardComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_card)) {
        override fun bind(component: Component) {
            view.findViewById<MaterialCardView>(R.id.ticketViewCard).apply {
                val cornerSize = resources.getDimension(CommonR.dimen.smallShapeCornerRadius)
                val edgeTreatment = TriangleEdgeTreatment(cornerSize, true)
                shapeAppearanceModel = ShapeAppearanceModel.Builder()
                    .setLeftEdge(edgeTreatment)
                    .setRightEdge(edgeTreatment)
                    .setAllCornerSizes(cornerSize)
                    .build()
                elevation = 8f

                setOnClickListener { Snackbar.make(this, component.name, Snackbar.LENGTH_SHORT).show() }
            }

            view.setupThemedComposeView(R.id.composeCards, isDarkTheme) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    DaxCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                    ) { }
                    DaxCard(
                        onClick = {
                            Snackbar.make(view, component.name, Snackbar.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                    ) { }
                    DaxSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                    ) { }
                    DaxSurface(
                        onClick = {
                            Snackbar.make(view, component.name, Snackbar.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                    ) { }
                }
            }
        }
    }

    class ProgressSpinnerComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_progress_spinner)) {
        override fun bind(component: Component) {
            view.setupThemedComposeView(id = R.id.compose_dax_progress_spinner, isDarkTheme = isDarkTheme) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DaxProgressSpinner()
                    DaxProgressSpinner(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }

    class ScaffoldComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_scaffold)) {
        override fun bind(component: Component) {
            view.setupThemedComposeView(R.id.composeScaffold, isDarkTheme) {
                val searchState = rememberTextFieldState()
                var searchActive by remember { mutableStateOf(false) }
                DaxScaffold(
                    modifier = Modifier.height(400.dp),
                    topBar = {
                        DaxSearchTopAppBar(
                            title = "Bookmarks",
                            navigationIcon = DaxTopAppBarNavigationIcon.Back { },
                            shadow = true,
                            searchActive = searchActive,
                            searchState = searchState,
                            searchPlaceholder = "Search…",
                            onSearchBack = { searchActive = false },
                            actions = {
                                DaxIconButton(
                                    onClick = { searchActive = true },
                                    iconPainter = painterResource(CommonR.drawable.ic_find_search_24),
                                    contentDescription = "Search",
                                )
                            },
                        )
                    },
                ) { paddingValues ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        DaxText(text = "Content goes here")
                    }
                }
            }
        }
    }

    class SettingsListItemComponentViewHolder(
        parent: ViewGroup,
        private val isDarkTheme: Boolean,
    ) : ComponentViewHolder(inflate(parent, R.layout.component_settings)) {
        override fun bind(component: Component) {
            view.findViewById<SettingsListItem>(R.id.settingsListItemWithBetaTag).apply {
                showPillIcon(true)
            }
            val composeContent: List<Pair<Int, @Composable () -> Unit>> = listOf(
                R.id.composeSettingsWithIcon to { ComposeSettingsWithIcon() },
                R.id.composeSettingsAlwaysOn to { ComposeSettingsAlwaysOn() },
                R.id.composeSettingsOn to { ComposeSettingsOn() },
                R.id.composeSettingsOff to { ComposeSettingsOff() },
                R.id.composeSettingsBeta to { ComposeSettingsBeta() },
                R.id.composeSettingsBetaLongText to { ComposeSettingsBetaLongText() },
                R.id.composeSettingsNew to { ComposeSettingsNew() },
                R.id.composeSettingsExtras to { ComposeSettingsExtras() },
            )
            composeContent.forEach { (id, content) ->
                view.setupThemedComposeView(id, isDarkTheme) { Column(modifier = Modifier.fillMaxWidth()) { content() } }
            }
        }
    }

    companion object {
        fun create(
            parent: ViewGroup,
            viewType: Int,
            isDarkTheme: Boolean,
        ): ComponentViewHolder {
            return when (Component.values()[viewType]) {
                Component.BUTTON -> ButtonComponentViewHolder(parent)
                Component.TOP_APP_BAR -> TopAppBarComponentViewHolder(parent, isDarkTheme)
                Component.SWITCH -> SwitchComponentViewHolder(parent, isDarkTheme)
                Component.RADIO_BUTTON -> RadioButtonComponentViewHolder(parent, isDarkTheme)
                Component.CHECKBOX -> CheckboxComponentViewHolder(parent, isDarkTheme)
                Component.SLIDER -> SliderComponentViewHolder(parent)
                Component.SNACKBAR -> SnackbarComponentViewHolder(parent, isDarkTheme)
                Component.INFO_PANEL -> InfoPanelComponentViewHolder(parent, isDarkTheme)
                Component.REMOTE_MESSAGE -> RemoteMessageComponentViewHolder(parent, isDarkTheme)
                Component.SEARCH_BAR -> SearchBarComponentViewHolder(parent)
                Component.MENU_ITEM -> MenuItemComponentViewHolder(parent)
                Component.POPUP_MENU_ITEM -> PopupMenuItemComponentViewHolder(parent)
                Component.SECTION_HEADER_LIST_ITEM -> HeaderSectionComponentViewHolder(parent)
                Component.SINGLE_LINE_LIST_ITEM -> OneLineListItemComponentViewHolder(parent, isDarkTheme)
                Component.TWO_LINE_LIST_ITEM -> TwoLineItemComponentViewHolder(parent, isDarkTheme)
                Component.SECTION_DIVIDER -> DividerComponentViewHolder(parent, isDarkTheme)
                Component.PROGRESS_SPINNER -> ProgressSpinnerComponentViewHolder(parent, isDarkTheme)
                Component.CARD -> CardComponentViewHolder(parent, isDarkTheme)
                Component.SCAFFOLD -> ScaffoldComponentViewHolder(parent, isDarkTheme)
                Component.SETTINGS_LIST_ITEM -> SettingsListItemComponentViewHolder(parent, isDarkTheme)
                Component.CONTEXT_MENU -> ContextMenuComponentViewHolder(parent, isDarkTheme)
                else -> {
                    TODO()
                }
            }
        }

        private fun inflate(
            parent: ViewGroup,
            layout: Int,
        ): View {
            return LayoutInflater.from(parent.context).inflate(layout, parent, false)
        }
    }
}

private class ShowcaseSnackbar(
    private val view: View,
    private val componentName: String,
) {
    fun row() = show(componentName)

    fun trailingIcon() = show("Overflow menu clicked")

    fun switch(checked: Boolean) = show("Switch checked: $checked")

    private fun show(message: String) = Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show()
}

@Composable
private fun ComposeOneLineSimple(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxOneLineListItem(primaryText = "This is a simple item", onClick = { snackbar.row() })
}

@Composable
private fun ComposeOneLineMediumImage(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item with Medium Leading Image",
        leadingContent = { Image(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small) },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeOneLineMediumImageBg() {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item with Medium Leading Image",
        leadingContent = {
            Image(
                painterResource(CommonR.drawable.ic_dax_icon),
                null,
                size = DaxListItemIconSize.Small,
                background = DaxListItemIconBackground.Circular,
            )
        },
        onClick = {},
    )
}

@Composable
private fun ComposeOneLineLargeImage(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item with Large Leading Image",
        leadingContent = { Image(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Large) },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeOneLineLargeImageBg() {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item with Large Leading Image",
        leadingContent = {
            Image(
                painterResource(CommonR.drawable.ic_dax_icon),
                null,
                size = DaxListItemIconSize.Large,
                background = DaxListItemIconBackground.Circular,
            )
        },
        onClick = {},
    )
}

@Composable
private fun ComposeOneLineTrailingIcon(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item With Trailing Icon",
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = { snackbar.trailingIcon() }) },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeOneLineTrailingTinted() {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item With Trailing Icon Tinted",
        trailingContent = {
            Icon(painterResource(CommonR.drawable.ic_open_in_16), null, tint = DuckDuckGoTheme.colors.icons.secondary)
        },
    )
}

@Composable
private fun ComposeOneLineLeadingTrailing(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item With Leading and Trailing Icons",
        leadingContent = {
            Icon(
                painterResource(CommonR.drawable.ic_globe_24),
                null,
                size = DaxListItemIconSize.Small,
                background = DaxListItemIconBackground.Circular,
            )
        },
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = { snackbar.trailingIcon() }) },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeOneLineSwitch(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    var checked by remember { mutableStateOf(false) }
    DaxOneLineListItem(
        primaryText = "Item with Switch Item",
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    snackbar.switch(it)
                },
            )
        },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeOneLineSwitchRounded(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    var checked by remember { mutableStateOf(false) }
    DaxOneLineListItem(
        primaryText = "Item With Switch and Leading Icon",
        leadingContent = {
            Icon(
                painterResource(CommonR.drawable.ic_globe_24),
                null,
                size = DaxListItemIconSize.Small,
                background = DaxListItemIconBackground.Rounded,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    snackbar.switch(it)
                },
            )
        },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeOneLineDisabled(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxOneLineListItem(primaryText = "Item disabled", enabled = false, onClick = { snackbar.row() })
}

@Composable
private fun ComposeOneLineDestructive(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item with custom text color",
        primaryTextColor = DuckDuckGoTheme.textColors.destructive,
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeOneLineLongText() {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item with long primary text that expands to more lines as primaryTextTruncated is disabled by default",
        onClick = {},
    )
}

@Composable
private fun ComposeOneLineLongTextTruncated() {
    ComposeCaption()
    val primary = buildAnnotatedString {
        append("Item with ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("HTML tags") }
        append(" and ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("truncated") }
        append(" text: Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.")
    }
    DaxOneLineListItem(
        primaryText = primary,
        primaryMaxLines = 1,
        onClick = {},
    )
}

@Composable
private fun ComposeOneLineNewPill() {
    ComposeCaption()
    DaxOneLineListItem(primaryText = "Item with New Pill", inlineContent = { Pill("New") }, onClick = {})
}

@Composable
private fun ComposeOneLineExtras() {
    ComposeCaption(stringResource(R.string.dsShowcaseCaptionComposeOnly))
    DaxOneLineListItem(
        primaryText = "Favicon (untinted image)",
        leadingContent = { Image(painterResource(CommonR.drawable.ic_ddg_logo), null, size = DaxListItemIconSize.Large) },
        onClick = {},
    )
    DaxOneLineListItem(
        primaryText = "Disabled with checked switch",
        enabled = false,
        trailingContent = { Switch(checked = true, onCheckedChange = {}) },
    )
    DaxOneLineListItem(
        primaryText = "Switch-only disabled",
        trailingContent = { Switch(checked = true, onCheckedChange = {}, enabled = false) },
    )
    DaxOneLineListItem(primaryText = "Accent", primaryTextColor = DuckDuckGoTheme.colors.brand.accentBlue, onClick = {})
}

@Composable
private fun ComposeTwoLinePlain(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxTwoLineListItem(primaryText = "Two Line Item", secondaryText = "Without Image", onClick = { snackbar.row() })
}

@Composable
private fun ComposeTwoLineImage(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Leading Image",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeTwoLineMediumTrailing(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Medium Leading and Trailing Image",
        leadingContent = { Image(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small) },
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = { snackbar.trailingIcon() }) },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeTwoLineMediumTrailingBg() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Medium Leading Background and Trailing Image",
        leadingContent = {
            Image(
                painterResource(CommonR.drawable.ic_dax_icon),
                null,
                size = DaxListItemIconSize.Small,
                background = DaxListItemIconBackground.Circular,
            )
        },
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = {}) },
        onClick = {},
    )
}

@Composable
private fun ComposeTwoLineLargeTrailing(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Large Leading and Trailing Image",
        leadingContent = { Image(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Large) },
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = { snackbar.trailingIcon() }) },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeTwoLineLargeTrailingBg() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Large Leading Background and Trailing Image",
        leadingContent = {
            Image(
                painterResource(CommonR.drawable.ic_dax_icon),
                null,
                size = DaxListItemIconSize.Large,
                background = DaxListItemIconBackground.Circular,
            )
        },
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = {}) },
        onClick = {},
    )
}

@Composable
private fun ComposeTwoLineTrailing(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Trailing Image",
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = { snackbar.trailingIcon() }) },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeTwoLineSmallTrailing() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Small Trailing Image",
        trailingContent = {
            Icon(painterResource(CommonR.drawable.ic_exclamation_recolorable_16), "Info", onClick = {}, size = DaxListItemTrailingIconSize.Small)
        },
        onClick = {},
    )
}

@Composable
private fun ComposeTwoLineMediumTrailingOnly() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Medium (default) Trailing Image",
        trailingContent = {
            Icon(painterResource(CommonR.drawable.ic_exclamation_recolorable_16), "Info", onClick = {}, size = DaxListItemTrailingIconSize.Medium)
        },
        onClick = {},
    )
}

@Composable
private fun ComposeTwoLineBetaPill(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Beta Pill",
        inlineContent = { Pill("Beta") },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeTwoLineCircular() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Leading Image over Circular Background",
        leadingContent = {
            Icon(
                painterResource(CommonR.drawable.ic_globe_24),
                null,
                size = DaxListItemIconSize.Small,
                background = DaxListItemIconBackground.Circular,
            )
        },
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = {}) },
        onClick = {},
    )
}

@Composable
private fun ComposeTwoLineRounded() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Leading Image over Rounded Background",
        leadingContent = {
            Icon(
                painterResource(CommonR.drawable.ic_globe_24),
                null,
                size = DaxListItemIconSize.Small,
                background = DaxListItemIconBackground.Rounded,
            )
        },
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = {}) },
        onClick = {},
    )
}

@Composable
private fun ComposeTwoLineSwitch(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    var checked by remember { mutableStateOf(false) }
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Switch",
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    snackbar.switch(it)
                },
            )
        },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeTwoLineSwitchImage(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    var checked by remember { mutableStateOf(false) }
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Leading Image and Switch",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    snackbar.switch(it)
                },
            )
        },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeTwoLineSwitchPill(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    var checked by remember { mutableStateOf(false) }
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Beta Pill and Switch",
        inlineContent = { Pill("Beta") },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    snackbar.switch(it)
                },
            )
        },
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeTwoLineSwitchTruncated() {
    ComposeCaption()
    var checked by remember { mutableStateOf(false) }
    DaxTwoLineListItem(
        primaryText = "Two Line Item Two Line Item Two Line Item Two Line Item",
        secondaryText = "In disabled state",
        inlineContent = { Pill("Beta") },
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
        trailingContent = { Switch(checked = checked, onCheckedChange = { checked = it }) },
        primaryMaxLines = 1,
        onClick = {},
    )
}

@Composable
private fun ComposeTwoLineDisabled(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item Two Line Item Two Line Item Two Line Item",
        secondaryText = "In disabled state",
        inlineContent = { Pill("Beta") },
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
        trailingContent = { Switch(checked = false, onCheckedChange = {}) },
        enabled = false,
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeTwoLineSwitchChecked(snackbar: ShowcaseSnackbar) {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item Two",
        secondaryText = "Checked in disabled state",
        inlineContent = { Pill("Whatever") },
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
        trailingContent = { Switch(checked = true, onCheckedChange = {}) },
        enabled = false,
        onClick = { snackbar.row() },
    )
}

@Composable
private fun ComposeTwoLineSwitchDisabledChecked() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item Two",
        secondaryText = "Checked with switch in disabled state",
        inlineContent = { Pill("Beta") },
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
        trailingContent = { Switch(checked = true, onCheckedChange = {}, enabled = false) },
        onClick = {},
    )
}

@Composable
private fun ComposeTwoLinePrimaryColor() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With custom Primary Text color",
        primaryTextColor = DuckDuckGoTheme.textColors.destructive,
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
    )
}

@Composable
private fun ComposeTwoLineSecondaryColor() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With custom Secondary Text color",
        secondaryTextColor = DuckDuckGoTheme.textColors.destructive,
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
    )
}

@Composable
private fun ComposeTwoLineHtml() {
    ComposeCaption()
    val primary: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Two Line") }
        append(" Item")
    }
    val secondary: AnnotatedString = buildAnnotatedString {
        append("With HTML tags in ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("primary") }
        append(" and ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("secondary") }
        append(" text")
    }
    DaxTwoLineListItem(primaryText = primary, secondaryText = secondary, onClick = {})
}

@Composable
private fun ComposeTwoLineExtras() {
    ComposeCaption(stringResource(R.string.dsShowcaseCaptionComposeOnly))
    DaxTwoLineListItem(
        primaryText = "Unbounded secondary text",
        secondaryText = "This supporting caption is intentionally long so it wraps over several lines, showing the unbounded secondary default.",
        onClick = {},
    )
}

@Composable
private fun ComposeCaption(text: String = stringResource(R.string.dsShowcaseCaptionCompose)) {
    DaxText(
        text = text,
        style = DuckDuckGoTheme.typography.caption,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ComposeSettingsWithIcon() {
    ComposeCaption()
    DaxSettingsListItem(
        primaryText = "Settings List Item",
        trailingContent = { StatusIndicator(Status.Off) },
        leadingContent = { Image(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsAlwaysOn() {
    ComposeCaption()
    DaxSettingsListItem(
        primaryText = "Settings List Item Always On",
        trailingContent = { StatusIndicator(Status.AlwaysOn) },
        leadingContent = { Image(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsOn() {
    ComposeCaption()
    DaxSettingsListItem(
        primaryText = "Settings List Item on",
        trailingContent = { StatusIndicator(Status.On) },
        leadingContent = { Image(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsOff() {
    ComposeCaption()
    DaxSettingsListItem(
        primaryText = "Settings List Item Off",
        trailingContent = { StatusIndicator(Status.Off) },
        leadingContent = { Image(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsBeta() {
    ComposeCaption()
    DaxSettingsListItem(
        primaryText = "Settings List Item with Beta Pill",
        trailingContent = { StatusIndicator(Status.On) },
        inlineContent = { Pill("Beta") },
        leadingContent = { Image(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsBetaLongText() {
    ComposeCaption()
    DaxSettingsListItem(
        primaryText = "Settings List Item with Beta Pill and a very long piece of text that should hopefully wrap",
        trailingContent = { StatusIndicator(Status.On) },
        inlineContent = { Pill("Beta") },
        leadingContent = { Image(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsNew() {
    ComposeCaption()
    DaxSettingsListItem(
        primaryText = "Settings List Item with New Pill",
        trailingContent = { StatusIndicator(Status.On) },
        inlineContent = { Pill("New") },
        leadingContent = { Image(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsExtras() {
    ComposeCaption(stringResource(R.string.dsShowcaseCaptionComposeOnly))
    DaxSettingsListItem(
        primaryText = "Leading icon over circular background",
        trailingContent = { StatusIndicator(Status.On) },
        leadingContent = {
            Image(
                painterResource(CommonR.drawable.ic_dax_icon),
                null,
                size = DaxListItemIconSize.Small,
                background = DaxListItemIconBackground.Circular,
            )
        },
        onClick = {},
    )
    DaxSettingsListItem(primaryText = "Disabled", trailingContent = { StatusIndicator(Status.Off) }, enabled = false, onClick = {})
    DaxSettingsListItem(
        primaryText = "Trailing icon instead of a status",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null) },
        trailingContent = {
            Icon(painterResource(CommonR.drawable.ic_open_in_16), "Open", onClick = {}, size = DaxListItemTrailingIconSize.Small)
        },
        onClick = {},
    )
    DaxSettingsListItem(
        primaryText = "Two line settings row",
        secondaryText = "Your Privacy Pro subscription expired",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null) },
        trailingContent = {
            Icon(
                painterResource(CommonR.drawable.ic_exclamation_recolorable_16),
                "Expired",
                onClick = {},
                size = DaxListItemTrailingIconSize.Small,
            )
        },
        onClick = {},
    )
}
