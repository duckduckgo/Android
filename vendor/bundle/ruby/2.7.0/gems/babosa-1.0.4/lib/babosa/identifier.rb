# encoding: utf-8
module Babosa

  # Codepoints for characters that will be deleted by +#word_chars!+.
  STRIPPABLE = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 14, 15, 16, 17, 18, 19,
    20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 33, 34, 35, 36, 37, 38, 39,
    40, 41, 42, 43, 44, 45, 46, 47, 58, 59, 60, 61, 62, 63, 64, 91, 92, 93, 94,
    96, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136,
    137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151,
    152, 153, 154, 155, 156, 157, 158, 159, 161, 162, 163, 164, 165, 166, 167,
    168, 169, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 182, 183, 184,
    185, 187, 188, 189, 190, 191, 215, 247, 8203, 8204, 8205, 8239, 65279]

  # This class provides some string-manipulation methods specific to slugs.
  #
  # Note that this class includes many "bang methods" such as {#clean!} and
  # {#normalize!} that perform actions on the string in-place. Each of these
  # methods has a corresponding "bangless" method (i.e., +Identifier#clean!+
  # and +Identifier#clean+) which does not appear in the documentation because
  # it is generated dynamically.
  #
  # All of the bang methods return an instance of String, while the bangless
  # versions return an instance of Babosa::Identifier, so that calls to methods
  # specific to this class can be chained:
  #
  #   string = Identifier.new("hello world")
  #   string.with_separators! # => "hello-world"
  #   string.with_separators  # => <Babosa::Identifier:0x000001013e1590 @wrapped_string="hello-world">
  #
  # @see http://www.utf8-chartable.de/unicode-utf8-table.pl?utf8=dec Unicode character table
  class Identifier

    Error = Class.new(StandardError)

    attr_reader :wrapped_string
    alias to_s wrapped_string

    @@utf8_proxy = if Babosa.jruby15?
      UTF8::JavaProxy
    elsif defined? Unicode::VERSION
      UTF8::UnicodeProxy
    elsif defined? ActiveSupport
      UTF8::ActiveSupportProxy
    else
      UTF8::DumbProxy
    end

    # Return the proxy used for UTF-8 support.
    # @see Babosa::UTF8::Proxy
    def self.utf8_proxy
      @@utf8_proxy
    end

    # Set a proxy object used for UTF-8 support.
    # @see Babosa::UTF8::Proxy
    def self.utf8_proxy=(obj)
      @@utf8_proxy = obj
    end

    def method_missing(symbol, *args, &block)
      @wrapped_string.__send__(symbol, *args, &block)
    end

    # @param string [#to_s] The string to use as the basis of the Identifier.
    def initialize(string)
      @wrapped_string = string.to_s
      tidy_bytes!
      normalize_utf8!
    end

    def ==(value)
      @wrapped_string.to_s == value.to_s
    end

    def eql?(value)
      @wrapped_string == value
    end

    def empty?
      # included to make this class :respond_to? :empty for compatibility with Active Support's
      # #blank?
      @wrapped_string.empty?
    end

    # Approximate an ASCII string. This works only for Western strings using
    # characters that are Roman-alphabet characters + diacritics. Non-letter
    # characters are left unmodified.
    #
    #   string = Identifier.new "Łódź
    #   string.transliterate                 # => "Lodz, Poland"
    #   string = Identifier.new "日本"
    #   string.transliterate                 # => "日本"
    #
    # You can pass any key(s) from +Characters.approximations+ as arguments. This allows
    # for contextual approximations. Various languages are supported, you can see which ones
    # by looking at the source of {Babosa::Transliterator::Base}.
    #
    #   string = Identifier.new "Jürgen Müller"
    #   string.transliterate                 # => "Jurgen Muller"
    #   string.transliterate :german         # => "Juergen Mueller"
    #   string = Identifier.new "¡Feliz año!"
    #   string.transliterate                 # => "¡Feliz ano!"
    #   string.transliterate :spanish        # => "¡Feliz anio!"
    #
    # The approximations are an array, which you can modify if you choose:
    #
    #   # Make Spanish use "nh" rather than "nn"
    #   Babosa::Transliterator::Spanish::APPROXIMATIONS["ñ"] = "nh"
    #
    # Notice that this method does not simply convert to ASCII; if you want
    # to remove non-ASCII characters such as "¡" and "¿", use {#to_ascii!}:
    #
    #   string.transliterate!(:spanish)       # => "¡Feliz anio!"
    #   string.transliterate!                 # => "¡Feliz anio!"
    #
    # @param *args <Symbol>
    # @return String
    def transliterate!(*kinds)
      kinds.compact!
      kinds = [:latin] if kinds.empty?
      kinds.each do |kind|
        transliterator = Transliterator.get(kind).instance
        @wrapped_string = transliterator.transliterate(@wrapped_string)
      end
      @wrapped_string
    end

    # Converts dashes to spaces, removes leading and trailing spaces, and
    # replaces multiple whitespace characters with a single space.
    # @return String
    def clean!
      @wrapped_string = @wrapped_string.gsub("-", " ").squeeze(" ").strip
    end

    # Remove any non-word characters. For this library's purposes, this means
    # anything other than letters, numbers, spaces, newlines and linefeeds.
    # @return String
    def word_chars!
      @wrapped_string = (unpack("U*") - Babosa::STRIPPABLE).pack("U*")
    end

    # Normalize the string for use as a URL slug. Note that in this context,
    # +normalize+ means, strip, remove non-letters/numbers, downcasing,
    # truncating to 255 bytes and converting whitespace to dashes.
    # @param Options
    # @return String
    def normalize!(options = nil)
      options = default_normalize_options.merge(options || {})

      if translit_option = options[:transliterate]
        if translit_option != true
          transliterate!(*translit_option)
        else
          transliterate!(*options[:transliterations])
        end
      end
      to_ascii! if options[:to_ascii]
      clean!
      word_chars!
      clean!
      downcase!
      truncate_bytes!(options[:max_length])
      with_separators!(options[:separator])
    end

    # Normalize a string so that it can safely be used as a Ruby method name.
    def to_ruby_method!(allow_bangs = true)
      leader, trailer = @wrapped_string.strip.scan(/\A(.+)(.)\z/).flatten
      leader          = leader.to_s.dup
      trailer         = trailer.to_s.dup
      if allow_bangs
        trailer.downcase!
        trailer.gsub!(/[^a-z0-9!=\\?]/, '')
      else
        trailer.downcase!
        trailer.gsub!(/[^a-z0-9]/, '')
      end
      id = leader.to_identifier
      id.transliterate!
      id.to_ascii!
      id.clean!
      id.word_chars!
      id.clean!
      @wrapped_string = id.to_s + trailer
      if @wrapped_string == ""
        raise Error, "Input generates impossible Ruby method name"
      end
      with_separators!("_")
    end

    # Delete any non-ascii characters.
    # @return String
    def to_ascii!
      @wrapped_string = @wrapped_string.gsub(/[^\x00-\x7f]/u, '')
    end

    # Truncate the string to +max+ characters.
    # @example
    #   "üéøá".to_identifier.truncate(3) #=> "üéø"
    # @return String
    def truncate!(max)
      @wrapped_string = unpack("U*")[0...max].pack("U*")
    end

    # Truncate the string to +max+ bytes. This can be useful for ensuring that
    # a UTF-8 string will always fit into a database column with a certain max
    # byte length. The resulting string may be less than +max+ if the string must
    # be truncated at a multibyte character boundary.
    # @example
    #   "üéøá".to_identifier.truncate_bytes(3) #=> "ü"
    # @return String
    def truncate_bytes!(max)
      return @wrapped_string if @wrapped_string.bytesize <= max
      curr = 0
      new = []
      unpack("U*").each do |char|
        break if curr > max
        char = [char].pack("U")
        curr += char.bytesize
        if curr <= max
          new << char
        end
      end
      @wrapped_string = new.join
    end

    # Replaces whitespace with dashes ("-").
    # @return String
    def with_separators!(char = "-")
      @wrapped_string = @wrapped_string.gsub(/\s/u, char)
    end

    # Perform UTF-8 sensitive upcasing.
    # @return String
    def upcase!
      @wrapped_string = @@utf8_proxy.upcase(@wrapped_string)
    end

    # Perform UTF-8 sensitive downcasing.
    # @return String
    def downcase!
      @wrapped_string = @@utf8_proxy.downcase(@wrapped_string)
    end

    # Perform Unicode composition on the wrapped string.
    # @return String
    def normalize_utf8!
      @wrapped_string = @@utf8_proxy.normalize_utf8(@wrapped_string)
    end

    # Attempt to convert characters encoded using CP1252 and IS0-8859-1 to
    # UTF-8.
    # @return String
    def tidy_bytes!
      @wrapped_string = @@utf8_proxy.tidy_bytes(@wrapped_string)
    end

    %w[transliterate clean downcase word_chars normalize normalize_utf8
      tidy_bytes to_ascii to_ruby_method truncate truncate_bytes upcase
      with_separators].each do |method|
      class_eval(<<-EOM, __FILE__, __LINE__ + 1)
        def #{method}(*args)
          send_to_new_instance(:#{method}!, *args)
        end
      EOM
    end

    def to_identifier
      self
    end

    # The default options for {#normalize!}. Override to set your own defaults.
    def default_normalize_options
      {:transliterate => true, :max_length => 255, :separator => "-"}
    end

    alias approximate_ascii transliterate
    alias approximate_ascii! transliterate!
    alias with_dashes with_separators
    alias with_dashes! with_separators!
    alias to_slug to_identifier

    private

    # Used as the basis of the bangless methods.
    def send_to_new_instance(*args)
      id = Identifier.allocate
      id.instance_variable_set :@wrapped_string, to_s
      id.send(*args)
      id
    end
  end
end
