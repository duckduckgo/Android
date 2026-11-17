"""Duck.ai opens in the browser from the New Tab Page and the tab switcher, answers a prompt, lists the chat in
Chat History, offers model and reasoning pickers, shows seeded text, voice and image chats with pinned ones first,
and its header and model picker follow the subscription tier (Free Plan, Plus).

Recipe: features/duck-ai.md. Resets the app, so run it on an owned emulator.
"""
import time

import steps
from prove import Error


def body(android):
    return android.web_eval("document.body.innerText") or ""


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


def open_model_picker(android):
    android.tap("id:inputField")
    if not android.wait("id:modelPickerChip", 5):
        raise Error("the model chip didn't show")
    android.tap("id:modelPickerChip")
    time.sleep(2)


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
    android.check("Ask button appears", android.wait("id:nativeInputAskButton", 5))
    android.mark()
    android.tap("id:nativeInputAskButton")
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

    # seeded chats (seeding is setup: a refused seed is an error, not a failed check)
    steps.seed_chats(android, "--count", "4", "--pinned", "2", "--title", "Seed")
    steps.seed_chats(android, "--kind", "voice", "--count", "1", "--title", "Voice")
    steps.seed_chats(android, "--kind", "image", "--count", "1", "--title", "Image")
    android.tap("id:duckAiSidebar")
    time.sleep(2)
    sections = [line[1] for line in android.lines() if line[0] == "sectionHeaderText"]
    android.check("Pinned and Recent sections", "Pinned" in sections and "Recent" in sections, sections)
    titles = history_titles(android)
    android.check("pinned chats first", titles[:2] == ["Seed 1", "Seed 2"], titles)
    android.shot("duckai-history-seeded")
    android.tap("text:Voice 1")
    header = android.wait("id:duckAIHeader", 25)
    time.sleep(4)
    android.check("voice chat opens, answers labelled Voice Chat", header and "Voice Chat" in body(android))
    android.shot("duckai-voice-chat")
    android.tap("id:duckAiSidebar")
    time.sleep(2)
    android.tap("text:Image 1")
    header = android.wait("id:duckAIHeader", 25)
    time.sleep(4)
    images = android.web_eval('document.querySelectorAll("img").length') or 0
    android.check("image chat opens with a seeded image", header and "Seeded image" in body(android) and images >= 1,
                  f"{images} img")
    android.shot("duckai-image-chat")
    android.seed("duckai-chats-none")
    time.sleep(1)
    android.tap("id:duckAiSidebar")
    time.sleep(2)
    titles = history_titles(android)
    seeded = [t for t in titles if t.split(" ")[0] in ("Seed", "Voice", "Image")]
    real = any("pineapple" in t.lower() for t in titles)
    android.check("clearing seeds keeps the real chat", not seeded and real, titles)
    android.key("back")

    # tier spot-check (plus)
    android.subscription("--tier", "plus")
    time.sleep(3)
    steps.new_chat(android)
    title = android.text("aiTitle")
    android.check("Plus header reads Duck.ai", title == "Duck.ai", title)
    open_model_picker(android)
    gated = [m for m in model_rows(android) if m.endswith("…")]
    android.check("gated rows end in …", len(gated) >= 1, gated)
    android.shot("duckai-plus-model-picker")
    steps.outside(android)
    time.sleep(1)
    android.subscription("none")
    time.sleep(3)
    steps.new_chat(android)
    plan = android.text("duckAIFreePillTitle")
    android.check("back to Free Plan", plan == "Free Plan", plan)
