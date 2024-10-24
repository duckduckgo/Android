#
# Copyright 2015-2018 Chef Software, Inc.
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
  class Resource::Build < Resource::Base
    BUILD_SCHEMA_VERSION = "1.0.1".freeze
    # valid build types as dictated by the Artifactory API
    BUILD_TYPES = %w{ ANT IVY MAVEN GENERIC GRADLE }.freeze

    class << self
      #
      # Search for all builds in the system.
      #
      # @param [String] name
      #   the name of the build component
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Array<Resource::Build>]
      #   the list of builds
      #
      def all(name, options = {})
        client = extract_client!(options)
        client.get("/api/build/#{url_safe(name)}")["buildsNumbers"].map do |build_number|
          # Remove the leading / from the `uri` value. Converts `/484` to `484`.
          number = build_number["uri"].slice(1..-1)
          find(name, number, client: client)
        end.compact.flatten
      rescue Error::HTTPError => e
        # Artifactory returns a 404 instead of an empty list when there are no
        # builds. Whoever decided that was a good idea clearly doesn't
        # understand the point of REST interfaces...
        raise unless e.code == 404

        []
      end

      #
      # Find (fetch) data for a particular build of a component
      #
      # @example Find data for a build of a component
      #   Build.find('wicket', 25) #=> #<Build name: 'wicket' ...>
      #
      # @param [String] name
      #   the name of the build component
      # @param [String] number
      #   the number of the build
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Resource::Build, nil]
      #   an instance of the build that matches the given name/number
      #   combination, or +nil+ if one does not exist
      #
      def find(name, number, options = {})
        client = extract_client!(options)
        response = client.get("/api/build/#{url_safe(name)}/#{url_safe(number)}")
        from_hash(response["buildInfo"], client: client)
      rescue Error::HTTPError => e
        raise unless e.code == 404

        nil
      end

      #
      # @see Artifactory::Resource::Base.from_hash
      #
      def from_hash(hash, options = {})
        super.tap do |instance|
          instance.started = Time.parse(instance.started) rescue nil
          instance.duration_millis = instance.duration_millis.to_i
        end
      end
    end

    # Based on https://github.com/JFrogDev/build-info/blob/master/README.md#build-info-json-format
    attribute :properties, {}
    attribute :version, BUILD_SCHEMA_VERSION
    attribute :name, -> { raise "Build component missing!" }
    attribute :number, -> { raise "Build number missing!" }
    attribute :type, "GENERIC"
    attribute :build_agent, {}
    attribute :agent, {}
    attribute :started, Time.now.utc.iso8601(3)
    attribute :duration_millis
    attribute :artifactory_principal
    attribute :url
    attribute :vcs_revision
    attribute :vcs_url
    attribute :license_control, {}
    attribute :build_retention, {}
    attribute :modules, []
    attribute :governance
    attribute :statuses, []

    #
    # Compare a build artifacts/dependencies/environment with an older
    # build to see what has changed (new artifacts added, old dependencies
    # deleted etc).
    #
    # @example List all properties for an artifact
    #   build.diff(35) #=> { 'artifacts'=>{}, 'dependencies'=>{}, 'properties'=>{} }
    #
    # @param [String] previous_build_number
    #   the number of the previous build to compare against
    #
    # @return [Hash<String, Hash>]
    #   the list of properties
    #
    def diff(previous_build_number)
      endpoint = api_path + "?" "diff=#{url_safe(previous_build_number)}"
      client.get(endpoint, {})
    end

    #
    # Move a build's artifacts to a new repository optionally moving or
    # copying the build's dependencies to the target repository
    # and setting properties on promoted artifacts.
    #
    # @example promote the build to 'omnibus-stable-local'
    #   build.promote('omnibus-stable-local')
    # @example promote a build attaching some new properites
    #   build.promote('omnibus-stable-local'
    #     properties: {
    #       'promoted_by' => 'hipchat:schisamo@chef.io'
    #     }
    #   )
    #
    # @param [String] target_repo
    #   repository to move or copy the build's artifacts and/or dependencies
    # @param [Hash] options
    #   the list of options to pass
    #
    # @option options [String] :status (default: 'promoted')
    #   new build status (any string)
    # @option options [String] :comment (default: '')
    #   an optional comment describing the reason for promotion
    # @option options [String] :user (default: +Artifactory.username+)
    #   the user that invoked promotion
    # @option options [Boolean] :dry_run (default: +false+)
    #   pretend to do the promotion
    # @option options [Boolean] :copy (default: +false+)
    #   whether to copy instead of move
    # @option options [Boolean] :dependencies (default: +false+)
    #   whether to move/copy the build's dependencies
    # @option options [Array] :scopes (default: [])
    #   an array of dependency scopes to include when "dependencies" is true
    # @option options [Hash<String, Array<String>>] :properties (default: [])
    #   a list of properties to attach to the build's artifacts
    # @option options [Boolean] :fail_fast (default: +true+)
    #   fail and abort the operation upon receiving an error
    #
    # @return [Hash]
    #   the parsed JSON response from the server
    #
    def promote(target_repo, options = {})
      request_body = {}.tap do |body|
        body[:status]       = options[:status] || "promoted"
        body[:comment]      = options[:comment] || ""
        body[:ciUser]       = options[:user] || Artifactory.username
        body[:dryRun]       = options[:dry_run] || false
        body[:targetRepo]   = target_repo
        body[:copy]         = options[:copy] || false
        body[:artifacts]    = true # always move/copy the build's artifacts
        body[:dependencies] = options[:dependencies] || false
        body[:scopes]       = options[:scopes] || []
        body[:properties]   = options[:properties] || {}
        body[:failFast]     = options[:fail_fast] || true
      end

      endpoint = "/api/build/promote/#{url_safe(name)}/#{url_safe(number)}"
      client.post(endpoint, JSON.fast_generate(request_body),
        "Content-Type" => "application/json")
    end

    #
    # Creates data about a build.
    #
    # @return [Boolean]
    #
    def save
      raise Error::InvalidBuildType.new(type) unless BUILD_TYPES.include?(type)

      file = Tempfile.new("build.json")
      file.write(to_json)
      file.rewind

      client.put("/api/build", file,
        "Content-Type" => "application/json")
      true
    ensure
      if file
        file.close
        file.unlink
      end
    end

    private

    #
    # The path to this build on the server.
    #
    # @return [String]
    #
    def api_path
      "/api/build/#{url_safe(name)}/#{url_safe(number)}"
    end
  end
end
