"""App Tracking Protection turns on from Settings (through its onboarding the first time, the screen's switch
after that) and Android's VPN consent dialog, which brings up a VPN network; the tracker activity screen shows
it on, Settings and the New Tab Page agree; the app list turns protection off and on for one app; the report
flows reach their form; and the switch's "Disable for All Apps" takes the VPN down again.

Recipe: features/app-tracking-protection.md. Doesn't clear app data, but changes the device (ask first on the
user's own): it leaves App Tracking Protection off and the VPN consent granted, as a user who accepted it once
would have it.
"""
import re
import time

import steps
from prove import Error

ROW = "App Tracking Protection"
SWITCH = "deviceShieldTrackerSwitch"
UNPROTECTED = re.compile(r"(\d+) of your apps (?:has|have) protection disabled")
PROTECTED = re.compile(r"\d+ of your apps (?:is|are) protected from hidden trackers")
CATEGORY = "App freezes or crashes"


def row_status(android, label):
    """The status text Settings shows under a row, such as "On" or "Off"."""
    lines = [line[1] for line in android.lines()]
    return lines[lines.index(label) + 1] if label in lines[:-1] else None


def labels(android, resource_id):
    return [line[1] for line in android.lines() if line[0] == resource_id]


def tracker_screen_bottom(android):
    android.scroll("up", 3)
    time.sleep(1)


def opening(text, words=4):
    """The first words of a text that can go on to name the user's apps, for a check's detail."""
    return " ".join((text or "").split()[:words])


def unprotected_apps(android):
    """The tracker screen's "N of your apps have protection disabled". With none, that row is gone and the protected
    row is the last of the feed (TrackerFeedAdapter), so the protected row with "About" below it reads as 0."""
    for _ in range(4):
        lines = android.lines()
        texts = [line[1] for line in lines]
        rows = [line[1] for line in lines if line[0] == "activity_apps"]
        counts = [int(m.group(1)) for m in map(UNPROTECTED.fullmatch, rows) if m]
        if counts:
            return counts[0]
        protected = next((i for i, line in enumerate(lines)
                          if line[0] == "activity_apps" and PROTECTED.match(line[1])), None)
        if protected is not None and "About" in texts[protected + 1:]:
            return 0
        android.scroll("up")
        time.sleep(1)
    raise Error("couldn't read how many apps have protection disabled on the tracker screen")


def my_apps(android):
    """From the tracker screen, View Apps."""
    tracker_screen_bottom(android)
    android.tap("text:View Apps")
    if not android.wait("text:My Apps", 10):
        raise Error("View Apps didn't open My Apps")


def protect(android, app):
    """Turns the app's protection back on in My Apps, wherever the run stopped; returns whether it is on."""
    if android.where() != "TrackingProtectionExclusionListActivity":
        steps.apptp(android)
        my_apps(android)
    for _ in range(8):
        if android.visible(f"text:{app}"):
            break
        android.scroll("up")
    names = labels(android, "deviceShieldAppEntryName")
    if app not in names:
        return False
    index = names.index(app) + 1
    if android.checked("deviceShieldAppEntryShieldEnabled", index) is False:
        android.tap("id:deviceShieldAppEntryShieldEnabled", index=index)
        time.sleep(2)
    return android.checked("deviceShieldAppEntryShieldEnabled", index) is True


def run(android):
    steps.settings(android)
    for _ in range(3):
        if android.visible(f"text:{ROW}"):
            break
        android.scroll("up")
    if row_status(android, ROW) == "On":
        # A run that stopped half-way leaves it on; the claim starts from off.
        android.note("App Tracking Protection was on at the start and was turned off first")
        steps.apptp(android)
        steps.apptp_disable_all(android)
        time.sleep(3)

    # apptp-vpn-consent: forgetting the consent makes Android ask again, the way it does on a first enable.
    android._adb("shell", "appops", "set", steps.APP, "ACTIVATE_VPN", "default")

    # apptp-settings-entry / apptp-onboarding / apptp-reenable
    android.mark()
    entry = steps.apptp(android)
    android.shot("apptp-entry")
    if entry == "VpnOnboardingActivity":
        titles = []
        for _ in range(3):
            titles.append(android.text("onboarding_page_title"))
            cta = android.text("onboarding_next_cta")
            if cta == "Enable App Tracking Protection":
                break
            android.tap("id:onboarding_next_cta")
            # The pager animates; reading during it returns the previous page.
            time.sleep(1.5)
        android.check("onboarding: three pages end on Enable",
                      len(titles) == 3 and cta == "Enable App Tracking Protection", f"{titles}, {cta}")
        android.shot("apptp-onboarding-last")
        android.tap("id:onboarding_next_cta")
    else:
        android.note("already onboarded: enabled from the tracker screen's switch")
        android.check("switch off before enabling", android.checked(SWITCH) is False, android.checked(SWITCH))
        android.tap(f"id:{SWITCH}")

    android.check("Android asks for VPN consent", android.wait("text:Connection request", 10),
                  android.where())
    android.shot("apptp-vpn-consent")
    android.tap("id:button1")

    if entry == "VpnOnboardingActivity":
        android.check("onboarding: \"now enabled\" message",
                      android.wait("text~:App Tracking Protection is now enabled", 10), android.text("dialogText"))
        android.shot("apptp-enabled-dialog")
        android.tap("id:primaryCta")
        time.sleep(2)

    # apptp-enabled
    where = android.poll(android.where, lambda w: w == "DeviceShieldTrackerActivity", 10)
    android.check("lands on the tracker activity screen", where == "DeviceShieldTrackerActivity", where)
    android.check("switch on", android.poll(lambda: android.checked(SWITCH), bool, 10), android.checked(SWITCH))
    description = android.text("deviceShieldTrackerBlockingTrackersDescription")
    android.check("says it is blocking", description == "DuckDuckGo is blocking hidden trackers across your apps.",
                  description)
    android.check("VPN network up", android.poll(lambda: steps.vpn_up(android), bool, 10))
    android.expect_pixel("m_vpn_ev_start_attempt_success_c")
    android.expect_pixel("m_atp_ev_enabled_onboarding_c" if entry == "VpnOnboardingActivity"
                         else "m_atp_ev_enabled_tracker_activity_c")
    feed = labels(android, "activity_description") + labels(android, "activity_message")
    android.check("activity feed shown (empty message or rows)", feed, f"{len(feed)} line(s)")
    android.shot("apptp-on")

    # Second views: the Settings row the user came from, then the New Tab Page card.
    android.key("back")
    time.sleep(2)
    status = row_status(android, ROW)
    android.check("Settings row reads On", status == "On", status)
    steps.to_browser(android)
    card = android.text("deviceShieldCtaHeader")
    # With blocked trackers in its history, the card counts them instead.
    android.check("New Tab Page card says enabled", (card or "").startswith(
        ("App Tracking Protection is enabled", "App Tracking Protection blocked")), opening(card))
    android.shot("apptp-ntp-on")

    # apptp-app-list
    steps.apptp(android)
    disabled_before = unprotected_apps(android)
    my_apps(android)
    names = labels(android, "deviceShieldAppEntryName")
    switches = [android.checked("deviceShieldAppEntryShieldEnabled", i) for i in range(1, len(names) + 1)]
    # The first protected app, other than the android CLI's on-device server, which drives this proof.
    index = next((i + 1 for i, (name, on) in enumerate(zip(names, switches))
                  if on and not name.startswith("Android CLI")), None)
    if not index:
        raise Error(f"no protected app on screen to toggle among {len(names)}")
    # The user's own app: its name stays out of check names and details, which go into a public PR.
    app = names[index - 1]
    android.note(f"toggled: {app}")
    restored = False
    try:
        android.tap("id:deviceShieldAppEntryShieldEnabled", index=index)
        if not android.wait("id:trackingProtectionExcludeAppDialogReport", 5):
            raise Error("turning a protected app off didn't ask to report an issue")
        label = android.text("trackingProtectionAppLabel")
        android.check("turning a protected app off asks to report, naming it", app in (label or ""))
        android.shot("apptp-app-off-dialog")

        # apptp-report-from-app: the dialog's Report Issue, up to the step before Submit.
        android.tap("id:trackingProtectionExcludeAppDialogReport")
        where = android.poll(android.where, lambda w: w == "ReportBreakageCategorySingleChoiceActivity", 10)
        android.check("Report Issue opens the report form", where == "ReportBreakageCategorySingleChoiceActivity",
                      where)
        android.tap("text:What's happening?")
        if not android.wait(f"text:{CATEGORY}", 5):
            raise Error("the category list didn't open")
        android.tap(f"text:{CATEGORY}")
        android.tap("text:OK")
        # layout.py reads every application window, so the open list would also show the category's text; the
        # form's field (the first internal_edit_text, whose hint is "What's happening?") is what changes.
        field = android.poll(lambda: android.text("internal_edit_text"), lambda t: t == CATEGORY, 5)
        dialog_open = android.visible("text:OK")
        android.check("category chosen, Submit offered",
                      field == CATEGORY and not dialog_open and android.visible("id:ctaNextFormSubmit"),
                      f"field reads {field!r}, list {'still open' if dialog_open else 'closed'}")
        android.shot("apptp-report-form")
        # Submit sends m_atp_breakage_report with the app and the user's text to DuckDuckGo; leave without it.
        android.key("back")
        if not android.wait("text:My Apps", 10):
            raise Error("back from the report form didn't return to My Apps")
        android.check("the app's protection off", android.checked("deviceShieldAppEntryShieldEnabled", index) is False)
        reasons = [line[1] for line in android.lines() if line[0] in ("deviceShieldAppEntryName",
                                                                     "deviceShieldAppExclusionReason")]
        after = reasons[reasons.index(app) + 1] if app in reasons[:-1] else None
        android.check("the app reads Manually disabled", after == "Manually disabled",
                      after if after in (None, "Manually disabled") else "another reason")
        android.key("back")
        time.sleep(2)
        disabled_now = unprotected_apps(android)
        android.check("tracker screen counts one more unprotected app", disabled_now == disabled_before + 1,
                      f"{disabled_before} -> {disabled_now}")
        my_apps(android)
        android.tap("id:deviceShieldAppEntryShieldEnabled", index=index)
        time.sleep(2)
        restored = android.checked("deviceShieldAppEntryShieldEnabled", index) is True
        android.check("the app's protection back on", restored)
    finally:
        if not restored:
            try:
                restored = protect(android, app)
            except Exception:
                # Raising here would replace the error that stopped the run.
                restored = False
            android.note(f"{app}: protection turned back on after the run stopped" if restored else
                         f"{app}: protection is still off; turn it on in My Apps")

    # apptp-report-from-list: My Apps' overflow, pick the app, Next, the same form.
    android.mark()
    android.tap("desc:More options")
    if not android.wait("text:Report an Issue With an App", 5):
        raise Error("My Apps' menu didn't offer Report an Issue With an App")
    android.tap("text:Report an Issue With an App")
    where = android.poll(android.where, lambda w: w == "ReportBreakageAppListActivity", 10)
    android.check("menu opens the app picker", where == "ReportBreakageAppListActivity", where)
    android.expect_pixel("m_atp_imp_report_breakage_c")
    picks = labels(android, "deviceShieldInstalledAppEntryName")
    if app not in picks:
        raise Error(f"the toggled app isn't among the {len(picks)} apps in the picker")
    android.tap("id:deviceShieldInstalledAppSelector", index=picks.index(app) + 1)
    android.tap("id:ctaSubmitAppBreakage")
    where = android.poll(android.where, lambda w: w == "ReportBreakageCategorySingleChoiceActivity", 10)
    android.check("Next opens the report form", where == "ReportBreakageCategorySingleChoiceActivity", where)
    android.shot("apptp-report-picker-form")
    android.key("back")
    time.sleep(1)
    android.key("back")
    time.sleep(1)
    android.key("back")
    time.sleep(2)

    # apptp-having-issues / apptp-remove-cancel
    where = android.where()
    if where != "DeviceShieldTrackerActivity":
        raise Error(f"expected the tracker screen after leaving the reports, on {where}")
    disabled_end = unprotected_apps(android)
    android.check("unprotected count restored", disabled_end == disabled_before, f"{disabled_before} -> {disabled_end}")
    tracker_screen_bottom(android)
    android.tap("text:Having Issues?")
    where = android.poll(android.where, lambda w: w == "ManageRecentAppsProtectionActivity", 10)
    android.check("Having Issues? opens its screen",
                  where == "ManageRecentAppsProtectionActivity" and android.visible("text:Recent App Activity"), where)
    android.key("back")
    time.sleep(2)
    tracker_screen_bottom(android)
    android.tap("text:Disable and Delete Data")
    android.check("Disable and Delete Data asks first", android.wait("text:Disable and delete data?", 5))
    android.tap("text:Cancel")
    time.sleep(1)
    android.check("cancel keeps it on", android.checked(SWITCH) is True and steps.vpn_up(android))

    # apptp-disable
    android.tap(f"id:{SWITCH}")
    android.wait("id:stackedAlertDialogTitle", 5)
    options = [o for o in ("Disable for One App", "Disable for All Apps", "Cancel") if android.visible(f"text:{o}")]
    android.check("disable dialog offers three options", len(options) == 3, options)
    android.shot("apptp-disable-dialog")
    android.tap("text:Cancel")
    time.sleep(1)
    android.check("Cancel keeps the switch on", android.checked(SWITCH) is True)
    android.mark()
    steps.apptp_disable_all(android)
    android.check("switch off", android.poll(lambda: android.checked(SWITCH), lambda c: c is False, 10) is False)
    android.check("VPN network down", android.poll(lambda: steps.vpn_up(android), lambda up: not up, 10) is False)
    # The screen is still scrolled down from Disable and Delete Data; with a long feed the description is off-screen.
    android.scroll("down", 3)
    description = android.poll(lambda: android.text("deviceShieldTrackerBlockingTrackersDescription"),
                               lambda d: (d or "").startswith("Re-enable"), 5)
    android.check("asks to re-enable", (description or "").startswith("Re-enable App Tracking Protection"),
                  description)
    android.expect_pixel("m_atp_ev_disabled_tracker_activity_c")
    android.shot("apptp-off")
    android.key("back")
    time.sleep(2)
    status = row_status(android, ROW)
    android.check("Settings row reads Off", status == "Off", status)
    steps.to_browser(android)
    card = android.text("deviceShieldCtaHeader")
    android.check("New Tab Page card says disabled", (card or "").startswith("App Tracking Protection disabled"),
                  opening(card))

    # apptp-dev-settings
    steps.settings(android)
    for _ in range(8):
        if android.visible("text:AppTP dev settings"):
            break
        android.scroll("up")
    android.tap("text:AppTP dev settings")
    where = android.poll(android.where, lambda w: w == "VpnInternalSettingsActivity", 10)
    android.check("AppTP dev settings open", where == "VpnInternalSettingsActivity"
                  and android.visible("text:VPN Debug Logging"), where)
    android.note(f"VPN Debug Logging switch: {android.checked('trailingSwitch')}")
    android.shot("apptp-dev-settings")
    steps.to_browser(android)
