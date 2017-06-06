package com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Created by fgei on 6/6/17.
 */
public class TabManagerTest {

    private TabManager.OnTabListener mockOnTabListener;
    private TabManager tabManager;
    final private List<Tab> mockTabList = new ArrayList<>();

    @Before
    public void setup() {
        tabManager = new TabManager();
        mockOnTabListener = mock(TabManager.OnTabListener.class);

        for (int i = 0; i < 10; i++) {
            Tab tab = Tab.createNewTab();
            tab.index = i;
            mockTabList.add(tab);
        }
    }

    private void createRandomTabs(TabManager tabManager, int count) {
        for (int i = 0; i < count; i++) {
            tabManager.createNewTab();
        }
    }

    @Test
    public void whenCreateNewTabThenCallOnTabCreated() {
        tabManager.setOnTabListener(mockOnTabListener);
        tabManager.createNewTab();
        verify(mockOnTabListener, times(1)).onTabCreated(any(Tab.class));
    }

    @Test
    public void whenCreateNewTabWithNoListenerThenNoCrash() {
        tabManager.createNewTab();
    }

    @Test
    public void whenSelectTabThenCallOnCurrentTabChanged() {
        final int TAB_COUNT = 4;
        final int selectTabPosition = 1;
        tabManager.setOnTabListener(mockOnTabListener);
        createRandomTabs(tabManager, TAB_COUNT);
        tabManager.selectTab(selectTabPosition);
        final int interaction = 1 + TAB_COUNT;
        verify(mockOnTabListener, times(interaction)).onCurrentTabChanged(any(Tab.class));
    }

    @Test
    public void whenSelectTabWithNoListenerThenNoCrash() {
        final int TAB_COUNT = 6;
        final int selectedPosition = 4;
        createRandomTabs(tabManager, TAB_COUNT);
        tabManager.selectTab(selectedPosition);
    }

    @Test
    public void whenRemoveTabThenCallOnRemovedTab() {
        final int TAB_COUNT = 5;
        final Integer[] removedPosition = new Integer[]{0, 2, 3};
        tabManager.setOnTabListener(mockOnTabListener);
        createRandomTabs(tabManager, TAB_COUNT);
        final int interaction = removedPosition.length;
        tabManager.removeTabs(Arrays.asList(removedPosition));
        verify(mockOnTabListener, times(interaction)).onTabRemoved(any(Tab.class));
    }

    @Test
    public void whenRemoveTabThenNoCrash() {
        final int TAB_COUNT = 5;
        final Integer[] removedPosition = new Integer[]{3, 0, 1};
        createRandomTabs(tabManager, TAB_COUNT);
        tabManager.removeTabs(Arrays.asList(removedPosition));
    }

    @Test
    public void whenRemoveAllTabsThenClearList() {
        final int TAB_COUNT = 4;
        createRandomTabs(tabManager, TAB_COUNT);
        tabManager.removeAll();
        assertTrue(tabManager.getTabs().size() == 0);
    }

    @Test
    public void whenResumeStateThenReturnTheRightTab() {
        tabManager.setTabs(mockTabList);
        tabManager.setOnTabListener(mockOnTabListener);
        final int position = 2;
        final Tab expectedTab = mockTabList.get(position);
        tabManager.selectTab(position);
        assertEquals(expectedTab, tabManager.getCurrentTab());
    }

    @Test
    public void whenResumedStateAndSelectTabThenCallOnCurrentTabChanged() {
        tabManager.setTabs(mockTabList);
        tabManager.setOnTabListener(mockOnTabListener);
        final int position = 3;
        tabManager.selectTab(position);
        verify(mockOnTabListener, times(1)).onCurrentTabChanged(any(Tab.class));
    }

    @Test
    public void whenSelectedPositionUnavailableThenNoCrash() {
        final int TAB_COUNT = 10;
        createRandomTabs(tabManager, TAB_COUNT);
        int[] positions = new int[]{-1, 20};
        for (int position : positions) {
            tabManager.selectTab(position);
        }
    }

    @Test
    public void whenRemovingPositionUnavailableThenNoCrash() {
        final int TAB_COUNT = 10;
        createRandomTabs(tabManager, TAB_COUNT);
        Integer[] positions = new Integer[]{-1, 20};
        tabManager.removeTabs(Arrays.asList(positions));
    }

}