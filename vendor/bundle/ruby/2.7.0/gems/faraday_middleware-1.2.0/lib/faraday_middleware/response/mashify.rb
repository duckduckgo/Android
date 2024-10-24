# frozen_string_literal: true

require 'faraday'

module FaradayMiddleware
  # Public: Converts parsed response bodies to a Hashie::Mash if they were of
  # Hash or Array type.
  class Mashify < Faraday::Response::Middleware
    attr_accessor :mash_class

    class << self
      attr_accessor :mash_class
    end

    dependency do
      require 'hashie/mash'
      self.mash_class = ::Hashie::Mash
    end

    def initialize(app = nil, options = {})
      super(app)
      self.mash_class = options[:mash_class] || self.class.mash_class
    end

    def parse(body)
      case body
      when Hash
        mash_class.new(body)
      when Array
        body.map { |item| parse(item) }
      else
        body
      end
    end
  end
end

# deprecated alias
Faraday::Response::Mashify = FaradayMiddleware::Mashify
