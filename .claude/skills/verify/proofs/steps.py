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
