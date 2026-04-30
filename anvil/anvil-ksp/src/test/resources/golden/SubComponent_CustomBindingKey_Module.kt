package com.test

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.android.AndroidInjector
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@Module
@ContributesTo(scope = AppScope::class)
public abstract class MyService_SubComponent_Module {
  @Binds
  @IntoMap
  @ClassKey(CustomKey::class)
  public abstract fun bindMyService_SubComponentFactory(factory: MyService_SubComponent.Factory):
      AndroidInjector.Factory<*, *>
}
