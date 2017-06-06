package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Created by fgei on 6/6/17.
 */
public class TabSwitcherPresenterTest {

    private TabSwitcherPresenter tabSwitcherPresenter;
    private TabSwitcherView mockTabSwitcherView;

    private List<Tab> displayTabs;

    final private List<Integer> emptyList = new ArrayList<>();

    @Before
    public void setup() {
        mockTabSwitcherView = mock(TabSwitcherView.class);
        tabSwitcherPresenter = new TabSwitcherPresenterImpl();

        displayTabs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Tab tab = Tab.createNewTab();
            tab.index = i;
            tab.currentUrl = "https://test.com/?q=" + i;
            displayTabs.add(tab);
        }
    }

    private void loadAndAttachView() {
        tabSwitcherPresenter.load(displayTabs);
        tabSwitcherPresenter.attach(mockTabSwitcherView);
    }

    @Test
    public void whenAttachThenLoadsTabs() {
        loadAndAttachView();
        verify(mockTabSwitcherView, times(1)).loadTabs(displayTabs);
    }

    @Test
    public void whenAttachAfterRestoreThenLoadsTabs() {
        tabSwitcherPresenter.restoreState(displayTabs, new ArrayList<Tab>());
        tabSwitcherPresenter.attach(mockTabSwitcherView);
        verify(mockTabSwitcherView, times(1)).loadTabs(displayTabs);
    }

    @Test
    public void whenCreateNewTabWithNoTabDeletedThenCreateNewTabWithEmptyList() {
        loadAndAttachView();
        tabSwitcherPresenter.createNewTab();
        verify(mockTabSwitcherView, times(1)).resultCreateNewTab(emptyList);
    }

    @Test
    public void whenCreateNewTabWithTabsDeletedThenCreateNewTabWithDeletedList() {
        final Integer[] deletedPosition = new Integer[]{0, 1, 5};
        final List<Integer> expectedList = Arrays.asList(deletedPosition);
        final List<Tab> deletedTabs = new ArrayList<>();
        for (int position : deletedPosition) {
            deletedTabs.add(displayTabs.get(position));
        }
        loadAndAttachView();
        for (Tab tab : deletedTabs) {
            tabSwitcherPresenter.closeTab(tab);
        }
        tabSwitcherPresenter.createNewTab();
        verify(mockTabSwitcherView, times(1)).resultCreateNewTab(expectedList);
    }

    @Test
    public void whenOpenTabWithNoTabDeletedThenOpenTabWithEmptyList() {
        final int selectedPosition = 2;
        loadAndAttachView();
        tabSwitcherPresenter.openTab(displayTabs.get(selectedPosition));
        verify(mockTabSwitcherView, times(1)).resultSelectTab(selectedPosition, emptyList);
    }

    @Test
    public void whenOpenTabWithTabsDeletedThenOpenTabWithDeletedList() {
        final int selectedPosition = 3;
        final Integer[] deletedPosition = new Integer[]{0, 8, 4, 5};
        final List<Integer> expectedList = Arrays.asList(deletedPosition);
        final List<Tab> deletedTabs = new ArrayList<>();
        for (int position : deletedPosition) {
            deletedTabs.add(displayTabs.get(position));
        }
        loadAndAttachView();
        for (Tab tab : deletedTabs) {
            tabSwitcherPresenter.closeTab(tab);
        }
        final Tab selectedTab = displayTabs.get(selectedPosition);
        tabSwitcherPresenter.openTab(selectedTab);
        verify(mockTabSwitcherView, times(1)).resultSelectTab(selectedPosition, expectedList);
    }

    @Test
    public void whenCloseTabSwitcherWithNoTabDeletedThenCloseWithEmptyList() {
        loadAndAttachView();
        final List<Integer> emptyList = new ArrayList<>();
        tabSwitcherPresenter.closeTabSwitcher();
        verify(mockTabSwitcherView, times(1)).closeTabSwitcher(emptyList);
    }

    @Test
    public void whenCloseTaSwitcherWithTabsDeletedThenCloseWithDeletedList() {
        final Integer[] deletedPosition = new Integer[]{6, 8};
        final List<Integer> expectedList = Arrays.asList(deletedPosition);
        final List<Tab> deletedTabs = new ArrayList<>();
        for (int position : deletedPosition) {
            deletedTabs.add(displayTabs.get(position));
        }
        loadAndAttachView();
        for (Tab tab : deletedTabs) {
            tabSwitcherPresenter.closeTab(tab);
        }
        tabSwitcherPresenter.closeTabSwitcher();
        verify(mockTabSwitcherView, times(1)).closeTabSwitcher(expectedList);
    }

    @Test
    public void whenCloseAllTabsThenRemoveAllTabs() {
        loadAndAttachView();
        tabSwitcherPresenter.closeAllTabs();
        verify(mockTabSwitcherView, times(1)).resultRemoveAllTabs();
    }


}