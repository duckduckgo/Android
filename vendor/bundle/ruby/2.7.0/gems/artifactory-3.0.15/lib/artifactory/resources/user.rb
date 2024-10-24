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
  class Resource::User < Resource::Base
    class << self
      #
      # Get a list of all users in the system.
      #
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Array<Resource::User>]
      #   the list of users
      #
      def all(options = {})
        client = extract_client!(options)
        client.get("/api/security/users").map do |hash|
          from_url(hash["uri"], client: client)
        end
      end

      #
      # Find (fetch) a user by its name.
      #
      # @example Find a user by its name
      #   User.find('readers') #=> #<User name: 'readers' ...>
      #
      # @param [String] name
      #   the name of the user to find
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Resource::User, nil]
      #   an instance of the user that matches the given name, or +nil+
      #   if one does not exist
      #
      def find(name, options = {})
        client = extract_client!(options)

        response = client.get("/api/security/users/#{url_safe(name)}")
        from_hash(response, client: client)
      rescue Error::HTTPError => e
        raise unless e.code == 404

        nil
      end
    end

    attribute :admin, false
    attribute :email
    attribute :groups, []
    attribute :internal_password_disabled, false
    attribute :last_logged_in
    attribute :name, -> { raise "Name missing" }
    attribute :password # write only, never returned
    attribute :profile_updatable, true
    attribute :realm

    #
    # Delete this user from artifactory, suppressing any +ResourceNotFound+
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

    #
    # Creates or updates a user configuration depending on if the
    # user configuration previously existed.
    #
    # @return [Boolean]
    #
    def save
      if self.class.find(name, client: client)
        client.post(api_path, to_json, headers)
      else
        client.put(api_path, to_json, headers)
      end
      true
    end

    private

    #
    # The path to this user on the server.
    #
    # @return [String]
    #
    def api_path
      @api_path ||= "/api/security/users/#{url_safe(name)}"
    end

    #
    # The default headers for this object. This includes the +Content-Type+.
    #
    # @return [Hash]
    #
    def headers
      @headers ||= {
        "Content-Type" => "application/vnd.org.jfrog.artifactory.security.User+json",
      }
    end
  end
end
