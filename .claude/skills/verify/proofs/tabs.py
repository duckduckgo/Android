"""The tab switcher lists one card per tab titled with its page, opens a new tab, switches to a tab by its card,
closes a tab from its card, and offers layout/select/close-all in its menu.

Recipe: features/tabs.md. Resets the app, so run it on an owned emulator.
"""
import time

import steps
from prove import Error

PTP = "text:Privacy Test Pages - Home"

def close_buttons(android):
    return sum(1 for line in android.lines() if line[0] == "close")

def run(android):
    android.reset()
    time.sleep(4)
    if android.visible("text:Not Now"):
        android.tap("text:Not Now")
    steps.web_debugging_on(android)
    android.open("https://privacy-test-pages.site/")
    if steps.page_host(android, "privacy-test-pages.site") != "privacy-test-pages.site":
        raise Error("privacy-test-pages.site didn't load")

    # tabs-open-switcher / tabs-card
    android.tap("id:tabsMenu")
    time.sleep(2)
    where = android.where()
    android.check("at the switcher", where == "TabSwitcherActivity", where)
    android.check("card titled with the page", android.visible(PTP))
    cards = close_buttons(android)
    android.check("one card, one close button", cards == 1, cards)
    count = android.text("regularTabCount")
    android.check("regular tab count 1", count == "1", count)
    android.shot("tabs-switcher")

    # tabs-new
    android.mark()
    android.tap("id:newTabToolbarButton")
    opened = android.wait("id:inputField", 5)
    where = android.where()
    android.check("New Tab Page opens", opened and where == "BrowserActivity", where)
    count = android.text("tabCount")
    android.check("tab count 2", count == "2", count)
    # AppPixelName.kt:386-388 (new, switch, close); read from logcat, independent of the UI.
    android.expect_pixel("m_tab_manager_new_tab_click")
    android.shot("tabs-new")

    # tabs-switch
    android.mark()
    android.tap("id:inputModeWidgetNavTabs")
    time.sleep(2)
    cards = close_buttons(android)
    android.check("two cards", cards == 2, cards)
    android.check("New Tab card listed", android.visible("text:New Tab"))
    android.shot("tabs-switcher-two")
    android.tap(PTP)
    time.sleep(2)
    where = android.where()
    android.check("back in the browser", where == "BrowserActivity", where)
    host = steps.page_host(android, "privacy-test-pages.site", timeout=5)
    address = android.text("omnibarTextInput")
    android.check("on the page tab", host == "privacy-test-pages.site" and "privacy-test-pages.site" in (address or ""),
                  f"{host}, {address}")
    android.expect_pixel("m_tab_manager_switch_tabs")

    # tabs-close
    android.mark()
    android.tap("id:tabsMenu")
    time.sleep(2)
    android.tap("id:close", index=1)
    time.sleep(2)
    android.check("page card gone", not android.visible(PTP))
    cards = close_buttons(android)
    android.check("one card left", cards == 1, cards)
    android.expect_pixel("m_tab_manager_close_tab_click")
    android.shot("tabs-closed")
    android.key("back")
    time.sleep(2)
    count = android.text("tabCount")
    android.check("second view: toolbar count 1", count == "1", count)

    # tabs-menu
    android.tap("id:inputModeWidgetNavTabs")
    time.sleep(2)
    android.tap("id:popupMenuToolbarButton")
    time.sleep(1)
    items = {i: android.visible(f"id:{i}") for i in ("listLayoutMenuItem", "selectTabsMenuItem", "closeAllTabsMenuItem")}
    android.check("overflow lists layout, select, close all", all(items.values()), items)
    android.shot("tabs-menu")
    android.key("back")
    time.sleep(1)
