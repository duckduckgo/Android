# WordWrap

[![Gem Version](https://badge.fury.io/rb/word_wrap.png)](http://badge.fury.io/rb/word_wrap)

This gem is a extremely simple tool to word-wrap texts, which is the one and
only thing it can do. It comes with a script called `ww` that you can use
in the command line. And of course, you can get the functionality from within
Ruby as well.

For more information on usage, please refer to the **Usage** section of this
README bellow.

## Installation

Add this line to your application's Gemfile:

    gem 'word_wrap'

And then execute:

    $ bundle

Or install it yourself as:

    $ gem install word_wrap

## Usage

You can use either the binary for the command line or the library directly
from your Ruby scripts. Both use cases are explained bellow.

### Command Line

When in shell, you can use the `ww` tool (ww stands for word-wrap). It takes
only two arguments:

* `-w W, --width WIDTH` - The width to which the text should be wrapped.
                          It is set to 80 by default.
* `-f, --fit` - In this case, the program will also rearrange lines, that are
                shorter than 80 to fit them as much as possible to the
                predefined width, in addition to wrapping the lines that exceed
                it. This option is generally better for plain text. For code,
                however, it will mess up your indentation.

#### Examples

The example file looks like this:

```bash
$ cat hip.txt
Forage Shoreditch disrupt Pitchfork meh.

Mustache 3 wolf moon gluten-free whatever master burn
vinyl.
```

```bash
$ ww -w 20 hip.txt
Forage Shoreditch
disrupt Pitchfork
meh.

Mustache 3 wolf moon
gluten-free whatever
master burn
vinyl.
```

But you can also use stdin:

```bash
$ cat hip | ww -w 20
Forage Shoreditch
disrupt Pitchfork
meh.

Mustache 3 wolf moon
gluten-free whatever
master burn
vinyl.
```

Note the difference at end of the second paragraph:

```bash
$ cat hip | ww -w 20 -f
Forage Shoreditch
disrupt Pitchfork
meh.

Mustache 3 wolf moon
gluten-free whatever
master burn vinyl.
```

### Ruby library

If you would like to use the library in Ruby, you have two options:

* Use the `WordWrap#ww` function directly
* Use the `String#wrap` and `String#fit` functions this module adds to the
  standard `String` class.

#### Examples

```irb
irb(main):001:0> require 'word_wrap'
=> true

irb(main):002:0> WordWrap.ww "123 456 789", 5
=> "123\n456\n789\n"

irb(main):003:0> "123 456 789".wrap 5
=> "123\n456\n789\n"

irb(main):004:0> "123 456 789".fit 8
=> "123 456\n789\n"
```

## Contributing

1. Fork it ( http://github.com/pazdera/word_wrap/fork )
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create new Pull Request
