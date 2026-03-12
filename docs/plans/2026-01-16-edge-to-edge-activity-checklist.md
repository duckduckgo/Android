# Edge-to-Edge Activity Migration Checklist

> **Reference:** See the [Migration Design Document](./2026-01-16-edge-to-edge-migration-design.md) for architecture details, patterns, and troubleshooting.

---

### Group 1 - Simple Settings/About Screens (lowest risk)

Single scroll content, minimal complexity. These use shared toolbar, so only need `enableEdgeToEdge()` + bottom insets.

- [x] `AboutDuckDuckGoActivity` - app/src/main/java/.../app/about/ ✅ **COMPLETED** (Commit: `c6a59223ae`)
- [x] `GeneralSettingsActivity` - app/src/main/java/.../app/generalsettings/ ✅ **COMPLETED** (Commit: `e0c3ed3c96`)
- [x] `ShowOnAppLaunchActivity` - app/src/main/java/.../app/generalsettings/showonapplaunch/ ✅ **COMPLETED** (Commit: `ee8f95eb4a`)
- [x] `NewTabSettingsActivity` - new-tab-page/new-tab-page-impl/src/main/java/.../newtabpage/impl/settings/ ✅ **COMPLETED** (Commit: `27c8540f8a`)
- [x] `AppearanceActivity` - app/src/main/java/.../app/appearance/ ✅ **COMPLETED** (Commit: `68d9c0a86d`)
- [x] `AccessibilityActivity` - app/src/main/java/.../app/accessibility/ ✅ **COMPLETED** (Commit: `1fed3201cc`)
- [x] `PermissionsActivity` - app/src/main/java/.../app/permissions/ ✅ **COMPLETED** (Commit: `35ee8636cd`)
- [x] `PrivateSearchActivity` - app/src/main/java/.../app/privatesearch/ ✅ **COMPLETED** (Commit: `9d3b466e8a`)
- [x] `GlobalPrivacyControlActivity` - app/src/main/java/.../app/globalprivacycontrol/ui/ ✅ **COMPLETED** (Commit: `bbcfa1d0a3`)
- [x] `WebTrackingProtectionActivity` - app/src/main/java/.../app/webtrackingprotection/ ✅ **COMPLETED** (Commit: `37533fba98`)
- [x] `ThreatProtectionSettingsActivity` - app/src/main/java/.../app/browser/threatprotection/ ✅ **COMPLETED** (Commit: `c428dfd5e8`)
- [x] `FireButtonActivity` - app/src/main/java/.../app/firebutton/ ✅ **COMPLETED** (Commit: `fd9bf9ba9f`) ⚠️**Manual screen recording review required**
- [x] `FireAnimationActivity` - app/src/main/java/.../app/settings/ ✅ **COMPLETED** (Commit: `373c471218`)
- [x] `ChangeIconActivity` - app/src/main/java/.../app/icon/ui/ ✅ **COMPLETED** (Commit: `df6233f52b`)
- [x] `AutoconsentSettingsActivity` - autoconsent/autoconsent-impl/src/main/java/.../autoconsent/impl/ui/ ✅ **COMPLETED** (Commit: `4d64416952`)
- [x] `DuckPlayerSettingsActivity` - duckplayer/duckplayer-impl/src/main/java/.../duckplayer/impl/ ✅ **COMPLETED** (Commit: `62be9a8b94`)
- [x] `DuckChatSettingsActivity` - duckchat/duckchat-impl/src/main/java/.../duckchat/impl/ui/settings/ ✅ **COMPLETED** (Commit: `4ecfb81190`)
- [x] `DuckAiShortcutSettingsActivity` - duckchat/duckchat-impl/src/main/java/.../duckchat/impl/ui/settings/ ✅ **COMPLETED** (Commit: `c0a6a7ca5a`)
- [x] `DuckAiPaidSettingsActivity` - duckchat/duckchat-impl/src/main/java/.../duckchat/impl/subscription/ ✅ **COMPLETED** (Commit: `f961ec027d`)
- [x] `AutofillSettingsActivity` - autofill/autofill-impl/src/main/java/.../autofill/impl/ui/settings/ ✅ **COMPLETED** (Commit: `33ea4293be`)
- [x] `SitePermissionsActivity` - site-permissions/site-permissions-impl/src/main/java/.../site/permissions/impl/ui/ ✅ **COMPLETED** (Commit: `df6c880e0b`)
- [x] `PermissionsPerWebsiteActivity` - site-permissions/site-permissions-impl/src/main/java/.../site/permissions/impl/ui/permissionsperwebsite/ ✅ **COMPLETED** (Commit: `562043c57f`)
- [x] `NetPVpnSettingsActivity` - network-protection/network-protection-impl/src/main/java/.../networkprotection/impl/settings/ ✅ **COMPLETED** (Commit: `0250c0e95c`)
- [x] `VpnCustomDnsActivity` - network-protection/network-protection-impl/src/main/java/.../networkprotection/impl/settings/custom_dns/ ✅ **COMPLETED** (Commit: `b71f621e2b`)
- [x] `NetpGeoswitchingActivity` - network-protection/network-protection-impl/src/main/java/.../networkprotection/impl/settings/geoswitching/ ✅ **COMPLETED** (Commit: `4cf8f00a95`)
- [x] `SubscriptionSettingsActivity` - subscriptions/subscriptions-impl/src/main/java/.../subscriptions/impl/ui/ ✅ **COMPLETED** (Commit: `32cfde2dfe`)
- [x] `AutomaticDataClearingSettingsActivity` - app/src/main/java/.../app/firebutton/ ✅ **COMPLETED** (Commit: `1dda5bfe93`)
- [x] `DataClearingSettingsActivity` - app/src/main/java/.../app/firebutton/ ✅ **COMPLETED** (Commit: `ad01c31320`)

### Group 2 - Internal/Dev Settings Screens

Lower risk since internal-only, good for practice.

- [x] `DevSettingsActivity` - app/src/internal/java/.../app/dev/settings/ ✅ **COMPLETED** (Commit: `fbf1d2d185`)
- [x] `DevTabsActivity` - app/src/internal/java/.../app/dev/settings/tabs/ ✅ **COMPLETED** (Commit: `7a6b23aa7f`)
- [x] `AuditSettingsActivity` - app/src/internal/java/.../app/audit/ ✅ **COMPLETED** (Commit: `24abe24344`)
- [x] `WebViewDevSettingsActivity` - app/src/internal/java/.../app/browser/webview/ ✅ **COMPLETED** (Commit: `eb679cb334`)
- [x] `CustomTabsInternalSettingsActivity` - app/src/internal/java/.../app/dev/settings/customtabs/ ✅ **COMPLETED** (Commit: `e4045e6ff4`)
- [x] `NotificationsActivity` - app/src/internal/java/.../app/dev/settings/notifications/ ✅ **COMPLETED** (Commit: `7768095bbc`)
- [x] `AppComponentsActivity` - android-design-system/design-system-internal/src/main/java/.../common/ui/internal/ui/ ✅ **COMPLETED** (Commit: `21f5c63be2`)
- [x] `CrashANRsInternalSettingsActivity` - anrs/anrs-internal/src/main/java/.../app/anr/internal/feature/ ✅ **COMPLETED** (Commit: `defdc54c18`)
- [x] `VpnInternalSettingsActivity` - app-tracking-protection/vpn-internal/src/main/java/.../vpn/internal/feature/ ✅ **COMPLETED** (Commit: `ac408e3134`)
- [x] `ExceptionRulesDebugActivity` - app-tracking-protection/vpn-internal/src/main/java/.../vpn/internal/feature/rules/ ✅ **COMPLETED** (Commit: `f12df2e381`)
- [x] `AutofillInternalSettingsActivity` - autofill/autofill-internal/src/main/java/.../autofill/internal/ ✅ **COMPLETED** (Commit: `51feb6a0a7`)
- [x] `FeatureToggleInventoryActivity` - feature-toggles/feature-toggles-internal/src/main/java/.../examplefeature/internal/ui/ ✅ **COMPLETED** (Commit: `0700ad1dbc`)
- [x] `NetPInternalSettingsActivity` - network-protection/network-protection-internal/src/main/java/.../networkprotection/internal/feature/ ✅ **COMPLETED** (Commit: `b78af24971`)
- [x] `NetPSystemAppsExclusionListActivity` - network-protection/network-protection-internal/src/main/java/.../networkprotection/internal/feature/system_apps/ ✅ **COMPLETED** (Commit: `d319a17e67`)
- [x] `PirDevSettingsActivity` - pir/pir-internal/src/main/java/.../pir/internal/settings/ ✅ **COMPLETED** (Commit: `5e7f150557`)
- [x] `PirDevWebViewActivity` - pir/pir-internal/src/main/java/.../pir/internal/settings/ ✅ **COMPLETED** (Commit: `1f68cf5031`)
- [x] `PirDevOptOutActivity` - pir/pir-internal/src/main/java/.../pir/internal/settings/ ✅ **COMPLETED** (Commit: `fb186fc454`)
- [x] `PirDevScanActivity` - pir/pir-internal/src/main/java/.../pir/internal/settings/ ✅ **COMPLETED** (Commit: `da5bd7d305`)
- [x] `PirResultsActivity` - pir/pir-internal/src/main/java/.../pir/internal/settings/ ✅ **COMPLETED** (Commit: `c8cc238385`)
- [x] `PrivacyConfigInternalSettingsActivity` - privacy-config/privacy-config-internal/src/main/java/.../privacy/config/internal/ ✅ **COMPLETED** (Commit: `762b15843c`)
- [x] `RMFInternalSettingsActivity` - remote-messaging/remote-messaging-internal/src/main/java/.../remote/messaging/internal/feature/ ✅ **COMPLETED** (Commit: `4bafa1cbf3`)
- [x] `SavedSitesInternalSettingsActivity` - saved-sites/saved-sites-internal/src/main/java/.../savedsites/internal/ ✅ **COMPLETED** (Commit: `62004ebe52`)
- [x] `SyncInternalSettingsActivity` - sync/sync-impl/src/main/java/.../sync/impl/ui/ ✅ **COMPLETED** (Commit: `f303530a98`)
- [x] `SubscriptionsInternalSettingsActivity` - subscriptions/subscriptions-internal/src/main/java/.../subscriptions/internal/ ✅ **COMPLETED** (Commit: `a0be99a5a2`)
- [x] `AttributedMetricsDevSettingsActivity` - attributed-metrics/attributed-metrics-internal/src/main/java/.../attributed/metrics/internal/ui/ ✅ **COMPLETED** (Commit: `cc5e25bd8d`)
- [x] `OnboardingDevSettingsActivity` - app/src/internal/java/.../app/dev/settings/onboarding/ ✅ **COMPLETED** (Commit: `378d5948ff`)
- [x] `PirDevBrokerConfigActivity` - pir/pir-internal/src/main/java/.../pir/internal/settings/ ✅ **COMPLETED** (Commit: `e3a2bb6f5d`)
- [x] `PirDevEmailActivity` - pir/pir-internal/src/main/java/.../pir/internal/settings/ ✅ **COMPLETED** (Commit: `2eb71d9ac9`)

### Group 3 - List/Management Screens with Toolbars

Screens with RecyclerViews and standard toolbars.

- [x] `SettingsActivity` - app/src/main/java/.../app/settings/ ✅ **COMPLETED** (Commit: `0e171f9230`)
- [x] `BookmarksActivity` - saved-sites/saved-sites-impl/src/main/java/.../savedsites/impl/bookmarks/ ✅ **COMPLETED** (Commit: `90d70bd23c`)
- [x] `BookmarkFoldersActivity` - saved-sites/saved-sites-impl/src/main/java/.../savedsites/impl/folders/ ✅ **COMPLETED** (Commit: `8d8880cdef`)
- [x] `DownloadsActivity` - app/src/main/java/.../app/downloads/ ✅ **COMPLETED** (Commit: `f2d78df3b0`)
- [x] `FireproofWebsitesActivity` - app/src/main/java/.../app/fire/fireproofwebsite/ui/ ✅ **COMPLETED** (Commit: `e2bae62677`)
- [x] `AllowListActivity` - app/src/main/java/.../app/privacy/ui/ ✅ **COMPLETED** (Commit: `063f40834f`)
- [x] `AutofillManagementActivity` - autofill/autofill-impl/src/main/java/.../autofill/impl/ui/credential/management/ ✅ **COMPLETED** (Commit: `c08b95002c`)
- [x] `ImportPasswordsActivity` - autofill/autofill-impl/src/main/java/.../autofill/impl/ui/credential/management/importpassword/ ✅ **COMPLETED** (Commit: `03d8302f4c`)
- [x] `ImportPasswordsGetDesktopAppActivity` - autofill/autofill-impl/src/main/java/.../autofill/impl/ui/credential/management/importpassword/desktopapp/ ✅ **COMPLETED** (Commit: `b1fc01f65d`)
- [x] `TrackingProtectionExclusionListActivity` - app-tracking-protection/vpn-impl/src/main/java/.../mobile/android/vpn/apps/ui/ ✅ **COMPLETED** (Commit: `1a2ef72ff9`)
- [x] `ManageRecentAppsProtectionActivity` - app-tracking-protection/vpn-impl/src/main/java/.../mobile/android/vpn/apps/ui/ ✅ **COMPLETED** (Commit: `61f071cdad`)
- [x] `NetpAppExclusionListActivity` - network-protection/network-protection-impl/src/main/java/.../networkprotection/impl/exclusion/ui/ ✅ **COMPLETED** (Commit: `4aa58e6346`)
- [x] `TabSwitcherActivity` - app/src/main/java/.../app/tabs/ui/ ✅ **COMPLETED** (Commit: `ce0950768f`)

### Group 4 - Feature Screens with Custom Layouts

More complex layouts, may have bottom elements.

- [x] `OnboardingActivity` - app/src/main/java/.../app/onboarding/ui/ ✅ **COMPLETED** (Commit: `9f77533c12`)
- [x] `FeedbackActivity` - app/src/main/java/.../app/feedback/ui/common/ ✅ **COMPLETED** (Commit: `df5f56d0c6`)
- [x] `SurveyActivity` - app/src/main/java/.../app/survey/ui/ ⏭️ **SKIPPED** - Not edge-to-edge, respects system insets
- [x] `SubscriptionFeedbackActivity` - subscriptions/subscriptions-impl/src/main/java/.../subscriptions/impl/feedback/ ✅ **COMPLETED** (Commit: `b90f31623f`)
- [x] `AddWidgetInstructionsActivity` - app/src/main/java/.../app/widget/ui/ ✅ **COMPLETED** (Commit: `2b5cb3403d`)
- [x] `VpnOnboardingActivity` - app-tracking-protection/vpn-impl/src/main/java/.../mobile/android/vpn/ui/onboarding/ ✅ **COMPLETED** (Commit: `9cc2952f0d`)
- [x] `DeviceShieldTrackerActivity` - app-tracking-protection/vpn-impl/src/main/java/.../mobile/android/vpn/ui/tracker_activity/ ✅ **COMPLETED** (Commit: `2a8338fac7`)
- [x] `DeviceShieldMostRecentActivity` - app-tracking-protection/vpn-impl/src/main/java/.../mobile/android/vpn/ui/tracker_activity/ ✅ **COMPLETED** (Commit: `a98373c1ec`)
- [x] `AppTPCompanyTrackersActivity` - app-tracking-protection/vpn-impl/src/main/java/.../mobile/android/vpn/ui/tracker_activity/ ✅ **COMPLETED** (Commit: `6ad404a91a`)
- [x] `ReportBreakageAppListActivity` - app-tracking-protection/vpn-impl/src/main/java/.../mobile/android/vpn/breakage/ ✅ **COMPLETED** (Commit: `36476b2f81`)
- [x] `ReportBreakageCategorySingleChoiceActivity` - app-tracking-protection/vpn-impl/src/main/java/.../mobile/android/vpn/breakage/ ✅ **COMPLETED** (Commit: `c04ae1d7e1`)
- [x] `NetworkProtectionManagementActivity` - network-protection/network-protection-impl/src/main/java/.../networkprotection/impl/management/ ✅ **COMPLETED** (Commit: `3378751aa2`)
- [x] `SyncActivity` - sync/sync-impl/src/main/java/.../sync/impl/ui/ ✅ **COMPLETED** (Commit: `b1bf9839f8`)
- [x] `SyncConnectActivity` - sync/sync-impl/src/main/java/.../sync/impl/ui/ ✅ **COMPLETED** (Commit: `8e7cf24359`)
- [x] `SyncLoginActivity` - sync/sync-impl/src/main/java/.../sync/impl/ui/ ✅ **COMPLETED** (Commit: `a6cbe1b9b4`)
- [x] `SyncWithAnotherDeviceActivity` - sync/sync-impl/src/main/java/.../sync/impl/ui/ ✅ **COMPLETED** (Commit: `3ab1f2be4f`)
- [x] `SetupAccountActivity` - sync/sync-impl/src/main/java/.../sync/impl/ui/setup/ ✅ **COMPLETED** (Commit: `d6a32cfe5f`) ⚠️ **Pattern D** - Fragments need separate inset handling (see Group 10)
- [x] `EnterCodeActivity` - sync/sync-impl/src/main/java/.../sync/impl/ui/ ✅ **COMPLETED** (Commit: `84b2733518`)
- [x] `DeviceUnsupportedActivity` - sync/sync-impl/src/main/java/.../sync/impl/ui/ ✅ **COMPLETED** (Commit: `29179ee72a`)
- [x] `SyncGetOnOtherPlatformsActivity` - sync/sync-impl/src/main/java/.../sync/impl/promotion/ ✅ **COMPLETED** (Commit: `0815237547`)
- [x] `ChangePlanActivity` - subscriptions/subscriptions-impl/src/main/java/.../subscriptions/impl/ui/ ✅ **COMPLETED** (Commit: `6de81f3461`)
- [x] `RestoreSubscriptionActivity` - subscriptions/subscriptions-impl/src/main/java/.../subscriptions/impl/ui/ ✅ **COMPLETED** (Commit: `3c21044ec2`)
- [x] `PirActivity` - subscriptions/subscriptions-impl/src/main/java/.../subscriptions/impl/pir/ ✅ **COMPLETED** (Commit: `344f8af897`)
- [x] `MacOsActivity` - macos/macos-impl/src/main/java/.../macos/impl/ ✅ **COMPLETED** (Commit: `234e940c2a`)
- [x] `WindowsActivity` - windows/windows-impl/src/main/java/.../windows/impl/ui/ ✅ **COMPLETED** (Commit: `c9d3b0fce1`)
- [x] `EmailProtectionUnsupportedActivity` - app/src/main/java/.../app/email/ui/ ✅ **COMPLETED** (Commit: `bf69a2605a`)
- [x] `EmailProtectionInContextSignupActivity` - autofill/autofill-impl/src/main/java/.../autofill/impl/email/incontext/ ✅ **COMPLETED** (Commit: `bf69a2605a`)
- [x] `InputScreenActivity` - duckchat/duckchat-impl/src/main/java/.../duckchat/impl/inputscreen/ui/ ✅ **COMPLETED** (Commit: `3d9ba775fc`)
- [x] `ModalSurfaceActivity` - remote-messaging/remote-messaging-impl/src/main/java/.../remote/messaging/impl/modal/ ✅ **COMPLETED** (Commit: `77b532e0ff`)
- [x] `SerpEasterEggLogoActivity` - serp-logos/serp-logos-impl/src/main/kotlin/.../serp/logos/impl/ui/ ✅ **COMPLETED** (Commit: `ba5205488f`)
- [ ] `GetDesktopBrowserActivity` - app/src/main/java/.../app/desktopbrowser/

### Group 5 - WebView Screens

Need special handling per WebView edge-to-edge guide.

- [x] `WebViewActivity` - app/src/main/java/.../app/browser/webview/ ✅ **COMPLETED**
- [x] `SettingsWebViewActivity` - settings/settings-impl/src/main/java/.../settings/impl/ ✅ **COMPLETED** (Commit: `408f46ad83`)
- [x] `PrivacyDashboardHybridActivity` - privacy-dashboard/privacy-dashboard-impl/src/main/java/.../privacy/dashboard/impl/ui/ ✅ **COMPLETED** (Commit: `62ada574c9`)
  > **Implementation Note:** JS injection for insets didn't work because the
  > Privacy Dashboard uses `position: fixed` for its `.top-nav` element. Instead,
  > we use container padding with dynamic background color extraction (query
  > `document.body.backgroundColor` via JavaScript after page load).
  >
  > **Future FE Support:** If the `@duckduckgo/privacy-dashboard` npm package
  > adds `viewport-fit=cover` to the viewport meta tag and CSS that consumes
  > `--safe-area-inset-*` custom properties, true edge-to-edge WebView rendering
  > can be achieved by injecting inset values via `PrivacyDashboardRenderer.applyEdgeToEdgeInsets()`.
- [x] `PirDashboardWebViewActivity` - pir/pir-impl/src/main/java/.../pir/impl/dashboard/ ✅ **COMPLETED** (Commit: `932abaff79`)
- [x] `SubscriptionsWebViewActivity` - subscriptions/subscriptions-impl/src/main/java/.../subscriptions/impl/ui/ ✅ **COMPLETED** (Commit: `d692636465`)
- [x] `ImportGooglePasswordsWebFlowActivity` - autofill/autofill-impl/src/main/java/.../autofill/impl/importing/gpm/webflow/ ✅ **COMPLETED** (Commit: `e56496c437`)
- [x] `ImportGoogleBookmarksWebFlowActivity` - autofill/autofill-impl/src/main/java/.../autofill/impl/importing/takeout/webflow/ ✅ **COMPLETED** (Commit: `074346732d`)

### Group 6 - Autofill Service Activities

Special system integration screens.

- [x] `AutofillProviderChooseActivity` - autofill/autofill-impl/src/main/java/.../autofill/impl/service/ ✅ **COMPLETED** (Commit: `cbe696eef5`)
- [x] `AutofillProviderFillSuggestionActivity` - autofill/autofill-impl/src/main/java/.../autofill/impl/service/ ✅ **COMPLETED** (Commit: `1ee083dd30`)

### Group 7 - Transparent/Launcher Activities

May need special handling or no changes.

- [x] `LaunchBridgeActivity` - app/src/main/java/.../app/launch/ ✅ **COMPLETED** (Commit: c030150504)
- [x] `IntentDispatcherActivity` ✅ **COMPLETED** (Commit: 06cc8cfbd4) - app/src/main/java/.../app/dispatchers/
- [x] `FireActivity` ✅ **N/A** - Runs in separate `:fire` process with no Dagger graph, and kills itself immediately after restarting the app - app/src/main/java/.../app/fire/
- [x] `SelectedTextSearchActivity` ✅ **COMPLETED** (Commit: 523a714e8f) - app/src/main/java/.../app/
- [x] `DuckAiPinShortcutActivity` ✅ **COMPLETED** (Commit: b9ac06300b) - app/src/main/java/.../widget/
- [x] `CustomTabActivity` ✅ **COMPLETED** (Commit: 69394989cc) - app/src/main/java/.../app/browser/customtabs/

### Group 8 - Already Edge-to-Edge (Verify Only)

These already have edge-to-edge support. Verify they still work correctly.

- [x] `VoiceSearchActivity` - voice-search/voice-search-impl/src/main/java/.../voice/impl/listeningmode/ ✅ **VERIFIED** (Code inspection - auto-closes on focus loss, proper edge-to-edge implementation confirmed)
- [x] `DaxPromptBrowserComparisonActivity` - dax-prompts/dax-prompts-impl/src/main/java/.../daxprompts/impl/ui/ ✅ **MIGRATED** — Had partial edge-to-edge via manual flags (`FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS` + `WindowCompat.setDecorFitsSystemWindows`) but used `navigationBarColor = Color.BLACK` instead of `enableEdgeToEdge()`. Migrated to standard pattern with `enableEdgeToEdge()` + `ViewCompat.setOnApplyWindowInsetsListener` + `updatePadding(top, bottom)`. (Screenshot: dax-prompt-browser-comparison-after.png)

### Group 9 - Complex/High-Risk (last)

Main browser and system search - tackle these last.

- [x] `BrowserActivity` - app/src/main/java/.../app/browser/ ✅ **COMPLETED** (Commit: `a417b1ac08`) - Activity-level only, fragments deferred
- [x] `SystemSearchActivity` - app/src/main/java/.../app/systemsearch/ ✅ **COMPLETED** (Commit: `873d483844`)

---

### Group 10 - Fragments (Pattern D)

Fragments hosted by activities using Pattern D (full-screen FragmentContainerView). These fragments have bottom-positioned content that needs inset handling.

**Parent Activity: `SetupAccountActivity`** (Pattern D - already migrated, just `enableEdgeToEdge()`)

- [x] `SyncDeviceConnectedFragment` - sync/sync-impl/src/main/java/.../sync/impl/ui/setup/ - Has "Done" button and "Get DuckDuckGo on Other Devices" link at bottom ✅ **COMPLETED** (Commit: `7f66a7e814`)
- [x] `SyncSetupIntroFragment` - sync/sync-impl/src/main/java/.../sync/impl/ui/setup/ - Has button and footer text at bottom ✅ **COMPLETED** (Commit: `7f66a7e814`)
- [x] `SaveRecoveryCodeFragment` - sync/sync-impl/src/main/java/.../sync/impl/ui/setup/ - Has "Next" button at bottom ✅ **COMPLETED** (Commit: `7f66a7e814`)
- [x] `SyncCreateAccountFragment` - sync/sync-impl/src/main/java/.../sync/impl/ui/setup/ - Has loading indicator and text at bottom ✅ **COMPLETED**

**Parent Activity: `SyncWithAnotherDeviceActivity`** (has own inset handling via ScrollView)

- [x] `SyncSetupDeepLinkFragment` - sync/sync-impl/src/main/java/.../sync/impl/ui/setup/ - May need review if shown without activity's ScrollView insets ✅ **COMPLETED**

**Parent Activity: `InputScreenActivity`** (Pattern D - already migrated with `enableEdgeToEdge()`)

- [x] `InputScreenFragment` - duckchat/duckchat-impl/src/main/java/.../duckchat/impl/inputscreen/ui/ - Dual-mode (top bar/bottom bar) inset handling for search/chat input ✅ **COMPLETED** (Commit: `d1e553a890`)

**Parent Activity: `BrowserActivity`** (Pattern D - already migrated with `enableEdgeToEdge()`)

- [x] `DuckChatWebViewFragment` - duckchat/duckchat-impl/src/main/java/.../duckchat/impl/ui/ - WebView chat interface needing bottom insets (Pattern D+W with IME) ✅ **COMPLETED**
