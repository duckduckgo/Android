require 'active_support'
require 'active_support/multibyte/unicode'

module Babosa
  module UTF8
    # A UTF-8 proxy using Active Support's multibyte support.
    module ActiveSupportProxy
      extend ActiveSupport::Multibyte::Unicode
      extend self

      def self.normalize_utf8(string)
        normalize(string, :c)
      end

      if ActiveSupport::VERSION::MAJOR == 3
        def downcase(string)
          ActiveSupport::Multibyte::Chars.new(string).downcase.to_s
        end

        def upcase(string)
          ActiveSupport::Multibyte::Chars.new(string).upcase.to_s
        end
      elsif ActiveSupport::VERSION::MAJOR >= 6
        def self.normalize_utf8(string)
          string.unicode_normalize(:nfc).to_s
        end

        def downcase(string)
          string.downcase.to_s
        end

        def upcase(string)
          string.upcase.to_s
        end
      end
    end
  end
end
