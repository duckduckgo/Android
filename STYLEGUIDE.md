## DuckDuckGo Android Style Guide
* We care about clean code and aim to make this codebase as self-documenting and readable as possible.
* We primarily use Kotlin and follow coding conventions based on the [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide)
* There may be instances of code that pre-dates our use of this style guide, these can be refactored as we encounter them.

###  Code formatting

To adhere to codestyle, please run `./gradlew spotlessApply` to autoformat in order to fix any CI issues.

If you want to do this automatically upon commit we recommend the following `pre-commit` hook.

```bash
❯ cat .git/hooks/pre-commit
./gradlew :app:spotlessApply
git add `git diff --name-only`
exit 0
```

If you want to reformat code whilst writing it, please make sure you have installed and enabled 
the [IntelliJ Ktfmt plugin](https://plugins.jetbrains.com/plugin/14912-ktfmt), with Dropbox Style selected.

##  Code conventions

### Logging
When logging with Timber we use the new Kotlin styles strings

```Timber.w("Loading $url")```

Rather than C style strings

```Timber.w("Loading %s", url)```

Mixing the two styles within a single statement can lead to crashes so we have standardized on the more readable Kotlin style. This is slightly less efficient - should efficiency become an issue we can use proguard to optimize away log statements for releases.

### Package Names
Case in package names is problematic as some file system and tools do not handle case sensitive file changes well. For this reason, we opt for lowercase packages in our project. Thus we have:

```package com.duckduckgo.app.trackerdetection```

rather than:

```package com.duckduckgo.app.trackerDetection```

### Unit test names
We use the when then convention for test:

```when <condition> then <expected result>```

For example:

```whenUrlIsNotATrackerThenMatchesIsFalse()```
