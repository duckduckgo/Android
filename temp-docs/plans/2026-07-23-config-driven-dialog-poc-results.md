# Config-driven dialog POC — results (2026-07-23)

Implements `temp-docs/2026-07-09-onboarding-dialog-spec-design-v6-summary.md` end to end on branch `feature/lpaczos/linear-onboarding-dialog-spec` (commits `abd8eda963..bc6773a3a9`, 21 commits, ~4.3k lines). Everything lives in `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/`. A follow-up device-debugging round (2026-07-24, commits `7c6c778844..6a4e4ed65e`) fixed ten issues found on the first device run — see "Device debugging round" below.

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
- No new tests (POC scope). First device run happened in the 2026-07-24 debugging round below: welcome → comparison → address bar → input → input preview plus quick setup, on a phone, both orientations, with tap-to-skip, mid-flow restarts and side-by-side screenshot comparison against the legacy arm.
- Every task went through implementer → reviewer → fix → re-verify loops; final whole-branch review (most capable model): **"ready with fixes" for an internal-flag POC** — the one Important finding (outro cancel guard) fixed in `bc6773a3a9`.

Criticals caught and fixed during review loops: bind-time Lottie autostart (state replay), stale ChangeBounds continuation corrupting a later render, embellishment skip-escape + transition reentrancy, first-render racing the outro, legacy test compile break from the pixel extraction, CTAs clickable at alpha 0.

## Device debugging round (2026-07-24)

Ten fixes from the first device run (commits `7c6c778844..6a4e4ed65e`). Every issue was either a legacy behavior the port missed or an engine policy that didn't survive contact with the device; none required changing the spec's architecture, and several sharpen it (folded into the gaps list below).

| Symptom | Root cause | Fix |
|---|---|---|
| Welcome card never appeared after the intro | Card root starts alpha 0 + invisible in XML; legacy's `fadeInDialog`/snap reveal was never ported — whole-stage choreography owned by no single dialog | New engine `revealCard()` stage before the morph (fades on an empty stage, synchronous no-op once visible); snap path sets visible + alpha directly (`7c6c778844`) |
| Card sank to the screen bottom (address bar et al.) | `CardAnchorController`'s bias "normalization" inverted legacy: phone bias is 0 in every legacy anchor block, bias 1 exists only in the welcome XML | Anchored bias became per-decoration data (`anchoredCardBiasPhone/Tablet` on `SettledDecoration`); unanchored normalized to tablet 0.5 / phone 0 (`9267292fdd`) |
| Mid-flow restart: intro logo peeking behind the card; blank space above non-welcome content | Re-entry settled the intro views at their intro-END state (legacy zeroes them in every snap branch); the welcome include defaults *visible* in the shared XML and the engine only hides the include it bound itself | `OnboardingIntroChoreographer.snapToOutroEndState()`; `ContentBinder.hideAll()` on an empty stage before the first bind (`bcf3f076c9`) |
| Rotation replayed the whole entrance | Engine forced an empty stage (= every fragment recreation) to animate, overriding the VM's `animateEntry = false` | Engine obeys `animate` verbatim; the VM signal alone encodes the spec's empty-stage rule (gap 4, resolved as proposed) (`adeb93f0d7`) |
| Embellishment vanished for good after landscape → portrait | The retained VM emits before the recreated view's first layout; the fit veto measured a 0-height root and the veto path never re-measures | First render defers through `binding.root.doOnLayout`, mirroring legacy's wrapping of both no-predecessor renders (`0d5b5bc734`) |
| Bobbing dax entered seconds late, out of sync with the background | Engine serialized the enter behind the exit's multi-second Lottie dismiss; legacy runs dismiss + enter in one synchronous block | Fit + enter run immediately; only the card re-anchor (`onSettled`) still waits for the exit, per the hold-anchor rule (`c5cbe756be`) |
| Card arrow hid seconds late on input → preview, and sat on the wrong side of the card on every screen after welcome | Arrow visibility rode the deferred embellishment settle; the horizontal offset choreography (XML start offset on welcome, 80dp-from-end after, slide across on welcome → next) was not ported at all | Both are screen data applied synchronously at render; the position diff drives a tracked 400ms slide on the one transition where it changes (`001760ebd6`) |
| Tap-to-skip dead on the card (background worked); a settled-stage tap could visibly replay a decoration | No `interceptChildTouches` during entrances, so interactive children ate card taps; finished animators stayed in the tracked lists forever, and `end()` on a finished/unstarted animator RESTARTS it, re-firing the `onAnimationStart` listeners that call `playAnimation()` | Engine `isAnimating` → `onAnimatingChanged` → `interceptChildTouches` (the signal this doc proposed exposing); tracked animator lists self-remove on natural completion; embellishment skip drains then `snap()`s the current decoration; skip now settles every axis including background — a deliberate improvement over legacy's content-only skip (`40f61c5c6a`) |
| Rotation on input preview: suggestions invisible but clickable | Snap path called `end()` on never-started entrance animators — `AnimatorSet.end()` is a no-op while unstarted (unlike `ValueAnimator.end()`, which self-starts) | Snap path `start()`s then `end()`s each entrance animator; also covers the check-icon stagger (`a6ffd7c317`) |
| Quick-setup default-browser toggle stuck ON after declining the system dialog | Store only held confirmed state while the switch view flips itself optimistically; the corrective write equalled the stored value, so the `MutableStateFlow` deduped it and the binder never saw it | Toggle interactions mirror the optimistic checked value into the content state before their side effect (`6a4e4ed65e`); the add-widget toggle had the identical latent bug |

New known gap found but not fixed: rotating during the live intro animation likely leaves a blank screen — `PlayIntroAnimation` is a one-shot command and `hasPlayedIntroAnimation` never flips, so the recreated fragment's collector waits forever. Same family as the custom-AI intro-snap-on-rotation dropped nicety (item 10 below).

Also checked and ruled out: the "Skip Onboarding" pill differing between comparison screenshots is activity-level (`FullOnboardingSkipper` state, shared by both arms), not a renderer difference.

## Identified gaps (spec-level learnings)

1. **`ContentHandle` is not enough for content-initiated interactions.** Input-screen-preview submits queries from text input (not a CTA); quick setup opens bottom sheets / system dialogs. Added `BindScope(coroutineScope, emit, execute)` + `ContentInteraction` sealed interface. Spec should adopt this shape.
2. **`primaryCta` must be nullable** — the preview screen has no primary CTA (progresses via query submit). Changed in the model.
3. **Card arrow visibility AND position are screen data** — confirmed on device (2026-07-24): both must apply synchronously at render, not ride the embellishment settle. The engine now has two screen-type branches (`showArrowFor`, `arrowAtEndFor`); the spec's `DialogConfig` should carry both as fields (e.g. `showCardArrow`, `cardArrowAtEnd`), with the engine diffing the position for the slide.
4. **Animate policy conflict — RESOLVED (2026-07-24), first option as proposed:** the engine obeys `animate` verbatim and has no empty-stage policy of its own. The VM signal alone suffices: rotation keeps the VM (`animateEntry` already false for the showing step → snap), every activity entry builds a fresh VM (first publish of a step carries `animateEntry = true` → entrance animates). That IS the spec's "an empty stage always animates", expressed as the VM's decision.
5. **Copy variants the config can't express:** legacy html-decodes welcome body1 only for sync-restore/custom-AI. Binder infers from `body2 == null`; a `TextConfig` html flag would be honest.
6. **The `ChangeBounds` card morph is the one choreography piece outside Animator ownership** — mid-morph skip settles content but not the bounds tween; a same-frame double render can strand a continuation (self-recovers on next render/skip; documented).
7. **Legacy card anchoring biases are internally inconsistent** — the original normalization FAILED its device check (2026-07-24): it inverted legacy's phone biases and sank the card to the screen bottom. Resolved by carrying the anchored bias as per-decoration data (`anchoredCardBiasPhone/Tablet`), matching legacy exactly; the one remaining normalization is the *unanchored* tablet bias (0.5 everywhere, where legacy mixes 0.5 and 0), which still wants design sign-off.
8. `ContentConfig.QuickSetup.isReinstallUser` is dead — legacy also never renders on it. Drop from the spec or wire it.
9. `ContentValueStore` keyed by config class assumes each stateful screen appears at most once per plan run (true today; documented).
10. Dropped legacy niceties, noted in reports: `interceptChildTouches` during entrance (RESTORED 2026-07-24 via the engine's `isAnimating` signal), preview tab-switch card ChangeBounds (binder can't reach the shared cardView — structurally correct per spec), left-wing snap `requestLayout()`, custom-AI intro snap on rotation (VM lacks the flag).
11. **Whole-stage choreography is the port's blind spot** (2026-07-24). The per-dialog port was faithful; nearly every device bug was legacy behavior owned by *no single dialog*: the card-root reveal, the arrow offset machinery, intro-view zeroing on snap, `interceptChildTouches`, the shared-XML visible-by-default welcome include, waiting for first layout before measuring fit. A future port of this kind should inventory legacy's screen-agnostic behaviors explicitly, not just its dialog branches.
12. **Animator-ownership sharp edges the spec should state** (2026-07-24): tracked animator lists must self-remove entries on natural completion — `end()` on a finished animator restarts it and re-fires `onAnimationStart` triggers (Lottie replays); `AnimatorSet.end()` is a no-op while unstarted, so the snap path must `start()` then `end()` entrance animators; a decoration's enter runs in parallel with its predecessor's exit — only the card re-anchor defers to exit completion.
13. **Stateful screens with self-toggling views must mirror optimistic state into the store at event time** (2026-07-24). The switch view flips itself before the event reaches the VM; if the store only records confirmed state, a corrective write of the unchanged value is deduped by the `MutableStateFlow` and never reaches the binder. Rule for the spec's state model: the store always reflects what the view currently shows.
14. **First-render timing is a fragment/engine contract** (2026-07-24): a retained VM's emission can arrive before the recreated view's first layout, and the fit veto measures the root's height. The first render must wait for layout (legacy wraps its no-predecessor renders in `doOnLayout`); subsequent renders are safe by construction.
15. Rotation during the live intro animation is a known unfixed gap (see the debugging-round section): the one-shot `PlayIntroAnimation` command is consumed and `hasPlayedIntroAnimation` never flips, so the recreated fragment renders nothing.

## Proposed improvements

- **Spec v7 edits:** adopt `BindScope`/`ContentInteraction`; nullable `primaryCta`; `showCardArrow` + `cardArrowAtEnd` on `DialogConfig` (gap 3); html flag on `TextConfig`; drop `isReinstallUser`; codify the VM-owned animate policy (gap 4, now implemented); state the animator-ownership rules (gap 12) and the optimistic-state-mirroring rule for stateful screens (gap 13); document the whole-stage engine responsibilities the port initially missed (gap 11: card reveal, arrow, fresh-stage include reset, intro-view settle, first-layout wait, `isAnimating`/touch interception).
- **Engine:** ~~expose an `isAnimating` signal so the fragment can gate `interceptChildTouches`~~ (done 2026-07-24); consider owning the morph via a trackable mechanism (or accepting the documented limitation).
- **Next steps to graduate beyond INTERNAL:** ~~phone smoke test of the new arm~~ (done 2026-07-24, ten fixes — see the debugging round); still open: tablet pass, flag-off parity run on device, Maestro release-blocker flows in both flag states, fix rotation-during-intro (gap 15), in-place `OnboardingDialogTitleView` compound-widget refactor, design sign-off narrowed to the unanchored-tablet bias normalization (gap 7), unify license-header years.

## Artifacts

- Plan: `temp-docs/plans/2026-07-23-config-driven-dialog-poc-plan.md`
- Per-task briefs/reports/review diffs + progress ledger: `.superpowers/sdd/` (gitignored)
