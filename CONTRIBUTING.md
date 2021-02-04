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

We welcome pull requests aimed at fixing bugs and security issues.

If you have a great idea or a feature request that can help improving our browser, start by opening a [Discussion](https://github.com/duckduckgo/Android/discussions). Discussions is the right place to have an open conversation about an idea with our devs and maintainers. Proposals that fit our product direction and timeline will be added to our backlog and labelled accordingly.

We have also labeled tasks you can help with as [help wanted](https://github.com/duckduckgo/Android/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22). Those tasks a great places to start contributing to DuckDuckGo and we'll support you through the process.

### Style Guide

We care about clean code. Refer to our [style guide](styleguide/STYLEGUIDE.md).

### Commit Messages

See Chris Beams' guide to writing good commit messages https://chris.beams.io/posts/git-commit/

### Why is a build failing?

Bitrise as a our CI environment, [all builds can be seen here](https://app.bitrise.io/app/dc22e377b9a9ccbf#/builds).
We use [Spotless](https://github.com/diffplug/spotless) as a code formatter, and every build in Bitrise will trigger a check for several rules.
If your PR is failing because of that, please make sure that you have our [style guide](styleguide/STYLEGUIDE.md) imported and the code formatted.
You can also trigger an automatic code formatting of the code by executing:

```
./gradleW app:spotlessApply
```
