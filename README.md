# DuckDuckGo Android

Welcome to our android application. We are excited to engage the community in development, see [CONTRIBUTING.md](CONTRIBUTING.md).

## We are hiring!
DuckDuckGo is growing fast and we continue to expand our fully distributed team. We embrace diverse perspectives, and seek out passionate, self-motivated people, committed to our shared vision of raising the standard of trust online. If you are a senior software engineer capable in either iOS or Android, visit our [careers](https://duckduckgo.com/hiring/#open) page to find out more about our openings!

## Building the Project

### Prerequisites
- **JDK 17** or higher
- **Android Studio** (latest stable version recommended)
- **Git** with submodule support
- **Minimum 8GB RAM** for build performance
- **~10GB disk space** for project and dependencies

### Clone the Repository

We use git submodules, so you need to ensure the submodules are initialized properly. You can use the `--recursive` flag when cloning the project:

```bash
git clone --recursive https://github.com/duckduckgo/android.git
cd android
```

Alternatively, if you already have the project checked out, you can initialize the submodules manually:

```bash
git submodule update --init
```

### Build Configuration

#### Development Build
To build and run a debug version:

```bash
./gradlew assembleDebug
```

Install on connected device:
```bash
./gradlew installDebug
```

#### Release Build
To build an optimized release version:

```bash
./gradlew assembleRelease
```

**Note:** The release build includes ProGuard optimization for reduced APK size and improved performance.

#### Running Tests

Run unit tests:
```bash
./gradlew test
```

Run instrumented tests:
```bash
./gradlew connectedAndroidTest
```

#### Code Quality Checks

Run linting:
```bash
./gradlew lint
```

Run code formatting checks:
```bash
./gradlew spotlessCheck
```

Apply code formatting:
```bash
./gradlew spotlessApply
```

### Build Variants

The project supports multiple build variants:
- **Debug**: Development build with debugging enabled
- **Release**: Optimized production build
- **Internal**: Internal testing build with additional features

### Troubleshooting

**Issue: Submodules not initialized**
```bash
git submodule update --init --recursive
```

**Issue: Build fails with memory error**
```bash
# Increase Gradle memory in gradle.properties
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC
```

**Issue: Dependency resolution fails**
```bash
./gradlew --refresh-dependencies
```

**Issue: Clean build needed**
```bash
./gradlew clean
./gradlew assembleDebug
```

### Project Structure

```
android/
├── app/                    # Main application module
├── autofill/              # Autofill feature module
├── app-tracking-protection/ # Anti-tracking feature
├── network-protection/    # VPN feature
├── privacy-config/        # Privacy configuration
├── common/                # Shared utilities
└── [other feature modules]
```

For more details on code quality standards, see [CODE_QUALITY.md](CODE_QUALITY.md).
    
## Terminology

We have taken steps to update our terminology and remove words with problematic racial connotations, most notably the change to `main` branches, `allow lists`, and `blocklists`. Closed issues or PRs may contain deprecated terminology that should not be used going forward.

## Contribute

Please refer to [contributing](CONTRIBUTING.md).

## Discuss

Contact us at https://duckduckgo.com/feedback if you have feedback, questions or want to chat. You can also use the feedback form embedded within our Mobile App - to do so please navigate to Settings and select "Leave Feedback".

## License
DuckDuckGo android is distributed under the Apache 2.0 [license](LICENSE).
