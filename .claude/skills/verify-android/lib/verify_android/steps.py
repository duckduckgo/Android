"""App steps shared by proofs: each handles an app quirk once. Add one when a second proof needs it."""
import time

from .core import APP_ID, Error


def fresh_start(android):
    """Clears the app's data: no onboarding done, AppTP off."""
    android.shell("pm", "clear", APP_ID)


def enable_apptp(android):
    """Turns AppTP on through onboarding (wipes app data), with verbose AppTP and netguard logs."""
    android.shell("pm", "clear", APP_ID)
    android.launch()
    # 16 KB page-size emulators warn that the app's native libraries aren't 16 KB aligned.
    if android.wait_for("id=skipOnboardingButton", "text=16 KB compatible", timeout=30) == "text=16 KB compatible":
        android.tap("text=/^OK$/")
    android.tap("id=skipOnboardingButton", wait=30)
    # The browser opens with the omnibar focused and the keyboard up. Depending on the omnibar variant the menu button
    # is the browser toolbar's or the native input widget's.
    android.hide_keyboard()
    android.tap(r"id=/:id\/(browserMenuImageView|inputModeWidgetNavMenu)$/", wait=30)
    android.tap("text=/^Settings$/", first=True)
    android.scroll_to("text=/^App Tracking Protection$/")
    android.tap("text=/^App Tracking Protection$/")
    # The onboarding pages share one call-to-action button: Continue, Continue, then Enable.
    for label in ("Continue", "Continue", "Enable App Tracking Protection"):
        android.tap(f"id=onboarding_next_cta,text=/^{label}$/")
        time.sleep(1)  # the pager animates: don't tap the next page's button before it's the one on screen
    # Android's VPN permission dialog, shown only the first time.
    if android.wait_for("text=/^OK$/", "text=Good news! App Tracking Protection is now enabled", timeout=30) == "text=/^OK$/":
        android.tap("text=/^OK$/")
    if not android.wait_for("text=Good news! App Tracking Protection is now enabled", timeout=30):
        raise Error("AppTP's onboarding didn't end on its enabled screen; see ui.log")
    android.tap("text=/^Got it!$/")
    android.broadcast("logging", turn="on")
    time.sleep(3)  # the broadcast restarts the VPN: let the old tunnel go down first
    deadline = time.time() + 30
    while not android.tunnel_up():
        if time.time() > deadline:
            raise Error("AppTP's tunnel didn't come up")
        time.sleep(1)
