# frozen_string_literal: true

require 'faraday_middleware/response_middleware'

module FaradayMiddleware
  # Public: Parse response bodies as JSON.
  class ParseJson < ResponseMiddleware
    dependency do
      require 'json' unless defined?(::JSON)
    end

    define_parser do |body, parser_options|
      ::JSON.parse(body, parser_options || {}) unless body.strip.empty?
    end

    # Public: Override the content-type of the response with "application/json"
    # if the response body looks like it might be JSON, i.e. starts with an
    # open bracket.
    #
    # This is to fix responses from certain API providers that insist on serving
    # JSON with wrong MIME-types such as "text/javascript".
    class MimeTypeFix < ResponseMiddleware
      MIME_TYPE = 'application/json'

      def process_response(env)
        old_type = env[:response_headers][CONTENT_TYPE].to_s
        new_type = MIME_TYPE.dup
        new_type << ';' << old_type.split(';', 2).last if old_type.index(';')
        env[:response_headers][CONTENT_TYPE] = new_type
      end

      BRACKETS = %w-[ {-.freeze
      WHITESPACE = [' ', "\n", "\r", "\t"].freeze

      def parse_response?(env)
        super && BRACKETS.include?(first_char(env[:body]))
      end

      def first_char(body)
        idx = -1
        char = body[idx += 1]
        char = body[idx += 1] while char && WHITESPACE.include?(char)
        char
      end
    end
  end
end

# deprecated alias
Faraday::Response::ParseJson = FaradayMiddleware::ParseJson
