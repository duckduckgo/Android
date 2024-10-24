# frozen_string_literal: true

require 'monitor'
require 'tty-cursor'

require_relative 'spinner/version'
require_relative 'spinner/formats'

module TTY
  # Used for creating terminal spinner
  #
  # @api public
  class Spinner
    include Formats
    include MonitorMixin

    # @raised when attempting to join dead thread
    NotSpinningError = Class.new(StandardError)

    ECMA_CSI = "\x1b["

    MATCHER = /:spinner/
    TICK = '✔'
    CROSS = '✖'

    CURSOR_LOCK = Monitor.new

    # The object that responds to print call defaulting to stderr
    #
    # @api public
    attr_reader :output

    # The current format type
    #
    # @return [String]
    #
    # @api public
    attr_reader :format

    # Whether to show or hide cursor
    #
    # @return [Boolean]
    #
    # @api public
    attr_reader :hide_cursor

    # The message to print before the spinner
    #
    # @return [String]
    #   the current message
    #
    # @api public
    attr_reader :message

    # Tokens for the message
    #
    # @return [Hash[Symbol, Object]]
    #   the current tokens
    #
    # @api public
    attr_reader :tokens

    # The amount of time between frames in auto spinning
    #
    # @api public
    attr_reader :interval

    # The current row inside the multi spinner
    #
    # @api public
    attr_reader :row

    # Initialize a spinner
    #
    # @example
    #   spinner = TTY::Spinner.new
    #
    # @param [String] message
    #   the message to print in front of the spinner
    #
    # @param [Hash] options
    # @option options [String] :format
    #   the spinner format type defaulting to :spin_1
    # @option options [Object] :output
    #   the object that responds to print call defaulting to stderr
    # @option options [Boolean] :hide_cursor
    #   display or hide cursor
    # @option options [Boolean] :clear
    #   clear ouptut when finished
    # @option options [Float] :interval
    #   the interval for auto spinning
    #
    # @api public
    def initialize(*args)
      super()
      options  = args.last.is_a?(::Hash) ? args.pop : {}
      @message = args.empty? ? ':spinner' : args.pop
      @tokens  = {}

      @format      = options.fetch(:format) { :classic }
      @output      = options.fetch(:output) { $stderr }
      @hide_cursor = options.fetch(:hide_cursor) { false }
      @frames      = options.fetch(:frames) do
                       fetch_format(@format.to_sym, :frames)
                     end
      @clear       = options.fetch(:clear) { false }
      @success_mark= options.fetch(:success_mark) { TICK }
      @error_mark  = options.fetch(:error_mark) { CROSS }
      @interval    = options.fetch(:interval) do
                       fetch_format(@format.to_sym, :interval)
                     end
      @row         = options[:row]

      @callbacks   = Hash.new { |h, k| h[k] = [] }
      @length      = @frames.length
      @thread      = nil
      @job         = nil
      @multispinner= nil
      reset
    end

    # Reset the spinner to initial frame
    #
    # @api public
    def reset
      synchronize do
        @current   = 0
        @done      = false
        @state     = :stopped
        @succeeded = false
        @first_run = true
      end
    end

    # Notifies the TTY::Spinner that it is running under a multispinner
    #
    # @param [TTY::Spinner::Multi] the multispinner that it is running under
    #
    # @api private
    def attach_to(multispinner)
      @multispinner = multispinner
    end

    # Whether the spinner has completed spinning
    #
    # @return [Boolean] whether or not the spinner has finished
    #
    # @api public
    def done?
      @done
    end

    # Whether the spinner is spinning
    #
    # @return [Boolean] whether or not the spinner is spinning
    #
    # @api public
    def spinning?
      @state == :spinning
    end

    # Whether the spinner is in the success state.
    # When true the spinner is marked with a success mark.
    #
    # @return [Boolean] whether or not the spinner succeeded
    #
    # @api public
    def success?
      @succeeded == :success
    end

    # Whether the spinner is in the error state. This is only true
    # temporarily while it is being marked with a failure mark.
    #
    # @return [Boolean] whether or not the spinner is erroring
    #
    # @api public
    def error?
      @succeeded == :error
    end

    # Register callback
    #
    # @param [Symbol] name
    #   the name for the event to listen for, e.i. :complete
    #
    # @return [self]
    #
    # @api public
    def on(name, &block)
      synchronize do
        @callbacks[name] << block
      end
      self
    end

    # Start timer and unlock spinner
    #
    # @api public
    def start
      @started_at = Time.now
      @done = false
      reset
    end

    # Add job to this spinner
    #
    # @api public
    def job(&work)
      synchronize do
        if block_given?
          @job = work
        else
          @job
        end
      end
    end

    # Execute this spinner job
    #
    # @yield [TTY::Spinner]
    #
    # @api public
    def execute_job
      job.(self) if job?
    end

    # Check if this spinner has a scheduled job
    #
    # @return [Boolean]
    #
    # @api public
    def job?
      !@job.nil?
    end

    # Start automatic spinning animation
    #
    # @api public
    def auto_spin
      CURSOR_LOCK.synchronize do
        start
        sleep_time = 1.0 / @interval

        spin
        @thread = Thread.new do
          sleep(sleep_time)
          while @started_at
            if Thread.current['pause']
              Thread.stop
              Thread.current['pause'] = false
            end
            spin
            sleep(sleep_time)
          end
        end
      end
    ensure
      if @hide_cursor
        write(TTY::Cursor.show, false)
      end
    end

    # Checked if current spinner is paused
    #
    # @return [Boolean]
    #
    # @api public
    def paused?
      !!(@thread && @thread['pause'])
    end

    # Pause spinner automatic animation
    #
    # @api public
    def pause
      return if paused?

      synchronize do
        @thread['pause'] = true if @thread
      end
    end

    # Resume spinner automatic animation
    #
    # @api public
    def resume
      return unless paused?

      @thread.wakeup if @thread
    end

    # Run spinner while executing job
    #
    # @param [String] stop_message
    #   the message displayed when block is finished
    #
    # @yield automatically animate and finish spinner
    #
    # @example
    #   spinner.run('Migrated DB') { ... }
    #
    # @api public
    def run(stop_message = '', &block)
      job(&block)
      auto_spin

      @work = Thread.new { execute_job }
      @work.join
    ensure
      stop(stop_message)
    end

    # Duration of the spinning animation
    #
    # @return [Numeric]
    #
    # @api public
    def duration
      @started_at ? Time.now - @started_at : nil
    end

    # Join running spinner
    #
    # @param [Float] timeout
    #   the timeout for join
    #
    # @api public
    def join(timeout = nil)
      unless @thread
        raise(NotSpinningError, 'Cannot join spinner that is not running')
      end

      timeout ? @thread.join(timeout) : @thread.join
    end

    # Kill running spinner
    #
    # @api public
    def kill
      synchronize do
        @thread.kill if @thread
      end
    end

    # Perform a spin
    #
    # @return [String]
    #   the printed data
    #
    # @api public
    def spin
      synchronize do
        return if @done
        emit(:spin)

        if @hide_cursor && !spinning?
          write(TTY::Cursor.hide)
        end

        data = message.gsub(MATCHER, @frames[@current])
        data = replace_tokens(data)
        write(data, true)
        @current = (@current + 1) % @length
        @state = :spinning
        data
      end
    end

    # Redraw the indent for this spinner, if it exists
    #
    # @api private
    def redraw_indent
      if @hide_cursor && !spinning?
        write(TTY::Cursor.hide)
      end

      write("", false)
    end

    # Finish spining
    #
    # @param [String] stop_message
    #   the stop message to print
    #
    # @api public
    def stop(stop_message = '')
      mon_enter
      return if done?

      clear_line
      return if @clear

      data = message.gsub(MATCHER, next_char)
      data = replace_tokens(data)
      if !stop_message.empty?
        data << ' ' + stop_message
      end

      write(data, false)
      write("\n", false) unless @clear || @multispinner
    ensure
      @state      = :stopped
      @done       = true
      @started_at = nil

      if @hide_cursor
        write(TTY::Cursor.show, false)
      end

      emit(:done)
      kill
      mon_exit
    end

    # Retrieve next character
    #
    # @return [String]
    #
    # @api private
    def next_char
      if success?
        @success_mark
      elsif error?
        @error_mark
      else
        @frames[@current - 1]
      end
    end

    # Finish spinning and set state to :success
    #
    # @api public
    def success(stop_message = '')
      return if done?

      synchronize do
        @succeeded = :success
        stop(stop_message)
        emit(:success)
      end
    end

    # Finish spinning and set state to :error
    #
    # @api public
    def error(stop_message = '')
      return if done?

      synchronize do
        @succeeded = :error
        stop(stop_message)
        emit(:error)
      end
    end

    # Clear current line
    #
    # @api public
    def clear_line
      write(ECMA_CSI + '0m' + TTY::Cursor.clear_line)
    end

    # Update string formatting tokens
    #
    # @param [Hash[Symbol]] tokens
    #   the tokens used in formatting string
    #
    # @api public
    def update(tokens)
      synchronize do
        clear_line if spinning?
        @tokens.merge!(tokens)
      end
    end

    private

    # Execute a block on the proper terminal line if the spinner is running
    # under a multispinner. Otherwise, execute the block on the current line.
    #
    # @api private
    def execute_on_line
      if @multispinner
        @multispinner.synchronize do
          if @first_run
            @row ||= @multispinner.next_row
            yield if block_given?
            output.print "\n"
            @first_run = false
          else
            lines_up = (@multispinner.rows + 1) - @row
            output.print TTY::Cursor.save
            output.print TTY::Cursor.up(lines_up)
            yield if block_given?
            output.print TTY::Cursor.restore
          end
        end
      else
        yield if block_given?
      end
    end

    # Write data out to output
    #
    # @return [nil]
    #
    # @api private
    def write(data, clear_first = false)
      return unless tty? # write only to terminal

      execute_on_line do
        output.print(TTY::Cursor.column(1)) if clear_first
        # If there's a top level spinner, print with inset
        characters_in = @multispinner.line_inset(@row) if @multispinner
        output.print("#{characters_in}#{data}")
        output.flush
      end
    end

    # Check if IO is attached to a terminal
    #
    # return [Boolean]
    #
    # @api public
    def tty?
      output.respond_to?(:tty?) && output.tty?
    end

    # Emit callback
    #
    # @api private
    def emit(name, *args)
      @callbacks[name].each do |callback|
        callback.call(*args)
      end
    end

    # Find frames by token name
    #
    # @param [Symbol] token
    #   the name for the frames
    #
    # @return [Array, String]
    #
    # @api private
    def fetch_format(token, property)
      if FORMATS.key?(token)
        FORMATS[token][property]
      else
        raise ArgumentError, "Unknown format token `:#{token}`"
      end
    end

    # Replace any token inside string
    #
    # @param [String] string
    #   the string containing tokens
    #
    # @return [String]
    #
    # @api private
    def replace_tokens(string)
      data = string.dup
      @tokens.each do |name, val|
        data.gsub!(/\:#{name}/, val.to_s)
      end
      data
    end
  end # Spinner
end # TTY
