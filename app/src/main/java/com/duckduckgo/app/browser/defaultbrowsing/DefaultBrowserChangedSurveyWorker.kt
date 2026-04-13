package com.duckduckgo.app.browser.defaultbrowsing

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class DefaultBrowserChangedSurveyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @Inject
    lateinit var defaultBrowserChangedSurveyManager: DefaultBrowserChangedSurveyManager

    @Inject
    lateinit var notificationSender: NotificationSender

    @Inject
    lateinit var defaultBrowserChangedSurveyNotification: DefaultBrowserChangedSurveyNotification

    @Inject
    lateinit var workManager: WorkManager

    override suspend fun doWork(): Result {
        logcat { "DefaultBrowserChangedSurveyWorker running periodic check" }

        if (!defaultBrowserChangedSurveyManager.shouldTriggerSurvey()) {
            logcat { "Survey not needed" }
            return Result.success()
        }

        logcat { "Default-browser-changed survey trigger condition met, sending notification" }
        defaultBrowserChangedSurveyManager.markSurveyShown()
        notificationSender.sendNotification(defaultBrowserChangedSurveyNotification)
        return Result.success()
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class DefaultBrowserChangedSurveyWorkerScheduler @Inject constructor(
    private val workManager: WorkManager,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        val workerRequest =
            PeriodicWorkRequestBuilder<DefaultBrowserChangedSurveyWorker>(12, TimeUnit.HOURS)
                .addTag(WORKER_TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()
        workManager.enqueueUniquePeriodicWork(
            WORKER_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            workerRequest,
        )
    }

    companion object {
        const val WORKER_TAG = "DEFAULT_BROWSER_CHANGED_SURVEY_WORKER_TAG"
    }
}
