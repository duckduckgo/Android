package com.test

import androidx.lifecycle.ViewModel
import com.duckduckgo.common.utils.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.ActivityScope
import dev.zacsweers.metro.ContributesIntoSet
import java.lang.Class
import javax.inject.Inject
import javax.inject.Provider

@ContributesIntoSet(ActivityScope::class)
public class MyViewModel_ViewModelFactory @Inject constructor(
  private val viewModelProvider: Provider<MyViewModel>,
) : ViewModelFactoryPlugin {
  public override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
    with(modelClass) {
        return when {
            isAssignableFrom(MyViewModel::class.java) -> (viewModelProvider.get() as T)
            else -> null
        }
    }
  }
}
