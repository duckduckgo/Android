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
  class Resource::MailServer < Resource::Base
    class << self
      #
      # Get a list of all mail servers in the system.
      #
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Array<Resource::MailServer>]
      #   the list of layouts
      #
      def all(options = {})
        config = Resource::System.configuration(options)
        list_from_config("config/mailServer", config, options)
      end

      #
      # Find (fetch) a mail server by its host.
      #
      # @example Find a MailServer by its host.
      #   mail_server.find('smtp.gmail.com') #=> #<MailServer host: 'smtp.gmail.com' ...>
      #
      # @param [String] host
      #   the host of the mail server to find
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Resource::MailServer, nil]
      #   an instance of the mail server that matches the given host, or +nil+
      #   if one does not exist
      #
      def find(host, options = {})
        config = Resource::System.configuration(options)
        find_from_config("config/mailServer/host[text()='#{host}']", config, options)
      rescue Error::HTTPError => e
        raise unless e.code == 404

        nil
      end
    end

    attribute :enabled
    attribute :host, -> { raise "host missing!" }
    attribute :port
    attribute :username
    attribute :password
    attribute :from
    attribute :subject_prefix
    attribute :tls
    attribute :ssl
    attribute :artifactory_url
  end
end
