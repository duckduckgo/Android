# Changelog

## 0.3.0

###### Enhancements

* Add catch-all formatter to allow formatting unrecognized text
  | [Chris Ballinger](https://github.com/chrisballinger)
  | [#327](https://github.com/supermarin/xcpretty/pull/327)

* Support `ccache-clang` (and other commands) in formatted output and JSON
  compilation database reports
  | [Delisa Mason](https://github.com/kattrali)
  | [#332](https://github.com/supermarin/xcpretty/pull/332)


## 0.2.6

* Codesigning matchers improvements
* Screenshots parsing fixes

###### Bug fixes

* Fix reporers crash by default ld warning implementation
  | [iKiKi](https://github.com/iKiKi)
  | [#187](https://github.com/supermarin/xcpretty/pull/187)

## 0.2.1

###### Bug fixes

* Fix reporers crash by default ld warning implementation
  | [iKiKi](https://github.com/iKiKi)
  | [#187](https://github.com/supermarin/xcpretty/pull/187)


## 0.2.0

###### Enhancements

* Format `ld` warnings as warnings instead of errors
  | [Cédric Luthi](https://github.com/0xced)
  | [#160](https://github.com/supermarin/xcpretty/pull/160)

* Use `rouge` instead of `pygments` for syntax highlighting
  | [Kyle Fuller](https://github.com/kylef)
  | [#141](https://github.com/supermarin/xcpretty/pull/141)

* Output phase success messages in simple formatter
  | [Delisa Mason](https://github.com/kattrali)
  | [#167](https://github.com/supermarin/xcpretty/pull/167)

* Automatically detect terminal color, and make `-c --color` flag redundant
  | [Delisa Mason](https://github.com/kattrali)
  | [#136](https://github.com/supermarin/xcpretty/pull/136)
  <br>You can still use `--no-color` or force `--color`.

* Show check dependencies phase
  | [Vladimir Burdukov](https://github.com/chipp)
  | [#176](https://github.com/supermarin/xcpretty/pull/176)


* Show compile storyboard outputs
* Show compilation errors for missing files

###### Misc

* Use the CROSS MARK (U+274C) symbol for errors
  | [Cédric Luthi](https://github.com/0xced)
  | [#159](https://github.com/supermarin/xcpretty/pull/159)

## 0.1.12

###### Bug fixes

* Fixed COMPILE_MATCHER to match all languages
  | [Paul Cantrell](https://github.com/pcantrell)
  | [#158](https://github.com/supermarin/xcpretty/pull/158)


## 0.1.11

###### Enhancements

* Added support for Analyzing target output
  | [Cédric Luthi](https://github.com/0xced)
  | [#146](https://github.com/supermarin/xcpretty/pull/146)

* Improved logging of `ld` error messages
  | [Felix Krause](https://github.com/KrauseFx)
  | [#153](https://github.com/supermarin/xcpretty/pull/153)

## 0.1.10

###### Enhancements

* Fixed buffering issues. Output looks realtime now
  | [mtauraso](https://github.com/mtauraso)
  | [#123](https://github.com/supermarin/xcpretty/pull/123)


## 0.1.8

###### Enhancements

* Added support for build warnings (even multiline with snippets)
  | [#131](https://github.com/supermarin/xcpretty/pull/131)
* Support for measuring test runtimes
  | [Norio Nomura](https://github.com/norio-nomura)
  | [#124](https://github.com/supermarin/xcpretty/pull/124)
* Support for `write file` and `Write auxiliary files`
  | [Piet Brauer](https://github.com/pietbrauer)
  | [#128](https://github.com/supermarin/xcpretty/pull/128)
* Support for `oclint v0.9dev` json-compilation-database
  | [iKiKi](https://github.com/iKiKi)
  | [#121](https://github.com/supermarin/xcpretty/pull/121)
* Syntax highlighting for other languages than Objc (Swift, C++, Objc++, Dylan, Ruby)
  | [#110](https://github.com/supermarin/xcpretty/pull/110)


## 0.1.5

###### Enhancements

* Json-compilation-databse report!
  | [Boris Bügling](https://github.com/neonichu)
  | [#77](https://github.com/supermarin/xcpretty/pull/77)
* Parsing duplicate symbols
  | [#78](https://github.com/supermarin/xcpretty/pull/78)
* Improved performance (nobody cares)

###### Bug Fixes

* Fix closing files when used from another proccess
  | [Jonas Witt](https://github.com/jonaswitt)
  | [#75](http://github.com/supermarin/xcpretty/pull/75)


## 0.1.4

###### Enhancements

* New logo
* New output format: tap
* New output format: knock
* Updated parser to support Specta 0.2.1
  | [Josh Vickery](https://github.com/vickeryj)
  | [#64](https://github.com/supermarin/xcpretty/pull/64)
* Support additional file extensions
  | [Boris Bügling](https://github.com/neonichu)
  | [#59](https://github.com/supermarin/xcpretty/pull/59)


## 0.1.3

###### Enhancements

* Indented test runs by suite
* Added HTML reporter

###### Misc

* removed the faux exit statuts hangling. use `exit ${PIPESTATUS[0]}`


## 0.1.2

###### Enhancements

* More consistent error output (add some spacing before and after)
* Parsed clang errors
* Parsed ld: errors


## 0.1.1

###### Enhancements

* Parse more fatal errors, and CodeSign errors that were printed to STDOUT |
  [#51](https://github.com/supermarin/xcpretty/issues/51)


## 0.1.0

###### Enhancements

* Color semi-slow tests in yellow, slow tests in red |
  [#46](https://github.com/supermarin/xcpretty/pull/46)
* Add option to specify a custom location for report generation |
  [#43](https://github.com/supermarin/xcpretty/pull/43)


## 0.0.9

###### Enhancements

* major performance fix, it's faster than `cat` by 2-3 times on big inputs.
  Thanks [@kviksilver](https://github.com/kviksilver) for providing debug log and helping to reproduce


## 0.0.8

###### Bug Fixes

* show version if not piped |
  [#39](https://github.com/supermarin/xcpretty/issues/39)

###### Enhancements

* format linker failures (Undefined symbols for arch...)


## 0.0.7

###### Enhancements

* exit(1) if xcodebuild failure detected
* Print compile errors nicely. Currently we support compiler erorrs,
  and Pods-not-installed errors. Missing mach-o-linker failures
* Added support for loading arbitrary custom printers (experimental) |
  [Eloy Durán](https://github.com/alloy) |
  [#29](https://github.com/supermarin/xcpretty/pulls/29)
* Show help banner in case no data is piped in |
  [Eloy Durán](https://github.com/alloy) |
  [#29](https://github.com/supermarin/xcpretty/pulls/29)


## 0.0.6

###### Enhancements

* Added support for reporters
* Added JUnit reporter


## 0.0.5

###### Bug Fixes

* `--no-utf` was set incorrectly. now it works as expected.


## 0.0.4

###### Enhancements

* Prettier `--simple` output |
  [Preview](https://travis-ci.org/allending/Kiwi/builds/15190533)
* Removed Paint dependency
* Better test failure formatting (added indentation, grouped by suite)
* Added example of running tests continuously
* Support for not using Unicode (`--no-utf`)

###### Bug Fixes

* Fixed multi-suite summary output (when tests are run on many devices)


## 0.0.3

* add Ruby 1.8 support

## 0.0.2

* add Ruby 1.9 support

## 0.0.1

* initial version
