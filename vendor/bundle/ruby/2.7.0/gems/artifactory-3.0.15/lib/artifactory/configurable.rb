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

module Artifactory
  #
  # A re-usable class containing configuration information for the {Client}. See
  # {Defaults} for a list of default values.
  #
  module Configurable
    class << self
      #
      # The list of configurable keys.
      #
      # @return [Array<Symbol>]
      #
      def keys
        @keys ||= %i{
          endpoint
          username
          password
          api_key
          proxy_address
          proxy_password
          proxy_port
          proxy_username
          ssl_pem_file
          ssl_verify
          user_agent
          read_timeout
        }
      end
    end

    #
    # Create one attribute getter and setter for each key.
    #
    Artifactory::Configurable.keys.each do |key|
      attr_accessor key
    end

    #
    # Set the configuration for this config, using a block.
    #
    # @example Configure the API endpoint
    #   Artifactory.configure do |config|
    #     config.endpoint = "http://www.my-artifactory-server.com/artifactory"
    #   end
    #
    def configure
      yield self
    end

    #
    # Reset all configuration options to their default values.
    #
    # @example Reset all settings
    #   Artifactory.reset!
    #
    # @return [self]
    #
    def reset!
      Artifactory::Configurable.keys.each do |key|
        instance_variable_set(:"@#{key}", Defaults.options[key])
      end
      self
    end
    alias_method :setup, :reset!

    private

    #
    # The list of configurable keys, as an options hash.
    #
    # @return [Hash]
    #
    def options
      map = Artifactory::Configurable.keys.map do |key|
        [key, instance_variable_get(:"@#{key}")]
      end
      Hash[map]
    end
  end
end
