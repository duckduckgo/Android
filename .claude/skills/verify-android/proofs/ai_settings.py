"""AI Features settings drive the New Tab Page: Search Only (the default) shows a plain search input with a start-chat
button; Search & Duck.ai adds Search/Duck.ai tabs and a toggle-position setting that picks the tab a new tab opens
on; turning Duck.ai off or its shortcuts off removes its entry points; "Use DuckDuckGo Without AI" sets every AI
search setting off.

Recipe: features/ai-settings.md. Resets the app, so run it on an owned emulator.
"""
import time

import steps
from prove import Error


def ai_features(android):
    steps.settings(android)
    android.scroll("up")
    if not android.wait("id:includeDuckChatSetting", 5):
        raise Error("Settings has no AI Features row")
    android.tap("id:includeDuckChatSetting")
    if not android.wait("id:duckChatSettingsTitle", 10):
        raise Error("AI Features didn't open")


def cards(android):
    android.scroll("up")
    android.wait("id:duckAiInputScreenWithAiContainer", 5)


def label_after(android, title):
    """The label right below `title` in layout order (a row's value)."""
    labels = [line[1] for line in android.lines()]
    return labels[labels.index(title) + 1] if title in labels[:-1] else None


def duck_items(android):
    return [r for r in (line.split("\t")[1] for line in android.ui().splitlines() if "\t" in line)
            if "duck" in r.lower() or "chat" in r.lower()]


def switches(android, *indexes):
    return tuple(android.checked("trailingSwitch", i) for i in indexes)


def run(android):
    android.reset()
    android.launch()
    if android.visible("text:Not Now"):
        android.tap("text:Not Now")

    # Search Only (default)
    steps.new_tab(android)
    placeholder = android.text("inputField")
    android.check("placeholder is Search", placeholder == "Search", placeholder)
    tabs = [line[1] for line in android.lines() if line[0] == "tab_title"]
    android.check("no Search/Duck.ai tabs", not tabs, tabs)
    android.check("start-chat button shown", android.visible("id:aiChatIconMenu"))
    android.shot("ai-ntp-search-only")

    # Toggle between Search and Duck.ai
    ai_features(android)
    where = android.where()
    android.check("at AI Features", where == "DuckChatSettingsActivity", where)
    cards(android)
    android.mark()
    android.tap("id:duckAiInputScreenWithAiContainer")
    time.sleep(2)
    android.check("New Tab Toggle Position row appears", android.wait("id:duckAiDefaultTogglePosition", 5))
    # DuckChatPixels.kt:1499; read from logcat, independent of the UI.
    android.expect_pixel("aichat_experimental_address_bar_setting_on")
    android.shot("ai-settings-with-ai")
    steps.new_tab(android)
    placeholder = android.text("inputField")
    android.check("placeholder is Search or enter address", placeholder == "Search or enter address", placeholder)
    tabs = [line[1] for line in android.lines() if line[0] == "tab_title"]
    android.check("tabs Search and Duck.ai", tabs == ["Search", "Duck.ai"], tabs)
    android.check("nav bar back button", android.visible("id:inputModeWidgetNavBack"))
    android.check("no start-chat button", not android.visible("id:aiChatIconMenu"))
    android.shot("ai-ntp-search-and-duckai")
    steps.settings(android)
    android.tap("text:General")
    time.sleep(2)
    labels = [line[1] for line in android.lines()]
    android.check("General shows Chat Suggestions", "Chat Suggestions" in labels, labels[:12])

    # New Tab Toggle Position = Duck.ai
    ai_features(android)
    cards(android)
    android.tap("id:duckAiDefaultTogglePosition")
    if not android.wait("text:Save", 5):
        raise Error("the toggle position dialog didn't open")
    android.tap("text:Duck.ai")
    android.tap("text:Save")
    time.sleep(1)
    value = label_after(android, "New Tab Toggle Position")
    android.check("row reads Duck.ai", value == "Duck.ai", value)
    steps.to_browser(android)
    # New Tab on a blank New Tab Page reuses that tab, so open a page first.
    android.open("https://privacy-test-pages.site/")
    time.sleep(5)
    steps.new_tab(android)
    time.sleep(2)
    duck_tab = next((line for line in android.lines() if line[0] == "tab_title" and line[1] == "Duck.ai"), None)
    android.check("new tab opens on the Duck.ai tab", duck_tab and "selected" in duck_tab[2], duck_tab)
    placeholder = android.text("inputField")
    android.check("Duck.ai placeholder", placeholder == "Ask anything privately…", placeholder)

    # Back to Search Only
    ai_features(android)
    cards(android)
    android.tap("id:duckAiInputScreenWithoutAiContainer")
    time.sleep(2)
    android.check("toggle position row gone", not android.visible("id:duckAiDefaultTogglePosition"))
    steps.new_tab(android)
    placeholder = android.text("inputField")
    android.check("placeholder back to Search", placeholder == "Search", placeholder)

    # Duck.ai off
    ai_features(android)
    state = android.checked("trailingSwitch", 1)
    android.check("Duck.ai switch starts on", state is True, state)
    android.mark()
    android.tap("id:trailingSwitch", index=1)
    time.sleep(2)
    state = android.checked("trailingSwitch", 1)
    android.check("Duck.ai switch now off", state is False, state)
    # DuckChatPixels.kt:1495.
    android.expect_pixel("aichat_disabled")
    android.check("mode cards hidden", not android.visible("id:duckAiInputScreenWithAiContainer"))
    android.shot("ai-duckai-off")
    steps.new_tab(android)
    android.check("no start-chat button", not android.visible("id:aiChatIconMenu"))
    steps.menu(android)
    items = duck_items(android)
    android.check("menu has no Duck.ai items", not items, items)
    android.key("back")
    time.sleep(1)
    ai_features(android)
    android.tap("id:trailingSwitch", index=1)
    time.sleep(1)
    state = android.checked("trailingSwitch", 1)
    android.check("Duck.ai back on", state is True, state)

    # Shortcuts
    cards(android)
    android.tap("id:duckAiShortcuts")
    time.sleep(2)
    where = android.where()
    android.check("at Shortcuts", where == "DuckAiShortcutSettingsActivity", where)
    android.tap("id:trailingSwitch", index=1)
    android.tap("id:trailingSwitch", index=2)
    time.sleep(1)
    state = switches(android, 1, 2)
    android.check("Browser Menu and Address Bar off", state == (False, False), state)
    steps.new_tab(android)
    android.check("Address Bar off: no start-chat button", not android.visible("id:aiChatIconMenu"))
    steps.menu(android)
    items = duck_items(android)
    android.check("Browser Menu off: no Duck.ai items", not items, items)
    android.key("back")
    time.sleep(1)
    android.tap("id:inputModeWidgetNavTabs")
    time.sleep(2)
    android.check("tab switcher has no Duck.ai button", not android.visible("id:duckAIToolbarButton"))
    android.key("back")
    time.sleep(1)
    ai_features(android)
    cards(android)
    android.tap("id:duckAiShortcuts")
    time.sleep(2)
    android.tap("id:trailingSwitch", index=1)
    android.tap("id:trailingSwitch", index=2)
    time.sleep(1)
    state = switches(android, 1, 2)
    android.check("shortcuts restored", state == (True, True), state)

    # Use DuckDuckGo Without AI
    ai_features(android)
    cards(android)
    android.scroll("up")
    android.mark()
    android.tap("id:duckAINoAiItem")
    time.sleep(2)
    value = label_after(android, "Search Assist")
    android.check("Search Assist Never", value is not None and "Never" in value, value)
    value = label_after(android, "Hide AI-Generated Images")
    android.check("AI images hidden On", value is not None and "On" in value, value)
    row = next((line for line in android.lines() if line[1] == "Use DuckDuckGo Without AI"), None)
    android.check("Without AI row disabled", row and "enabled" not in row[2], row)
    # DuckChatPixels.kt:1509.
    android.expect_pixel("ai_features_disabled_count")
    android.shot("ai-without-ai")
