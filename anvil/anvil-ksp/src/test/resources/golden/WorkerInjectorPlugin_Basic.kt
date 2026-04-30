package com.test

import androidx.work.ListenableWorker
import com.duckduckgo.common.utils.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Provider
import kotlin.Boolean

@ContributesMultibinding(AppScope::class)
public class TestWorker_WorkerInjectorPlugin @Inject constructor(
  private val settingsDataStore: Provider<SettingsDataStore>,
  private val clearDataAction: Provider<ClearDataAction>,
) : WorkerInjectorPlugin {
  public override fun inject(worker: ListenableWorker): Boolean {
    if (worker is TestWorker) {
        worker.settingsDataStore = settingsDataStore.get()
        worker.clearDataAction = clearDataAction.get()
        return true
    }
    return false
  }
}
