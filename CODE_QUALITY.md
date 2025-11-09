# Code Quality Guidelines

This document outlines code quality standards and best practices for the DuckDuckGo Android app.

## Table of Contents
- [Null Safety](#null-safety)
- [Dependency Injection](#dependency-injection)
- [State Management](#state-management)
- [Architecture Patterns](#architecture-patterns)
- [Performance Best Practices](#performance-best-practices)
- [Security Guidelines](#security-guidelines)

## Null Safety

### Avoid Non-null Assertions (`!!`)

**Problem:**
The codebase currently contains numerous non-null assertions (`!!`) which can cause runtime crashes if assumptions are violated.

**Bad:**
```kotlin
val url = tab.url!!.toUri()  // Crashes if url is null
val adapter = Adapters.ruleListAdapter.fromJson(value)!!
```

**Good:**
```kotlin
// Option 1: Safe call with elvis operator
val url = tab.url?.toUri() ?: Uri.EMPTY

// Option 2: Let expression for null handling
tab.url?.let { urlString ->
    val uri = Uri.parse(urlString)
    // Use uri safely here
}

// Option 3: Early return
val urlString = tab.url ?: return
val url = Uri.parse(urlString)

// Option 4: Explicit null check
if (tab.url != null) {
    val url = tab.url.toUri()
}
```

### Safe Collection Access

**Bad:**
```kotlin
val item = optional.get()  // May throw NoSuchElementException
val first = list[0]        // May throw IndexOutOfBoundsException
```

**Good:**
```kotlin
val item = optional.getOrNull()
val item = optional.getOrElse { defaultValue }
val first = list.firstOrNull()
val first = list.getOrNull(0)
```

## Dependency Injection

### Constructor Injection Best Practices

**Problem:**
Some classes have constructors with 20+ parameters, violating the Single Responsibility Principle.

**Bad:**
```kotlin
class BrowserWebViewClient @Inject constructor(
    private val param1: Dep1,
    private val param2: Dep2,
    // ... 24 more parameters
    private val param26: Dep26
)
```

**Good:**
```kotlin
// Group related dependencies
data class WebViewSecurityDependencies @Inject constructor(
    val httpAuthStore: WebViewHttpAuthStore,
    val certificateStore: TrustedCertificateStore,
    val sslErrorHandler: SslErrorHandler
)

data class WebViewNavigationDependencies @Inject constructor(
    val urlHandler: UrlHandler,
    val requestHandler: RequestHandler,
    val redirectHandler: RedirectHandler
)

class BrowserWebViewClient @Inject constructor(
    private val securityDeps: WebViewSecurityDependencies,
    private val navigationDeps: WebViewNavigationDependencies,
    private val tracker: TrackerDetector
)
```

### Avoid `@Inject` on Mutable Properties

**Bad:**
```kotlin
class MyViewModel @Inject constructor() {
    @Inject lateinit var repository: Repository
}
```

**Good:**
```kotlin
class MyViewModel @Inject constructor(
    private val repository: Repository
)
```

## State Management

### Prefer Immutability

**Bad:**
```kotlin
class TabManager {
    var currentTab: Tab? = null
    var tabList: MutableList<Tab> = mutableListOf()
}
```

**Good:**
```kotlin
class TabManager {
    private val _currentTab = MutableStateFlow<Tab?>(null)
    val currentTab: StateFlow<Tab?> = _currentTab.asStateFlow()

    private val _tabList = MutableStateFlow<List<Tab>>(emptyList())
    val tabList: StateFlow<List<Tab>> = _tabList.asStateFlow()

    fun addTab(tab: Tab) {
        _tabList.update { it + tab }
    }
}
```

### Use StateFlow/LiveData for Observable State

**Bad:**
```kotlin
var isLoading: Boolean = false  // No observers notified
```

**Good:**
```kotlin
private val _isLoading = MutableStateFlow(false)
val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

// Or with LiveData
private val _isLoading = MutableLiveData(false)
val isLoading: LiveData<Boolean> = _isLoading
```

## Architecture Patterns

### Reactive Programming

**Current State:**
The app uses both RxJava 2 and Kotlin Coroutines/Flow. We are gradually migrating to coroutines.

**Preferred (Coroutines):**
```kotlin
class UserRepository @Inject constructor(
    private val api: UserApi
) {
    fun getUsers(): Flow<List<User>> = flow {
        val users = api.getUsers()
        emit(users)
    }.flowOn(Dispatchers.IO)
}

class UserViewModel @Inject constructor(
    private val repository: UserRepository
) : ViewModel() {
    val users = repository.getUsers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
```

**Legacy (RxJava - Avoid in New Code):**
```kotlin
class LegacyRepository {
    fun getUsers(): Observable<List<User>> {
        return api.getUsers()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }
}
```

### Data Serialization

**Deprecated (Java Serializable):**
```kotlin
// AVOID: Security vulnerabilities and poor performance
data class FireAnimation(...) : Serializable
```

**Preferred (Parcelable):**
```kotlin
@Parcelize
data class FireAnimation(
    val duration: Int,
    val type: AnimationType
) : Parcelable
```

**Alternative (Moshi for Network/Storage):**
```kotlin
@JsonClass(generateAdapter = true)
data class UserResponse(
    @Json(name = "user_id") val userId: String,
    @Json(name = "username") val username: String
)
```

## Performance Best Practices

### Lazy Initialization

Use `lazy` for expensive objects that might not be needed:

```kotlin
class MyActivity : AppCompatActivity() {
    // Good: Only initialized when first accessed
    private val expensiveAdapter by lazy {
        ComplexAdapter(this, database, formatter)
    }

    // Avoid for simple objects
    private val simpleString by lazy { "Hello" }  // Unnecessary overhead
}
```

### ProGuard/R8 Optimization

The app now includes comprehensive ProGuard rules. When adding new dependencies:

1. Check if they require custom ProGuard rules
2. Add rules to `app/proguard-rules.pro`
3. Test release builds thoroughly

### Resource Management

**Good:**
```kotlin
suspend fun processFile(file: File): Result<Data> = withContext(Dispatchers.IO) {
    file.inputStream().use { stream ->
        // Stream automatically closed
        processStream(stream)
    }
}
```

## Security Guidelines

### Sensitive Data Storage

**Bad:**
```kotlin
// NEVER store sensitive data in plain SharedPreferences
sharedPrefs.edit().putString("password", password).apply()
```

**Good:**
```kotlin
// Use EncryptedSharedPreferences
val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
encryptedPrefs.edit().putString("password", password).apply()
```

### WebView Security

When adding JavaScript interfaces:

```kotlin
// Always annotate with @JavascriptInterface
class MyJavascriptInterface {
    @JavascriptInterface
    fun performAction(data: String) {
        // Validate and sanitize input
        if (!isValidInput(data)) return

        // Process safely
    }
}

// Enable only necessary features
webView.settings.apply {
    javaScriptEnabled = true
    allowFileAccess = false  // Unless specifically needed
    allowContentAccess = false
    allowFileAccessFromFileURLs = false
    allowUniversalAccessFromFileURLs = false
}
```

### Network Security

```kotlin
// Use HTTPS for all network requests
// Implement certificate pinning for critical endpoints

val certificatePinner = CertificatePinner.Builder()
    .add("api.duckduckgo.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .build()

val client = OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .build()
```

## Code Review Checklist

Before submitting a PR, ensure:

- [ ] No new `!!` operators (use safe alternatives)
- [ ] No usage of Java `Serializable` (use `Parcelable` or Moshi)
- [ ] New code uses Coroutines/Flow (not RxJava)
- [ ] Sensitive data uses encrypted storage
- [ ] All new dependencies have ProGuard rules if needed
- [ ] No hardcoded secrets or API keys
- [ ] WebView configurations follow security guidelines
- [ ] New nullable types have proper null handling
- [ ] Tests cover new functionality
- [ ] Documentation updated if architecture changes

## Migration Guides

### From RxJava to Coroutines

**RxJava:**
```kotlin
fun loadData(): Observable<Data> {
    return dataSource.getData()
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
}
```

**Coroutines:**
```kotlin
fun loadData(): Flow<Data> = flow {
    emit(dataSource.getData())
}.flowOn(Dispatchers.IO)
```

### From LiveData to StateFlow

**LiveData:**
```kotlin
private val _data = MutableLiveData<Data>()
val data: LiveData<Data> = _data

fun update(newData: Data) {
    _data.value = newData
}
```

**StateFlow:**
```kotlin
private val _data = MutableStateFlow<Data?>(null)
val data: StateFlow<Data?> = _data.asStateFlow()

fun update(newData: Data) {
    _data.value = newData
}
```

## Resources

- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [Android Security Best Practices](https://developer.android.com/privacy-and-security/security-best-practices)
- [Jetpack Compose Guidelines](https://developer.android.com/jetpack/compose/guidelines)
- [DuckDuckGo Style Guide](STYLEGUIDE.md)
- [Contributing Guidelines](CONTRIBUTING.md)

## Questions?

For questions about code quality or architecture decisions, please:
1. Check existing documentation in this repository
2. Review recent PRs for similar patterns
3. Reach out to the team via the contribution guidelines
