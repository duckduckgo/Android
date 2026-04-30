package com.test

import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.lang.Class
import javax.inject.Inject

@ContributesMultibinding(scope = AppScope::class)
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
      GlobalActivityStarter.ActivityParams? {
    val screenName = deeplinkActivityParams.screenName
    if (screenName.isNullOrEmpty()) {
        return null
    }

    val definedScreenName = "example"
    if (definedScreenName.isNullOrEmpty()) {
        return null
    }

    return if (screenName == definedScreenName) {
        if (deeplinkActivityParams.jsonArguments.isEmpty()) {
            val instance = tryCreateObjectInstance(TestParams::class.java)
            if (instance != null) {
                return instance
            }
        }
        tryCreateActivityParams(TestParams::class.java, deeplinkActivityParams)
    } else {
        null
    }
  }

  private fun tryCreateObjectInstance(clazz: Class<out GlobalActivityStarter.ActivityParams>):
      GlobalActivityStarter.ActivityParams? = kotlin.runCatching {
      Types.getRawType(clazz).kotlin.objectInstance as GlobalActivityStarter.ActivityParams
  }.getOrNull()

  private fun tryCreateActivityParams(clazz: Class<out GlobalActivityStarter.ActivityParams>,
      deeplinkActivityParams: GlobalActivityStarter.DeeplinkActivityParams):
      GlobalActivityStarter.ActivityParams? = kotlin.runCatching {
      moshi.adapter(clazz).fromJson(deeplinkActivityParams.jsonArguments)
  }.getOrNull()
}
