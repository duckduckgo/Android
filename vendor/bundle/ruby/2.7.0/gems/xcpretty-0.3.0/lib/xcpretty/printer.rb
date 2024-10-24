require "xcpretty/ansi"

module XCPretty

  class Printer

    attr_reader :formatter

    def initialize(params)
      klass = params[:formatter]
      @formatter = klass.new(params[:unicode], params[:colorize])
    end

    def finish
      @formatter.finish
    end

    def pretty_print(text)
      formatted_text = formatter.pretty_format(text)
      unless formatted_text.empty?
        STDOUT.print(formatted_text + formatter.optional_newline)
        STDOUT.flush
      end
    end

  end
end

