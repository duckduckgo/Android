# frozen_string_literal: true

module Commander
  module HelpFormatter
    autoload :Base, 'commander/help_formatters/base'
    autoload :Terminal, 'commander/help_formatters/terminal'
    autoload :TerminalCompact, 'commander/help_formatters/terminal_compact'

    class Context
      def initialize(target)
        @target = target
      end

      def get_binding
        @target.instance_eval { binding }.tap do |bind|
          decorate_binding(bind)
        end
      end

      # No-op, override in subclasses.
      def decorate_binding(_bind)
      end
    end

    class ProgramContext < Context
      def decorate_binding(bind)
        bind.eval("max_command_length = #{max_command_length(bind)}")
        bind.eval("max_aliases_length = #{max_aliases_length(bind)}")
      end

      def max_command_length(bind)
        max_key_length(bind.eval('@commands'))
      end

      def max_aliases_length(bind)
        max_key_length(bind.eval('@aliases'))
      end

      def max_key_length(hash, default = 20)
        longest = hash.keys.max_by(&:size)
        longest ? longest.size : default
      end
    end

    module_function

    def indent(amount, text)
      text.to_s.gsub("\n", "\n#{' ' * amount}")
    end
  end
end
