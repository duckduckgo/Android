require 'unicode'

module Babosa
  module UTF8
    # A UTF-8 proxy using the Unicode gem.
    # @see http://github.com/blackwinter/unicode
    module UnicodeProxy
      extend Proxy
      extend self
      def downcase(string)
        Unicode.downcase(string)
      end

      def upcase(string)
        Unicode.upcase(string)
      end

      def normalize_utf8(string)
        Unicode.normalize_C(string)
      end
    end
  end
end
