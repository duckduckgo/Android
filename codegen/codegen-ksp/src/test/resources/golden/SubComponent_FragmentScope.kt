package com.test

import com.duckduckgo.di.android.AndroidInjector
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.FragmentScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides

@GraphExtension(scope = FragmentScope::class)
public interface MyFragment_SubComponent : AndroidInjector<MyFragment> {
  @GraphExtension.Factory
  public interface Factory : AndroidInjector.Factory<MyFragment, MyFragment_SubComponent> {
    public override fun create(@Provides instance: MyFragment): MyFragment_SubComponent
  }

  @ContributesTo(scope = ActivityScope::class)
  public fun interface ParentComponent {
    public fun provideMyFragmentComponentFactory(): Factory
  }
}
