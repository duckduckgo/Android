# frozen_string_literal: true

module Commander
  ##
  # = Help Formatter
  #
  # Commander's help formatters control the output when
  # either the help command, or --help switch are called.
  # The default formatter is Commander::HelpFormatter::Terminal.

  module HelpFormatter
    class Base
      def initialize(runner)
        @runner = runner
      end

      def render
        'Implement global help here'
      end

      def render_command(command)
        "Implement help for #{command.name} here"
      end
    end
  end
end
