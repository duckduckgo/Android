package com.test

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import dagger.BindsInstance
import dagger.SingleInstanceIn
import dagger.android.AndroidInjector

@SingleInstanceIn(scope = FragmentScope::class)
@ContributesSubcomponent(
  scope = FragmentScope::class,
  parentScope = ActivityScope::class,
)
public interface MyFragment_SubComponent : AndroidInjector<MyFragment> {
  @ContributesSubcomponent.Factory
  public interface Factory : AndroidInjector.Factory<MyFragment, MyFragment_SubComponent> {
    public override fun create(@BindsInstance instance: MyFragment): MyFragment_SubComponent
  }

  @ContributesTo(scope = ActivityScope::class)
  public fun interface ParentComponent {
    public fun provideMyFragmentComponentFactory(): Factory
  }
}
