# frozen_string_literal: true

begin
  require "rbconfig"
rescue LoadError
end

require_relative "screen/version"

module TTY
  # Used for detecting screen properties
  #
  # @api public
  module Screen
    # Helper to define private functions
    def self.private_module_function(name)
      module_function(name)
      private_class_method(name)
    end

    case (defined?(::RbConfig) ? ::RbConfig::CONFIG["host_os"] : ::RUBY_PLATFORM)
    when /mswin|msys|mingw|cygwin|bccwin|wince|emc/
      def windows?; true end
    else
      def windows?; false end
    end
    module_function :windows?

    case (defined?(::RbConfig) ? ::RbConfig::CONFIG["ruby_install_name"] : ::RUBY_ENGINE)
    when /jruby/
      def jruby?; true end
    else
      def jruby?; false end
    end
    module_function :jruby?

    # Default terminal size
    #
    # @api public
    DEFAULT_SIZE = [27, 80].freeze

    @env = ENV
    @output = $stderr

    class << self
      # Holds the environment variables
      # @api public
      attr_accessor :env

      # Specifies an output stream
      # @api public
      attr_accessor :output
    end

    # Get terminal rows and columns
    #
    # @return [Array[Integer, Integer]]
    #   return rows and columns
    #
    # @api public
    def size(verbose: false)
      size_from_java(verbose: verbose) ||
      size_from_win_api(verbose: verbose) ||
      size_from_ioctl ||
      size_from_io_console(verbose: verbose) ||
      size_from_readline(verbose: verbose) ||
      size_from_tput ||
      size_from_stty ||
      size_from_env ||
      size_from_ansicon ||
      size_from_default
    end
    module_function :size

    def width
      size[1]
    end
    module_function :width

    alias columns width
    alias cols width
    module_function :columns
    module_function :cols

    def height
      size[0]
    end
    module_function :height

    alias rows height
    alias lines height
    module_function :rows
    module_function :lines

    # Default size for the terminal
    #
    # @return [Array[Integer, Integer]]
    #
    # @api private
    def size_from_default
      DEFAULT_SIZE
    end
    module_function :size_from_default

    # Determine terminal size with a Windows native API
    #
    # @return [nil, Array[Integer, Integer]]
    #
    # @api private
    if windows?
      STDOUT_HANDLE = 0xFFFFFFF5

      def size_from_win_api(verbose: false)
        require "fiddle" unless defined?(Fiddle)

        kernel32 = Fiddle::Handle.new("kernel32")
        get_std_handle = Fiddle::Function.new(kernel32["GetStdHandle"],
                          [-Fiddle::TYPE_INT], Fiddle::TYPE_INT)
        get_console_buffer_info = Fiddle::Function.new(
          kernel32["GetConsoleScreenBufferInfo"],
          [Fiddle::TYPE_LONG, Fiddle::TYPE_VOIDP], Fiddle::TYPE_INT)

        format        = "SSSSSssssSS"
        buffer        = ([0] * format.size).pack(format)
        stdout_handle = get_std_handle.(STDOUT_HANDLE)

        get_console_buffer_info.(stdout_handle, buffer)
        _, _, _, _, _, left, top, right, bottom, = buffer.unpack(format)
        size = [bottom - top + 1, right - left + 1]
        return size if nonzero_column?(size[1] - 1)
      rescue LoadError
        warn "no native fiddle module found" if verbose
      rescue Fiddle::DLError
        # non windows platform or no kernel32 lib
      end
    else
      def size_from_win_api(verbose: false); nil end
    end
    module_function :size_from_win_api

    # Determine terminal size on jruby using native Java libs
    #
    # @return [nil, Array[Integer, Integer]]
    #
    # @api private
    if jruby?
      def size_from_java(verbose: false)
        require "java"

        java_import "jline.TerminalFactory"
        terminal = TerminalFactory.get
        size = [terminal.get_height, terminal.get_width]
        return size if nonzero_column?(size[1])
      rescue
        warn "failed to import java terminal package" if verbose
      end
    else
      def size_from_java(verbose: false); nil end
    end
    module_function :size_from_java

    # Detect screen size by loading io/console lib
    #
    # On Windows io_console falls back to reading environment
    # variables. This means any user changes to the terminal
    # size won't be reflected in the runtime of the Ruby app.
    #
    # @return [nil, Array[Integer, Integer]]
    #
    # @api private
    def size_from_io_console(verbose: false)
      require "io/console" unless IO.method_defined?(:winsize)

      return unless @output.tty? && @output.respond_to?(:winsize)

      size = @output.winsize
      size if nonzero_column?(size[1])
    rescue Errno::EOPNOTSUPP
      # no support for winsize on output
    rescue LoadError
      warn "no native io/console support or io-console gem" if verbose
    end
    module_function :size_from_io_console

    if !jruby? && @output.respond_to?(:ioctl)
      TIOCGWINSZ = 0x5413 # linux
      TIOCGWINSZ_PPC = 0x40087468 # macos, freedbsd, netbsd, openbsd
      TIOCGWINSZ_SOL = 0x5468 # solaris

      # Read terminal size from Unix ioctl
      #
      # @return [nil, Array[Integer, Integer]]
      #
      # @api private
      def size_from_ioctl
        format = "SSSS"
        buffer = ([0] * format.size).pack(format)

        if ioctl?(TIOCGWINSZ, buffer) ||
           ioctl?(TIOCGWINSZ_PPC, buffer) ||
           ioctl?(TIOCGWINSZ_SOL, buffer)

          rows, cols, = buffer.unpack(format)[0..1]
          return [rows, cols] if nonzero_column?(cols)
        end
      end

      # Check if ioctl can be called and any of the streams is
      # attached to a terminal.
      #
      # @return [Boolean]
      #   True if any of the streams is attached to a terminal, false otherwise.
      #
      # @api private
      def ioctl?(control, buf)
        ($stdout.ioctl(control, buf) >= 0) ||
          ($stdin.ioctl(control, buf) >= 0) ||
          ($stderr.ioctl(control, buf) >= 0)
      rescue SystemCallError
        false
      end
      module_function :ioctl?
    else
      def size_from_ioctl; nil end
    end
    module_function :size_from_ioctl

    # Detect screen size using Readline
    #
    # @api private
    def size_from_readline(verbose: false)
      require "readline" unless defined?(::Readline)

      return unless ::Readline.respond_to?(:get_screen_size)

      size = ::Readline.get_screen_size
      size if nonzero_column?(size[1])
    rescue LoadError
      warn "no readline gem" if verbose
    rescue NotImplementedError
    end
    module_function :size_from_readline

    # Detect terminal size from tput utility
    #
    # @api private
    def size_from_tput
      return unless @output.tty? && command_exist?("tput")

      lines = run_command("tput", "lines")
      return unless lines

      cols = run_command("tput", "cols")
      [lines.to_i, cols.to_i] if nonzero_column?(lines)
    end
    module_function :size_from_tput

    # Detect terminal size from stty utility
    #
    # @api private
    def size_from_stty
      return unless @output.tty? && command_exist?("stty")

      out = run_command("stty", "size")
      return unless out

      size = out.split.map(&:to_i)
      size if nonzero_column?(size[1])
    end
    module_function :size_from_stty

    # Detect terminal size from environment
    #
    # After executing Ruby code if the user changes terminal
    # dimensions during code runtime, the code won't be notified,
    # and hence won't see the new dimensions reflected in its copy
    # of LINES and COLUMNS environment variables.
    #
    # @return [nil, Array[Integer, Integer]]
    #
    # @api private
    def size_from_env
      return unless @env["COLUMNS"] =~ /^\d+$/

      size = [(@env["LINES"] || @env["ROWS"]).to_i, @env["COLUMNS"].to_i]
      size if nonzero_column?(size[1])
    end
    module_function :size_from_env

    # Detect terminal size from Windows ANSICON
    #
    # @api private
    def size_from_ansicon
      return unless @env["ANSICON"] =~ /\((.*)x(.*)\)/

      size = [$2, $1].map(&:to_i)
      size if nonzero_column?(size[1])
    end
    module_function :size_from_ansicon

    # Check if command exists
    #
    # @return [Boolean]
    #
    # @api private
    def command_exist?(command)
      exts = env.fetch("PATHEXT", "").split(::File::PATH_SEPARATOR)
      env.fetch("PATH", "").split(::File::PATH_SEPARATOR).any? do |dir|
        file = ::File.join(dir, command)
        ::File.exist?(file) || exts.any? { |ext| ::File.exist?("#{file}#{ext}") }
      end
    end
    private_module_function :command_exist?

    # Runs command silently capturing the output
    #
    # @api private
    def run_command(*args)
      %x(#{args.join(" ")})
    rescue IOError, SystemCallError
      nil
    end
    private_module_function :run_command

    # Check if number is non zero
    #
    # return [Boolean]
    #
    # @api private
    def nonzero_column?(column)
      column.to_i > 0
    end
    private_module_function :nonzero_column?
  end # Screen
end # TTY
