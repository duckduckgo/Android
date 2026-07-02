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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
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
import com.duckduckgo.common.ui.compose.Status
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.cards.DaxCard
import com.duckduckgo.common.ui.compose.cards.DaxSurface
import com.duckduckgo.common.ui.compose.checkbox.DaxCheckbox
import com.duckduckgo.common.ui.compose.divider.DaxHorizontalDivider
import com.duckduckgo.common.ui.compose.divider.DaxVerticalDivider
import com.duckduckgo.common.ui.compose.listitem.DaxListItemIconBackground
import com.duckduckgo.common.ui.compose.listitem.DaxListItemIconSize
import com.duckduckgo.common.ui.compose.listitem.DaxListItemTrailingIconSize
import com.duckduckgo.common.ui.compose.listitem.DaxOneLineListItem
import com.duckduckgo.common.ui.compose.listitem.DaxSettingsListItem
import com.duckduckgo.common.ui.compose.listitem.DaxTwoLineListItem
import com.duckduckgo.common.ui.compose.message.DaxAction
import com.duckduckgo.common.ui.compose.message.remote.DaxBigSingleActionMessage
import com.duckduckgo.common.ui.compose.message.remote.DaxBigTwoActionsMessage
import com.duckduckgo.common.ui.compose.message.remote.DaxMediumMessage
import com.duckduckgo.common.ui.compose.message.remote.DaxPromoSingleActionMessage
import com.duckduckgo.common.ui.compose.message.remote.DaxSmallMessage
import com.duckduckgo.common.ui.compose.panel.DaxAlertPanel
import com.duckduckgo.common.ui.compose.panel.DaxInfoPanel
import com.duckduckgo.common.ui.compose.radiobutton.DaxRadioButton
import com.duckduckgo.common.ui.compose.switch.DaxSwitch
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.internal.R
import com.duckduckgo.common.ui.internal.ui.setupThemedComposeView
import com.duckduckgo.common.ui.view.MessageCta
import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.common.ui.view.MessageCta.MessageType.REMOTE_PROMO_MESSAGE
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

    class TopAppBarComponentViewHolder(parent: ViewGroup) :
        ComponentViewHolder(inflate(parent, R.layout.component_top_app_bar))

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

            val composeContent: List<Pair<Int, @Composable () -> Unit>> = listOf(
                R.id.composeOneLineSimple to { ComposeOneLineSimple() },
                R.id.composeOneLineMediumImage to { ComposeOneLineMediumImage() },
                R.id.composeOneLineMediumImageBg to { ComposeOneLineMediumImageBg() },
                R.id.composeOneLineLargeImage to { ComposeOneLineLargeImage() },
                R.id.composeOneLineLargeImageBg to { ComposeOneLineLargeImageBg() },
                R.id.composeOneLineTrailingIcon to { ComposeOneLineTrailingIcon() },
                R.id.composeOneLineTrailingTinted to { ComposeOneLineTrailingTinted() },
                R.id.composeOneLineLeadingTrailing to { ComposeOneLineLeadingTrailing() },
                R.id.composeOneLineSwitch to { ComposeOneLineSwitch() },
                R.id.composeOneLineDisabled to { ComposeOneLineDisabled() },
                R.id.composeOneLineDestructive to { ComposeOneLineDestructive() },
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

            val composeContent: List<Pair<Int, @Composable () -> Unit>> = listOf(
                R.id.composeTwoLinePlain to { ComposeTwoLinePlain() },
                R.id.composeTwoLineImage to { ComposeTwoLineImage() },
                R.id.composeTwoLineMediumTrailing to { ComposeTwoLineMediumTrailing() },
                R.id.composeTwoLineMediumTrailingBg to { ComposeTwoLineMediumTrailingBg() },
                R.id.composeTwoLineLargeTrailing to { ComposeTwoLineLargeTrailing() },
                R.id.composeTwoLineLargeTrailingBg to { ComposeTwoLineLargeTrailingBg() },
                R.id.composeTwoLineTrailing to { ComposeTwoLineTrailing() },
                R.id.composeTwoLineSmallTrailing to { ComposeTwoLineSmallTrailing() },
                R.id.composeTwoLineMediumTrailingOnly to { ComposeTwoLineMediumTrailingOnly() },
                R.id.composeTwoLineBetaPill to { ComposeTwoLineBetaPill() },
                R.id.composeTwoLineCircular to { ComposeTwoLineCircular() },
                R.id.composeTwoLineSwitch to { ComposeTwoLineSwitch() },
                R.id.composeTwoLineSwitchImage to { ComposeTwoLineSwitchImage() },
                R.id.composeTwoLineSwitchPill to { ComposeTwoLineSwitchPill() },
                R.id.composeTwoLineSwitchTruncated to { ComposeTwoLineSwitchTruncated() },
                R.id.composeTwoLineDisabled to { ComposeTwoLineDisabled() },
                R.id.composeTwoLineSwitchChecked to { ComposeTwoLineSwitchChecked() },
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
    class SnackbarComponentViewHolder(parent: ViewGroup) :
        ComponentViewHolder(inflate(parent, R.layout.component_snackbar)) {

        init {
            val container: FrameLayout = view.findViewById(R.id.snackbar_container)
            val snackbarView =
                Snackbar.make(container, "This is a Snackbar message", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Action") { Snackbar.make(container, "Action pressed", Snackbar.LENGTH_LONG).show() }
                    .view
            (snackbarView.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER

            container.addView(snackbarView)
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
                Component.TOP_APP_BAR -> TopAppBarComponentViewHolder(parent)
                Component.SWITCH -> SwitchComponentViewHolder(parent, isDarkTheme)
                Component.RADIO_BUTTON -> RadioButtonComponentViewHolder(parent, isDarkTheme)
                Component.CHECKBOX -> CheckboxComponentViewHolder(parent, isDarkTheme)
                Component.SLIDER -> SliderComponentViewHolder(parent)
                Component.SNACKBAR -> SnackbarComponentViewHolder(parent)
                Component.INFO_PANEL -> InfoPanelComponentViewHolder(parent, isDarkTheme)
                Component.REMOTE_MESSAGE -> RemoteMessageComponentViewHolder(parent, isDarkTheme)
                Component.SEARCH_BAR -> SearchBarComponentViewHolder(parent)
                Component.MENU_ITEM -> MenuItemComponentViewHolder(parent)
                Component.POPUP_MENU_ITEM -> PopupMenuItemComponentViewHolder(parent)
                Component.SECTION_HEADER_LIST_ITEM -> HeaderSectionComponentViewHolder(parent)
                Component.SINGLE_LINE_LIST_ITEM -> OneLineListItemComponentViewHolder(parent, isDarkTheme)
                Component.TWO_LINE_LIST_ITEM -> TwoLineItemComponentViewHolder(parent, isDarkTheme)
                Component.SECTION_DIVIDER -> DividerComponentViewHolder(parent, isDarkTheme)
                Component.CARD -> CardComponentViewHolder(parent, isDarkTheme)
                Component.SETTINGS_LIST_ITEM -> SettingsListItemComponentViewHolder(parent, isDarkTheme)
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

@Composable
private fun ComposeOneLineSimple() {
    ComposeCaption()
    DaxOneLineListItem(primaryText = "This is a simple item", onClick = {})
}

@Composable
private fun ComposeOneLineMediumImage() {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item with Medium Leading Image",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small, tint = null) },
        onClick = {},
    )
}

@Composable
private fun ComposeOneLineMediumImageBg() {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item with Medium Leading Image",
        leadingContent = {
            Icon(
                painterResource(CommonR.drawable.ic_dax_icon),
                null,
                size = DaxListItemIconSize.Small,
                background = DaxListItemIconBackground.Circular,
                tint = null,
            )
        },
        onClick = {},
    )
}

@Composable
private fun ComposeOneLineLargeImage() {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item with Large Leading Image",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Large, tint = null) },
        onClick = {},
    )
}

@Composable
private fun ComposeOneLineLargeImageBg() {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item with Large Leading Image",
        leadingContent = {
            Icon(
                painterResource(CommonR.drawable.ic_dax_icon),
                null,
                size = DaxListItemIconSize.Large,
                background = DaxListItemIconBackground.Circular,
                tint = null,
            )
        },
        onClick = {},
    )
}

@Composable
private fun ComposeOneLineTrailingIcon() {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item With Trailing Icon",
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = {}) },
        onClick = {},
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
private fun ComposeOneLineLeadingTrailing() {
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
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = {}) },
        onClick = {},
    )
}

@Composable
private fun ComposeOneLineSwitch() {
    ComposeCaption()
    var checked by remember { mutableStateOf(false) }
    DaxOneLineListItem(
        primaryText = "Item with Switch Item",
        trailingContent = { Switch(checked = checked, onCheckedChange = { checked = it }) },
    )
}

@Composable
private fun ComposeOneLineDisabled() {
    ComposeCaption()
    DaxOneLineListItem(primaryText = "Item disabled", enabled = false, onClick = {})
}

@Composable
private fun ComposeOneLineDestructive() {
    ComposeCaption()
    DaxOneLineListItem(
        primaryText = "Item with custom text color",
        primaryTextColor = DuckDuckGoTheme.textColors.destructive,
        onClick = {},
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
    DaxOneLineListItem(primaryText = "Item with New Pill", pillText = "New", onClick = {})
}

@Composable
private fun ComposeOneLineExtras() {
    ComposeCaption(stringResource(R.string.dsShowcaseCaptionComposeOnly))
    DaxOneLineListItem(
        primaryText = "Favicon (untinted image)",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_ddg_logo), null, size = DaxListItemIconSize.Large, tint = null) },
        onClick = {},
    )
    DaxOneLineListItem(
        primaryText = "Decorative trailing icon",
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null) },
    )
    DaxOneLineListItem(
        primaryText = "Trailing button",
        trailingContent = { Button(text = "Action", onClick = {}) },
    )
    DaxOneLineListItem(primaryText = "With Beta pill", pillText = "Beta", onClick = {})
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
private fun ComposeTwoLinePlain() {
    ComposeCaption()
    DaxTwoLineListItem(primaryText = "Two Line Item", secondaryText = "Without Image", onClick = {})
}

@Composable
private fun ComposeTwoLineImage() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Leading Image",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
        onClick = {},
    )
}

@Composable
private fun ComposeTwoLineMediumTrailing() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Medium Leading and Trailing Image",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = {}) },
        onClick = {},
    )
}

@Composable
private fun ComposeTwoLineMediumTrailingBg() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Medium Leading Background and Trailing Image",
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
private fun ComposeTwoLineLargeTrailing() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Large Leading and Trailing Image",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Large) },
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = {}) },
        onClick = {},
    )
}

@Composable
private fun ComposeTwoLineLargeTrailingBg() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Large Leading Background and Trailing Image",
        leadingContent = {
            Icon(
                painterResource(CommonR.drawable.ic_globe_24),
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
private fun ComposeTwoLineTrailing() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Trailing Image",
        trailingContent = { Icon(painterResource(CommonR.drawable.ic_menu_vertical_24), "Overflow", onClick = {}) },
        onClick = {},
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
private fun ComposeTwoLineBetaPill() {
    ComposeCaption()
    DaxTwoLineListItem(primaryText = "Two Line Item", secondaryText = "With Beta Pill", pillText = "Beta", onClick = {})
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
private fun ComposeTwoLineSwitch() {
    ComposeCaption()
    var checked by remember { mutableStateOf(false) }
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Switch",
        trailingContent = { Switch(checked = checked, onCheckedChange = { checked = it }) },
    )
}

@Composable
private fun ComposeTwoLineSwitchImage() {
    ComposeCaption()
    var checked by remember { mutableStateOf(false) }
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Leading Image and Switch",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
        trailingContent = { Switch(checked = checked, onCheckedChange = { checked = it }) },
    )
}

@Composable
private fun ComposeTwoLineSwitchPill() {
    ComposeCaption()
    var checked by remember { mutableStateOf(false) }
    DaxTwoLineListItem(
        primaryText = "Two Line Item",
        secondaryText = "With Beta Pill and Switch",
        pillText = "Beta",
        trailingContent = { Switch(checked = checked, onCheckedChange = { checked = it }) },
    )
}

@Composable
private fun ComposeTwoLineSwitchTruncated() {
    ComposeCaption()
    var checked by remember { mutableStateOf(false) }
    DaxTwoLineListItem(
        primaryText = "Two Line Item Two Line Item Two Line Item Two Line Item",
        secondaryText = "In disabled state",
        pillText = "Beta",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
        trailingContent = { Switch(checked = checked, onCheckedChange = { checked = it }) },
        primaryMaxLines = 1,
    )
}

@Composable
private fun ComposeTwoLineDisabled() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item Two Line Item Two Line Item Two Line Item",
        secondaryText = "In disabled state",
        pillText = "Beta",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
        trailingContent = { Switch(checked = false, onCheckedChange = {}) },
        enabled = false,
    )
}

@Composable
private fun ComposeTwoLineSwitchChecked() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item Two",
        secondaryText = "Checked in disabled state",
        pillText = "Whatever",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
        trailingContent = { Switch(checked = true, onCheckedChange = {}) },
        enabled = false,
    )
}

@Composable
private fun ComposeTwoLineSwitchDisabledChecked() {
    ComposeCaption()
    DaxTwoLineListItem(
        primaryText = "Two Line Item Two",
        secondaryText = "Checked with switch in disabled state",
        pillText = "Beta",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_globe_24), null, size = DaxListItemIconSize.Small) },
        trailingContent = { Switch(checked = true, onCheckedChange = {}, enabled = false) },
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
        status = Status.Off,
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small, tint = null) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsAlwaysOn() {
    ComposeCaption()
    DaxSettingsListItem(
        primaryText = "Settings List Item Always On",
        status = Status.AlwaysOn,
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small, tint = null) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsOn() {
    ComposeCaption()
    DaxSettingsListItem(
        primaryText = "Settings List Item on",
        status = Status.On,
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small, tint = null) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsOff() {
    ComposeCaption()
    DaxSettingsListItem(
        primaryText = "Settings List Item Off",
        status = Status.Off,
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small, tint = null) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsBeta() {
    ComposeCaption()
    DaxSettingsListItem(
        primaryText = "Settings List Item with Beta Pill",
        status = Status.On,
        pillText = "Beta",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small, tint = null) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsBetaLongText() {
    ComposeCaption()
    DaxSettingsListItem(
        primaryText = "Settings List Item with Beta Pill and a very long piece of text that should hopefully wrap",
        status = Status.On,
        pillText = "Beta",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small, tint = null) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsNew() {
    ComposeCaption()
    DaxSettingsListItem(
        primaryText = "Settings List Item with New Pill",
        status = Status.On,
        pillText = "New",
        leadingContent = { Icon(painterResource(CommonR.drawable.ic_dax_icon), null, size = DaxListItemIconSize.Small, tint = null) },
        onClick = {},
    )
}

@Composable
private fun ComposeSettingsExtras() {
    ComposeCaption(stringResource(R.string.dsShowcaseCaptionComposeOnly))
    DaxSettingsListItem(
        primaryText = "Leading icon over circular background",
        status = Status.On,
        leadingContent = {
            Icon(
                painterResource(CommonR.drawable.ic_dax_icon),
                null,
                size = DaxListItemIconSize.Small,
                background = DaxListItemIconBackground.Circular,
                tint = null,
            )
        },
        onClick = {},
    )
    DaxSettingsListItem(primaryText = "Disabled", status = Status.Off, enabled = false, onClick = {})
}
