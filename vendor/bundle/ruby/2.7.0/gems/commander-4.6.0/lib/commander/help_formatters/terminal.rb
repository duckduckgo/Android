# frozen_string_literal: true

require 'erb'

module Commander
  module HelpFormatter
    class Terminal < Base
      def render
        template(:help).result(ProgramContext.new(@runner).get_binding)
      end

      def render_command(command)
        template(:command_help).result(Context.new(command).get_binding)
      end

      def template(name)
        if RUBY_VERSION < '2.6'
          ERB.new(File.read(File.join(File.dirname(__FILE__), 'terminal', "#{name}.erb")), nil, '-')
        else
          ERB.new(File.read(File.join(File.dirname(__FILE__), 'terminal', "#{name}.erb")), trim_mode: '-')
        end
      end
    end
  end
end
