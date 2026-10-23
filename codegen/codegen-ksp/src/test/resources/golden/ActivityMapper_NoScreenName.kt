package com.test

import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.ContributesIntoSet
import java.lang.Class
import javax.inject.Inject

@ContributesIntoSet(scope = AppScope::class)
public class TestActivity_TestParams_Mapper @Inject constructor() :
    GlobalActivityStarter.ParamToActivityMapper {
  private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

  public override fun map(activityParams: GlobalActivityStarter.ActivityParams):
      Class<out AppCompatActivity>? = if (activityParams is TestParams) {
      TestActivity::class.java
  } else {
      null
  }

  public override fun map(deeplinkActivityParams: GlobalActivityStarter.DeeplinkActivityParams):
      GlobalActivityStarter.ActivityParams? = null
}
