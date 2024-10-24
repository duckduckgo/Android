# frozen_string_literal: true

require 'faraday_middleware/response/mashify'

module FaradayMiddleware
  # Public: Converts parsed response bodies to a Hashie::Rash if they were of
  # Hash or Array type.
  class Rashify < Mashify
    dependency do
      require 'rash'
      self.mash_class = ::Hashie::Mash::Rash
    end
  end
end

# deprecated alias
Faraday::Response::Rashify = FaradayMiddleware::Rashify
