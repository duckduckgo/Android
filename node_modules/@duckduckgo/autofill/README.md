# DuckDuckGo Autofill

This code is installed using `npm` in our [extension](https://github.com/duckduckgo/duckduckgo-privacy-extension) and as a git submodule by native apps ([iOS](https://github.com/duckduckgo/iOS) and [Android](https://github.com/duckduckgo/Android)).

DuckDuckGo Autofill is distributed under the Apache 2.0 [License](LICENSE.md).

## How to develop and test within the context of the extensions

To simplify development workflows, you can use [`npm-link`](https://docs.npmjs.com/cli/v6/commands/npm-link). `npm-link` creates a symlink to the source repo and links it to local package usages. It's a two-step process.

1. In the source repo (this folder), run `npm link`. This must be done only once.
1. In the client repo (the extension folder), run `npm link @duckduckgo/autofill`. Do this every time you start working on the extension repo.

Now you can run `npm start` in this repo and the changes will be picked up automatically in the client 🎉.

## How to add this as a subrepo to another project
###### [See the docs](https://git-scm.com/book/en/v2/Git-Tools-Submodules#_starting_submodules)

> Note: if you can use a package manager like `npm` it's probably better to go that route. Use submodules only if necessary.

To add this repo as a submodule, run:

```shell
git submodule add https://github.com/duckduckgo/duckduckgo-autofill
git config -f .gitmodules submodule.duckduckgo-autofill.branch main
```

Then you add the Git artifacts to the parent project and commit.

## How to develop this code in the context of another project
###### [See the docs](https://git-scm.com/book/en/v2/Git-Tools-Submodules#_working_on_a_submodule)

By default git submodules are in a detached HEAD state. This means that there isn't a proper branch to keep track of changes and running `git submodule update` can overwrite your changes, even if you committed them.

So, the first thing to do when working on the submodule is to checkout a branch (new or existing).

```shell
# from the parent project
cd duckduckgo-autofill/
git checkout myname/my-feature
```

You can now work on your code and commit it as usual. If you want to pull changes from upstream, you must run:

```shell
# got back to the parent project
cd ..
git submodule update --remote --merge # or --rebase
```

If you don't pass `--merge` or `--rebase`, Git will revert to a detached HEAD with the remote content. Don't worry, though, your changes are still in your branch and you can check it out again.

## How to push the changes upstream
###### [See the docs](https://git-scm.com/book/en/v2/Git-Tools-Submodules#_publishing_submodules)

Once you check out a specific branch, the submodule works as a normal git repo. You can commit, push and pull from the remote.

Parent projects are setup to track the `main` branch of this repo, so just follow the usual workflow of opening a PR against `main`.

Once merged, consumer projects will run `git submodule update --remote --merge` to include these new changes.

## Start a release using the CI pipeline

We have GitHub Action to facilitate releases. Remember to test on all platforms before proceeding. 

1. [Draft a new release in GitHub](https://github.com/duckduckgo/duckduckgo-autofill/releases/new)
2. Add a tag using the [semver convention](https://semver.org/) (like `3.2.4`) and use the same tag as a title
3. Add release notes (these will be included in the Asana task)
4. Publish!

This will create the relevant tasks in the [Autofill Project](https://app.asana.com/0/1198964220583541/1200878329826704) in Asana and add the subtasks to relevant projects.
