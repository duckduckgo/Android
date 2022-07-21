# DuckDuckGo Privacy Dashboard

DuckDuckGo Privacy Dashboard is distributed under the Apache 2.0
[License](LICENSE.md).

## How to add this repo to another project

**[See the Git Submodule docs](https://git-scm.com/book/en/v2/Git-Tools-Submodules#_starting_submodules)**

To add this repo as a submodule, run:

```shell
git submodule add https://github.com/more-duckduckgo-org/duckduckgo-privacy-dashboard
git config -f .gitmodules submodule.duckduckgo-privacy-dashboard.branch main
```

Then you add the Git artifacts to the parent project and commit.

## How to develop this code in the context of another project

**[See the Git Submodule docs](https://git-scm.com/book/en/v2/Git-Tools-Submodules#_working_on_a_submodule)**

By default git submodules are in a detached HEAD state. This means that there
isn't a proper branch to keep track of changes and running
`git submodule update` can overwrite your changes, even if you committed them.

So, the first thing to do when working on the submodule is to checkout a branch
(new or existing).

```shell
cd duckduckgo-privacy-dashboard/
git checkout myname/my-feature
```

You can now work on your code and commit it as usual. If you want to pull
changes from upstream, you must run:

```shell
cd duckduckgo-privacy-dashboard/
git submodule update --remote --merge # or --rebase
```

If you don't pass `--merge` or `--rebase`, Git will revert to a detached HEAD
with the remote content. Don't worry, though, your changes are still in your
branch and you can check it out again.

## How to push the changes upstream

**[See the Git Submodule docs](https://git-scm.com/book/en/v2/Git-Tools-Submodules#_publishing_submodules)**

Once you check out a specific branch, the submodule works as a normal git repo.
You can commit, push and pull from the remote.

Parent projects are setup to track the `main` branch of this repo, so just
follow the usual workflow of opening a PR against `main`.

Once merged, consumer projects will run `git submodule update --remote --merge`
to include these new changes.

## Building

The Privacy Dashboard can be built for all supported environments using
`npm run build`. To preview the application using mock data, use
`npm run preview.example` to view it in your default browser.

## Linting

Code can be linted with `npm run lint`. Use `npm run lint.fix` to automatically
fix supported issues.

## Testing

All tests can be run with `npm test` -- this runs both unit and end-to-end
tests.

### Unit Tests

These run in Jest, using JSDOM. Test files can be found co-located with the
corresponding code under test, using a `tests/` directory and `.test.js`
filename suffix.

ℹ️ Note: Not all code is covered with unit tests.

### End-to-end Tests

These run in Jest, using Playwright to run a Safari instance. Test files can be
found under the `e2e/` directory with a `.e2e.js` filename suffix.

Included with the end-to-end tests is a visual regression testing setup. Tests
can use the `testScreenshot` helper to confirm that the page looks as expected,
by comparing it against a saved screenshot and failing the test if if differs.
Saved screenshot files can be found under `__image_snapshots__`, and show the
Privacy Dashboard in both light and dark themes.
