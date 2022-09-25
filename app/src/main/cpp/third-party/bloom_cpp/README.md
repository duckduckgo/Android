# DuckDuckGo

Welcome to our bloom filter. We are excited to engage the community in development.

## Components

The main bloom filter library source is cross-platform and has been used on:
- android >= 5.0.0
- ios >=10
- linux >= Ubuntu 16.04.3 LTS
- macOS >= 10.12

The library is written in C++ 14. Utils and unit tests have been run only on linux and macOS.
We recommend installing g++ and cmake to run this project. Underlying architecture is expected
to use 8-bit chars.

### Utils - GenerateFilter
A convenience utility that uses the main filter library to generate a binary bloom filter that can then be imported 
 later or used on a different device. This is useful for distributing / sharing populated bloom filters.
 Also generates a false positive list that can be used as supplementary data to ensure final result is always correct.

Run via the `./make_filter.sh` script with appropriate parameters.

**Additional system dependencies**

**openssl:** This is often available on linux but needs to be installed
 for macOS where we assume this will be installed via `brew` to `/usr/local/opt/openssl/`

### Tests
Project unit tests. We use the Catch library at https://github.com/catchorg/Catch2 for unit testing.
We include this as a submodule, run `git submodule update --init --recursive` to pull down the submodule.
Run the `./run_tests.sh` script to execute the unit tests.

**Additional system dependencies**

**uuid**: This is generally available on macOS. On debian based linux systems 
install `uuid-dev`.

## Reporting Bugs

When reporting bugs let us know the:
* OS and version
* Steps to reproduce the bug
* Expected behavior
* Actual behavior

## Terminology
We have taken steps to update our terminology and remove words with problematic racial connotations, most notably the change to `main` branches, `allow lists`, and `blocklists`. Closed issues or PRs may contain deprecated terminology that should not be used going forward.

## Discuss

Open a github issue or contact us at https://duckduckgo.com/feedback if you have feedback, questions or want to chat.

## License
DuckDuckGo android is distributed under the Apache 2.0 [license](LICENSE).
