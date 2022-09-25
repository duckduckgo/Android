fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android deploy_playstore

```sh
[bundle exec] fastlane android deploy_playstore
```

Upload APK to Play Store, in pre-production staging track

### android deploy_dogfood

```sh
[bundle exec] fastlane android deploy_dogfood
```

Upload APK to Play Store internal testing track

### android deploy_github

```sh
[bundle exec] fastlane android deploy_github
```

Deploy APK to GitHub

### android release

```sh
[bundle exec] fastlane android release
```

Create new release

### android hotfix-start

```sh
[bundle exec] fastlane android hotfix-start
```

Start new hotfix

### android hotfix-finish

```sh
[bundle exec] fastlane android hotfix-finish
```

Finish a hotfix in progress

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
