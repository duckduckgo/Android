package com.duckduckgo.app.experiments

import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

/**
 * Use this model to launch the Experiments screen
 */
sealed class ExperimentsScreen : ActivityParams {
    /**
     * Use this model to launch the Experiments screen
     */
    data object Default : ExperimentsScreen() {
        private fun readResolve(): Any = Default
    }
}
