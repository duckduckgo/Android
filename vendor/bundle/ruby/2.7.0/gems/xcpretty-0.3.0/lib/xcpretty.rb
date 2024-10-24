require 'xcpretty/version'
require 'xcpretty/printer'
require 'xcpretty/syntax'
require 'xcpretty/snippet'
require 'xcpretty/term'
require 'xcpretty/formatters/formatter'
require 'xcpretty/formatters/simple'
require 'xcpretty/formatters/rspec'
require 'xcpretty/formatters/knock'
require 'xcpretty/formatters/tap'
require 'xcpretty/reporters/reporter'
require 'xcpretty/reporters/junit'
require 'xcpretty/reporters/html'
require 'xcpretty/reporters/json_compilation_database'

module XCPretty

  def self.class_from_path(path)
    source = File.read(path)
    klass = eval(source, nil, path)
    raise unless klass.is_a?(Class)
    klass
  end

  def self.load_custom_class(path)
    $LOAD_PATH.unshift File.dirname(path)
    class_from_path(path)
  rescue SyntaxError => e
    exit_with_error("Expected custom source file to return a class. #{e}")
  end

  def self.exit_with_error(message)
    $stderr.puts "[!] #{message}"
    exit 1
  end

end

