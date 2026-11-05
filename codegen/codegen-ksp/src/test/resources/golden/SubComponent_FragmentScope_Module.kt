package com.test

import com.duckduckgo.di.android.AndroidInjector
import com.duckduckgo.di.scopes.ActivityScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap

@BindingContainer
@ContributesTo(scope = ActivityScope::class)
public abstract class MyFragment_SubComponent_Module {
  @Binds
  @IntoMap
  @ClassKey(MyFragment::class)
  public abstract fun bindMyFragment_SubComponentFactory(factory: MyFragment_SubComponent.Factory):
      AndroidInjector.Factory<*, *>
}
