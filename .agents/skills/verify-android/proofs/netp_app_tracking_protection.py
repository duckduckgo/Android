"""With the VPN (NetP) and App Tracking Protection both on, no ClientHello to a tracker gets out of the tunnel: not
the app's own hello, and not the one the kernel retransmits after the app gave up and closed its socket, when the
kernel no longer knows which app the connection belongs to. Each connection is judged, and counted in the tracker
screen, once: its retransmitted hellos are dropped as already blocked. The probe's traffic goes through the tunnel:
NetP judges and allows the control, and Cloudflare sees the probe at another public IP than this computer's.

Recipe: features/app-tracking-protection.md (apptp-in-netp). Needs the app signed in to a subscription with the VPN.
The probe app (verify.sh probe) is the other app; the hosts are live. Doesn't clear app data; leaves NetP and App
Tracking Protection on. The IP check assumes the device leaves through this computer's network, as an emulator does.
Neither address is saved or printed: they would identify this network and the VPN server.
"""
import re
import time
import urllib.request

import steps
from app_tracking_protection_blocking import CONTROL, TRACKERS, protect_probe
from prove import Error, verify

LIVE = TRACKERS + [CONTROL]
PROBE_PACKAGE = "dev.verifyandroid.probe"
# The probe gives up after 3 s; the kernel retransmits the unacknowledged hello after that, within a few seconds.
RETRANSMIT_WAIT = 10
# Cloudflare's own trace page answers with the client's public IP; the probe and this computer both ask it.
IP_ECHO = "one.one.one.one"
# Saved to the evidence: only these lines. Debug builds also log the WireGuard private key, never saved.
KEPT = re.compile(r"GoBackend: (shouldAllowDomain for|Owner of|\S+ \([^)]*\) already blocked)|TLS server \S+ \(\S+\) found")


def netp_on(android):
    """Turns NetP on from Settings > VPN unless it is on already, accepting Android's VPN consent if it asks; ends
    on Settings."""
    steps.settings(android)
    # A reinstall restarts a VPN that was on; give it the time before treating it as off.
    state = android.poll(lambda: steps.netp_row(android), lambda s: s == "On", 15)
    if state is None:
        raise Error("Settings has no VPN row: the app isn't signed in to a subscription with the VPN")
    if state != "On":
        android.tap("text:VPN")
        if android.poll(android.where, lambda w: w == "NetworkProtectionManagementActivity", 10) != "NetworkProtectionManagementActivity":
            raise Error(f"the VPN row opened {android.where()}")
        if android.wait("id:autoExcludePromptCancelAction", 3):
            android.tap("id:autoExcludePromptCancelAction")
        # While the VPN is on, the screen's globe animation keeps android layout empty, so it is only read while off.
        android.tap("id:trailingSwitch")
        if android.wait("text:Connection request", 5):
            android.tap("id:button1")
        android.key("back")
        state = android.poll(lambda: steps.netp_row(android), lambda s: s == "On", 30)
    android.check("NetP on: Settings' VPN row reads On and Android has a VPN network",
                  state == "On" and steps.vpn_up(android), state)


def probe_public_ip():
    out = verify("probe", IP_ECHO, "ip", 5000).stdout
    found = re.search(r"outcome=(\S+) ip=(\S*)", out)
    if not found or found.group(1) != "ALLOWED":
        raise Error(f"the probe couldn't read its public IP from {IP_ECHO}: {found.group(1) if found else out.strip()}")
    return found.group(2)


def host_public_ip():
    with urllib.request.urlopen(f"https://{IP_ECHO}/cdn-cgi/trace", timeout=10) as response:
        found = re.search(r"^ip=(\S+)$", response.read().decode("ascii"), re.M)
    if not found:
        raise Error(f"{IP_ECHO} didn't return this computer's public IP")
    return found.group(1)


def probe_uid(android):
    out = android._adb("shell", "pm", "list", "packages", "-U", PROBE_PACKAGE)
    found = re.search(r"uid:(\d+)", out)
    if not found:
        raise Error(f"{PROBE_PACKAGE} isn't installed")
    return found.group(1)


def run(android):
    # Installs the probe, so My Apps lists it.
    steps.probe(android, [CONTROL])
    netp_on(android)
    android.shot("netp-on")
    steps.apptp_on(android)
    protect_probe(android)
    uid = probe_uid(android)

    android.mark()
    results = steps.probe(android, TRACKERS + [CONTROL], "single")
    time.sleep(RETRANSMIT_WAIT)
    log = android.logs()
    kept = [line for line in log.splitlines() if KEPT.search(line)]
    if android.evidence:
        (android.evidence / f"{android.prefix}netp-apptp-decisions.txt").write_text("\n".join(kept) + "\n")

    allowed = len(re.findall(rf"GoBackend: shouldAllowDomain for {re.escape(CONTROL)} \({uid}\) = null", log))
    android.check(f"control {CONTROL} reached through the tunnel", results[CONTROL] == "ALLOWED" and allowed,
                  f"{results[CONTROL]}, {allowed} allow decision(s) for uid {uid} under GoBackend")
    rows = []
    for tracker in TRACKERS:
        host = re.escape(tracker)
        blocked = len(re.findall(rf"GoBackend: shouldAllowDomain for {host} \({uid}\) = AppTracker", log))
        # The kernel can't attribute a closed socket's retransmission: it reports uid 0 (or -1 when not found).
        unknown = len(re.findall(rf"GoBackend: shouldAllowDomain for {host} \((0|-1)\) = null", log))
        remembered = len(re.findall(rf"GoBackend: Owner of {host} .*using uid {uid}", log))
        dropped = len(re.findall(rf"GoBackend: {host} \([^)]*\) already blocked on this connection", log))
        rows.append(f"| {tracker} | {results[tracker]} | {blocked} | {dropped} | {remembered} | {unknown} |")
        android.check(f"{tracker}: the probe's hello blocked inside NetP", results[tracker] == "TIMEOUT" and blocked,
                      f"{results[tracker]}, {blocked} block decision(s) for uid {uid} under GoBackend")
        # Without a retransmission, the next checks would pass without testing anything.
        android.check(f"{tracker}: the kernel retransmitted the hello and it reached the app",
                      dropped + remembered + unknown > 0, f"{dropped} dropped as already blocked, "
                      f"{remembered + unknown} judged again")
        # The probe opens one connection per host; each block decision is a tracker the tracker screen counts.
        android.check(f"{tracker}: the connection judged and counted once", blocked == 1 and remembered == 0,
                      f"{blocked} block decision(s), {remembered} more for retransmissions")
        android.check(f"{tracker}: no hello allowed with an unknown owner", unknown == 0,
                      f"{unknown} hello(s) allowed with uid 0 or -1")
    android.section("Decisions", "\n".join([
        "| Tracker | Probe | Blocked for the probe | Dropped as already blocked | Owner remembered | Allowed, owner unknown |",
        "|---|---|---|---|---|---|", *rows]))
    differs = probe_public_ip() != host_public_ip()
    android.check("the probe leaves through the VPN: its public IP isn't this computer's", differs,
                  "different addresses" if differs else "the same address")
    android.check("the VPN still up at the end", steps.vpn_up(android))
    steps.to_browser(android)
