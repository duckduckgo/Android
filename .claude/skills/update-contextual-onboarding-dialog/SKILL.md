---
name: update-contextual-onboarding-dialog
description: >
  Migrate one `OnboardingDaxDialogCta` subclass (contextual in-browser onboarding dialog)
  to the brand-design layout. Populates the Stage-1-created stub file, replaces sentinel
  lines in CtaViewModel and BrowserTabViewModel, adds a per-dialog test file, and commits
  locally on its pre-created Graphite branch. Does NOT push or submit — halts for user
  verification of the full stack. Runs once per CTA, parallel across up to 7 agents.
---

# Update Contextual Onboarding Dialog

## Context

Stage 2 of the contextual onboarding rebrand. Stage 1 (`bootstrap-contextual-onboarding-rebrand`) has already created the abstract base class, the new card chrome layout, two content-include templates, 7 stub CTA files, and sentinels in `CtaViewModel`/`BrowserTabViewModel`. This skill populates one CTA's stub and replaces that CTA's sentinels.

Up to 7 agents run this skill in parallel, each in its own worktree on a pre-created branch.

## Preconditions (fail fast if any are missing)

- The orchestrator has provided a worktree checked out to one of: `feature/mike/onboarding-brand-design-updates/contextual-<slug>` where `<slug>` is one of: `serp`, `trackers-blocked`, `main-network`, `no-trackers`, `fire-button`, `site-suggestions`, `end`.
- Branch was created via `gt create` with its Graphite parent chain already in place.
- Stack root and Stage 1 branch exist **locally** (stack root may also be upstream from prior work, but Stage 1 is local-only per the Push / Submit Policy — do NOT attempt to verify Stage 1 exists on the remote). `gt log short` should show the expected parent chain without any upstream-write operation.
- `BrandDesignContextualDaxDialogCta` abstract class exists (Stage 1 S2).
- `include_onboarding_in_context_dax_dialog_brand_design_update.xml` exists (Stage 1 S3).
- `app/src/main/java/com/duckduckgo/app/cta/ui/Dax<Name>BrandDesignUpdateContextualCta.kt` exists as a stub (Stage 1 S8a).
- The `CtaViewModel` sentinel(s) for the target CTA exist (Stage 1 S8c). Every CTA has at least one.
- The `BrowserTabViewModel` sentinel(s) for the target CTA exist **if any are expected** per this table:

| CTA | `BrowserTabViewModel` sentinels expected |
|---|---|
| `DaxSerpCta` | 1 (touchpoint at `cta-when-branch`) |
| `DaxTrackersBlockedCta` | 3 (touchpoints at `privacy-shield-highlight-check`, `trackers-blocked-typing-finished`, `cta-when-branch`) |
| `DaxMainNetworkCta` | 1 (touchpoint at `cta-when-branch`) |
| `DaxNoTrackersCta` | 1 (touchpoint at `cta-when-branch`) |
| `DaxFireButtonCta` | 2 (touchpoints at `launch-fire-dialog`, `launch-fire-dialog-check`) |
| `DaxSiteSuggestionsCta` | 1 (touchpoint at `cta-when-branch`) |
| `DaxEndCta` | 0 — skip P5 entirely |

If any precondition is missing, STOP and report: "Stage 1 has not been run or is incomplete. Run it first." Do NOT bootstrap — that is Stage 1's job.

## Inputs

1. Old CTA class name (e.g. `DaxSerpCta`).
2. Figma URL for the new design (optional; layout/structure only — copy is NOT authoritative).

## Parallel-edit contract

This skill is strictly file-local. Allowed files only:

| File | Kind | Edit |
|---|---|---|
| `app/src/main/java/com/duckduckgo/app/cta/ui/Dax<Name>BrandDesignUpdateContextualCta.kt` | Stage 1 stub | populate `activeIncludeId` + `configureContentViews` + click overrides |
| `app/src/test/java/com/duckduckgo/app/cta/ui/Dax<Name>BrandDesignUpdateContextualCtaTest.kt` | new file | create |
| `app/src/main/java/com/duckduckgo/app/cta/ui/CtaViewModel.kt` | shared | replace own SENTINEL line(s) only |
| `app/src/main/java/com/duckduckgo/app/browser/BrowserTabViewModel.kt` | shared | replace own SENTINEL line(s) only (skip for `DaxEndCta`) |

**Not touched:** `BrowserTabFragment.kt`, shared test files (`CtaViewModelTest`, `BrowserTabViewModelTest`), `BrandDesignContextualDaxDialogCta.kt`, layouts, content includes, string resources.

If Figma shows a content variant neither `primaryCtaContent` nor `optionsContent` covers, STOP and report to the orchestrator.

## Required Rules

The full 22-rule set applies. Stage-2-specific rules are:

### Architecture

1. **Base class owns the render pipeline.** Do NOT override `showOnboardingCta`, `hideOnboardingCta`, `snapToFinished`, or any lifecycle method. Only `activeIncludeId` + `configureContentViews` + per-button click overrides.
2. **Stub file is per-CTA, top-level `data class` extending `BrandDesignContextualDaxDialogCta`.**
3. **Override click handlers only for buttons this CTA uses.** Missing override = silently broken button (PR 8293 bug).

### Copy

4. **Reuse existing `strings.xml` IDs per the matrix below. Never copy text from Figma.** If Figma shows a title for a CTA the matrix lists as "none," stop and report.
5. **Never add user-facing strings to `donottranslate.xml`.**

### Content-include binding

9. **Set title/description/option labels before the fade-in begins.**
11. **Do not introduce duplicate IDs** across content includes.
12. **Option buttons use `OnboardingSelectionButton`,** not `DaxButtonSecondary`.
13. **`DaxButtonPrimary` onboarding theme overlay** — already present in Stage 1 templates; don't remove.

### ViewModel

16. **Single `command.value =` per state change** when replacing `BrowserTabViewModel` sentinels.
17. **Every dismiss path emits `Command.HideOnboardingDaxDialog`.**

### Code hygiene

21. **Alias outside-module R imports:** `import com.duckduckgo.mobile.android.R as DesignSystemR`.
22. **No testing shortcuts.**

## Per-CTA Copy Matrix (settled, verified against `Cta.kt`)

Reuse these existing `strings.xml` IDs. Where the legacy CTA has no title, render NO title.

| CTA | Title | Description | Primary button | Options |
|---|---|---|---|---|
| `DaxSerpCta` | *(none)* | `onboardingSerpDaxDialogDescription` | `onboardingSerpDaxDialogButton` | — |
| `DaxTrackersBlockedCta` | *(none)* | dynamic via `getTrackersDescription()` | `onboardingTrackersBlockedDaxDialogButton` | — |
| `DaxMainNetworkCta` | *(none)* | dynamic via `getTrackersDescription()` | `daxDialogGotIt` | — |
| `DaxNoTrackersCta` | *(none)* | `daxNonSerpCtaText` | `daxDialogGotIt` | — |
| `DaxFireButtonCta` | *(none)* | `onboardingFireButtonDaxDialogDescription` | `onboardingFireButtonDaxDialogOkButton` | — |
| `DaxSiteSuggestionsCta` | `onboardingSitesSuggestionsDaxDialogTitle` (wired via legacy suggestions layout, not constructor — preserve in brand-design variant) | `onboardingSitesDaxDialogDescription` | — | From `onboardingStore.getSitesOptions()` — locale-specific data source, NOT string resources |
| `DaxEndCta` | *(none)* | `onboardingEndDaxDialogDescription` | `onboardingEndDaxDialogButton` | — |

Content variant (authoritative — do NOT create new variants):

| CTA | Variant | `activeIncludeId` |
|---|---|---|
| `DaxSerpCta` | `primaryCtaContent` | `R.id.primaryCtaContent` |
| `DaxTrackersBlockedCta` | `primaryCtaContent` | `R.id.primaryCtaContent` |
| `DaxMainNetworkCta` | `primaryCtaContent` | `R.id.primaryCtaContent` |
| `DaxNoTrackersCta` | `primaryCtaContent` | `R.id.primaryCtaContent` |
| `DaxFireButtonCta` | `primaryCtaContent` | `R.id.primaryCtaContent` |
| `DaxSiteSuggestionsCta` | `optionsContent` | `R.id.optionsContent` |
| `DaxEndCta` | `primaryCtaContent` | `R.id.primaryCtaContent` |

## Steps

### P1. Read the target CTA class

Open `Cta.kt` and read the legacy CTA class. Record: constructor params, string resource IDs, pixel configuration (`shownPixel`, `okPixel`, `cancelPixel`, `pixelShownParameters()`, `pixelOkParameters()`, `pixelCancelParameters()`), `markAsReadOnShow` flag.

### P2. Inspect the design and confirm the content variant

If your assigned Figma URL is `NONE`, skip the fetch and rely on the copy matrix + content-variant table. Otherwise:

- Use `mcp__claude_ai_Figma__get_screenshot` (or equivalent) to fetch the frame.
- Confirm the visual structure matches your CTA's row in the content-variant table (`primaryCtaContent` or `optionsContent`). If Figma shows anything the matrix doesn't cover (e.g., a title where the matrix says *(none)*, a secondary button, a header image, a structure that is neither primary-CTA nor options) — STOP and report to the orchestrator. Do NOT improvise.
- Cross-check copy against the Per-CTA Copy Matrix. Any Figma copy that differs from the matrix is considered wrong per rule 4; the matrix wins.

### P3. Populate the stub

Open `app/src/main/java/com/duckduckgo/app/cta/ui/Dax<Name>BrandDesignUpdateContextualCta.kt` (stub already exists from Stage 1 S8a — do NOT create).

- **Carry forward the legacy CTA's real constructor values from P1.** Stage 1's stub used placeholder values (e.g., a best-guess `ctaId`, copied pixel parameters from the legacy class's constructor); confirm each is correct. Specifically:
  - `ctaId`: use the exact `CtaId.*` constant the legacy class passes.
  - `shownPixel`, `okPixel`, `cancelPixel`, `closePixel` and their parameter maps (`pixelShownParameters`, `pixelOkParameters`, `pixelCancelParameters`): must match the legacy CTA exactly so telemetry is preserved. Contextual CTAs typically leave `cancelPixel = null` (no separate "cancel" pixel) and use `closePixel` for X-dismiss via `CtaViewModel.onUserDismissedCta(viaCloseBtn = true)`. Preserve the legacy null/non-null pattern exactly — do NOT invent a `cancelPixel` where the legacy class has none.
  - `ctaPixelParam` (the CTA's unique pixel-history token, e.g. `"s"` for SERP, `"t"` for trackers): carry forward unchanged. It's used in `pixelShownParameters` to build the journey-history pixel string.
  - `markAsReadOnShow`: carry forward as-is (notably `true` for `DaxEndCta`).
  - Any CTA-specific injected dependencies (e.g., `getTrackersDescription()` helpers): preserve.
- Replace `activeIncludeId = 0` with the real ID per the variant table.
- Implement `configureContentViews(view)` using the Per-CTA Copy Matrix string IDs. Reuse dynamic description helpers (e.g., `getTrackersDescription()`) for CTAs that have them.
- Override `setOnPrimaryCtaClicked` / `setOnSecondaryCtaClicked` / `setOnOptionClicked` only for buttons this CTA uses.
- If this is `DaxTrackersBlockedCta`, override `onTypingAnimationSettled(onTypingAnimationFinished)` to invoke the callback.
- If this is `DaxSiteSuggestionsCta`: (a) bind `onboardingStore.getSitesOptions()` to the option buttons (do NOT rewrite the options data source); (b) preserve the legacy option-click callback shape — the fragment passes `onOptionSelected: (DaxDialogIntroOption) -> Unit` and the legacy CTA invokes it with the clicked `DaxDialogIntroOption`. Keep that exact signature and invocation; the fragment handler at Stage 1's wiring relies on receiving the full `DaxDialogIntroOption` (not just its index or link text).
- Verify the class remains a top-level `data class` extending `BrandDesignContextualDaxDialogCta`.

### P4. Replace `CtaViewModel` sentinel(s)

```bash
grep -n "SENTINEL\[<CtaName>@" app/src/main/java/com/duckduckgo/app/cta/ui/CtaViewModel.kt
```

For each match, replace the marker-plus-legacy-fallback block with real brand-design CTA construction. Example for `DaxSerpCta`:

```kotlin
if (isBrandDesignUpdateEnabled()) {
    return DaxSerpBrandDesignUpdateContextualCta(
        onboardingStore,
        appInstallStore,
        isLightTheme = appTheme.isLightModeEnabled(),
    )
}
return DaxSerpCta(onboardingStore, appInstallStore)
```

Flag read must be on a worker thread (inherit whatever pattern Stage 1 set up).

### P5. Replace `BrowserTabViewModel` sentinel(s)

**Skip this step entirely for `DaxEndCta`** (0 touchpoints).

For all other CTAs:

```bash
grep -n "SENTINEL\[<CtaName>@" app/src/main/java/com/duckduckgo/app/browser/BrowserTabViewModel.kt
```

For each match, remove the sentinel comment and finalize the body. Keep legacy behavior if the new type needs no specialization; specialize if it does. Single `command.value =` per state change.

### P6. `BrowserTabFragment` — no changes

Stage 1 already handled all fragment wiring. If you believe your CTA needs a fragment edit, STOP and report to the orchestrator.

### P7. Per-dialog test

Create `app/src/test/java/com/duckduckgo/app/cta/ui/Dax<Name>BrandDesignUpdateContextualCtaTest.kt`. Put here:
- A test asserting the CTA returns the new brand-design class when the flag is on. Use the same entrypoint `CtaViewModelTest` uses for this CTA — typically `refreshCta()` for SERP/trackers/network/no-trackers/end, or direct calls to `getFireDialogCta()` / `getSiteSuggestionsDialogCta()` / `getEndStaticDialogCta()` for the CTAs those functions produce. Mirror the pattern already in `CtaViewModelTest` for your CTA's legacy counterpart.
- Pixel-parameter assertions. Preserve whichever pixel fields the legacy CTA sets and leave the null ones null. Specifically:
  - Read your CTA's constructor in `Cta.kt` to see which of `shownPixel` / `okPixel` / `cancelPixel` / `closePixel` are non-null. Contextual CTAs typically set `shownPixel`, `okPixel`, and `closePixel` while leaving `cancelPixel = null`; do not invent a `cancelPixel` where the legacy CTA has none.
  - For each non-null pixel in the new brand-design CTA, assert it fires with the correct `ctaPixelParam` via the standard `CtaViewModel` path (`onCtaShown`, `onUserClickCtaOkButton`, `onUserDismissedCta(viaCloseBtn = true)`).
  - This catches the telemetry-regression class specifically — if the Stage 1 stub's pixel placeholders weren't carried forward correctly in P3, the non-null pixel assertions fail.

Do NOT add test cases to shared `CtaViewModelTest` or `BrowserTabViewModelTest`.

### P8. Verify

```bash
./gradlew spotlessCheck
./gradlew :app:testPlayDebugUnitTest --tests "com.duckduckgo.app.cta.ui.Dax<Name>BrandDesignUpdateContextualCtaTest"
./gradlew :app:testPlayDebugUnitTest --tests "com.duckduckgo.app.cta.ui.CtaViewModelTest"
./gradlew :app:testPlayDebugUnitTest --tests "com.duckduckgo.app.browser.BrowserTabViewModelTest"
```

Flag on + off manual smoke test on a device if possible.

### P9. Halt for user verification (NO PUSH)

Do NOT run `gt submit`, `git push`, `gh pr create`, or any remote-write command. All Stage 2 work for this CTA must remain local until the user explicitly verifies and pushes the stack.

Confirm the local state is clean and report:

```bash
git status
git log --oneline -3
gt log short
```

Expected: working tree clean; this CTA's commit sits on its pre-created dialog branch; Graphite parent chain is intact up to `contextual-dialog-foundation`; no commits have been pushed.

**Hand-off signal:** CTA migrated and locally verified (tests green, flag toggling checked). Report the branch name and HEAD SHA, state that no push has occurred, and stop. The user runs `gt submit --stack` themselves once all 7 Stage 2 migrations are reviewed.
