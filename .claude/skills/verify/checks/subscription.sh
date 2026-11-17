#!/usr/bin/env bash
# Drives features/subscription.md with the android-CLI commands only (trapdoor for the subscription seed).
source "$(dirname "$0")/common.sh"
# Settings, scrolled to the subscription section; prints the section's text lines
hub() { local j; for j in 1 2 3; do settings; [[ "$($V where)" == SettingsActivity ]] && break; echo "   (settings not reached, retry $j)"; done; sleep 1; local i; for i in 1 2 3; do $V assert visible id:subscriptionSettingContainer >/dev/null 2>&1 && return; $V scroll up >/dev/null; sleep 1; done; }
section() { $V text | awk -F'\t' '$2=="DuckDuckGo Subscription"{on=1} $2=="Other Settings"{on=0} on {print $1"\t"$2}'; }
sub_settings() { $V tap id:subscriptionSettingContainer >/dev/null; sleep 2; }
state() { echo "== $*"; $V subscription "$@" >/dev/null; sleep 3; to_browser; }

$V record start subscription-1-cli.mp4 >/dev/null
state none
hub
check "none: section hidden" '! $V text | grep -q "DuckDuckGo Subscription"'
$V shot sub-none-hub.png >/dev/null

state --tier plus --region us --status active
hub
check "plus us: VPN, PIR, Duck.ai, ITR rows" '$V assert visible id:netpPSetting && $V assert visible id:pirSettings && $V assert visible id:duckAiSettings && $V assert visible id:itrSettings'
$V shot sub-plus-us-hub.png >/dev/null
sub_settings
check "plus us: Subscription Settings" '$V assert where SubscriptionSettingsActivity'
check "plus us: Subscribed" '$V text subscriptionActiveStatusTextView | grep -q Subscribed'
check "plus us: tier PLUS" '[[ "$($V text tierName)" == PLUS ]]'
check "plus us: Upgrade to Pro" '$V assert visible id:upgradeToProContainer'
check "plus us: renews on" '$V text | grep -qi "renews on"'
$V shot sub-plus-us-settings.png >/dev/null
hub; $V tap id:duckAiSettings >/dev/null; sleep 2
check "Duck.ai row: DuckAiPaidSettingsActivity" '$V assert where DuckAiPaidSettingsActivity && $V text | grep -q "subscriber-exclusive AI models"'
hub; $V tap id:netpPSetting >/dev/null; sleep 3
check "VPN row: NetworkProtectionManagementActivity" '$V assert where NetworkProtectionManagementActivity'
check "VPN is Off" '$V text | grep -q "VPN is Off"'
$V shot sub-vpn.png >/dev/null
hub; $V tap id:pirSettings >/dev/null; sleep 4
check "PIR row: PirDashboardWebViewActivity" '$V assert where PirDashboardWebViewActivity'
to_browser; menu
check "browser menu has VPN (includeVpnMenuItem)" '$V assert visible id:includeVpnMenuItem && $V text | grep -q "^label	VPN"'
$V key back >/dev/null
$V record stop >/dev/null

$V record start subscription-2-cli.mp4 >/dev/null
state --tier pro --region us --status active
hub; sub_settings
check "pro us: tier PRO" '[[ "$($V text tierName)" == PRO ]]'
check "pro us: no Upgrade to Pro" '$V assert not-visible id:upgradeToProContainer'
$V shot sub-pro-us-settings.png >/dev/null

state --tier plus --region row --status active
hub
check "plus row: VPN, Duck.ai, ITR, no PIR" '$V assert not-visible id:pirSettings && $V assert visible id:netpPSetting && $V assert visible id:duckAiSettings && $V assert visible id:itrSettings'
$V shot sub-plus-row-hub.png >/dev/null

state --status waiting
hub
check "waiting: Activating under Subscription Settings" '$V text | grep -q Activating'
$V shot sub-waiting-hub.png >/dev/null
sub_settings
check "waiting: Subscribed, expires on" '$V assert where SubscriptionSettingsActivity && $V text subscriptionActiveStatusTextView | grep -q Subscribed && $V text | grep -qi "expires on"'
$V shot sub-waiting-settings.png >/dev/null

state --status expired
hub
check "expired: section hidden" '! $V text | grep -q "DuckDuckGo Subscription"'
to_browser; menu
check "expired: no VPN in the browser menu" '$V assert visible id:settingsMenuItem && $V assert not-visible id:includeVpnMenuItem'
$V key back >/dev/null

state none
hub
check "none again: section hidden" '! $V text | grep -q "DuckDuckGo Subscription"'
$V record stop >/dev/null
echo "RESULT pass=$pass fail=$fail"
