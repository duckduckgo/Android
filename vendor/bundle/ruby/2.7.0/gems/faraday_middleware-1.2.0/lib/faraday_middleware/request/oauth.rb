# frozen_string_literal: true

require 'faraday'
require 'forwardable'

module FaradayMiddleware
  # Public: Uses the simple_oauth library to sign requests according the
  # OAuth protocol.
  #
  # The options for this middleware are forwarded to SimpleOAuth::Header:
  # :consumer_key, :consumer_secret, :token, :token_secret. All these
  # parameters are optional.
  #
  # The signature is added to the "Authorization" HTTP request header. If the
  # value for this header already exists, it is not overriden.
  #
  # If no Content-Type header is specified, this middleware assumes that
  # request body parameters should be included while signing the request.
  # Otherwise, it only includes them if the Content-Type is
  # "application/x-www-form-urlencoded", as per OAuth 1.0.
  #
  # For better performance while signing requests, this middleware should be
  # positioned before UrlEncoded middleware on the stack, but after any other
  # body-encoding middleware (such as EncodeJson).
  class OAuth < Faraday::Middleware
    dependency 'simple_oauth'

    AUTH_HEADER = 'Authorization'
    CONTENT_TYPE = 'Content-Type'
    TYPE_URLENCODED = 'application/x-www-form-urlencoded'

    extend Forwardable
    def_delegator :'Faraday::Utils', :parse_nested_query

    def initialize(app, options)
      super(app)
      @options = options
    end

    def call(env)
      env[:request_headers][AUTH_HEADER] ||= oauth_header(env).to_s if sign_request?(env)
      @app.call(env)
    end

    def oauth_header(env)
      SimpleOAuth::Header.new env[:method],
                              env[:url].to_s,
                              signature_params(body_params(env)),
                              oauth_options(env)
    end

    def sign_request?(env)
      !!env[:request].fetch(:oauth, true)
    end

    def oauth_options(env)
      if (extra = env[:request][:oauth]) && extra.is_a?(Hash) && !extra.empty?
        @options.merge extra
      else
        @options
      end
    end

    def body_params(env)
      if include_body_params?(env)
        if env[:body].respond_to?(:to_str)
          parse_nested_query env[:body]
        else
          env[:body]
        end
      end || {}
    end

    def include_body_params?(env)
      # see RFC 5849, section 3.4.1.3.1 for details
      !(type = env[:request_headers][CONTENT_TYPE]) || (type == TYPE_URLENCODED)
    end

    def signature_params(params)
      if params.empty?
        params
      else
        params.reject { |_k, v| v.respond_to?(:content_type) }
      end
    end
  end
end

# deprecated alias
Faraday::Request::OAuth = FaradayMiddleware::OAuth
