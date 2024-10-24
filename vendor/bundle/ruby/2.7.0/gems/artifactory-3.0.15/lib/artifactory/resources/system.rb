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
  class Resource::System < Resource::Base
    class << self
      #
      # Get general system information.
      #
      # @example Get the system information
      #   System.info #=> "..."
      #
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [String]
      #   a "table" of the system information as returned by the API
      #
      def info(options = {})
        client = extract_client!(options)
        client.get("/api/system")
      end

      #
      # Check the status of the Artifactory server and API. This method will
      # always return a boolean response, so it's safe to call without
      # exception handling.
      #
      # @example Wait until the Artifactory server is ready
      #   until System.ping
      #     sleep(0.5)
      #     print '.'
      #   end
      #
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Boolean]
      #   true if the Artifactory server is ready, false otherwise
      #
      def ping(options = {})
        client = extract_client!(options)
        !!client.get("/api/system/ping")
      rescue Error::ConnectionError
        false
      end

      #
      # Get the current system configuration as XML.
      #
      # @example Get the current configuration
      #   System.configuration
      #
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [REXML::Document]
      #   the parsed XML document
      #
      def configuration(options = {})
        client   = extract_client!(options)
        response = client.get("/api/system/configuration")

        REXML::Document.new(response)
      end

      #
      # Update the configuration with the given XML.
      #
      # @example Update the configuration
      #   new_config = File.new('/path/to/new.xml')
      #   System.update_configuration(new_config)
      #
      # @param [Hash] options
      #   the list of options
      # @param [File] xml
      #   a pointer to the file descriptor of the XML to upload
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      def update_configuration(xml, options = {})
        client = extract_client!(options)

        # The Artifactory api requires a content type of 'application/xml'.
        # See http://bit.ly/1l2IvZY
        headers = { "Content-Type" => "application/xml" }
        client.post("/api/system/configuration", xml, headers)
      end

      #
      # Get the version information from the server.
      #
      # @example Get the version information
      #   System.version #=> { ... }
      #
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Hash]
      #   the parsed JSON from the response
      #
      def version(options = {})
        client = extract_client!(options)
        client.get("/api/system/version")
      end
    end
  end
end
