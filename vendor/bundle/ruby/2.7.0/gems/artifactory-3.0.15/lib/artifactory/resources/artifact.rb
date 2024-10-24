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

require "tempfile"
require "time"

module Artifactory
  class Resource::Artifact < Resource::Base
    class << self
      #
      # Search for an artifact by the full or partial filename.
      #
      # @example Search for all repositories with the name "artifact"
      #   Artifact.search(name: 'artifact')
      #
      # @example Search for all artifacts named "artifact" in a specific repo
      #   Artifact.search(name: 'artifact', repos: 'libs-release-local')
      #
      # @param [Hash] options
      #   the list of options to search with
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      # @option options [String] :name
      #   the name of the artifact to search (it can be a regular expression)
      # @option options [String, Array<String>] :repos
      #   the list of repos to search
      #
      # @return [Array<Resource::Artifact>]
      #   a list of artifacts that match the query
      #
      def search(options = {})
        client = extract_client!(options)
        params = Util.slice(options, :name, :repos)
        format_repos!(params)

        client.get("/api/search/artifact", params)["results"].map do |artifact|
          from_url(artifact["uri"], client: client)
        end
      end

      #
      # Search for an artifact by Maven coordinates: +Group ID+, +Artifact ID+,
      # +Version+ and +Classifier+.
      #
      # @example Search for all repositories with the given gavc
      #   Artifact.gavc_search(
      #     group:      'org.acme',
      #     name:       'artifact',
      #     version:    '1.0',
      #     classifier: 'sources',
      #   )
      #
      # @example Search for all artifacts with the given gavc in a specific repo
      #   Artifact.gavc_search(
      #     group:      'org.acme',
      #     name:       'artifact',
      #     version:    '1.0',
      #     classifier: 'sources',
      #     repos:      'libs-release-local',
      #   )
      #
      # @param [Hash] options
      #   the list of options to search with
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      # @option options [String] :group
      #   the group id to search for
      # @option options [String] :name
      #   the artifact id to search for
      # @option options [String] :version
      #   the version of the artifact to search for
      # @option options [String] :classifier
      #   the classifer to search for
      # @option options [String, Array<String>] :repos
      #   the list of repos to search
      #
      # @return [Array<Resource::Artifact>]
      #   a list of artifacts that match the query
      #
      def gavc_search(options = {})
        client = extract_client!(options)
        options = Util.rename_keys(options,
          group: :g,
          name: :a,
          version: :v,
          classifier: :c)
        params = Util.slice(options, :g, :a, :v, :c, :repos)
        format_repos!(params)

        client.get("/api/search/gavc", params)["results"].map do |artifact|
          from_url(artifact["uri"], client: client)
        end
      end

      #
      # Search for an artifact by the given properties. These are arbitrary
      # properties defined by the user on artifact, so the search uses a free-
      # form schema.
      #
      # @example Search for all repositories with the given properties
      #   Artifact.property_search(
      #     branch: 'master',
      #     author: 'sethvargo',
      #   )
      #
      # @example Search for all artifacts with the given gavc in a specific repo
      #   Artifact.property_search(
      #     branch: 'master',
      #     author: 'sethvargo',
      #     repos: 'libs-release-local',
      #   )
      #
      # @param [Hash] options
      #   the free-form list of options to search with
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      # @option options [String, Array<String>] :repos
      #   the list of repos to search
      #
      # @return [Array<Resource::Artifact>]
      #   a list of artifacts that match the query
      #
      def property_search(options = {})
        client = extract_client!(options)
        params = options.dup
        format_repos!(params)

        client.get("/api/search/prop", params)["results"].map do |artifact|
          from_url(artifact["uri"], client: client)
        end
      end

      #
      # Search for an artifact by its checksum
      #
      # @example Search for all repositories with the given MD5 checksum
      #   Artifact.checksum_search(
      #     md5: 'abcd1234...',
      #   )
      #
      # @example Search for all artifacts with the given SHA1 checksum in a repo
      #   Artifact.checksum_search(
      #     sha1: 'abcdef123456....',
      #     repos: 'libs-release-local',
      #   )
      #
      # @param [Hash] options
      #   the list of options to search with
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      # @option options [String] :md5
      #   the MD5 checksum of the artifact to search for
      # @option options [String] :sha1
      #   the SHA1 checksum of the artifact to search for
      # @option options [String, Array<String>] :repos
      #   the list of repos to search
      #
      # @return [Array<Resource::Artifact>]
      #   a list of artifacts that match the query
      #
      def checksum_search(options = {})
        client = extract_client!(options)
        params = Util.slice(options, :md5, :sha1, :repos)
        format_repos!(params)

        client.get("/api/search/checksum", params)["results"].map do |artifact|
          from_url(artifact["uri"], client: client)
        end
      end

      #
      # Search for an artifact by its usage
      #
      # @example Search for all repositories with the given usage statistics
      #   Artifact.usage_search(
      #     notUsedSince: 1388534400000,
      #     createdBefore: 1388534400000,
      #   )
      #
      # @example Search for all artifacts with the given usage statistics in a repo
      #   Artifact.usage_search(
      #     notUsedSince: 1388534400000,
      #     createdBefore: 1388534400000,
      #     repos: 'libs-release-local',
      #   )
      #
      # @param [Hash] options
      #   the list of options to search with
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      # @option options [Long] :notUsedSince
      #   the last downloaded cutoff date of the artifact to search for (millis since epoch)
      # @option options [Long] :createdBefore
      #   the creation cutoff date of the artifact to search for (millis since epoch)
      # @option options [String, Array<String>] :repos
      #   the list of repos to search
      #
      # @return [Array<Resource::Artifact>]
      #   a list of artifacts that match the query
      #
      def usage_search(options = {})
        client = extract_client!(options)
        params = Util.slice(options, :notUsedSince, :createdBefore, :repos)
        format_repos!(params)

        client.get("/api/search/usage", params)["results"].map do |artifact|
          from_url(artifact["uri"], client: client)
        end
      end

      #
      # Search for an artifact by its creation date
      #
      # @example Search for all repositories with the given creation date range
      #   Artifact.usage_search(
      #     from : 1414800000000,
      #     to   : 1414871200000,
      #   )
      #
      # @example Search for all artifacts with the given creation date range in a repo
      #   Artifact.usage_search(
      #     from : 1414800000000,
      #     to   : 1414871200000,
      #     repos: 'libs-release-local',
      #   )
      #
      # @param [Hash] options
      #   the list of options to search with
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      # @option options [Long] :from
      #   the creation start date of the artifact to search for (millis since epoch)
      # @option options [Long] :to
      #   the creation end date of the artifact to search for (millis since epoch)
      # @option options [String, Array<String>] :repos
      #   the list of repos to search
      #
      # @return [Array<Resource::Artifact>]
      #   a list of artifacts that match the query
      #
      def creation_search(options = {})
        client = extract_client!(options)
        params = Util.slice(options, :from, :to, :repos)
        format_repos!(params)

        client.get("/api/search/creation", params)["results"].map do |artifact|
          from_url(artifact["uri"], client: client)
        end
      end

      #
      # Get all versions of an artifact.
      #
      # @example Get all versions of a given artifact
      #   Artifact.versions(name: 'artifact')
      # @example Get all versions of a given artifact in a specific repo
      #   Artifact.versions(name: 'artifact', repos: 'libs-release-local')
      #
      # @param [Hash] options
      #   the list of options to search with
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      # @option options [String] :group
      #   the
      # @option options [String] :sha1
      #   the SHA1 checksum of the artifact to search for
      # @option options [String, Array<String>] :repos
      #   the list of repos to search
      #
      def versions(options = {})
        client  = extract_client!(options)
        options = Util.rename_keys(options,
          group: :g,
          name: :a,
          version: :v)
        params = Util.slice(options, :g, :a, :v, :repos)
        format_repos!(params)

        client.get("/api/search/versions", params)["results"]
      rescue Error::HTTPError => e
        raise unless e.code == 404

        []
      end

      #
      # Get the latest version of an artifact.
      #
      # @example Find the latest version of an artifact
      #   Artifact.latest_version(name: 'artifact')
      # @example Find the latest version of an artifact in a repo
      #   Artifact.latest_version(
      #     name: 'artifact',
      #     repo: 'libs-release-local',
      #   )
      # @example Find the latest snapshot version of an artifact
      #   Artifact.latest_version(name: 'artifact', version: '1.0-SNAPSHOT')
      # @example Find the latest version of an artifact in a group
      #   Artifact.latest_version(name: 'artifact', group: 'org.acme')
      #
      # @param [Hash] options
      #   the list of options to search with
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      # @option options [String] :group
      #   the group id to search for
      # @option options [String] :name
      #   the artifact id to search for
      # @option options [String] :version
      #   the version of the artifact to search for
      # @option options [Boolean] :remote
      #   search remote repos (default: +false+)
      # @option options [String, Array<String>] :repos
      #   the list of repos to search
      #
      # @return [String, nil]
      #   the latest version as a string (e.g. +1.0-201203131455-2+), or +nil+
      #   if no artifact matches the given query
      #
      def latest_version(options = {})
        client = extract_client!(options)
        options = Util.rename_keys(options,
          group: :g,
          name: :a,
          version: :v)
        params = Util.slice(options, :g, :a, :v, :repos, :remote)
        format_repos!(params)

        # For whatever reason, Artifactory won't accept "true" - they want a
        # literal "1"...
        params[:remote] = 1 if options[:remote]

        client.get("/api/search/latestVersion", params)
      rescue Error::HTTPError => e
        raise unless e.code == 404

        nil
      end

      #
      # @see Artifactory::Resource::Base.from_hash
      #
      def from_hash(hash, options = {})
        super.tap do |instance|
          instance.created       = Time.parse(instance.created) rescue nil
          instance.last_modified = Time.parse(instance.last_modified) rescue nil
          instance.last_updated  = Time.parse(instance.last_updated)  rescue nil
          instance.size          = instance.size.to_i
        end
      end
    end

    attribute :uri, -> { raise "API path missing!" }
    attribute :checksums
    attribute :created
    attribute :download_uri, -> { raise "Download URI missing!" }
    attribute :key
    attribute :last_modified
    attribute :last_updated
    attribute :local_path, -> { raise "Local destination missing!" }
    attribute :mime_type
    attribute :repo
    attribute :size

    #
    # The SHA of this artifact.
    #
    # @return [String]
    #
    def sha1
      checksums && checksums["sha1"]
    end

    #
    # The MD5 of this artifact.
    #
    # @return [String]
    #
    def md5
      checksums && checksums["md5"]
    end

    #
    # @see Artifact#copy_or_move
    #
    def copy(destination, options = {})
      copy_or_move(:copy, destination, options)
    end

    #
    # Delete this artifact from repository, suppressing any +ResourceNotFound+
    # exceptions might occur.
    #
    # @return [Boolean]
    #   true if the object was deleted successfully, false otherwise
    #
    def delete
      !!client.delete(download_uri)
    rescue Error::HTTPError
      false
    end

    #
    # @see {Artifact#copy_or_move}
    #
    def move(destination, options = {})
      copy_or_move(:move, destination, options)
    end

    #
    # Set properties for this object. If no properties are given it lists the properties for this object.
    #
    # @example List all properties for an artifact
    #   artifact.properties #=> { 'licenses'=>['Apache-2.0'] }
    #
    # @example Set new properties for an artifact
    #   artifact.properties(maintainer: 'SuperStartup01') #=> { 'licenses'=>['Apache-2.0'], 'maintainer'=>'SuperStartup01' }
    #
    # @param [Hash<String, Object>] props (default: +nil+)
    #   A hash of properties and corresponding values to set for the artifact
    #
    # @return [Hash<String, Object>]
    #   the list of properties
    #
    def properties(props = nil)
      if props.nil? || props.empty?
        get_properties
      else
        set_properties(props)
        get_properties(true)
      end
    end

    #
    # Get compliance info for a given artifact path. The result includes
    # license and vulnerabilities, if any.
    #
    # **This requires the Black Duck addon to be enabled!**
    #
    # @example Get compliance info for an artifact
    #   artifact.compliance #=> { 'licenses' => [{ 'name' => 'LGPL v3' }] }
    #
    # @return [Hash<String, Array<Hash>>]
    #
    def compliance
      @compliance ||= client.get(File.join("/api/compliance", relative_path))
    end

    #
    # Download the artifact onto the local disk.
    #
    # @example Download an artifact
    #   artifact.download #=> /tmp/cache/000adad0-bac/artifact.deb
    #
    # @example Download a remote artifact into a specific target
    #   artifact.download('~/Desktop') #=> ~/Desktop/artifact.deb
    #
    # @param [String] target
    #   the target directory where the artifact should be downloaded to
    #   (defaults to a temporary directory). **It is the user's responsibility
    #   to cleanup the temporary directory when finished!**
    # @param [Hash] options
    # @option options [String] filename
    #   the name of the file when downloaded to disk (defaults to the basename
    #   of the file on the server)
    #
    # @return [String]
    #   the path where the file was downloaded on disk
    #
    def download(target = Dir.mktmpdir, options = {})
      target = File.expand_path(target)

      # Make the directory if it doesn't yet exist
      FileUtils.mkdir_p(target) unless File.exist?(target)

      # Use the server artifact's filename if one wasn't given
      filename = options[:filename] || File.basename(download_uri)

      # Construct the full path for the file
      destination = File.join(target, filename)

      File.open(destination, "wb") do |file|
        client.get(download_uri) do |chunk|
          file.write chunk
        end
      end

      destination
    end

    #
    # Upload an artifact into the repository. If the first parameter is a File
    # object, that file descriptor is passed to the uploader. If the first
    # parameter is a string, it is assumed to be the path to a local file on
    # disk. This method will automatically construct the File object from the
    # given path.
    #
    # @see bit.ly/1dhJRMO Artifactory Matrix Properties
    #
    # @example Upload an artifact from a File instance
    #   artifact = Artifact.new(local_path: '/local/path/to/file.deb')
    #   artifact.upload('libs-release-local', '/remote/path')
    #
    # @example Upload an artifact with matrix properties
    #   artifact = Artifact.new(local_path: '/local/path/to/file.deb')
    #   artifact.upload('libs-release-local', '/remote/path', {
    #     status: 'DEV',
    #     rating: 5,
    #     branch: 'master'
    #   })
    #
    # @param [String] repo
    #   the key of the repository to which to upload the file
    # @param [String] remote_path
    #   the path where this resource will live in the remote artifactory
    #   repository, relative to the repository key
    # @param [Hash] headers
    #   the list of headers to send with the request
    # @param [Hash] properties
    #   a list of matrix properties
    #
    # @return [Resource::Artifact]
    #
    def upload(repo, remote_path, properties = {}, headers = {})
      file     = File.new(File.expand_path(local_path))
      matrix   = to_matrix_properties(properties)
      endpoint = File.join("#{url_safe(repo)}#{matrix}", remote_path)

      # Include checksums in headers if given.
      headers["X-Checksum-Md5"] = md5   if md5
      headers["X-Checksum-Sha1"] = sha1 if sha1

      response = client.put(endpoint, file, headers)

      return unless response.is_a?(Hash)

      self.class.from_hash(response)
    end

    #
    # Upload the checksum for this artifact. **The artifact must already be
    # uploaded or Artifactory will throw an exception!**.
    #
    # @example Set an artifact's md5
    #   artifact = Artifact.new(local_path: '/local/path/to/file.deb')
    #   artifact.upload_checksum('libs-release-local', '/remote/path', :md5, 'ABCD1234')
    #
    # @param (see Artifact#upload)
    # @param [Symbol] type
    #   the type of checksum to write (+md5+ or +sha1+)
    # @param [String] value
    #   the actual checksum
    #
    # @return [true]
    #
    def upload_checksum(repo, remote_path, type, value)
      file = Tempfile.new("checksum.#{type}")
      file.write(value)
      file.rewind

      endpoint = File.join(url_safe(repo), "#{remote_path}.#{type}")

      client.put(endpoint, file)
      true
    ensure
      if file
        file.close
        file.unlink
      end
    end

    #
    # Upload an artifact with the given SHA checksum. Consult the artifactory
    # documentation for the possible responses when the checksums fail to
    # match.
    #
    # @see Artifact#upload More syntax examples
    #
    # @example Upload an artifact with a checksum
    #   artifact = Artifact.new(local_path: '/local/path/to/file.deb')
    #   artifact.upload_with_checksum('libs-release-local', /remote/path', 'ABCD1234')
    #
    # @param (see Artifact#upload)
    # @param [String] checksum
    #   the SHA1 checksum of the artifact to upload
    #
    def upload_with_checksum(repo, remote_path, checksum, properties = {})
      upload(repo, remote_path, properties,
        "X-Checksum-Deploy" => true,
        "X-Checksum-Sha1"   => checksum)
    end

    #
    # Upload an artifact with the given archive. Consult the artifactory
    # documentation for the format of the archive to upload.
    #
    # @see Artifact#upload More syntax examples
    #
    # @example Upload an artifact with a checksum
    #   artifact = Artifact.new(local_path: '/local/path/to/file.deb')
    #   artifact.upload_from_archive('/remote/path')
    #
    # @param (see Repository#upload)
    #
    def upload_from_archive(repo, remote_path, properties = {})
      upload(repo, remote_path, properties,
        "X-Explode-Archive" => true)
    end

    private

    #
    # Helper method for reading artifact properties
    #
    # @example List all properties for an artifact
    #   artifact.get_properties #=> { 'artifactory.licenses'=>['Apache-2.0'] }
    #
    # @param [TrueClass, FalseClass] refresh_cache (default: +false+)
    #   wether or not to use the locally cached value if it exists and is not nil
    #
    # @return [Hash<String, Object>]
    #   the list of properties
    #
    def get_properties(refresh_cache = false)
      if refresh_cache || @properties.nil?
        @properties = client.get(File.join("/api/storage", relative_path), properties: nil)["properties"]
      end

      @properties
    end

    #
    # Helper method for setting artifact properties
    #
    # @example Set properties for an artifact
    #   artifact.set_properties({ prop1: 'value1', 'prop2' => 'value2' })
    #
    # @param [Hash<String, Object>] properties
    #   A hash of properties and corresponding values to set for the artifact
    #
    # @return [Hash]
    #   the parsed JSON response from the server
    #
    def set_properties(properties)
      matrix = to_matrix_properties(properties)
      endpoint = File.join("/api/storage", relative_path) + "?properties=#{matrix}"

      client.put(endpoint, nil)
    end

    #
    # Helper method for extracting the relative (repo) path, since it's not
    # returned as part of the API.
    #
    # @example Get the relative URI from the resource
    #   /libs-release-local/org/acme/artifact.deb
    #
    # @return [String]
    #
    def relative_path
      @relative_path ||= uri.split("/api/storage", 2).last
    end

    #
    # Copy or move current artifact to a new destination.
    #
    # @example Move the current artifact to +ext-releases-local+
    #   artifact.move(to: '/ext-releaes-local/org/acme')
    # @example Copy the current artifact to +ext-releases-local+
    #   artifact.move(to: '/ext-releaes-local/org/acme')
    #
    # @param [Symbol] action
    #   the action (+:move+ or +:copy+)
    # @param [String] destination
    #   the server-side destination to move or copy the artifact
    # @param [Hash] options
    #   the list of options to pass
    #
    # @option options [Boolean] :fail_fast (default: +false+)
    #   fail on the first failure
    # @option options [Boolean] :suppress_layouts (default: +false+)
    #   suppress cross-layout module path translation during copying or moving
    # @option options [Boolean] :dry_run (default: +false+)
    #   pretend to do the copy or move
    #
    # @return [Hash]
    #   the parsed JSON response from the server
    #
    def copy_or_move(action, destination, options = {})
      params = {}.tap do |param|
        param[:to]              = destination
        param[:failFast]        = 1 if options[:fail_fast]
        param[:suppressLayouts] = 1 if options[:suppress_layouts]
        param[:dry]             = 1 if options[:dry_run]
      end

      endpoint = File.join("/api", action.to_s, relative_path) + "?#{to_query_string_parameters(params)}"

      client.post(endpoint, {})
    end
  end
end
