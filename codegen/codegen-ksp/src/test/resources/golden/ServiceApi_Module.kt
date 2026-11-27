package com.test

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import javax.inject.Named
import retrofit2.Retrofit

@BindingContainer
@ContributesTo(scope = AppScope::class)
public object TestService_Module {
  @Provides
  public fun providesTestService(@Named(value = "api") retrofit: Retrofit): TestService =
      retrofit.create(TestService::class.java)
}
