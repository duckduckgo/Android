# frozen_string_literal: true

require_relative 'decode_configuration'
require_relative 'jwk_configuration'

module JWT
  module Configuration
    class Container
      attr_accessor :decode, :jwk

      def initialize
        reset!
      end

      def reset!
        @decode = DecodeConfiguration.new
        @jwk    = JwkConfiguration.new
      end
    end
  end
end
