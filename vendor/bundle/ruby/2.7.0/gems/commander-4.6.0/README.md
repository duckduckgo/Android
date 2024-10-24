[<img src="https://api.travis-ci.org/commander-rb/commander.svg" alt="Build Status" />](https://travis-ci.org/commander-rb/commander)
[![Inline docs](https://inch-ci.org/github/commander-rb/commander.svg)](https://inch-ci.org/github/commander-rb/commander)

# Commander

The complete solution for Ruby command-line executables.
Commander bridges the gap between other terminal related libraries
you know and love (OptionParser, HighLine), while providing many new
features, and an elegant API.

## Features

* Easier than baking cookies
* Parses options using OptionParser
* Auto-populates struct with options ( no more `{ |v| options[:recursive] = v }` )
* Auto-generates help documentation via pluggable help formatters
* Optional default command when none is present
* Global / Command level options
* Packaged with two help formatters (Terminal, TerminalCompact)
* Imports the highline gem for interacting with the terminal
* Adds additional user interaction functionality
* Highly customizable progress bar with intuitive, simple usage
* Multi-word command name support such as `drupal module install MOD`, rather than `drupal module_install MOD`
* Sexy paging for long bodies of text
* Support for MacOS text-to-speech
* Command aliasing (very powerful, as both switches and arguments can be used)
* Growl notification support for MacOS
* Use the `commander` executable to initialize a commander driven program

## Installation

    $ gem install commander

## Quick Start

To generate a quick template for a commander app, run:

    $ commander init yourfile.rb

To generate a quick modular style template for a commander app, run:

    $ commander init --modular yourfile.rb

## Example

For more option examples view the `Commander::Command#option` method. Also
an important feature to note is that action may be a class to instantiate,
as well as an object, specifying a method to call, so view the RDoc for more information.

### Classic style

```ruby
require 'rubygems'
require 'commander/import'

# :name is optional, otherwise uses the basename of this executable
program :name, 'Foo Bar'
program :version, '1.0.0'
program :description, 'Stupid command that prints foo or bar.'

command :foo do |c|
  c.syntax = 'foobar foo'
  c.description = 'Displays foo'
  c.action do |args, options|
    say 'foo'
  end
end

command :bar do |c|
  c.syntax = 'foobar bar [options]'
  c.description = 'Display bar with optional prefix and suffix'
  c.option '--prefix STRING', String, 'Adds a prefix to bar'
  c.option '--suffix STRING', String, 'Adds a suffix to bar'
  c.action do |args, options|
    options.default :prefix => '(', :suffix => ')'
    say "#{options.prefix}bar#{options.suffix}"
  end
end
```

Example output:

```
$ foobar bar
# => (bar)

$ foobar bar --suffix '}' --prefix '{'
# => {bar}
```

### Modular style

**NOTE:** Make sure to use `require 'commander'` rather than `require 'commander/import'`, otherwise Commander methods will still be imported into the global namespace.

```ruby
require 'rubygems'
require 'commander'

class MyApplication
  include Commander::Methods

  def run
    program :name, 'Foo Bar'
    program :version, '1.0.0'
    program :description, 'Stupid command that prints foo or bar.'

    command :foo do |c|
      c.syntax = 'foobar foo'
      c.description = 'Displays foo'
      c.action do |args, options|
        say 'foo'
      end
    end

    run!
  end
end

MyApplication.new.run if $0 == __FILE__
```

### Block style

```ruby
require 'rubygems'
require 'commander'

Commander.configure do
  program :name, 'Foo Bar'
  program :version, '1.0.0'
  program :description, 'Stupid command that prints foo or bar.'

  # see classic style example for options
end
```

## HighLine

As mentioned above, the highline gem is imported into the global scope. Here
are some quick examples for how to utilize highline in your commands:

```ruby
# Ask for password masked with '*' character
ask("Password:  ") { |q| q.echo = "*" }

# Ask for password
ask("Password:  ") { |q| q.echo = false }

# Ask if the user agrees (yes or no)
agree("Do something?")

# Asks on a single line (note the space after ':')
ask("Name: ")

# Asks with new line after "Description:"
ask("Description:")

# Calls Date#parse to parse the date string passed
ask("Birthday? ", Date)

# Ensures Integer is within the range specified
ask("Age? ", Integer) { |q| q.in = 0..105 }

# Asks for a list of strings, converts to array
ask("Fav colors?", Array)
```

## HighLine & Interaction Additions

In addition to highline's fantastic choice of methods, commander adds the
following methods to simplify common tasks:

```ruby
# Ask for password
password

# Ask for password with specific message and mask character
password "Enter your password please:", '-'

# Ask for CLASS, which may be any valid class responding to #parse. Date, Time, Array, etc
names = ask_for_array 'Names: '
bday = ask_for_date 'Birthday?: '

# Simple progress bar (Commander::UI::ProgressBar)
uris = %w[
  http://vision-media.ca
  http://google.com
  http://yahoo.com
]
progress uris do |uri|
  res = open uri
  # Do something with response
end

# 'Log' action to stdout
log "create", "path/to/file.rb"

# Enable paging of output after this point
enable_paging

# Ask editor for input (EDITOR environment variable or whichever is available: TextMate, vim, vi, emacs, nano, pico)
ask_editor

# Ask editor, supplying initial text
ask_editor 'previous data to update'

# Ask editor, preferring a specific editor
ask_editor 'previous data', 'vim'

# Choose from an array of elements
choice = choose("Favorite language?", :ruby, :perl, :js)

# Alter IO for the duration of the block
io new_input, new_output do
  new_input_contents = $stdin.read
  puts new_input_contents # outputs to new_output stream
end
# $stdin / $stdout reset back to original streams

# Speech synthesis
speak 'What is your favorite food? '
food = ask 'favorite food?: '
speak "Wow, I like #{food} too. We have so much in common."
speak "I like #{food} as well!", "Victoria", 190

# Execute arbitrary applescript
applescript 'foo'

# Converse with speech recognition server
case converse 'What is the best food?', :cookies => 'Cookies', :unknown => 'Nothing'
when :cookies
  speak 'o.m.g. you are awesome!'
else
  case converse 'That is lame, shall I convince you cookies are the best?', :yes => 'Ok', :no => 'No', :maybe => 'Maybe another time'
  when :yes
    speak 'Well you see, cookies are just fantastic, they melt in your mouth.'
  else
    speak 'Ok then, bye.'
  end
end
```

## Growl Notifications

Commander provides methods for displaying Growl notifications. To use these
methods you need to install https://github.com/tj/growl which utilizes
the [growlnotify](https://growl.info/extras.php#growlnotify) executable. Note that
growl is auto-imported by Commander when available, no need to require.

```ruby
# Display a generic Growl notification
notify 'Something happened'

# Display an 'info' status notification
notify_info 'You have #{emails.length} new email(s)'

# Display an 'ok' status notification
notify_ok 'Gems updated'

# Display a 'warning' status notification
notify_warning '1 gem failed installation'

# Display an 'error' status notification
notify_error "Gem #{name} failed"
```

## Commander Goodies

### Option Defaults

The options struct passed to `#action` provides a `#default` method, allowing you
to set defaults in a clean manner for options which have not been set.

```ruby
command :foo do |c|
  c.option '--interval SECONDS', Integer, 'Interval in seconds'
  c.option '--timeout SECONDS', Integer, 'Timeout in seconds'
  c.action do |args, options|
    options.default \
      :interval => 2,
      :timeout  => 60
  end
end
```

### Command Aliasing

Aliases can be created using the `#alias_command` method like below:

```ruby
command :'install gem' do |c|
  c.action { puts 'foo' }
end
alias_command :'gem install', :'install gem'
```

Or more complicated aliases can be made, passing any arguments
as if it was invoked via the command line:

```ruby
command :'install gem' do |c|
  c.syntax = 'install gem <name> [options]'
  c.option '--dest DIR', String, 'Destination directory'
  c.action { |args, options| puts "installing #{args.first} to #{options.dest}" }
end
alias_command :update, :'install gem', 'rubygems', '--dest', 'some_path'
```

```
$ foo update
# => installing rubygems to some_path
```

### Command Defaults

Although working with a command executable framework provides many
benefits over a single command implementation, sometimes you still
want the ability to create a terse syntax for your command. With that
in mind we may use `#default_command` to help with this. Considering
our previous `:'install gem'` example:

```ruby
default_command :update
```

```
$ foo
# => installing rubygems to some_path
```

Keeping in mind that commander searches for the longest possible match
when considering a command, so if you were to pass arguments to foo
like below, expecting them to be passed to `:update`, this would be incorrect,
and would end up calling `:'install gem'`, so be careful that the users do
not need to use command names within the arguments.

```
$ foo install gem
# => installing  to
```

### Long descriptions

If you need to have a long command description, keep your short description under `summary`, and consider multi-line strings for `description`:

```ruby
  program :summary, 'Stupid command that prints foo or bar.'
  program :description, %q(
#{c.summary}

More information about that stupid command that prints foo or bar.

And more
  )
```

### Additional Global Help

Arbitrary help can be added using the following `#program` symbol:

```ruby
program :help, 'Author', 'TJ Holowaychuk <tj@vision-media.ca>'
```

Which will output the rest of the help doc, along with:

    AUTHOR:

      TJ Holowaychuk <tj@vision-media.ca>

### Global Options

Although most switches will be at the command level, several are available by
default at the global level, such as `--version`, and `--help`. Using
`#global_option` you can add additional global options:

```ruby
global_option('-c', '--config FILE', 'Load config data for your commands to use') { |file| ... }
```

This method accepts the same syntax as `Commander::Command#option` so check it out for documentation.

All global options regardless of providing a block are accessable at the command level. This
means that instead of the following:

```ruby
global_option('--verbose') { $verbose = true }
...
c.action do |args, options|
  say 'foo' if $verbose
...
```

You may:

```ruby
global_option '--verbose'
...
c.action do |args, options|
  say 'foo' if options.verbose
...
```

### Formatters

Two core formatters are currently available, the default `Terminal` formatter
as well as `TerminalCompact`. To utilize a different formatter simply use
`:help_formatter` like below:

```ruby
program :help_formatter, Commander::HelpFormatter::TerminalCompact
```

Or utilize the help formatter aliases:

```ruby
program :help_formatter, :compact
```

This abstraction could be utilized to generate HTML documentation for your executable.

### Tracing

By default the `-t` and `--trace` global options are provided to allow users to get a backtrace to aid debugging.

You can disable these options:

```ruby
never_trace!
```

Or make it always on:

```ruby
always_trace!
```

## Tips

When adding a global or command option, OptionParser implicitly adds a small
switch even when not explicitly created, for example `-c` will be the same as
`--config` in both examples, however `-c` will only appear in the documentation
when explicitly assigning it.

```ruby
global_option '-c', '--config FILE'
global_option '--config FILE'
```

## ASCII Tables

For feature rich ASCII tables for your terminal app check out the terminal-table gem at https://github.com/tj/terminal-table

    +----------+-------+----+--------+-----------------------+
    | Terminal | Table | Is | Wicked | Awesome               |
    +----------+-------+----+--------+-----------------------+
    |          |       |    |        | get it while its hot! |
    +----------+-------+----+--------+-----------------------+

## Running Specifications

    $ rake spec

OR

    $ spec --color spec

## Contrib

Feel free to fork and request a pull, or submit a ticket
https://github.com/commander-rb/commander/issues

## License

This project is available under the MIT license. See LICENSE for details.
