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

Upload AAB to Play Store, in production track with a very small rollout percentage

### android update_release_notes_playstore

```sh
[bundle exec] fastlane android update_release_notes_playstore
```

Update Play Store release notes

### android update_release_notes_github

```sh
[bundle exec] fastlane android update_release_notes_github
```

Update GitHub release notes

### android deploy_dogfood

```sh
[bundle exec] fastlane android deploy_dogfood
```

Upload AAB to Play Store internal testing track and APK to Firebase

### android deploy_github

```sh
[bundle exec] fastlane android deploy_github
```

Deploy APK to GitHub

### android tag_and_push_release_version

```sh
[bundle exec] fastlane android tag_and_push_release_version
```

Create a new release branch and update the version

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

### android asana_release_prep

```sh
[bundle exec] fastlane android asana_release_prep
```

Prepares the Asana release board with a new release task, tags tasks waiting for release etc..

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
