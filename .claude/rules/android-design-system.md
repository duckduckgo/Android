---
description: Android Design System components and conventions — buttons, text, inputs, list items, dialogs, bottom sheets, colors, spacing, icons. Read before writing or changing any UI (Kotlin or XML).
---
# Android Design System (ADS)

The design system lives in `android-design-system/design-system/`; View components are under
`com.duckduckgo.common.ui.view.*`. Use ADS components instead of raw Android/Material widgets.

This rule is the component index and the constraints. For a component's API, read its class — the
signatures are not repeated here.

---

## What breaks the build

Lint fails the build on all of these (`lint-rules/src/main/java/com/duckduckgo/lint/ui/`):

- Raw `Button`, `TextView`, `SwitchView` in XML
- `style=` on any ADS component; a custom style not prefixed `Widget.DuckDuckGo.`
- `@color/` references in XML — use `?attr/daxColor*` so both themes work
- A plain `View` used as a divider
- `AlertDialog` / `MaterialAlertDialog`; a raw `BottomSheetDialog` without the ADS style
- `background_skeleton` set as the background of a `SkeletonView`
- On `DaxButton*`: `android:textStyle`, `android:textColor`, `android:textAppearance`,
  `android:textAllCaps`, `android:tint`, and `android:layout_height` unless `wrap_content`
- On `DaxTextView`: `android:textAppearance`, `android:textAllCaps`, `android:tint`,
  `android:textSize`, `android:textStyle`, `android:fontFamily`

---

## Components

Pick by intent, never by style.

### Buttons — `view.button.*`

`DaxButtonPrimary` · `DaxButtonSecondary` · `DaxButtonGhost` · `DaxButtonGhostAlt` ·
`DaxButtonDestructive` · `DaxButtonDestructiveSecondary` · `DaxButtonGhostDestructive`

`GhostAlt` is a ghost button placed on an alternate (non-default) background. Of the destructive
variants, pick by emphasis: `Destructive` (filled) → `DestructiveSecondary` → `GhostDestructive`.

Accepted attributes: `android:text`, `android:enabled`, `app:icon`,
`app:daxButtonSize="large|small"` (default `small` — note the `dax` prefix).

Between stacked buttons use `@dimen/keyline_1` (4dp), not 16dp: buttons carry 6dp insets top and
bottom, so 4+6+6 gives the intended 16dp of visual spacing.

### Text — `view.text.DaxTextView`

One component for all text. Pick `app:typography` by semantic role; never set size or weight.

`title` · `h1` · `h2` · `h3` · `h4` · `h5` · `body1` (default) · `body1_bold` · `body1_mono` ·
`body2` · `body2_bold` · `button` · `caption` · `caption_allCaps` · `onboarding_title` ·
`onboarding_body`

`app:textType`: `primary` (default) · `secondary`

### Text input — `view.text.DaxTextInput`

`app:type`: `single_line` · `multi_line` · `password` · `form_mode` · `ip_address` · `url`.
`app:editable` toggles editability.

Set `error` for invalid user input only. Network and other external failures go in a Snackbar, not
the field.

### List items — `view.listitem.*`

All extend `DaxListItem`, which carries the shared primary/secondary text, leading/trailing icon,
and switch API. Pick by the content you have:

| Class | Use for |
|---|---|
| `OneLineListItem` | primary text only |
| `TwoLineListItem` | primary + secondary text |
| `SectionHeaderListItem` | section title above a group (`app:showOverflowMenu`) |
| `SettingsListItem` | settings row carrying a `StatusIndicatorView` |
| `RadioListItem` | single-selection row |
| `BookmarksListItem` | bookmark / favourite row |
| `DaxGridItem` | grid cell (favourites) |

`app:leadingIconImageBackground`: `none` · `circular` · `rounded`.
Leading-icon sizes come from `IconSize`: Small, Medium, Large, ExtraLarge.

### Dialogs — `view.dialog.*`

Builders taking an `EventListener` for button callbacks: `TextAlertDialogBuilder` (standard alert),
`StackedAlertDialogBuilder` (three or more vertically stacked options), `RadioListAlertDialogBuilder`
(single selection).

### Bottom sheets

`ActionBottomSheetDialog` and `PromoBottomSheetDialog` cover the two standard patterns. A custom
`BottomSheetDialog` must be constructed with the ADS style
(`com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_FireDialog`); a `BottomSheetDialogFragment`
inherits it from the theme.

### Other components

| Component | Notes |
|---|---|
| `view.DaxSwitch` | `quietlySetIsChecked(state, listener)` changes state without firing the listener |
| `view.divider.HorizontalDivider` / `VerticalDivider` | `app:fullWidth`, `app:defaultPadding` |
| `view.MenuItemView` | browser menu entries |
| `view.PopupMenuItemView` | popup / context menus; `app:primaryTextType="destructive"` |
| `view.SkeletonView` | loading placeholder — must be wrapped in `com.facebook.shimmer.ShimmerFrameLayout` |
| `view.notifyme.NotifyMeView` | self-hiding prompt, shown only when system notifications are disabled |

---

## Colors

Theme attributes, never `@color/`:

`?attr/daxColorBackground` (screen) · `?attr/daxColorSurface` (toolbars, navigation, dialogs) ·
`?attr/daxColorContainer` (object containers, e.g. favicon background) · `?attr/daxColorPrimaryText` ·
`?attr/daxColorSecondaryText` · `?attr/daxColorTertiaryText` (hints, placeholders) ·
`?attr/daxColorPrimaryIcon` · `?attr/daxColorSecondaryIcon` · `?attr/daxColorAccentBlue` (accent,
CTAs) · `?attr/daxColorDestructive` · `?attr/daxColorLines` (dividers)

In Kotlin: `context.getColorFromAttr(R.attr.daxColorPrimaryText)`.
In a vector drawable that must follow the theme: `android:fillColor="?attr/daxColorPrimaryIcon"`.

Adding an icon the project doesn't have yet: read `.claude/docs/icons.md` — it must be fetched from the
internal Icons repository, never invented.

---

## Spacing

Use keylines rather than arbitrary dp. The values are here so a dp figure from a design spec can be
mapped back to the right resource:

| Resource | Value |
|---|---|
| `@dimen/keyline_0` | 2dp |
| `@dimen/keyline_1` | 4dp — between buttons |
| `@dimen/keyline_2` | 8dp |
| `@dimen/keyline_3` | 12dp |
| `@dimen/keyline_4` | 16dp — screen edge → content |
| `@dimen/keyline_5` | 24dp — dialog edge → content |
| `@dimen/keyline_6` | 32dp |
| `@dimen/keyline_7` | 48dp |

Defined in `design-system-dimensions.xml`.
