# frozen_string_literal: true

require 'faraday'
require 'forwardable'

module FaradayMiddleware
  # Public: A simple middleware that adds an access token to each request.
  #
  # By default, the token is added as both "access_token" query parameter
  # and the "Authorization" HTTP request header. It can alternatively be
  # added exclusively as a bearer token "Authorization" header by specifying
  # a "token_type" option of "bearer". However, an explicit "access_token"
  # parameter or "Authorization" header for the current request are not
  # overriden.
  #
  # Examples
  #
  #   # configure default token:
  #   OAuth2.new(app, 'abc123')
  #
  #   # configure query parameter name:
  #   OAuth2.new(app, 'abc123', :param_name => 'my_oauth_token')
  #
  #   # use bearer token authorization header only
  #   OAuth2.new(app, 'abc123', :token_type => 'bearer')
  #
  #   # default token value is optional:
  #   OAuth2.new(app, :param_name => 'my_oauth_token')
  class OAuth2 < Faraday::Middleware
    PARAM_NAME  = 'access_token'
    TOKEN_TYPE  = 'param'
    AUTH_HEADER = 'Authorization'

    attr_reader :param_name, :token_type

    extend Forwardable
    def_delegators :'Faraday::Utils', :parse_query, :build_query

    def call(env)
      params = { param_name => @token }.update query_params(env[:url])
      token = params[param_name]

      if token.respond_to?(:empty?) && !token.empty?
        case @token_type.downcase
        when 'param'
          env[:url].query = build_query params
          env[:request_headers][AUTH_HEADER] ||= %(Token token="#{token}")
        when 'bearer'
          env[:request_headers][AUTH_HEADER] ||= %(Bearer #{token})
        end
      end

      @app.call env
    end

    def initialize(app, token = nil, options = {})
      super(app)
      if token.is_a? Hash
        options = token
        token = nil
      end
      @token = token&.to_s
      @param_name = options.fetch(:param_name, PARAM_NAME).to_s
      @token_type = options.fetch(:token_type, TOKEN_TYPE).to_s

      raise ArgumentError, ":param_name can't be blank" if @token_type == 'param' && @param_name.empty?

      return unless options[:token_type].nil?

      warn "\nWarning: FaradayMiddleware::OAuth2 initialized with default "\
        'token_type - token will be added as both a query string parameter '\
        'and an Authorization header. In the next major release, tokens will '\
        'be added exclusively as an Authorization header by default. Please '\
        'see https://github.com/lostisland/faraday_middleware/wiki.'
    end

    def query_params(url)
      if url.query.nil? || url.query.empty?
        {}
      else
        parse_query url.query
      end
    end
  end
end

# deprecated alias
Faraday::Request::OAuth2 = FaradayMiddleware::OAuth2
