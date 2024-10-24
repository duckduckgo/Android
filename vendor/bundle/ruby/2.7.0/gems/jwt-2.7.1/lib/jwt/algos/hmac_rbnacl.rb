# frozen_string_literal: true

module JWT
  module Algos
    module HmacRbNaCl
      module_function

      MAPPING = {
        'HS256' => ::RbNaCl::HMAC::SHA256,
        'HS512256' => ::RbNaCl::HMAC::SHA512256,
        'HS384' => nil,
        'HS512' => ::RbNaCl::HMAC::SHA512
      }.freeze

      SUPPORTED = MAPPING.keys

      def sign(algorithm, msg, key)
        if (hmac = resolve_algorithm(algorithm))
          hmac.auth(key_for_rbnacl(hmac, key).encode('binary'), msg.encode('binary'))
        else
          Hmac.sign(algorithm, msg, key)
        end
      end

      def verify(algorithm, key, signing_input, signature)
        if (hmac = resolve_algorithm(algorithm))
          hmac.verify(key_for_rbnacl(hmac, key).encode('binary'), signature.encode('binary'), signing_input.encode('binary'))
        else
          Hmac.verify(algorithm, key, signing_input, signature)
        end
      rescue ::RbNaCl::BadAuthenticatorError, ::RbNaCl::LengthError
        false
      end

      def key_for_rbnacl(hmac, key)
        key ||= ''
        raise JWT::DecodeError, 'HMAC key expected to be a String' unless key.is_a?(String)

        return padded_empty_key(hmac.key_bytes) if key == ''

        key
      end

      def resolve_algorithm(algorithm)
        MAPPING.fetch(algorithm)
      end

      def padded_empty_key(length)
        Array.new(length, 0x0).pack('C*').encode('binary')
      end
    end
  end
end
