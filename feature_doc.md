# `webkit` APIs Overview
## Profile
A [Profile](https://developer.android.com/reference/androidx/webkit/Profile) represents one browsing session for WebView. It isolates local storage, `IndexedDB`, `Cookies` database, and other browsing data from other profiles. The independent instance of storage or cookie manager can be accessed via `Profile#webStorage` and `Profile#cookieManager`.

Profiles are persisted on disk and can be used across app restarts. The data is stored in the app's data directory under `app_webview/<profileName>`.

To use `Profile` APIs, the WebView version on the device must support the [WebViewFeature#MULTI_PROFILE](https://developer.android.com/reference/androidx/webkit/WebViewFeature#MULTI_PROFILE()).

### Managing Profiles
A [ProfileStore](https://developer.android.com/reference/androidx/webkit/ProfileStore) is used to create and manage profiles. The singleton instance of `ProfileStore` can be accessed via `ProfileStore#getInstance()`.

It offers 4 functions:
- `getOrCreateProfile(profileName: String)`: Creates a new profile with the given name or returns an existing one if it already exists.
- `getProfile(profileName: String)`: Returns an existing profile, if one exists.
  - :warning: This API is broken because it's supposed to return `null` if profile doesn't exist, but it always returns `Profile` instance that will crash when trying to access its data, therefore, all calls to a profile need to be wrapped with a try-catch.
- `deleteProfile(profileName: String)`: Deletes the profile with the given name and all its associated data.
  - :warning: Deleting a profile that was ever loaded to memory (either by the `ProfileStore#get*` functions or by setting a profile to WebView) seems to be impossible, even if its reference is released from the platform code. It's only possible to delete a profile on app restart if it was never loaded or used.
  - `Default` profile cannot be deleted, trying to do so will throw an exception.
- `getAllProfileNames()`: Returns a list of all existing profile names.

### Using Profiles with WebView
A Profile can be assigned to a WebView using `WebViewCompat#setProfile(webView, profileName)`. If no profile is set, WebView uses the `Default` profile. Profile assignment needs to be the first action performed on a WebView - trying to assign a profile after loading a page or running any scripts will throw an exception. There's no option to change or un-assign a profile after it has been set, to change a profile, a new WebView instance needs to be created.

## WebStorageCompat
To make profile-based storage management easier, 
[WebStorageCompat](https://developer.android.com/reference/androidx/webkit/WebStorageCompat) provides a set of functions to clear data associated with a specific `WebStorage` instance, which can be obtained independently for each profile. Furthermore, it allows clearing data only for specific sites with given `WebStorage` instance.

Available functions are:
- `deleteBrowsingData(instance: WebStorage, *callbacks*)`: Deletes all browsing data associated with the given `WebStorage` instance.
- `deleteBrowsingDataForSite(instance: WebStorage, site: String, *callbacks*)`: Deletes browsing data for the specified site associated with the given `WebStorage` instance.

Example usage when paired with profiles:
```kotlin
val profileStore = ProfileStore.getInstance()
val profile = profileStore.getProfile(name)
if (profile != null) {
  WebStorageCompat.deleteBrowsingData(profile.webStorage) {
      // Data deleted callback
  }
}
```

To use `WebStorageCompat` APIs, the WebView version on the device must support the [WebViewFeature#DELETE_BROWSING_DATA](https://developer.android.com/reference/androidx/webkit/WebViewFeature#DELETE_BROWSING_DATA()).
- Anecdotally, this is supported on WebView version `141.0.7390.97` (running on a Pixel 9 Pro) and isn't on `124.0.6367.219` (running on an emulator).

# Use-case analysis
## Can we create Fire Tabs (Containerised tabs that burn after use)?
A single tab can branch into multiple tabs when pages open links in new tabs, so a concept of a "Fire Tab" would require either blocking opening new tabs from links or open new tabs from links as independent Fire Tabs without a reference to their parent, where either would break all sorts of navigation patterns, login sessions or cookie sharing.

That's why, I think we should reconsider this use-case as a "Fire Session" instead of "Fire Tabs".

A Fire Session won't have any navigation history or visited links references to any other browsing session.