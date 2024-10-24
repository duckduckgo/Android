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

require "rexml/document"

module Artifactory
  class Resource::Layout < Resource::Base
    class << self
      #
      # Get a list of all repository layouts in the system.
      #
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Array<Resource::Layout>]
      #   the list of layouts
      #
      def all(options = {})
        config = Resource::System.configuration(options)
        list_from_config("config/repoLayouts/repoLayout", config, options)
      end

      #
      # Find (fetch) a layout by its name.
      #
      # @example Find a layout by its name
      #   Layout.find('maven-2-default') #=> #<Layout name: 'maven-2-default' ...>
      #
      # @param [String] name
      #   the name of the layout to find
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Resource::Layout, nil]
      #   an instance of the layout that matches the given name, or +nil+
      #   if one does not exist
      #
      def find(name, options = {})
        config = Resource::System.configuration(options)
        find_from_config("config/repoLayouts/repoLayout/name[text()='#{name}']", config, options)
      rescue Error::HTTPError => e
        raise unless e.code == 404

        nil
      end
    end

    attribute :name, -> { raise "Name missing!" }
    attribute :artifact_path_pattern
    attribute :distinctive_descriptor_path_pattern, true
    attribute :descriptor_path_pattern
    attribute :folder_integration_revision_reg_exp
    attribute :file_integration_revision_reg_exp
  end
end
