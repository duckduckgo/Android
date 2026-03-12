# Edge-to-Edge Commit Cleanup Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Transform 260 messy commits into clean, one-per-activity commits ordered by the checklist's risk groups.

**Architecture:** Create a new branch from `main`, then replay each activity's final file state as a single commit in checklist order. Since no files overlap between activities (confirmed), we can safely checkout each activity's files from the completed branch tip.

**Tech Stack:** Git (checkout --no-overlay, branch, commit)

---

## Current State

- **Branch:** `feature/mike/edgetoedge` (260 commits over `main`)
- **Noise:** ~76 docs-only commits, ~8 fix/revert commits, ~48 from-main PR commits
- **No file overlaps** between activities — each migration touches unique files
- **SurveyActivity** was migrated, fixed, then reverted — net zero changes (skip)
- **BrowserActivity, WebViewActivity, PirActivity, SyncConnectActivity, FeatureToggleInventoryActivity** each have multiple commits that need squashing

## Approach

1. Back up the current branch
2. Create a new branch from `main`
3. For each activity (in checklist order), `git checkout` its files from the completed branch and commit
4. Final commit: docs (design doc + checklist + screenshots for non-activity items)

**Why this works:** Since every activity touches unique files, we just grab the final state from the branch tip. Multi-commit activities (BrowserActivity, etc.) are automatically squashed because we take the end result.

## Files to EXCLUDE

| File | Reason |
|------|--------|
| `gradle/gradle-daemon-jvm.properties` | Accidentally created, not on main |
| SurveyActivity changes | Net zero (migrated then fully reverted) |
| All `docs/plans/*.md` changes | Consolidated into final docs commit |

## Source Branch Reference

Throughout this plan, `SOURCE` refers to `feature/mike/edgetoedge` (the completed branch before cleanup).

---

## Task 0: Setup

**Step 1: Back up current branch**

```bash
git branch backup/edgetoedge-pre-cleanup
```

**Step 2: Create clean branch from main**

```bash
git checkout -b feature/mike/edgetoedge-clean main
```

**Step 3: Verify clean state**

```bash
git log --oneline -1  # Should show main's HEAD
```

---

## Task 1: Shared Toolbar Fix (prerequisite for all activities)

**Files:**
- `android-design-system/design-system/src/main/res/layout/include_default_toolbar.xml`

**Step 1: Checkout and commit**

```bash
git checkout SOURCE -- \
  android-design-system/design-system/src/main/res/layout/include_default_toolbar.xml

git commit -m "$(cat <<'EOF'
edge-to-edge: Add background to shared toolbar AppBarLayout

The AppBarLayout had fitsSystemWindows=true which adds top padding,
but no background color was set. This caused the status bar area to
show the window background instead of the toolbar color.

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Group 1 — Simple Settings/About Screens

Each commit below follows this pattern:
```bash
git checkout SOURCE -- <files...>
git add -A
git commit -m "edge-to-edge: Migrate <ActivityName>

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### 2.1 AboutDuckDuckGoActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/about/AboutDuckDuckGoActivity.kt \
  docs/plans/screenshots/about-after.png \
  docs/plans/screenshots/about-before.png
```

### 2.2 GeneralSettingsActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/generalsettings/GeneralSettingsActivity.kt \
  app/src/main/res/layout/activity_general_settings.xml \
  docs/plans/screenshots/general-settings-after.png \
  docs/plans/screenshots/general-settings-before.png
```

### 2.3 ShowOnAppLaunchActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/generalsettings/showonapplaunch/ShowOnAppLaunchActivity.kt \
  app/src/main/res/layout/activity_show_on_app_launch_setting.xml \
  docs/plans/screenshots/show-on-app-launch-after.png \
  docs/plans/screenshots/show-on-app-launch-before.png
```

### 2.4 NewTabSettingsActivity

```bash
git checkout SOURCE -- \
  new-tab-page/new-tab-page-impl/src/main/java/com/duckduckgo/newtabpage/impl/settings/NewTabSettingsActivity.kt \
  docs/plans/screenshots/new-tab-settings-after.png \
  docs/plans/screenshots/new-tab-settings-before.png
```

### 2.5 AppearanceActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/appearance/AppearanceActivity.kt \
  docs/plans/screenshots/appearance-after.png \
  docs/plans/screenshots/appearance-before.png
```

### 2.6 AccessibilityActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/accessibility/AccessibilityActivity.kt \
  docs/plans/screenshots/accessibility-after.png \
  docs/plans/screenshots/accessibility-before.png
```

### 2.7 PermissionsActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/permissions/PermissionsActivity.kt \
  app/src/main/res/layout/content_permissions.xml \
  docs/plans/screenshots/permissions-after.png \
  docs/plans/screenshots/permissions-before.png
```

### 2.8 PrivateSearchActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/privatesearch/PrivateSearchActivity.kt \
  app/src/main/res/layout/activity_private_search.xml \
  docs/plans/screenshots/private-search-after.png \
  docs/plans/screenshots/private-search-before.png
```

### 2.9 GlobalPrivacyControlActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/globalprivacycontrol/ui/GlobalPrivacyControlActivity.kt \
  docs/plans/screenshots/global-privacy-control-after.png \
  docs/plans/screenshots/global-privacy-control-before.png
```

### 2.10 WebTrackingProtectionActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/webtrackingprotection/WebTrackingProtectionActivity.kt \
  app/src/main/res/layout/activity_web_tracking_protection.xml \
  docs/plans/screenshots/web-tracking-protection-after.png \
  docs/plans/screenshots/web-tracking-protection-before.png
```

### 2.11 ThreatProtectionSettingsActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/browser/threatprotection/ThreatProtectionSettingsActivity.kt \
  app/src/main/res/layout/activity_threat_protection_settings.xml \
  docs/plans/screenshots/threat-protection-settings-after.png \
  docs/plans/screenshots/threat-protection-settings-before.png
```

### 2.12 FireButtonActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/firebutton/FireButtonActivity.kt \
  app/src/main/res/layout/activity_data_clearing.xml \
  docs/plans/screenshots/fire-button-after.png \
  docs/plans/screenshots/fire-button-before.png
```

### 2.13 FireAnimationActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/settings/FireAnimationActivity.kt
```

> Note: No screenshots — FireAnimationActivity is a dialog picker, no visible layout change.

### 2.14 ChangeIconActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/icon/ui/ChangeIconActivity.kt \
  app/src/main/res/layout/activity_app_icons.xml \
  docs/plans/screenshots/change-icon-after.png \
  docs/plans/screenshots/change-icon-before.png
```

### 2.15 AutoconsentSettingsActivity

```bash
git checkout SOURCE -- \
  autoconsent/autoconsent-impl/src/main/java/com/duckduckgo/autoconsent/impl/ui/AutoconsentSettingsActivity.kt \
  autoconsent/autoconsent-impl/src/main/res/layout/activity_autoconsent_settings.xml \
  docs/plans/screenshots/autoconsent-settings-after.png \
  docs/plans/screenshots/autoconsent-settings-before.png
```

### 2.16 DuckPlayerSettingsActivity

```bash
git checkout SOURCE -- \
  duckplayer/duckplayer-impl/src/main/java/com/duckduckgo/duckplayer/impl/DuckPlayerSettingsActivity.kt \
  duckplayer/duckplayer-impl/src/main/res/layout/activity_duck_player_settings.xml \
  docs/plans/screenshots/duck-player-settings-after.png \
  docs/plans/screenshots/duck-player-settings-before.png
```

### 2.17 DuckChatSettingsActivity

```bash
git checkout SOURCE -- \
  duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/settings/DuckChatSettingsActivity.kt \
  duckchat/duckchat-impl/src/main/res/layout/activity_duck_chat_settings.xml \
  docs/plans/screenshots/duck-chat-settings-after.png \
  docs/plans/screenshots/duck-chat-settings-before.png
```

### 2.18 DuckAiShortcutSettingsActivity

```bash
git checkout SOURCE -- \
  duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/settings/DuckAiShortcutSettingsActivity.kt \
  duckchat/duckchat-impl/src/main/res/layout/activity_duck_ai_shortcut_settings.xml \
  docs/plans/screenshots/duck-ai-shortcut-settings-after.png \
  docs/plans/screenshots/duck-ai-shortcut-settings-before.png
```

### 2.19 DuckAiPaidSettingsActivity

```bash
git checkout SOURCE -- \
  duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/subscription/DuckAiPaidSettingsActivity.kt \
  duckchat/duckchat-impl/src/main/res/layout/activity_duck_ai_paid_settings.xml \
  docs/plans/screenshots/duck-ai-paid-settings-after.png \
  docs/plans/screenshots/duck-ai-paid-settings-before.png
```

### 2.20 AutofillSettingsActivity

```bash
git checkout SOURCE -- \
  autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/ui/settings/AutofillSettingsActivity.kt \
  autofill/autofill-impl/src/main/res/layout/view_autofill_settings.xml \
  docs/plans/screenshots/autofill-settings-after.png \
  docs/plans/screenshots/autofill-settings-before.png
```

### 2.21 SitePermissionsActivity

```bash
git checkout SOURCE -- \
  site-permissions/site-permissions-impl/src/main/java/com/duckduckgo/site/permissions/impl/ui/SitePermissionsActivity.kt \
  site-permissions/site-permissions-impl/src/main/res/layout/activity_site_permissions.xml \
  docs/plans/screenshots/site-permissions-after.png \
  docs/plans/screenshots/site-permissions-before.png
```

### 2.22 PermissionsPerWebsiteActivity

```bash
git checkout SOURCE -- \
  site-permissions/site-permissions-impl/src/main/java/com/duckduckgo/site/permissions/impl/ui/permissionsperwebsite/PermissionsPerWebsiteActivity.kt \
  site-permissions/site-permissions-impl/src/main/res/layout/activity_permission_per_website.xml \
  docs/plans/screenshots/permissions-per-website-after.png \
  docs/plans/screenshots/permissions-per-website-before.png
```

### 2.23 NetPVpnSettingsActivity

```bash
git checkout SOURCE -- \
  network-protection/network-protection-impl/src/main/java/com/duckduckgo/networkprotection/impl/settings/NetPVpnSettingsActivity.kt \
  network-protection/network-protection-impl/src/main/res/layout/activity_netp_vpn_settings.xml \
  docs/plans/screenshots/netp-vpn-settings-after.png \
  docs/plans/screenshots/netp-vpn-settings-before.png
```

### 2.24 VpnCustomDnsActivity

```bash
git checkout SOURCE -- \
  network-protection/network-protection-impl/src/main/java/com/duckduckgo/networkprotection/impl/settings/custom_dns/VpnCustomDnsActivity.kt \
  network-protection/network-protection-impl/src/main/res/layout/activity_netp_custom_dns.xml \
  docs/plans/screenshots/vpn-custom-dns-after.png \
  docs/plans/screenshots/vpn-custom-dns-before.png
```

### 2.25 NetpGeoswitchingActivity

```bash
git checkout SOURCE -- \
  network-protection/network-protection-impl/src/main/java/com/duckduckgo/networkprotection/impl/settings/geoswitching/NetpGeoswitchingActivity.kt \
  docs/plans/screenshots/netp-geoswitching-after.png \
  docs/plans/screenshots/netp-geoswitching-before.png
```

### 2.26 SubscriptionSettingsActivity

```bash
git checkout SOURCE -- \
  subscriptions/subscriptions-impl/src/main/java/com/duckduckgo/subscriptions/impl/ui/SubscriptionSettingsActivity.kt \
  subscriptions/subscriptions-impl/src/main/res/layout/activity_subscription_settings.xml \
  docs/plans/screenshots/subscription-settings-after.png \
  docs/plans/screenshots/subscription-settings-before.png
```

---

## Task 3: Group 2 — Internal/Dev Settings Screens

### 3.1 DevSettingsActivity

```bash
git checkout SOURCE -- \
  app/src/internal/java/com/duckduckgo/app/dev/settings/DevSettingsActivity.kt \
  app/src/internal/res/layout/activity_dev_settings.xml \
  docs/plans/screenshots/dev-settings-after.png \
  docs/plans/screenshots/dev-settings-before.png
```

### 3.2 DevTabsActivity

```bash
git checkout SOURCE -- \
  app/src/internal/java/com/duckduckgo/app/dev/settings/tabs/DevTabsActivity.kt \
  docs/plans/screenshots/dev-tabs-after.png \
  docs/plans/screenshots/dev-tabs-before.png
```

### 3.3 AuditSettingsActivity

```bash
git checkout SOURCE -- \
  app/src/internal/java/com/duckduckgo/app/audit/AuditSettingsActivity.kt \
  app/src/internal/res/layout/activity_audit_settings.xml \
  docs/plans/screenshots/audit-settings-after.png \
  docs/plans/screenshots/audit-settings-before.png
```

### 3.4 WebViewDevSettingsActivity

```bash
git checkout SOURCE -- \
  app/src/internal/java/com/duckduckgo/app/browser/webview/WebViewDevSettingsActivity.kt \
  app/src/internal/res/layout/activity_web_view_dev_settings.xml \
  docs/plans/screenshots/webview-dev-settings-after.png \
  docs/plans/screenshots/webview-dev-settings-before.png
```

### 3.5 CustomTabsInternalSettingsActivity

```bash
git checkout SOURCE -- \
  app/src/internal/java/com/duckduckgo/app/dev/settings/customtabs/CustomTabsInternalSettingsActivity.kt \
  app/src/internal/res/layout/activity_custom_tabs_internal_settings.xml \
  docs/plans/screenshots/custom-tabs-internal-settings-after.png \
  docs/plans/screenshots/custom-tabs-internal-settings-before.png
```

### 3.6 NotificationsActivity

```bash
git checkout SOURCE -- \
  app/src/internal/java/com/duckduckgo/app/dev/settings/notifications/NotificationsActivity.kt \
  app/src/internal/res/layout/activity_notifications.xml \
  docs/plans/screenshots/notifications-after.png \
  docs/plans/screenshots/notifications-before.png
```

### 3.7 AppComponentsActivity

```bash
git checkout SOURCE -- \
  android-design-system/design-system-internal/src/main/java/com/duckduckgo/common/ui/internal/ui/AppComponentsActivity.kt \
  android-design-system/design-system-internal/src/main/res/layout/activity_app_components.xml \
  docs/plans/screenshots/app-components-after.png \
  docs/plans/screenshots/app-components-before.png
```

### 3.8 CrashANRsInternalSettingsActivity

```bash
git checkout SOURCE -- \
  anrs/anrs-internal/src/main/java/com/duckduckgo/app/anr/internal/feature/CrashANRsInternalSettingsActivity.kt \
  anrs/anrs-internal/src/main/res/layout/activity_crash_anr_internal_settings.xml \
  docs/plans/screenshots/crash-anrs-internal-settings-after.png \
  docs/plans/screenshots/crash-anrs-internal-settings-before.png
```

### 3.9 VpnInternalSettingsActivity

```bash
git checkout SOURCE -- \
  app-tracking-protection/vpn-internal/src/main/java/com/duckduckgo/vpn/internal/feature/VpnInternalSettingsActivity.kt \
  app-tracking-protection/vpn-internal/src/main/res/layout/activity_vpn_internal_settings.xml \
  docs/plans/screenshots/vpn-internal-settings-after.png \
  docs/plans/screenshots/vpn-internal-settings-before.png
```

### 3.10 ExceptionRulesDebugActivity

```bash
git checkout SOURCE -- \
  app-tracking-protection/vpn-internal/src/main/java/com/duckduckgo/vpn/internal/feature/rules/ExceptionRulesDebugActivity.kt \
  app-tracking-protection/vpn-internal/src/main/res/layout/activity_exception_rules_debug.xml \
  docs/plans/screenshots/exception-rules-debug-after.png \
  docs/plans/screenshots/exception-rules-debug-before.png
```

### 3.11 AutofillInternalSettingsActivity

```bash
git checkout SOURCE -- \
  autofill/autofill-internal/src/main/java/com/duckduckgo/autofill/internal/AutofillInternalSettingsActivity.kt \
  autofill/autofill-internal/src/main/res/layout/activity_autofill_internal_settings.xml \
  docs/plans/screenshots/autofill-internal-settings-after.png \
  docs/plans/screenshots/autofill-internal-settings-before.png
```

### 3.12 FeatureToggleInventoryActivity

> Note: Had 2 commits (original screenshots + later code after rebase). Squashed here.

```bash
git checkout SOURCE -- \
  feature-toggles/feature-toggles-internal/src/main/java/com/duckduckgo/examplefeature/internal/ui/FeatureToggleInventoryActivity.kt \
  feature-toggles/feature-toggles-internal/src/main/res/layout/activity_feature_toggle_inventory.xml \
  docs/plans/screenshots/feature-toggle-inventory-after.png \
  docs/plans/screenshots/feature-toggle-inventory-before.png
```

### 3.13 NetPInternalSettingsActivity

```bash
git checkout SOURCE -- \
  network-protection/network-protection-internal/src/main/java/com/duckduckgo/networkprotection/internal/feature/NetPInternalSettingsActivity.kt \
  network-protection/network-protection-internal/src/main/res/layout/activity_netp_internal_settings.xml \
  docs/plans/screenshots/netp-internal-settings-after.png \
  docs/plans/screenshots/netp-internal-settings-before.png
```

### 3.14 NetPSystemAppsExclusionListActivity

```bash
git checkout SOURCE -- \
  network-protection/network-protection-internal/src/main/java/com/duckduckgo/networkprotection/internal/feature/system_apps/NetPSystemAppsExclusionListActivity.kt \
  network-protection/network-protection-internal/src/main/res/layout/activity_netp_internal_system_apps_exclusion.xml \
  docs/plans/screenshots/netp-system-apps-after.png \
  docs/plans/screenshots/netp-system-apps-before.png
```

### 3.15 PirDevSettingsActivity

```bash
git checkout SOURCE -- \
  pir/pir-internal/src/main/java/com/duckduckgo/pir/internal/settings/PirDevSettingsActivity.kt \
  pir/pir-internal/src/main/res/layout/activity_pir_internal_settings.xml \
  docs/plans/screenshots/pir-dev-settings-after.png \
  docs/plans/screenshots/pir-dev-settings-before.png
```

### 3.16 PirDevWebViewActivity

```bash
git checkout SOURCE -- \
  pir/pir-internal/src/main/java/com/duckduckgo/pir/internal/settings/PirDevWebViewActivity.kt \
  pir/pir-internal/src/main/res/layout/activity_pir_internal_webview.xml \
  docs/plans/screenshots/pir-dev-webview-after.png \
  docs/plans/screenshots/pir-dev-webview-before.png
```

### 3.17 PirDevOptOutActivity

```bash
git checkout SOURCE -- \
  pir/pir-internal/src/main/java/com/duckduckgo/pir/internal/settings/PirDevOptOutActivity.kt \
  pir/pir-internal/src/main/res/layout/activity_pir_internal_optout.xml \
  docs/plans/screenshots/pir-dev-optout-after.png \
  docs/plans/screenshots/pir-dev-optout-before.png
```

### 3.18 PirDevScanActivity

```bash
git checkout SOURCE -- \
  pir/pir-internal/src/main/java/com/duckduckgo/pir/internal/settings/PirDevScanActivity.kt \
  pir/pir-internal/src/main/res/layout/activity_pir_internal_scan.xml \
  docs/plans/screenshots/pir-dev-scan-after.png \
  docs/plans/screenshots/pir-dev-scan-before.png
```

### 3.19 PirResultsActivity

```bash
git checkout SOURCE -- \
  pir/pir-internal/src/main/java/com/duckduckgo/pir/internal/settings/PirResultsActivity.kt \
  pir/pir-internal/src/main/res/layout/activity_pir_internal_results.xml \
  docs/plans/screenshots/pir-results-after.png \
  docs/plans/screenshots/pir-results-before.png
```

### 3.20 PrivacyConfigInternalSettingsActivity

```bash
git checkout SOURCE -- \
  privacy-config/privacy-config-internal/src/main/java/com/duckduckgo/privacy/config/internal/PrivacyConfigInternalSettingsActivity.kt \
  privacy-config/privacy-config-internal/src/main/res/layout/activity_privacy_config_internal_settings.xml \
  docs/plans/screenshots/privacy-config-settings-after.png \
  docs/plans/screenshots/privacy-config-settings-before.png
```

### 3.21 RMFInternalSettingsActivity

```bash
git checkout SOURCE -- \
  remote-messaging/remote-messaging-internal/src/main/java/com/duckduckgo/remote/messaging/internal/feature/RMFInternalSettingsActivity.kt \
  remote-messaging/remote-messaging-internal/src/main/res/layout/activity_rmf_internal_settings.xml \
  docs/plans/screenshots/rmf-settings-after.png \
  docs/plans/screenshots/rmf-settings-before.png
```

### 3.22 SavedSitesInternalSettingsActivity

```bash
git checkout SOURCE -- \
  saved-sites/saved-sites-internal/src/main/java/com/duckduckgo/savedsites/internal/SavedSitesInternalSettingsActivity.kt \
  saved-sites/saved-sites-internal/src/main/res/layout/activity_saved_sites_internal_settings.xml \
  docs/plans/screenshots/saved-sites-settings-after.png \
  docs/plans/screenshots/saved-sites-settings-before.png
```

### 3.23 SyncInternalSettingsActivity

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/ui/SyncInternalSettingsActivity.kt \
  sync/sync-impl/src/main/res/layout/activity_internal_sync_settings.xml \
  docs/plans/screenshots/sync-settings-after.png \
  docs/plans/screenshots/sync-settings-before.png
```

### 3.24 SubscriptionsInternalSettingsActivity

```bash
git checkout SOURCE -- \
  subscriptions/subscriptions-internal/src/main/java/com/duckduckgo/subscriptions/internal/SubscriptionsInternalSettingsActivity.kt \
  subscriptions/subscriptions-internal/src/main/res/layout/activity_subs_internal_settings.xml \
  docs/plans/screenshots/subscriptions-settings-after.png \
  docs/plans/screenshots/subscriptions-settings-before.png
```

### 3.25 AttributedMetricsDevSettingsActivity

```bash
git checkout SOURCE -- \
  attributed-metrics/attributed-metrics-internal/src/main/java/com/duckduckgo/app/attributed/metrics/internal/ui/AttributedMetricsDevSettingsActivity.kt \
  attributed-metrics/attributed-metrics-internal/src/main/res/layout/activity_attributed_metrics_dev_settings.xml \
  docs/plans/screenshots/attributed-metrics-dev-settings-after.png \
  docs/plans/screenshots/attributed-metrics-dev-settings-before.png
```

---

## Task 4: Group 3 — List/Management Screens with Toolbars

### 4.1 SettingsActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/settings/SettingsActivity.kt \
  app/src/main/res/layout/activity_settings_new.xml \
  app/src/main/res/layout/content_settings.xml \
  docs/plans/screenshots/settings-after.png \
  docs/plans/screenshots/settings-before.png
```

### 4.2 BookmarksActivity

> Note: Had 2 commits. Final state includes layout change.

```bash
git checkout SOURCE -- \
  saved-sites/saved-sites-impl/src/main/java/com/duckduckgo/savedsites/impl/bookmarks/BookmarksActivity.kt \
  saved-sites/saved-sites-impl/src/main/res/layout/activity_bookmarks.xml \
  docs/plans/screenshots/bookmarks-after.png \
  docs/plans/screenshots/bookmarks-before.png
```

### 4.3 BookmarkFoldersActivity

```bash
git checkout SOURCE -- \
  saved-sites/saved-sites-impl/src/main/java/com/duckduckgo/savedsites/impl/folders/BookmarkFoldersActivity.kt \
  docs/plans/screenshots/bookmark-folders-after.png \
  docs/plans/screenshots/bookmark-folders-before.png
```

### 4.4 DownloadsActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/downloads/DownloadsActivity.kt \
  app/src/main/res/layout/activity_downloads.xml \
  docs/plans/screenshots/downloads-after.png \
  docs/plans/screenshots/downloads-before.png
```

### 4.5 FireproofWebsitesActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/fire/fireproofwebsite/ui/FireproofWebsitesActivity.kt \
  app/src/main/res/layout/activity_fireproof_websites.xml \
  docs/plans/screenshots/fireproof-websites-after.png \
  docs/plans/screenshots/fireproof-websites-before.png
```

### 4.6 AllowListActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/privacy/ui/AllowListActivity.kt \
  app/src/main/res/layout/activity_allowlist.xml \
  docs/plans/screenshots/allowlist-after.png \
  docs/plans/screenshots/allowlist-before.png
```

### 4.7 AutofillManagementActivity

```bash
git checkout SOURCE -- \
  autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/ui/credential/management/AutofillManagementActivity.kt \
  autofill/autofill-impl/src/main/res/layout/activity_autofill_settings_legacy.xml \
  docs/plans/screenshots/autofill-management-after.png \
  docs/plans/screenshots/autofill-management-before.png
```

### 4.8 ImportPasswordsActivity

```bash
git checkout SOURCE -- \
  autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/ui/credential/management/importpassword/ImportPasswordsActivity.kt \
  autofill/autofill-impl/src/main/res/layout/activity_import_passwords.xml \
  docs/plans/screenshots/import-passwords-after.png \
  docs/plans/screenshots/import-passwords-before.png
```

### 4.9 ImportPasswordsGetDesktopAppActivity

```bash
git checkout SOURCE -- \
  autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/ui/credential/management/importpassword/desktopapp/ImportPasswordsGetDesktopAppActivity.kt \
  autofill/autofill-impl/src/main/res/layout/activity_get_desktop_app.xml \
  docs/plans/screenshots/import-passwords-get-desktop-app-after.png \
  docs/plans/screenshots/import-passwords-get-desktop-app-before.png
```

### 4.10 TrackingProtectionExclusionListActivity

```bash
git checkout SOURCE -- \
  app-tracking-protection/vpn-impl/src/main/java/com/duckduckgo/mobile/android/vpn/apps/ui/TrackingProtectionExclusionListActivity.kt \
  docs/plans/screenshots/tracking-protection-exclusion-list-after.png \
  docs/plans/screenshots/tracking-protection-exclusion-list-before.png
```

### 4.11 ManageRecentAppsProtectionActivity

```bash
git checkout SOURCE -- \
  app-tracking-protection/vpn-impl/src/main/java/com/duckduckgo/mobile/android/vpn/apps/ui/ManageRecentAppsProtectionActivity.kt \
  app-tracking-protection/vpn-impl/src/main/res/layout/activity_manage_recent_apps_protection.xml \
  docs/plans/screenshots/manage-recent-apps-protection-after.png \
  docs/plans/screenshots/manage-recent-apps-protection-before.png
```

### 4.12 NetpAppExclusionListActivity

```bash
git checkout SOURCE -- \
  network-protection/network-protection-impl/src/main/java/com/duckduckgo/networkprotection/impl/exclusion/ui/NetpAppExclusionListActivity.kt \
  docs/plans/screenshots/netp-app-exclusion-list-after.png \
  docs/plans/screenshots/netp-app-exclusion-list-before.png
```

### 4.13 TabSwitcherActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/tabs/ui/TabSwitcherActivity.kt \
  android-design-system/design-system/src/main/res/layout/include_tab_switcher_toolbar_bottom.xml \
  android-design-system/design-system/src/main/res/layout/include_tab_switcher_toolbar_top.xml \
  docs/plans/screenshots/tab-switcher-after.png \
  docs/plans/screenshots/tab-switcher-before.png
```

---

## Task 5: Group 4 — Feature Screens with Custom Layouts

### 5.1 OnboardingActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/onboarding/ui/OnboardingActivity.kt \
  docs/plans/screenshots/onboarding-after.png \
  docs/plans/screenshots/onboarding-before.png
```

### 5.2 FeedbackActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/feedback/ui/common/FeedbackActivity.kt \
  docs/plans/screenshots/feedback-after.png \
  docs/plans/screenshots/feedback-before.png
```

### 5.3 SurveyActivity — SKIP

> Net zero changes. Was migrated, fixed, then fully reverted. No commit needed.

### 5.4 SubscriptionFeedbackActivity

```bash
git checkout SOURCE -- \
  subscriptions/subscriptions-impl/src/main/java/com/duckduckgo/subscriptions/impl/feedback/SubscriptionFeedbackActivity.kt \
  docs/plans/screenshots/subscription-feedback-after.png \
  docs/plans/screenshots/subscription-feedback-before.png
```

### 5.5 AddWidgetInstructionsActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/widget/ui/AddWidgetInstructionsActivity.kt \
  docs/plans/screenshots/add-widget-instructions-after.png \
  docs/plans/screenshots/add-widget-instructions-before.png
```

### 5.6 VpnOnboardingActivity

```bash
git checkout SOURCE -- \
  app-tracking-protection/vpn-impl/src/main/java/com/duckduckgo/mobile/android/vpn/ui/onboarding/VpnOnboardingActivity.kt \
  docs/plans/screenshots/vpn-onboarding-after.png \
  docs/plans/screenshots/vpn-onboarding-before.png
```

### 5.7 DeviceShieldTrackerActivity

```bash
git checkout SOURCE -- \
  app-tracking-protection/vpn-impl/src/main/java/com/duckduckgo/mobile/android/vpn/ui/tracker_activity/DeviceShieldTrackerActivity.kt \
  app-tracking-protection/vpn-impl/src/main/res/layout/include_trackers_toolbar.xml \
  docs/plans/screenshots/device-shield-tracker-after.png \
  docs/plans/screenshots/device-shield-tracker-before.png
```

### 5.8 DeviceShieldMostRecentActivity

```bash
git checkout SOURCE -- \
  app-tracking-protection/vpn-impl/src/main/java/com/duckduckgo/mobile/android/vpn/ui/tracker_activity/DeviceShieldMostRecentActivity.kt \
  docs/plans/screenshots/device-shield-most-recent-after.png \
  docs/plans/screenshots/device-shield-most-recent-before.png
```

### 5.9 AppTPCompanyTrackersActivity

```bash
git checkout SOURCE -- \
  app-tracking-protection/vpn-impl/src/main/java/com/duckduckgo/mobile/android/vpn/ui/tracker_activity/AppTPCompanyTrackersActivity.kt \
  app-tracking-protection/vpn-impl/src/main/res/layout/include_company_trackers_toolbar.xml \
  docs/plans/screenshots/apptp-company-trackers-after.png \
  docs/plans/screenshots/apptp-company-trackers-before.png
```

### 5.10 ReportBreakageAppListActivity

```bash
git checkout SOURCE -- \
  app-tracking-protection/vpn-impl/src/main/java/com/duckduckgo/mobile/android/vpn/breakage/ReportBreakageAppListActivity.kt \
  docs/plans/screenshots/report-breakage-app-list-after.png \
  docs/plans/screenshots/report-breakage-app-list-before.png
```

### 5.11 ReportBreakageCategorySingleChoiceActivity

```bash
git checkout SOURCE -- \
  app-tracking-protection/vpn-impl/src/main/java/com/duckduckgo/mobile/android/vpn/breakage/ReportBreakageCategorySingleChoiceActivity.kt \
  docs/plans/screenshots/report-breakage-category-single-choice-after.png \
  docs/plans/screenshots/report-breakage-category-single-choice-before.png
```

### 5.12 NetworkProtectionManagementActivity

```bash
git checkout SOURCE -- \
  network-protection/network-protection-impl/src/main/java/com/duckduckgo/networkprotection/impl/management/NetworkProtectionManagementActivity.kt \
  docs/plans/screenshots/network-protection-management-after.png \
  docs/plans/screenshots/network-protection-management-before.png
```

### 5.13 SyncActivity

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/ui/SyncActivity.kt \
  docs/plans/screenshots/sync-activity-after.png \
  docs/plans/screenshots/sync-activity-before.png
```

### 5.14 SyncConnectActivity

> Note: Had 2 commits (original layout + later Kotlin + new layout). Squashed here.

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/ui/SyncConnectActivity.kt \
  sync/sync-impl/src/main/res/layout/activity_connect_sync.xml \
  sync/sync-impl/src/main/res/layout/activity_connect_sync_new.xml \
  docs/plans/screenshots/sync-connect-activity-after.png \
  docs/plans/screenshots/sync-connect-activity-before.png
```

### 5.15 SyncLoginActivity

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/ui/SyncLoginActivity.kt \
  docs/plans/screenshots/sync-login-activity-after.png \
  docs/plans/screenshots/sync-login-activity-before.png
```

### 5.16 SyncWithAnotherDeviceActivity

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/ui/SyncWithAnotherDeviceActivity.kt \
  docs/plans/screenshots/sync-with-another-device-activity-after.png \
  docs/plans/screenshots/sync-with-another-device-activity-before.png
```

### 5.17 SetupAccountActivity

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/ui/setup/SetupAccountActivity.kt \
  docs/plans/screenshots/setup-account-activity-after.png \
  docs/plans/screenshots/setup-account-activity-before.png
```

### 5.18 EnterCodeActivity

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/ui/EnterCodeActivity.kt \
  docs/plans/screenshots/enter-code-activity-after.png \
  docs/plans/screenshots/enter-code-activity-before.png
```

### 5.19 DeviceUnsupportedActivity

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/ui/DeviceUnsupportedActivity.kt \
  docs/plans/screenshots/device-unsupported-after.png \
  docs/plans/screenshots/device-unsupported-before.png
```

### 5.20 SyncGetOnOtherPlatformsActivity

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/promotion/SyncGetOnOtherPlatformsActivity.kt \
  docs/plans/screenshots/sync-get-on-other-platforms-after.png \
  docs/plans/screenshots/sync-get-on-other-platforms-before.png
```

### 5.21 ChangePlanActivity

```bash
git checkout SOURCE -- \
  subscriptions/subscriptions-impl/src/main/java/com/duckduckgo/subscriptions/impl/ui/ChangePlanActivity.kt \
  subscriptions/subscriptions-impl/src/main/res/layout/activity_change_plan.xml \
  docs/plans/screenshots/change-plan-after.png \
  docs/plans/screenshots/change-plan-before.png
```

### 5.22 RestoreSubscriptionActivity

```bash
git checkout SOURCE -- \
  subscriptions/subscriptions-impl/src/main/java/com/duckduckgo/subscriptions/impl/ui/RestoreSubscriptionActivity.kt \
  subscriptions/subscriptions-impl/src/main/res/layout/activity_restore_subscription.xml \
  docs/plans/screenshots/restore-subscription-after.png \
  docs/plans/screenshots/restore-subscription-before.png
```

### 5.23 PirActivity

> Note: Had migrate + fix commit. Squashed here (final state).

```bash
git checkout SOURCE -- \
  subscriptions/subscriptions-impl/src/main/java/com/duckduckgo/subscriptions/impl/pir/PirActivity.kt \
  subscriptions/subscriptions-impl/src/main/res/layout/privacy_pro_toolbar.xml \
  docs/plans/screenshots/pir-after.png \
  docs/plans/screenshots/pir-before.png
```

### 5.24 MacOsActivity

```bash
git checkout SOURCE -- \
  macos/macos-impl/src/main/java/com/duckduckgo/macos/impl/MacOsActivity.kt \
  macos/macos-impl/src/main/res/layout/activity_macos.xml \
  docs/plans/screenshots/macos-after.png \
  docs/plans/screenshots/macos-before.png
```

### 5.25 WindowsActivity

```bash
git checkout SOURCE -- \
  windows/windows-impl/src/main/java/com/duckduckgo/windows/impl/ui/WindowsActivity.kt \
  windows/windows-impl/src/main/res/layout/activity_windows.xml \
  docs/plans/screenshots/windows-after.png \
  docs/plans/screenshots/windows-before.png
```

### 5.26 EmailProtectionUnsupportedActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/email/ui/EmailProtectionUnsupportedActivity.kt \
  docs/plans/screenshots/email-protection-unsupported-after.png \
  docs/plans/screenshots/email-protection-unsupported-before.png
```

### 5.27 EmailProtectionInContextSignupActivity

```bash
git checkout SOURCE -- \
  autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/email/incontext/EmailProtectionInContextSignupActivity.kt \
  docs/plans/screenshots/email-protection-in-context-signup-after.png \
  docs/plans/screenshots/email-protection-in-context-signup-before.png
```

### 5.28 InputScreenActivity (includes InputScreenFragment)

```bash
git checkout SOURCE -- \
  duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/inputscreen/ui/InputScreenActivity.kt \
  duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/inputscreen/ui/InputScreenFragment.kt \
  docs/plans/screenshots/input-screen-activity-after.png \
  docs/plans/screenshots/input-screen-activity-before.png
```

### 5.29 ModalSurfaceActivity

```bash
git checkout SOURCE -- \
  remote-messaging/remote-messaging-impl/src/main/java/com/duckduckgo/remote/messaging/impl/modal/ModalSurfaceActivity.kt \
  remote-messaging/remote-messaging-impl/src/main/java/com/duckduckgo/remote/messaging/impl/modal/cardslist/CardsListRemoteMessageView.kt \
  docs/plans/screenshots/modal-surface-activity-after.png \
  docs/plans/screenshots/modal-surface-activity-before.png
```

### 5.30 SerpEasterEggLogoActivity

```bash
git checkout SOURCE -- \
  serp-logos/serp-logos-impl/src/main/kotlin/com/duckduckgo/serp/logos/impl/ui/SerpEasterEggLogoActivity.kt \
  docs/plans/screenshots/serp-easter-egg-logo-activity-after.png \
  docs/plans/screenshots/serp-easter-egg-logo-activity-before.png
```

---

## Task 6: Group 5 — WebView Screens

### 6.1 WebViewActivity

> Note: Had 2 commits (migrate + IME fix). Squashed here.

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/browser/webview/WebViewActivity.kt \
  docs/plans/screenshots/web-view-activity-after.png \
  docs/plans/screenshots/web-view-activity-before.png \
  docs/plans/screenshots/webview-activity-ime-after.png \
  docs/plans/screenshots/webview-activity-ime-before.png
```

### 6.2 SettingsWebViewActivity

```bash
git checkout SOURCE -- \
  settings/settings-impl/src/main/java/com/duckduckgo/settings/impl/SettingsWebViewActivity.kt \
  docs/plans/screenshots/settings-webview-after.png \
  docs/plans/screenshots/settings-webview-before.png
```

### 6.3 PrivacyDashboardHybridActivity

```bash
git checkout SOURCE -- \
  privacy-dashboard/privacy-dashboard-impl/src/main/java/com/duckduckgo/privacy/dashboard/impl/ui/PrivacyDashboardHybridActivity.kt \
  docs/plans/screenshots/privacy-dashboard-after.png \
  docs/plans/screenshots/privacy-dashboard-before.png
```

### 6.4 PirDashboardWebViewActivity

```bash
git checkout SOURCE -- \
  pir/pir-impl/src/main/java/com/duckduckgo/pir/impl/dashboard/PirDashboardWebViewActivity.kt \
  docs/plans/screenshots/pir-dashboard-webview-after.png \
  docs/plans/screenshots/pir-dashboard-webview-before.png
```

### 6.5 SubscriptionsWebViewActivity

```bash
git checkout SOURCE -- \
  subscriptions/subscriptions-impl/src/main/java/com/duckduckgo/subscriptions/impl/ui/SubscriptionsWebViewActivity.kt \
  docs/plans/screenshots/subscriptions-webview-after.png \
  docs/plans/screenshots/subscriptions-webview-before.png
```

### 6.6 ImportGooglePasswordsWebFlowActivity

```bash
git checkout SOURCE -- \
  autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/importing/gpm/webflow/ImportGooglePasswordsWebFlowActivity.kt \
  autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/importing/gpm/webflow/ImportGooglePasswordsWebFlowFragment.kt \
  docs/plans/screenshots/import-google-passwords-webflow-after.png \
  docs/plans/screenshots/import-google-passwords-webflow-before.png
```

### 6.7 ImportGoogleBookmarksWebFlowActivity

```bash
git checkout SOURCE -- \
  autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/importing/takeout/webflow/ImportGoogleBookmarksWebFlowActivity.kt \
  autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/importing/takeout/webflow/ImportGoogleBookmarksWebFlowFragment.kt \
  docs/plans/screenshots/import-google-bookmarks-webflow-after.png \
  docs/plans/screenshots/import-google-bookmarks-webflow-before.png
```

---

## Task 7: Group 6 — Autofill Service Activities

### 7.1 AutofillProviderChooseActivity

```bash
git checkout SOURCE -- \
  autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/service/AutofillProviderChooseActivity.kt \
  autofill/autofill-impl/src/main/res/layout/activity_custom_autofill_provider.xml \
  autofill/autofill-impl/src/main/res/layout/fragment_autofill_provider_list.xml
```

### 7.2 AutofillProviderFillSuggestionActivity

```bash
git checkout SOURCE -- \
  autofill/autofill-impl/src/main/java/com/duckduckgo/autofill/impl/service/AutofillProviderFillSuggestionActivity.kt
```

---

## Task 8: Group 7 — Transparent/Launcher Activities

### 8.1 LaunchBridgeActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/launch/LaunchBridgeActivity.kt \
  docs/plans/screenshots/launch-bridge-after.png \
  docs/plans/screenshots/launch-bridge-before.png
```

### 8.2 IntentDispatcherActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/dispatchers/IntentDispatcherActivity.kt \
  docs/plans/screenshots/intent-dispatcher-after.png \
  docs/plans/screenshots/intent-dispatcher-before.png
```

### 8.3 FireActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/fire/FireActivity.kt \
  docs/plans/screenshots/fire-after.png \
  docs/plans/screenshots/fire-before.png
```

### 8.4 SelectedTextSearchActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/SelectedTextSearchActivity.kt \
  docs/plans/screenshots/selected-text-search-after.png \
  docs/plans/screenshots/selected-text-search-before.png
```

### 8.5 DuckAiPinShortcutActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/widget/DuckAiPinShortcutActivity.kt \
  docs/plans/screenshots/duckai-pin-shortcut-after.png \
  docs/plans/screenshots/duckai-pin-shortcut-before.png
```

### 8.6 CustomTabActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/browser/customtabs/CustomTabActivity.kt \
  docs/plans/screenshots/custom-tab-after.png \
  docs/plans/screenshots/custom-tab-before.png
```

---

## Task 9: Group 8 — Already Edge-to-Edge (Verify Only)

> These had no code changes. Only verification screenshot if applicable.

### 9.1 DaxPromptBrowserComparisonActivity (migrated)

> Originally classified as "screenshot only" but found to use manual flags (`FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS` + `WindowCompat.setDecorFitsSystemWindows`) with hardcoded `navigationBarColor = Color.BLACK` instead of the standard `enableEdgeToEdge()` pattern. Migrated to match all other activities.

```bash
git checkout SOURCE -- \
  dax-prompts/dax-prompts-impl/src/main/java/com/duckduckgo/daxprompts/impl/ui/DaxPromptBrowserComparisonActivity.kt \
  docs/plans/screenshots/dax-prompt-browser-comparison-before.png \
  docs/plans/screenshots/dax-prompt-browser-comparison-after.png
git add -A
git commit -m "$(cat <<'EOF'
edge-to-edge: Migrate DaxPromptBrowserComparisonActivity

Had partial edge-to-edge via manual flags but used hardcoded
navigationBarColor = Color.BLACK. Migrated to standard pattern
using enableEdgeToEdge() + ViewCompat.setOnApplyWindowInsetsListener
with updatePadding(top, bottom) on the ConstraintLayout root.

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

> **VoiceSearchActivity** — Verified by code inspection only. No code changes, no screenshots. Skip commit.

---

## Task 10: Group 9 — Complex/High-Risk

### 10.1 BrowserActivity

> Note: Had 2 migration commits. Squashed here with all files from both.

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/browser/BrowserActivity.kt \
  app/src/main/java/com/duckduckgo/app/browser/navigation/bar/view/BrowserNavigationBarView.kt \
  app/src/main/java/com/duckduckgo/app/browser/omnibar/Omnibar.kt \
  app/src/main/java/com/duckduckgo/app/browser/omnibar/OmnibarLayout.kt \
  app/src/main/java/com/duckduckgo/app/browser/webview/BrowserContainerLayoutBehavior.kt \
  app/src/main/res/layout/view_browser_navigation_bar.xml \
  app/src/main/res/layout/view_browser_navigation_bar_mockup.xml \
  app/src/main/res/layout/view_omnibar.xml \
  docs/plans/screenshots/browser-activity-after.png \
  docs/plans/screenshots/browser-activity-before.png \
  docs/plans/screenshots/browser-activity-bottom-after-keyboard.png \
  docs/plans/screenshots/browser-activity-bottom-after.png \
  docs/plans/screenshots/browser-activity-bottom-before-keyboard.png \
  docs/plans/screenshots/browser-activity-bottom-before.png \
  docs/plans/screenshots/browser-activity-split-after-keyboard.png \
  docs/plans/screenshots/browser-activity-split-after.png \
  docs/plans/screenshots/browser-activity-split-before-keyboard.png \
  docs/plans/screenshots/browser-activity-split-before.png \
  docs/plans/screenshots/browser-bottom-logo-fix-after.png \
  docs/plans/screenshots/browser-top-logo-fix-comparison.png
```

### 10.2 SystemSearchActivity

```bash
git checkout SOURCE -- \
  app/src/main/java/com/duckduckgo/app/systemsearch/SystemSearchActivity.kt \
  docs/plans/screenshots/system-search-after.png \
  docs/plans/screenshots/system-search-before.png
```

---

## Task 11: Group 10 — Fragments (Pattern D)

### 11.1 SyncDeviceConnectedFragment

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/ui/setup/SyncDeviceConnectedFragment.kt \
  docs/plans/screenshots/sync-device-connected-fragment-after.png \
  docs/plans/screenshots/sync-device-connected-fragment-before.png
```

### 11.2 SyncSetupIntroFragment

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/ui/setup/SyncSetupIntroFragment.kt \
  docs/plans/screenshots/sync-setup-intro-fragment-after.png \
  docs/plans/screenshots/sync-setup-intro-fragment-before.png
```

### 11.3 SaveRecoveryCodeFragment

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/ui/setup/SaveRecoveryCodeFragment.kt \
  docs/plans/screenshots/save-recovery-code-fragment-after.png \
  docs/plans/screenshots/save-recovery-code-fragment-before.png
```

### 11.4 SyncCreateAccountFragment

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/ui/setup/SyncCreateAccountFragment.kt \
  sync/sync-impl/src/main/res/layout/fragment_create_account.xml \
  docs/plans/screenshots/sync-create-account-fragment-after.png \
  docs/plans/screenshots/sync-create-account-fragment-before.png
```

### 11.5 SyncSetupDeepLinkFragment

```bash
git checkout SOURCE -- \
  sync/sync-impl/src/main/java/com/duckduckgo/sync/impl/ui/setup/SyncSetupDeepLinkFragment.kt
```

### 11.6 DuckChatWebViewFragment

```bash
git checkout SOURCE -- \
  duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/DuckChatWebViewFragment.kt
```

---

## Task 12: Final — Documentation

Single commit with all docs changes.

```bash
git checkout SOURCE -- \
  docs/plans/2026-01-16-edge-to-edge-migration-design.md \
  docs/plans/2026-01-16-edge-to-edge-activity-checklist.md

git add -A
git commit -m "$(cat <<'EOF'
docs: Add edge-to-edge migration design and activity checklist

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Verification

**Step 1: Compare file trees**

```bash
# Should show NO differences for code files (only docs/plan changes may differ)
diff <(git diff --name-only main..SOURCE -- '*.kt' '*.xml' '*.java' | grep -v gradle-daemon | sort) \
     <(git diff --name-only main..HEAD -- '*.kt' '*.xml' '*.java' | sort)
```

**Step 2: Count commits**

```bash
git log --oneline main..HEAD | wc -l
# Expected: ~107 commits (1 toolbar + ~100 activities + ~5 fragments + 1 verify + 1 docs)
```

**Step 3: Verify commit order matches checklist**

```bash
git log --oneline --reverse main..HEAD
```

**Step 4: Verify no accidental files**

```bash
# Should NOT exist
git show HEAD:gradle/gradle-daemon-jvm.properties 2>&1 | head -1
# Expected: fatal: path ... does not exist
```

**Step 5: Build**

```bash
./gradlew :app:assembleInternalDebug
```

**Step 6: Replace original branch**

```bash
# Only after all verification passes
git branch -m feature/mike/edgetoedge feature/mike/edgetoedge-old
git branch -m feature/mike/edgetoedge-clean feature/mike/edgetoedge
```

---

## Automation Script

For efficiency, this entire plan can be executed as a shell script. Each task follows the same pattern:

```bash
checkout_and_commit() {
  local msg="$1"
  shift
  git checkout SOURCE -- "$@"
  git add -A
  git commit -m "$msg

Co-Authored-By: Claude <noreply@anthropic.com>"
}
```

The executing session should generate the full script from this plan and run it in one pass, then verify.
