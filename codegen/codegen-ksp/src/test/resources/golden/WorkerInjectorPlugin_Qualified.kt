package com.test

import androidx.work.ListenableWorker
import com.duckduckgo.common.utils.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import javax.inject.Inject
import javax.inject.Named
import kotlin.Boolean

@ContributesIntoSet(AppScope::class)
public class QualifiedWorker_WorkerInjectorPlugin @Inject constructor(
  @Named(value = "foo")
  private val myService: () -> MyService,
) : WorkerInjectorPlugin {
  public override fun inject(worker: ListenableWorker): Boolean {
    if (worker is QualifiedWorker) {
        worker.myService = myService()
        return true
    }
    return false
  }
}
