# frozen_string_literal: true

require 'optparse'

module Commander
  class Runner
    #--
    # Exceptions
    #++

    class CommandError < StandardError; end

    class InvalidCommandError < CommandError; end

    attr_reader :commands, :options, :help_formatter_aliases

    ##
    # Initialize a new command runner. Optionally
    # supplying _args_ for mocking, or arbitrary usage.

    def initialize(args = ARGV)
      @args, @commands, @aliases, @options = args, {}, {}, []
      @help_formatter_aliases = help_formatter_alias_defaults
      @program = program_defaults
      @always_trace = false
      @never_trace = false
      create_default_commands
    end

    ##
    # Return singleton Runner instance.

    def self.instance
      @instance ||= new
    end

    ##
    # Run command parsing and execution process.

    def run!
      trace = @always_trace || false
      require_program :version, :description
      trap('INT') { abort program(:int_message) } if program(:int_message)
      trap('INT') { program(:int_block).call } if program(:int_block)
      global_option('-h', '--help', 'Display help documentation') do
        args = @args - %w(-h --help)
        command(:help).run(*args)
        return
      end
      global_option('-v', '--version', 'Display version information') do
        say version
        return
      end
      global_option('-t', '--trace', 'Display backtrace when an error occurs') { trace = true } unless @never_trace || @always_trace
      parse_global_options
      remove_global_options options, @args
      if trace
        run_active_command
      else
        begin
          run_active_command
        rescue InvalidCommandError => e
          abort "#{e}. Use --help for more information"
        rescue \
          OptionParser::InvalidOption,
          OptionParser::InvalidArgument,
          OptionParser::MissingArgument => e
          abort e.to_s
        rescue StandardError => e
          if @never_trace
            abort "error: #{e}."
          else
            abort "error: #{e}. Use --trace to view backtrace"
          end
        end
      end
    end

    ##
    # Return program version.

    def version
      format('%s %s', program(:name), program(:version))
    end

    ##
    # Enable tracing on all executions (bypasses --trace)

    def always_trace!
      @always_trace = true
      @never_trace = false
    end

    ##
    # Hide the trace option from the help menus and don't add it as a global option

    def never_trace!
      @never_trace = true
      @always_trace = false
    end

    ##
    # Assign program information.
    #
    # === Examples
    #
    #   # Set data
    #   program :name, 'Commander'
    #   program :version, Commander::VERSION
    #   program :description, 'Commander utility program.'
    #   program :help, 'Copyright', '2008 TJ Holowaychuk'
    #   program :help, 'Anything', 'You want'
    #   program :int_message 'Bye bye!'
    #   program :help_formatter, :compact
    #   program :help_formatter, Commander::HelpFormatter::TerminalCompact
    #
    #   # Get data
    #   program :name # => 'Commander'
    #
    # === Keys
    #
    #   :version         (required) Program version triple, ex: '0.0.1'
    #   :description     (required) Program description
    #   :name            Program name, defaults to basename of executable
    #   :help_formatter  Defaults to Commander::HelpFormatter::Terminal
    #   :help            Allows addition of arbitrary global help blocks
    #   :help_paging     Flag for toggling help paging
    #   :int_message     Message to display when interrupted (CTRL + C)
    #

    def program(key, *args, &block)
      if key == :help && !args.empty?
        @program[:help] ||= {}
        @program[:help][args.first] = args.at(1)
      elsif key == :help_formatter && !args.empty?
        @program[key] = (@help_formatter_aliases[args.first] || args.first)
      elsif block
        @program[key] = block
      else
        unless args.empty?
          @program[key] = args.count == 1 ? args[0] : args
        end
        @program[key]
      end
    end

    ##
    # Creates and yields a command instance when a block is passed.
    # Otherwise attempts to return the command, raising InvalidCommandError when
    # it does not exist.
    #
    # === Examples
    #
    #   command :my_command do |c|
    #     c.when_called do |args|
    #       # Code
    #     end
    #   end
    #

    def command(name, &block)
      yield add_command(Commander::Command.new(name)) if block
      @commands[name.to_s]
    end

    ##
    # Add a global option; follows the same syntax as Command#option
    # This would be used for switches such as --version, --trace, etc.

    def global_option(*args, &block)
      switches, description = Runner.separate_switches_from_description(*args)
      @options << {
        args: args,
        proc: block,
        switches: switches,
        description: description,
      }
    end

    ##
    # Alias command _name_ with _alias_name_. Optionally _args_ may be passed
    # as if they were being passed straight to the original command via the command-line.

    def alias_command(alias_name, name, *args)
      @commands[alias_name.to_s] = command name
      @aliases[alias_name.to_s] = args
    end

    ##
    # Default command _name_ to be used when no other
    # command is found in the arguments.

    def default_command(name)
      @default_command = name
    end

    ##
    # Add a command object to this runner.

    def add_command(command)
      @commands[command.name] = command
    end

    ##
    # Check if command _name_ is an alias.

    def alias?(name)
      @aliases.include? name.to_s
    end

    ##
    # Check if a command _name_ exists.

    def command_exists?(name)
      @commands[name.to_s]
    end

    #:stopdoc:

    ##
    # Get active command within arguments passed to this runner.

    def active_command
      @active_command ||= command(command_name_from_args)
    end

    ##
    # Attempts to locate a command name from within the arguments.
    # Supports multi-word commands, using the largest possible match.
    # Returns the default command, if no valid commands found in the args.

    def command_name_from_args
      @command_name_from_args ||= (longest_valid_command_name_from(@args) || @default_command)
    end

    ##
    # Returns array of valid command names found within _args_.

    def valid_command_names_from(*args)
      remove_global_options options, args
      arg_string = args.delete_if { |value| value =~ /^-/ }.join ' '
      commands.keys.find_all { |name| name if arg_string =~ /^#{name}\b/ }
    end

    ##
    # Help formatter instance.

    def help_formatter
      @help_formatter ||= program(:help_formatter).new self
    end

    ##
    # Return arguments without the command name.

    def args_without_command_name
      removed = []
      parts = command_name_from_args.split rescue []
      @args.dup.delete_if do |arg|
        removed << arg if parts.include?(arg) && !removed.include?(arg)
      end
    end

    ##
    # Returns hash of help formatter alias defaults.

    def help_formatter_alias_defaults
      {
        compact: HelpFormatter::TerminalCompact,
      }
    end

    ##
    # Returns hash of program defaults.

    def program_defaults
      {
        help_formatter: HelpFormatter::Terminal,
        name: File.basename($PROGRAM_NAME),
        help_paging: true,
      }
    end

    ##
    # Creates default commands such as 'help' which is
    # essentially the same as using the --help switch.

    def create_default_commands
      command :help do |c|
        c.syntax = 'commander help [command]'
        c.description = 'Display global or [command] help documentation'
        c.example 'Display global help', 'command help'
        c.example "Display help for 'foo'", 'command help foo'
        c.when_called do |args, _options|
          UI.enable_paging if program(:help_paging)
          if args.empty?
            say help_formatter.render
          else
            command = command(longest_valid_command_name_from(args))
            begin
              require_valid_command command
            rescue InvalidCommandError => e
              abort "#{e}. Use --help for more information"
            end
            say help_formatter.render_command(command)
          end
        end
      end
    end

    ##
    # Raises InvalidCommandError when a _command_ is not found.

    def require_valid_command(command = active_command)
      fail InvalidCommandError, 'invalid command', caller if command.nil?
    end

    ##
    # Removes global _options_ from _args_. This prevents an invalid
    # option error from occurring when options are parsed
    # again for the command.

    def remove_global_options(options, args)
      options.each do |option|
        switches = option[:switches]
        next if switches.empty?

        option_takes_argument = switches.any? { |s| s =~ /[ =]/ }
        switches = expand_optionally_negative_switches(switches)

        option_argument_needs_removal = false
        args.delete_if do |token|
          break if token == '--'

          # Use just the portion of the token before the = when
          # comparing switches.
          index_of_equals = token.index('=') if option_takes_argument
          token = token[0, index_of_equals] if index_of_equals
          token_contains_option_argument = !index_of_equals.nil?

          if switches.any? { |s| s[0, token.length] == token }
            option_argument_needs_removal =
              option_takes_argument && !token_contains_option_argument
            true
          elsif option_argument_needs_removal && token !~ /^-/
            option_argument_needs_removal = false
            true
          else
            option_argument_needs_removal = false
            false
          end
        end
      end
    end

    # expand switches of the style '--[no-]blah' into both their
    # '--blah' and '--no-blah' variants, so that they can be
    # properly detected and removed
    def expand_optionally_negative_switches(switches)
      switches.reduce([]) do |memo, val|
        if val =~ /\[no-\]/
          memo << val.gsub(/\[no-\]/, '')
          memo << val.gsub(/\[no-\]/, 'no-')
        else
          memo << val
        end
      end
    end

    ##
    # Parse global command options.

    def parse_global_options
      parser = options.inject(OptionParser.new) do |options, option|
        options.on(*option[:args], &global_option_proc(option[:switches], &option[:proc]))
      end

      options = @args.dup
      begin
        parser.parse!(options)
      rescue OptionParser::InvalidOption => e
        # Remove the offending args and retry.
        options = options.reject { |o| e.args.include?(o) }
        retry
      end
    end

    ##
    # Returns a proc allowing for commands to inherit global options.
    # This functionality works whether a block is present for the global
    # option or not, so simple switches such as --verbose can be used
    # without a block, and used throughout all commands.

    def global_option_proc(switches, &block)
      lambda do |value|
        unless active_command.nil?
          active_command.global_options << [Runner.switch_to_sym(switches.last), value]
        end
        yield value if block && !value.nil?
      end
    end

    ##
    # Raises a CommandError when the program any of the _keys_ are not present, or empty.

    def require_program(*keys)
      keys.each do |key|
        fail CommandError, "program #{key} required" if program(key).nil? || program(key).empty?
      end
    end

    ##
    # Return switches and description separated from the _args_ passed.

    def self.separate_switches_from_description(*args)
      switches = args.find_all { |arg| arg.to_s =~ /^-/ }
      description = args.last if args.last.is_a?(String) && !args.last.match(/^-/)
      [switches, description]
    end

    ##
    # Attempts to generate a method name symbol from +switch+.
    # For example:
    #
    #   -h                 # => :h
    #   --trace            # => :trace
    #   --some-switch      # => :some_switch
    #   --[no-]feature     # => :feature
    #   --file FILE        # => :file
    #   --list of,things   # => :list
    #

    def self.switch_to_sym(switch)
      switch.scan(/[\-\]](\w+)/).join('_').to_sym rescue nil
    end

    ##
    # Run the active command.

    def run_active_command
      require_valid_command
      if alias? command_name_from_args
        active_command.run(*(@aliases[command_name_from_args.to_s] + args_without_command_name))
      else
        active_command.run(*args_without_command_name)
      end
    end

    def say(*args) #:nodoc:
      HighLine.default_instance.say(*args)
    end

    private

    ##
    # Attempts to locate a command name from within the provided arguments.
    # Supports multi-word commands, using the largest possible match.

    def longest_valid_command_name_from(args)
      valid_command_names_from(*args.dup).max
    end
  end
end
