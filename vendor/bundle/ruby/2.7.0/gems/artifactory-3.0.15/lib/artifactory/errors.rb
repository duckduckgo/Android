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
  module Error
    # Base class for all errors
    class ArtifactoryError < StandardError; end

    # Class for all HTTP errors
    class HTTPError < ArtifactoryError
      attr_reader :code

      def initialize(hash = {})
        @code = hash["status"].to_i
        @http = hash["message"].to_s

        super "The Artifactory server responded with an HTTP Error " \
              "#{@code}: `#{@http}'"
      end
    end

    # A general connection error with a more informative message
    class ConnectionError < ArtifactoryError
      def initialize(endpoint)
        super "The Artifactory server at `#{endpoint}' is not currently " \
              "accepting connections. Please ensure that the server is " \
              "running an that your authentication information is correct."
      end
    end

    # A general connection error with a more informative message
    class InvalidBuildType < ArtifactoryError
      def initialize(given_type)
        super <<~EOH
          '#{given_type}' is not a valid build type.

          Valid build types include:

              #{Resource::Build::BUILD_TYPES.join("\n    ")}"

        EOH
      end
    end
  end
end
