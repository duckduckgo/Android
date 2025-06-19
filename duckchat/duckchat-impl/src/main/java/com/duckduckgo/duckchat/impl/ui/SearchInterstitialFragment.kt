package com.duckduckgo.duckchat.impl.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.transition.Transition
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.common.utils.extensions.showKeyboard
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentSearchInterstitialBinding
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.newtabpage.api.NewTabPagePlugin
import javax.inject.Inject
import kotlinx.coroutines.launch

@InjectWith(FragmentScope::class)
class SearchInterstitialFragment : DuckDuckGoFragment(R.layout.fragment_search_interstitial) {

    @Inject
    lateinit var duckChat: DuckChat

    @Inject
    lateinit var newTabPagePlugins: ActivePluginPoint<NewTabPagePlugin>

    private val binding: FragmentSearchInterstitialBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().window.sharedElementEnterTransition?.addListener(
            object : Transition.TransitionListener {
                override fun onTransitionEnd(transition: Transition) {
                    setupNewTabPage()
                    transition.removeListener(this)
                }
                override fun onTransitionStart(transition: Transition) {}
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            },
        )

        val params = requireActivity().intent.getActivityParams(SearchInterstitialActivityParams::class.java)
        params?.query?.let { query ->
            binding.duckChatOmnibar.duckChatInput.setText(query)
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val query = binding.duckChatOmnibar.duckChatInput.text.toString()
                    val data = Intent().putExtra(SearchInterstitialActivity.QUERY, query)
                    requireActivity().setResult(Activity.RESULT_CANCELED, data)
                    exitInterstitial()
                }
            },
        )

        setupOmnibarCallbacks()

        binding.duckChatOmnibar.duckChatInput.post {
            showKeyboard(binding.duckChatOmnibar.duckChatInput)
        }
    }

    private fun setupOmnibarCallbacks() {
        binding.duckChatOmnibar.apply {
            selectTab(0)
            enableFireButton = false
            enableNewChatButton = false
            onSearchSent = { query ->
                val data = Intent().putExtra(SearchInterstitialActivity.QUERY, query)
                requireActivity().setResult(Activity.RESULT_OK, data)
                exitInterstitial()
            }
            onDuckChatSent = { query ->
                val data = Intent().putExtra(SearchInterstitialActivity.QUERY, query)
                requireActivity().setResult(Activity.RESULT_CANCELED, data)
                requireActivity().finish()
                duckChat.openDuckChatWithAutoPrompt(query)
            }
            onBack = {
                requireActivity().onBackPressed()
            }
            onSearchSelected = {
                binding.contentContainer.isVisible = true
                binding.ddgLogo.isVisible = false
            }
            onDuckChatSelected = {
                binding.contentContainer.isVisible = false
                binding.ddgLogo.isVisible = true
            }
        }
    }

    private fun setupNewTabPage() {
        lifecycleScope.launch {
            newTabPagePlugins.getPlugins().firstOrNull()?.let { plugin ->
                val newTabView = plugin.getView(requireContext())
                newTabView.alpha = 0f

                val displayMetrics = requireContext().resources.displayMetrics
                val slideDistance = displayMetrics.heightPixels * CONTENT_SLIDE_DISTANCE
                newTabView.translationY = -slideDistance

                binding.contentContainer.addView(
                    newTabView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )

                newTabView.animate()
                    .alpha(1f)
                    .setDuration(CONTENT_ANIMATION_DURATION)
                    .start()

                newTabView.animate()
                    .translationY(0f)
                    .setInterpolator(OvershootInterpolator(CONTENT_INTERPOLATOR_TENSION))
                    .setDuration(CONTENT_ANIMATION_DURATION)
                    .start()
            }
        }
    }

    private fun exitInterstitial() {
        binding.duckChatOmnibar.animateOmnibarFocusedState(false)
        hideKeyboard(binding.duckChatOmnibar.duckChatInput)
        requireActivity().supportFinishAfterTransition()
    }

    companion object {
        const val QUERY = "query"
        const val CONTENT_ANIMATION_DURATION = 500L
        const val CONTENT_INTERPOLATOR_TENSION = 1F
        const val CONTENT_SLIDE_DISTANCE = 0.05F
    }
}
