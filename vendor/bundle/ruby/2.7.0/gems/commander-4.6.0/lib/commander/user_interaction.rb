# frozen_string_literal: true

require 'tempfile'
require 'shellwords'

module Commander
  ##
  # = User Interaction
  #
  # Commander's user interaction module mixes in common
  # methods which extend HighLine's functionality such
  # as a #password method rather than calling #ask directly.

  module UI
    module_function

    #--
    # Auto include growl when available.
    #++

    begin
      require 'growl'
    rescue LoadError
      # Do nothing
    else
      include Growl
    end

    ##
    # Ask the user for a password. Specify a custom
    # _message_ other than 'Password: ' or override the
    # default _mask_ of '*'.

    def password(message = 'Password: ', mask = '*')
      pass = ask(message) { |q| q.echo = mask }
      pass = password message, mask if pass.nil? || pass.empty?
      pass
    end

    ##
    # Choose from a set array of _choices_.

    def choose(message = nil, *choices, &block)
      say message if message
      super(*choices, &block)
    end

    ##
    # 'Log' an _action_ to the terminal. This is typically used
    # for verbose output regarding actions performed. For example:
    #
    #   create  path/to/file.rb
    #   remove  path/to/old_file.rb
    #   remove  path/to/old_file2.rb
    #

    def log(action, *args)
      say format('%15s  %s', action, args.join(' '))
    end

    ##
    # 'Say' something using the OK color (green).
    #
    # === Examples
    #   say_ok 'Everything is fine'
    #   say_ok 'It is ok', 'This is ok too'
    #

    def say_ok(*args)
      args.each do |arg|
        say HighLine.default_instance.color(arg, :green)
      end
    end

    ##
    # 'Say' something using the WARNING color (yellow).
    #
    # === Examples
    #   say_warning 'This is a warning'
    #   say_warning 'Be careful', 'Think about it'
    #

    def say_warning(*args)
      args.each do |arg|
        say HighLine.default_instance.color(arg, :yellow)
      end
    end

    ##
    # 'Say' something using the ERROR color (red).
    #
    # === Examples
    #   say_error 'Everything is not fine'
    #   say_error 'It is not ok', 'This is not ok too'
    #

    def say_error(*args)
      args.each do |arg|
        say HighLine.default_instance.color(arg, :red)
      end
    end

    ##
    # 'Say' something using the specified color
    #
    # === Examples
    #   color 'I am blue', :blue
    #   color 'I am bold', :bold
    #   color 'White on Red', :white, :on_red
    #
    # === Notes
    #   You may use:
    #   * color:    black blue cyan green magenta red white yellow
    #   * style:    blink bold clear underline
    #   * highligh: on_<color>

    def color(*args)
      say HighLine.default_instance.color(*args)
    end

    ##
    # Speak _message_ using _voice_ at a speaking rate of _rate_
    #
    # Voice defaults to 'Alex', which is one of the better voices.
    # Speaking rate defaults to 175 words per minute
    #
    # === Examples
    #
    #   speak 'What is your favorite food? '
    #   food = ask 'favorite food?: '
    #   speak "Wow, I like #{food} too. We have so much in common."
    #   speak "I like #{food} as well!", "Victoria", 190
    #
    # === Notes
    #
    # * MacOS only
    #

    def speak(message, voice = :Alex, rate = 175)
      Thread.new { applescript "say #{message.inspect} using #{voice.to_s.inspect} speaking rate #{rate}" }
    end

    ##
    # Converse with speech recognition.
    #
    # Currently a "poorman's" DSL to utilize applescript and
    # the MacOS speech recognition server.
    #
    # === Examples
    #
    #   case converse 'What is the best food?', :cookies => 'Cookies', :unknown => 'Nothing'
    #   when :cookies
    #     speak 'o.m.g. you are awesome!'
    #   else
    #     case converse 'That is lame, shall I convince you cookies are the best?', :yes => 'Ok', :no => 'No', :maybe => 'Maybe another time'
    #     when :yes
    #       speak 'Well you see, cookies are just fantastic.'
    #     else
    #       speak 'Ok then, bye.'
    #     end
    #   end
    #
    # === Notes
    #
    # * MacOS only
    #

    def converse(prompt, responses = {})
      i, commands = 0, responses.map { |_key, value| value.inspect }.join(',')
      statement = responses.inject '' do |inner_statement, (key, value)|
        inner_statement <<
        (
          (i += 1) == 1 ?
          %(if response is "#{value}" then\n) :
          %(else if response is "#{value}" then\n)
        ) <<
        %(do shell script "echo '#{key}'"\n)
      end
      applescript(
        %(
        tell application "SpeechRecognitionServer"
          set response to listen for {#{commands}} with prompt "#{prompt}"
          #{statement}
          end if
        end tell
        )
      ).strip.to_sym
    end

    ##
    # Execute apple _script_.

    def applescript(script)
      `osascript -e "#{ script.gsub('"', '\"') }"`
    end

    ##
    # Normalize IO streams, allowing for redirection of
    # +input+ and/or +output+, for example:
    #
    #   $ foo              # => read from terminal I/O
    #   $ foo in           # => read from 'in' file, output to terminal output stream
    #   $ foo in out       # => read from 'in' file, output to 'out' file
    #   $ foo < in > out   # => equivalent to above (essentially)
    #
    # Optionally a +block+ may be supplied, in which case
    # IO will be reset once the block has executed.
    #
    # === Examples
    #
    #   command :foo do |c|
    #     c.syntax = 'foo [input] [output]'
    #     c.when_called do |args, options|
    #       # or io(args.shift, args.shift)
    #       io *args
    #       str = $stdin.gets
    #       puts 'input was: ' + str.inspect
    #     end
    #   end
    #

    def io(input = nil, output = nil, &block)
      orig_stdin, orig_stdout = $stdin, $stdout
      $stdin = File.new(input) if input
      $stdout = File.new(output, 'r+') if output
      return unless block

      yield
      $stdin, $stdout = orig_stdin, orig_stdout
      reset_io
    end

    ##
    # Find an editor available in path. Optionally supply the _preferred_
    # editor. Returns the name as a string, nil if none is available.

    def available_editor(preferred = nil)
      [preferred, ENV['EDITOR'], 'mate -w', 'vim', 'vi', 'emacs', 'nano', 'pico']
        .compact
        .find { |name| system("hash #{name.split.first} 2>&-") }
    end

    ##
    # Prompt an editor for input. Optionally supply initial
    # _input_ which is written to the editor.
    #
    # _preferred_editor_ can be hinted.
    #
    # === Examples
    #
    #   ask_editor                # => prompts EDITOR with no input
    #   ask_editor('foo')         # => prompts EDITOR with default text of 'foo'
    #   ask_editor('foo', 'mate -w')  # => prompts TextMate with default text of 'foo'
    #

    def ask_editor(input = nil, preferred_editor = nil)
      editor = available_editor preferred_editor
      program = Commander::Runner.instance.program(:name).downcase rescue 'commander'
      tmpfile = Tempfile.new program
      begin
        tmpfile.write input if input
        tmpfile.close
        system("#{editor} #{tmpfile.path.shellescape}") ? IO.read(tmpfile.path) : nil
      ensure
        tmpfile.unlink
      end
    end

    ##
    # Enable paging of output after called.

    def enable_paging
      return unless $stdout.tty?
      return unless Process.respond_to? :fork

      read, write = IO.pipe

      # Kernel.fork is not supported on all platforms and configurations.
      # As of Ruby 1.9, `Process.respond_to? :fork` should return false on
      # configurations that don't support it, but versions before 1.9 don't
      # seem to do this reliably and instead raise a NotImplementedError
      # (which is rescued below).

      if Kernel.fork
        $stdin.reopen read
        write.close
        read.close
        Kernel.select [$stdin]
        ENV['LESS'] = 'FSRX' unless ENV.key? 'LESS'
        pager = ENV['PAGER'] || 'less'
        exec pager rescue exec '/bin/sh', '-c', pager
      else
        # subprocess
        $stdout.reopen write
        $stderr.reopen write if $stderr.tty?
        write.close
        read.close
      end
    rescue NotImplementedError
    ensure
      write.close if write && !write.closed?
      read.close if read && !read.closed?
    end

    ##
    # Output progress while iterating _arr_.
    #
    # === Examples
    #
    #   uris = %w( http://vision-media.ca http://google.com )
    #   progress uris, :format => "Remaining: :time_remaining" do |uri|
    #     res = open uri
    #   end
    #

    def progress(arr, options = {})
      bar = ProgressBar.new arr.length, options
      bar.show
      arr.each { |v| bar.increment yield(v) }
    end

    ##
    # Implements ask_for_CLASS methods.

    module AskForClass
      DEPRECATED_CONSTANTS = %i[Config TimeoutError MissingSourceFile NIL TRUE FALSE Fixnum Bignum Data].freeze

      # define methods for common classes
      [Float, Integer, String, Symbol, Regexp, Array, File, Pathname].each do |klass|
        define_method "ask_for_#{klass.to_s.downcase}" do |prompt|
          HighLine.default_instance.ask(prompt, klass)
        end
      end

      def method_missing(method_name, *arguments, &block)
        if method_name.to_s =~ /^ask_for_(.*)/
          if arguments.count != 1
            fail ArgumentError, "wrong number of arguments (given #{arguments.count}, expected 1)"
          end

          prompt = arguments.first
          requested_class = Regexp.last_match[1]

          # All Classes that respond to #parse
          # Ignore constants that trigger deprecation warnings
          available_classes = (Object.constants - DEPRECATED_CONSTANTS).map do |const|
            begin
              Object.const_get(const)
            rescue RuntimeError
              # Rescue errors in Ruby 3 for SortedSet:
              # The `SortedSet` class has been extracted from the `set` library.
            end
          end.compact.select do |const|
            const.instance_of?(Class) && const.respond_to?(:parse)
          end

          klass = available_classes.find { |k| k.to_s.downcase == requested_class }
          if klass
            HighLine.default_instance.ask(prompt, klass)
          else
            super
          end
        else
          super
        end
      end

      def respond_to_missing?(method_name, include_private = false)
        method_name.to_s.start_with?('ask_for_') || super
      end
    end

    ##
    # Substitute _hash_'s keys with their associated values in _str_.

    def replace_tokens(str, hash) #:nodoc:
      hash.inject(str) do |string, (key, value)|
        string.gsub ":#{key}", value.to_s
      end
    end

    ##
    # = Progress Bar
    #
    # Terminal progress bar utility. In its most basic form
    # requires that the developer specifies when the bar should
    # be incremented. Note that a hash of tokens may be passed to
    # #increment, (or returned when using Object#progress).
    #
    #   uris = %w(
    #     http://vision-media.ca
    #     http://yahoo.com
    #     http://google.com
    #     )
    #
    #   bar = Commander::UI::ProgressBar.new uris.length, options
    #   threads = []
    #   uris.each do |uri|
    #     threads << Thread.new do
    #       begin
    #         res = open uri
    #         bar.increment :uri => uri
    #       rescue Exception => e
    #         bar.increment :uri => "#{uri} failed"
    #       end
    #     end
    #   end
    #   threads.each { |t| t.join }
    #
    # The Object method #progress is also available:
    #
    #   progress uris, :width => 10 do |uri|
    #     res = open uri
    #     { :uri => uri } # Can now use :uri within :format option
    #   end
    #

    class ProgressBar
      ##
      # Creates a new progress bar.
      #
      # === Options
      #
      #   :title              Title, defaults to "Progress"
      #   :width              Width of :progress_bar
      #   :progress_str       Progress string, defaults to "="
      #   :incomplete_str     Incomplete bar string, defaults to '.'
      #   :format             Defaults to ":title |:progress_bar| :percent_complete% complete "
      #   :tokens             Additional tokens replaced within the format string
      #   :complete_message   Defaults to "Process complete"
      #
      # === Tokens
      #
      #   :title
      #   :percent_complete
      #   :progress_bar
      #   :step
      #   :steps_remaining
      #   :total_steps
      #   :time_elapsed
      #   :time_remaining
      #

      def initialize(total, options = {})
        @total_steps, @step, @start_time = total, 0, Time.now
        @title = options.fetch :title, 'Progress'
        @width = options.fetch :width, 25
        @progress_str = options.fetch :progress_str, '='
        @incomplete_str = options.fetch :incomplete_str, '.'
        @complete_message = options.fetch :complete_message, 'Process complete'
        @format = options.fetch :format, ':title |:progress_bar| :percent_complete% complete '
        @tokens = options.fetch :tokens, {}
      end

      ##
      # Completion percentage.

      def percent_complete
        if @total_steps.zero?
          100
        else
          @step * 100 / @total_steps
        end
      end

      ##
      # Time that has elapsed since the operation started.

      def time_elapsed
        Time.now - @start_time
      end

      ##
      # Estimated time remaining.

      def time_remaining
        (time_elapsed / @step) * steps_remaining
      end

      ##
      # Number of steps left.

      def steps_remaining
        @total_steps - @step
      end

      ##
      # Formatted progress bar.

      def progress_bar
        (@progress_str * (@width * percent_complete / 100)).ljust @width, @incomplete_str
      end

      ##
      # Generates tokens for this step.

      def generate_tokens
        {
          title: @title,
          percent_complete: percent_complete,
          progress_bar: progress_bar,
          step: @step,
          steps_remaining: steps_remaining,
          total_steps: @total_steps,
          time_elapsed: format('%0.2fs', time_elapsed),
          time_remaining: @step.positive? ? format('%0.2fs', time_remaining) : '',
        }.merge! @tokens
      end

      ##
      # Output the progress bar.

      def show
        return if finished?

        erase_line
        if completed?
          HighLine.default_instance.say UI.replace_tokens(@complete_message, generate_tokens) if @complete_message.is_a? String
        else
          HighLine.default_instance.say UI.replace_tokens(@format, generate_tokens) << ' '
        end
      end

      ##
      # Whether or not the operation is complete, and we have finished.

      def finished?
        @step == @total_steps + 1
      end

      ##
      # Whether or not the operation has completed.

      def completed?
        @step == @total_steps
      end

      ##
      # Increment progress. Optionally pass _tokens_ which
      # can be displayed in the output format.

      def increment(tokens = {})
        @step += 1
        @tokens.merge! tokens if tokens.is_a? Hash
        show
      end

      ##
      # Erase previous terminal line.

      def erase_line
        # highline does not expose the output stream
        HighLine.default_instance.instance_variable_get('@output').print "\r\e[K"
      end
    end
  end
end
