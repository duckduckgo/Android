![logo](http://i.imgur.com/i2fElxx.png)

__`xcpretty` is a fast and flexible formatter for `xcodebuild`__.<br/>
It does one thing, and it should do it well.

[![Gem version](http://img.shields.io/gem/v/xcpretty.svg)](http://rubygems.org/gems/xcpretty)
[![Build Status](https://travis-ci.org/supermarin/xcpretty.svg?branch=master)](https://travis-ci.org/supermarin/xcpretty)
[![Code Climate](http://img.shields.io/codeclimate/github/supermarin/xcpretty.svg)](https://codeclimate.com/github/supermarin/xcpretty)
[![Reviewed by Hound](https://img.shields.io/badge/Reviewed_by-Hound-8E64B0.svg)](https://houndci.com)

## Installation
``` bash
$ gem install xcpretty
```

## Usage
``` bash
$ xcodebuild [flags] | xcpretty
```
`xcpretty` is designed to be piped with `xcodebuild` and thus keeping 100%
compatibility with it. It's even a bit faster than `xcodebuild` itself, since
it saves your terminal some prints.

__Important:__ If you're running `xcpretty` on a CI like Travis or Jenkins, you
may want to exit with same status code as `xcodebuild`.
CI systems usually use status codes to determine if the build has failed.

``` bash
$ set -o pipefail && xcodebuild [flags] | xcpretty
#
# OR
#
$ xcodebuild [flags] | xcpretty && exit ${PIPESTATUS[0]}
```

## Raw xcodebuild output
You might want to use `xcpretty` together with `tee` to store the raw log in a
file, and get the pretty output in the terminal. This might be useful if you
want to inspect a failure in detail and aren't able to tell from the pretty
output.

Here's a way of doing it:
``` bash
$ xcodebuild [flags] | tee xcodebuild.log | xcpretty
```

## Formats

- `--simple`, `-s` (default)
![xcpretty --simple](http://i.imgur.com/LdmozBS.gif)

- `--test`, `-t` (RSpec style)
![xcpretty alpha](http://i.imgur.com/VeTQQub.gif)
- `--tap` ([Test Anything Protocol](http://testanything.org)-compatible output)
- `--knock`, `-k` (a [simplified version](https://github.com/chneukirchen/knock) of the Test Anything Protocol)

## ANSI / UTF-8

- `--[no-]color`: Show build icons in color. (you can add it to `--simple` or `--test` format).
  Defaults to auto-detecting color availability.
- `--[no-]utf`: Use unicode characters in build output or only ASCII.
  Defaults to auto-detecting the current locale.

## Reporters

- `--report junit`, `-r junit`: Creates a JUnit-style XML report at `build/reports/junit.xml`, compatible with Jenkins and TeamCity CI.

- `--report html`, `-r html`: Creates a simple HTML report at `build/reports/tests.html`.
![xcpretty html](http://i.imgur.com/0Rnux3v.gif)

- `--report json-compilation-database`, `-r json-compilation-database`: Creates a [JSON compilation database](http://clang.llvm.org/docs/JSONCompilationDatabase.html) at `build/reports/compilation_db.json`. This is a format to replay single compilations independently of the build system.

Writing a report to a custom path can be specified using `--output PATH`.

## Extensions

`xcpretty` supports custom formatters through the use of the
`--formatter` flag, which takes a path to a file as an argument. The
file must contain a Ruby subclass of `XCPretty::Formatter`, and
return that class at the end of the file. The class
can override the `format_*` methods to hook into output parsing
events.

### Known extensions

* [xcpretty-travis-formatter](https://github.com/kattrali/xcpretty-travis-formatter): support for cleaner output in TravisCI using code folding

The recommended format is a gem containing the formatter and named
with an `xcpretty-` prefix, for easier discovery.


## Team

- [Marin Usalj](http://github.com/supermarin) http://supermar.in
- [Delisa Mason](http://github.com/kattrali) http://delisa.me
