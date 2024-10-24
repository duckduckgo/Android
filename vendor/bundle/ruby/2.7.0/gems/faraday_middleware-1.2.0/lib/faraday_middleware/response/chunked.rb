# frozen_string_literal: true

require 'faraday_middleware/response_middleware'

module FaradayMiddleware
  # Public: Parse a Transfer-Encoding. Chunks response to just the original data
  class Chunked < FaradayMiddleware::ResponseMiddleware
    TRANSFER_ENCODING = 'transfer-encoding'

    define_parser do |raw_body|
      decoded_body = []
      until raw_body.empty?
        chunk_len, raw_body = raw_body.split("\r\n", 2)
        chunk_len = chunk_len.split(';', 2).first.hex
        break if chunk_len.zero?

        decoded_body << raw_body[0, chunk_len]
        # The 2 is to strip the extra CRLF at the end of the chunk
        raw_body = raw_body[chunk_len + 2, raw_body.length - chunk_len - 2]
      end
      decoded_body.join('')
    end

    def parse_response?(env)
      super && chunked_encoding?(env[:response_headers])
    end

    def chunked_encoding?(headers)
      (encoding = headers[TRANSFER_ENCODING]) &&
        encoding.split(',').include?('chunked')
    end
  end
end
