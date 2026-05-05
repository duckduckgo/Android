package com.test

import androidx.work.ListenableWorker
import com.duckduckgo.common.utils.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import kotlin.Boolean

@ContributesMultibinding(AppScope::class)
public class QualifiedWorker_WorkerInjectorPlugin @Inject constructor(
  @Named(value = "foo")
  private val myService: Provider<MyService>,
) : WorkerInjectorPlugin {
  public override fun inject(worker: ListenableWorker): Boolean {
    if (worker is QualifiedWorker) {
        worker.myService = myService.get()
        return true
    }
    return false
  }
}
