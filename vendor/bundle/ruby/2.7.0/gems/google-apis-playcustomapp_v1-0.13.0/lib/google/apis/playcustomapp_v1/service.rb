# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

require 'google/apis/core/base_service'
require 'google/apis/core/json_representation'
require 'google/apis/core/hashable'
require 'google/apis/errors'

module Google
  module Apis
    module PlaycustomappV1
      # Google Play Custom App Publishing API
      #
      # API to create and publish custom Android apps
      #
      # @example
      #    require 'google/apis/playcustomapp_v1'
      #
      #    Playcustomapp = Google::Apis::PlaycustomappV1 # Alias the module
      #    service = Playcustomapp::PlaycustomappService.new
      #
      # @see https://developers.google.com/android/work/play/custom-app-api/
      class PlaycustomappService < Google::Apis::Core::BaseService
        # @return [String]
        #  API key. Your API key identifies your project and provides you with API access,
        #  quota, and reports. Required unless you provide an OAuth 2.0 token.
        attr_accessor :key

        # @return [String]
        #  Available to use for quota purposes for server-side applications. Can be any
        #  arbitrary string assigned to a user, but should not exceed 40 characters.
        attr_accessor :quota_user

        def initialize
          super('https://playcustomapp.googleapis.com/', '',
                client_name: 'google-apis-playcustomapp_v1',
                client_version: Google::Apis::PlaycustomappV1::GEM_VERSION)
          @batch_path = 'batch'
        end
        
        # Creates a new custom app.
        # @param [Fixnum] account
        #   Developer account ID.
        # @param [Google::Apis::PlaycustomappV1::CustomApp] custom_app_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [IO, String] upload_source
        #   IO stream or filename containing content to upload
        # @param [String] content_type
        #   Content type of the uploaded content.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::PlaycustomappV1::CustomApp] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::PlaycustomappV1::CustomApp]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def create_account_custom_app(account, custom_app_object = nil, fields: nil, quota_user: nil, upload_source: nil, content_type: nil, options: nil, &block)
          if upload_source.nil?
            command = make_simple_command(:post, 'playcustomapp/v1/accounts/{account}/customApps', options)
          else
            command = make_upload_command(:post, 'playcustomapp/v1/accounts/{account}/customApps', options)
            command.upload_source = upload_source
            command.upload_content_type = content_type
          end
          command.request_representation = Google::Apis::PlaycustomappV1::CustomApp::Representation
          command.request_object = custom_app_object
          command.response_representation = Google::Apis::PlaycustomappV1::CustomApp::Representation
          command.response_class = Google::Apis::PlaycustomappV1::CustomApp
          command.params['account'] = account unless account.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end

        protected

        def apply_command_defaults(command)
          command.query['key'] = key unless key.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
        end
      end
    end
  end
end
