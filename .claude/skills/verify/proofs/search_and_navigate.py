"""Typing in the native input navigates the current tab: a domain loads the site (by enter or by its suggestion),
a query loads the DuckDuckGo SERP with that query, and the input opened over a loaded page navigates the same tab.

Recipe: features/search-and-navigate.md. Needs Web Content Debugging on.
"""
import time

import steps

SITE = "privacy-test-pages.site"
OTHER = "www.search-company.site"
QUERY = "what is a car"


def suggestion_rows(android):
    """Suggestion rows as (id, text) pairs from the layout, e.g. ("phrase", "privacy-test-pages.site")."""
    return [tuple(line.split("\t")[:2]) for line in (android.text() or "").splitlines()]


def run(android):
    # nav-url
    steps.new_tab(android)
    android.type("id:inputField", SITE)
    rows = android.poll(lambda: suggestion_rows(android), lambda r: ("message", "Ask Duck.ai") in r, timeout=5)
    android.check("suggestion row reads the domain", ("phrase", SITE) in rows or ("url", SITE) in rows,
                  [r for r in rows if r[0] in ("phrase", "url", "message")])
    android.check("Ask Duck.ai row offered", ("message", "Ask Duck.ai") in rows)
    android.shot("snav-typed")
    android.mark()
    android.key("enter")
    host = steps.page_host(android, SITE)
    android.check(f"page host is {SITE}", host == SITE, host)
    address = android.text("omnibarTextInput")
    android.check("address bar shows the URL", SITE in (address or ""), address)
    android.check("still in BrowserActivity", android.where() == "BrowserActivity", android.where())
    # DuckChatPixels.kt:1543 and AppPixelName.kt:464; read from logcat, independent of the UI.
    android.expect_pixel("m_aichat_experimental_omnibar_query_submitted_count")
    android.expect_pixel("m_uri_loaded")
    android.shot("snav-loaded")

    # nav-autocomplete
    steps.new_tab(android)
    android.type("id:inputField", SITE)
    android.poll(lambda: android.visible("id:url") or android.visible("id:phrase"), bool, timeout=5)
    android.shot("snav-autocomplete")
    android.tap("id:url" if android.visible("id:url") else "id:phrase")
    host = steps.page_host(android, SITE)
    android.check("tapped suggestion loads the page", host == SITE, host)

    # nav-query
    steps.new_tab(android)
    android.type("id:inputField", QUERY)
    android.mark()
    android.key("enter")
    host = steps.page_host(android, "duckduckgo.com", timeout=25)
    android.check("SERP host", host == "duckduckgo.com", host)
    query = android.web_eval('new URLSearchParams(location.search).get("q")')
    android.check("SERP query", query == QUERY, query)
    android.expect_pixel("m_aichat_experimental_omnibar_query_submitted_count")
    time.sleep(2)
    android.shot("snav-serp")

    # nav-from-page
    tabs_before = android.text("tabCount")
    if not android.visible("id:omnibarTextInputClickCatcher"):
        android.scroll("down")
    android.tap("id:omnibarTextInputClickCatcher")
    opened = android.wait("id:inputField", 5)
    field = android.text("inputField")
    android.check("input opens with the current query", opened and QUERY in (field or ""), field)
    android.type("id:inputField", OTHER)
    android.key("enter")
    host = steps.page_host(android, OTHER)
    android.check("same tab navigates", host == OTHER, host)
    tabs_after = android.text("tabCount")
    android.check("tab count unchanged", tabs_after == tabs_before, f"{tabs_before} -> {tabs_after}")
    time.sleep(2)
    android.shot("snav-from-page")
