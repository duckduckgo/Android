/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.cta.ui

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.cta.ui.CtaConfiguration.*
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.include_cta_buttons.view.*
import kotlinx.android.synthetic.main.include_cta_content.view.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CtaViewModel @Inject constructor(
    private val appInstallStore: AppInstallStore,
    private val pixel: Pixel,
    private val surveyDao: SurveyDao,
    private val widgetCapabilities: WidgetCapabilities,
    private val dismissedCtaDao: DismissedCtaDao
) {

    data class CtaViewState(
        val cta: CtaConfiguration? = null
    )

    val ctaViewState: MutableLiveData<CtaViewState> = MutableLiveData()
    val surveyLiveData: LiveData<Survey> = surveyDao.getLiveScheduled()

    private val currentViewState: CtaViewState
        get() = ctaViewState.value!!

    private var activeSurvey: Survey? = null

    init {
        ctaViewState.value = CtaViewState()
    }

    fun onSurveyChanged(survey: Survey?) {
        activeSurvey = survey
        refreshCta()
    }

    fun refreshCta() {
        surveyCta()?.let {
            ctaViewState.postValue(currentViewState.copy(cta = it))
            return
        }

        Schedulers.io().scheduleDirect {
            if (canShowWidgetCta()) {
                val ctaType = if (widgetCapabilities.supportsAutomaticWidgetAdd) AddWidgetAuto else AddWidgetInstructions
                ctaViewState.postValue(currentViewState.copy(cta = ctaType))
            } else {
                ctaViewState.postValue(currentViewState.copy(cta = null))
            }
        }
    }

    private fun surveyCta(): CtaConfiguration.Survey? {
        val survey = activeSurvey
        if (survey?.url != null) {
            val showOnDay = survey.daysInstalled?.toLong()
            val daysInstalled = appInstallStore.daysInstalled()
            if (showOnDay == null || showOnDay == daysInstalled) {
                return Survey(survey)
            }
        }
        return null
    }

    @WorkerThread
    private fun canShowWidgetCta(): Boolean {
        return widgetCapabilities.supportsStandardWidgetAdd &&
                !widgetCapabilities.hasInstalledWidgets &&
                !dismissedCtaDao.exists(CtaId.ADD_WIDGET)
    }

    fun onCtaShown() {
        currentViewState.cta?.shownPixel?.let {
            pixel.fire(it)
        }
    }

    fun onCtaDismissed() {
        val cta = currentViewState.cta ?: return
        cta.cancelPixel?.let {
            pixel.fire(it)
        }

        Schedulers.io().scheduleDirect {
            when (cta) {
                is CtaConfiguration.Survey -> {
                    activeSurvey = null
                    surveyDao.cancelScheduledSurveys()
                }
                else -> {
                    dismissedCtaDao.insert(DismissedCta(cta.ctaId))
                }
            }
            ctaViewState.postValue(currentViewState.copy(cta = null))
            refreshCta()
        }
    }

    fun onCtaLaunched() {
        currentViewState.cta?.okPixel?.let {
            pixel.fire(it)
        }
    }
}

sealed class CtaConfiguration(
    open val ctaId: CtaId,
    @DrawableRes open val image: Int,
    @StringRes open val title: Int,
    @StringRes open val description: Int,
    @StringRes open val okButton: Int,
    @StringRes open val dismissButton: Int,
    open val shownPixel: Pixel.PixelName?,
    open val okPixel: Pixel.PixelName?,
    open val cancelPixel: Pixel.PixelName?
) {

    data class Survey(val survey: com.duckduckgo.app.survey.model.Survey) : CtaConfiguration(
        CtaId.SURVEY,
        R.drawable.survey_cta_icon,
        R.string.surveyCtaTitle,
        R.string.surveyCtaDescription,
        R.string.surveyCtaLaunchButton,
        R.string.surveyCtaDismissButton,
        SURVEY_CTA_SHOWN,
        SURVEY_CTA_LAUNCHED,
        SURVEY_CTA_DISMISSED
    )

    object AddWidgetAuto : CtaConfiguration(
        CtaId.ADD_WIDGET,
        R.drawable.add_widget_cta_icon,
        R.string.addWidgetCtaTitle,
        R.string.addWidgetCtaDescription,
        R.string.addWidgetCtaAutoLaunchButton,
        R.string.addWidgetCtaDismissButton,
        null,
        null,
        null
    )

    object AddWidgetInstructions : CtaConfiguration(
        CtaId.ADD_WIDGET,
        R.drawable.add_widget_cta_icon,
        R.string.addWidgetCtaTitle,
        R.string.addWidgetCtaDescription,
        R.string.addWidgetCtaInstructionsLaunchButton,
        R.string.addWidgetCtaDismissButton,
        null,
        null,
        null
    )

    fun apply(view: View) {
        view.ctaIcon.setImageResource(image)
        view.ctaTitle.text = view.context.getString(title)
        view.ctaSubtitle.text = view.context.getString(description)
        view.ctaOkButton.text = view.context.getString(okButton)
        view.ctaDismissButton.text = view.context.getString(dismissButton)
    }
}