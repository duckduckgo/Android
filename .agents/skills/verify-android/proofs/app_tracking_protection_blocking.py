"""App Tracking Protection blocks another app's connections to tracker domains by the server name (SNI) in its
TLS ClientHello, wherever the SNI sits in the first segment, and lets the rest through; the tracker activity
screen then lists that app with the companies it blocked.

Recipe: features/app-tracking-protection.md (apptp-blocking-*). The probe app (verify.sh probe) is the other app.
The hosts are live. Doesn't clear app data; leaves App Tracking Protection off and rmem_max as it found it.
"""
import re
import time

import steps
from prove import Error

TRACKERS = ["app.adjust.com", "api2.branch.io"]
CONTROL = "example.com"
# AppTP routes only public addresses through its tunnel, so a fixture on the host or the LAN would bypass it.
HOSTS = TRACKERS + [CONTROL]
LIVE = HOSTS + ["App Tracking Protection blocklist download"]
PROBE = "Verify Probe"

EXPECTED = {
    "single": "blocked", "window": "blocked", "split": "blocked",
    # netguard reads only the first segment, so an SNI that arrives later is missed: a known limitation.
    "late-sni": "allowed",
}
# The moto g(60)'s rmem_max. Apps there advertise window scale 12, which netguard before 1.10.3 multiplied into a
# 128-byte receive window, so the hello arrived in segments that cut the SNI and trackers got through.
MOTO_RMEM_MAX = "134217728"
NETGUARD_TAGS = ("NetGuard.JNI", "NgVpnNetworkStack", "VerifyProbe")


def probe_mode(android, mode, label, uid):
    """Probes every host in one mode and checks each outcome; returns the report row and the tracker companies
    netguard's block decisions named for the probe (uid)."""
    android.mark()
    results = steps.probe(android, HOSTS, mode)
    log = android.logs()
    if android.evidence:
        excerpt = [line for line in log.splitlines() if any(tag in line for tag in NETGUARD_TAGS)]
        (android.evidence / f"apptp-netguard-{label.replace(' ', '-')}.txt").write_text("\n".join(excerpt) + "\n")

    android.check(f"{label}: control {CONTROL} allowed", results[CONTROL] == "ALLOWED", results[CONTROL])
    for tracker in TRACKERS:
        outcome = results[tracker]
        decided = re.search(rf"shouldAllowDomain for {re.escape(tracker)} \({uid}\) = AppTracker", log)
        if outcome == "NO_ADDRESS" and decided:
            # Blocked at the DNS lookup: the TLS server-name blocking this proof is about never ran.
            detail = "blocked by DNS, TLS blocking not reached"
        else:
            detail = f"{outcome}, block decision {'logged' if decided else 'missing'}"
            if "new session" not in log:
                # An app AppTP doesn't protect is routed around the tunnel, so netguard never sees it.
                detail += ", no tunnel session (app unprotected, or AppTP logging off)"
        if EXPECTED[mode] == "blocked":
            # netguard drops a blocked connection without a reset, so the probe's read times out.
            android.check(f"{label}: {tracker} blocked", outcome == "TIMEOUT" and decided, detail)
        else:
            android.check(f"{label}: {tracker} allowed (known limitation)", outcome == "ALLOWED", detail)

    scales = sorted(set(re.findall(r"new session mss \d+ ws (\d+)", log)))
    segments = sorted({int(n) for n in re.findall(r"/443 seq 1 ack \d+ data (\d+)", log)} - {0})
    companies = set(re.findall(r"trackerCompanyDisplayName=([^,]+), trackingAppId=dev\.verifyandroid\.probe", log))
    row = f"| {label} | {', '.join(f'{h}={o}' for h, o in results.items())} | {', '.join(scales)} | {segments} |"
    return row, companies


def protect_probe(android):
    """With AppTP on, turns the probe's protection back on in My Apps when a red run or a hand test left it off. AppTP
    routes an unprotected app around its tunnel, which says nothing about the build."""
    steps.apptp(android)
    android.scroll("up", 3)
    time.sleep(1)
    android.tap("text:View Apps")
    if not android.wait("text:My Apps", 10):
        raise Error("View Apps didn't open My Apps")
    for _ in range(6):
        if android.visible(f"text:{PROBE}"):
            break
        android.scroll("up")
    names = [line[1] for line in android.lines() if line[0] == "deviceShieldAppEntryName"]
    if PROBE not in names:
        raise Error(f"My Apps doesn't list {PROBE} among the {len(names)} apps on screen")
    index = names.index(PROBE) + 1
    if android.checked("deviceShieldAppEntryShieldEnabled", index) is False:
        android.note(f"{PROBE} had its protection off in My Apps and it was turned back on first")
        android.mark()
        android.tap("id:deviceShieldAppEntryShieldEnabled", index=index)
        time.sleep(2)
        if android.checked("deviceShieldAppEntryShieldEnabled", index) is not True:
            raise Error(f"couldn't turn {PROBE}'s protection back on")
        # My Apps applies the change when the user leaves it (ManageAppsProtectionViewModel.onPause), by restarting
        # the VPN, which cuts any connection open at the time. Leave it here, not when the probe comes to the front.
        android.key("back")
        restarted = android.poll(android.pixels, lambda fired: "m_vpn_ev_start_attempt_success_c" in fired, 20)
        if "m_vpn_ev_start_attempt_success_c" not in restarted or not android.poll(lambda: steps.vpn_up(android), bool, 10):
            raise Error("App Tracking Protection's VPN didn't come back after protecting the probe")


def like_moto(android, uid):
    """window with the moto g(60)'s rmem_max; returns its report row and companies, or None without root."""
    if not steps.root(android):
        android.unverified("window like moto", "adbd isn't running as root, so rmem_max can't be set; to verify it, "
                           "run adb root before the proof, on an emulator you may root (google_apis images allow it)")
        return None
    before = steps.rmem_max(android)
    try:
        now = steps.rmem_max(android, MOTO_RMEM_MAX)
        if now != MOTO_RMEM_MAX:
            raise Error(f"couldn't set rmem_max to {MOTO_RMEM_MAX}: it reads {now}")
        android.note(f"like-moto: rmem_max {before} -> {now} for the window run")
        return probe_mode(android, "window", "window like moto", uid)
    finally:
        restored = steps.rmem_max(android, before)
        android.check("rmem_max restored after the like-moto run", restored == before, f"{before} -> {restored}")


def run(android):
    if steps.apptp_off(android):
        android.note("App Tracking Protection was on at the start and was turned off first")
    baseline = steps.probe(android, HOSTS, "single")
    android.check("baseline, App Tracking Protection off: every host reachable",
                  all(v == "ALLOWED" for v in baseline.values()), baseline)
    uid = steps.uid(android, steps.PROBE_PACKAGE)

    android.mark()
    steps.apptp_on(android)
    android.expect_pixel("m_vpn_ev_start_attempt_success_c")
    android.shot("apptp-blocking-on")
    # After the baseline, which installs the probe, so My Apps lists it; and after onboarding, which View Apps needs.
    protect_probe(android)

    rows, companies = [], set()
    for mode in EXPECTED:
        row, named = probe_mode(android, mode, mode, uid)
        rows.append(row)
        companies |= named
    moto = like_moto(android, uid)
    if moto:
        rows.append(moto[0])
        companies |= moto[1]
    # Without the VPN, the allowed results (the control, late-sni) would pass for the wrong reason.
    android.check("App Tracking Protection still up at the end", steps.vpn_up(android))
    android.section("Modes", "\n".join(["| Mode | Results | Window scales | First segments (bytes) |",
                                        "|---|---|---|---|", *rows]))

    # apptp-blocking-feed: the second view, the blocked companies listed for the probe in the app.
    steps.apptp(android)
    if not android.wait(f"text~:in {PROBE} app", 10):
        raise Error(f"the tracker screen's feed has no row for {PROBE}")
    row = next(l[1] for l in android.lines() if l[0] == "activity_message" and f"in {PROBE} app" in l[1])
    android.note(f"feed: {row}")
    android.shot("apptp-blocking-feed")
    android.tap(f"text~:in {PROBE} app")
    where = android.poll(android.where, lambda w: w == "AppTPCompanyTrackersActivity", 10)
    android.check(f"the feed's {PROBE} row opens its trackers", where == "AppTPCompanyTrackersActivity"
                  and android.text("app_name") == PROBE, where)
    listed = {l[1] for l in android.lines() if l[0] == "tracking_company_name"}
    android.check("lists the companies netguard blocked for the probe", companies and companies <= listed,
                  f"blocked {sorted(companies)}, listed {sorted(listed)}")
    ago = android.text("app_tracked_ago")
    android.check("blocked just now", ago == "Just Now", ago)
    android.shot("apptp-blocking-companies")

    android.key("back")
    time.sleep(1)
    steps.apptp_disable_all(android)
    if android.poll(lambda: steps.vpn_up(android), lambda up: not up, 15):
        raise Error("App Tracking Protection's VPN stayed up after Disable for All Apps")
    steps.to_browser(android)
