package com.test

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Named
import retrofit2.Retrofit

@Module
@ContributesTo(scope = AppScope::class)
public object TestBoundTypeService_Module {
  @Provides
  public fun providesMyBoundType(@Named(value = "api") retrofit: Retrofit): MyBoundType =
      retrofit.create(MyBoundType::class.java)
}
