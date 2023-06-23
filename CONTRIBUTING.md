# Contributing to DuckDuckGo

Thank you for taking the time to contribute to DuckDuckGo! :sparkles:

We are pleased to open up the project to you - our community. How can you contribute?

## Improve translations
We are introducing support for additional languages and would like your help in improving the translations. Please see [improving translations](TRANSLATIONS.md) for how you can help us.

## Share feedback
Contact us at https://duckduckgo.com/feedback if you have feedback, questions or want to chat. You can also use the feedback form embedded within our Mobile App - to do so please navigate to Settings and select "Leave Feedback".

## Report an issue
A great way to contribute to the project is to report an issue when you encounter a problem.

We want our app to be as stable as possible thus your bug reports are immensely valuable. When reporting bug, please follow the bug template.

If you have encountered a security issue, please reach us through https://hackerone.com/duckduckgo.

## What to expect

When a new issue is opened, we will label it as:
* Needs triage: needs a triage from a member of our team
* under investigation: our team will investigate the issue

After our team has investigated each issue, we will label them as:
* Will fix: issue will be fixed internally by DuckDuckGo
* [help wanted](https://github.com/duckduckgo/Android/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22): community contributions are welcome
* Won't fix: issue will be closed without a fix

## Contribute Code
We're always open to contributions from the community! There are different approaches depending on how you wish to contribute:
* **For bug fixes**, please feel free to open an issue to initiate a discussion before submitting any pull requests. If there's already an associated issue created, please add it to the description. Someone from the team will review your issue/change within a few days.
* Refactoring, product changes and other features won't be considered and therefore the Pull Request will be closed.
* If you're looking for a bug to work on, see the [Help Wanted](https://github.com/duckduckgo/Android/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22) tag for a list of open issues.

### Style Guide

Refer to our [style guide](STYLEGUIDE.md).

### Commit Messages

See Chris Beams' guide to writing good commit messages https://chris.beams.io/posts/git-commit/

### Why is a build failing?

Bitrise as a our CI environment, [all builds can be seen here](https://app.bitrise.io/app/dc22e377b9a9ccbf#/builds).
We use [Spotless](https://github.com/diffplug/spotless) as a code formatter, and every build in Bitrise will trigger a check for several rules.
If your PR is failing because of that, please make sure that you follow our [style guide](STYLEGUIDE.md) and the code is formatted.
You can also trigger an automatic code formatting of the code by executing:

```
./gradleW app:spotlessApply
./gradleW formatKotlin
```
