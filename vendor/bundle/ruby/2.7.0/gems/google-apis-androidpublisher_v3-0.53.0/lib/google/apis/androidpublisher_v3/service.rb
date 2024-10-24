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
    module AndroidpublisherV3
      # Google Play Android Developer API
      #
      # Lets Android application developers access their Google Play accounts. At a
      #  high level, the expected workflow is to "insert" an Edit, make changes as
      #  necessary, and then "commit" it.
      #
      # @example
      #    require 'google/apis/androidpublisher_v3'
      #
      #    Androidpublisher = Google::Apis::AndroidpublisherV3 # Alias the module
      #    service = Androidpublisher::AndroidPublisherService.new
      #
      # @see https://developers.google.com/android-publisher
      class AndroidPublisherService < Google::Apis::Core::BaseService
        # @return [String]
        #  API key. Your API key identifies your project and provides you with API access,
        #  quota, and reports. Required unless you provide an OAuth 2.0 token.
        attr_accessor :key

        # @return [String]
        #  Available to use for quota purposes for server-side applications. Can be any
        #  arbitrary string assigned to a user, but should not exceed 40 characters.
        attr_accessor :quota_user

        def initialize
          super('https://androidpublisher.googleapis.com/', '',
                client_name: 'google-apis-androidpublisher_v3',
                client_version: Google::Apis::AndroidpublisherV3::GEM_VERSION)
          @batch_path = 'batch'
        end
        
        # Creates a new device tier config for an app.
        # @param [String] package_name
        #   Package name of the app.
        # @param [Google::Apis::AndroidpublisherV3::DeviceTierConfig] device_tier_config_object
        # @param [Boolean] allow_unknown_devices
        #   Whether the service should accept device IDs that are unknown to Play's device
        #   catalog.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::DeviceTierConfig] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::DeviceTierConfig]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def create_application_device_tier_config(package_name, device_tier_config_object = nil, allow_unknown_devices: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/deviceTierConfigs', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::DeviceTierConfig::Representation
          command.request_object = device_tier_config_object
          command.response_representation = Google::Apis::AndroidpublisherV3::DeviceTierConfig::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::DeviceTierConfig
          command.params['packageName'] = package_name unless package_name.nil?
          command.query['allowUnknownDevices'] = allow_unknown_devices unless allow_unknown_devices.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Returns a particular device tier config.
        # @param [String] package_name
        #   Package name of the app.
        # @param [Fixnum] device_tier_config_id
        #   Required. Id of an existing device tier config.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::DeviceTierConfig] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::DeviceTierConfig]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_application_device_tier_config(package_name, device_tier_config_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/deviceTierConfigs/{deviceTierConfigId}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::DeviceTierConfig::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::DeviceTierConfig
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['deviceTierConfigId'] = device_tier_config_id unless device_tier_config_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Returns created device tier configs, ordered by descending creation time.
        # @param [String] package_name
        #   Package name of the app.
        # @param [Fixnum] page_size
        #   The maximum number of device tier configs to return. The service may return
        #   fewer than this value. If unspecified, at most 10 device tier configs will be
        #   returned. The maximum value for this field is 100; values above 100 will be
        #   coerced to 100. Device tier configs will be ordered by descending creation
        #   time.
        # @param [String] page_token
        #   A page token, received from a previous `ListDeviceTierConfigs` call. Provide
        #   this to retrieve the subsequent page.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ListDeviceTierConfigsResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ListDeviceTierConfigsResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_application_device_tier_configs(package_name, page_size: nil, page_token: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/deviceTierConfigs', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::ListDeviceTierConfigsResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ListDeviceTierConfigsResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.query['pageSize'] = page_size unless page_size.nil?
          command.query['pageToken'] = page_token unless page_token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Commits an app edit.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [Boolean] changes_not_sent_for_review
        #   Indicates that the changes in this edit will not be reviewed until they are
        #   explicitly sent for review from the Google Play Console UI. These changes will
        #   be added to any other changes that are not yet sent for review.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::AppEdit] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::AppEdit]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def commit_edit(package_name, edit_id, changes_not_sent_for_review: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}:commit', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::AppEdit::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::AppEdit
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['changesNotSentForReview'] = changes_not_sent_for_review unless changes_not_sent_for_review.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deletes an app edit.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def delete_edit(package_name, edit_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'androidpublisher/v3/applications/{packageName}/edits/{editId}', options)
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets an app edit.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::AppEdit] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::AppEdit]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_edit(package_name, edit_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/edits/{editId}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::AppEdit::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::AppEdit
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Creates a new edit for an app.
        # @param [String] package_name
        #   Package name of the app.
        # @param [Google::Apis::AndroidpublisherV3::AppEdit] app_edit_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::AppEdit] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::AppEdit]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def insert_edit(package_name, app_edit_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/edits', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::AppEdit::Representation
          command.request_object = app_edit_object
          command.response_representation = Google::Apis::AndroidpublisherV3::AppEdit::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::AppEdit
          command.params['packageName'] = package_name unless package_name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Validates an app edit.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::AppEdit] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::AppEdit]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def validate_edit(package_name, edit_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}:validate', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::AppEdit::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::AppEdit
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Creates a new APK without uploading the APK itself to Google Play, instead
        # hosting the APK at a specified URL. This function is only available to
        # organizations using Managed Play whose application is configured to restrict
        # distribution to the organizations.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [Google::Apis::AndroidpublisherV3::ApksAddExternallyHostedRequest] apks_add_externally_hosted_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ApksAddExternallyHostedResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ApksAddExternallyHostedResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def addexternallyhosted_edit_apk(package_name, edit_id, apks_add_externally_hosted_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/apks/externallyHosted', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::ApksAddExternallyHostedRequest::Representation
          command.request_object = apks_add_externally_hosted_request_object
          command.response_representation = Google::Apis::AndroidpublisherV3::ApksAddExternallyHostedResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ApksAddExternallyHostedResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists all current APKs of the app and edit.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ApksListResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ApksListResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_edit_apks(package_name, edit_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/apks', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::ApksListResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ApksListResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Uploads an APK and adds to the current edit.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
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
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Apk] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Apk]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def upload_edit_apk(package_name, edit_id, fields: nil, quota_user: nil, upload_source: nil, content_type: nil, options: nil, &block)
          if upload_source.nil?
            command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/apks', options)
          else
            command = make_upload_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/apks', options)
            command.upload_source = upload_source
            command.upload_content_type = content_type
          end
          command.response_representation = Google::Apis::AndroidpublisherV3::Apk::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Apk
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists all current Android App Bundles of the app and edit.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::BundlesListResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::BundlesListResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_edit_bundles(package_name, edit_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/bundles', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::BundlesListResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::BundlesListResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Uploads a new Android App Bundle to this edit. If you are using the Google API
        # client libraries, please increase the timeout of the http request before
        # calling this endpoint (a timeout of 2 minutes is recommended). See [Timeouts
        # and Errors](https://developers.google.com/api-client-library/java/google-api-
        # java-client/errors) for an example in java.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [Boolean] ack_bundle_installation_warning
        #   Must be set to true if the app bundle installation may trigger a warning on
        #   user devices (for example, if installation size may be over a threshold,
        #   typically 100 MB).
        # @param [String] device_tier_config_id
        #   Device tier config (DTC) to be used for generating deliverables (APKs).
        #   Contains id of the DTC or "LATEST" for last uploaded DTC.
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
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Bundle] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Bundle]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def upload_edit_bundle(package_name, edit_id, ack_bundle_installation_warning: nil, device_tier_config_id: nil, fields: nil, quota_user: nil, upload_source: nil, content_type: nil, options: nil, &block)
          if upload_source.nil?
            command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/bundles', options)
          else
            command = make_upload_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/bundles', options)
            command.upload_source = upload_source
            command.upload_content_type = content_type
          end
          command.response_representation = Google::Apis::AndroidpublisherV3::Bundle::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Bundle
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['ackBundleInstallationWarning'] = ack_bundle_installation_warning unless ack_bundle_installation_warning.nil?
          command.query['deviceTierConfigId'] = device_tier_config_id unless device_tier_config_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets country availability.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] track
        #   The track to read from.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::TrackCountryAvailability] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::TrackCountryAvailability]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_edit_countryavailability(package_name, edit_id, track, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/countryAvailability/{track}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::TrackCountryAvailability::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::TrackCountryAvailability
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['track'] = track unless track.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Uploads a new deobfuscation file and attaches to the specified APK.
        # @param [String] package_name
        #   Unique identifier for the Android app.
        # @param [String] edit_id
        #   Unique identifier for this edit.
        # @param [Fixnum] apk_version_code
        #   The version code of the APK whose Deobfuscation File is being uploaded.
        # @param [String] deobfuscation_file_type
        #   The type of the deobfuscation file.
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
        # @yieldparam result [Google::Apis::AndroidpublisherV3::DeobfuscationFilesUploadResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::DeobfuscationFilesUploadResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def upload_edit_deobfuscationfile(package_name, edit_id, apk_version_code, deobfuscation_file_type, fields: nil, quota_user: nil, upload_source: nil, content_type: nil, options: nil, &block)
          if upload_source.nil?
            command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/apks/{apkVersionCode}/deobfuscationFiles/{deobfuscationFileType}', options)
          else
            command = make_upload_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/apks/{apkVersionCode}/deobfuscationFiles/{deobfuscationFileType}', options)
            command.upload_source = upload_source
            command.upload_content_type = content_type
          end
          command.response_representation = Google::Apis::AndroidpublisherV3::DeobfuscationFilesUploadResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::DeobfuscationFilesUploadResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['apkVersionCode'] = apk_version_code unless apk_version_code.nil?
          command.params['deobfuscationFileType'] = deobfuscation_file_type unless deobfuscation_file_type.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets details of an app.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::AppDetails] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::AppDetails]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_edit_detail(package_name, edit_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/details', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::AppDetails::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::AppDetails
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Patches details of an app.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [Google::Apis::AndroidpublisherV3::AppDetails] app_details_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::AppDetails] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::AppDetails]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def patch_edit_detail(package_name, edit_id, app_details_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/details', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::AppDetails::Representation
          command.request_object = app_details_object
          command.response_representation = Google::Apis::AndroidpublisherV3::AppDetails::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::AppDetails
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Updates details of an app.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [Google::Apis::AndroidpublisherV3::AppDetails] app_details_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::AppDetails] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::AppDetails]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def update_edit_detail(package_name, edit_id, app_details_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:put, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/details', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::AppDetails::Representation
          command.request_object = app_details_object
          command.response_representation = Google::Apis::AndroidpublisherV3::AppDetails::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::AppDetails
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Fetches the expansion file configuration for the specified APK.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [Fixnum] apk_version_code
        #   The version code of the APK whose expansion file configuration is being read
        #   or modified.
        # @param [String] expansion_file_type
        #   The file type of the file configuration which is being read or modified.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ExpansionFile] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ExpansionFile]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_edit_expansionfile(package_name, edit_id, apk_version_code, expansion_file_type, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/apks/{apkVersionCode}/expansionFiles/{expansionFileType}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::ExpansionFile::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ExpansionFile
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['apkVersionCode'] = apk_version_code unless apk_version_code.nil?
          command.params['expansionFileType'] = expansion_file_type unless expansion_file_type.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Patches the APK's expansion file configuration to reference another APK's
        # expansion file. To add a new expansion file use the Upload method.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [Fixnum] apk_version_code
        #   The version code of the APK whose expansion file configuration is being read
        #   or modified.
        # @param [String] expansion_file_type
        #   The file type of the expansion file configuration which is being updated.
        # @param [Google::Apis::AndroidpublisherV3::ExpansionFile] expansion_file_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ExpansionFile] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ExpansionFile]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def patch_edit_expansionfile(package_name, edit_id, apk_version_code, expansion_file_type, expansion_file_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/apks/{apkVersionCode}/expansionFiles/{expansionFileType}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::ExpansionFile::Representation
          command.request_object = expansion_file_object
          command.response_representation = Google::Apis::AndroidpublisherV3::ExpansionFile::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ExpansionFile
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['apkVersionCode'] = apk_version_code unless apk_version_code.nil?
          command.params['expansionFileType'] = expansion_file_type unless expansion_file_type.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Updates the APK's expansion file configuration to reference another APK's
        # expansion file. To add a new expansion file use the Upload method.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [Fixnum] apk_version_code
        #   The version code of the APK whose expansion file configuration is being read
        #   or modified.
        # @param [String] expansion_file_type
        #   The file type of the file configuration which is being read or modified.
        # @param [Google::Apis::AndroidpublisherV3::ExpansionFile] expansion_file_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ExpansionFile] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ExpansionFile]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def update_edit_expansionfile(package_name, edit_id, apk_version_code, expansion_file_type, expansion_file_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:put, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/apks/{apkVersionCode}/expansionFiles/{expansionFileType}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::ExpansionFile::Representation
          command.request_object = expansion_file_object
          command.response_representation = Google::Apis::AndroidpublisherV3::ExpansionFile::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ExpansionFile
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['apkVersionCode'] = apk_version_code unless apk_version_code.nil?
          command.params['expansionFileType'] = expansion_file_type unless expansion_file_type.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Uploads a new expansion file and attaches to the specified APK.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [Fixnum] apk_version_code
        #   The version code of the APK whose expansion file configuration is being read
        #   or modified.
        # @param [String] expansion_file_type
        #   The file type of the expansion file configuration which is being updated.
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
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ExpansionFilesUploadResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ExpansionFilesUploadResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def upload_edit_expansionfile(package_name, edit_id, apk_version_code, expansion_file_type, fields: nil, quota_user: nil, upload_source: nil, content_type: nil, options: nil, &block)
          if upload_source.nil?
            command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/apks/{apkVersionCode}/expansionFiles/{expansionFileType}', options)
          else
            command = make_upload_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/apks/{apkVersionCode}/expansionFiles/{expansionFileType}', options)
            command.upload_source = upload_source
            command.upload_content_type = content_type
          end
          command.response_representation = Google::Apis::AndroidpublisherV3::ExpansionFilesUploadResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ExpansionFilesUploadResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['apkVersionCode'] = apk_version_code unless apk_version_code.nil?
          command.params['expansionFileType'] = expansion_file_type unless expansion_file_type.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deletes the image (specified by id) from the edit.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] language
        #   Language localization code (a BCP-47 language tag; for example, "de-AT" for
        #   Austrian German).
        # @param [String] image_type
        #   Type of the Image.
        # @param [String] image_id
        #   Unique identifier an image within the set of images attached to this edit.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def delete_edit_image(package_name, edit_id, language, image_type, image_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/listings/{language}/{imageType}/{imageId}', options)
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['language'] = language unless language.nil?
          command.params['imageType'] = image_type unless image_type.nil?
          command.params['imageId'] = image_id unless image_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deletes all images for the specified language and image type. Returns an empty
        # response if no images are found.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] language
        #   Language localization code (a BCP-47 language tag; for example, "de-AT" for
        #   Austrian German). Providing a language that is not supported by the App is a
        #   no-op.
        # @param [String] image_type
        #   Type of the Image. Providing an image type that refers to no images is a no-op.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ImagesDeleteAllResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ImagesDeleteAllResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def deleteall_edit_image(package_name, edit_id, language, image_type, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/listings/{language}/{imageType}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::ImagesDeleteAllResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ImagesDeleteAllResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['language'] = language unless language.nil?
          command.params['imageType'] = image_type unless image_type.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists all images. The response may be empty.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] language
        #   Language localization code (a BCP-47 language tag; for example, "de-AT" for
        #   Austrian German). There must be a store listing for the specified language.
        # @param [String] image_type
        #   Type of the Image. Providing an image type that refers to no images will
        #   return an empty response.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ImagesListResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ImagesListResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_edit_images(package_name, edit_id, language, image_type, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/listings/{language}/{imageType}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::ImagesListResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ImagesListResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['language'] = language unless language.nil?
          command.params['imageType'] = image_type unless image_type.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Uploads an image of the specified language and image type, and adds to the
        # edit.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] language
        #   Language localization code (a BCP-47 language tag; for example, "de-AT" for
        #   Austrian German). Providing a language that is not supported by the App is a
        #   no-op.
        # @param [String] image_type
        #   Type of the Image.
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
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ImagesUploadResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ImagesUploadResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def upload_edit_image(package_name, edit_id, language, image_type, fields: nil, quota_user: nil, upload_source: nil, content_type: nil, options: nil, &block)
          if upload_source.nil?
            command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/listings/{language}/{imageType}', options)
          else
            command = make_upload_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/listings/{language}/{imageType}', options)
            command.upload_source = upload_source
            command.upload_content_type = content_type
          end
          command.response_representation = Google::Apis::AndroidpublisherV3::ImagesUploadResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ImagesUploadResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['language'] = language unless language.nil?
          command.params['imageType'] = image_type unless image_type.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deletes a localized store listing.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] language
        #   Language localization code (a BCP-47 language tag; for example, "de-AT" for
        #   Austrian German).
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def delete_edit_listing(package_name, edit_id, language, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/listings/{language}', options)
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['language'] = language unless language.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deletes all store listings.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def deleteall_edit_listing(package_name, edit_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/listings', options)
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets a localized store listing.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] language
        #   Language localization code (a BCP-47 language tag; for example, "de-AT" for
        #   Austrian German).
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Listing] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Listing]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_edit_listing(package_name, edit_id, language, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/listings/{language}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::Listing::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Listing
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['language'] = language unless language.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists all localized store listings.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ListingsListResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ListingsListResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_edit_listings(package_name, edit_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/listings', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::ListingsListResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ListingsListResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Patches a localized store listing.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] language
        #   Language localization code (a BCP-47 language tag; for example, "de-AT" for
        #   Austrian German).
        # @param [Google::Apis::AndroidpublisherV3::Listing] listing_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Listing] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Listing]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def patch_edit_listing(package_name, edit_id, language, listing_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/listings/{language}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::Listing::Representation
          command.request_object = listing_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Listing::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Listing
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['language'] = language unless language.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Creates or updates a localized store listing.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] language
        #   Language localization code (a BCP-47 language tag; for example, "de-AT" for
        #   Austrian German).
        # @param [Google::Apis::AndroidpublisherV3::Listing] listing_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Listing] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Listing]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def update_edit_listing(package_name, edit_id, language, listing_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:put, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/listings/{language}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::Listing::Representation
          command.request_object = listing_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Listing::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Listing
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['language'] = language unless language.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets testers. Note: Testers resource does not support email lists.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] track
        #   The track to read from.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Testers] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Testers]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_edit_tester(package_name, edit_id, track, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/testers/{track}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::Testers::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Testers
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['track'] = track unless track.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Patches testers. Note: Testers resource does not support email lists.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] track
        #   The track to update.
        # @param [Google::Apis::AndroidpublisherV3::Testers] testers_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Testers] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Testers]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def patch_edit_tester(package_name, edit_id, track, testers_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/testers/{track}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::Testers::Representation
          command.request_object = testers_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Testers::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Testers
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['track'] = track unless track.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Updates testers. Note: Testers resource does not support email lists.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] track
        #   The track to update.
        # @param [Google::Apis::AndroidpublisherV3::Testers] testers_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Testers] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Testers]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def update_edit_tester(package_name, edit_id, track, testers_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:put, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/testers/{track}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::Testers::Representation
          command.request_object = testers_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Testers::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Testers
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['track'] = track unless track.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Creates a new track.
        # @param [String] package_name
        #   Required. Package name of the app.
        # @param [String] edit_id
        #   Required. Identifier of the edit.
        # @param [Google::Apis::AndroidpublisherV3::TrackConfig] track_config_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Track] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Track]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def create_edit_track(package_name, edit_id, track_config_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/tracks', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::TrackConfig::Representation
          command.request_object = track_config_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Track::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Track
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets a track.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] track
        #   Identifier of the track. [More on track name](https://developers.google.com/
        #   android-publisher/tracks#ff-track-name)
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Track] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Track]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_edit_track(package_name, edit_id, track, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/tracks/{track}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::Track::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Track
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['track'] = track unless track.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists all tracks.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::TracksListResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::TracksListResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_edit_tracks(package_name, edit_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/tracks', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::TracksListResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::TracksListResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Patches a track.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] track
        #   Identifier of the track. [More on track name](https://developers.google.com/
        #   android-publisher/tracks#ff-track-name)
        # @param [Google::Apis::AndroidpublisherV3::Track] track_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Track] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Track]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def patch_edit_track(package_name, edit_id, track, track_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/tracks/{track}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::Track::Representation
          command.request_object = track_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Track::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Track
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['track'] = track unless track.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Updates a track.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] edit_id
        #   Identifier of the edit.
        # @param [String] track
        #   Identifier of the track. [More on track name](https://developers.google.com/
        #   android-publisher/tracks#ff-track-name)
        # @param [Google::Apis::AndroidpublisherV3::Track] track_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Track] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Track]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def update_edit_track(package_name, edit_id, track, track_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:put, 'androidpublisher/v3/applications/{packageName}/edits/{editId}/tracks/{track}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::Track::Representation
          command.request_object = track_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Track::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Track
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['editId'] = edit_id unless edit_id.nil?
          command.params['track'] = track unless track.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Creates a new external transaction.
        # @param [String] parent
        #   Required. The parent resource where this external transaction will be created.
        #   Format: applications/`package_name`
        # @param [Google::Apis::AndroidpublisherV3::ExternalTransaction] external_transaction_object
        # @param [String] external_transaction_id
        #   Required. The id to use for the external transaction. Must be unique across
        #   all other transactions for the app. This value should be 1-63 characters and
        #   valid characters are /a-z0-9_-/. Do not use this field to store any Personally
        #   Identifiable Information (PII) such as emails. Attempting to store PII in this
        #   field may result in requests being blocked.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ExternalTransaction] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ExternalTransaction]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def createexternaltransaction_externaltransaction(parent, external_transaction_object = nil, external_transaction_id: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/{+parent}/externalTransactions', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::ExternalTransaction::Representation
          command.request_object = external_transaction_object
          command.response_representation = Google::Apis::AndroidpublisherV3::ExternalTransaction::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ExternalTransaction
          command.params['parent'] = parent unless parent.nil?
          command.query['externalTransactionId'] = external_transaction_id unless external_transaction_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets an existing external transaction.
        # @param [String] name
        #   Required. The name of the external transaction to retrieve. Format:
        #   applications/`package_name`/externalTransactions/`external_transaction`
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ExternalTransaction] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ExternalTransaction]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def getexternaltransaction_externaltransaction(name, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/{+name}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::ExternalTransaction::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ExternalTransaction
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Refunds or partially refunds an existing external transaction.
        # @param [String] name
        #   Required. The name of the external transaction that will be refunded. Format:
        #   applications/`package_name`/externalTransactions/`external_transaction`
        # @param [Google::Apis::AndroidpublisherV3::RefundExternalTransactionRequest] refund_external_transaction_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ExternalTransaction] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ExternalTransaction]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def refundexternaltransaction_externaltransaction(name, refund_external_transaction_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/{+name}:refund', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::RefundExternalTransactionRequest::Representation
          command.request_object = refund_external_transaction_request_object
          command.response_representation = Google::Apis::AndroidpublisherV3::ExternalTransaction::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ExternalTransaction
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Downloads a single signed APK generated from an app bundle.
        # @param [String] package_name
        #   Package name of the app.
        # @param [Fixnum] version_code
        #   Version code of the app bundle.
        # @param [String] download_id
        #   Download ID, which uniquely identifies the APK to download. Can be obtained
        #   from the response of `generatedapks.list` method.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [IO, String] download_dest
        #   IO stream or filename to receive content download
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def download_generatedapk(package_name, version_code, download_id, fields: nil, quota_user: nil, download_dest: nil, options: nil, &block)
          if download_dest.nil?
            command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/generatedApks/{versionCode}/downloads/{downloadId}:download', options)
          else
            command = make_download_command(:get, 'androidpublisher/v3/applications/{packageName}/generatedApks/{versionCode}/downloads/{downloadId}:download', options)
            command.download_dest = download_dest
          end
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['versionCode'] = version_code unless version_code.nil?
          command.params['downloadId'] = download_id unless download_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Returns download metadata for all APKs that were generated from a given app
        # bundle.
        # @param [String] package_name
        #   Package name of the app.
        # @param [Fixnum] version_code
        #   Version code of the app bundle.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::GeneratedApksListResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::GeneratedApksListResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_generatedapks(package_name, version_code, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/generatedApks/{versionCode}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::GeneratedApksListResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::GeneratedApksListResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['versionCode'] = version_code unless version_code.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Grant access for a user to the given package.
        # @param [String] parent
        #   Required. The user which needs permission. Format: developers/`developer`/
        #   users/`user`
        # @param [Google::Apis::AndroidpublisherV3::Grant] grant_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Grant] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Grant]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def create_grant(parent, grant_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/{+parent}/grants', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::Grant::Representation
          command.request_object = grant_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Grant::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Grant
          command.params['parent'] = parent unless parent.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Removes all access for the user to the given package or developer account.
        # @param [String] name
        #   Required. The name of the grant to delete. Format: developers/`developer`/
        #   users/`email`/grants/`package_name`
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def delete_grant(name, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'androidpublisher/v3/{+name}', options)
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Updates access for the user to the given package.
        # @param [String] name
        #   Required. Resource name for this grant, following the pattern "developers/`
        #   developer`/users/`email`/grants/`package_name`". If this grant is for a draft
        #   app, the app ID will be used in this resource name instead of the package name.
        # @param [Google::Apis::AndroidpublisherV3::Grant] grant_object
        # @param [String] update_mask
        #   Optional. The list of fields to be updated.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Grant] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Grant]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def patch_grant(name, grant_object = nil, update_mask: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'androidpublisher/v3/{+name}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::Grant::Representation
          command.request_object = grant_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Grant::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Grant
          command.params['name'] = name unless name.nil?
          command.query['updateMask'] = update_mask unless update_mask.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deletes an in-app product (a managed product or a subscription). This method
        # should no longer be used to delete subscriptions. See [this article](https://
        # android-developers.googleblog.com/2023/06/changes-to-google-play-developer-api-
        # june-2023.html) for more information.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] sku
        #   Unique identifier for the in-app product.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def delete_inappproduct(package_name, sku, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'androidpublisher/v3/applications/{packageName}/inappproducts/{sku}', options)
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['sku'] = sku unless sku.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets an in-app product, which can be a managed product or a subscription. This
        # method should no longer be used to retrieve subscriptions. See [this article](
        # https://android-developers.googleblog.com/2023/06/changes-to-google-play-
        # developer-api-june-2023.html) for more information.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] sku
        #   Unique identifier for the in-app product.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::InAppProduct] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::InAppProduct]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_inappproduct(package_name, sku, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/inappproducts/{sku}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::InAppProduct::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::InAppProduct
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['sku'] = sku unless sku.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Creates an in-app product (a managed product or a subscription). This method
        # should no longer be used to create subscriptions. See [this article](https://
        # android-developers.googleblog.com/2023/06/changes-to-google-play-developer-api-
        # june-2023.html) for more information.
        # @param [String] package_name
        #   Package name of the app.
        # @param [Google::Apis::AndroidpublisherV3::InAppProduct] in_app_product_object
        # @param [Boolean] auto_convert_missing_prices
        #   If true the prices for all regions targeted by the parent app that don't have
        #   a price specified for this in-app product will be auto converted to the target
        #   currency based on the default price. Defaults to false.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::InAppProduct] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::InAppProduct]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def insert_inappproduct(package_name, in_app_product_object = nil, auto_convert_missing_prices: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/inappproducts', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::InAppProduct::Representation
          command.request_object = in_app_product_object
          command.response_representation = Google::Apis::AndroidpublisherV3::InAppProduct::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::InAppProduct
          command.params['packageName'] = package_name unless package_name.nil?
          command.query['autoConvertMissingPrices'] = auto_convert_missing_prices unless auto_convert_missing_prices.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists all in-app products - both managed products and subscriptions. If an app
        # has a large number of in-app products, the response may be paginated. In this
        # case the response field `tokenPagination.nextPageToken` will be set and the
        # caller should provide its value as a `token` request parameter to retrieve the
        # next page. This method should no longer be used to retrieve subscriptions. See
        # [this article](https://android-developers.googleblog.com/2023/06/changes-to-
        # google-play-developer-api-june-2023.html) for more information.
        # @param [String] package_name
        #   Package name of the app.
        # @param [Fixnum] max_results
        #   Deprecated and ignored. The page size is determined by the server.
        # @param [Fixnum] start_index
        #   Deprecated and ignored. Set the `token` parameter to retrieve the next page.
        # @param [String] token
        #   Pagination token. If empty, list starts at the first product.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::InappproductsListResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::InappproductsListResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_inappproducts(package_name, max_results: nil, start_index: nil, token: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/inappproducts', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::InappproductsListResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::InappproductsListResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.query['maxResults'] = max_results unless max_results.nil?
          command.query['startIndex'] = start_index unless start_index.nil?
          command.query['token'] = token unless token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Patches an in-app product (a managed product or a subscription). This method
        # should no longer be used to update subscriptions. See [this article](https://
        # android-developers.googleblog.com/2023/06/changes-to-google-play-developer-api-
        # june-2023.html) for more information.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] sku
        #   Unique identifier for the in-app product.
        # @param [Google::Apis::AndroidpublisherV3::InAppProduct] in_app_product_object
        # @param [Boolean] auto_convert_missing_prices
        #   If true the prices for all regions targeted by the parent app that don't have
        #   a price specified for this in-app product will be auto converted to the target
        #   currency based on the default price. Defaults to false.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::InAppProduct] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::InAppProduct]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def patch_inappproduct(package_name, sku, in_app_product_object = nil, auto_convert_missing_prices: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'androidpublisher/v3/applications/{packageName}/inappproducts/{sku}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::InAppProduct::Representation
          command.request_object = in_app_product_object
          command.response_representation = Google::Apis::AndroidpublisherV3::InAppProduct::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::InAppProduct
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['sku'] = sku unless sku.nil?
          command.query['autoConvertMissingPrices'] = auto_convert_missing_prices unless auto_convert_missing_prices.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Updates an in-app product (a managed product or a subscription). This method
        # should no longer be used to update subscriptions. See [this article](https://
        # android-developers.googleblog.com/2023/06/changes-to-google-play-developer-api-
        # june-2023.html) for more information.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] sku
        #   Unique identifier for the in-app product.
        # @param [Google::Apis::AndroidpublisherV3::InAppProduct] in_app_product_object
        # @param [Boolean] allow_missing
        #   If set to true, and the in-app product with the given package_name and sku
        #   doesn't exist, the in-app product will be created.
        # @param [Boolean] auto_convert_missing_prices
        #   If true the prices for all regions targeted by the parent app that don't have
        #   a price specified for this in-app product will be auto converted to the target
        #   currency based on the default price. Defaults to false.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::InAppProduct] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::InAppProduct]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def update_inappproduct(package_name, sku, in_app_product_object = nil, allow_missing: nil, auto_convert_missing_prices: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:put, 'androidpublisher/v3/applications/{packageName}/inappproducts/{sku}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::InAppProduct::Representation
          command.request_object = in_app_product_object
          command.response_representation = Google::Apis::AndroidpublisherV3::InAppProduct::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::InAppProduct
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['sku'] = sku unless sku.nil?
          command.query['allowMissing'] = allow_missing unless allow_missing.nil?
          command.query['autoConvertMissingPrices'] = auto_convert_missing_prices unless auto_convert_missing_prices.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Uploads an APK to internal app sharing. If you are using the Google API client
        # libraries, please increase the timeout of the http request before calling this
        # endpoint (a timeout of 2 minutes is recommended). See [Timeouts and Errors](
        # https://developers.google.com/api-client-library/java/google-api-java-client/
        # errors) for an example in java.
        # @param [String] package_name
        #   Package name of the app.
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
        # @yieldparam result [Google::Apis::AndroidpublisherV3::InternalAppSharingArtifact] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::InternalAppSharingArtifact]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def uploadapk_internalappsharingartifact(package_name, fields: nil, quota_user: nil, upload_source: nil, content_type: nil, options: nil, &block)
          if upload_source.nil?
            command = make_simple_command(:post, 'androidpublisher/v3/applications/internalappsharing/{packageName}/artifacts/apk', options)
          else
            command = make_upload_command(:post, 'androidpublisher/v3/applications/internalappsharing/{packageName}/artifacts/apk', options)
            command.upload_source = upload_source
            command.upload_content_type = content_type
          end
          command.response_representation = Google::Apis::AndroidpublisherV3::InternalAppSharingArtifact::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::InternalAppSharingArtifact
          command.params['packageName'] = package_name unless package_name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Uploads an app bundle to internal app sharing. If you are using the Google API
        # client libraries, please increase the timeout of the http request before
        # calling this endpoint (a timeout of 2 minutes is recommended). See [Timeouts
        # and Errors](https://developers.google.com/api-client-library/java/google-api-
        # java-client/errors) for an example in java.
        # @param [String] package_name
        #   Package name of the app.
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
        # @yieldparam result [Google::Apis::AndroidpublisherV3::InternalAppSharingArtifact] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::InternalAppSharingArtifact]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def uploadbundle_internalappsharingartifact(package_name, fields: nil, quota_user: nil, upload_source: nil, content_type: nil, options: nil, &block)
          if upload_source.nil?
            command = make_simple_command(:post, 'androidpublisher/v3/applications/internalappsharing/{packageName}/artifacts/bundle', options)
          else
            command = make_upload_command(:post, 'androidpublisher/v3/applications/internalappsharing/{packageName}/artifacts/bundle', options)
            command.upload_source = upload_source
            command.upload_content_type = content_type
          end
          command.response_representation = Google::Apis::AndroidpublisherV3::InternalAppSharingArtifact::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::InternalAppSharingArtifact
          command.params['packageName'] = package_name unless package_name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Calculates the region prices, using today's exchange rate and country-specific
        # pricing patterns, based on the price in the request for a set of regions.
        # @param [String] package_name
        #   Required. The app package name.
        # @param [Google::Apis::AndroidpublisherV3::ConvertRegionPricesRequest] convert_region_prices_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ConvertRegionPricesResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ConvertRegionPricesResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def convert_monetization_region_prices(package_name, convert_region_prices_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/pricing:convertRegionPrices', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::ConvertRegionPricesRequest::Representation
          command.request_object = convert_region_prices_request_object
          command.response_representation = Google::Apis::AndroidpublisherV3::ConvertRegionPricesResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ConvertRegionPricesResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Archives a subscription. Can only be done if at least one base plan was active
        # in the past, and no base plan is available for new or existing subscribers
        # currently. This action is irreversible, and the subscription ID will remain
        # reserved.
        # @param [String] package_name
        #   Required. The parent app (package name) of the app of the subscription to
        #   delete.
        # @param [String] product_id
        #   Required. The unique product ID of the subscription to delete.
        # @param [Google::Apis::AndroidpublisherV3::ArchiveSubscriptionRequest] archive_subscription_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Subscription] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Subscription]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def archive_subscription(package_name, product_id, archive_subscription_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}:archive', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::ArchiveSubscriptionRequest::Representation
          command.request_object = archive_subscription_request_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Subscription::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Subscription
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Creates a new subscription. Newly added base plans will remain in draft state
        # until activated.
        # @param [String] package_name
        #   Required. The parent app (package name) for which the subscription should be
        #   created. Must be equal to the package_name field on the Subscription resource.
        # @param [Google::Apis::AndroidpublisherV3::Subscription] subscription_object
        # @param [String] product_id
        #   Required. The ID to use for the subscription. For the requirements on this
        #   format, see the documentation of the product_id field on the Subscription
        #   resource.
        # @param [String] regions_version_version
        #   Required. A string representing the version of available regions being used
        #   for the specified resource. Regional prices for the resource have to be
        #   specified according to the information published in [this article](https://
        #   support.google.com/googleplay/android-developer/answer/10532353). Each time
        #   the supported locations substantially change, the version will be incremented.
        #   Using this field will ensure that creating and updating the resource with an
        #   older region's version and set of regional prices and currencies will succeed
        #   even though a new version is available. The latest version is 2022/02.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Subscription] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Subscription]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def create_monetization_subscription(package_name, subscription_object = nil, product_id: nil, regions_version_version: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/subscriptions', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::Subscription::Representation
          command.request_object = subscription_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Subscription::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Subscription
          command.params['packageName'] = package_name unless package_name.nil?
          command.query['productId'] = product_id unless product_id.nil?
          command.query['regionsVersion.version'] = regions_version_version unless regions_version_version.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deletes a subscription. A subscription can only be deleted if it has never had
        # a base plan published.
        # @param [String] package_name
        #   Required. The parent app (package name) of the app of the subscription to
        #   delete.
        # @param [String] product_id
        #   Required. The unique product ID of the subscription to delete.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def delete_monetization_subscription(package_name, product_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}', options)
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Reads a single subscription.
        # @param [String] package_name
        #   Required. The parent app (package name) of the subscription to get.
        # @param [String] product_id
        #   Required. The unique product ID of the subscription to get.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Subscription] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Subscription]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_monetization_subscription(package_name, product_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::Subscription::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Subscription
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists all subscriptions under a given app.
        # @param [String] package_name
        #   Required. The parent app (package name) for which the subscriptions should be
        #   read.
        # @param [Fixnum] page_size
        #   The maximum number of subscriptions to return. The service may return fewer
        #   than this value. If unspecified, at most 50 subscriptions will be returned.
        #   The maximum value is 1000; values above 1000 will be coerced to 1000.
        # @param [String] page_token
        #   A page token, received from a previous `ListSubscriptions` call. Provide this
        #   to retrieve the subsequent page. When paginating, all other parameters
        #   provided to `ListSubscriptions` must match the call that provided the page
        #   token.
        # @param [Boolean] show_archived
        #   Whether archived subscriptions should be included in the response. Defaults to
        #   false.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ListSubscriptionsResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ListSubscriptionsResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_monetization_subscriptions(package_name, page_size: nil, page_token: nil, show_archived: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/subscriptions', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::ListSubscriptionsResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ListSubscriptionsResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.query['pageSize'] = page_size unless page_size.nil?
          command.query['pageToken'] = page_token unless page_token.nil?
          command.query['showArchived'] = show_archived unless show_archived.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Updates an existing subscription.
        # @param [String] package_name
        #   Immutable. Package name of the parent app.
        # @param [String] product_id
        #   Immutable. Unique product ID of the product. Unique within the parent app.
        #   Product IDs must be composed of lower-case letters (a-z), numbers (0-9),
        #   underscores (_) and dots (.). It must start with a lower-case letter or number,
        #   and be between 1 and 40 (inclusive) characters in length.
        # @param [Google::Apis::AndroidpublisherV3::Subscription] subscription_object
        # @param [String] regions_version_version
        #   Required. A string representing the version of available regions being used
        #   for the specified resource. Regional prices for the resource have to be
        #   specified according to the information published in [this article](https://
        #   support.google.com/googleplay/android-developer/answer/10532353). Each time
        #   the supported locations substantially change, the version will be incremented.
        #   Using this field will ensure that creating and updating the resource with an
        #   older region's version and set of regional prices and currencies will succeed
        #   even though a new version is available. The latest version is 2022/02.
        # @param [String] update_mask
        #   Required. The list of fields to be updated.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Subscription] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Subscription]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def patch_monetization_subscription(package_name, product_id, subscription_object = nil, regions_version_version: nil, update_mask: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::Subscription::Representation
          command.request_object = subscription_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Subscription::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Subscription
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.query['regionsVersion.version'] = regions_version_version unless regions_version_version.nil?
          command.query['updateMask'] = update_mask unless update_mask.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Activates a base plan. Once activated, base plans will be available to new
        # subscribers.
        # @param [String] package_name
        #   Required. The parent app (package name) of the base plan to activate.
        # @param [String] product_id
        #   Required. The parent subscription (ID) of the base plan to activate.
        # @param [String] base_plan_id
        #   Required. The unique base plan ID of the base plan to activate.
        # @param [Google::Apis::AndroidpublisherV3::ActivateBasePlanRequest] activate_base_plan_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Subscription] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Subscription]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def activate_base_plan(package_name, product_id, base_plan_id, activate_base_plan_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}/basePlans/{basePlanId}:activate', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::ActivateBasePlanRequest::Representation
          command.request_object = activate_base_plan_request_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Subscription::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Subscription
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['basePlanId'] = base_plan_id unless base_plan_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deactivates a base plan. Once deactivated, the base plan will become
        # unavailable to new subscribers, but existing subscribers will maintain their
        # subscription
        # @param [String] package_name
        #   Required. The parent app (package name) of the base plan to deactivate.
        # @param [String] product_id
        #   Required. The parent subscription (ID) of the base plan to deactivate.
        # @param [String] base_plan_id
        #   Required. The unique base plan ID of the base plan to deactivate.
        # @param [Google::Apis::AndroidpublisherV3::DeactivateBasePlanRequest] deactivate_base_plan_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Subscription] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Subscription]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def deactivate_base_plan(package_name, product_id, base_plan_id, deactivate_base_plan_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}/basePlans/{basePlanId}:deactivate', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::DeactivateBasePlanRequest::Representation
          command.request_object = deactivate_base_plan_request_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Subscription::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Subscription
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['basePlanId'] = base_plan_id unless base_plan_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deletes a base plan. Can only be done for draft base plans. This action is
        # irreversible.
        # @param [String] package_name
        #   Required. The parent app (package name) of the base plan to delete.
        # @param [String] product_id
        #   Required. The parent subscription (ID) of the base plan to delete.
        # @param [String] base_plan_id
        #   Required. The unique offer ID of the base plan to delete.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def delete_monetization_subscription_base_plan(package_name, product_id, base_plan_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}/basePlans/{basePlanId}', options)
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['basePlanId'] = base_plan_id unless base_plan_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Migrates subscribers who are receiving an historical subscription price to the
        # currently-offered price for the specified region. Requests will cause price
        # change notifications to be sent to users who are currently receiving an
        # historical price older than the supplied timestamp. Subscribers who do not
        # agree to the new price will have their subscription ended at the next renewal.
        # @param [String] package_name
        #   Required. Package name of the parent app. Must be equal to the package_name
        #   field on the Subscription resource.
        # @param [String] product_id
        #   Required. The ID of the subscription to update. Must be equal to the
        #   product_id field on the Subscription resource.
        # @param [String] base_plan_id
        #   Required. The unique base plan ID of the base plan to update prices on.
        # @param [Google::Apis::AndroidpublisherV3::MigrateBasePlanPricesRequest] migrate_base_plan_prices_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::MigrateBasePlanPricesResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::MigrateBasePlanPricesResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def migrate_monetization_subscription_base_plan_prices(package_name, product_id, base_plan_id, migrate_base_plan_prices_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}/basePlans/{basePlanId}:migratePrices', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::MigrateBasePlanPricesRequest::Representation
          command.request_object = migrate_base_plan_prices_request_object
          command.response_representation = Google::Apis::AndroidpublisherV3::MigrateBasePlanPricesResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::MigrateBasePlanPricesResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['basePlanId'] = base_plan_id unless base_plan_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Activates a subscription offer. Once activated, subscription offers will be
        # available to new subscribers.
        # @param [String] package_name
        #   Required. The parent app (package name) of the offer to activate.
        # @param [String] product_id
        #   Required. The parent subscription (ID) of the offer to activate.
        # @param [String] base_plan_id
        #   Required. The parent base plan (ID) of the offer to activate.
        # @param [String] offer_id
        #   Required. The unique offer ID of the offer to activate.
        # @param [Google::Apis::AndroidpublisherV3::ActivateSubscriptionOfferRequest] activate_subscription_offer_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::SubscriptionOffer] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionOffer]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def activate_subscription_offer(package_name, product_id, base_plan_id, offer_id, activate_subscription_offer_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}/basePlans/{basePlanId}/offers/{offerId}:activate', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::ActivateSubscriptionOfferRequest::Representation
          command.request_object = activate_subscription_offer_request_object
          command.response_representation = Google::Apis::AndroidpublisherV3::SubscriptionOffer::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::SubscriptionOffer
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['basePlanId'] = base_plan_id unless base_plan_id.nil?
          command.params['offerId'] = offer_id unless offer_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Creates a new subscription offer. Only auto-renewing base plans can have
        # subscription offers. The offer state will be DRAFT until it is activated.
        # @param [String] package_name
        #   Required. The parent app (package name) for which the offer should be created.
        #   Must be equal to the package_name field on the Subscription resource.
        # @param [String] product_id
        #   Required. The parent subscription (ID) for which the offer should be created.
        #   Must be equal to the product_id field on the SubscriptionOffer resource.
        # @param [String] base_plan_id
        #   Required. The parent base plan (ID) for which the offer should be created.
        #   Must be equal to the base_plan_id field on the SubscriptionOffer resource.
        # @param [Google::Apis::AndroidpublisherV3::SubscriptionOffer] subscription_offer_object
        # @param [String] offer_id
        #   Required. The ID to use for the offer. For the requirements on this format,
        #   see the documentation of the offer_id field on the SubscriptionOffer resource.
        # @param [String] regions_version_version
        #   Required. A string representing the version of available regions being used
        #   for the specified resource. Regional prices for the resource have to be
        #   specified according to the information published in [this article](https://
        #   support.google.com/googleplay/android-developer/answer/10532353). Each time
        #   the supported locations substantially change, the version will be incremented.
        #   Using this field will ensure that creating and updating the resource with an
        #   older region's version and set of regional prices and currencies will succeed
        #   even though a new version is available. The latest version is 2022/02.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::SubscriptionOffer] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionOffer]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def create_monetization_subscription_base_plan_offer(package_name, product_id, base_plan_id, subscription_offer_object = nil, offer_id: nil, regions_version_version: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}/basePlans/{basePlanId}/offers', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::SubscriptionOffer::Representation
          command.request_object = subscription_offer_object
          command.response_representation = Google::Apis::AndroidpublisherV3::SubscriptionOffer::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::SubscriptionOffer
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['basePlanId'] = base_plan_id unless base_plan_id.nil?
          command.query['offerId'] = offer_id unless offer_id.nil?
          command.query['regionsVersion.version'] = regions_version_version unless regions_version_version.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deactivates a subscription offer. Once deactivated, existing subscribers will
        # maintain their subscription, but the offer will become unavailable to new
        # subscribers.
        # @param [String] package_name
        #   Required. The parent app (package name) of the offer to deactivate.
        # @param [String] product_id
        #   Required. The parent subscription (ID) of the offer to deactivate.
        # @param [String] base_plan_id
        #   Required. The parent base plan (ID) of the offer to deactivate.
        # @param [String] offer_id
        #   Required. The unique offer ID of the offer to deactivate.
        # @param [Google::Apis::AndroidpublisherV3::DeactivateSubscriptionOfferRequest] deactivate_subscription_offer_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::SubscriptionOffer] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionOffer]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def deactivate_subscription_offer(package_name, product_id, base_plan_id, offer_id, deactivate_subscription_offer_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}/basePlans/{basePlanId}/offers/{offerId}:deactivate', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::DeactivateSubscriptionOfferRequest::Representation
          command.request_object = deactivate_subscription_offer_request_object
          command.response_representation = Google::Apis::AndroidpublisherV3::SubscriptionOffer::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::SubscriptionOffer
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['basePlanId'] = base_plan_id unless base_plan_id.nil?
          command.params['offerId'] = offer_id unless offer_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deletes a subscription offer. Can only be done for draft offers. This action
        # is irreversible.
        # @param [String] package_name
        #   Required. The parent app (package name) of the offer to delete.
        # @param [String] product_id
        #   Required. The parent subscription (ID) of the offer to delete.
        # @param [String] base_plan_id
        #   Required. The parent base plan (ID) of the offer to delete.
        # @param [String] offer_id
        #   Required. The unique offer ID of the offer to delete.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def delete_monetization_subscription_base_plan_offer(package_name, product_id, base_plan_id, offer_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}/basePlans/{basePlanId}/offers/{offerId}', options)
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['basePlanId'] = base_plan_id unless base_plan_id.nil?
          command.params['offerId'] = offer_id unless offer_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Reads a single offer
        # @param [String] package_name
        #   Required. The parent app (package name) of the offer to get.
        # @param [String] product_id
        #   Required. The parent subscription (ID) of the offer to get.
        # @param [String] base_plan_id
        #   Required. The parent base plan (ID) of the offer to get.
        # @param [String] offer_id
        #   Required. The unique offer ID of the offer to get.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::SubscriptionOffer] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionOffer]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_monetization_subscription_base_plan_offer(package_name, product_id, base_plan_id, offer_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}/basePlans/{basePlanId}/offers/{offerId}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::SubscriptionOffer::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::SubscriptionOffer
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['basePlanId'] = base_plan_id unless base_plan_id.nil?
          command.params['offerId'] = offer_id unless offer_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists all offers under a given subscription.
        # @param [String] package_name
        #   Required. The parent app (package name) for which the subscriptions should be
        #   read.
        # @param [String] product_id
        #   Required. The parent subscription (ID) for which the offers should be read.
        #   May be specified as '-' to read all offers under an app.
        # @param [String] base_plan_id
        #   Required. The parent base plan (ID) for which the offers should be read. May
        #   be specified as '-' to read all offers under a subscription or an app. Must be
        #   specified as '-' if product_id is specified as '-'.
        # @param [Fixnum] page_size
        #   The maximum number of subscriptions to return. The service may return fewer
        #   than this value. If unspecified, at most 50 subscriptions will be returned.
        #   The maximum value is 1000; values above 1000 will be coerced to 1000.
        # @param [String] page_token
        #   A page token, received from a previous `ListSubscriptionsOffers` call. Provide
        #   this to retrieve the subsequent page. When paginating, all other parameters
        #   provided to `ListSubscriptionOffers` must match the call that provided the
        #   page token.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ListSubscriptionOffersResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ListSubscriptionOffersResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_monetization_subscription_base_plan_offers(package_name, product_id, base_plan_id, page_size: nil, page_token: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}/basePlans/{basePlanId}/offers', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::ListSubscriptionOffersResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ListSubscriptionOffersResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['basePlanId'] = base_plan_id unless base_plan_id.nil?
          command.query['pageSize'] = page_size unless page_size.nil?
          command.query['pageToken'] = page_token unless page_token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Updates an existing subscription offer.
        # @param [String] package_name
        #   Required. Immutable. The package name of the app the parent subscription
        #   belongs to.
        # @param [String] product_id
        #   Required. Immutable. The ID of the parent subscription this offer belongs to.
        # @param [String] base_plan_id
        #   Required. Immutable. The ID of the base plan to which this offer is an
        #   extension.
        # @param [String] offer_id
        #   Required. Immutable. Unique ID of this subscription offer. Must be unique
        #   within the base plan.
        # @param [Google::Apis::AndroidpublisherV3::SubscriptionOffer] subscription_offer_object
        # @param [String] regions_version_version
        #   Required. A string representing the version of available regions being used
        #   for the specified resource. Regional prices for the resource have to be
        #   specified according to the information published in [this article](https://
        #   support.google.com/googleplay/android-developer/answer/10532353). Each time
        #   the supported locations substantially change, the version will be incremented.
        #   Using this field will ensure that creating and updating the resource with an
        #   older region's version and set of regional prices and currencies will succeed
        #   even though a new version is available. The latest version is 2022/02.
        # @param [String] update_mask
        #   Required. The list of fields to be updated.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::SubscriptionOffer] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionOffer]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def patch_monetization_subscription_base_plan_offer(package_name, product_id, base_plan_id, offer_id, subscription_offer_object = nil, regions_version_version: nil, update_mask: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'androidpublisher/v3/applications/{packageName}/subscriptions/{productId}/basePlans/{basePlanId}/offers/{offerId}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::SubscriptionOffer::Representation
          command.request_object = subscription_offer_object
          command.response_representation = Google::Apis::AndroidpublisherV3::SubscriptionOffer::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::SubscriptionOffer
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['basePlanId'] = base_plan_id unless base_plan_id.nil?
          command.params['offerId'] = offer_id unless offer_id.nil?
          command.query['regionsVersion.version'] = regions_version_version unless regions_version_version.nil?
          command.query['updateMask'] = update_mask unless update_mask.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Refunds a user's subscription or in-app purchase order. Orders older than 3
        # years cannot be refunded.
        # @param [String] package_name
        #   The package name of the application for which this subscription or in-app item
        #   was purchased (for example, 'com.some.thing').
        # @param [String] order_id
        #   The order ID provided to the user when the subscription or in-app order was
        #   purchased.
        # @param [Boolean] revoke
        #   Whether to revoke the purchased item. If set to true, access to the
        #   subscription or in-app item will be terminated immediately. If the item is a
        #   recurring subscription, all future payments will also be terminated. Consumed
        #   in-app items need to be handled by developer's app. (optional).
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def refund_order(package_name, order_id, revoke: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/orders/{orderId}:refund', options)
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['orderId'] = order_id unless order_id.nil?
          command.query['revoke'] = revoke unless revoke.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Acknowledges a purchase of an inapp item.
        # @param [String] package_name
        #   The package name of the application the inapp product was sold in (for example,
        #   'com.some.thing').
        # @param [String] product_id
        #   The inapp product SKU (for example, 'com.some.thing.inapp1').
        # @param [String] token
        #   The token provided to the user's device when the inapp product was purchased.
        # @param [Google::Apis::AndroidpublisherV3::ProductPurchasesAcknowledgeRequest] product_purchases_acknowledge_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def acknowledge_purchase_product(package_name, product_id, token, product_purchases_acknowledge_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/purchases/products/{productId}/tokens/{token}:acknowledge', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::ProductPurchasesAcknowledgeRequest::Representation
          command.request_object = product_purchases_acknowledge_request_object
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['token'] = token unless token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Consumes a purchase for an inapp item.
        # @param [String] package_name
        #   The package name of the application the inapp product was sold in (for example,
        #   'com.some.thing').
        # @param [String] product_id
        #   The inapp product SKU (for example, 'com.some.thing.inapp1').
        # @param [String] token
        #   The token provided to the user's device when the inapp product was purchased.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def consume_purchase_product(package_name, product_id, token, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/purchases/products/{productId}/tokens/{token}:consume', options)
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['token'] = token unless token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Checks the purchase and consumption status of an inapp item.
        # @param [String] package_name
        #   The package name of the application the inapp product was sold in (for example,
        #   'com.some.thing').
        # @param [String] product_id
        #   The inapp product SKU (for example, 'com.some.thing.inapp1').
        # @param [String] token
        #   The token provided to the user's device when the inapp product was purchased.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ProductPurchase] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ProductPurchase]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_purchase_product(package_name, product_id, token, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/purchases/products/{productId}/tokens/{token}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::ProductPurchase::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ProductPurchase
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['productId'] = product_id unless product_id.nil?
          command.params['token'] = token unless token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Acknowledges a subscription purchase.
        # @param [String] package_name
        #   The package name of the application for which this subscription was purchased (
        #   for example, 'com.some.thing').
        # @param [String] subscription_id
        #   The purchased subscription ID (for example, 'monthly001').
        # @param [String] token
        #   The token provided to the user's device when the subscription was purchased.
        # @param [Google::Apis::AndroidpublisherV3::SubscriptionPurchasesAcknowledgeRequest] subscription_purchases_acknowledge_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def acknowledge_purchase_subscription(package_name, subscription_id, token, subscription_purchases_acknowledge_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/purchases/subscriptions/{subscriptionId}/tokens/{token}:acknowledge', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::SubscriptionPurchasesAcknowledgeRequest::Representation
          command.request_object = subscription_purchases_acknowledge_request_object
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['subscriptionId'] = subscription_id unless subscription_id.nil?
          command.params['token'] = token unless token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Cancels a user's subscription purchase. The subscription remains valid until
        # its expiration time.
        # @param [String] package_name
        #   The package name of the application for which this subscription was purchased (
        #   for example, 'com.some.thing').
        # @param [String] subscription_id
        #   The purchased subscription ID (for example, 'monthly001').
        # @param [String] token
        #   The token provided to the user's device when the subscription was purchased.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def cancel_purchase_subscription(package_name, subscription_id, token, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/purchases/subscriptions/{subscriptionId}/tokens/{token}:cancel', options)
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['subscriptionId'] = subscription_id unless subscription_id.nil?
          command.params['token'] = token unless token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Defers a user's subscription purchase until a specified future expiration time.
        # @param [String] package_name
        #   The package name of the application for which this subscription was purchased (
        #   for example, 'com.some.thing').
        # @param [String] subscription_id
        #   The purchased subscription ID (for example, 'monthly001').
        # @param [String] token
        #   The token provided to the user's device when the subscription was purchased.
        # @param [Google::Apis::AndroidpublisherV3::SubscriptionPurchasesDeferRequest] subscription_purchases_defer_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::SubscriptionPurchasesDeferResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionPurchasesDeferResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def defer_purchase_subscription(package_name, subscription_id, token, subscription_purchases_defer_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/purchases/subscriptions/{subscriptionId}/tokens/{token}:defer', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::SubscriptionPurchasesDeferRequest::Representation
          command.request_object = subscription_purchases_defer_request_object
          command.response_representation = Google::Apis::AndroidpublisherV3::SubscriptionPurchasesDeferResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::SubscriptionPurchasesDeferResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['subscriptionId'] = subscription_id unless subscription_id.nil?
          command.params['token'] = token unless token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Checks whether a user's subscription purchase is valid and returns its expiry
        # time.
        # @param [String] package_name
        #   The package name of the application for which this subscription was purchased (
        #   for example, 'com.some.thing').
        # @param [String] subscription_id
        #   The purchased subscription ID (for example, 'monthly001').
        # @param [String] token
        #   The token provided to the user's device when the subscription was purchased.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::SubscriptionPurchase] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionPurchase]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_purchase_subscription(package_name, subscription_id, token, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/purchases/subscriptions/{subscriptionId}/tokens/{token}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::SubscriptionPurchase::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::SubscriptionPurchase
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['subscriptionId'] = subscription_id unless subscription_id.nil?
          command.params['token'] = token unless token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Refunds a user's subscription purchase, but the subscription remains valid
        # until its expiration time and it will continue to recur.
        # @param [String] package_name
        #   The package name of the application for which this subscription was purchased (
        #   for example, 'com.some.thing').
        # @param [String] subscription_id
        #   "The purchased subscription ID (for example, 'monthly001').
        # @param [String] token
        #   The token provided to the user's device when the subscription was purchased.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def refund_purchase_subscription(package_name, subscription_id, token, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/purchases/subscriptions/{subscriptionId}/tokens/{token}:refund', options)
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['subscriptionId'] = subscription_id unless subscription_id.nil?
          command.params['token'] = token unless token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Refunds and immediately revokes a user's subscription purchase. Access to the
        # subscription will be terminated immediately and it will stop recurring.
        # @param [String] package_name
        #   The package name of the application for which this subscription was purchased (
        #   for example, 'com.some.thing').
        # @param [String] subscription_id
        #   The purchased subscription ID (for example, 'monthly001').
        # @param [String] token
        #   The token provided to the user's device when the subscription was purchased.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def revoke_purchase_subscription(package_name, subscription_id, token, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/purchases/subscriptions/{subscriptionId}/tokens/{token}:revoke', options)
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['subscriptionId'] = subscription_id unless subscription_id.nil?
          command.params['token'] = token unless token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Get metadata about a subscription
        # @param [String] package_name
        #   The package of the application for which this subscription was purchased (for
        #   example, 'com.some.thing').
        # @param [String] token
        #   Required. The token provided to the user's device when the subscription was
        #   purchased.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::SubscriptionPurchaseV2] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionPurchaseV2]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_purchase_subscriptionsv2(package_name, token, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/purchases/subscriptionsv2/tokens/{token}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::SubscriptionPurchaseV2::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::SubscriptionPurchaseV2
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['token'] = token unless token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists the purchases that were canceled, refunded or charged-back.
        # @param [String] package_name
        #   The package name of the application for which voided purchases need to be
        #   returned (for example, 'com.some.thing').
        # @param [Fixnum] end_time
        #   The time, in milliseconds since the Epoch, of the newest voided purchase that
        #   you want to see in the response. The value of this parameter cannot be greater
        #   than the current time and is ignored if a pagination token is set. Default
        #   value is current time. Note: This filter is applied on the time at which the
        #   record is seen as voided by our systems and not the actual voided time
        #   returned in the response.
        # @param [Fixnum] max_results
        #   Defines how many results the list operation should return. The default number
        #   depends on the resource collection.
        # @param [Fixnum] start_index
        #   Defines the index of the first element to return. This can only be used if
        #   indexed paging is enabled.
        # @param [Fixnum] start_time
        #   The time, in milliseconds since the Epoch, of the oldest voided purchase that
        #   you want to see in the response. The value of this parameter cannot be older
        #   than 30 days and is ignored if a pagination token is set. Default value is
        #   current time minus 30 days. Note: This filter is applied on the time at which
        #   the record is seen as voided by our systems and not the actual voided time
        #   returned in the response.
        # @param [String] token
        #   Defines the token of the page to return, usually taken from TokenPagination.
        #   This can only be used if token paging is enabled.
        # @param [Fixnum] type
        #   The type of voided purchases that you want to see in the response. Possible
        #   values are: 0. Only voided in-app product purchases will be returned in the
        #   response. This is the default value. 1. Both voided in-app purchases and
        #   voided subscription purchases will be returned in the response. Note: Before
        #   requesting to receive voided subscription purchases, you must switch to use
        #   orderId in the response which uniquely identifies one-time purchases and
        #   subscriptions. Otherwise, you will receive multiple subscription orders with
        #   the same PurchaseToken, because subscription renewal orders share the same
        #   PurchaseToken.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::VoidedPurchasesListResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::VoidedPurchasesListResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_purchase_voidedpurchases(package_name, end_time: nil, max_results: nil, start_index: nil, start_time: nil, token: nil, type: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/purchases/voidedpurchases', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::VoidedPurchasesListResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::VoidedPurchasesListResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.query['endTime'] = end_time unless end_time.nil?
          command.query['maxResults'] = max_results unless max_results.nil?
          command.query['startIndex'] = start_index unless start_index.nil?
          command.query['startTime'] = start_time unless start_time.nil?
          command.query['token'] = token unless token.nil?
          command.query['type'] = type unless type.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets a single review.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] review_id
        #   Unique identifier for a review.
        # @param [String] translation_language
        #   Language localization code.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Review] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Review]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_review(package_name, review_id, translation_language: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/reviews/{reviewId}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::Review::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Review
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['reviewId'] = review_id unless review_id.nil?
          command.query['translationLanguage'] = translation_language unless translation_language.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists all reviews.
        # @param [String] package_name
        #   Package name of the app.
        # @param [Fixnum] max_results
        #   How many results the list operation should return.
        # @param [Fixnum] start_index
        #   The index of the first element to return.
        # @param [String] token
        #   Pagination token. If empty, list starts at the first review.
        # @param [String] translation_language
        #   Language localization code.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ReviewsListResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ReviewsListResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_reviews(package_name, max_results: nil, start_index: nil, token: nil, translation_language: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/reviews', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::ReviewsListResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ReviewsListResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.query['maxResults'] = max_results unless max_results.nil?
          command.query['startIndex'] = start_index unless start_index.nil?
          command.query['token'] = token unless token.nil?
          command.query['translationLanguage'] = translation_language unless translation_language.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Replies to a single review, or updates an existing reply.
        # @param [String] package_name
        #   Package name of the app.
        # @param [String] review_id
        #   Unique identifier for a review.
        # @param [Google::Apis::AndroidpublisherV3::ReviewsReplyRequest] reviews_reply_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ReviewsReplyResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ReviewsReplyResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def reply_review(package_name, review_id, reviews_reply_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/reviews/{reviewId}:reply', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::ReviewsReplyRequest::Representation
          command.request_object = reviews_reply_request_object
          command.response_representation = Google::Apis::AndroidpublisherV3::ReviewsReplyResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ReviewsReplyResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['reviewId'] = review_id unless review_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Creates an APK which is suitable for inclusion in a system image from an
        # already uploaded Android App Bundle.
        # @param [String] package_name
        #   Package name of the app.
        # @param [Fixnum] version_code
        #   The version code of the App Bundle.
        # @param [Google::Apis::AndroidpublisherV3::Variant] variant_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Variant] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Variant]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def create_systemapk_variant(package_name, version_code, variant_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/applications/{packageName}/systemApks/{versionCode}/variants', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::Variant::Representation
          command.request_object = variant_object
          command.response_representation = Google::Apis::AndroidpublisherV3::Variant::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Variant
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['versionCode'] = version_code unless version_code.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Downloads a previously created system APK which is suitable for inclusion in a
        # system image.
        # @param [String] package_name
        #   Package name of the app.
        # @param [Fixnum] version_code
        #   The version code of the App Bundle.
        # @param [Fixnum] variant_id
        #   The ID of a previously created system APK variant.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [IO, String] download_dest
        #   IO stream or filename to receive content download
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def download_systemapk_variant(package_name, version_code, variant_id, fields: nil, quota_user: nil, download_dest: nil, options: nil, &block)
          if download_dest.nil?
            command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/systemApks/{versionCode}/variants/{variantId}:download', options)
          else
            command = make_download_command(:get, 'androidpublisher/v3/applications/{packageName}/systemApks/{versionCode}/variants/{variantId}:download', options)
            command.download_dest = download_dest
          end
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['versionCode'] = version_code unless version_code.nil?
          command.params['variantId'] = variant_id unless variant_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Returns a previously created system APK variant.
        # @param [String] package_name
        #   Package name of the app.
        # @param [Fixnum] version_code
        #   The version code of the App Bundle.
        # @param [Fixnum] variant_id
        #   The ID of a previously created system APK variant.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::Variant] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::Variant]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_systemapk_variant(package_name, version_code, variant_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/systemApks/{versionCode}/variants/{variantId}', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::Variant::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::Variant
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['versionCode'] = version_code unless version_code.nil?
          command.params['variantId'] = variant_id unless variant_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Returns the list of previously created system APK variants.
        # @param [String] package_name
        #   Package name of the app.
        # @param [Fixnum] version_code
        #   The version code of the App Bundle.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::SystemApksListResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::SystemApksListResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_systemapk_variants(package_name, version_code, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/applications/{packageName}/systemApks/{versionCode}/variants', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::SystemApksListResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::SystemApksListResponse
          command.params['packageName'] = package_name unless package_name.nil?
          command.params['versionCode'] = version_code unless version_code.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Grant access for a user to the given developer account.
        # @param [String] parent
        #   Required. The developer account to add the user to. Format: developers/`
        #   developer`
        # @param [Google::Apis::AndroidpublisherV3::User] user_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::User] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::User]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def create_user(parent, user_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'androidpublisher/v3/{+parent}/users', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::User::Representation
          command.request_object = user_object
          command.response_representation = Google::Apis::AndroidpublisherV3::User::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::User
          command.params['parent'] = parent unless parent.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Removes all access for the user to the given developer account.
        # @param [String] name
        #   Required. The name of the user to delete. Format: developers/`developer`/users/
        #   `email`
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [NilClass] No result returned for this method
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [void]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def delete_user(name, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'androidpublisher/v3/{+name}', options)
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists all users with access to a developer account.
        # @param [String] parent
        #   Required. The developer account to fetch users from. Format: developers/`
        #   developer`
        # @param [Fixnum] page_size
        #   The maximum number of results to return. This must be set to -1 to disable
        #   pagination.
        # @param [String] page_token
        #   A token received from a previous call to this method, in order to retrieve
        #   further results.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::ListUsersResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::ListUsersResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_users(parent, page_size: nil, page_token: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'androidpublisher/v3/{+parent}/users', options)
          command.response_representation = Google::Apis::AndroidpublisherV3::ListUsersResponse::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::ListUsersResponse
          command.params['parent'] = parent unless parent.nil?
          command.query['pageSize'] = page_size unless page_size.nil?
          command.query['pageToken'] = page_token unless page_token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Updates access for the user to the developer account.
        # @param [String] name
        #   Required. Resource name for this user, following the pattern "developers/`
        #   developer`/users/`email`".
        # @param [Google::Apis::AndroidpublisherV3::User] user_object
        # @param [String] update_mask
        #   Optional. The list of fields to be updated.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::AndroidpublisherV3::User] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::AndroidpublisherV3::User]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def patch_user(name, user_object = nil, update_mask: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'androidpublisher/v3/{+name}', options)
          command.request_representation = Google::Apis::AndroidpublisherV3::User::Representation
          command.request_object = user_object
          command.response_representation = Google::Apis::AndroidpublisherV3::User::Representation
          command.response_class = Google::Apis::AndroidpublisherV3::User
          command.params['name'] = name unless name.nil?
          command.query['updateMask'] = update_mask unless update_mask.nil?
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
