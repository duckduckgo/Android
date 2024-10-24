# frozen_string_literal: true

require 'faraday'

module FaradayMiddleware
  # Middleware to automatically decompress response bodies. If the
  # "Accept-Encoding" header wasn't set in the request, this sets it to
  # "gzip,deflate" and appropriately handles the compressed response from the
  # server. This resembles what Ruby 1.9+ does internally in Net::HTTP#get.
  #
  # This middleware is NOT necessary when these adapters are used:
  # - net_http on Ruby 1.9+
  # - net_http_persistent on Ruby 2.0+
  # - em_http
  class Gzip < Faraday::Middleware
    dependency 'zlib'

    def self.optional_dependency(lib = nil)
      lib ? require(lib) : yield
      true
    rescue LoadError, NameError
      false
    end

    BROTLI_SUPPORTED = optional_dependency 'brotli'

    def self.supported_encodings
      encodings = %w[gzip deflate]
      encodings << 'br' if BROTLI_SUPPORTED
      encodings
    end

    ACCEPT_ENCODING = 'Accept-Encoding'
    CONTENT_ENCODING = 'Content-Encoding'
    CONTENT_LENGTH = 'Content-Length'
    SUPPORTED_ENCODINGS = supported_encodings.join(',').freeze

    def call(env)
      env[:request_headers][ACCEPT_ENCODING] ||= SUPPORTED_ENCODINGS
      @app.call(env).on_complete do |response_env|
        if response_env[:body].empty?
          reset_body(response_env, &method(:raw_body))
        else
          case response_env[:response_headers][CONTENT_ENCODING]
          when 'gzip'
            reset_body(response_env, &method(:uncompress_gzip))
          when 'deflate'
            reset_body(response_env, &method(:inflate))
          when 'br'
            reset_body(response_env, &method(:brotli_inflate))
          end
        end
      end
    end

    def reset_body(env)
      env[:body] = yield(env[:body])
      env[:response_headers].delete(CONTENT_ENCODING)
      env[:response_headers][CONTENT_LENGTH] = env[:body].length
    end

    def uncompress_gzip(body)
      io = StringIO.new(body)
      gzip_reader = Zlib::GzipReader.new(io, encoding: 'ASCII-8BIT')
      gzip_reader.read
    end

    def inflate(body)
      # Inflate as a DEFLATE (RFC 1950+RFC 1951) stream
      Zlib::Inflate.inflate(body)
    rescue Zlib::DataError
      # Fall back to inflating as a "raw" deflate stream which
      # Microsoft servers return
      inflate = Zlib::Inflate.new(-Zlib::MAX_WBITS)
      begin
        inflate.inflate(body)
      ensure
        inflate.close
      end
    end

    def brotli_inflate(body)
      Brotli.inflate(body)
    end

    def raw_body(body)
      body
    end
  end
end
