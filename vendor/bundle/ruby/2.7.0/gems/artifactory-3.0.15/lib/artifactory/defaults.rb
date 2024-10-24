#
# Copyright 2014-2018 Chef Software, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

require_relative "version"

module Artifactory
  module Defaults
    # Default API endpoint
    ENDPOINT = "http://localhost:8080/artifactory".freeze

    # Default User Agent header string
    USER_AGENT = "Artifactory Ruby Gem #{Artifactory::VERSION}".freeze

    class << self
      #
      # The list of calculated default options for the configuration.
      #
      # @return [Hash]
      #
      def options
        Hash[Configurable.keys.map { |key| [key, send(key)] }]
      end

      #
      # The endpoint where artifactory lives
      #
      # @return [String]
      #
      def endpoint
        ENV["ARTIFACTORY_ENDPOINT"] || ENDPOINT
      end

      #
      # The User Agent header to send along
      #
      # @return [String]
      #
      def user_agent
        ENV["ARTIFACTORY_USER_AGENT"] || USER_AGENT
      end

      #
      # The HTTP Basic Authentication username
      #
      # @return [String, nil]
      #
      def username
        ENV["ARTIFACTORY_USERNAME"]
      end

      #
      # The HTTP Basic Authentication password
      #
      # @return [String, nil]
      #
      def password
        ENV["ARTIFACTORY_PASSWORD"]
      end

      #
      # The API Key for authentication
      #
      # @return [String, nil]
      #
      def api_key
        ENV["ARTIFACTORY_API_KEY"]
      end

      #
      # The HTTP Proxy server address as a string
      #
      # @return [String, nil]
      #
      def proxy_address
        ENV["ARTIFACTORY_PROXY_ADDRESS"]
      end

      #
      # The HTTP Proxy user password as a string
      #
      # @return [String, nil]
      #
      def proxy_password
        ENV["ARTIFACTORY_PROXY_PASSWORD"]
      end

      #
      # The HTTP Proxy server port as a string
      #
      # @return [String, nil]
      #
      def proxy_port
        ENV["ARTIFACTORY_PROXY_PORT"]
      end

      #
      # The HTTP Proxy server username as a string
      #
      # @return [String, nil]
      #
      def proxy_username
        ENV["ARTIFACTORY_PROXY_USERNAME"]
      end

      #
      # The path to a pem file on disk for use with a custom SSL verification
      #
      # @return [String, nil]
      #
      def ssl_pem_file
        ENV["ARTIFACTORY_SSL_PEM_FILE"]
      end

      #
      # Verify SSL requests (default: true)
      #
      # @return [true, false]
      #
      def ssl_verify
        if ENV["ARTIFACTORY_SSL_VERIFY"].nil?
          true
        else
          %w{t y}.include?(ENV["ARTIFACTORY_SSL_VERIFY"].downcase[0])
        end
      end

      #
      # Number of seconds to wait for a response from Artifactory
      #
      # @return [Integer]
      #
      def read_timeout
        if ENV["ARTIFACTORY_READ_TIMEOUT"]
          ENV["ARTIFACTORY_READ_TIMEOUT"].to_i
        else
          120
        end
      end
    end
  end
end
