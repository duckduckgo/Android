# `lateinit var` initialization hazards

Reading a `lateinit var` before it has been assigned throws `UninitializedPropertyAccessException` at runtime. There is no compile-time check — only review and runtime crashes catch it. When writing or reviewing code that declares or reads a `lateinit var`, walk through these cases.

## View subclasses with `@Inject lateinit var`

A View annotated with `@InjectWith(ViewScope::class)` injects its `@Inject lateinit var` members inside `onAttachedToWindow()`, via `AndroidSupportInjection.inject(this)`. The fields are uninitialized between construction and attach.

The trap: a parent or manager class can call a public/internal/override method on the view immediately after `addView(…)` — before the view tree is attached. If that method reads an `@Inject lateinit var` directly, it crashes.

When reviewing a View subclass, treat every `public` / `internal` / `override` method that reads an `@Inject lateinit var` as suspect. It is safe only if at least one is true:

- the read happens in `onAttachedToWindow()` (after `inject(this)`) or `onDetachedFromWindow()`
- the read is inside `doOnAttach { … }` (from `androidx.core.view`)
- the read is inside a lambda that only fires post-attach (click listener, flow collector that is `launchIn(…)` started inside `onAttachedToWindow`, `post { … }`, etc.)
- the read is guarded by `::propertyName.isInitialized`
- the method is `private` and every call site is one of the above (verify by checking each caller — do not assume)

When in doubt, the standard fix is to wrap the body in `doOnAttach { … }`. It runs immediately if the view is already attached, and defers otherwise — same behaviour as before in the common case, no crash in the edge case.

Real-world bugs caused by missing this:

- **PR #8424** — `NativeInputModeWidget.configure()` / `configureContextual()` / `setWidgetPosition()` read `viewModel` (a `@Inject lateinit var`) directly; `RealNativeInputManager.attachWidget()` called them right after `addView()`, before attach. Fix: wrap each body in `doOnAttach { … }`.
- **PR #8461** — `DuckDuckGoWebView.setContentAllowsSwipeToRefresh()` read `browserUiLockFeature` unconditionally. Fix: `if (!allowed || (::browserUiLockFeature.isInitialized && browserUiLockFeature.self().isEnabled()))`.
- **PR #8577** — `NativeInputModeWidget.applyDefaultTogglePosition()` read injected state directly. Fix: wrap in `doOnAttach { … }`.

## Construction-time reads in any class

Independent of class type — even a plain Kotlin class with no DI involvement — a `lateinit var` is also uninitialized during the primary constructor's run, which includes:

- property initializers (`val x = lateinitProp.foo()`)
- `init { … }` blocks
- secondary constructor bodies that delegate to the primary

If a property initializer or init block reads a `lateinit var` before any earlier init block or initializer has assigned it, that read crashes. The order is **source declaration order**, not visual placement of the `lateinit var` declaration.

```kotlin
class Foo {
    lateinit var bar: String
    val length: Int = bar.length          // ❌ crash — bar not assigned yet
    init { bar = "x" }                    // assignment runs after the val above
}
```

```kotlin
class Foo {
    lateinit var bar: String
    init { bar = "x" }                    // assigns first
    val length: Int = bar.length          // ✓ safe — bar already assigned
}
```

For Fragments, Activities, Services, ViewModels and other framework-managed classes, the construction-time hazard still applies even though their *regular* method bodies are safe (those run post-injection in `onAttach` / `onCreate`). Concretely:

- Fragment/Activity: an `init { … }` or property initializer reading an `@Inject lateinit var` crashes — injection has not run yet. By the time `onCreate` / `onAttach` runs, the read is safe.
- ViewModel with `@Inject constructor(…)`: prefer constructor-injected `val` for required deps. `lateinit var` on a ViewModel is a smell — if you see it, ask why it isn't a constructor parameter.

The universal escape hatch — `if (::propertyName.isInitialized) { … }` — works for any class type. Reach for it only when the access really is optional. If the property is *required*, fix the initialization order instead, don't paper over it with the guard.
