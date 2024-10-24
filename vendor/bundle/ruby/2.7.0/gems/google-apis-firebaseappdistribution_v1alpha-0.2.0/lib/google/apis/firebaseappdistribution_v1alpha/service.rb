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
    module FirebaseappdistributionV1alpha
      # Firebase App Distribution API
      #
      # 
      #
      # @example
      #    require 'google/apis/firebaseappdistribution_v1alpha'
      #
      #    Firebaseappdistribution = Google::Apis::FirebaseappdistributionV1alpha # Alias the module
      #    service = Firebaseappdistribution::FirebaseAppDistributionService.new
      #
      # @see https://firebase.google.com/products/app-distribution
      class FirebaseAppDistributionService < Google::Apis::Core::BaseService
        # @return [String]
        #  API key. Your API key identifies your project and provides you with API access,
        #  quota, and reports. Required unless you provide an OAuth 2.0 token.
        attr_accessor :key

        # @return [String]
        #  Available to use for quota purposes for server-side applications. Can be any
        #  arbitrary string assigned to a user, but should not exceed 40 characters.
        attr_accessor :quota_user

        def initialize
          super('https://firebaseappdistribution.googleapis.com/', '',
                client_name: 'google-apis-firebaseappdistribution_v1alpha',
                client_version: Google::Apis::FirebaseappdistributionV1alpha::GEM_VERSION)
          @batch_path = 'batch'
        end
        
        # Get the app, if it exists
        # @param [String] mobilesdk_app_id
        #   Unique id for a Firebase app of the format: `version`:`project_number`:`
        #   platform`:`hash(bundle_id)` Example: 1:581234567376:android:aa0a3c7b135e90289
        # @param [String] app_view
        #   App view. When unset or set to BASIC, returns an App with everything set
        #   except for aab_state. When set to FULL, returns an App with aab_state set.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaApp] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaApp]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_app(mobilesdk_app_id, app_view: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1alpha/apps/{mobilesdkAppId}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaApp::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaApp
          command.params['mobilesdkAppId'] = mobilesdk_app_id unless mobilesdk_app_id.nil?
          command.query['appView'] = app_view unless app_view.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Get a JWT token
        # @param [String] mobilesdk_app_id
        #   Unique id for a Firebase app of the format: `version`:`project_number`:`
        #   platform`:`hash(bundle_id)` Example: 1:581234567376:android:aa0a3c7b135e90289
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaJwt] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaJwt]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_app_jwt(mobilesdk_app_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1alpha/apps/{mobilesdkAppId}/jwt', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaJwt::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaJwt
          command.params['mobilesdkAppId'] = mobilesdk_app_id unless mobilesdk_app_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Provision app distribution for an existing Firebase app, enabling it to
        # subsequently be used by appdistro.
        # @param [String] mobilesdk_app_id
        #   Unique id for a Firebase app of the format: `version`:`project_number`:`
        #   platform`:`hash(bundle_id)` Example: 1:581234567376:android:aa0a3c7b135e90289
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaProvisionAppResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaProvisionAppResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def provision_app_app(mobilesdk_app_id, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'v1alpha/apps/{mobilesdkAppId}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaProvisionAppResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaProvisionAppResponse
          command.params['mobilesdkAppId'] = mobilesdk_app_id unless mobilesdk_app_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # GET Release by binary upload hash
        # @param [String] mobilesdk_app_id
        #   Unique id for a Firebase app of the format: `version`:`project_number`:`
        #   platform`:`hash(bundle_id)` Example: 1:581234567376:android:aa0a3c7b135e90289
        # @param [String] upload_hash
        #   The hash for the upload
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetReleaseByUploadHashResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetReleaseByUploadHashResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_app_release_by_hash(mobilesdk_app_id, upload_hash, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1alpha/apps/{mobilesdkAppId}/release_by_hash/{uploadHash}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetReleaseByUploadHashResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetReleaseByUploadHashResponse
          command.params['mobilesdkAppId'] = mobilesdk_app_id unless mobilesdk_app_id.nil?
          command.params['uploadHash'] = upload_hash unless upload_hash.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Enable access on a release for testers.
        # @param [String] mobilesdk_app_id
        #   Unique id for a Firebase app of the format: `version`:`project_number`:`
        #   platform`:`hash(bundle_id)` Example: 1:581234567376:android:aa0a3c7b135e90289
        # @param [String] release_id
        #   Release identifier
        # @param [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaEnableAccessOnReleaseRequest] google_firebase_appdistro_v1alpha_enable_access_on_release_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaEnableAccessOnReleaseResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaEnableAccessOnReleaseResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def enable_app_release_access(mobilesdk_app_id, release_id, google_firebase_appdistro_v1alpha_enable_access_on_release_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'v1alpha/apps/{mobilesdkAppId}/releases/{releaseId}/enable_access', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaEnableAccessOnReleaseRequest::Representation
          command.request_object = google_firebase_appdistro_v1alpha_enable_access_on_release_request_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaEnableAccessOnReleaseResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaEnableAccessOnReleaseResponse
          command.params['mobilesdkAppId'] = mobilesdk_app_id unless mobilesdk_app_id.nil?
          command.params['releaseId'] = release_id unless release_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Create release notes on a release.
        # @param [String] mobilesdk_app_id
        #   Unique id for a Firebase app of the format: `version`:`project_number`:`
        #   platform`:`hash(bundle_id)` Example: 1:581234567376:android:aa0a3c7b135e90289
        # @param [String] release_id
        #   Release identifier
        # @param [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaCreateReleaseNotesRequest] google_firebase_appdistro_v1alpha_create_release_notes_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaCreateReleaseNotesResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaCreateReleaseNotesResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def create_app_release_note(mobilesdk_app_id, release_id, google_firebase_appdistro_v1alpha_create_release_notes_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'v1alpha/apps/{mobilesdkAppId}/releases/{releaseId}/notes', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaCreateReleaseNotesRequest::Representation
          command.request_object = google_firebase_appdistro_v1alpha_create_release_notes_request_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaCreateReleaseNotesResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaCreateReleaseNotesResponse
          command.params['mobilesdkAppId'] = mobilesdk_app_id unless mobilesdk_app_id.nil?
          command.params['releaseId'] = release_id unless release_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Get UDIDs of tester iOS devices in a project
        # @param [String] mobilesdk_app_id
        #   Unique id for a Firebase app of the format: `version`:`project_number`:`
        #   platform`:`hash(bundle_id)` Example: 1:581234567376:android:aa0a3c7b135e90289
        # @param [String] project
        #   The name of the project, which is the parent of testers Format: `projects/`
        #   project_number``
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetTesterUdidsResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetTesterUdidsResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_app_tester_tester_udids(mobilesdk_app_id, project: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1alpha/apps/{mobilesdkAppId}/testers:getTesterUdids', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetTesterUdidsResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetTesterUdidsResponse
          command.params['mobilesdkAppId'] = mobilesdk_app_id unless mobilesdk_app_id.nil?
          command.query['project'] = project unless project.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # GET Binary upload status by token
        # @param [String] mobilesdk_app_id
        #   Unique id for a Firebase app of the format: `version`:`project_number`:`
        #   platform`:`hash(bundle_id)` Example: 1:581234567376:android:aa0a3c7b135e90289
        # @param [String] upload_token
        #   The token for the upload
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetUploadStatusResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetUploadStatusResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_app_upload_status(mobilesdk_app_id, upload_token, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1alpha/apps/{mobilesdkAppId}/upload_status/{uploadToken}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetUploadStatusResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetUploadStatusResponse
          command.params['mobilesdkAppId'] = mobilesdk_app_id unless mobilesdk_app_id.nil?
          command.params['uploadToken'] = upload_token unless upload_token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets configuration for automated tests.
        # @param [String] name
        #   Required. The name of the `TestConfig` resource to retrieve. Format: `projects/
        #   `project_number`/apps/`app_id`/testConfig`
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestConfig] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestConfig]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_project_app_test_config(name, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1alpha/{+name}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestConfig::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestConfig
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Updates a release.
        # @param [String] name
        #   Identifier. The name of the test configuration resource. Format: `projects/`
        #   project_number`/apps/`app_id`/testConfig`
        # @param [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestConfig] google_firebase_appdistro_v1alpha_test_config_object
        # @param [String] update_mask
        #   Optional. The list of fields to update.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestConfig] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestConfig]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def update_project_app_test_config(name, google_firebase_appdistro_v1alpha_test_config_object = nil, update_mask: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'v1alpha/{+name}', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestConfig::Representation
          command.request_object = google_firebase_appdistro_v1alpha_test_config_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestConfig::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestConfig
          command.params['name'] = name unless name.nil?
          command.query['updateMask'] = update_mask unless update_mask.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Run automated test(s) on release.
        # @param [String] parent
        #   Required. The name of the release resource, which is the parent of the test
        #   Format: `projects/`project_number`/apps/`app_id`/releases/`release_id``
        # @param [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseTest] google_firebase_appdistro_v1alpha_release_test_object
        # @param [String] release_test_id
        #   Optional. The ID to use for the test, which will become the final component of
        #   the tests's resource name. This value should be 4-63 characters, and valid
        #   characters are /a-z-/. If it is not provided one will be automatically
        #   generated.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseTest] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseTest]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def create_project_app_release_test(parent, google_firebase_appdistro_v1alpha_release_test_object = nil, release_test_id: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'v1alpha/{+parent}/tests', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseTest::Representation
          command.request_object = google_firebase_appdistro_v1alpha_release_test_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseTest::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseTest
          command.params['parent'] = parent unless parent.nil?
          command.query['releaseTestId'] = release_test_id unless release_test_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Get results for automated test run on release.
        # @param [String] name
        #   Required. The name of the release test resource. Format: `projects/`
        #   project_number`/apps/`app_id`/releases/`release_id`/tests/`test_id``
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseTest] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseTest]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_project_app_release_test(name, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1alpha/{+name}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseTest::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseTest
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # List results for automated tests run on release.
        # @param [String] parent
        #   Required. The name of the release resource, which is the parent of the tests
        #   Format: `projects/`project_number`/apps/`app_id`/releases/`release_id``
        # @param [Fixnum] page_size
        #   Optional. The maximum number of tests to return. The service may return fewer
        #   than this value.
        # @param [String] page_token
        #   Optional. A page token, received from a previous `ListReleaseTests` call.
        #   Provide this to retrieve the subsequent page.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaListReleaseTestsResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaListReleaseTestsResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_project_app_release_tests(parent, page_size: nil, page_token: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1alpha/{+parent}/tests', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaListReleaseTestsResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaListReleaseTestsResponse
          command.params['parent'] = parent unless parent.nil?
          command.query['pageSize'] = page_size unless page_size.nil?
          command.query['pageToken'] = page_token unless page_token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Get UDIDs of tester iOS devices in a project
        # @param [String] project
        #   The name of the project, which is the parent of testers Format: `projects/`
        #   project_number``
        # @param [String] mobilesdk_app_id
        #   Unique id for a Firebase app of the format: `version`:`project_number`:`
        #   platform`:`hash(bundle_id)` Example: 1:581234567376:android:aa0a3c7b135e90289
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetTesterUdidsResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetTesterUdidsResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_project_tester_udids(project, mobilesdk_app_id: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1alpha/{+project}/testers:udids', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetTesterUdidsResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaGetTesterUdidsResponse
          command.params['project'] = project unless project.nil?
          command.query['mobilesdkAppId'] = mobilesdk_app_id unless mobilesdk_app_id.nil?
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
