module Babosa
  module UTF8
    # A UTF-8 proxy module using Java's built-in Unicode support. Requires JRuby 1.5+.
    module JavaProxy
      extend Proxy
      extend self
      java_import java.text.Normalizer

      def downcase(string)
        string.to_java.to_lower_case.to_s
      end

      def upcase(string)
        string.to_java.to_upper_case.to_s
      end

      def normalize_utf8(string)
        Normalizer.normalize(string, Normalizer::Form::NFC).to_s
      end
    end
  end
end
