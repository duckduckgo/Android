fastlane documentation
================
# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```
xcode-select --install
```

Install _fastlane_ using
```
[sudo] gem install fastlane -NV
```
or alternatively using `brew install fastlane`

# Available Actions
## Android
### android deploy_playstore
```
fastlane android deploy_playstore
```
Upload APK to Play Store, in pre-production staging track
### android deploy_dogfood
```
fastlane android deploy_dogfood
```
Upload APK to Play Store internal testing track
### android deploy_github
```
fastlane android deploy_github
```
Deploy APK to GitHub
### android release
```
fastlane android release
```
Create new release

----

This README.md is auto-generated and will be re-generated every time [fastlane](https://fastlane.tools) is run.
More information about fastlane can be found on [fastlane.tools](https://fastlane.tools).
The documentation of fastlane can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
