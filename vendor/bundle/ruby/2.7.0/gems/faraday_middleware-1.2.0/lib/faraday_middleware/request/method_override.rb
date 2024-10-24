# frozen_string_literal: true

require 'faraday'

module FaradayMiddleware
  # Public: Writes the original HTTP method to "X-Http-Method-Override" header
  # and sends the request as POST.
  #
  # This can be used to work around technical issues with making non-POST
  # requests, e.g. faulty HTTP client or server router.
  #
  # This header is recognized in Rack apps by default, courtesy of the
  # Rack::MethodOverride module. See
  # http://rack.rubyforge.org/doc/classes/Rack/MethodOverride.html
  class MethodOverride < Faraday::Middleware
    HEADER = 'X-Http-Method-Override'

    # Public: Initialize the middleware.
    #
    # app     - the Faraday app to wrap
    # options - (optional)
    #           :rewrite - Array of HTTP methods to rewrite
    #                      (default: all but GET and POST)
    def initialize(app, options = nil)
      super(app)
      @methods = options&.fetch(:rewrite)&.map do |method|
        method = method.downcase if method.respond_to? :downcase
        method.to_sym
      end
    end

    def call(env)
      method = env[:method]
      rewrite_request(env, method) if rewrite_request?(method)
      @app.call(env)
    end

    def rewrite_request?(method)
      if @methods.nil? || @methods.empty?
        (method != :get) && (method != :post)
      else
        @methods.include? method
      end
    end

    # Internal: Write the original HTTP method to header, change method to POST.
    def rewrite_request(env, original_method)
      env[:request_headers][HEADER] = original_method.to_s.upcase
      env[:method] = :post
    end
  end
end
