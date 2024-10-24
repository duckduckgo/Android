#
# Copyright 2015 Chef Software, Inc.
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

require "time"

module Artifactory
  class Resource::BuildComponent < Resource::Base
    class << self
      #
      # Search for all compoenents for which build data exists.
      #
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Array<Resource::BuildComponent>]
      #   the list of builds
      #
      def all(options = {})
        client = extract_client!(options)
        client.get("/api/build")["builds"].map do |component|
          from_hash(component, client: client)
        end.compact.flatten
      rescue Error::HTTPError => e
        # Artifactory returns a 404 instead of an empty list when there are no
        # builds. Whoever decided that was a good idea clearly doesn't
        # understand the point of REST interfaces...
        raise unless e.code == 404

        []
      end

      #
      # Find (fetch) data for a particular build component
      #
      # @example Find a particular build component
      #   BuildComponent.find('wicket') #=> #<BuildComponent name: 'wicket' ...>
      #
      # @param [String] name
      #   the name of the build component
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Resource::BuildComponent, nil]
      #   an instance of the build component that matches the given name,
      #   or +nil+ if one does not exist
      #
      def find(name, options = {})
        client = extract_client!(options)
        all.find do |component|
          component.name == name
        end
      end

      #
      # @see Artifactory::Resource::Base.from_hash
      #
      def from_hash(hash, options = {})
        super.tap do |instance|
          # Remove the leading / from the `uri` value. Converts `/foo` to `foo`.
          instance.name = instance.uri.slice(1..-1)
          instance.last_started = Time.parse(instance.last_started) rescue nil
        end
      end
    end

    attribute :uri
    attribute :name, -> { raise "Name missing!" }
    attribute :last_started

    #
    # The list of build data for this component.
    #
    # @example Get the list of artifacts for a repository
    #   component = BuildComponent.new(name: 'wicket')
    #   component.builds #=> [#<Resource::Build>, ...]
    #
    # @return [Collection::Build]
    #   the list of builds
    #
    def builds
      @builds ||= Collection::Build.new(self, name: name) do
        Resource::Build.all(name)
      end
    end

    #
    # Remove this component's build data stored in Artifactory
    #
    # @option options [Array<String>] :build_numbers (default: nil)
    #   an array of build numbers that should be deleted; if not given
    #   all builds (for this component) are deleted
    # @option options [Boolean] :artifacts (default: +false+)
    #   if true the component's artifacts are also removed
    # @option options [Boolean] :delete_all (default: +false+)
    #   if true the entire component is removed
    #
    # @return [Boolean]
    #   true if the object was deleted successfully, false otherwise
    #
    def delete(options = {})
      params = {}.tap do |param|
        param[:buildNumbers] = options[:build_numbers].join(",") if options[:build_numbers]
        param[:artifacts]    = 1 if options[:artifacts]
        param[:deleteAll]    = 1 if options[:delete_all]
      end

      endpoint = api_path + "?#{to_query_string_parameters(params)}"
      client.delete(endpoint, {})
      true
    rescue Error::HTTPError => e
      false
    end

    #
    # Rename a build component.
    #
    # @param [String] new_name
    #   new name for the component
    #
    # @return [Boolean]
    #   true if the object was renamed successfully, false otherwise
    #
    def rename(new_name, options = {})
      endpoint = "/api/build/rename/#{url_safe(name)}" + "?to=#{new_name}"
      client.post(endpoint, {})
      true
    rescue Error::HTTPError => e
      false
    end

    private

    #
    # The path to this build component on the server.
    #
    # @return [String]
    #
    def api_path
      "/api/build/#{url_safe(name)}"
    end
  end
end
