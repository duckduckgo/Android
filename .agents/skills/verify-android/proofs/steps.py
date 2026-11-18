"""Common paths from features/README.md, shared by the proofs. Each raises Error when it can't get there."""
import re
import time

from prove import Error, verify


def dismiss_cookie_prompt(android):
    """Declines "Tired of cookie pop-ups?" when it is up; returns whether it was.

    The app shows it once onboarding is done and some days have passed since install
    (CookiePopupOptInEvaluator.kt:66-109), and clearing data keeps the install date, so a reset app gets it.
    """
    if android.where() != "CookiePopupOptInActivity":
        return False
    android.note("the cookie pop-up opt-in prompt showed and was declined")
    android.tap("id:cookiePopupOptInDeclineButton")
    time.sleep(1)
    return True


def to_browser(android):
    for _ in range(6):
        where = android.where()
        if where == "BrowserActivity":
            return
        if where == "-":
            android.launch()
            continue
        if dismiss_cookie_prompt(android):
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


def settings(android):
    to_browser(android)
    menu(android)
    android.tap("id:settingsMenuItem")
    if not android.wait("text:Settings", 10):
        raise Error("Settings didn't open")
    time.sleep(1)


def web_debugging_on(android):
    """Turns on Developer Settings > Web Content Debugging, which every web_eval needs."""
    settings(android)
    android.scroll("up", 3)
    android.tap("text:Developer Settings")
    if not android.wait("text:Web Content Debugging", 5):
        raise Error("Developer Settings didn't show Web Content Debugging")
    if not android.checked("trailingSwitch", 3):
        android.tap("id:trailingSwitch", index=3)
    time.sleep(1)
    to_browser(android)


def outside(android):
    """Taps above a bottom sheet to dismiss it."""
    android._adb("shell", "input", "tap", "150", "1500")


def page(android, url="https://privacy-test-pages.site/", host="privacy-test-pages.site"):
    """On the page's tab, opening it if the current tab is something else."""
    to_browser(android)
    if android.web_eval("location.host") == host and android.visible("id:browserMenu"):
        return
    android.open(url)
    if page_host(android, host) != host:
        raise Error(f"{url} didn't load")
    time.sleep(1)


def entry(android, entry_id):
    """Opens the browser menu and taps an entry, scrolling the sheet when it is further down."""
    menu(android)
    for _ in range(3):
        if android.visible(f"id:{entry_id}"):
            break
        android.scroll("up")
    android.tap(f"id:{entry_id}")


def duckai_attached(android, timeout=30):
    return android.poll(lambda: "visible  attached  https://duck.ai" in android.web_targets(), bool, timeout)


def new_chat(android):
    """A new Duck.ai chat in the current tab (a Duck.ai tab's menu has only New Chat, not the Duck.ai shortcut)."""
    to_browser(android)
    menu(android)
    android.tap("id:newDuckChatMenuItem" if android.visible("id:newDuckChatMenuItem") else "id:duckAiNewChatMenuItem")
    if not android.wait("id:duckAIHeader", 25) or not duckai_attached(android):
        raise Error("Duck.ai didn't open and attach")


def wait_reply(android, word, timeout=30):
    """Polls the Duck.ai page until `word` shows twice (the prompt and the reply); returns whether it did."""
    count = f'document.body.innerText.toLowerCase().split("{word}").length - 1'
    return (android.poll(lambda: android.web_eval(count) or 0, lambda n: n >= 2, timeout) or 0) >= 2



SEND_BUTTONS = ("nativeInputAskButton", "nativeInputSubmitButton")


def send_button(android, timeout=10):
    """The id of the button that sends what's typed: "Ask" (`nativeInputAskButton`) or the arrow
    (`nativeInputSubmitButton`); both were seen on Duck.ai on 2026-11-17. None when neither shows."""
    found = android.poll(lambda: next((b for b in SEND_BUTTONS if android.visible(f"id:{b}")), None), bool, timeout)
    return found or None


def ask(android, prompt):
    """Types a prompt into the Duck.ai input and sends it."""
    android.type("id:inputField", prompt)
    button = send_button(android)
    if not button:
        raise Error("no send button appeared after typing")
    android.tap(f"id:{button}")


def apptp(android):
    """Opens App Tracking Protection from Settings: its onboarding until the user has been through it,
    afterwards the tracker activity screen."""
    settings(android)
    for _ in range(3):
        if android.visible("text:App Tracking Protection"):
            break
        android.scroll("up")
    android.tap("text:App Tracking Protection")
    where = android.poll(android.where, lambda w: w in ("VpnOnboardingActivity", "DeviceShieldTrackerActivity"), 10)
    if where not in ("VpnOnboardingActivity", "DeviceShieldTrackerActivity"):
        raise Error(f"the App Tracking Protection row opened {where}")
    time.sleep(1)
    return where


APP = "com.duckduckgo.mobile.android.debug"
PROBE_PACKAGE = "dev.verifyandroid.probe"


def uid(android, package):
    """The package's Linux uid, which netguard's block decisions and ConnectivityService's VPN agent name."""
    found = re.search(rf"^package:{re.escape(package)} uid:(\d+)",
                      android._adb("shell", "pm", "list", "packages", "-U", package), re.M)
    if not found:
        raise Error(f"{package} isn't installed")
    return found.group(1)


def vpn_up(android):
    """Whether this app's VPN is up: AppTP's, or NetP's, which the app also runs (require_netp_off rules NetP's out).
    Another app's VPN doesn't count."""
    # ConnectivityService prints one network agent per line; a VPN's names the app that established it.
    owner = re.compile(rf"OwnerUid: {uid(android, APP)}\b")
    return any("ni{VPN" in line and owner.search(line)
               for line in android._adb("shell", "dumpsys", "connectivity").splitlines())


def netp_row(android):
    """On Settings: the label under its VPN row, "On" or "Off"; None when there is no VPN row (no subscription)."""
    lines = android.lines()
    for i, line in enumerate(lines):
        if line[:2] == ["primaryText", "VPN"]:
            return next((l[1] for l in lines[i + 1:i + 3] if l[0] == "label"), None)
    return None


def require_netp_off(android):
    """App Tracking Protection's proofs read it as the app's VPN network, which NetP's also is."""
    settings(android)
    if netp_row(android) == "On":
        raise Error("the VPN (NetP) is on, so the app's VPN network can't tell App Tracking Protection's state; "
                    "turn it off in Settings > VPN first")


# My Apps: each row's app name, and its protection switch.
MY_APPS_ROW = ("deviceShieldAppEntryName", "deviceShieldAppEntryShieldEnabled")


def app_switch(android, app):
    """In My Apps, (index, checked) of the app's own protection switch; raises Error when it isn't on screen."""
    index, on = android.rows(*MY_APPS_ROW).get(app, (None, None))
    if index is None:
        raise Error("the app's protection switch isn't on screen in My Apps")
    return index, on


def apptp_disable_all(android):
    """On the tracker activity screen: the switch, then "Disable for All Apps"."""
    # Tapped while off, the switch would turn AppTP on instead.
    state = android.checked("deviceShieldTrackerSwitch")
    if state is not True:
        raise Error(f"the tracker screen's switch reads {state}, not on, so there is nothing to disable")
    android.tap("id:deviceShieldTrackerSwitch")
    if not android.wait("text:Disable for All Apps", 5):
        raise Error("the switch didn't ask how to disable")
    android.tap("text:Disable for All Apps")


def apptp_on(android):
    """Turns App Tracking Protection on through the UI, as setup: onboarding the first time, the tracker screen's
    switch after, accepting Android's VPN consent if it asks. Ends on the tracker screen with the VPN up."""
    if apptp(android) == "VpnOnboardingActivity":
        for _ in range(3):
            if android.text("onboarding_next_cta") == "Enable App Tracking Protection":
                break
            android.tap("id:onboarding_next_cta")
            time.sleep(1.5)
        android.tap("id:onboarding_next_cta")
    elif not android.checked("deviceShieldTrackerSwitch"):
        android.tap("id:deviceShieldTrackerSwitch")
    if android.wait("text:Connection request", 5):
        android.tap("id:button1")
    if android.wait("id:primaryCta", 5) and android.where() == "VpnOnboardingActivity":
        android.tap("id:primaryCta")
    if not android.poll(lambda: vpn_up(android), bool, 20):
        raise Error("App Tracking Protection's VPN didn't come up")
    if android.poll(android.where, lambda w: w == "DeviceShieldTrackerActivity", 10) != "DeviceShieldTrackerActivity":
        raise Error(f"enabling App Tracking Protection ended on {android.where()}")


def apptp_off(android):
    """Turns App Tracking Protection off through the UI when its VPN is up; returns whether it was."""
    if not vpn_up(android):
        return False
    apptp(android)
    apptp_disable_all(android)
    if android.poll(lambda: vpn_up(android), lambda up: not up, 15):
        raise Error("App Tracking Protection's VPN stayed up after Disable for All Apps")
    return True


def leave_apptp_off(android):
    """For a proof's finally: a run that stopped half-way would leave App Tracking Protection's VPN up. Never raises,
    so the error that stopped the run is the one reported."""
    try:
        if apptp_off(android):
            android.note("App Tracking Protection was still on when the run stopped, and was turned off")
    except Exception as e:
        android.note(f"couldn't turn App Tracking Protection off after the run: {e}")


PROBE_OUTCOMES = {"ALLOWED", "TIMEOUT", "CLOSED", "RESET", "NO_ADDRESS"}


def probe(android, hosts, mode="single", timeout_ms=3000):
    """Connects to each host from the probe app (verify.sh probe) and returns {host: outcome}.

    ALLOWED: the server replied. TIMEOUT, CLOSED, RESET: cut after the hello was sent. NO_ADDRESS: the name didn't
    resolve, so nothing was sent. A connection that never opened says nothing about the hello, so it is an Error."""
    out = verify("probe", ",".join(hosts), mode, timeout_ms).stdout
    results = {}
    for line in out.splitlines():
        fields = dict(f.split("=", 1) for f in line.split()[1:] if "=" in f)
        if "host" in fields:
            results[fields["host"]] = fields["outcome"]
    missing = [h for h in hosts if h not in results]
    if missing:
        raise Error(f"the probe returned no result for {missing} in {mode}")
    unopened = {h: o for h, o in results.items() if o not in PROBE_OUTCOMES}
    if unopened:
        raise Error(f"connections never got as far as the hello in {mode} ({unopened}): the network, not the app; rerun")
    return results


RMEM_MAX = "/proc/sys/net/core/rmem_max"


def root(android):
    """Whether adbd already runs as root. Never restarts it as root: that drops adb mid-run, recording included, and
    leaves the device rooted until it reboots. The user opts in with adb root before the proof."""
    return android._adb("shell", "id", "-u").strip() == "0"


def rmem_max(android, value=None):
    """The kernel's largest socket receive buffer; with a value, sets it first (needs root). Returns it."""
    if value is not None:
        android._adb("shell", f"echo {value} > {RMEM_MAX}")
    return android._adb("shell", "cat", RMEM_MAX).strip()
