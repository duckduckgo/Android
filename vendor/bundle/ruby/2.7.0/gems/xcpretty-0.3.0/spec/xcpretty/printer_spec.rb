require "xcpretty/printer"
require 'xcpretty/formatters/formatter'
require 'xcpretty/formatters/simple'

module XCPretty
  describe Printer do

    before(:each) do
      STDOUT.stub(:print) { |text| text }
      @printer = Printer.new(colorize: true, unicode: true, formatter: DummyFormatter)
    end

    it "prints to stdout" do
      STDOUT.should receive(:print).with("hey ho let's go\n")
      @printer.pretty_print("hey ho let's go")
    end

    it "doesn't print empty lines" do
      STDOUT.should_not receive(:print)
      @printer.pretty_print("")
    end

    it "prints with newlines only when needed" do
      @printer.formatter.stub(:optional_newline).and_return("")

      STDOUT.should receive(:print).with("hey ho let's go")
      @printer.pretty_print("hey ho let's go")
    end

    it "makes a formatter with unicode and colorized flags" do
      @printer.formatter.colorize?.should == true
      @printer.formatter.use_unicode?.should == true
    end

  end
end

module XCPretty
  class DummyFormatter < Formatter

    def initialize(unicode, colorize)
      @use_unicode = unicode
      @colorize = colorize
    end

    def pretty_format(text)
      text
    end

    def optional_newline
      "\n"
    end
  end
end

