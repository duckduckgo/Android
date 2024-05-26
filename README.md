# Automated migrations using lint and LM

This is a fork of the DuckDuckGo Android project. We chose this project to experiment on for two reasons:
1. It's public so we don't have to worry about jeopardizing intellectual property
2. It's representative of a large Android project
3. It already has integration with lint, so this makes it easier to try out new things.

Note that the DuckDuckGo Android codebase is probably in the training data for 
many LMs given the age of the public repo on GitHub.

This repo is merely using the DuckDuckGo Android code for experimentation. The author
is not affiliated in any way with DuckDuckGo.

# What's to see here?

Look at the open pull requests for examples of AI-assisted refactors.

To run the refactors yourself you can do:

```bash
# Defaults to using Ollama with llama3
./gradlew lintFix --continue

# Configuring Ollama
./gradlew lintFix --continue -Dcom.duckduckgo.lint.model=ollama -Dcom.duckduckgo.lint.ollama.baseurl=BASE_URL -Dcom.duckduckgo.lint.ollama.modelname=llama3

# Use Open AI
./gradlew lintFix --continue -Dcom.duckduckgo.lint.model=openai -Dcom.duckduckgo.lint.openai.key=MY_API_KEY -Dcom.duckduckgo.lint.openai.model=gpt-4o
```

This performs the entire refactor over a large set of modules.

If you want to just try one module then do

```bash
./gradle :autofill-impl:lintFix
```

To add your own models and integrations, look at `com/duckduckgo/lint/chatmodel/ChatModels.kt` in the repo and refer to the langchain4j
documentation [here](https://docs.langchain4j.dev/integrations/language-models/)

## License
DuckDuckGo android is distributed under the Apache 2.0 [license](LICENSE).
