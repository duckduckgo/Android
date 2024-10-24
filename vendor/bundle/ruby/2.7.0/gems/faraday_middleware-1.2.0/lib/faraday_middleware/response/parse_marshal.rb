# frozen_string_literal: true

require 'faraday_middleware/response_middleware'

module FaradayMiddleware
  # Public: Restore marshalled Ruby objects in response bodies.
  class ParseMarshal < ResponseMiddleware
    define_parser do |body|
      ::Marshal.load(body) unless body.empty?
    end
  end
end

# deprecated alias
Faraday::Response::ParseMarshal = FaradayMiddleware::ParseMarshal
