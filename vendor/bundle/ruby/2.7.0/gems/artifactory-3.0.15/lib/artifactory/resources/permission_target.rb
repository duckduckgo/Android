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
  class Resource::PermissionTarget < Resource::Base
    VERBOSE_PERMS = {
      "d" => "delete",
      "m" => "admin",
      "n" => "annotate",
      "r" => "read",
      "w" => "deploy",
    }.freeze
    class << self
      #
      # Get a list of all PermissionTargets in the system.
      #
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Array<Resource::PermissionTarget>]
      #   the list of PermissionTargets
      #
      def all(options = {})
        client = extract_client!(options)
        client.get("/api/security/permissions").map do |hash|
          from_url(hash["uri"], client: client)
        end
      end

      #
      # Find (fetch) a permission target by its name.
      #
      # @example Find a permission target by its name
      #   PermissionTarget.find('readers') #=> #<PermissionTarget name: 'readers' ...>
      #
      # @param [String] name
      #   the name of the permission target to find
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Resource::PermissionTarget, nil]
      #   an instance of the permission target that matches the given name, or +nil+
      #   if one does not exist
      #
      def find(name, options = {})
        client = extract_client!(options)

        response = client.get("/api/security/permissions/#{url_safe(name)}")
        from_hash(response, client: client)
      rescue Error::HTTPError => e
        raise unless e.code == 404

        nil
      end

      #
      # @see Resource::Base.from_hash
      # Additionally use verbose names for permissions (e.g. 'read' for 'r')
      #
      def from_hash(hash, options = {})
        super.tap do |instance|
          %w{users groups}.each do |key|
            if instance.principals[key] && !instance.principals[key].nil?
              instance.principals[key] = Hash[instance.principals[key].map { |k, v| [k, verbose(v)] } ]
            end
          end
        end
      end

      private

      #
      # Replace an array of permissions with one using verbose permission names
      #
      def verbose(array)
        array.map { |elt| VERBOSE_PERMS[elt] }.sort
      end
    end

    class Principal
      attr_accessor :users, :groups

      def initialize(users = {}, groups = {})
        @users = users
        @groups = groups
      end

      #
      # Converts the user-friendly form of the principals hash to one suitable
      # for posting to Artifactory.
      # @return [Hash]
      #
      def to_abbreviated
        { "users" => abbreviate_principal(@users), "groups" => abbreviate_principal(@groups) }
      end

      private

      #
      # Replace an array of verbose permission names with an equivalent array of abbreviated permission names.
      #
      def abbreviate_permissions(array)
        inverse = VERBOSE_PERMS.invert
        if (inverse.keys & array).sort != array.sort
          raise "One of your principals contains an invalid permission.  Valid permissions are #{inverse.keys.join(", ")}"
        end

        array.map { |elt| inverse[elt] }.sort
      end

      #
      # Replace a principal with verbose permissions with an equivalent one with abbreviated permissions.
      #
      def abbreviate_principal(principal_hash)
        Hash[principal_hash.map { |k, v| [k, abbreviate_permissions(v)] } ]
      end
    end

    attribute :name, -> { raise "Name missing!" }
    attribute :includes_pattern, "**"
    attribute :excludes_pattern, ""
    attribute :repositories
    attribute :principals, { "users" => {}, "groups" => {} }

    def client_principal
      @client_principal ||= Principal.new(principals["users"], principals["groups"])
    end

    #
    # Delete this PermissionTarget from artifactory, suppressing any +ResourceNotFound+
    # exceptions might occur.
    #
    # @return [Boolean]
    #   true if the object was deleted successfully, false otherwise
    #
    def delete
      client.delete(api_path)
      true
    rescue Error::HTTPError
      false
    end

    #
    # Save the PermissionTarget to the artifactory server.
    # See http://bit.ly/1qMOw0L
    #
    # @return [Boolean]
    #
    def save
      send("principals=", client_principal.to_abbreviated)
      client.put(api_path, to_json, headers)
      true
    end

    #
    # Getter for groups
    #
    def groups
      client_principal.groups
    end

    #
    # Setter for groups (groups_hash expected to be friendly)
    #
    def groups=(groups_hash)
      client_principal.groups = Hash[groups_hash.map { |k, v| [k, v.sort] } ]
    end

    #
    # Getter for users
    #
    def users
      client_principal.users
    end

    #
    # Setter for users (expecting users_hash to be friendly)
    #
    def users=(users_hash)
      client_principal.users = Hash[users_hash.map { |k, v| [k, v.sort] } ]
    end

    private

    #
    # The path to this PermissionTarget on the server.
    #
    # @return [String]
    #
    def api_path
      @api_path ||= "/api/security/permissions/#{url_safe(name)}"
    end

    #
    # The default headers for this object. This includes the +Content-Type+.
    #
    # @return [Hash]
    #
    def headers
      @headers ||= {
        "Content-Type" => "application/vnd.org.jfrog.artifactory.security.PermissionTarget+json",
      }
    end
  end
end
