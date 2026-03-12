# Edge-to-Edge Migration Implementation Plan

**Goal:** Migrate all 113 Activities to proper edge-to-edge support before Android 16 removes the opt-out capability.

**Architecture:** The shared toolbar component (`include_default_toolbar.xml`) handles top insets automatically via `fitsSystemWindows="true"` + background color. Each Activity calls `enableEdgeToEdge()` and only adds bottom inset handling when content could be obscured by the navigation bar. Material Components handle their own insets automatically.

**Tech Stack:** AndroidX Activity (`enableEdgeToEdge`), AndroidX Core (`WindowInsetsCompat`, `ViewCompat`), Material Components

---

## Reference Documentation

- **Primary Guide**: https://developer.android.com/develop/ui/views/layout/edge-to-edge
- **Android 16 Changes**: https://developer.android.com/about/versions/16/behavior-changes-16#edge-to-edge
- **WebView Guide**: https://freedium-mirror.cfd/https://medium.com/androiddevelopers/make-webviews-edge-to-edge-a6ef319adfac
- **Insets Blog**: https://freedium-mirror.cfd/https://medium.com/androiddevelopers/insets-handling-tips-for-android-15s-edge-to-edge-enforcement-872774e8839b

---

## Architecture: How Insets Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                         SYSTEM                                   │
│  (provides WindowInsets: status bar, nav bar, IME, cutout)      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      enableEdgeToEdge()                          │
│  - Sets WindowCompat.setDecorFitsSystemWindows(window, false)   │
│  - Makes status bar transparent                                  │
│  - Makes nav bar transparent (or translucent for 3-button)      │
│  - Configures light/dark status bar icons based on theme        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│            ViewCompat.setOnApplyWindowInsetsListener (if needed) │
│  - Receives WindowInsetsCompat from system                       │
│  - Extracts insets: systemBars(), displayCutout(), ime()        │
│  - Returns CONSUMED or passes to children                        │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌───────────────────┐ ┌─────────────┐ ┌─────────────────┐
│   Root Layout     │ │  Toolbar    │ │  Bottom Bar     │
│  updatePadding(   │ │  padding or │ │  padding or     │
│    top, bottom)   │ │  fits...    │ │  margin         │
└───────────────────┘ └─────────────┘ └─────────────────┘
```

### Inset Types

| Type | Use Case | API |
|------|----------|-----|
| `systemBars()` | Status bar + navigation bar padding | `WindowInsetsCompat.Type.systemBars()` |
| `displayCutout()` | Notch/punch-hole avoidance | `WindowInsetsCompat.Type.displayCutout()` |
| `ime()` | Keyboard visibility | `WindowInsetsCompat.Type.ime()` |
| `systemGestures()` | Gesture navigation areas | `WindowInsetsCompat.Type.systemGestures()` |

### Inset Consumption

```kotlin
// CONSUMED - Don't pass insets to children (you handled everything)
ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
    v.updatePadding(top = insets.getInsets(Type.systemBars()).top)
    WindowInsetsCompat.CONSUMED
}

// PASS THROUGH - Let children also receive insets
ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
    v.updatePadding(top = insets.getInsets(Type.systemBars()).top)
    insets  // Return unchanged for children
}
```

### SDK 29 Backward-Compatible Dispatching

**Critical:** On Android 10 (API 29) and earlier, when you return `WindowInsetsCompat.CONSUMED`, insets are **NOT** dispatched to sibling views. This causes visual overlap issues where sibling views don't receive insets.

**Solution:** Call `ViewGroupCompat.installCompatInsetsDispatch()` on the root view **before** consuming insets.

```kotlin
import androidx.core.view.ViewGroupCompat

// Call BEFORE setting up the insets listener
ViewGroupCompat.installCompatInsetsDispatch(binding.root)

ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
    // ... handle insets ...
    WindowInsetsCompat.CONSUMED  // Now safe on SDK 29 and below
}
```

**When to use:**
- Any time you return `WindowInsetsCompat.CONSUMED` from an insets listener
- Pattern C activities (custom layouts returning CONSUMED)
- NOT needed when returning `windowInsets` unchanged (Pattern A)

**Requirement:** AndroidX Core 1.16.0 or higher

**Reference:** [Android Docs - Backward-compatible dispatching](https://developer.android.com/develop/ui/views/layout/edge-to-edge#backward-compatible-dispatching)

---

## API Reference

### enableEdgeToEdge()

```kotlin
import androidx.activity.enableEdgeToEdge

// Call AFTER super.onCreate(), BEFORE setContentView()
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_example)
}
```

**What it does:**
- Calls `WindowCompat.setDecorFitsSystemWindows(window, false)`
- Sets `window.statusBarColor = Color.TRANSPARENT`
- Sets `window.navigationBarColor = Color.TRANSPARENT` (or translucent scrim for 3-button nav)
- Configures `WindowInsetsControllerCompat.isAppearanceLightStatusBars` based on theme

### ViewCompat.setOnApplyWindowInsetsListener

```kotlin
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
    val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    )
    view.updatePadding(
        left = insets.left,
        top = insets.top,
        right = insets.right,
        bottom = insets.bottom
    )
    WindowInsetsCompat.CONSUMED
}
```

### ViewGroupCompat.installCompatInsetsDispatch() (SDK 29 Fix)

**Required** when returning `WindowInsetsCompat.CONSUMED` to ensure backward compatibility on Android 10 (API 29) and earlier.

```kotlin
import androidx.core.view.ViewGroupCompat

// Call BEFORE setting the insets listener, typically in setupEdgeToEdge()
ViewGroupCompat.installCompatInsetsDispatch(binding.root)
```

**Why:** On SDK 29 and below, `CONSUMED` prevents insets from being dispatched to sibling views. This API ensures siblings still receive insets.

### Handling Keyboard (IME)

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
    val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

    // Use the larger of system bars or IME for bottom padding
    view.updatePadding(
        top = systemBars.top,
        bottom = maxOf(systemBars.bottom, ime.bottom)
    )
    WindowInsetsCompat.CONSUMED
}
```

### RecyclerView Setup

```xml
<androidx.recyclerview.widget.RecyclerView
    android:id="@+id/recyclerView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:clipToPadding="false" />
```

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerView) { view, windowInsets ->
    val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    )
    view.updatePadding(
        top = insets.top,
        bottom = insets.bottom
    )
    WindowInsetsCompat.CONSUMED
}
```

### Dialog Edge-to-Edge

```kotlin
class MyDialogFragment : DialogFragment() {
    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }
}
```

### Full-Screen/Immersive Mode

```kotlin
val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

// Hide system bars
windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
windowInsetsController.systemBarsBehavior =
    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

// Show system bars
windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
```

---

## Troubleshooting Guide

### Problem: Content is cut off by status bar

**Symptoms:** Text/icons appear behind the status bar and are unreadable.

**Cause:** Insets not applied to root layout or toolbar.

**Fix:**
```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    view.updatePadding(top = insets.top)
    WindowInsetsCompat.CONSUMED
}
```

### Problem: Content is cut off by navigation bar

**Symptoms:** Bottom content (buttons, FABs) hidden behind nav bar.

**Cause:** Bottom insets not applied.

**Fix:**
```kotlin
val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
view.updatePadding(bottom = insets.bottom)
```

### Problem: Double padding on toolbar

**Symptoms:** Toolbar has excessive top spacing.

**Cause:** Both `fitsSystemWindows="true"` AND manual inset handling.

**Fix:** Use one approach, not both. Prefer manual inset handling:
```kotlin
// Remove from XML: android:fitsSystemWindows="true"
// Use only:
ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, windowInsets ->
    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    view.updatePadding(top = insets.top)
    windowInsets
}
```

### Problem: RecyclerView items don't scroll behind nav bar

**Symptoms:** List stops at nav bar instead of scrolling under it.

**Cause:** Missing `clipToPadding="false"`.

**Fix:**
```xml
<RecyclerView
    android:clipToPadding="false"
    android:paddingBottom="@dimen/nav_bar_height" />
```
Or better, apply padding dynamically via insets.

### Problem: Keyboard overlaps input fields

**Symptoms:** When keyboard opens, it covers the EditText.

**Cause:** IME insets not handled.

**Fix:**
```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
    val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
    view.updatePadding(bottom = maxOf(systemBars.bottom, ime.bottom))
    WindowInsetsCompat.CONSUMED
}
```

Also ensure `android:windowSoftInputMode="adjustResize"` in manifest.

### Problem: Status bar icons not visible (wrong color)

**Symptoms:** Light icons on light background or dark icons on dark background.

**Cause:** `isAppearanceLightStatusBars` not set correctly.

**Fix:**
```kotlin
val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
windowInsetsController.isAppearanceLightStatusBars = true  // Dark icons for light bg
windowInsetsController.isAppearanceLightNavigationBars = true
```

### Problem: Insets not dispatched to child views

**Symptoms:** Parent handles insets but children don't receive them.

**Cause:** Returning `WindowInsetsCompat.CONSUMED` stops propagation.

**Fix:** Return the insets unchanged to propagate:
```kotlin
ViewCompat.setOnApplyWindowInsetsListener(parent) { view, windowInsets ->
    // Handle parent insets...
    windowInsets  // Pass through to children
}
```

### Problem: Display cutout not avoided

**Symptoms:** Content appears under the notch/punch-hole.

**Cause:** Only using `systemBars()`, not `displayCutout()`.

**Fix:**
```kotlin
val insets = windowInsets.getInsets(
    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
)
```

### Problem: Edge-to-edge works on API 35+ but not older

**Symptoms:** Looks correct on Android 15, broken on older versions.

**Cause:** Not calling `enableEdgeToEdge()` (relies on automatic enforcement).

**Fix:** Always call `enableEdgeToEdge()` for consistent behavior across API levels.

---

## Pre-Requisite: Fix Shared Toolbar

**CRITICAL:** Before migrating any more Activities, the shared toolbar must be fixed.

### Task 0: Add Background to Shared Toolbar AppBarLayout ✅ COMPLETED

**Commit:** `b10f8002de` - edge-to-edge: Add background to shared toolbar AppBarLayout

**Files:**
- Modify: `android-design-system/design-system/src/main/res/layout/include_default_toolbar.xml`

**Problem:** The `AppBarLayout` has `fitsSystemWindows="true"` (which adds top padding) but no background color. This causes the status bar area to show the window background instead of the toolbar color.

**Step 1: Add background attribute to AppBarLayout**

```xml
<com.google.android.material.appbar.AppBarLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/appBarLayout"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/daxColorToolbar"
    android:fitsSystemWindows="true"
    android:outlineProvider="none"
    android:theme="@style/Widget.DuckDuckGo.ToolbarTheme">
```

**Step 2: Build and verify**

Run: `./gradlew :app:assembleInternalDebug`

**Step 3: Visual verification**

Launch any activity with the shared toolbar and verify the toolbar color extends behind the status bar.

**Step 4: Commit**

```bash
git add android-design-system/design-system/src/main/res/layout/include_default_toolbar.xml
git commit -m "edge-to-edge: Add background to shared toolbar AppBarLayout

The AppBarLayout had fitsSystemWindows=true which adds top padding,
but no background color was set. This caused the status bar area to
show the window background instead of the toolbar color.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Simplified Migration Pattern

### When to add inset listeners

| Screen Type | Pattern | What's Needed |
|-------------|---------|---------------|
| Basic screen with shared toolbar, content doesn't reach bottom | B | Just `enableEdgeToEdge()` - toolbar handles top insets |
| Shared toolbar + scrollable content reaching bottom | A | `enableEdgeToEdge()` + bottom inset listener on scroll view |
| Shared toolbar + bottom-positioned elements (FAB, buttons) | A | `enableEdgeToEdge()` + bottom margin handling |
| Input fields | A/C | `enableEdgeToEdge()` + IME inset handling |
| Custom layouts without shared toolbar | C | Full inset handling (top + bottom) |
| Activity hosts full-screen fragments | D | Activity: just `enableEdgeToEdge()`. Fragments: handle their own bottom insets |

### Decision flowchart

```
Does the Activity use include_default_toolbar.xml?
├── YES → Top insets handled automatically
│         └── Does content reach bottom edge?
│             ├── YES → Pattern A: Add bottom inset listener
│             └── NO → Pattern B: Just enableEdgeToEdge()
└── NO → Does the Activity host full-screen fragments?
          ├── YES → Pattern D: Activity just enableEdgeToEdge(), fragments handle their own insets
          └── NO → Pattern C: Handle both top and bottom insets manually
```

---

## Strategy

### Approach: Risk-First Migration

Start with lower-risk screens to establish patterns, then tackle high-risk screens (BrowserActivity) last.

### Commit Strategy: Atomic, Cherry-Pickable

- Each screen gets its own commit
- Commits can be cherry-picked into separate PRs for incremental review
- Code duplication is acceptable initially; utilities extracted at the end

### Opt-Out Removal: Last

Keep `windowOptOutEdgeToEdgeEnforcement` until all screens are migrated, then remove it as the final step.

### Testing: Before/After Screenshots

**REQUIRED DEVICE:** All screenshots MUST be taken on **Pixel 9 API 36 emulator (`emulator-5554`)**.

- Verify device before capturing any screenshots
- Use mobile-mcp to capture before/after screenshots for each screen
- Consistency across all screenshots is critical for documentation

---

## Implementation Pattern

### Pattern A: Screen with Shared Toolbar (Most Common)

For screens using `include_default_toolbar.xml` where content could reach the bottom:

```kotlin
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_example)
    setupToolbar(binding.includeToolbar.toolbar)

    // Only add if content could be obscured by nav bar
    ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { view, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        view.updatePadding(bottom = insets.bottom)
        windowInsets  // Pass through to other views
    }
}
```

### Pattern B: Screen with Shared Toolbar, No Bottom Content

For screens using `include_default_toolbar.xml` where content doesn't reach the bottom:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_example)
    setupToolbar(binding.includeToolbar.toolbar)
    // That's it! Toolbar handles top insets, content doesn't reach bottom
}
```

### Pattern C: Custom Layout Without Shared Toolbar

For screens with custom layouts that need full inset handling:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_example)
    setupEdgeToEdge()
}

private fun setupEdgeToEdge() {
    // REQUIRED: Enable backward-compatible inset dispatching for SDK 29 and below
    ViewGroupCompat.installCompatInsetsDispatch(binding.root)

    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        view.updatePadding(top = insets.top, bottom = insets.bottom)
        WindowInsetsCompat.CONSUMED
    }
}
```

**Important:** The `ViewGroupCompat.installCompatInsetsDispatch()` call is **required** because Pattern C returns `CONSUMED`. Without it, sibling views won't receive insets on Android 10 (SDK 29) and below.

### Pattern D: Fragment-Hosted Activities (Fragment Handles Insets)

For activities that host full-screen fragments in a `FragmentContainerView`, the **fragments** handle their own insets rather than the activity.

**Activity** (minimal - just enables edge-to-edge):

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()  // Enable edge-to-edge
    setContentView(binding.root)
    // NO inset handling here - fragments handle their own insets
}
```

**Fragment** (handles bottom insets for its content):

```kotlin
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import android.view.ViewGroup.MarginLayoutParams

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupEdgeToEdgeInsets()
}

private fun setupEdgeToEdgeInsets() {
    // Apply bottom insets to the bottom-most interactive element
    ViewCompat.setOnApplyWindowInsetsListener(binding.footerButton) { view, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        view.updateLayoutParams<MarginLayoutParams> {
            bottomMargin = resources.getDimensionPixelSize(R.dimen.keyline_4) + insets.bottom
        }
        windowInsets  // Pass through to other views
    }
}
```

**When to use Pattern D:**
- Activity uses `FragmentContainerView` that fills the screen (no shared toolbar in activity layout)
- Fragments have bottom-positioned content (buttons, links, text) that could be obscured by navigation bar
- Each fragment manages its own layout and insets independently

**Key differences from Pattern A:**
- Activity does NOT apply insets to FragmentContainerView (this would shrink available space)
- Each fragment applies insets to its own bottom elements
- Use `updateLayoutParams<MarginLayoutParams>` for fixed-position elements (not padding)

**Why fragments handle insets:**
- Applying padding/margin to FragmentContainerView reduces available space for fragment content
- Different fragments may have different bottom elements requiring different inset handling
- Fragment lifecycle ensures insets are applied when view is created

#### Pattern D+W: Fragment-Hosted WebView

When a Pattern D fragment contains a WebView (e.g., `DuckChatWebViewFragment` in `BrowserActivity`), combine Pattern D's fragment-level inset handling with Pattern W's IME inclusion. The fragment applies bottom insets — including `ime()` — as padding on the WebView. The shared toolbar's `fitsSystemWindows="true"` handles top insets automatically.

```kotlin
private fun setupEdgeToEdge() {
    ViewCompat.setOnApplyWindowInsetsListener(binding.simpleWebview) { view, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.ime(),
        )
        view.updatePadding(bottom = insets.bottom)
        windowInsets
    }
}
```

### Component-Specific Handling

| Component                                  | Approach |
|--------------------------------------------|----------|
| Shared toolbar (`include_default_toolbar`) | Automatic - `fitsSystemWindows="true"` on AppBarLayout handles top insets |
| Custom `AppBarLayout`                      | Manual padding via `updatePadding(top = insets.top)` - see below |
| `BottomNavigationView`, `BottomAppBar`     | Automatic - handles insets itself |
| `RecyclerView`                             | Bottom padding + `clipToPadding="false"` in XML |
| FABs, fixed buttons                        | Bottom margins via `updateLayoutParams<MarginLayoutParams>` |
| Dialogs                                    | Call `WindowCompat.setDecorFitsSystemWindows(window, false)` in `onStart()` |

### AppBarLayout Inset Handling

**Important:** Unlike other Material Components, `AppBarLayout` does **NOT** automatically handle insets. You must choose ONE approach:

#### Option 1: Use `fitsSystemWindows="true"` (Pattern A - Shared Toolbar)

```xml
<com.google.android.material.appbar.AppBarLayout
    android:fitsSystemWindows="true"
    android:background="?attr/daxColorToolbar"
    ...>
```

- ✅ Used by `include_default_toolbar.xml` (our shared toolbar)
- ✅ AppBarLayout automatically applies top insets as padding
- ✅ Your code only needs to handle bottom insets
- ⚠️ **Do NOT also apply manual top padding** - this causes double padding

#### Option 2: Manual Padding (Pattern C - Custom Toolbar)

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
    val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
    )
    binding.appBar.updatePadding(top = insets.top)  // Manual top padding
    binding.scrollView.updatePadding(bottom = insets.bottom)
    WindowInsetsCompat.CONSUMED
}
```

- ✅ Used when you have a custom AppBarLayout (not using shared toolbar)
- ✅ Full control over inset handling
- ⚠️ **Do NOT add `fitsSystemWindows="true"`** to the AppBarLayout - this causes double padding
- ⚠️ **Must also add background** to AppBarLayout: `android:background="?attr/daxColorToolbar"`

#### Common Mistakes to Avoid

| Mistake | Symptom | Fix |
|---------|---------|-----|
| Using BOTH `fitsSystemWindows` AND manual `updatePadding()` | Double top padding (excessive space) | Use only ONE approach |
| Forgetting `android:background` on custom AppBarLayout | Content visible behind status bar | Add `android:background="?attr/daxColorToolbar"` |
| Hardcoded `layout_marginTop="64dp"` on content | Fixed margin doesn't adapt to device | Use constraints: `app:layout_constraintTop_toBottomOf="@id/appBar"` |
| Missing `clipToPadding="false"` on ScrollView | Content can't scroll behind nav bar | Add `android:clipToPadding="false"` |
| Using `updatePadding()` on fixed-height LinearLayout toolbar | Toolbar content compressed/invisible | Use `updateLayoutParams<MarginLayoutParams> { topMargin = insets.top }` instead |

**Reference:** [Android Docs - Material Components](https://developer.android.com/develop/ui/views/layout/edge-to-edge#material-components)

### Key Guidelines

- **Top insets:** Handled by shared toolbar for most screens
- **Bottom insets:** Only add listener if content could be obscured
- Use **margins** for non-scrolling views, **padding** for scrolling content
- Include `displayCutout()` alongside `systemBars()` for notch devices
- Return `windowInsets` (not `CONSUMED`) to let other views also receive insets

---

## WebView Edge-to-Edge Handling

**Reference:** [Make WebViews Edge-to-Edge](https://freedium-mirror.cfd/https://medium.com/androiddevelopers/make-webviews-edge-to-edge-a6ef319adfac)

### Pattern W: WebView Screens (External Content)

For WebViews loading external content (app doesn't control the HTML), apply insets to the WebView container:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(binding.root)
    setupToolbar(binding.includeToolbar.toolbar)
    setupEdgeToEdge()
}

private fun setupEdgeToEdge() {
    ViewCompat.setOnApplyWindowInsetsListener(binding.webView) { view, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.ime(),  // CRITICAL: Always include IME for WebViews
        )
        view.updatePadding(bottom = insets.bottom)
        windowInsets
    }
}
```

### Critical: Always Include IME Insets

> "Always include IME insets alongside system bars and display cutout. Omitting keyboard insets causes content to be obscured when input fields are focused, preventing user interaction with hidden elements."

WebViews often contain forms and input fields. Without IME inset handling:
- Keyboard appears and covers input fields
- Users cannot see what they're typing
- Submit buttons may be hidden behind the keyboard

### WebView-Specific Considerations

| Consideration | Approach |
|---------------|----------|
| External content | Apply padding to WebView, include `ime()` insets |
| Owned HTML content | Option 1: Container padding (same as external), Option 2: JavaScript injection with CSS safe-area variables |
| Input fields in WebView | **Must** include `WindowInsetsCompat.Type.ime()` |
| Window background | Consider matching `android:windowBackground` to WebView content color |

### Testing WebView Edge-to-Edge

1. Load a page with input fields (e.g., a search form or login page)
2. Tap an input field to show keyboard
3. Verify: Content scrolls/adjusts so input field remains visible above keyboard
4. Verify: Bottom of WebView content is not cut off by navigation bar

---

## Activity Migration Checklist

**Total Activities: 113** (excluding base classes and test activities)

**IMPORTANT:** Complete Task 0 (Fix Shared Toolbar) before continuing with Activity migrations.

👉 **See [Activity Checklist](./2026-01-16-edge-to-edge-activity-checklist.md) for the full migration tracking list.**

---

## Immediate Fixes Required

### Task 1: Simplify Already-Migrated Activities ✅ COMPLETED

These activities were migrated before the toolbar fix. They were verified and already had the correct implementation.

**Files verified:**
- ✅ `app/src/main/java/com/duckduckgo/app/about/AboutDuckDuckGoActivity.kt` - Already correct (only bottom padding, returns windowInsets)
- ✅ `app/src/main/java/com/duckduckgo/app/generalsettings/GeneralSettingsActivity.kt` - Already correct (only bottom padding, returns windowInsets)

**What was verified:**

1. ✅ The `setupEdgeToEdge()` function only applies bottom padding (not top)
2. ✅ Top insets are handled by the shared toolbar
3. ✅ `windowInsets` is returned (not `CONSUMED`) to let other views receive insets

**Example of correct implementation:**

```kotlin
private fun setupEdgeToEdge() {
    ViewCompat.setOnApplyWindowInsetsListener(binding.includeContent.root) { view, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        view.updatePadding(bottom = insets.bottom)
        windowInsets  // Return unchanged, not CONSUMED
    }
}
```

---

## Final Cleanup Commits

After all Activities are migrated:

- [ ] **DELETE `app/src/debug/AndroidManifest.xml`** - Temporary file for direct activity launching during migration
- [ ] Remove `windowOptOutEdgeToEdgeEnforcement` from `design-system/src/main/res/values-v35/design-system-theming.xml`
- [ ] Clean up deprecated `systemUiVisibility` in `DuckDuckGoActivity.toggleFullScreen()`
- [ ] Clean up deprecated `systemUiVisibility` in `BrowserTabFragment`
- [ ] Clean up `isFullScreen()` and `isImmersiveModeEnabled()` in `ActivityExtensions.kt`
- [ ] Re-enable LeakCanary in `app/build.gradle`

---

## Per-Screen Workflow

### Step 1: Take BEFORE screenshot

**IMPORTANT:** Always capture a before screenshot for verification and documentation.

**REQUIRED DEVICE:** Pixel 9 API 36 emulator (`emulator-5554`)

**DIRECT LAUNCH:** Use adb to launch activities directly (much faster than navigation):
```bash
adb -s emulator-5554 shell am start -n com.duckduckgo.mobile.android.debug/<full.activity.ClassName>
# Example:
adb -s emulator-5554 shell am start -n com.duckduckgo.mobile.android.debug/com.duckduckgo.app.accessibility.AccessibilityActivity
```

Using mobile-mcp on emulator:
1. **Launch activity directly** using the adb command above
2. Save screenshot: `docs/plans/screenshots/<activity-name>-before.png`
   - Use kebab-case for filename (e.g., `new-tab-settings-before.png`)

### Step 2: Check if screen uses shared toolbar

```bash
# Look for includeToolbar in the layout file
grep -l "includeToolbar" app/src/main/res/layout/activity_*.xml
```

### Step 3: Determine which pattern to use

- **Uses shared toolbar + scrollable content reaching bottom?** → Pattern A (bottom insets only)
- **Uses shared toolbar + content doesn't reach bottom?** → Pattern B (just enableEdgeToEdge)
- **Custom layout without shared toolbar?** → Pattern C (full inset handling)

### Step 4: Add enableEdgeToEdge()

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()  // Add AFTER super.onCreate(), BEFORE setContentView()
    setContentView(R.layout.activity_example)
    // ... rest of onCreate
}
```

### Step 5: Add bottom inset handling (only if needed)

Only if content could be obscured by navigation bar:

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { view, windowInsets ->
    val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
    )
    view.updatePadding(bottom = insets.bottom)
    windowInsets
}
```

### Step 6: Build and install

```bash
./gradlew :app:installInternalDebug
```

Wait for build and installation to complete.

### Step 7: Take AFTER screenshot

**IMPORTANT:** Always capture an after screenshot for verification.

**REQUIRED DEVICE:** Pixel 9 API 36 emulator (`emulator-5554`)

Using mobile-mcp on emulator:
1. **Launch activity directly** using same adb command from Step 1
2. Save screenshot: `docs/plans/screenshots/<activity-name>-after.png`
   - Use same kebab-case filename as before (e.g., `new-tab-settings-after.png`)

### Step 8: Visual verification

Compare before/after screenshots and verify:
- ✅ Toolbar color extends behind status bar (no light/gray strip)
- ✅ Status bar icons are visible and readable
- ✅ Bottom content not obscured by nav bar
- ✅ Content scrolls properly (if scrollable)
- ✅ No double padding on toolbar

### Step 9: Commit

**IMPORTANT:** Include both screenshots in the commit.

```bash
git add <kotlin-files> <xml-files> docs/plans/screenshots/<activity-name>-before.png docs/plans/screenshots/<activity-name>-after.png
git commit -m "edge-to-edge: Migrate <ActivityName>

- Enable edge-to-edge display
- [If applicable: Handle bottom insets for scrollable content]

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

### Step 10: Update plan

Mark the activity as completed in this plan document:

1. Find the activity in the checklist
2. Change `- [ ]` to `- [x]`
3. Add completion marker: `✅ **COMPLETED** (Commit: <commit-hash>)`
4. Commit the plan update:

```bash
git add docs/plans/2026-01-16-edge-to-edge-migration-design.md
git commit -m "docs: Mark <ActivityName> as completed

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Verification Checklist

For each screen, verify:

| Check | What to Look For |
|-------|------------------|
| Status bar | Transparent, content visible behind, icons readable |
| Navigation bar | Transparent (or translucent for 3-button nav) |
| Top content | Not obscured, proper padding below status bar |
| Bottom content | Not obscured, proper padding above nav bar |
| Scrollable content | Scrolls behind system bars, `clipToPadding="false"` working |
| Display cutout | Content avoids notch/punch-hole on applicable devices |
| Keyboard (if inputs) | IME doesn't overlap input fields |
| Light/dark theme | Test both, ensure bar icons have proper contrast |

### Quick Smoke Test

1. Launch screen
2. Scroll if scrollable (content should go behind bars)
3. Rotate to landscape (if supported)
4. Tap any input fields (keyboard shouldn't overlap)
5. Compare before/after screenshots