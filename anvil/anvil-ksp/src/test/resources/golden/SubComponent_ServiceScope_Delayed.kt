package com.test

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ServiceScope
import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import dagger.BindsInstance
import dagger.SingleInstanceIn
import dagger.android.AndroidInjector

@SingleInstanceIn(scope = ServiceScope::class)
@ContributesSubcomponent(
  scope = ServiceScope::class,
  parentScope = AppScope::class,
)
public interface MyService_SubComponent : AndroidInjector<MyService> {
  @ContributesSubcomponent.Factory
  public interface Factory : AndroidInjector.Factory<MyService, MyService_SubComponent> {
    public override fun create(@BindsInstance instance: MyService): MyService_SubComponent
  }

  @ContributesTo(scope = AppScope::class)
  public fun interface ParentComponent {
    public fun provideMyServiceComponentFactory(): Factory
  }
}
