# frozen_string_literal: true

require 'rubygems'
require 'stringio'
require 'simplecov'
SimpleCov.start do
  add_filter '/spec/'
end

# Unshift so that local files load instead of something in gems
$LOAD_PATH.unshift "#{File.dirname(__FILE__)}/../lib"

# This basically replicates the behavior of `require 'commander/import'`
# but without adding an `at_exit` hook, which interferes with exit code
require 'commander'
require 'commander/methods'

# Mock terminal IO streams so we can spec against them
def mock_terminal
  @input = StringIO.new
  @output = StringIO.new
  HighLine.default_instance = HighLine.new(@input, @output)
end

# Stub Kernel.abort
TestSystemExit = Class.new(RuntimeError)
module Commander
  class Runner
    def abort(message)
      fail TestSystemExit, message
    end
  end
end

# Create test command for usage within several specs

def create_test_command
  command :test do |c|
    c.syntax = 'test [options] <file>'
    c.description = 'test description'
    c.example 'description', 'command'
    c.example 'description 2', 'command 2'
    c.option '-v', '--verbose', 'verbose description'
    c.when_called do |args, _options|
      format('test %s', args.join)
    end
  end
  @command = command :test
end

# Create a new command runner

def new_command_runner(*args, &block)
  Commander::Runner.instance_variable_set :@instance, Commander::Runner.new(args)
  program :name, 'test'
  program :version, '1.2.3'
  program :description, 'something'
  create_test_command
  yield if block
  Commander::Runner.instance
end

# Comply with how specs were previously written

def command_runner
  Commander::Runner.instance
end

def run(*args)
  new_command_runner(*args) do
    program :help_formatter, Commander::HelpFormatter::Base
    yield if block_given?
  end.run!
  @output.string
end

RSpec.configure do |c|
  c.expect_with(:rspec) do |e|
    e.syntax = :expect
  end

  c.mock_with(:rspec) do |m|
    m.syntax = :expect
  end

  c.before(:each) do
    allow(Commander::UI).to receive(:enable_paging)
  end
end
