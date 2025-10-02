## DuckDuckGo Android Style Guide
* We care about clean code and aim to make this codebase as self-documenting and readable as possible.
* We primarily use Kotlin and follow coding conventions based on the [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide)
* There may be instances of code that pre-dates our use of this style guide, these can be refactored as we encounter them.

###  Code formatting

You can check the code formatting correctness by running `./gradleW spotlessCheck`.
To adhere to codestyle, please run `./gradleW spotlessApply` to autoformat and fix any CI issues.

If you want to do this automatically upon commit we recommend the existing [pre-commit hook](.githooks/pre-commit):
- Pull develop branch
- Execute the following command `git config core.hooksPath .githooks` in the terminal

##  Code conventions

### Logging
We use Square's logcat library for logging. When logging, we use Kotlin string interpolation:

```logcat { "Loading $url" }```

By default, calling logcat without a priority logs at DEBUG level.

For different log levels, specify the priority:

```logcat(WARN) { "Loading $url" }```

For logging exceptions, use the `asLog()` extension:

```logcat(ERROR) { "Failed to load: ${exception.asLog()}" }```

This approach provides better performance and readability compared to traditional string formatting.

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
