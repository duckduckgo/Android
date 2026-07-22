package com.test

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Named
import retrofit2.Retrofit

@Module
@ContributesTo(scope = AppScope::class)
public object TestNonCachingService_Module {
  @Provides
  public fun providesTestNonCachingService(@Named(value = "nonCaching") retrofit: Retrofit):
      TestNonCachingService = retrofit.create(TestNonCachingService::class.java)
}
