# frozen_string_literal: true

module Commander
  module Delegates
    %w(
      add_command
      command
      program
      run!
      global_option
      alias_command
      default_command
      always_trace!
      never_trace!
    ).each do |meth|
      eval <<-END, binding, __FILE__, __LINE__ + 1
        def #{meth}(*args, &block)
          ::Commander::Runner.instance.#{meth}(*args, &block)
        end
      END
    end

    def defined_commands(*args, &block)
      ::Commander::Runner.instance.commands(*args, &block)
    end
  end
end
