# DuckDuckGo Autofill

This code is installed using `npm` in our [extension](https://github.com/duckduckgo/duckduckgo-privacy-extension) and as a git submodule by native apps ([iOS/macOS](https://github.com/duckduckgo/apple-browsers) and [Android](https://github.com/duckduckgo/Android)).

DuckDuckGo Autofill is distributed under the Apache 2.0 [License](LICENSE.md).

## Found a bug? Let us know!

Open an [issue](https://github.com/duckduckgo/duckduckgo-autofill/issues) on GitHub or use the feedback forms embedded in our apps. If you are a DuckDuckGo employee post a task in the [Autofill Bugs board](https://app.asana.com/0/1200930669568058/1204279134793324). There you can also find steps to [triage](https://app.asana.com/0/1200930669568058/1204007305709129/f) and [debug](https://app.asana.com/0/1200930669568058/1204279134793324/f) further.

## How to develop and test on the client platforms

### Extension and Android

Both the extension codebase and Android use `npm` to import autofill. To simplify development workflows, you can use [`npm-link`](https://docs.npmjs.com/cli/v6/commands/npm-link). `npm-link` creates a symlink to the source repo and links it to local package usages. It's a two-step process.

1. In the source repo (this folder), run `npm link`. This must be done only once.
1. In the client repo (the extension folder), run `npm link @duckduckgo/autofill`. Do this every time you start working on the extension repo.

Now you can run `npm start` in this repo and the changes will be picked up automatically each time your client is built 🎉.

### Apple apps (iOS and macOS)

On Apple clients, autofill is included as part of the [BrowserServicesKit](https://github.com/duckduckgo/apple-browsers) (BSK) package. If your changes involve native code, you probably want to work within BSK, otherwise you can work directly within the client codebases.

The easiest way to override the client version of autofill is to drag-and-drop your local autofill folder from the Finder right into Xcode project navigator, at the root level. If you're working in BSK, you can drag-and-drop autofill in the BSK project and then drag-and-drop BSK itself in the platform project.

### Updating Translations

Translations are stored in `src/locales/${language}/${namespace}.json`, e.g. `src/locales/xa/autofill.json` for the pseudo-locale "xa" and namespace "autofill". Using these translations requires updating those JSON files and rebuilding this project.

If a new language or namespace is added, the `translations.js` file must be rebuilt to import the new languages/namespaces. This is available via `npm run build:translations`. Because languages and namespaces are rarely added, it is not run automatically at any point of the build process.

## Start a release using the CI pipeline

We have GitHub Action to facilitate releases. Remember to test on all platforms before proceeding.

1. [Draft a new release in GitHub](https://github.com/duckduckgo/duckduckgo-autofill/releases/new)
2. Add a tag using the [semver convention](https://semver.org/) (like `3.2.4`) and use the same tag as a title
3. Add release notes (these will be included in the Asana task)
4. Publish!

This will create the relevant tasks in the [Autofill Project](https://app.asana.com/0/1198964220583541/1200878329826704) in Asana, add the subtasks to relevant projects, and create PRs in all client repos.

### Communicating breaking changes
If you're introducing a breaking change, make sure to communicate clearly to native engineers. For each platform, if there is already a branch that includes the changes needed to integrate a new version, you can just:

1. Add a comment and close the relevant asana tasks and PRs that were created by the automation. Make sure the future course of action to support those platforms is made clear in these comments.
2. Remove those Asana tasks from any other projects/boards they may have been added to by the asana automation.

If there are no existing branches, instruct the native engineer on what needs to change on their platform to integrate the new version.

If the breaking change is only for a specific platform, just communicate to the others that their platform can be integrated right away following the usual smoke testing procedures.
