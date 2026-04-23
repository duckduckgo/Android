---
name: bootstrap-contextual-onboarding-rebrand
description: >
  One-shot bootstrap for the contextual (in-context) onboarding dialog rebrand.
  Creates the abstract base class, new layout, content-include templates, tap-to-skip wiring,
  stub CTA classes for all seven contextual CTAs, sentinel scaffolding in CtaViewModel and
  BrowserTabViewModel, and Fragment/ViewModel wiring. Runs once, serially, before Stage 2
  per-dialog agents fan out.
---

# Bootstrap Contextual Onboarding Rebrand

## Context

This skill is the Stage 1 foundation for migrating the seven `OnboardingDaxDialogCta` subclasses to the brand-design layout. It runs exactly once, serially, in its own worktree. After it lands, an orchestrator pre-stacks seven Stage 2 branches (one per contextual CTA) and dispatches seven agents in parallel, each running the sibling skill `update-contextual-onboarding-dialog`.

## Preconditions

- Worktree is checked out to `feature/mike/onboarding-brand-design-updates/contextual-dialog-foundation` (the orchestrator created this branch via `gt create` off the stack root `feature/mike/onboarding-brand-design-updates/better-incontext-click-handling`).
- Working tree is clean.
- The two skill files (`bootstrap-contextual-onboarding-rebrand/SKILL.md` and `update-contextual-onboarding-dialog/SKILL.md`) are already committed to this branch (the orchestration plan does skill authoring before this skill runs).
- This skill does NOT create branches or worktrees — that is the orchestrator's responsibility.

## Inputs

None required. Optionally a Figma URL for the new card chrome; if absent, adapt the sibling NTP bubble card chrome at `include_onboarding_bubble_dax_dialog_brand_design_update.xml`.

## Required Rules

These rules MUST be followed during Stage 1. Each was hard-won from PR review on 8277/8291/8292/8293 or the Asana tap-to-skip bug.

### Architecture

1. **Base class owns the render pipeline.** `BrandDesignContextualDaxDialogCta` owns `showOnboardingCta`, `hideOnboardingCta`, `snapToFinished`, `getAllContentIncludes`, `resetAllIncludesExcept`, `resetSharedViewState`. Subclasses provide `activeIncludeId` + `configureContentViews` + per-button click overrides only.
2. **`BrandDesignContextualDaxDialogCta` is an abstract subclass of the sealed `OnboardingDaxDialogCta`** (`Cta.kt:117`). Declare in the same package/module (`com.duckduckgo.app.cta.ui` in `:app`) so Kotlin's sealed constraint is satisfied. Mirrors how `BrandDesignUpdateBubbleCta` relates to sealed `DaxBubbleCta`.
3. **Never override lifecycle methods in stub subclasses.** Subclasses override `setOnPrimaryCtaClicked` / `setOnSecondaryCtaClicked` / `setOnOptionClicked` only for buttons they actually render (PR 8293 bug).

### Copy & text

4. **Reuse existing `strings.xml` IDs.** Never take copy from Figma — it is frequently wrong on this project. If new copy seems genuinely needed, stop and ask.
5. **Never place user-facing strings in `donottranslate.xml`.**

### Animation & tap-to-skip

6. **Use `DaxTypeAnimationTextView`, not `TypeAnimationTextView`.** The former respects `app:typography`.
7. **Tap-to-skip uses `snapToFinished()`:** `finishAnimation()`, then if `!hasAnimationStarted()` set title text directly (prevents blank title), snap `contentFadeInAnimator`, flip `animationFinished` before invoking `onTypingAnimationFinished()` exactly once. This pattern is already implemented on the stack root in `BrandDesignUpdateBubbleCta` — copy it, don't reinvent.
8. **Tap-intercept on the card container via `TouchInterceptingLinearLayout`** (class already exists on stack root). Set `interceptChildTouches = true` during animation; clear on natural animation end AND tap-to-skip. Never wire click listeners directly on title/description/option buttons for skip purposes.
9. **Set all text content before the card fade-in begins.** Hidden title, description, and option button labels must all be set before the card becomes visible — `alpha=0` views still occupy layout space; late-setting causes visible growth.
10. **Content-include children must NOT have `android:alpha="0"` in XML.** Parent include's alpha is the sole visibility controller.

### Content includes

11. **IDs unique across every content include mountable in the card.** Not just within one include — across all of them. `findViewById` is depth-first; a duplicate ID guarantees the wrong (typically GONE) view is targeted.
12. **Use `OnboardingSelectionButton` for option buttons, not `DaxButtonSecondary`.**
13. **`DaxButtonPrimary` in brand-design includes requires the onboarding theme overlay:**

```xml
android:theme="@style/ThemeOverlay.DuckDuckGo.Onboarding"
android:textAppearance="@style/Typography.DuckDuckGo.Onboarding.Button"
tools:ignore="InvalidDaxButtonProperty"
```

### Shared-view state

14. **Reset every mutable property you set on shared views** (gravity, text, icon, visibility, alpha, padding) before the next CTA binds. Base class `resetSharedViewState()` owns this. Contextual dialogs routinely transition between each other, so this matters more than it did for NTP bubbles.
15. **Header image fade-out:** documented for future CTAs; none of the seven contextual CTAs in this project uses a header image, so Stage 1 does not implement header-image support.

### ViewModel & command flow

16. **Single `command.value =` per state change.** `SingleLiveEvent` drops the first of two consecutive setValue calls if the observer hasn't registered yet.
17. **Every dismiss path** (X, "No thanks", primary after success, back press) emits `Command.HideOnboardingDaxDialog(onboardingCta)` — reuse the existing command at `Command.kt:433`. Do NOT introduce a new command.
18. **Feature-flag reads in `CtaViewModel` run on a worker thread.** Use an IO dispatcher.

### Fragment wiring

19. **When showing the brand-design container, `gone()` the legacy one.** And vice versa. Prevents the "empty background" bug surfaced on PR 8291.
20. **Use `appTheme.isLightModeEnabled()` for theme decisions, not `Configuration.UI_MODE_NIGHT_MASK`.** Except inside view classes that can't inject `appTheme`.

### Code hygiene

21. **Alias outside-module R imports:** `import com.duckduckgo.mobile.android.R as DesignSystemR`. Keep `com.duckduckgo.app.browser.R` as the default `R`.
22. **No testing shortcuts in production code.** Remove any CTA-logic bypass before committing.

## Steps

### S1. Read current state

- `OnboardingDaxDialogCta` (sealed at `Cta.kt:117` and its 7 subclasses)
- `BrandDesignUpdateBubbleCta` (reference implementation on the stack root — mirror its structure for contextual)
- `include_onboarding_in_context_dax_dialog.xml` (the legacy contextual layout)
- `BrowserTabFragment.showOnboardingDialogCta` (show entry) and `BrowserTabFragment.hideOnboardingDaxDialog` (hide entry; delegates to `onboardingCta.hideOnboardingCta(binding)`)
- `Command.HideOnboardingDaxDialog` at `Command.kt:433` — the dismissal command to reuse
- `TouchInterceptingLinearLayout` at `app/src/main/java/com/duckduckgo/app/onboarding/ui/view/TouchInterceptingLinearLayout.kt` — the tap-intercept mechanism to reuse
- Existing `isBrandDesignUpdateEnabled()` toggle — this is the gating toggle (settled)

### S2. Create `BrandDesignContextualDaxDialogCta`

Abstract subclass of sealed `OnboardingDaxDialogCta`, declared in `Cta.kt` or a sibling file in `com.duckduckgo.app.cta.ui`. Mirrors the `BrandDesignUpdateBubbleCta` ↔ `DaxBubbleCta` relationship.

**Scope note.** None of the seven contextual CTAs uses a secondary button or a header image today. Include the no-op `setOnSecondaryCtaClicked` default (rule 3) for future-proofing, but do NOT implement header-image fade-out (rule 15).

Methods owned:
- `showOnboardingCta(binding, onPrimaryClicked, onSecondaryClicked, onTypingAnimationFinished, onSuggestedOptionsSelected, onDismissCtaClicked)` — card fade-in → type title → fade in active content include → final callbacks
- `hideOnboardingCta(binding)` — reverse and clear
- `snapToFinished()` — rule 7 implementation (copy from `BrandDesignUpdateBubbleCta`)
- `resetSharedViewState()` — rule 14 implementation (reset gravity, text, visibility, alpha on title/description/dismiss button)
- `getAllContentIncludes()` → list of content-include IDs
- `resetAllIncludesExcept(activeId)` — housekeeping between transitions
- `protected open fun onTypingAnimationSettled(onTypingAnimationFinished: () -> Unit) { /* no-op default */ }` — the hook `DaxTrackersBlockedBrandDesignUpdateContextualCta` will override to notify the ViewModel
- Default no-op `setOnPrimaryCtaClicked`, `setOnSecondaryCtaClicked`, `setOnOptionClicked` — subclasses override only what they need

### S3. Create `include_onboarding_in_context_dax_dialog_brand_design_update.xml`

New card chrome layout. Structure:

- Outer `TouchInterceptingLinearLayout` (rule 8) as the card container
- `DaxTypeAnimationTextView` title (rule 6, 9)
- `DaxTextView` description (shared, chrome-level — NOT per-include)
- Dismiss button (the X)
- `<include layout="@layout/include_brand_design_contextual_dialog_primary_cta" android:visibility="gone" />`
- `<include layout="@layout/include_brand_design_contextual_dialog_options" android:visibility="gone" />`

**Description ownership (settled):** the description view lives in this shared card chrome, not in the content includes. Mirror the NTP bubble pattern.

**Shared-text contract (settled):** title and description are populated by the subclass's `configureContentViews(view)` implementation. The base class does NOT expose separate `setTitle(id)` / `setDescription(id)` hooks — subclasses call `view.findViewById(R.id.brandDesignTitle).text = ...` (and similarly for description) directly, matching the NTP bubble pattern. For CTAs with no title (see the matrix), the subclass leaves the title view empty and its alpha stays 0 per rule 9. For dynamic descriptions (trackers, main network), the subclass assembles the HTML string the same way the legacy class does via `getTrackersDescription()`.

### S4. Create the two initial content include templates

- `include_brand_design_contextual_dialog_primary_cta.xml` — primary button + optional secondary button. **Description is NOT in this include** (see S3).
- `include_brand_design_contextual_dialog_options.xml` — multiple `OnboardingSelectionButton`s for site suggestions. Description is NOT in this include.

Apply rules 11, 12, 13 during layout creation. Unique IDs across both includes; `OnboardingSelectionButton` for option buttons; `DaxButtonPrimary` gets the onboarding theme overlay.

### S5. Tap-to-skip wiring

Attach `snapToFinished` to the outer `TouchInterceptingLinearLayout` card container (rule 8). Set `interceptChildTouches = true` when animation starts; clear on both natural end and tap-to-skip. `hasAnimationStarted()` on `DaxTypeAnimationTextView` already exists — reuse.

### S6. Fragment wiring

This is the ONLY stage that touches `BrowserTabFragment` and `fragment_browser_tab.xml`; Stage 2 never does.

- Add an `<include>` to `app/src/main/res/layout/fragment_browser_tab.xml` for the new brand-design contextual container, sibling to the existing legacy `includeOnboardingInContextDaxDialog` include. Give it a stable id (`includeOnboardingInContextDaxDialogBrandDesign`). Both includes remain in the layout; the fragment toggles visibility per rule 19.
- Add a corresponding view-binding reference in `BrowserTabFragment.kt`: `binding.includeOnboardingInContextDaxDialogBrandDesign`.
- In `showOnboardingDialogCta`, branch on `configuration is BrandDesignContextualDaxDialogCta` — show the brand-design container, `.gone()` the legacy one, invoke the new brand-design code path. Else: existing legacy path unchanged.
- **Move CTA-type-specific callback logic into the base class.** The current fragment type-tests at line 5399 (`DaxTrackersBlockedCta`) and 5405 (`DaxSiteSuggestionsCta`) MUST NOT persist for the brand-design path. Fragment always passes both callbacks; the base class exposes a settled hook shape:

```kotlin
abstract class BrandDesignContextualDaxDialogCta(...) {
    protected open fun onTypingAnimationSettled(onTypingAnimationFinished: () -> Unit) {
        // Default: no-op. Subclasses that need to notify the ViewModel override.
    }
}
```

The base class invokes `onTypingAnimationSettled(onTypingAnimationFinished)` exactly once when the typing animation has fully settled (natural end or tap-to-skip). `DaxTrackersBlockedBrandDesignUpdateContextualCta` overrides it to invoke `onTypingAnimationFinished()`; all other CTAs inherit the no-op.

Option selection: the fragment passes `onOptionSelected`; only `DaxSiteSuggestionsBrandDesignUpdateContextualCta` invokes it.

- `hideOnboardingDaxDialog` (the fragment-level dispatch method) is extended to clear the new brand-design container as well as the legacy one. Each CTA's own `hideOnboardingCta(binding)` continues to own its view-level cleanup.
- **Also update the generic `hideDaxCta()` helper** at `BrowserTabFragment.kt:5559`. It currently calls `daxDialogInContext.dialogTextCta.cancelAnimation(); daxDialogInContext.daxCtaContainer.gone()` — extend it to also cancel the brand-design typing animation and `gone()` the brand-design container. This is called when switching to home/widget CTAs; without the update, the brand-design contextual dialog can stay visible over the wrong screen.

### S7. `BrowserTabViewModel` wiring

Reuse the existing `Command.HideOnboardingDaxDialog(onboardingCta)` — already handles dismissal for contextual CTAs and is wired through `BrowserTabFragment.handleCommand` at line 2798 (rule 17). Do not introduce a new command.

Ensure every dismiss path for a `BrandDesignContextualDaxDialogCta` emits this command (rule 17). Single `command.value =` per state change (rule 16).

### S8. Stub 7 Stage 2 CTA classes, then scaffold sentinels

**S8a. Stub files (prerequisite for sentinels to compile).** Create 7 stub files in `app/src/main/java/com/duckduckgo/app/cta/ui/`, one per CTA. Example for `DaxSerpCta`:

```kotlin
// app/src/main/java/com/duckduckgo/app/cta/ui/DaxSerpBrandDesignUpdateContextualCta.kt
// STUB: Stage 2 agent populates activeIncludeId and configureContentViews.
data class DaxSerpBrandDesignUpdateContextualCta(
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
    override val isLightTheme: Boolean,
) : BrandDesignContextualDaxDialogCta(
    ctaId = CtaId.DAX_DIALOG_SERP,
    // ...use the legacy CTA's constructor values as placeholders
) {
    override val activeIncludeId: Int = 0 // STUB: Stage 2 replaces with R.id.<include>.
    override fun configureContentViews(view: View) {
        // STUB: Stage 2 agent implements.
    }
}
```

Repeat for `DaxTrackersBlocked`, `DaxMainNetwork`, `DaxNoTrackers`, `DaxFireButton`, `DaxSiteSuggestions`, `DaxEnd`. Class-name convention: `Dax<Name>BrandDesignUpdateContextualCta`. Each stub compiles on its own. **Runtime behavior on Stage 1 branch:** the stubs are never instantiated — sentinels return legacy CTAs. Stub files exist in source only.

**S8b. Canonical sentinel shape** (used in both `CtaViewModel` and `BrowserTabViewModel`):

```
// SENTINEL[<CtaName>@<location-hint>]: Stage 2 replaces this block. Until then, legacy behavior is preserved.
```

The body below the marker preserves legacy behavior so the Stage 1 branch is runtime-safe with `isBrandDesignUpdateEnabled()` both on and off. Never use bare `TODO()`.

**S8c. Scaffold `CtaViewModel`.** One sentinel per CTA, at each construction site. Construction sites (verified 2026-04-23):

- `getBrowserCta()` — `DaxSerpCta`, `DaxTrackersBlockedCta`, `DaxMainNetworkCta`, `DaxNoTrackersCta`, `DaxEndCta`
- `getFireDialogCta()` — `DaxFireButtonCta`
- `getSiteSuggestionsDialogCta()` — `DaxSiteSuggestionsCta`
- `getEndStaticDialogCta()` — `DaxEndCta` (second site). **Return-type widening required**: current signature is `suspend fun getEndStaticDialogCta(): OnboardingDaxDialogCta.DaxEndCta?`. Widen to `OnboardingDaxDialogCta?`. Audit call sites and adjust.

Note: `getHomeCta()` constructs `DaxBubbleCta.DaxEndCta` (NTP bubble variant) — out of scope; leave untouched.

Example for `DaxSerpCta` (Stage 1 writes):

```kotlin
if (isBrandDesignUpdateEnabled()) {
    // SENTINEL[DaxSerpCta@cta-construction]: Stage 2 replaces this block. Until then, legacy behavior is preserved.
    return DaxSerpCta(onboardingStore, appInstallStore)
}
return DaxSerpCta(onboardingStore, appInstallStore)
```

All flag reads on a worker thread (rule 18).

**S8d. Scaffold `BrowserTabViewModel`.** Audit every per-CTA touchpoint:

- `when` branches: line 1308 (`DaxSiteSuggestionsCta`), 4597 (`DaxSerpCta`), 4615–4617 (`DaxTrackersBlockedCta`/`DaxNoTrackersCta`/`DaxMainNetworkCta` grouped), 4632 (`DaxFireButtonCta`).
- Standalone `if (cta is ...)`: line 3359 and 3375 (`DaxTrackersBlockedCta`), 4668 (`DaxFireButtonCta`).

**Split the grouped `when` branch at 4615–4617 into three separate branches** so each CTA gets its own sentinel line — otherwise three Stage 2 agents would contend for the same line.

For each touchpoint, add a sibling branch / sibling check for the new `Dax<X>BrandDesignUpdateContextualCta` type. `<location-hint>` values distinguish touchpoints within the same CTA (e.g. `cta-construction`, `launch-fire-dialog-check`, `trackers-blocked-typing-finished`, `privacy-shield-highlight-check`).

`DaxEndCta` has **zero** touchpoints in `BrowserTabViewModel` — no sentinel needed there.

Example for `DaxFireButtonCta` at line 4668 (Stage 1 writes):

```kotlin
if (cta is OnboardingDaxDialogCta.DaxFireButtonCta || cta is DaxFireButtonBrandDesignUpdateContextualCta) {
    // SENTINEL[DaxFireButtonCta@launch-fire-dialog-check]: Stage 2 replaces this block. Until then, legacy behavior is preserved.
    <existing legacy body unchanged>
}
```

**Stage 2 discovery:** `grep -n "SENTINEL\[<CtaName>@" app/src/main/java/com/duckduckgo/app/cta/ui/CtaViewModel.kt app/src/main/java/com/duckduckgo/app/browser/BrowserTabViewModel.kt` returns every line the Stage 2 agent for `<CtaName>` must touch.

### S9. Wire the feature flag

Use `isBrandDesignUpdateEnabled()` (no new toggle — settled). All reads on a worker thread (rule 18).

### S10. Tests

Add a new Robolectric-backed JVM unit test file `app/src/test/java/com/duckduckgo/app/cta/ui/BrandDesignContextualDaxDialogCtaTest.kt`. Runnable via:

```
./gradlew :app:testPlayDebugUnitTest --tests "com.duckduckgo.app.cta.ui.BrandDesignContextualDaxDialogCtaTest"
```

Robolectric is the accepted harness — `CtaViewModelTest` in this repo already uses Android-backed test infrastructure; follow its setup.

Test cases:
- `snapToFinished_tapBeforeAnimationStarts_setsTitleDirectly`
- `snapToFinished_tapMidAnimation_finishesWithoutBlankTitle`
- `snapToFinished_tapAfterAnimationEnds_isNoOp`
- `snapToFinished_rapidDoubleTap_firesCallbackOnlyOnce`
- `resetSharedViewState_resetsGravityTextAlphaVisibility`
- `getAllContentIncludes_returnsExpectedIds`

If Robolectric proves insufficient for animation-listener timing, extract the state machine into a pure Kotlin helper.

### S11. Verify

```bash
./gradlew spotlessCheck
./gradlew :app:testPlayDebugUnitTest --tests "com.duckduckgo.app.cta.ui.BrandDesignContextualDaxDialogCtaTest"
./gradlew :app:testPlayDebugUnitTest --tests "com.duckduckgo.app.cta.ui.CtaViewModelTest"
./gradlew :app:testPlayDebugUnitTest --tests "com.duckduckgo.app.browser.BrowserTabViewModelTest"
```

The first line runs the new base-class test file. The second and third confirm pre-existing shared tests still pass — Stage 1 edited both `CtaViewModel` and `BrowserTabViewModel` (sentinel scaffolding), so both test classes must remain green.

Manually build and install internal; confirm the legacy contextual flow still works with the new flag off.

### S12. Halt for user verification (NO PUSH)

Do NOT run `gt submit`, `git push`, `gh pr create`, or any remote-write command. All Stage 1 work must remain local until the user explicitly verifies and pushes.

Confirm the local state is clean and report:

```bash
git status
git log --oneline -5
gt log short
```

Expected: working tree clean; the Stage 1 bootstrap commit(s) sit on `feature/mike/onboarding-brand-design-updates/contextual-dialog-foundation`; Graphite parent is `feature/mike/onboarding-brand-design-updates/better-incontext-click-handling`; no commits have been pushed.

**Hand-off signal:** Stage 1 foundation committed locally and verified. Report the branch name and HEAD SHA, state that no push has occurred, and stop. The orchestrator proceeds to pre-stack the 7 dialog branches locally; the user runs `gt submit` themselves once they are satisfied.
