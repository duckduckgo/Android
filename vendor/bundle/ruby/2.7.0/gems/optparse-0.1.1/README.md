# OptionParser

OptionParser is a class for command-line option analysis.  It is much more
advanced, yet also easier to use, than GetoptLong, and is a more Ruby-oriented
solution.

## Features

1. The argument specification and the code to handle it are written in the
   same place.
2. It can output an option summary; you don't need to maintain this string
   separately.
3. Optional and mandatory arguments are specified very gracefully.
4. Arguments can be automatically converted to a specified class.
5. Arguments can be restricted to a certain set.

## Installation

Add this line to your application's Gemfile:

```ruby
gem 'optparse'
```

And then execute:

    $ bundle install

Or install it yourself as:

    $ gem install optparse

## Usage

```ruby
require 'optparse'

options = {}
OptionParser.new do |opts|
  opts.banner = "Usage: example.rb [options]"

  opts.on("-v", "--[no-]verbose", "Run verbosely") do |v|
    options[:verbose] = v
  end
end.parse!

p options
p ARGV
```

## Development

After checking out the repo, run `bin/setup` to install dependencies. Then, run `rake test` to run the tests. You can also run `bin/console` for an interactive prompt that will allow you to experiment.

To install this gem onto your local machine, run `bundle exec rake install`. To release a new version, update the version number in `version.rb`, and then run `bundle exec rake release`, which will create a git tag for the version, push git commits and tags, and push the `.gem` file to [rubygems.org](https://rubygems.org).

## Contributing

Bug reports and pull requests are welcome on GitHub at https://github.com/ruby/optparse.
