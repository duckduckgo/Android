package com.test

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import javax.inject.Named
import retrofit2.Retrofit

@BindingContainer
@ContributesTo(scope = AppScope::class)
public object TestBoundTypeService_Module {
  @Provides
  public fun providesMyBoundType(@Named(value = "api") retrofit: Retrofit): MyBoundType =
      retrofit.create(MyBoundType::class.java)
}
