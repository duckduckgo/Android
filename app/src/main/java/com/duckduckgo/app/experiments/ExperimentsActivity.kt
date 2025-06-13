package com.duckduckgo.app.experiments

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.databinding.ActivityExperimentsBinding
import com.duckduckgo.app.experiments.ExperimentsScreen.Default
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(Default::class, screenName = "experiments")
class ExperimentsActivity : DuckDuckGoActivity() {

    private val viewModel: ExperimentsViewModel by bindViewModel()
    private val binding: ActivityExperimentsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState.let {
                    binding.duckDiveExperiment.quietlySetIsChecked(viewState.isDuckDiveExperimentEnabled) { _, isChecked ->
                        viewModel.onDuckDiveExperimentToggled(
                            isChecked,
                        )
                    }
                }
            }.launchIn(lifecycleScope)
    }
}
