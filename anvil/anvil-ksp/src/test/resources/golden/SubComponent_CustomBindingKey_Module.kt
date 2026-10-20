package com.test

import com.duckduckgo.di.scopes.AppScope
import dagger.android.AndroidInjector
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap

@BindingContainer
@ContributesTo(scope = AppScope::class)
public abstract class MyService_SubComponent_Module {
  @Binds
  @IntoMap
  @ClassKey(CustomKey::class)
  public abstract fun bindMyService_SubComponentFactory(factory: MyService_SubComponent.Factory):
      AndroidInjector.Factory<*, *>
}
