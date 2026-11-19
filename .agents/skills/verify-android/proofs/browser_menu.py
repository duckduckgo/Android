"""The browser menu has 12 entries on the New Tab Page and 23 on a loaded page, and every loaded-page entry lands
where its label says (features/browser-menu.md has the table).

Recipe: features/browser-menu.md. Resets the app, so run it on an owned emulator.
"""
import time

import steps
from prove import Error

PTP = "privacy-test-pages.site"


def body(android):
    return android.web_eval("document.body.innerText") or ""


def ui_rows(android):
    """`verify.sh ui` rows as (id, text, description)."""
    rows = []
    for line in android.ui().splitlines():
        cols = line.split("\t")
        if len(cols) >= 4:
            rows.append((cols[1], cols[2], cols[3]))
    return rows


def all_menu_ids(android):
    steps.menu(android)
    ids = set()
    for scroll in (False, True, True):
        if scroll:
            android.scroll("up")
        ids |= {r[0] for r in ui_rows(android) if r[0].endswith("MenuItem")}
    android.key("back")
    time.sleep(1)
    return ids


def lands_on(android, what, activity, delay=2):
    time.sleep(delay)
    where = android.where()
    return android.check(what, where == activity, where)


def run(android):
    android.reset()
    time.sleep(4)
    if android.visible("text:Not Now"):
        android.tap("text:Not Now")
    steps.web_debugging_on(android)

    # menu-open
    steps.new_tab(android)
    ids = all_menu_ids(android)
    android.check("New Tab Page menu: 12 entries", len(ids) == 12, f"{len(ids)}: {sorted(ids)}")
    steps.page(android)
    steps.menu(android)
    android.shot("menu-page")
    android.key("back")
    time.sleep(1)
    ids = all_menu_ids(android)
    android.check("loaded page menu: 23 entries", len(ids) == 23, f"{len(ids)}: {sorted(ids)}")

    # shortcut row and Duck.ai entries
    android.tap("id:omnibarTextInputClickCatcher")
    if not android.wait("id:inputField", 5):
        raise Error("the address bar didn't open the input")
    android.type("id:inputField", "www.search-company.site")
    android.key("enter")
    if steps.page_host(android, "www.search-company.site") != "www.search-company.site":
        raise Error("www.search-company.site didn't load")
    steps.entry(android, "backMenuItem")
    host = steps.page_host(android, PTP, timeout=10)
    android.check("back: previous page", host == PTP, host)
    before = android.text("tabCount")
    steps.entry(android, "newTabMenuItem")
    opened = android.wait("id:inputField", 5)
    after = android.text("tabCount")
    android.check("new tab: focused input, count +1", opened and (before or "").isdigit() and after == str(int(before) + 1),
                  f"{before} -> {after}")
    steps.page(android)
    steps.entry(android, "newDuckChatMenuItem")
    android.check("Duck.ai shortcut: duckAIHeader", android.wait("id:duckAIHeader", 25), android.where())
    steps.page(android)
    steps.entry(android, "duckAiNewChatMenuItem")
    android.check("New Chat: duckAIHeader", android.wait("id:duckAIHeader", 25), android.where())
    steps.page(android)
    steps.entry(android, "duckAiNewVoiceChatMenuItem")
    header = android.wait("id:duckAIHeader", 25)
    time.sleep(4)
    text = body(android)
    sheet = "Say hello to Duck.ai" in text or "Voice chat with Duck.ai" in text
    android.check("New Voice Chat: terms sheet or voice sheet", header and sheet, text[:120])
    android.shot("menu-voice")
    android.key("back")
    steps.page(android)
    steps.entry(android, "duckAiChatsMenuItem")
    lands_on(android, "Chats: ChatHistoryActivity", "ChatHistoryActivity")
    steps.page(android)
    steps.entry(android, "duckAiChatSettingsMenuItem")
    header = android.wait("id:duckAIHeader", 25)
    time.sleep(3)
    text = body(android)
    android.check("Duck.ai Settings: web settings modal", header and "Duck.ai Settings" in text, text[:120])
    android.shot("menu-duckai-settings")
    steps.page(android)
    steps.entry(android, "settingsMenuItem")
    lands_on(android, "Settings: SettingsActivity", "SettingsActivity")

    # page entries
    steps.page(android)
    android.web_eval("window.__verifyMarker = 1")
    android.mark()
    steps.entry(android, "refreshMenuItem")
    time.sleep(3)
    host = steps.page_host(android, PTP)
    marker = android.web_eval("typeof window.__verifyMarker")
    android.check("Refresh: same page, reloaded", host == PTP and marker == "undefined", f"{host}, marker {marker}")
    # AppPixelName.kt:282, :287 and :296 (refresh, add bookmark, desktop site); read from logcat, independent of the UI.
    android.expect_pixel("m_nav_r_p")
    steps.page(android)
    android.mark()
    steps.entry(android, "addBookmarksMenuItem")
    time.sleep(1)
    android.shot("menu-bookmark-added")
    time.sleep(3)
    android.expect_pixel("m_nav_ab_p")
    steps.entry(android, "bookmarksMenuItem")
    time.sleep(2)
    where = android.where()
    android.check("Bookmarks: BookmarksActivity lists the page",
                  where == "BookmarksActivity" and android.visible("text:Privacy Test Pages - Home"), where)
    steps.page(android)
    steps.entry(android, "sharePageMenuItem")
    lands_on(android, "Share: system sheet, app not in front", "-")
    android.shot("menu-share")
    android.key("back")
    time.sleep(1)
    steps.page(android)
    steps.entry(android, "addToHomeMenuItem")
    time.sleep(2)
    where = android.where()
    android.check("Add to Home: launcher dialog", where == "-" and android.visible("text:Add to home screen"), where)
    android.tap("text:Cancel")
    time.sleep(1)
    steps.page(android)
    steps.entry(android, "findInPageMenuItem")
    bar = android.wait("id:findInPageInput", 5) and android.visible("id:closeFindInPagePanel")
    android.check("Find in Page: find bar", bar, android.where())
    android.tap("id:closeFindInPagePanel")
    time.sleep(1)
    steps.page(android)
    android.mark()
    steps.entry(android, "changeBrowsingModeMenuItem")
    time.sleep(4)
    agent = android.web_eval("navigator.userAgent") or ""
    android.check("Desktop Site: desktop user agent", agent and "Mobile" not in agent, agent)
    android.expect_pixel("m_nav_dse_p")
    steps.entry(android, "changeBrowsingModeMenuItem")
    time.sleep(4)
    agent = android.web_eval("navigator.userAgent") or ""
    android.check("Mobile Site restores the mobile user agent", "Mobile" in agent, agent)
    steps.page(android)
    steps.entry(android, "autofillMenuItem")
    lands_on(android, "Passwords: AutofillManagementActivity", "AutofillManagementActivity")
    steps.page(android)
    steps.entry(android, "downloadsMenuItem")
    lands_on(android, "Downloads: DownloadsActivity", "DownloadsActivity")

    # state and privacy entries
    steps.page(android)
    steps.entry(android, "fireproofWebsiteMenuItem")
    time.sleep(2)
    steps.settings(android)
    android.scroll("up")
    android.tap("text:Data Clearing")
    time.sleep(2)
    android.tap("text:Fireproof Sites")
    time.sleep(2)
    android.check("Fireproof This Site: listed in Fireproof Sites", android.visible(f"text:{PTP}"), android.where())
    # Undo the fireproofing so the run leaves no state behind.
    steps.page(android)
    steps.menu(android)
    android.scroll("up", 2)
    android.tap("id:fireproofWebsiteMenuItem")
    time.sleep(2)
    steps.page(android)
    steps.entry(android, "fireMenuItem")
    shown = android.wait("id:dialogTitle", 5)
    title = android.text("dialogTitle")
    android.check("Delete Tabs and Data: fire sheet", shown and title == "Delete tabs and site data?", title)
    android.tap("id:touch_outside")
    time.sleep(1)
    steps.page(android)
    steps.entry(android, "reportBrokenSiteMenuItem")
    time.sleep(3)
    where = android.where()
    android.check("Report Broken Site: dashboard breakage form",
                  where == "PrivacyDashboardHybridActivity" and "screen=breakageForm" in android.web_targets(), where)
    android.key("back")
    time.sleep(1)
    steps.page(android)
    steps.entry(android, "privacyProtectionMenuItem")
    time.sleep(3)
    where = android.where()
    text = body(android)
    android.check("Disable Privacy Protection: breakage prompt",
                  where == "PrivacyDashboardHybridActivity" and "Site not working" in text, f"{where}: {text[:80]}")
    android.shot("menu-privacy-protection")
    android.key("back")
    time.sleep(1)
    steps.page(android)
    steps.menu(android)
    android.scroll("up", 2)
    entry = next((r for r in ui_rows(android) if r[0] == "privacyProtectionMenuItem"), None)
    android.check("entry now reads Enable Privacy Protection", entry and entry[2] == "Enable Privacy Protection", entry)
    android.tap("id:privacyProtectionMenuItem")
    time.sleep(3)
    steps.page(android)
    steps.entry(android, "printPageMenuItem")
    lands_on(android, "Print: system print spooler", "-", delay=3)
    android.shot("menu-print")
    android.key("back")
    time.sleep(1)
    android.key("back")
    time.sleep(1)
    steps.page(android)
    steps.entry(android, "internalFeedbackMenuItem")
    host = steps.page_host(android, "login.microsoftonline.com", timeout=30)
    where = android.where()
    android.check("Send Internal Feedback: SSO-gated form in a tab",
                  host == "login.microsoftonline.com" and where == "BrowserActivity", f"{host}, {where}")
    android.shot("menu-internal-feedback")
