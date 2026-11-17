"""Duck.ai opens in the browser from the New Tab Page and the tab switcher, answers a prompt, lists the chat in
Chat History, and offers model and reasoning pickers.

Recipe: features/duck-ai.md. Resets the app, so run it on an owned emulator.
"""
import time

import steps
from prove import Error


def window_rows(android):
    """Popup rows from `verify.sh windows` as (x, text)."""
    rows = []
    for line in android.windows().splitlines():
        point, _, text = line.partition("\t")
        if text:
            rows.append((point.split(" ")[0], text))
    return rows


def model_rows(android):
    """The model picker's rows: they share the x of the selected model's row (the chip's model plus a subtitle)."""
    rows, chip = window_rows(android), android.text("modelPickerChip") or ""
    x = next((x for x, text in rows if chip and chip in text and text != chip), None)
    seen = []
    for rx, text in rows:
        if rx == x and text not in seen:
            seen.append(text)
    return seen


def history_titles(android):
    return [line[1] for line in android.lines() if line[0] == "chatHistoryTitle"]


def run(android):
    android.reset()
    time.sleep(4)
    if android.visible("text:Not Now"):
        android.tap("text:Not Now")
    steps.web_debugging_on(android)

    # duckai-open
    steps.new_tab(android)
    android.tap("id:aiChatIconMenu")
    android.wait("id:contextualChatMenuNewChat", 5)
    items = {i: android.visible(f"id:{i}") for i in ("contextualChatMenuNewChat", "contextualChatMenuAllChats")}
    android.check("Start chat menu: New Chat and Chats", all(items.values()), items)
    android.mark()
    android.tap("id:contextualChatMenuNewChat")
    header = android.wait("id:duckAIHeader", 25)
    where = android.where()
    android.check("duckAIHeader in the browser", header and where == "BrowserActivity", where)
    android.check("duck.ai page visible attached", steps.duckai_attached(android))
    plan = android.text("duckAIFreePillTitle")
    android.check("header reads Free Plan", plan == "Free Plan", plan)
    # DuckChatPixels.kt:1683 and :1489; read from logcat, independent of the UI.
    android.expect_pixel("aichat_contextual_address_bar_menu_new_chat_selected_count")
    android.expect_pixel("m_aichat_entry_point_count")
    android.shot("duckai-open")

    # duckai-ask
    android.type("id:inputField", "Reply with only the word pineapple")
    button = steps.send_button(android, 5)
    android.check("Ask button appears", button, "neither send button showed")
    if not button:
        raise Error("no send button to tap")
    android.mark()
    android.tap(f"id:{button}")
    android.check("prompt and reply on the page", steps.wait_reply(android, "pineapple"))
    search = android.web_eval("location.search")
    android.check("URL carries chatID", "chatID=" in (search or ""), search)
    # DuckChatPixels.kt:1766.
    android.expect_pixel("m_aichat_unified_input_prompt_submitted_count")
    time.sleep(2)
    android.shot("duckai-answered")

    # duckai-history
    android.tap("id:duckAiSidebar")
    time.sleep(2)
    where = android.where()
    android.check("Chat History opens", where == "ChatHistoryActivity", where)
    titles = history_titles(android)
    android.check("the chat is listed", any("pineapple" in t.lower() for t in titles), titles)
    android.key("back")
    time.sleep(2)
    android.check("back returns to the chat", android.visible("id:duckAIHeader"), android.where())

    # open from the tab switcher
    android.tap("id:tabsMenu")
    time.sleep(2)
    android.tap("id:duckAIToolbarButton")
    popup = android.wait("id:contextualChatsPopupNewChat", 5) and android.visible("id:contextualChatsPopupViewAll")
    android.check("switcher Duck.ai popup: New Chat and View All Chats", popup, android.where())
    android.shot("duckai-switcher-popup")
    android.tap("id:contextualChatsPopupNewChat")
    opened = android.wait("id:duckAIHeader", 25) and steps.duckai_attached(android)
    android.check("switcher Duck.ai button opens a chat", opened, android.where())

    # duckai-pickers
    steps.new_chat(android)
    android.tap("id:inputField")
    android.check("model chip shown", android.wait("id:modelPickerChip", 5))
    android.tap("id:modelPickerChip")
    time.sleep(2)
    models = model_rows(android)
    android.check("model picker lists models", len(models) >= 3, models)
    android.shot("duckai-model-picker")
    steps.outside(android)
    time.sleep(1)
    closed = "Best for everyday use" not in android.windows() and android.visible("id:duckAIHeader")
    android.check("tap outside closes it, chat stays", closed)
    android.tap("id:inputField")
    android.wait("id:reasoningModePickerButton", 5)
    android.tap("id:reasoningModePickerButton")
    time.sleep(2)
    modes = sorted({t for _, t in window_rows(android) if t in ("Fast", "Reasoning", "Extended Reasoning")})
    android.check("reasoning picker offers Fast and Reasoning", {"Fast", "Reasoning"} <= set(modes), modes)
    android.shot("duckai-reasoning-picker")
    steps.outside(android)
    time.sleep(1)
    android.check("reasoning popup closed", "Fast" not in {t for _, t in window_rows(android)})
