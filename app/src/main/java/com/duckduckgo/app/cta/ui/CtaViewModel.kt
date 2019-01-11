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

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.view.View
import androidx.annotation.DrawableRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.feedback.db.SurveyDao
import com.duckduckgo.app.feedback.model.Survey
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.include_cta_buttons.view.*
import kotlinx.android.synthetic.main.include_cta_content.view.*
import javax.inject.Inject

class CtaViewModel @Inject constructor(
    private val context: Context,
    private val appInstallStore: AppInstallStore,
    private val pixel: Pixel,
    private val surveyDao: SurveyDao,
    private val dismissedCtaDao: DismissedCtaDao
) {

    val ctaViewState: MutableLiveData<CtaViewState> = MutableLiveData()
    val surveyLiveData: LiveData<Survey> = surveyDao.getLiveScheduled()
    private var activeSurvey: Survey? = null

    data class CtaViewState(
        val cta: CtaConfiguration? = null
    )

    init {
        ctaViewState.value = CtaViewState()
    }

    fun onSurveyChanged(survey: Survey?) {
        activeSurvey = survey
        selectNextCta()
    }

    private fun selectNextCta() {
        surveyCta()?.let {
            ctaViewState.value = ctaViewState.value!!.copy(cta = it)
            return@let
        }

        Schedulers.io().scheduleDirect {
            if (!dismissedCtaDao.exists(CtaId.ADD_WIDGET)) {
                ctaViewState.postValue(ctaViewState.value!!.copy(CtaConfiguration.AddWidget(context)))
            }
        }
    }

    private fun surveyCta(): CtaConfiguration.Survey? {
        val survey = activeSurvey
        if (survey?.url != null) {
            val showOnDay = survey.daysInstalled?.toLong()
            val daysInstalled = appInstallStore.daysInstalled()
            if (showOnDay == null || showOnDay == daysInstalled) {
                return CtaConfiguration.Survey(context, survey)
            }
        }
        return null
    }

    fun onCtaShown() {
        val cta = ctaViewState.value?.cta ?: return
        pixel.fire(cta.shownPixel)
    }

    fun onCtaDismissed() {
        val cta = ctaViewState.value?.cta ?: return
        pixel.fire(cta.cancelPixel)

        when (cta) {
            is CtaConfiguration.Survey -> {
                Schedulers.io().scheduleDirect {
                    surveyDao.cancelScheduledSurveys()
                }
            }
            else -> {
                Schedulers.io().scheduleDirect {
                    dismissedCtaDao.insert(DismissedCta(cta.ctaId))
                }
            }
        }

        ctaViewState.value = ctaViewState.value?.copy(cta = null)
        selectNextCta()
    }

    fun onCtaLaunched() {
        val cta = ctaViewState.value?.cta ?: return
        pixel.fire(cta.okPixel)
    }
}

enum class CtaId {
    SURVEY,
    ADD_WIDGET
}

sealed class CtaConfiguration(
    open val ctaId: CtaId,
    open @DrawableRes val image: Int,
    open val title: String,
    open val description: String,
    open val okButton: String,
    open val dismissButton: String,
    open val shownPixel: Pixel.PixelName,
    open val okPixel: Pixel.PixelName,
    open val cancelPixel: Pixel.PixelName
) {

    class Survey(context: Context, val survey: com.duckduckgo.app.feedback.model.Survey) : CtaConfiguration(
        CtaId.SURVEY,
        R.drawable.survey_cta_icon,
        context.getString(R.string.surveyCtaTitle),
        context.getString(R.string.surveyCtaDescription),
        context.getString(R.string.surveyCtaLaunchButton),
        context.getString(R.string.surveyCtaDismissButton),
        SURVEY_CTA_SHOWN,
        SURVEY_CTA_LAUNCHED,
        SURVEY_CTA_DISMISSED
    )

    class AddWidget(context: Context) : CtaConfiguration(
        CtaId.ADD_WIDGET,
        R.drawable.add_widget_cta_icon,
        context.getString(R.string.addWidgetCtaTitle),
        context.getString(R.string.addWidgetCtaDescription),
        context.getString(R.string.addWidgetCtaLaunchButton),
        context.getString(R.string.addWidgetCtaDismissButton),
        ADD_WIDGET_CTA_SHOWN,
        if (context.supportsAutomaticWidgets) ADD_WIDGET_CTA_LAUNCHED_AUTO else ADD_WIDGET_CTA_LAUNCHED_MANUAL,
        ADD_WIDGET_CTA_DISMISSED
    )

    fun apply(view: View) {
        view.ctaIcon.setImageResource(image)
        view.ctaTitle.text = title
        view.ctaSubtitle.text = description
        view.ctaOkButton.text = okButton
        view.ctaDismissButton.text = dismissButton
    }
}

val Context.supportsAutomaticWidgets: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported

