# frozen_string_literal: true

require 'stringio'

module FaradayMiddleware
  # Wraps a handler originally written for Rack for Faraday compatibility.
  #
  # Experimental. Only handles changes in request headers.
  class RackCompatible
    def initialize(app, rack_handler, *args)
      # tiny middleware that decomposes a Faraday::Response to standard Rack
      # array: [status, headers, body]
      compatible_app = lambda do |rack_env|
        env = restore_env(rack_env)
        response = app.call(env)
        [response.status, response.headers, Array(response.body)]
      end
      @rack = rack_handler.new(compatible_app, *args)
    end

    def call(env)
      rack_env = prepare_env(env)
      rack_response = @rack.call(rack_env)
      finalize_response(env, rack_response)
    end

    NON_PREFIXED_HEADERS = %w[CONTENT_LENGTH CONTENT_TYPE].freeze

    # faraday to rack-compatible
    def prepare_env(faraday_env)
      env = headers_to_rack(faraday_env)

      url = faraday_env[:url]
      env['rack.url_scheme'] = url.scheme
      env['PATH_INFO'] = url.path
      env['SERVER_PORT'] = if url.respond_to?(:inferred_port)
                             url.inferred_port
                           else
                             url.port
                           end
      env['QUERY_STRING'] = url.query
      env['REQUEST_METHOD'] = faraday_env[:method].to_s.upcase

      env['rack.errors'] ||= StringIO.new
      env['faraday'] = faraday_env

      env
    end

    def headers_to_rack(env)
      rack_env = {}
      env[:request_headers].each do |name, value|
        name = name.upcase.tr('-', '_')
        name = "HTTP_#{name}" unless NON_PREFIXED_HEADERS.include? name
        rack_env[name] = value
      end
      rack_env
    end

    # rack to faraday-compatible
    def restore_env(rack_env)
      env = rack_env.fetch('faraday')
      headers = env[:request_headers]
      headers.clear

      rack_env.each do |name, value|
        next unless name.is_a?(String) && value.is_a?(String)

        if NON_PREFIXED_HEADERS.include?(name) || name.start_with?('HTTP_')
          name = name.sub(/^HTTP_/, '').downcase.tr('_', '-')
          headers[name] = value
        end
      end

      env[:method] = rack_env['REQUEST_METHOD'].downcase.to_sym
      env[:rack_errors] = rack_env['rack.errors']
      env
    end

    def finalize_response(env, rack_response)
      status, headers, body = rack_response
      body = body.inject { |str, part| str << part }
      headers = Faraday::Utils::Headers.new(headers) unless headers.is_a?(Faraday::Utils::Headers)

      env.update status: status.to_i,
                 body: body,
                 response_headers: headers

      env[:response] ||= Faraday::Response.new(env)
      env[:response]
    end
  end
end
