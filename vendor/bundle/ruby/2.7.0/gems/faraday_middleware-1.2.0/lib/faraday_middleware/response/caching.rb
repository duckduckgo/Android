# frozen_string_literal: true

require 'faraday'
require 'forwardable'
require 'digest/sha1'

module FaradayMiddleware
  # Public: Caches GET responses and pulls subsequent ones from the cache.
  class Caching < Faraday::Middleware
    attr_reader :cache

    # Internal: List of status codes that can be cached:
    # * 200 - 'OK'
    # * 203 - 'Non-Authoritative Information'
    # * 300 - 'Multiple Choices'
    # * 301 - 'Moved Permanently'
    # * 302 - 'Found'
    # * 404 - 'Not Found'
    # * 410 - 'Gone'
    CACHEABLE_STATUS_CODES = [200, 203, 300, 301, 302, 404, 410].freeze

    extend Forwardable
    def_delegators :'Faraday::Utils', :parse_query, :build_query

    # Public: initialize the middleware.
    #
    # cache   - An object that responds to read and write (default: nil).
    # options - An options Hash (default: {}):
    #           :ignore_params - String name or Array names of query
    #                                    params that should be ignored when forming
    #                                    the cache key (default: []).
    #           :write_options - Hash of settings that should be passed as the
    #                                    third options parameter to the cache's #write
    #                                    method. If not specified, no options parameter
    #                                    will be passed.
    #           :full_key      - Boolean - use full URL as cache key:
    #                                    (url.host + url.request_uri)
    #           :status_codes  - Array of http status code to be cache
    #                                    (default: CACHEABLE_STATUS_CODE)
    #
    # Yields if no cache is given. The block should return a cache object.
    def initialize(app, cache = nil, options = {})
      super(app)
      if cache.is_a?(Hash) && block_given?
        options = cache
        cache = nil
      end
      @cache = cache || yield
      @options = options
    end

    def call(env)
      if env[:method] == :get
        if env[:parallel_manager]
          # callback mode
          cache_on_complete(env)
        else
          # synchronous mode
          key = cache_key(env)
          unless (response = cache.read(key)) && response
            response = @app.call(env)
            store_response_in_cache(key, response)
          end
          finalize_response(response, env)
        end
      else
        @app.call(env)
      end
    end

    def cache_key(env)
      url = env[:url].dup
      if url.query && params_to_ignore.any?
        params = parse_query url.query
        params.reject! { |k,| params_to_ignore.include? k }
        url.query = params.any? ? build_query(params) : nil
      end
      url.normalize!
      digest = full_key? ? url.host + url.request_uri : url.request_uri
      Digest::SHA1.hexdigest(digest)
    end

    def params_to_ignore
      @params_to_ignore ||= Array(@options[:ignore_params]).map(&:to_s)
    end

    def full_key?
      @full_key ||= @options[:full_key]
    end

    def custom_status_codes
      @custom_status_codes ||= begin
        codes = CACHEABLE_STATUS_CODES & Array(@options[:status_codes]).map(&:to_i)
        codes.any? ? codes : CACHEABLE_STATUS_CODES
      end
    end

    def cache_on_complete(env)
      key = cache_key(env)
      if (cached_response = cache.read(key))
        finalize_response(cached_response, env)
      else
        # response.status is nil at this point
        # any checks need to be done inside on_complete block
        @app.call(env).on_complete do |response_env|
          store_response_in_cache(key, response_env.response)
          response_env
        end
      end
    end

    def store_response_in_cache(key, response)
      return unless custom_status_codes.include?(response.status)

      if @options[:write_options]
        cache.write(key, response, @options[:write_options])
      else
        cache.write(key, response)
      end
    end

    def finalize_response(response, env)
      response = response.dup if response.frozen?
      env[:response] = response
      unless env[:response_headers]
        env.update response.env
        # FIXME: omg hax
        response.instance_variable_set('@env', env)
      end
      response
    end
  end
end
