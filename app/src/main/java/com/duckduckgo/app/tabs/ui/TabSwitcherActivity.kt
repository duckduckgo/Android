/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.tabs.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils.clamp
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.ClearPersonalDataAction
import com.duckduckgo.app.global.view.FireDialog
import com.duckduckgo.app.global.view.toPx
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.Close
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command.DisplayMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.anko.longToast
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs


class TabSwitcherActivity : DuckDuckGoActivity(), TabSwitcherListener, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob() + Dispatchers.Main

    @Inject
    lateinit var clearPersonalDataAction: ClearPersonalDataAction

    @Inject
    lateinit var variantManager: VariantManager

    @Inject
    lateinit var gridViewColumnCalculator: GridViewColumnCalculator

    @Inject
    lateinit var webViewPreviewPersister: WebViewPreviewPersister

    private val viewModel: TabSwitcherViewModel by bindViewModel()

    private val tabsAdapter: TabSwitcherAdapter by lazy { TabSwitcherAdapter(this, webViewPreviewPersister) }

    private var loadingTabs = true

    private lateinit var tabsRecycler: RecyclerView
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        postponeEnterTransition()
        setContentView(R.layout.activity_tab_switcher)
        configureViewReferences()
        configureToolbar()
        configureRecycler()
        configureObservers()
    }

    private fun configureViewReferences() {
        tabsRecycler = findViewById(R.id.tabsRecycler)
        toolbar = findViewById(R.id.toolbar)
    }

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun configureRecycler() {
        val numberColumns = gridViewColumnCalculator.calculateNumberOfColumns(TAB_GRID_COLUMN_WIDTH_DP, TAB_GRID_MAX_COLUMN_COUNT)
        val layoutManager = GridLayoutManager(this, numberColumns)
        tabsRecycler.layoutManager = layoutManager
        tabsRecycler.adapter = tabsAdapter

        // wait until recycler view is ready before allow Activity transition animation to run
        tabsRecycler.doOnPreDraw {
            Timber.i("onPreDraw")
            startPostponedEnterTransition()
        }

        val swipeListener = ItemTouchHelper(object : SwipeToDeleteCallback() {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val tab = tabsAdapter.getTab(viewHolder.adapterPosition)
                onTabDeleted(tab)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val alpha = 1 - (abs(dX) / (recyclerView.width / numberColumns))
                    viewHolder.itemView.alpha = clamp(alpha, 0f, 1f)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        swipeListener.attachToRecyclerView(tabsRecycler)


        val borderDecorator = object : RecyclerView.ItemDecoration() {

            private val radius = 8.toPx().toFloat()
            private val borderWidth = 2.toPx().toFloat()
            private val borderGap = 4.toPx().toFloat()

            val borderStroke: Paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = borderWidth

                val typedValue = TypedValue()
                themedContext.theme.resolveAttribute(R.attr.normalTextColor, typedValue, true)
                color = ContextCompat.getColor(applicationContext, typedValue.resourceId)
            }

            override fun onDrawOver(c: Canvas, recyclerView: RecyclerView, state: RecyclerView.State) {
                val adapter = recyclerView.adapter as TabSwitcherAdapter? ?: return

                for (i in 0 until adapter.itemCount) {
                    val tab = adapter.getTab(i)

                    if (tab.tabId == intent.getStringExtra(EXTRA_KEY_SELECTED_TAB)) {
                        val child = recyclerView.getChildAt(i)
                        val rect = child.getBounds()

                        borderStroke.alpha = (child.alpha * 255).toInt()
                        c.drawRoundRect(rect, radius, radius, borderStroke)
                    }
                }

                super.onDrawOver(c, recyclerView, state)
            }

            private fun View.getBounds(): RectF {
                val leftPosition = left + translationX - paddingLeft - borderGap
                val rightPosition = right + translationX + paddingRight + borderGap

                val topPosition = top - paddingTop - borderGap
                val bottomPosition = bottom + paddingBottom + borderGap

                return RectF(leftPosition, topPosition, rightPosition, bottomPosition)
            }
        }
        tabsRecycler.addItemDecoration(borderDecorator)
    }


    private fun configureObservers() {
        viewModel.tabs.observe(this, Observer<List<TabEntity>> {
            render()
        })
        viewModel.selectedTab.observe(this, Observer<TabEntity> {
            render()
        })
        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
    }

    private fun render() {
        tabsAdapter.updateData(viewModel.tabs.value, viewModel.selectedTab.value)

        if (loadingTabs) {
            loadingTabs = false

            // ensure we show the currently selected tab on screen
            scrollToShowCurrentTab()
        }
    }

    private fun scrollToShowCurrentTab() {
        val index = tabsAdapter.adapterPositionForTab(intent.getStringExtra(EXTRA_KEY_SELECTED_TAB))
        tabsRecycler.scrollToPosition(index)
    }

    private fun processCommand(command: Command?) {
        when (command) {
            is DisplayMessage -> applicationContext?.longToast(command.messageId)
            is Close -> finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_tab_switcher_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.fire -> onFire()
            R.id.newTab, R.id.newTabOverflow -> onNewTabRequested()
            R.id.closeAllTabs -> closeAllTabs()
            R.id.settings -> showSettings()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onFire() {
        val dialog = FireDialog(context = this, clearPersonalDataAction = clearPersonalDataAction)
        dialog.clearComplete = { viewModel.onClearComplete() }
        dialog.show()
    }

    override fun onNewTabRequested() {
        launch { viewModel.onNewTabRequested() }
    }

    override fun onTabSelected(tab: TabEntity) {
        launch { viewModel.onTabSelected(tab) }
    }

    override fun onTabDeleted(tab: TabEntity) {
        launch { viewModel.onTabDeleted(tab) }
    }

    private fun closeAllTabs() {
        launch {
            viewModel.tabs.value?.forEach {
                viewModel.onTabDeleted(it)
            }
        }
    }

    private fun showSettings() {
        startActivity(SettingsActivity.intent(this))
    }

    override fun finish() {
        clearObserversEarlyToStopViewUpdates()
        super.finish()
        overridePendingTransition(R.anim.slide_from_bottom, android.R.anim.fade_out)
    }

    private fun clearObserversEarlyToStopViewUpdates() {
        viewModel.tabs.removeObservers(this)
    }

    companion object {
        fun intent(context: Context, selectedTabId: String? = null): Intent {
            val intent = Intent(context, TabSwitcherActivity::class.java)
            intent.putExtra(EXTRA_KEY_SELECTED_TAB, selectedTabId)
            return intent
        }

        const val EXTRA_KEY_SELECTED_TAB = "selected"

        private const val TAB_GRID_COLUMN_WIDTH_DP = 180
        private const val TAB_GRID_MAX_COLUMN_COUNT = 4

    }

    abstract class SwipeToDeleteCallback : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return false
        }
    }
}
