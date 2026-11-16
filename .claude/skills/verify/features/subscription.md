# Subscription state

What a user sees depends on their DuckDuckGo subscription: no subscription, Plus or Pro, US or rest of
world, and active, waiting, expiring or expired. The subscription section in Settings, the Subscription
Settings screen, Duck.ai (header and model picker), the VPN, Personal Information Removal (PIR) and
Identity Theft Restoration change with it; App Tracking Protection does not. Verify a change in any of
these by putting the app in each state the change touches and driving the feature there.

## Sub-features

- `sub-state` `$V subscription ...` puts the app in a tier, region and status; `none` removes it.
- `sub-hub` Settings > DuckDuckGo Subscription lists one row per entitled product plus Subscription Settings.
- `sub-settings` Subscription Settings shows status, tier pill, renew/expiry line, Upgrade to Pro (Plus only), View Plans (expired).
- `sub-duckai` Duck.ai's header, pickers and upsells per tier: the `duckai-tier-*` sub-features in [duck-ai.md](duck-ai.md).
- `sub-duckai-settings` Settings > Duck.ai opens `duckai.paid-settings`.
- `sub-vpn` the VPN row opens `vpn.main`; the browser menu has a VPN item only with the VPN entitlement.
- `sub-pir` the PIR row opens `PirDashboardWebViewActivity` (US plans only).
- `sub-apptp` App Tracking Protection's row is the same in every state.

## How to get to it (user POV)

- Subscription section: Settings (browser menu > `settingsMenuItem`), scroll to "DuckDuckGo Subscription". Rows: `netpPSetting` VPN, `pirSettings` Personal Information Removal, `duckAiSettings` Duck.ai, `itrSettings` Identity Theft Restoration, `subscriptionSettingContainer` Subscription Settings.
- Subscription Settings: that last row; also RMF and New Tab Page messages (deep link screen `ppro.settings`).
- Duck.ai: browser menu (`inputModeWidgetNavMenu`) > `newDuckChatMenuItem`; header ids `aiTitle`, `duckAIFreePill` / `duckAIFreePillTitle`, `duckAIFreePillUpgrade`; model chip `modelPickerChip` (shown once `inputField` is focused).
- VPN: Settings row `netpPSetting`; browser menu item "VPN"; Quick Settings tile (not driven).
- PIR: Settings row `pirSettings`.
- App Tracking Protection: Settings row `vpnSetting` in Protections.
- The seed itself, and the dev screen Settings > Internal Features > "Subscription Settings" (subtitle "Subscription dev settings for internal users"), whose "Subscriptions data" row shows the stored account, subscription, entitlements and tokens.

## Driving it with verify.sh

Preconditions: baseline; trapdoor with the `seed subscription` command (see Gotchas). The seed never
touches a real subscription: it refuses when the app holds a real account or tokens, so on the user's
phone it works only if they are not signed in to a subscription in the internal app.

**Put the app in a state.** `$V subscription --tier pro --region us --status active` prints the seed
reply (`planId`, `products`, `status`) and `app restarted`. Arguments, all optional:

| Argument | Values | Default |
|---|---|---|
| `--tier` | `plus` `pro` (sets the plan id, which is what the app reads the tier from) | `plus` |
| `--region` | `us` `row` | `us` |
| `--status` | `active` `not-renewing` `grace` `waiting` `expired` `inactive` | `active` |
| `--billing` | `yearly` `monthly` | `yearly` |
| `--trial` | `true` `false` | `false` |
| `--products` | comma list of `netp` `itr` `row-itr` `pir` `duckai`, or `none` | US: netp,itr,pir,duckai; ROW: netp,row-itr,duckai |

`--products` drops entitlements to test "subscribed but not entitled" (e.g. `--products netp,itr` has no
PIR or Duck.ai). `$V subscription none` removes the seeded subscription. Every call restarts the app,
because the subscription flows are read once per process.

**Read the state back.** Status labels are not tappable, so read them with `$V text`, not `$V t assert`.

- Hub: `$V t goto settings`, swipe up once (`adb -s <serial> shell input swipe 540 2000 540 900 600`), then `$V text` lists the section between "DuckDuckGo Subscription" and "Other Settings": each row's `primaryText` and its On/Off `label`, and `secondaryText` "Activating" under Subscription Settings while waiting.
- Subscription Settings (the step under test): tap `subscriptionSettingContainer`, `$V t assert where ppro.settings`, then `$V text subscriptionActiveStatusTextView`, `$V text tierName`, `$V text subscriptionExpiredStatusText`; `$V t assert visible id:upgradeToProContainer` for Plus.

**Expected, driven on 2026-11-16 (Pixel 7, internal debug):**

| State | Settings section rows | Subscription Settings |
|---|---|---|
| none | section hidden (see Gotchas) | n/a |
| plus us active | VPN Off, PIR Off, Duck.ai On, ITR On, Subscription Settings | "Subscribed", `PLUS`, Upgrade to Pro, View All Plans, "renews on ..." |
| pro us active | same rows | "Subscribed", `PRO`, View All Plans, no Upgrade to Pro |
| plus row active | VPN, Duck.ai, ITR, Subscription Settings (no PIR) | as plus us |
| waiting | all rows, every label Off, "Activating" under Subscription Settings | "Subscribed", "expires on ..." |
| grace / not-renewing | as active | "Subscribed", "expires on ..." |
| trial (monthly) | as active | "Free Trial Active", "Your free trial ends on ... converts to a monthly paid subscription" |
| expired / inactive | section hidden | `subscriptionExpiredStatusText` "Your subscription expired on <date>.", View Plans, no tier pill (open with `$V t goto ppro.settings`) |
| `--products netp,itr` | VPN, ITR, Subscription Settings | as plus us |

**Duck.ai by tier.** Header, model picker, reasoning picker, upsell routing, downgrade and the
`privacyPro.duckAiPlus` flag are in [duck-ai.md](duck-ai.md) (By subscription tier). Settings row: hub >
`duckAiSettings`, `$V t assert where duckai.paid-settings`; `$V text` shows "Chat privately with
subscriber-exclusive AI models."

**VPN.** Hub > `netpPSetting`, `$V t assert where vpn.main`, `$V text` shows "DuckDuckGo VPN is Off". Browser menu: `$V t tap id:inputModeWidgetNavMenu`, `$V text` contains a `label` "VPN" with the entitlement and none without it (`--products itr,pir,duckai`) or when expired. Do not toggle `netp_toggle`: connecting needs a real token (it fails with the seed) and asks for the system VPN permission.

**PIR.** Hub > `pirSettings` opens `PirDashboardWebViewActivity` showing "Personal Information Removal" and Get Started. Not in ROW plans. Read the dashboard with `$V web eval` (Web Content Debugging on); anything past Get Started calls the backend with the token and fails.

**App Tracking Protection.** `vpnSetting` "App Tracking Protection" and its status were identical in every state above. Its subscription upsells on the trackers screen need a signed-out, purchase-eligible user, which this build cannot be (see Gotchas).

Proof per state: `$V t record start sub-<state>.mp4` before tapping into the surface under test, a shot of the hub and of the surface, the `$V text` output, `$V t record stop`. Finish with `$V subscription none`.

## Gotchas

- The seed needs trapdoor with `seed subscription` / `seed subscription-none` (android-trapdoor `android/trapdoor-bridge-surfaces-internal/.../SeedSubscriptionStateTrapdoorCommand.kt`). `$V t list --fixtures | grep subscription` lists them; if missing, update trapdoor and `$V build`.
- Why the seed stores fake tokens: without a refresh token `SubscriptionsManager.subscriptionStatus()` returns UNKNOWN (`SubscriptionsManager.kt:740-746`), which hides every subscriber row even with entitlements stored. The access token is set to expire in 2099 so the app never tries to refresh it (the path that signs out, `SubscriptionsManager.kt:826-859`); backend calls with it fail and `SubscriptionsCheckWorker` swallows that (`SubscriptionsChecker.kt:96-120`). Observed 2026-11-16: after 90 s in the background and a return to the foreground, logcat showed `SubscriptionsCheckWorker` calling `/api/subscription` with `Worker result FAILURE`, no sign-out, and the tier still `PLUS`. Anything that needs a real token fails: VPN connect, PIR scans, paid Duck.ai replies, model subtitles.
- The debug build cannot show purchase or upsell states. Play has no subscription products for `com.duckduckgo.mobile.android.debug`, (logcat: `Subs: Subscription offers after mapping: []`), so `isEligible()` (`RealSubscriptions.kt:97-107`) is false unless the status is active or waiting. With no subscription or an expired one, the whole Settings section, the VPN menu item, the "Upgrade" pill and the gated picker section are hidden. Expired rows that the code greys out are therefore never visible here; expired is only verifiable on Subscription Settings.
- `$V subscription` relaunches through the launcher. After a force-stop, trapdoor's `goto` is a background activity start that Android blocks (`BAL_BLOCK` in logcat, `goto` returns `notReady: ActivityResumed(...)`).
- `goto browser.duckai-view` can land on the native input's chat list instead of the chat when a previous state left the input focused; open Duck.ai through the browser menu as above.
- Each Duck.ai open adds a tab. On the user's phone, note the tab count before and close the tabs you opened afterwards.
- Leave the user's own VPN and App Tracking Protection switches alone; read their status, never toggle them.
- Driven on 2026-11-16 (Pixel 7, Android 17): `sub-state` for every status, both tiers and regions, trial and `--products`; `sub-hub`; `sub-settings` (via `goto ppro.settings` and the hub row); `sub-duckai`, `sub-duckai-settings`; `sub-vpn` (row and menu); `sub-pir` (row to dashboard); `sub-apptp` (row only). Not driven: VPN connect, the expired-VPN dialog (needs an onboarded VPN), PIR past Get Started, the Quick Settings tile, ITR's web page, plan changes in the paywall WebView.
