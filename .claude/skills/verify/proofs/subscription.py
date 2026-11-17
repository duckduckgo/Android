"""The Settings subscription section, Subscription Settings, the VPN, PIR and Duck.ai rows and the browser menu's VPN
item follow the subscription state: hidden with none or expired, one row per entitled product while active (no PIR
outside the US), "Activating" while waiting, and the tier pill and Upgrade to Pro by tier.

Port of checks/subscription.sh (features/subscription.md). Seeds fake subscription states, so run it only where no real
subscription is signed in.
"""
import time

import steps
from prove import Error


def state(android, *args):
    android.subscription(*args)
    time.sleep(3)
    steps.to_browser(android)


def hub(android):
    """Settings, scrolled until the subscription section's last row shows (it never does with no subscription)."""
    for _ in range(3):
        steps.settings(android)
        if android.where() == "SettingsActivity":
            break
    else:
        raise Error(f"Settings not reached, on {android.where()}")
    time.sleep(1)
    for _ in range(3):
        if android.visible("id:subscriptionSettingContainer"):
            return
        android.scroll("up")
        time.sleep(1)


def labels(android):
    return [line[1] for line in android.lines()]


def has_section(android):
    return "DuckDuckGo Subscription" in labels(android)


def rows(android, *ids):
    return {i: android.visible(f"id:{i}") for i in ids}


def sub_settings(android):
    android.tap("id:subscriptionSettingContainer")
    time.sleep(2)


def contains(android, text):
    return any(text.lower() in label.lower() for label in labels(android))


def run(android):
    state(android, "none")
    hub(android)
    android.check("none: section hidden", not has_section(android))
    android.shot("sub-none-hub")

    state(android, "--tier", "plus", "--region", "us", "--status", "active")
    hub(android)
    found = rows(android, "netpPSetting", "pirSettings", "duckAiSettings", "itrSettings")
    android.check("plus us: VPN, PIR, Duck.ai, ITR rows", all(found.values()), found)
    android.shot("sub-plus-us-hub")
    android.mark()
    sub_settings(android)
    where = android.where()
    android.check("plus us: Subscription Settings", where == "SubscriptionSettingsActivity", where)
    status = android.text("subscriptionActiveStatusTextView")
    android.check("plus us: Subscribed", "Subscribed" in (status or ""), status)
    tier = android.text("tierName")
    android.check("plus us: tier PLUS", tier == "PLUS", tier)
    android.check("plus us: Upgrade to Pro", android.visible("id:upgradeToProContainer"))
    android.check("plus us: renews on", contains(android, "renews on"))
    # SubscriptionPixel.kt:162 (base name, _c suffix added for the count); read from logcat, independent of the UI.
    android.expect_pixel("m_privacy-pro_settings_screen_impression_c")
    android.shot("sub-plus-us-settings")
    hub(android)
    android.tap("id:duckAiSettings")
    time.sleep(2)
    where = android.where()
    android.check("Duck.ai row: DuckAiPaidSettingsActivity",
                  where == "DuckAiPaidSettingsActivity" and contains(android, "subscriber-exclusive AI models"), where)
    hub(android)
    android.mark()
    android.tap("id:netpPSetting")
    time.sleep(3)
    where = android.where()
    android.check("VPN row: NetworkProtectionManagementActivity", where == "NetworkProtectionManagementActivity", where)
    android.check("VPN is Off", contains(android, "VPN is Off"))
    # NetworkProtectionPixelNames.kt:97 and :131.
    android.expect_pixel("m_netp_ev_setting_pressed_c")
    android.expect_pixel("m_netp_imp_vpn_screen_c")
    android.shot("sub-vpn")
    hub(android)
    android.tap("id:pirSettings")
    time.sleep(4)
    where = android.where()
    android.check("PIR row: PirDashboardWebViewActivity", where == "PirDashboardWebViewActivity", where)
    steps.to_browser(android)
    steps.menu(android)
    vpn = android.visible("id:includeVpnMenuItem") and any(
        line[0] == "label" and line[1].startswith("VPN") for line in android.lines())
    android.check("browser menu has VPN (includeVpnMenuItem)", vpn)
    android.key("back")

    state(android, "--tier", "pro", "--region", "us", "--status", "active")
    hub(android)
    sub_settings(android)
    tier = android.text("tierName")
    android.check("pro us: tier PRO", tier == "PRO", tier)
    android.check("pro us: no Upgrade to Pro", not android.visible("id:upgradeToProContainer"))
    android.shot("sub-pro-us-settings")

    state(android, "--tier", "plus", "--region", "row", "--status", "active")
    hub(android)
    found = rows(android, "pirSettings", "netpPSetting", "duckAiSettings", "itrSettings")
    android.check("plus row: VPN, Duck.ai, ITR, no PIR",
                  not found.pop("pirSettings") and all(found.values()), found)
    android.shot("sub-plus-row-hub")

    state(android, "--status", "waiting")
    hub(android)
    android.check("waiting: Activating under Subscription Settings", contains(android, "Activating"))
    android.shot("sub-waiting-hub")
    sub_settings(android)
    where, status = android.where(), android.text("subscriptionActiveStatusTextView")
    android.check("waiting: Subscribed, expires on",
                  where == "SubscriptionSettingsActivity" and "Subscribed" in (status or "") and contains(android, "expires on"),
                  f"{where}, {status}")
    android.shot("sub-waiting-settings")

    state(android, "--status", "expired")
    hub(android)
    android.check("expired: section hidden", not has_section(android))
    steps.to_browser(android)
    steps.menu(android)
    android.check("expired: no VPN in the browser menu",
                  android.visible("id:settingsMenuItem") and not android.visible("id:includeVpnMenuItem"))
    android.key("back")

    state(android, "none")
    hub(android)
    android.check("none again: section hidden", not has_section(android))
