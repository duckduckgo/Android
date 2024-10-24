# frozen_string_literal: true

require 'faraday'

# Main FaradayMiddleware module.
module FaradayMiddleware
  # Internal: The base class for middleware that parses responses.
  class ResponseMiddleware < Faraday::Middleware
    CONTENT_TYPE = 'Content-Type'

    class << self
      attr_accessor :parser
    end

    # Store a Proc that receives the body and returns the parsed result.
    def self.define_parser(parser = nil, &block)
      @parser = parser ||
                block  ||
                raise(ArgumentError, 'Define parser with a block')
    end

    def self.inherited(subclass)
      super
      subclass.load_error = load_error if subclass.respond_to? :load_error=
      subclass.parser = parser
    end

    def initialize(app = nil, options = {})
      super(app)
      @options = options
      @parser_options = options[:parser_options]
      @content_types = Array(options[:content_type])
    end

    def call(environment)
      @app.call(environment).on_complete do |env|
        process_response(env) if process_response_type?(response_type(env)) && parse_response?(env)
      end
    end

    def process_response(env)
      env[:raw_body] = env[:body] if preserve_raw?(env)
      env[:body] = parse(env[:body])
    rescue Faraday::ParsingError => e
      raise Faraday::ParsingError.new(e.wrapped_exception, env[:response])
    end

    # Parse the response body.
    #
    # Instead of overriding this method, consider using `define_parser`.
    def parse(body)
      if self.class.parser
        begin
          self.class.parser.call(body, @parser_options)
        rescue StandardError, SyntaxError => e
          raise e if e.is_a?(SyntaxError) &&
                     e.class.name != 'Psych::SyntaxError'

          raise Faraday::ParsingError, e
        end
      else
        body
      end
    end

    def response_type(env)
      type = env[:response_headers][CONTENT_TYPE].to_s
      type = type.split(';', 2).first if type.index(';')
      type
    end

    def process_response_type?(type)
      @content_types.empty? || @content_types.any? do |pattern|
        pattern.is_a?(Regexp) ? type =~ pattern : type == pattern
      end
    end

    def parse_response?(env)
      env[:body].respond_to? :to_str
    end

    def preserve_raw?(env)
      env[:request].fetch(:preserve_raw, @options[:preserve_raw])
    end
  end

  # DRAGONS
  module OptionsExtension
    attr_accessor :preserve_raw

    def to_hash
      super.update(preserve_raw: preserve_raw)
    end

    def each
      return to_enum(:each) unless block_given?

      super
      yield :preserve_raw, preserve_raw
    end

    def fetch(key, *args)
      if key == :preserve_raw
        value = __send__(key)
        value.nil? ? args.fetch(0) : value
      else
        super
      end
    end
  end

  if defined?(Faraday::RequestOptions)
    begin
      Faraday::RequestOptions.from(preserve_raw: true)
    rescue NoMethodError
      Faraday::RequestOptions.include OptionsExtension
    end
  end
end
