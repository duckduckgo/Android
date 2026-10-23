/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.duckduckgo.nextsteps.impl.ui.checklist

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.bumptech.glide.Glide
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.nextsteps.impl.R
import com.duckduckgo.nextsteps.impl.databinding.ViewNextStepsCheckListBinding
import com.duckduckgo.nextsteps.impl.databinding.ViewNextStepsCheckListDividerBinding
import com.duckduckgo.nextsteps.impl.databinding.ViewNextStepsCheckListItemBinding
import com.duckduckgo.nextsteps.impl.ui.checklist.NextStepsCheckListViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class NextStepsCheckListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    private val binding: ViewNextStepsCheckListBinding by viewBinding()

    private val viewModel: NextStepsCheckListViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[NextStepsCheckListViewModel::class.java]
    }

    private val conflatedStateJob = ConflatedJob()

    init {
        val horizontalPadding = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_4)
        val bottomPadding = resources.getDimensionPixelSize(R.dimen.nextStepsStackPaddingBottom)
        setPadding(horizontalPadding, horizontalPadding, horizontalPadding, bottomPadding)
        clipToPadding = false
        clipChildren = false
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        val lifecycleOwner = findViewTreeLifecycleOwner()!!
        conflatedStateJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(lifecycleOwner.lifecycleScope)
    }

    override fun onDetachedFromWindow() {
        conflatedStateJob.cancel()
        super.onDetachedFromWindow()
    }

    private fun render(viewState: ViewState) {
        if (viewState.message == null || viewState.items.isEmpty()) {
            gone()
            return
        }
        binding.sectionTitle.text = viewState.title
        bindItems(viewState.items)
        show()
    }

    private fun bindItems(items: List<ViewState.Item>) {
        val inflater = LayoutInflater.from(context)
        binding.itemsContainer.removeAllViews()
        items.forEachIndexed { index, item ->
            if (index > 0) {
                binding.itemsContainer.addView(ViewNextStepsCheckListDividerBinding.inflate(inflater, binding.itemsContainer, false).root)
            }
            val row = ViewNextStepsCheckListItemBinding.inflate(inflater, binding.itemsContainer, false)
            row.root.tag = item.item.id
            row.bind(item)
            binding.itemsContainer.addView(row.root)
        }
    }

    private fun ViewNextStepsCheckListItemBinding.bind(item: ViewState.Item) {
        itemTitle.text = item.item.titleText
        if (item.completed) {
            actionButton.gone()
            completedIcon.show()
        } else {
            completedIcon.gone()
            actionButton.show()
        }
        val imageUrl = item.item.imageUrl
        if (imageUrl.isNullOrEmpty()) {
            Glide.with(itemImage).clear(itemImage)
            itemImage.gone()
        } else {
            itemImage.show()
            Glide.with(itemImage)
                .load(imageUrl)
                .fitCenter()
                .into(itemImage)
        }
    }

    /**
     * Plays the completion feedback for the row of [itemId]: the chevron crossfades into the checkmark, then the row
     * slides below the remaining items. [onAnimationEnd] runs once the row is in its final position.
     */
    fun animateItemCompletion(
        itemId: String,
        onAnimationEnd: () -> Unit = {},
    ) {
        val row = binding.itemsContainer.children.firstOrNull { it.tag == itemId } ?: return onAnimationEnd()
        val rowBinding = ViewNextStepsCheckListItemBinding.bind(row)
        rowBinding.completedIcon.alpha = 0f
        rowBinding.completedIcon.show()
        rowBinding.completedIcon.animate().alpha(1f).setDuration(COMPLETION_CROSSFADE_MS).start()
        rowBinding.actionButton.animate()
            .alpha(0f)
            .setDuration(COMPLETION_CROSSFADE_MS)
            .withEndAction {
                rowBinding.actionButton.visibility = View.INVISIBLE
                row.postDelayed({ moveRowToBottom(row, onAnimationEnd) }, COMPLETION_HOLD_MS)
            }
            .start()
    }

    private fun moveRowToBottom(
        row: View,
        onAnimationEnd: () -> Unit,
    ) {
        val container = binding.itemsContainer
        val rowIndex = container.indexOfChild(row)
        if (rowIndex < 0 || rowIndex == container.childCount - 1) return onAnimationEnd()

        // Rows alternate with dividers, so the divider after the row travels with it to keep one between every pair.
        val divider = container.getChildAt(rowIndex + 1)
        val transition = TransitionSet()
            .addTransition(ChangeBounds())
            .addTransition(Fade())
            .setDuration(COMPLETION_MOVE_MS)
            .setInterpolator(FastOutSlowInInterpolator())
            .addListener(
                object : TransitionListenerAdapter() {
                    override fun onTransitionEnd(transition: Transition) = onAnimationEnd()
                },
            )
        TransitionManager.beginDelayedTransition(binding.checkListCard, transition)
        container.removeView(divider)
        container.removeView(row)
        container.addView(divider)
        container.addView(row)
    }

    private companion object {
        const val COMPLETION_CROSSFADE_MS = 200L
        const val COMPLETION_HOLD_MS = 350L
        const val COMPLETION_MOVE_MS = 350L
    }
}
