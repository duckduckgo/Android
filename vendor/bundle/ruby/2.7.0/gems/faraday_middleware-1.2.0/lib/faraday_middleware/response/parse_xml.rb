# frozen_string_literal: true

require 'faraday_middleware/response_middleware'

module FaradayMiddleware
  # Public: parses response bodies with MultiXml.
  class ParseXml < ResponseMiddleware
    dependency 'multi_xml'

    define_parser do |body, parser_options|
      ::MultiXml.parse(body, parser_options || {})
    end
  end
end

# deprecated alias
Faraday::Response::ParseXml = FaradayMiddleware::ParseXml
