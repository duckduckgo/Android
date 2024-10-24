# frozen_string_literal: true

require 'erb'

module Commander
  module HelpFormatter
    class TerminalCompact < Terminal
      def template(name)
        if RUBY_VERSION < '2.6'
          ERB.new(File.read(File.join(File.dirname(__FILE__), 'terminal_compact', "#{name}.erb")), nil, '-')
        else
          ERB.new(File.read(File.join(File.dirname(__FILE__), 'terminal_compact', "#{name}.erb")), trim_mode: '-')
        end
      end
    end
  end
end
