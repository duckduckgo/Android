# `webkit` APIs Analysis
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
A single tab can branch into multiple tabs when pages open links in new tabs, so a concept of a "Fire Tab" would require either blocking opening new tabs from links or open new tabs from links as independent Fire Tabs without a reference to their parent, where either would break all sorts of navigation patterns, login sessions, etc.

That's why, I think we should reframe this use-case as a "Fire Session" instead of "Fire Tabs".

Note: We can refer to a session that holds all regular tabs as the "Default Session".

A Fire Session doesn't share primary browsing data neither with the Default Session nor with any other Fire Session:
- Links visited in one session won't be marked in other sessions.
- Cookies, scrips, or files persisted in one session won't be accessible in other sessions. This includes login sessions, site preferences, etc.

We can choose to allow users to create multiple Fire Sessions or limit them to a single Fire Session at a time. Latter would simplify the technical changes needed to provide such capibility slightly but both should require a medium effort (within a week). This does not include any UI/UX work to expose the feature to the user, If we choose to only allow one Fire Session at a time, the UI could likely be much simpler.

### Availability
To be able to offer Fire Sessions, we need to ensure that the device's WebView supports `Profile` APIs. If these APIs are not supported, we cannot offer Fire Sessions.

### Data cleanup when a Fire Session is closed
When all tabs withing a Fire Session are closed, we can immediately delete all browsing data associated with that session. This clean up does not require app restart.

To be able to do the cleanup safely, the device's WebView needs to support a specific set of `WebStorageCompat` APIs. If these APIs are not supported, we can still attempt to delete the data manually - PoC has proven it works but there might be edge cases that we're not considering and having to implement this path will add some time to the cost estimation.

To decrease the investment needed, we might consider offering the Fire Sessions feature only on devices that support all the expected `WebView` APIs. We can check for API support upfront by adding exploratory Pixels to out existing app version that would measure the percentage of users on devices that support the expected APIs.

### Other features in Fire Sessions
#### Search and clicked suggestions history
We have a number of choices depending on the desired user experience:
- Should history from Default Session be accessible in Fire Sessions?
  - Yes: Requires no additional changes.
  - No: Small effort (within a day).
- Should history from Fire Sessions be accessible in the Default Session?
  - Yes: Requires no additional changes.
  - No: Should each Fire Session maintain a separate search and clicked suggestions history?
    - Yes: Medium effort (within a week).
    - No (do not save history within Fire Sessions): Small effort (within a day).

#### Favorites and Bookmarks
Favorites and bookmarks would be shared between Default Session and Fire Sessions.

#### Fireproofing
The concept of fireproofing a site cannot be applied to Fire Sessions, this option would have to be removed from the browser menu for Fire Sessions.

#### Privacy protections
Privacy protections would be applied equally in Default Session and Fire Sessions without any changes. Disabling privacy protections for a site in Fire Sessions would apply the same change to all the other sessions, and vice-versa.

### Summary
To offer Fire Sessions, we estimate between 1-2 weeks of development effort to prepare the necessary infrastructure. This estimate does not include any UI/UX work to expose the feature to the user.

## Can we burn single tabs?
Yes. When burning a single tab:
- We can either burn data for all sites visited in that tab or allow user to select specific sites to burn data for. This can match the experience available in our desktop browsers.
  -  Medium effort (within a week).
- We can burn search and clicked suggestions history entries generated by that tab.
  -  Medium effort (within a week).

This clean up does not require app restart.

These estimates do not include any UI/UX work to expose the feature to the user.

### Availability
We can only offer to burn single tabs if the device's WebView supports a specific set of `WebStorageCompat` APIs.

## Can we clear data only (not tabs)?
Yes, we can clear data only for all tabs, without closing them. Tabs would remain open on the tab switcher screen without their previews. This action requires app restart. Required effort is small (within a day).

These estimates do not include any UI/UX work to expose the feature to the user.

## Can we clear tabs only (not data)?
Yes, no technical investment required, the only cost is UI/UX work to expose the feature to the user.

## Can we clear history (back and forward stack and auto-suggest) separately from data?
Yes, the effort required on the technical side is small (within a day).

This estimates do not include any UI/UX work to expose the feature to the user.