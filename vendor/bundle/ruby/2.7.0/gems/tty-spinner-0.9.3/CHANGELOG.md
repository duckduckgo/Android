# Change log

## [v0.9.3] - 2020-01-28

### Changed
* Change gemspec to add metadata, remove test artefacts and load version directly

## [v0.9.2] - 2019-12-08

### Fixed
* Fix multi spinner cursor hiding by @benklop

## [v0.9.1] - 2019-05-29

### Changed
* Change bundler to remove version limit
* Change to update tty-cursor dependency

## [v0.9.0] - 2018-12-01

### Changed
* Change tty-cursor dependency
* Change to Ruby >= 2.0
* Change to freeze all string literals
* Change #execute_job to stop evaluating in spinner context and just execute the job
* Change #register to accept a spinner instance by Shane Cavanaugh(@shanecav84)

### Fixed
* Fix to remove a stray single quote in spin_4 by Kristofer Rye(@rye)
* Fix Multi#line_inset to correctly assign styling in threaded environment
* Fix #stop & #auto_spin to always restore hidden cursor if enabled
* Fix deadlock when registering multi spinners

## [v0.8.0] - 2018-01-11

### Added
* Add new formats :bounce, :burger, :dance, :dot_2, ..., dot_11, :shark, :pong

### Changed
* Change to only output to a console and stop output to a file, pipe etc...

### Fixed
* Fix spinner #stop to clear line before printing final message

## [v0.7.0] - 2017-09-11

### Added
* Add :spin event type and emit from TTY::Spinner#spin

### Changed
* Change to automatically spin top level multi spinner when registered spinners spin
* Remove unnecessary checks for top spinner in multi spinner #stop, #success, #error

### Fixed
* Fix multi spinner #observe to only listen for events from registered spinners

## [v0.6.0] - 2017-09-07

### Changed
* Change TTY::Spinner::Multi to render registered spinners at row
  position at point of rendering and not registration

### Fixed
* Fix handling of multi spinner events
* Fix multi spinner display for unicode inset characters

## [v0.5.0] - 2017-08-09

### Added
* Add TTY::Spinner::Multi to allow for parallel spinners executation by Austin Blatt[@austb]
* Add formatting for multi spinner display by Austin Blatt[@austb]
* Add ability to add and execute jobs for single and multi spinners
* Add abilty to register multi spinners with async jobs
* Add #pause and #resume for single and multispinner

### Changed
* Change to unify success category to mark spinner as succeded or errored
* Change Spinner to be thread safe

### Fixed
* Stop firing events when a spinner is stopped

## [v0.4.1] - 2016-08-07

### Changed
* Change #update to clear output when in spinning state

## [v0.4.0] - 2016-08-07

### Added
* Add #auto_spin to automatically displaying spinning animation

### Changed
* Change #start to setup timer and reset done state

## [v0.3.0] - 2016-07-14

### Added
* Add #run to automatically execute job with spinning animation by @Thermatix
* Add #update to allow for dynamic label name replacement

### Fixed
* Fixed cursor hiding for success and error calls by @m-o-e
* Fix #join call to define actual error
* Fix #stop to print only once when finished

## [v0.2.0] - 2016-03-13

### Added
* Add new spinner formats by @rlqualls
* Add ability to specify custom frames through :frames option
* Add :clear option for removing spinner output when done
* Add #success and #error calls for stopping spinner
* Add :done, :success, :error completion events
* Add :success_mark & :error_mark to allow changing markers
* Add :interval for automatic spinning duration
* Add #start, #join and #kill for automatic spinner animation

### Changed
* Change message formatting, use :spinner token to customize message
* Change format for defining spinner formats and intervals

## [v0.1.0] - 2014-11-15

* Initial implementation and release

[v0.9.3]: https://github.com/piotrmurach/tty-spinner/compare/v0.9.2...v0.9.3
[v0.9.2]: https://github.com/piotrmurach/tty-spinner/compare/v0.9.1...v0.9.2
[v0.9.1]: https://github.com/piotrmurach/tty-spinner/compare/v0.9.0...v0.9.1
[v0.9.0]: https://github.com/piotrmurach/tty-spinner/compare/v0.8.0...v0.9.0
[v0.8.0]: https://github.com/piotrmurach/tty-spinner/compare/v0.7.0...v0.8.0
[v0.7.0]: https://github.com/piotrmurach/tty-spinner/compare/v0.6.0...v0.7.0
[v0.6.0]: https://github.com/piotrmurach/tty-spinner/compare/v0.5.0...v0.6.0
[v0.5.0]: https://github.com/piotrmurach/tty-spinner/compare/v0.4.1...v0.5.0
[v0.4.1]: https://github.com/piotrmurach/tty-spinner/compare/v0.4.0...v0.4.1
[v0.4.0]: https://github.com/piotrmurach/tty-spinner/compare/v0.3.0...v0.4.0
[v0.3.0]: https://github.com/piotrmurach/tty-spinner/compare/v0.2.0...v0.3.0
[v0.2.0]: https://github.com/piotrmurach/tty-spinner/compare/v0.1.0...v0.2.0
[v0.1.0]: https://github.com/piotrmurach/tty-spinner/compare/v0.1.0
