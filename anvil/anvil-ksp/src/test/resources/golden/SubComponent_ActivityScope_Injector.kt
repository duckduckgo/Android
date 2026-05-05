package com.test

import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesTo
import kotlin.Unit

@ContributesTo(scope = ActivityScope::class)
public interface MyActivity_Injector {
  public fun inject(activity: MyActivity): Unit
}
