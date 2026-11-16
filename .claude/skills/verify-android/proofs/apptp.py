"""AppTP blocks other apps' connections to trackers, by the server name in the TLS ClientHello, wherever it sits in the first segment.

Params: modes=single,window,split,late-sni (default: all). See references/apptp.md for the modes and the device settings
that matter (--like-moto).
"""
import re

from verify_android import steps

TRACKERS = ["app.adjust.com", "api2.branch.io"]
CONTROL = "example.com"
# AppTP only routes public addresses through its tunnel, so these can't be a local fixture.
LIVE = TRACKERS + [CONTROL]

EXPECTED = {
    "single": "blocked", "window": "blocked", "split": "blocked",
    # netguard reads only the first segment, so an SNI that arrives later is missed: a known limitation.
    "late-sni": "allowed",
}


def run(android):
    modes = android.param("modes", ",".join(EXPECTED)).split(",")
    steps.fresh_start(android)
    baseline = android.probe(LIVE, "single")
    android.check("baseline, AppTP off: every host reachable", all(v == "ALLOWED" for v in baseline.values()), baseline)

    start = android.mark()
    steps.enable_apptp(android)
    android.expect_pixel("m_vpn_ev_start_attempt_success_c", since=start)

    rows = []
    for mode in modes:
        android.mark()
        results = android.probe(LIVE, mode)
        log = android.logs()
        android.save(f"logcat-{mode}.txt", "\n".join(
            l for l in log.splitlines() if any(t in l for t in ("NetGuard.JNI", "NgVpnNetworkStack", "VerifyProbe"))) + "\n")

        android.check(f"{mode}: control {CONTROL} allowed", results.get(CONTROL) == "ALLOWED", results.get(CONTROL))
        for tracker in TRACKERS:
            outcome = results.get(tracker)
            decided = re.search(rf"shouldAllowDomain for {re.escape(tracker)} \(\d+\) = AppTracker", log)
            if outcome == "NO_ADDRESS" and decided:
                # Blocked, but at the DNS lookup: the TLS server-name blocking this proof is about never ran.
                detail = "blocked by DNS, TLS blocking not reached"
            else:
                detail = f"{outcome}, block decision {'logged' if decided else 'missing'}"
            if EXPECTED[mode] == "blocked":
                android.check(f"{mode}: {tracker} blocked", outcome == "TIMEOUT" and decided, detail)
            else:
                android.check(f"{mode}: {tracker} allowed (known limitation)", outcome == "ALLOWED", detail)

        scales = sorted(set(re.findall(r"new session mss \d+ ws (\d+)", log)))
        segments = sorted({int(n) for n in re.findall(r"/443 seq 1 ack \d+ data (\d+)", log)} - {0})
        rows.append(f"| {mode} | {', '.join(f'{h}={o}' for h, o in results.items())} | {','.join(scales)} | {segments} |")

    # Without the VPN, the "allowed" results (control, late-sni) would pass for the wrong reason.
    android.check("AppTP still running at the end", android.tunnel_up())
    android.section("Modes", "\n".join(["| Mode | Results | Window scales | First segments (bytes) |",
                                        "|---|---|---|---|", *rows]))
