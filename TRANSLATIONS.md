# Improve Language Translations
Thank you for taking the time to contribute to DuckDuckGo! :sparkles:

If you know any of the following languages and would like to help us improve the translations for them, please follow the instructions below.

## List of languages
1. Bulgarian (bg)
1. Croatian (hr)
1. Czech (cs)
1. Danish (da)
1. Dutch (nl)
1. Finnish (fi)
1. French (fr)
1. German (de)
1. Greek (el)
1. Hungarian (hu)
1. Italian (it)
1. Lithuanian (lt)
1. Norwegian bokmål (nb)
1. Polish (pl)
1. Portuguese (pt)
1. Romanian (ro)
1. Russian (ru)
1. Slovak (sk)
1. Slovenian (sl)
1. Spanish (es)
1. Swedish (sv)

## How to help

There is a branch containing the current translations, called [`feature/app_translations`](https://github.com/duckduckgo/Android/tree/feature/app_translations/app/src/main/res).
1. Each language has a `values` directory containing the language code, and the `strings.xml` file contains the translations. Examples:
    - Italian translation files are located within `values-it/strings.xml`  
    - Spanish translation files are located within `values-es/strings.xml`
1. If you notice anything which could be improved, you should create a PR containing the improved language suggestions and those changes will be merged into that branch if accepted.
    1. Fork the project, and clone your forked project to your machine. [https://help.github.com/en/articles/fork-a-repo](https://help.github.com/en/articles/fork-a-repo)
    1. Checkout the translations branch `git checkout --track origin/feature/app_translations`
    1. Make the changes to the relevant `strings.xml` file, and commit
    1. Test the app in the language you've improved, and ensure the changed strings look good
    1. Push the changes to your fork `git push origin`
    1. Create a Pull Request for your changes. [https://help.github.com/en/articles/creating-a-pull-request-from-a-fork](https://help.github.com/en/articles/creating-a-pull-request-from-a-fork)
        - Ensure the base branch in your PR is `feature/app_translations`
      

ℹ️ Please note we are not looking for help with other languages at this time; only the languages listed above. We may open up requests in future for additional languages.