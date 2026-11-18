"""The Fire button asks "Delete tabs and site data?", keeps everything when dismissed, deletes only the current tab
with Delete This Tab, clears every tab with Delete All while fireproofed sites survive, lists automatic deletion in
Data Clearing, and on Duck.ai deletes only the current chat, keeping the user's other chats.

Recipe: features/fire-button.md. Resets the app, so run it on an owned emulator.
"""
import time

import steps
from prove import Error

LIVE = ["Duck.ai chat"]


def titles(android, resource_id):
    return [line[1] for line in android.lines() if line[0] == resource_id]


def run(android):
    android.reset()
    time.sleep(4)
    if android.visible("text:Not Now"):
        android.tap("text:Not Now")
    steps.web_debugging_on(android)
    for url, host in (("https://www.search-company.site/", "www.search-company.site"),
                      ("https://privacy-test-pages.site/", "privacy-test-pages.site")):
        android.open(url)
        if steps.page_host(android, host) != host:
            raise Error(f"{url} didn't load")
        if host == "www.search-company.site":
            # The site that has to survive Delete All, fireproofed the way a user does it.
            steps.entry(android, "fireproofWebsiteMenuItem")
            time.sleep(2)
    tabs0 = android.text("tabCount")

    # fire-dialog (loaded page)
    android.tap("id:fireIconMenu")
    android.wait("id:dialogTitle", 5)
    title = android.text("dialogTitle")
    android.check("title Delete tabs and site data?", title == "Delete tabs and site data?", title)
    buttons = {b: android.visible(f"id:{b}") for b in ("deleteThisTabButton", "deleteAllSecondaryButton")}
    android.check("Delete This Tab and Delete All offered", all(buttons.values()), buttons)
    android.shot("fire-dialog")

    # fire-dismiss
    android.tap("id:touch_outside")
    time.sleep(1)
    android.check("sheet closed", not android.visible("id:dialogTitle"))
    host = steps.page_host(android, "privacy-test-pages.site", timeout=3)
    android.check("page kept", host == "privacy-test-pages.site", host)
    count = android.text("tabCount")
    android.check("tab count kept", count == tabs0, f"{tabs0} -> {count}")

    # fire-single-tab
    android.tap("id:fireIconMenu")
    android.wait("id:deleteThisTabButton", 5)
    android.mark()
    android.tap("id:deleteThisTabButton")
    time.sleep(4)
    ntp = android.wait("id:inputField", 10) and android.visible("id:inputModeWidgetNavFire")
    android.check("tab replaced by a New Tab Page", ntp, android.where())
    count = android.text("tabCount")
    android.check("tab count unchanged", count == tabs0, f"{tabs0} -> {count}")
    # AppPixelName.kt:348; read from logcat, independent of the UI.
    android.expect_pixel("m_fire_dialog_single_tab_clear_pressed")
    android.tap("id:inputModeWidgetNavTabs")
    time.sleep(2)
    android.check("switcher: page card gone", not android.visible("text:Privacy Test Pages - Home"))
    cards = titles(android, "title")
    android.check("switcher: other tab kept", "Ad Click Flow" in cards, cards)
    android.shot("fire-single-tab-switcher")
    android.key("back")
    time.sleep(1)

    # fire-confirm (New Tab Page, Delete All)
    steps.to_browser(android)
    if not android.visible("id:inputModeWidgetNavFire"):
        android.tap("id:inputModeUnifiedBack")
    android.tap("id:inputModeWidgetNavFire" if android.visible("id:inputModeWidgetNavFire") else "id:fireIconMenu")
    if not android.wait("id:deleteAllSecondaryButton", 5):
        raise Error("the Fire dialog didn't offer Delete All")
    android.shot("fire-dialog-ntp")
    android.tap("id:deleteAllSecondaryButton")
    time.sleep(1)
    android.shot("fire-animation")
    # The app can put its cookie pop-up prompt over the fresh New Tab Page; a user declines it first.
    android.poll(lambda: android.visible("id:inputField") or steps.dismiss_cookie_prompt(android), bool, 15)
    android.check("fresh New Tab Page", android.wait("id:inputField", 15), android.where())
    time.sleep(2)
    count = android.text("tabCount")
    android.check("one tab left", count == "1", count)
    android.shot("fire-after")
    android.tap("id:inputModeWidgetNavTabs")
    time.sleep(2)
    closes = sum(1 for line in android.lines() if line[0] == "close")
    android.check("switcher: one New Tab card", closes == 1 and android.visible("text:New Tab"), f"{closes} cards")
    labels = [line[1] for line in android.lines()]
    left = [l for l in labels if "Ad Click Flow" in l or "Privacy Test Pages" in l]
    android.check("switcher: earlier pages gone", not left, left)
    android.key("back")
    time.sleep(1)

    # fire-fireproof / fire-automatic
    steps.settings(android)
    android.scroll("up")
    android.tap("text:Data Clearing")
    time.sleep(2)
    where = android.where()
    android.check("at Data Clearing", where == "DataClearingSettingsActivity", where)
    labels = [line[1] for line in android.lines()]
    android.check("1 site excluded", "1 site excluded from clearing" in labels, [l for l in labels if "excluded" in l])
    after = labels[labels.index("Automatically Delete Data") + 1] if "Automatically Delete Data" in labels else None
    android.check("automatic deletion Off", after is not None and "Off" in after, after)
    android.tap("text:Fireproof Sites")
    time.sleep(2)
    where = android.where()
    android.check("fireproofed site survives Delete All",
                  where == "FireproofWebsitesActivity" and android.visible("text~:search-company.site"), where)
    android.shot("fire-fireproof-after")
    android.key("back")
    time.sleep(1)
    android.tap("text:Automatically Delete Data")
    time.sleep(2)
    where = android.where()
    android.check("automatic deletion screen opens", where == "AutomaticDataClearingSettingsActivity", where)

    # fire-duckai-chat
    steps.to_browser(android)
    android.tap("id:aiChatIconMenu")
    android.wait("id:contextualChatMenuNewChat", 5)
    android.tap("id:contextualChatMenuNewChat")
    if not android.wait("id:duckAIHeader", 25) or not steps.duckai_attached(android):
        raise Error("Duck.ai didn't open and attach")
    # A chat the user keeps: it must still be listed after the Fire button deletes the other one.
    steps.ask(android, "Reply with only the word mango")
    if not steps.wait_reply(android, "mango"):
        raise Error("Duck.ai didn't answer the first chat")
    steps.new_chat(android)
    steps.ask(android, "Reply with only the word pineapple")
    android.check("reply arrived", steps.wait_reply(android, "pineapple"))
    search = android.web_eval("location.search")
    android.check("URL carries chatID", "chatID=" in (search or ""), search)
    tabsd = android.text("tabCount")
    android.tap("id:duckAiSidebar")
    time.sleep(2)
    history = titles(android, "chatHistoryTitle")
    chat = next((t for t in history if "pineapple" in t.lower()), None)
    android.check("history before: the kept chat + the new chat",
                  len(history) == 2 and chat and any("mango" in t.lower() for t in history), history)
    android.shot("fire-duckai-history-before")
    android.key("back")
    time.sleep(2)
    android.tap("id:inputFieldFireIconMenu")
    android.wait("id:dialogTitle", 5)
    title, button = android.text("dialogTitle"), android.text("deleteThisTabButton")
    android.check("dialog Delete this chat?", title == "Delete this chat?" and button == "Delete Chat", f"{title}, {button}")
    android.shot("fire-dialog-duckai")
    android.mark()
    android.tap("id:deleteThisTabButton")
    time.sleep(7)
    search = android.web_eval("location.search")
    android.check("fresh chat, no chatID", android.visible("id:duckAIHeader") and "chatID" not in (search or ""), search)
    count = android.text("tabCount")
    android.check("tab replaced, count kept", count == tabsd, f"{tabsd} -> {count}")
    # DuckChatPixels.kt:1709: the chat was deleted from native storage, not just navigated away from.
    android.expect_pixel("m_duck-ai_native-storage_deletion_native_count")
    android.tap("id:duckAiSidebar")
    time.sleep(2)
    history = titles(android, "chatHistoryTitle")
    android.check("history after: chat gone", chat not in history, history)
    android.check("history after: the other chat kept", any("mango" in t.lower() for t in history), history)
    android.shot("fire-duckai-history-after")
    android.key("back")
