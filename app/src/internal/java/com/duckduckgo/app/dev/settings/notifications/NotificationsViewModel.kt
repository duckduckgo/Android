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

package com.duckduckgo.app.dev.settings.notifications

import android.app.Notification
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.dev.settings.notifications.NotificationViewModel.ViewState.NotificationItem
import com.duckduckgo.app.notification.NotificationFactory
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.app.survey.api.SurveyRepository
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.model.Survey.Status.SCHEDULED
import com.duckduckgo.app.survey.notification.SurveyAvailableNotification
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.impl.notification.NetPDisabledNotificationBuilder
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesViewModel(ActivityScope::class)
class NotificationViewModel @Inject constructor(
    private val applicationContext: Context,
    private val dispatcher: DispatcherProvider,
    private val schedulableNotificationPluginPoint: PluginPoint<SchedulableNotificationPlugin>,
    private val factory: NotificationFactory,
    private val surveyRepository: SurveyRepository,
    private val netPDisabledNotificationBuilder: NetPDisabledNotificationBuilder,
) : ViewModel() {

    data class ViewState(
        val scheduledNotifications: List<NotificationItem> = emptyList(),
        val vpnNotifications: List<NotificationItem> = emptyList(),
    ) {

        data class NotificationItem(
            val id: Int,
            val title: String,
            val subtitle: String,
            val notification: Notification
        )
    }

    sealed class Command {
        data class TriggerNotification(val notificationItem: NotificationItem) : Command()
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val _command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    val command = _command.receiveAsFlow()

    init {
        viewModelScope.launch {
            val scheduledNotificationItems = schedulableNotificationPluginPoint.getPlugins().map { plugin ->

                // The survey notification will crash if we do not have a survey in the database
                if (plugin.getSchedulableNotification().javaClass == SurveyAvailableNotification::class.java) {
                    withContext(dispatcher.io()) {
                        addTestSurvey()
                    }
                }

                // the survey intent hits the DB, so we need to do this on IO
                val launchIntent = withContext(dispatcher.io()) { plugin.getLaunchIntent() }

                NotificationItem(
                    id = plugin.getSpecification().systemId,
                    title = plugin.getSpecification().title,
                    subtitle = plugin.getSpecification().description,
                    notification = factory.createNotification(plugin.getSpecification(), launchIntent, null),
                )
            }

            val netPDisabledNotificationItem = NotificationItem(
                id = 0,
                title = "NetP Disabled",
                subtitle = "NetP is disabled",
                notification = netPDisabledNotificationBuilder.buildVpnAccessRevokedNotification(applicationContext),
            )

            _viewState.update {
                it.copy(
                    scheduledNotifications = scheduledNotificationItems,
                    vpnNotifications = listOf(netPDisabledNotificationItem),
                )
            }
        }
    }

    private fun addTestSurvey() {
        surveyRepository.persistSurvey(
            Survey(
                "testSurveyId",
                "https://youtu.be/dQw4w9WgXcQ?si=iztopgFbXoWUnoOE",
                daysInstalled = 1,
                status = SCHEDULED,
            ),
        )
    }

    fun onNotificationItemClick(notificationItem: NotificationItem) {
        viewModelScope.launch {
            _command.send(Command.TriggerNotification(notificationItem))
        }
    }
}
