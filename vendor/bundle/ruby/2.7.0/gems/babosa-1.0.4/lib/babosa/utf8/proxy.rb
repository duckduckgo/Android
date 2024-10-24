module Babosa
  module UTF8

    autoload :JavaProxy,          "babosa/utf8/java_proxy"
    autoload :UnicodeProxy,       "babosa/utf8/unicode_proxy"
    autoload :ActiveSupportProxy, "babosa/utf8/active_support_proxy"
    autoload :DumbProxy,          "babosa/utf8/dumb_proxy"

    # A UTF-8 proxy for Babosa can be any object which responds to the methods in this module.
    # The following proxies are provided by Babosa: {ActiveSupportProxy}, {DumbProxy}, {JavaProxy}, and {UnicodeProxy}.
    module Proxy
      CP1252  = {
        128 => [226, 130, 172],
        129 => nil,
        130 => [226, 128, 154],
        131 => [198, 146],
        132 => [226, 128, 158],
        133 => [226, 128, 166],
        134 => [226, 128, 160],
        135 => [226, 128, 161],
        136 => [203, 134],
        137 => [226, 128, 176],
        138 => [197, 160],
        139 => [226, 128, 185],
        140 => [197, 146],
        141 => nil,
        142 => [197, 189],
        143 => nil,
        144 => nil,
        145 => [226, 128, 152],
        146 => [226, 128, 153],
        147 => [226, 128, 156],
        148 => [226, 128, 157],
        149 => [226, 128, 162],
        150 => [226, 128, 147],
        151 => [226, 128, 148],
        152 => [203, 156],
        153 => [226, 132, 162],
        154 => [197, 161],
        155 => [226, 128, 186],
        156 => [197, 147],
        157 => nil,
        158 => [197, 190],
        159 => [197, 184]
      }

      # This is a stub for a method that should return a Unicode-aware
      # downcased version of the given string.
      def downcase(string)
        raise NotImplementedError
      end

      # This is a stub for a method that should return a Unicode-aware
      # upcased version of the given string.
      def upcase(string)
        raise NotImplementedError
      end

      # This is a stub for a method that should return the Unicode NFC
      # normalization of the given string.
      def normalize_utf8(string)
        raise NotImplementedError
      end

      if ''.respond_to?(:scrub) && !defined?(Rubinius)
        # Attempt to replace invalid UTF-8 bytes with valid ones. This method
        # naively assumes if you have invalid UTF8 bytes, they are either Windows
        # CP-1252 or ISO8859-1. In practice this isn't a bad assumption, but may not
        # always work.
        def tidy_bytes(string)
          string.scrub do |bad|
            tidy_byte(*bad.bytes).flatten.compact.pack('C*').unpack('U*').pack('U*')
          end
        end
      else
        def tidy_bytes(string)
          bytes = string.unpack("C*")
          conts_expected = 0
          last_lead = 0

          bytes.each_index do |i|
            byte          = bytes[i]
            is_cont       = byte > 127 && byte < 192
            is_lead       = byte > 191 && byte < 245
            is_unused     = byte > 240
            is_restricted = byte > 244

            # Impossible or highly unlikely byte? Clean it.
            if is_unused || is_restricted
              bytes[i] = tidy_byte(byte)
            elsif is_cont
              # Not expecting contination byte? Clean up. Otherwise, now expect one less.
              conts_expected == 0 ? bytes[i] = tidy_byte(byte) : conts_expected -= 1
            else
              if conts_expected > 0
                # Expected continuation, but got ASCII or leading? Clean backwards up to
                # the leading byte.
                (1..(i - last_lead)).each {|j| bytes[i - j] = tidy_byte(bytes[i - j])}
                conts_expected = 0
              end
              if is_lead
                # Final byte is leading? Clean it.
                if i == bytes.length - 1
                  bytes[i] = tidy_byte(bytes.last)
                else
                  # Valid leading byte? Expect continuations determined by position of
                  # first zero bit, with max of 3.
                  conts_expected = byte < 224 ? 1 : byte < 240 ? 2 : 3
                  last_lead = i
                end
              end
            end
          end
          bytes.empty? ? "" : bytes.flatten.compact.pack("C*").unpack("U*").pack("U*")
        end
      end

      private

      def tidy_byte(byte)
        byte < 160 ? CP1252[byte] : byte < 192 ? [194, byte] : [195, byte - 64]
      end
    end
  end
end
