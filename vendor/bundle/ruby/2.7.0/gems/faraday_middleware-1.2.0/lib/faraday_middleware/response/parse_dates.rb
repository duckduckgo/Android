# frozen_string_literal: true

require 'time'
require 'faraday'

module FaradayMiddleware
  # Parse dates from response body
  class ParseDates < ::Faraday::Response::Middleware
    ISO_DATE_FORMAT = /\A\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?
    (Z|((\+|-)\d{2}:?\d{2}))\Z/xm.freeze

    def initialize(app, options = {})
      @regexp = options[:match] || ISO_DATE_FORMAT
      super(app)
    end

    def call(env)
      response = @app.call(env)
      parse_dates! response.env[:body]
      response
    end

    private

    def parse_dates!(value)
      case value
      when Hash
        value.each do |key, element|
          value[key] = parse_dates!(element)
        end
      when Array
        value.each_with_index do |element, index|
          value[index] = parse_dates!(element)
        end
      when @regexp
        Time.parse(value)
      else
        value
      end
    end
  end
end
