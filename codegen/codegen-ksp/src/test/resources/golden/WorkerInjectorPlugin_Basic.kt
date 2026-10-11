package com.test

import androidx.work.ListenableWorker
import com.duckduckgo.common.utils.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import javax.inject.Inject
import kotlin.Boolean

@ContributesIntoSet(AppScope::class)
public class TestWorker_WorkerInjectorPlugin @Inject constructor(
  private val settingsDataStore: () -> SettingsDataStore,
  private val clearDataAction: () -> ClearDataAction,
) : WorkerInjectorPlugin {
  public override fun inject(worker: ListenableWorker): Boolean {
    if (worker is TestWorker) {
        worker.settingsDataStore = settingsDataStore()
        worker.clearDataAction = clearDataAction()
        return true
    }
    return false
  }
}
