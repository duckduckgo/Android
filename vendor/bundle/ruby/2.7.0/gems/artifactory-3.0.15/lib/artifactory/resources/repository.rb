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
  class Resource::Repository < Resource::Base
    class << self
      #
      # Get a list of all repositories in the system.
      #
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Array<Resource::Repository>]
      #   the list of builds
      #
      def all(options = {})
        client = extract_client!(options)
        client.get("/api/repositories").map do |hash|
          find(hash["key"], client: client)
        end.compact
      end

      #
      # Find (fetch) a repository by name.
      #
      # @example Find a repository by named key
      #   Repository.find(name: 'libs-release-local') #=> #<Resource::Artifact>
      #
      # @param [Hash] options
      #   the list of options
      #
      # @option options [String] :name
      #   the name of the repository to find
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Resource::Repository, nil]
      #   an instance of the repository that matches the given name, or +nil+
      #   if one does not exist
      #
      def find(name, options = {})
        client = extract_client!(options)

        response = client.get("/api/repositories/#{url_safe(name)}")
        from_hash(response, client: client)
      rescue Error::HTTPError => e
        raise unless e.code == 400

        nil
      end
    end

    attribute :blacked_out, false
    attribute :description
    attribute :checksum_policy_type, "client-checksums"
    attribute :excludes_pattern, ""
    attribute :handle_releases, true
    attribute :handle_snapshots, true
    attribute :includes_pattern, "**/*"
    attribute :key, -> { raise "Key is missing!" }
    attribute :max_unique_snapshots, 0
    attribute :notes
    attribute :package_type, "generic"
    attribute :property_sets, []
    attribute :repo_layout_ref, "simple-default"
    attribute :rclass, "local"
    attribute :snapshot_version_behavior, "non-unique"
    attribute :suppress_pom_consistency_checks, false
    attribute :url, ""
    attribute :yum_root_depth, 0
    attribute :calculate_yum_metadata, false
    attribute :repositories, []
    attribute :external_dependencies_enabled, false
    attribute :client_tls_certificate, ""

    #
    # Creates or updates a repository configuration depending on if the
    # repository configuration previously existed. This method also works
    # around Artifactory's dangerous default behavior:
    #
    #   > An existing repository with the same key are removed from the
    #   > configuration and its content is removed!
    #
    # @return [Boolean]
    #
    def save
      if self.class.find(key, client: client)
        client.post(api_path, to_json, headers)
      else
        client.put(api_path, to_json, headers)
      end
      true
    end

    #
    # Upload to a given repository
    #
    # @see Artifact#upload Upload syntax examples
    #
    # @return [Resource::Artifact]
    #
    def upload(local_path, remote_path, properties = {}, headers = {})
      artifact = Resource::Artifact.new(local_path: local_path)
      artifact.upload(key, remote_path, properties, headers)
    end

    #
    # Upload an artifact with the given SHA checksum. Consult the artifactory
    # documentation for the possible responses when the checksums fail to
    # match.
    #
    # @see Artifact#upload_with_checksum More syntax examples
    #
    def upload_with_checksum(local_path, remote_path, checksum, properties = {})
      artifact = Resource::Artifact.new(local_path: local_path)
      artifact.upload_with_checksum(key, remote_path, checksum, properties)
    end

    #
    # Upload an artifact with the given archive. Consult the artifactory
    # documentation for the format of the archive to upload.
    #
    # @see Artifact#upload_from_archive More syntax examples
    #
    def upload_from_archive(local_path, remote_path, properties = {})
      artifact = Resource::Artifact.new(local_path: local_path)
      artifact.upload_from_archive(key, remote_path, properties)
    end

    #
    # The list of artifacts in this repository on the remote artifactory
    # server.
    #
    # @see Artifact.search Search syntax examples
    #
    # @example Get the list of artifacts for a repository
    #   repo = Repository.new('libs-release-local')
    #   repo.artifacts #=> [#<Resource::Artifacts>, ...]
    #
    # @return [Collection::Artifact]
    #   the list of artifacts
    #
    def artifacts
      @artifacts ||= Collection::Artifact.new(self, repos: key) do
        Resource::Artifact.search(name: ".*", repos: key)
      end
    end

    #
    #
    #
    def files
      response = client.get("/api/storage/#{url_safe(key)}", {
        deep:            0,
        listFolders:     0,
        mdTimestamps:    0,
        includeRootPath: 0,
      })

      response["children"]
    end

    #
    # Delete this repository from artifactory, suppressing any +ResourceNotFound+
    # exceptions might occur.
    #
    # @return [Boolean]
    #   true if the object was deleted successfully, false otherwise
    #
    def delete
      client.delete(api_path)
      true
    rescue Error::HTTPError => e
      false
    end

    private

    #
    # The path to this repository on the server.
    #
    # @return [String]
    #
    def api_path
      "/api/repositories/#{url_safe(key)}"
    end

    #
    # The default headers for this object. This includes the +Content-Type+.
    #
    # @return [Hash]
    #
    def headers
      @headers ||= {
        "Content-Type" => content_type,
      }
    end

    #
    # The default Content-Type for this repository. It varies based on the
    # repository type.
    #
    # @return [String]
    #
    def content_type
      case rclass.to_s.downcase
      when "local"
        "application/vnd.org.jfrog.artifactory.repositories.LocalRepositoryConfiguration+json"
      when "remote"
        "application/vnd.org.jfrog.artifactory.repositories.RemoteRepositoryConfiguration+json"
      when "virtual"
        "application/vnd.org.jfrog.artifactory.repositories.VirtualRepositoryConfiguration+json"
      else
        raise "Unknown Repository type `#{rclass}'!"
      end
    end
  end
end
