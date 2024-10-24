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

require "pathname"
require_relative "artifactory/version"

module Artifactory
  autoload :Client,       "artifactory/client"
  autoload :Configurable, "artifactory/configurable"
  autoload :Defaults,     "artifactory/defaults"
  autoload :Error,        "artifactory/errors"
  autoload :Util,         "artifactory/util"

  module Collection
    autoload :Artifact, "artifactory/collections/artifact"
    autoload :Base,     "artifactory/collections/base"
    autoload :Build, "artifactory/collections/build"
  end

  module Resource
    autoload :Artifact,         "artifactory/resources/artifact"
    autoload :Backup,           "artifactory/resources/backup"
    autoload :Base,             "artifactory/resources/base"
    autoload :Build,            "artifactory/resources/build"
    autoload :BuildComponent,   "artifactory/resources/build_component"
    autoload :Certificate,      "artifactory/resources/certificate"
    autoload :Group,            "artifactory/resources/group"
    autoload :Layout,           "artifactory/resources/layout"
    autoload :LDAPSetting,      "artifactory/resources/ldap_setting"
    autoload :MailServer,       "artifactory/resources/mail_server"
    autoload :PermissionTarget, "artifactory/resources/permission_target"
    autoload :Plugin,           "artifactory/resources/plugin"
    autoload :Repository,       "artifactory/resources/repository"
    autoload :System,           "artifactory/resources/system"
    autoload :URLBase,          "artifactory/resources/url_base"
    autoload :User,             "artifactory/resources/user"
  end

  class << self
    include Artifactory::Configurable

    #
    # The root of the Artifactory gem. This method is useful for finding files
    # relative to the root of the repository.
    #
    # @return [Pathname]
    #
    def root
      @root ||= Pathname.new(File.expand_path("../../", __FILE__))
    end

    #
    # API client object based off the configured options in {Configurable}.
    #
    # @return [Artifactory::Client]
    #
    def client
      unless defined?(@client) && @client.same_options?(options)
        @client = Artifactory::Client.new(options)
      end

      @client
    end

    #
    # Delegate all methods to the client object, essentially making the module
    # object behave like a {Client}.
    #
    def method_missing(m, *args, &block)
      if client.respond_to?(m)
        client.send(m, *args, &block)
      else
        super
      end
    end

    #
    # Delegating +respond_to+ to the {Client}.
    #
    def respond_to_missing?(m, include_private = false)
      client.respond_to?(m) || super
    end
  end
end

# Load the initial default values
Artifactory.setup
