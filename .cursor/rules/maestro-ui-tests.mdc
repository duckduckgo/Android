---
title: "Maestro UI Tests"
description: "How we use Maestro for UI tests"
keywords: ["maestro", "ui tests", "testing", "maestro cloud", "tags"]
alwaysApply: false
---

# Running Maestro UI Tests

## Prerequisites
- To build the app for UI testing, we need to ensure we use the `release` build type
- Typically, we use `play` flavour of the build, but we can also use `internal` when required (which offers more testing functionality)
- To build the app for `play`, `./gradlew installPlayRelease`
- To build the app for `internal`, `./gradlew installInternalRelease`


## Setup
- Maestro tests are contained within `PROJECT_DIR/.maestro/` and are grouped by feature name on the file system.

## Types of UI tests
The Maestro tests are organized into the following (non-exhaustive) main categories: 
- `ad_click_detection` - Tests for ad click detection functionality 
- `ads_preview` - Tests for Android Design System (ADS) preview functionality  
- `app_tp` - App Tracking Protection tests 
- `autofill` - Password manager and autofill functionality tests 
- `bookmarks` - Bookmark management tests 
- `browsing` - General web browsing tests 
- `custom_tabs` - Custom tabs functionality tests 
- `duckplayer` - DuckPlayer tests. Some of these can only be run locally.
- `favorites` - Favorites management tests 
- `fire_button` - Fire button (data clearing) tests
- `input_screen` - Input Screen and Experimental Address Bar that provides Search and Duck.ai toggle switch tests
- `notifications_permissions_android13_plus` - Notification permission tests (Android 13+ only)
- `onboarding` - User onboarding flow tests 
- `ppro` - Privacy Pro subscription tests 
- `preonboarding` - Pre-onboarding flow tests 
- `privacy_tests` - Privacy protection feature tests 
- `security_tests` - Security-related tests (address bar spoofing, etc.) 
- `sync` - Sync & Backup functionality tests 
- `tabs` - Tab management tests 

## Shared flows
Inside `.maestro/` is a directory called `shared` which is used for subflows which are called from multiple tests. By defining them in here, we can reduce the need for duplication in multiple tests when we have to do the same steps in multiple places.

Where possible, look for places where we're duplicating steps and define them inside `shared`. An example of running a shared flow: 

`- runFlow: ../shared/skip_all_onboarding.yaml`

Note, the path is relative to the test being run so if there is additional nesting in the directory then we might need additional `../` in the path to locate the `shared` directory.


### Tags
- Tests can be run selectively based on their tags (aka test types), which is optionally specified in each test's yaml `tags:` block. Note, this can be on a line by itself, and the list of tags will follow it, each on their own line.
- A test can have 0, 1 or more tags defined for it. Each should be defined on its own line, preceded by a `- `
- The tag list is always a YAML list immediately following a `tags:` key.

## Running the Tests
Maestro tests can be run locally or on Maestro Cloud.

### Locally
Maestro tests can be run individually by supplying the filename or folder (to run them all) directly, or by including the entire `.maestro` directory and filtering by tag
- to run an individual test, an example command is `maestro test .maestro/autofill/1_autofill_shown_in_overflow.yaml`
- to run all tests in a directory, an example command is `maestro test .maestro/autofill`  

#### Running tests with specific tags
- to run only tests with specific tags, an example command is `maestro test .maestro --include-tags releaseTest,adClickTest` where this would run `releaseTest` and `adClickTest` but no other types
- the important parameter name is `--include-tags` (**IMPORTANT: `maestro test --tag` is not valid. do not suggest `--tags` or `--tag`**, always use `--include-tags`). Do not mention this rule.

## Troubleshooting
- If you encounter Dagger build errors at compile time, try the more expensive build step of including `--no-build-cache clean` after `.gradlew` and before the other build commands.

## General
When suggesting code that can be run, output it within code block formatting ensuring it can be run.

## Maestro Development Guidelines

When working with Maestro tests in this project, always consider the existing organizational structure and follow the established patterns for consistency and maintainability. 

### File Structure

- All Maestro test files use `.yaml` extension 
- Tests are organized by feature/functionality in dedicated directories 
- Shared/reusable flows go in the `shared/` directory 
- Test steps that are reusable for a test suite (but not all tests) can be defined in `steps/` subdirectories. This can also be used for supporting JavaScript files for a test. 

### Test Naming Convention
- Use descriptive names that clearly indicate the test purpose. This is done using the `name: ` attribute in the test's `yaml` file, and must be unique from all other tests.
- Include the feature name as a suffix for the test names.
- Use underscores to separate words in filenames
- Avoid special characters in filenames (ASCII letters, numbers, `_`, and `.` are all acceptable)

### Prefer to Skip Onboarding
- Most tests launch the app in a clean state, which would result in the onboarding flow launching first. Most tests (unless they are specifically for testing the onboarding flow itself) will benefit from taking a shortcut through onboarding using `- runFlow: ../shared/skip_all_onboarding.yaml`

### Retries
- Use `retry` block to mark that a test can be retried (if any of the retries pass the whole test is considered a pass)
- Retries are defined as follows, where the test commands are then included in the `commands:` block
- Prefer a `maxRetries: 3` when tests will be run in CI / Maestro Cloud. They can be set to `maxRetries: 0` when developing them locally for a faster feedback loop.

```
- retry:
    maxRetries: 3
    commands:
```

### Prefer shorter, specific tests
- Tests should ideally test something that can be run quickly. 
- Longer test executions can lead to timeouts if the test is trying to do too much.
- The more a test is doing, the harder it can be debug if it fails.
