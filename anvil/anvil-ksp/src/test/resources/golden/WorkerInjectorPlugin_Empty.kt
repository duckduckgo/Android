package com.test

import androidx.work.ListenableWorker
import com.duckduckgo.common.utils.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.Boolean

@ContributesMultibinding(AppScope::class)
public class EmptyWorker_WorkerInjectorPlugin @Inject constructor() : WorkerInjectorPlugin {
  public override fun inject(worker: ListenableWorker): Boolean {
    if (worker is EmptyWorker) {
        return true
    }
    return false
  }
}
