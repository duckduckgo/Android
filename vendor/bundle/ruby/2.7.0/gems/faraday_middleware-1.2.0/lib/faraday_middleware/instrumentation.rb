# frozen_string_literal: true

require 'faraday'

module FaradayMiddleware
  # Public: Instruments requests using Active Support.
  #
  # Measures time spent only for synchronous requests.
  #
  # Examples
  #
  #   ActiveSupport::Notifications.
  #     subscribe('request.faraday') do |name, starts, ends, _, env|
  #     url = env[:url]
  #     http_method = env[:method].to_s.upcase
  #     duration = ends - starts
  #     $stderr.puts '[%s] %s %s (%.3f s)' % [url.host,
  #                                           http_method,
  #                                           url.request_uri,
  #                                           duration]
  #   end
  class Instrumentation < Faraday::Middleware
    dependency 'active_support/notifications'

    def initialize(app, options = {})
      super(app)
      @name = options.fetch(:name, 'request.faraday')
    end

    def call(env)
      ::ActiveSupport::Notifications.instrument(@name, env) do
        @app.call(env)
      end
    end
  end
end
