package com.test

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ServiceScope
import dagger.android.AndroidInjector
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides

@GraphExtension(scope = ServiceScope::class)
public interface MyService_SubComponent : AndroidInjector<MyService> {
  @GraphExtension.Factory
  public interface Factory : AndroidInjector.Factory<MyService, MyService_SubComponent> {
    public override fun create(@Provides instance: MyService): MyService_SubComponent
  }

  @ContributesTo(scope = AppScope::class)
  public fun interface ParentComponent {
    public fun provideMyServiceComponentFactory(): Factory
  }
}
