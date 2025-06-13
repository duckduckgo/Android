package com.duckduckgo.app.experiments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class ExperimentsViewModel @Inject constructor(
    private val experimentsDataStore: ExperimentsDataStore,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    data class ViewState(
        val isDuckDiveExperimentEnabled: Boolean = false,
    )

    val viewState = experimentsDataStore.isDuckDiveExperimentEnabled.map {
        ViewState(isDuckDiveExperimentEnabled = it)
    }

    fun onDuckDiveExperimentToggled(isChecked: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            experimentsDataStore.setDuckDiveExperimentEnabled(isChecked)
        }
    }
}
