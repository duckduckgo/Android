"""Common paths from features/README.md, shared by the proofs. Each raises Error when it can't get there."""
import time

from prove import Error


def to_browser(android):
    for _ in range(6):
        where = android.where()
        if where == "BrowserActivity":
            return
        if where == "-":
            android.launch()
            continue
        android.key("back")
        time.sleep(1)
    raise Error(f"couldn't get back to the browser, still on {android.where()}")


def menu(android):
    """Opens the browser menu from the New Tab Page (focused or not) or a loaded page."""
    if android.visible("id:inputModeUnifiedBack") and not android.visible("id:inputModeWidgetNavMenu"):
        android.tap("id:inputModeUnifiedBack")
        time.sleep(1)
    for _ in range(2):
        if android.visible("id:inputModeWidgetNavMenu"):
            android.tap("id:inputModeWidgetNavMenu")
        else:
            if not android.visible("id:browserMenu"):
                android.scroll("down")
            android.tap("id:browserMenu")
        # Right after a restart the first tap can land while the keyboard is still settling.
        if android.wait("id:settingsMenuItem", 5):
            return
    raise Error("the browser menu didn't open")


def new_tab(android):
    to_browser(android)
    menu(android)
    android.tap("id:newTabMenuItem")
    if not android.wait("id:inputField", 10):
        raise Error("New Tab didn't open the New Tab Page input")
    time.sleep(1)


def page_host(android, host, timeout=20):
    """Polls the attached page's host until it is `host`; returns the last host read."""
    return android.poll(lambda: android.web_eval("location.host"), lambda h: h == host, timeout)


def settings(android):
    to_browser(android)
    menu(android)
    android.tap("id:settingsMenuItem")
    if not android.wait("text:Settings", 10):
        raise Error("Settings didn't open")
    time.sleep(1)


def web_debugging_on(android):
    """Turns on Developer Settings > Web Content Debugging, which every web_eval needs."""
    settings(android)
    android.scroll("up", 3)
    android.tap("text:Developer Settings")
    if not android.wait("text:Web Content Debugging", 5):
        raise Error("Developer Settings didn't show Web Content Debugging")
    if not android.checked("trailingSwitch", 3):
        android.tap("id:trailingSwitch", index=3)
    time.sleep(1)
    to_browser(android)


def outside(android):
    """Taps above a bottom sheet to dismiss it."""
    android._adb("shell", "input", "tap", "150", "1500")


def page(android, url="https://privacy-test-pages.site/", host="privacy-test-pages.site"):
    """On the page's tab, opening it if the current tab is something else."""
    to_browser(android)
    if android.web_eval("location.host") == host and android.visible("id:browserMenu"):
        return
    android.open(url)
    if page_host(android, host) != host:
        raise Error(f"{url} didn't load")
    time.sleep(1)


def entry(android, entry_id):
    """Opens the browser menu and taps an entry, scrolling the sheet when it is further down."""
    menu(android)
    for _ in range(3):
        if android.visible(f"id:{entry_id}"):
            break
        android.scroll("up")
    android.tap(f"id:{entry_id}")


def duckai_attached(android, timeout=30):
    return android.poll(lambda: "visible  attached  https://duck.ai" in android.web_targets(), bool, timeout)


def new_chat(android):
    """A new Duck.ai chat in the current tab (a Duck.ai tab's menu has only New Chat, not the Duck.ai shortcut)."""
    to_browser(android)
    menu(android)
    android.tap("id:newDuckChatMenuItem" if android.visible("id:newDuckChatMenuItem") else "id:duckAiNewChatMenuItem")
    if not android.wait("id:duckAIHeader", 25) or not duckai_attached(android):
        raise Error("Duck.ai didn't open and attach")


def wait_reply(android, word, timeout=30):
    """Polls the Duck.ai page until `word` shows twice (the prompt and the reply); returns whether it did."""
    count = f'document.body.innerText.toLowerCase().split("{word}").length - 1'
    return (android.poll(lambda: android.web_eval(count) or 0, lambda n: n >= 2, timeout) or 0) >= 2


def seed_chats(android, *args):
    """Chat seeds refuse until Duck.ai storage has migrated, so retry for a while."""
    last = None
    for _ in range(10):
        try:
            return android.seed("duckai-chats", *args)
        except Error as e:
            last = e
            time.sleep(3)
    raise Error(f"seed duckai-chats kept refusing: {last}")
