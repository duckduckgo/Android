# frozen_string_literal: true

require 'faraday'

module FaradayMiddleware
  # Exception thrown when the maximum amount of requests is
  # exceeded.
  class RedirectLimitReached < Faraday::ClientError
    attr_reader :response

    def initialize(response)
      super "too many redirects; last one to: #{response['location']}"
      @response = response
    end
  end
end
