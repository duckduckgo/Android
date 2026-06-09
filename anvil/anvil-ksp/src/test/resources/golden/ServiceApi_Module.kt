package com.test

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Named
import retrofit2.Retrofit

@Module
@ContributesTo(scope = AppScope::class)
public object TestService_Module {
  @Provides
  public fun providesTestService(@Named(value = "api") retrofit: Retrofit): TestService =
      retrofit.create(TestService::class.java)
}
