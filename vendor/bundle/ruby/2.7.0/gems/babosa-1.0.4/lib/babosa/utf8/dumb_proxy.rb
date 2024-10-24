require File.expand_path("../mappings", __FILE__)

module Babosa
  module UTF8

    # This module provides fallback UTF-8 support when nothing else is
    # available. It does case folding for Roman alphabet-based characters
    # commonly used by Western European languages and little else, making it
    # useless for Russian, Bulgarian, Greek, etc. If at all possible, Unicode
    # or ActiveSupport should be used instead because they support the full
    # UTF-8 character range.
    module DumbProxy
      extend Proxy
      extend self

      def downcase(string)
        string.downcase.unpack("U*").map {|char| Mappings::DOWNCASE[char] or char}.flatten.pack("U*")
      end

      def upcase(string)
        string.upcase.unpack("U*").map {|char| Mappings::UPCASE[char] or char}.flatten.pack("U*")
      end

      if ''.respond_to?(:unicode_normalize)
        def normalize_utf8(string)
          string.unicode_normalize
        end
      else
        # On Ruby 2.2, this uses the native Unicode normalize method. On all
        # other Rubies, it does a very naive Unicode normalization, which should
        # work for this library's purposes (i.e., Roman-based codepoints, up to
        # U+017E).  Do not use reuse this as a general solution!  Use a real
        # library like Unicode or ActiveSupport instead.
        def normalize_utf8(string)
          codepoints = string.unpack("U*")
          new = []
          until codepoints.empty? do
            if Mappings::COMPOSITION[codepoints[0..1]]
              new << Mappings::COMPOSITION[codepoints.slice!(0,2)]
            else
              new << codepoints.shift
            end
          end
          new.compact.flatten.pack("U*")
        end
      end
    end
  end
end
