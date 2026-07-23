# Config-driven dialog POC — results (2026-07-23)

Implements `temp-docs/2026-07-09-onboarding-dialog-spec-design-v6-summary.md` end to end on branch `feature/lpaczos/linear-onboarding-dialog-spec` (commits `abd8eda963..bc6773a3a9`, 21 commits, ~4.3k lines). Everything lives in `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/`.

## What was built

| Spec element | Implementation |
|---|---|
| `DialogConfig` / `ContentConfig` / `Stateful` / `TextConfig` / `CtaConfig` | `DialogConfig.kt`, `ContentConfig.kt`, `TextConfig.kt` — pure value data, 8 content variants (4 stateless, 4 stateful with state classes) |
| `DialogConfigResolver` | Pure `when` over all 15 `NewUserOnboardingActivityDialog` variants; command-only dialogs → null; copy extracted verbatim from legacy |
| Title machinery | `DialogTitleController` (typing / snap / finishTyping / preventWidows / html-decode / sizing twin) — controller over existing include views; compound-widget XML swap deferred |
| Binders | `ContentHandle` + `EntranceScope` + `DialogBinder`/`StatefulDialogBinder` + `BindScope`/`ContentInteraction`; 8 binders + `ContentBinder` dispatcher. Binders declare animators, never run them |
| Render engine | `engine/DialogRenderEngine.kt` — per-axis diff, one pipeline for animate+snap, generation-guarded morph continuations, one-tap skip, engine-owned CTA wiring (armed post-entrance) |
| Axis controllers | `BackgroundController` (wraps `OnboardingBackgroundAnimator`), `EmbellishmentController` (fit veto + enter/exit/snap + generation supersede), `CardAnchorController`, `StepIndicatorController` |
| Live state | `ContentValueStore` (flow per stateful screen, keyed by config class), VM-owned; resume sync + bottom-sheet results write through it |
| Slim VM | `ConfigDrivenOnboardingPageViewModel` — ViewState = stepId + config + 2 flags; blind event forwarding; animate keyed by step identity |
| Shown pixels | `OnboardingDialogShownPixels` exhaustive mapper, called by BOTH VMs (legacy `applyDialog` delegates to it — the one legacy behavior edit) |
| Fragment | `ConfigDrivenWelcomePage` + `OnboardingIntroChoreographer` (intro/outro port, new arm only) |
| Rollout | `configDrivenDialogs()` toggle (default INTERNAL) on `OnboardingBrandDesignUpdateToggles`; new blueprint at the `OnboardingPageManager`/`Builder`/`OnboardingViewModel` seam; flag-off short-circuits (verified byte-identical) |

## Verification

- `:app:compileInternalDebugKotlin` + `:app:compileInternalDebugUnitTestKotlin` clean; `spotlessApply` clean.
- Touched legacy test classes pass: `BrandDesignUpdatePageViewModelTest` (incl. the 3 shown-pixel assertions through the shared mapper), `OnboardingViewModelTest`.
- No new tests (POC scope). No device run yet.
- Every task went through implementer → reviewer → fix → re-verify loops; final whole-branch review (most capable model): **"ready with fixes" for an internal-flag POC** — the one Important finding (outro cancel guard) fixed in `bc6773a3a9`.

Criticals caught and fixed during review loops: bind-time Lottie autostart (state replay), stale ChangeBounds continuation corrupting a later render, embellishment skip-escape + transition reentrancy, first-render racing the outro, legacy test compile break from the pixel extraction, CTAs clickable at alpha 0.

## Identified gaps (spec-level learnings)

1. **`ContentHandle` is not enough for content-initiated interactions.** Input-screen-preview submits queries from text input (not a CTA); quick setup opens bottom sheets / system dialogs. Added `BindScope(coroutineScope, emit, execute)` + `ContentInteraction` sealed interface. Spec should adopt this shape.
2. **`primaryCta` must be nullable** — the preview screen has no primary CTA (progresses via query submit). Changed in the model.
3. **Card arrow visibility is screen data** — currently the engine's one screen-type branch (`content !is InputScreenPreview`). Should be a `DialogConfig` field (e.g. `showCardArrow`).
4. **Animate policy conflict (biggest open question):** engine rule "empty stage always animates" vs rotation-snap. Fragment recreation gives a fresh engine (`previous == null`), so rotation replays the entrance even though the VM says snap. Proposed: make the empty-stage rule a VM decision (VM knows re-entry vs first-show), engine just obeys `animate`; or prime the new engine with the previous config from a retained holder.
5. **Copy variants the config can't express:** legacy html-decodes welcome body1 only for sync-restore/custom-AI. Binder infers from `body2 == null`; a `TextConfig` html flag would be honest.
6. **The `ChangeBounds` card morph is the one choreography piece outside Animator ownership** — mid-morph skip settles content but not the bounds tween; a same-frame double render can strand a continuation (self-recovers on next render/skip; documented).
7. **Legacy card anchoring biases are internally inconsistent**; `CardAnchorController` normalizes them (BottomWing phone bias 0→1, LeftWing/BobbingDax unanchored 0→1). Needs design sign-off / device check before wider exposure.
8. `ContentConfig.QuickSetup.isReinstallUser` is dead — legacy also never renders on it. Drop from the spec or wire it.
9. `ContentValueStore` keyed by config class assumes each stateful screen appears at most once per plan run (true today; documented).
10. Dropped legacy niceties, noted in reports: `interceptChildTouches` during entrance (partially replaced by post-entrance CTA arming), preview tab-switch card ChangeBounds (binder can't reach the shared cardView — structurally correct per spec), left-wing snap `requestLayout()`, custom-AI intro snap on rotation (VM lacks the flag).

## Proposed improvements

- **Spec v7 edits:** adopt `BindScope`/`ContentInteraction`; nullable `primaryCta`; `showCardArrow` on `DialogConfig`; html flag on `TextConfig`; resolve the animate/rotation policy explicitly; drop `isReinstallUser`.
- **Engine:** expose an `isAnimating` signal so the fragment can gate `interceptChildTouches`; consider owning the morph via a trackable mechanism (or accepting the documented limitation).
- **Next steps to graduate beyond INTERNAL:** device smoke test both flag states (welcome → comparison → address bar chain first, per spec's de-risk note), Maestro release-blocker flows in both flag states, in-place `OnboardingDialogTitleView` compound-widget refactor, design sign-off on anchor-bias normalization, unify license-header years.

## Artifacts

- Plan: `temp-docs/plans/2026-07-23-config-driven-dialog-poc-plan.md`
- Per-task briefs/reports/review diffs + progress ledger: `.superpowers/sdd/` (gitignored)
