"""The harness only counts what happened after mark(), reads every pixel log format, refuses a probe that never connected,
reports a UI element that never shows up as a failed check,
and refuses a tap on an element that isn't there.

Run it after changing the library: verify-android prove tests/must_fail.py
"""
import time

from verify_android import Error
from verify_android.harness import matches


def log(android, tag, text):
    android.shell("log", "-t", tag, f"'{text}'")


def run(android):
    log(android, "RxBasedPixel", "Pixel sent: m_verify_before_mark with params: {} {}")
    time.sleep(0.1)
    android.mark()
    log(android, "RxBasedPixel", "Pixel sent: m_verify_sent with params: {} {}")
    log(android, "PixelEntity", "Pixel sent: 7 m_verify_sent_queued with params: {appVersion=1} {}")
    log(android, "RxBasedPixel", "Pixel enqueued: m_verify_enqueued with params: {} {}")
    time.sleep(1)  # let logd flush before reading

    fired = android.pixels()
    android.check("a pixel logged before mark() isn't counted", "m_verify_before_mark" not in fired, fired)
    for name in ("m_verify_sent", "m_verify_sent_queued", "m_verify_enqueued"):
        android.check(f"reads the {name} log format", name in fired, fired)
    android.check("a pixel that wasn't fired isn't reported", "m_verify_never" not in fired, fired)

    never = "text=verify-android: this text is never on screen"
    android.launch()
    shown = android.expect_visible("(expected to fail)", never, timeout=2)
    failed = android.checks.pop()
    android.check("an element that never shows up is reported as a failed check", not shown and not failed["ok"], failed)
    android.check("the layout at that failure is kept with the evidence", (android.evidence / "layout-at-failure.json").exists())
    try:
        android.tap(never, wait=2)
        android.check("refuses a tap on an element that isn't there", False, "the tap succeeded")
    except Error as e:
        android.check("refuses a tap on an element that isn't there", "failed" in str(e), e)
    android.check("matches the CLI's selectors", all([
        matches("id=skipOnboarding", {"resource-id": "com.duckduckgo.mobile.android.debug:id/skipOnboardingButton"}),
        matches("text=ok", {"text": "OK"}),
        not matches("text=/^OK$/", {"text": "Book"}),
        matches(r"id=/:id\/(a|b)$/,text=/^x, y$/i", {"resource-id": "p:id/b", "text": "X, Y"}),
    ]))

    try:
        android.probe(["10.255.255.1"], "single", 2000)
        android.check("refuses a probe that never connected", False, "the probe returned a result")
    except Error as e:
        android.check("refuses a probe that never connected", "never opened" in str(e), e)
