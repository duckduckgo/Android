"""Must pass: the harness counts only pixels logged after mark(), reads every pixel log format and other log
lines, reports what it saw when a wait times out, reads the layout, and an unverified item doesn't fail the run.

A pixel here is a line written with `log`, not a real event, so this needs no particular screen.
"""
import time


def log(android, tag, text):
    android._adb("shell", "log", "-t", tag, f"'{text}'")


def run(android):
    log(android, "RxBasedPixel", "Pixel sent: m_verify_before_mark with params: {} {}")
    time.sleep(1.1)
    android.mark()
    log(android, "RxBasedPixel", "Pixel sent: m_verify_sent with params: {} {}")
    log(android, "PixelEntity", "Pixel sent: 7 m_verify_sent_queued with params: {appVersion=1} {}")
    log(android, "RxBasedPixel", "Pixel enqueued: m_verify_enqueued with params: {} {}")
    time.sleep(1)  # let logd flush before reading

    fired = android.pixels()
    android.check("a pixel logged before mark() isn't counted", "m_verify_before_mark" not in fired, fired)
    for name in ("m_verify_sent", "m_verify_sent_queued", "m_verify_enqueued"):
        android.check(f"reads the {name} log format", name in fired, fired)
    android.check("a pixel that wasn't logged isn't reported", "m_verify_never" not in fired, fired)
    android.expect_log("expect_log finds a line logged after mark()", r"Pixel enqueued: m_verify_enqueued")
    android.unverified("an unverified item", "the selftest checks it neither passes nor fails the run")

    started = time.monotonic()
    last = android.poll(lambda: "still waiting", lambda v: v == "done", timeout=2)
    android.check("poll gives up at its timeout and returns the last value",
                  last == "still waiting" and 1.5 < time.monotonic() - started < 6, last)

    android.launch()
    android.check("reads the layout", len(android.lines()) > 0, len(android.lines()))
    android.check("an element that isn't there isn't visible", not android.visible("text:verify: never on screen"))
