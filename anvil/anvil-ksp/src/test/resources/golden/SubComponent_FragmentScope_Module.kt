package com.test

import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.android.AndroidInjector
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@Module
@ContributesTo(scope = ActivityScope::class)
public abstract class MyFragment_SubComponent_Module {
  @Binds
  @IntoMap
  @ClassKey(MyFragment::class)
  public abstract fun bindMyFragment_SubComponentFactory(factory: MyFragment_SubComponent.Factory):
      AndroidInjector.Factory<*, *>
}
