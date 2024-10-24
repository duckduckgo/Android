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
    module FirebaseappdistributionV1
      # Firebase App Distribution API
      #
      # 
      #
      # @example
      #    require 'google/apis/firebaseappdistribution_v1'
      #
      #    Firebaseappdistribution = Google::Apis::FirebaseappdistributionV1 # Alias the module
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
                client_name: 'google-apis-firebaseappdistribution_v1',
                client_version: Google::Apis::FirebaseappdistributionV1::GEM_VERSION)
          @batch_path = 'batch'
        end
        
        # Uploads a binary. Uploading a binary can result in a new release being created,
        # an update to an existing release, or a no-op if a release with the same
        # binary already exists.
        # @param [String] app
        #   The name of the app resource. Format: `projects/`project_number`/apps/`app_id``
        # @param [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1UploadReleaseRequest] google_firebase_appdistro_v1_upload_release_request_object
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
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def upload_medium(app, google_firebase_appdistro_v1_upload_release_request_object = nil, fields: nil, quota_user: nil, upload_source: nil, content_type: nil, options: nil, &block)
          if upload_source.nil?
            command = make_simple_command(:post, 'v1/{+app}/releases:upload', options)
          else
            command = make_upload_command(:post, 'v1/{+app}/releases:upload', options)
            command.upload_source = upload_source
            command.upload_content_type = content_type
          end
          command.request_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1UploadReleaseRequest::Representation
          command.request_object = google_firebase_appdistro_v1_upload_release_request_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation
          command.params['app'] = app unless app.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets Android App Bundle (AAB) information for a Firebase app.
        # @param [String] name
        #   Required. The name of the `AabInfo` resource to retrieve. Format: `projects/`
        #   project_number`/apps/`app_id`/aabInfo`
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1AabInfo] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1AabInfo]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_project_app_aab_info(name, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1/{+name}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1AabInfo::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1AabInfo
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deletes releases. A maximum of 100 releases can be deleted per request.
        # @param [String] parent
        #   Required. The name of the app resource, which is the parent of the release
        #   resources. Format: `projects/`project_number`/apps/`app_id``
        # @param [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchDeleteReleasesRequest] google_firebase_appdistro_v1_batch_delete_releases_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def batch_project_app_release_delete(parent, google_firebase_appdistro_v1_batch_delete_releases_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'v1/{+parent}/releases:batchDelete', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchDeleteReleasesRequest::Representation
          command.request_object = google_firebase_appdistro_v1_batch_delete_releases_request_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty
          command.params['parent'] = parent unless parent.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Distributes a release to testers. This call does the following: 1. Creates
        # testers for the specified emails, if none exist. 2. Adds the testers and
        # groups to the release. 3. Sends new testers an invitation email. 4. Sends
        # existing testers a new release email. The request will fail with a `
        # INVALID_ARGUMENT` if it contains a group that doesn't exist.
        # @param [String] name
        #   Required. The name of the release resource to distribute. Format: `projects/`
        #   project_number`/apps/`app_id`/releases/`release_id``
        # @param [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1DistributeReleaseRequest] google_firebase_appdistro_v1_distribute_release_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1DistributeReleaseResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1DistributeReleaseResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def distribute_project_app_release(name, google_firebase_appdistro_v1_distribute_release_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'v1/{+name}:distribute', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1DistributeReleaseRequest::Representation
          command.request_object = google_firebase_appdistro_v1_distribute_release_request_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1DistributeReleaseResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1DistributeReleaseResponse
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets a release.
        # @param [String] name
        #   Required. The name of the release resource to retrieve. Format: projects/`
        #   project_number`/apps/`app_id`/releases/`release_id`
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_project_app_release(name, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1/{+name}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists releases. By default, sorts by `createTime` in descending order.
        # @param [String] parent
        #   Required. The name of the app resource, which is the parent of the release
        #   resources. Format: `projects/`project_number`/apps/`app_id``
        # @param [String] filter
        #   The expression to filter releases listed in the response. To learn more about
        #   filtering, refer to [Google's AIP-160 standard](http://aip.dev/160). Supported
        #   fields: - `releaseNotes.text` supports `=` (can contain a wildcard character (`
        #   *`) at the beginning or end of the string) - `createTime` supports `<`, `<=`, `
        #   >` and `>=`, and expects an RFC-3339 formatted string Examples: - `createTime <
        #   = "2021-09-08T00:00:00+04:00"` - `releaseNotes.text="fixes" AND createTime >= "
        #   2021-09-08T00:00:00.0Z"` - `releaseNotes.text="*v1.0.0-rc*"`
        # @param [String] order_by
        #   The fields used to order releases. Supported fields: - `createTime` To specify
        #   descending order for a field, append a "desc" suffix, for example, `createTime
        #   desc`. If this parameter is not set, releases are ordered by `createTime` in
        #   descending order.
        # @param [Fixnum] page_size
        #   The maximum number of releases to return. The service may return fewer than
        #   this value. The valid range is [1-100]; If unspecified (0), at most 25
        #   releases are returned. Values above 100 are coerced to 100.
        # @param [String] page_token
        #   A page token, received from a previous `ListReleases` call. Provide this to
        #   retrieve the subsequent page. When paginating, all other parameters provided
        #   to `ListReleases` must match the call that provided the page token.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListReleasesResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListReleasesResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_project_app_releases(parent, filter: nil, order_by: nil, page_size: nil, page_token: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1/{+parent}/releases', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListReleasesResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListReleasesResponse
          command.params['parent'] = parent unless parent.nil?
          command.query['filter'] = filter unless filter.nil?
          command.query['orderBy'] = order_by unless order_by.nil?
          command.query['pageSize'] = page_size unless page_size.nil?
          command.query['pageToken'] = page_token unless page_token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Updates a release.
        # @param [String] name
        #   The name of the release resource. Format: `projects/`project_number`/apps/`
        #   app_id`/releases/`release_id``
        # @param [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release] google_firebase_appdistro_v1_release_object
        # @param [String] update_mask
        #   The list of fields to update.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def patch_project_app_release(name, google_firebase_appdistro_v1_release_object = nil, update_mask: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'v1/{+name}', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release::Representation
          command.request_object = google_firebase_appdistro_v1_release_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release
          command.params['name'] = name unless name.nil?
          command.query['updateMask'] = update_mask unless update_mask.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deletes a feedback report.
        # @param [String] name
        #   Required. The name of the feedback report to delete. Format: projects/`
        #   project_number`/apps/`app`/releases/`release`/feedbackReports/`feedback_report`
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def delete_project_app_release_feedback_report(name, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'v1/{+name}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets a feedback report.
        # @param [String] name
        #   Required. The name of the feedback report to retrieve. Format: projects/`
        #   project_number`/apps/`app`/releases/`release`/feedbackReports/`feedback_report`
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1FeedbackReport] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1FeedbackReport]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_project_app_release_feedback_report(name, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1/{+name}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1FeedbackReport::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1FeedbackReport
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists feedback reports. By default, sorts by `createTime` in descending order.
        # @param [String] parent
        #   Required. The name of the release resource, which is the parent of the
        #   feedback report resources. Format: `projects/`project_number`/apps/`app`/
        #   releases/`release``
        # @param [Fixnum] page_size
        #   The maximum number of feedback reports to return. The service may return fewer
        #   than this value. The valid range is [1-100]; If unspecified (0), at most 25
        #   feedback reports are returned. Values above 100 are coerced to 100.
        # @param [String] page_token
        #   A page token, received from a previous `ListFeedbackReports` call. Provide
        #   this to retrieve the subsequent page. When paginating, all other parameters
        #   provided to `ListFeedbackReports` must match the call that provided the page
        #   token.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListFeedbackReportsResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListFeedbackReportsResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_project_app_release_feedback_reports(parent, page_size: nil, page_token: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1/{+parent}/feedbackReports', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListFeedbackReportsResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListFeedbackReportsResponse
          command.params['parent'] = parent unless parent.nil?
          command.query['pageSize'] = page_size unless page_size.nil?
          command.query['pageToken'] = page_token unless page_token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Starts asynchronous cancellation on a long-running operation. The server makes
        # a best effort to cancel the operation, but success is not guaranteed. If the
        # server doesn't support this method, it returns `google.rpc.Code.UNIMPLEMENTED`.
        # Clients can use Operations.GetOperation or other methods to check whether the
        # cancellation succeeded or whether the operation completed despite cancellation.
        # On successful cancellation, the operation is not deleted; instead, it becomes
        # an operation with an Operation.error value with a google.rpc.Status.code of 1,
        # corresponding to `Code.CANCELLED`.
        # @param [String] name
        #   The name of the operation resource to be cancelled.
        # @param [Google::Apis::FirebaseappdistributionV1::GoogleLongrunningCancelOperationRequest] google_longrunning_cancel_operation_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def cancel_project_app_release_operation(name, google_longrunning_cancel_operation_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'v1/{+name}:cancel', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1::GoogleLongrunningCancelOperationRequest::Representation
          command.request_object = google_longrunning_cancel_operation_request_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Deletes a long-running operation. This method indicates that the client is no
        # longer interested in the operation result. It does not cancel the operation.
        # If the server doesn't support this method, it returns `google.rpc.Code.
        # UNIMPLEMENTED`.
        # @param [String] name
        #   The name of the operation resource to be deleted.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def delete_project_app_release_operation(name, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'v1/{+name}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Gets the latest state of a long-running operation. Clients can use this method
        # to poll the operation result at intervals as recommended by the API service.
        # @param [String] name
        #   The name of the operation resource.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_project_app_release_operation(name, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1/{+name}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists operations that match the specified filter in the request. If the server
        # doesn't support this method, it returns `UNIMPLEMENTED`.
        # @param [String] name
        #   The name of the operation's parent resource.
        # @param [String] filter
        #   The standard list filter.
        # @param [Fixnum] page_size
        #   The standard list page size.
        # @param [String] page_token
        #   The standard list page token.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleLongrunningListOperationsResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleLongrunningListOperationsResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_project_app_release_operations(name, filter: nil, page_size: nil, page_token: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1/{+name}/operations', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleLongrunningListOperationsResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleLongrunningListOperationsResponse
          command.params['name'] = name unless name.nil?
          command.query['filter'] = filter unless filter.nil?
          command.query['pageSize'] = page_size unless page_size.nil?
          command.query['pageToken'] = page_token unless page_token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Waits until the specified long-running operation is done or reaches at most a
        # specified timeout, returning the latest state. If the operation is already
        # done, the latest state is immediately returned. If the timeout specified is
        # greater than the default HTTP/RPC timeout, the HTTP/RPC timeout is used. If
        # the server does not support this method, it returns `google.rpc.Code.
        # UNIMPLEMENTED`. Note that this method is on a best-effort basis. It may return
        # the latest state before the specified timeout (including immediately), meaning
        # even an immediate response is no guarantee that the operation is done.
        # @param [String] name
        #   The name of the operation resource to wait on.
        # @param [Google::Apis::FirebaseappdistributionV1::GoogleLongrunningWaitOperationRequest] google_longrunning_wait_operation_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def wait_project_app_release_operation(name, google_longrunning_wait_operation_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'v1/{+name}:wait', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1::GoogleLongrunningWaitOperationRequest::Representation
          command.request_object = google_longrunning_wait_operation_request_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Batch adds members to a group. The testers will gain access to all releases
        # that the groups have access to.
        # @param [String] group
        #   Required. The name of the group resource to which testers are added. Format: `
        #   projects/`project_number`/groups/`group_alias``
        # @param [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchJoinGroupRequest] google_firebase_appdistro_v1_batch_join_group_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def batch_project_group_join(group, google_firebase_appdistro_v1_batch_join_group_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'v1/{+group}:batchJoin', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchJoinGroupRequest::Representation
          command.request_object = google_firebase_appdistro_v1_batch_join_group_request_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty
          command.params['group'] = group unless group.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Batch removed members from a group. The testers will lose access to all
        # releases that the groups have access to.
        # @param [String] group
        #   Required. The name of the group resource from which testers are removed.
        #   Format: `projects/`project_number`/groups/`group_alias``
        # @param [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchLeaveGroupRequest] google_firebase_appdistro_v1_batch_leave_group_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def batch_project_group_leave(group, google_firebase_appdistro_v1_batch_leave_group_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'v1/{+group}:batchLeave', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchLeaveGroupRequest::Representation
          command.request_object = google_firebase_appdistro_v1_batch_leave_group_request_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty
          command.params['group'] = group unless group.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Create a group.
        # @param [String] parent
        #   Required. The name of the project resource, which is the parent of the group
        #   resource. Format: `projects/`project_number``
        # @param [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group] google_firebase_appdistro_v1_group_object
        # @param [String] group_id
        #   Optional. The "alias" to use for the group, which will become the final
        #   component of the group's resource name. This value must be unique per project.
        #   The field is named `groupId` to comply with AIP guidance for user-specified
        #   IDs. This value should be 4-63 characters, and valid characters are `/a-z-/`.
        #   If not set, it will be generated based on the display name.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def create_project_group(parent, google_firebase_appdistro_v1_group_object = nil, group_id: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'v1/{+parent}/groups', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group::Representation
          command.request_object = google_firebase_appdistro_v1_group_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group
          command.params['parent'] = parent unless parent.nil?
          command.query['groupId'] = group_id unless group_id.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Delete a group.
        # @param [String] name
        #   Required. The name of the group resource. Format: `projects/`project_number`/
        #   groups/`group_alias``
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def delete_project_group(name, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:delete, 'v1/{+name}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleProtobufEmpty
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Get a group.
        # @param [String] name
        #   Required. The name of the group resource to retrieve. Format: `projects/`
        #   project_number`/groups/`group_alias``
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def get_project_group(name, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1/{+name}', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group
          command.params['name'] = name unless name.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # List groups.
        # @param [String] parent
        #   Required. The name of the project resource, which is the parent of the group
        #   resources. Format: `projects/`project_number``
        # @param [Fixnum] page_size
        #   Optional. The maximum number of groups to return. The service may return fewer
        #   than this value. The valid range is [1-1000]; If unspecified (0), at most 25
        #   groups are returned. Values above 1000 are coerced to 1000.
        # @param [String] page_token
        #   Optional. A page token, received from a previous `ListGroups` call. Provide
        #   this to retrieve the subsequent page. When paginating, all other parameters
        #   provided to `ListGroups` must match the call that provided the page token.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListGroupsResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListGroupsResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_project_groups(parent, page_size: nil, page_token: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1/{+parent}/groups', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListGroupsResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListGroupsResponse
          command.params['parent'] = parent unless parent.nil?
          command.query['pageSize'] = page_size unless page_size.nil?
          command.query['pageToken'] = page_token unless page_token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Update a group.
        # @param [String] name
        #   The name of the group resource. Format: `projects/`project_number`/groups/`
        #   group_alias``
        # @param [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group] google_firebase_appdistro_v1_group_object
        # @param [String] update_mask
        #   The list of fields to update.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def patch_project_group(name, google_firebase_appdistro_v1_group_object = nil, update_mask: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'v1/{+name}', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group::Representation
          command.request_object = google_firebase_appdistro_v1_group_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group
          command.params['name'] = name unless name.nil?
          command.query['updateMask'] = update_mask unless update_mask.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Batch adds testers. This call adds testers for the specified emails if they
        # don't already exist. Returns all testers specified in the request, including
        # newly created and previously existing testers. This action is idempotent.
        # @param [String] project
        #   Required. The name of the project resource. Format: `projects/`project_number``
        # @param [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchAddTestersRequest] google_firebase_appdistro_v1_batch_add_testers_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchAddTestersResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchAddTestersResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def batch_project_tester_add(project, google_firebase_appdistro_v1_batch_add_testers_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'v1/{+project}/testers:batchAdd', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchAddTestersRequest::Representation
          command.request_object = google_firebase_appdistro_v1_batch_add_testers_request_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchAddTestersResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchAddTestersResponse
          command.params['project'] = project unless project.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Batch removes testers. If found, this call deletes testers for the specified
        # emails. Returns all deleted testers.
        # @param [String] project
        #   Required. The name of the project resource. Format: `projects/`project_number``
        # @param [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchRemoveTestersRequest] google_firebase_appdistro_v1_batch_remove_testers_request_object
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchRemoveTestersResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchRemoveTestersResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def batch_project_tester_remove(project, google_firebase_appdistro_v1_batch_remove_testers_request_object = nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:post, 'v1/{+project}/testers:batchRemove', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchRemoveTestersRequest::Representation
          command.request_object = google_firebase_appdistro_v1_batch_remove_testers_request_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchRemoveTestersResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchRemoveTestersResponse
          command.params['project'] = project unless project.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Lists testers and their resource ids.
        # @param [String] parent
        #   Required. The name of the project resource, which is the parent of the tester
        #   resources. Format: `projects/`project_number``
        # @param [String] filter
        #   Optional. The expression to filter testers listed in the response. To learn
        #   more about filtering, refer to [Google's AIP-160 standard](http://aip.dev/160).
        #   Supported fields: - `name` - `displayName` - `groups` Example: - `name = "
        #   projects/-/testers/*@example.com"` - `displayName = "Joe Sixpack"` - `groups =
        #   "projects/*/groups/qa-team"`
        # @param [Fixnum] page_size
        #   Optional. The maximum number of testers to return. The service may return
        #   fewer than this value. The valid range is [1-1000]; If unspecified (0), at
        #   most 10 testers are returned. Values above 1000 are coerced to 1000.
        # @param [String] page_token
        #   Optional. A page token, received from a previous `ListTesters` call. Provide
        #   this to retrieve the subsequent page. When paginating, all other parameters
        #   provided to `ListTesters` must match the call that provided the page token.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListTestersResponse] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListTestersResponse]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def list_project_testers(parent, filter: nil, page_size: nil, page_token: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:get, 'v1/{+parent}/testers', options)
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListTestersResponse::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ListTestersResponse
          command.params['parent'] = parent unless parent.nil?
          command.query['filter'] = filter unless filter.nil?
          command.query['pageSize'] = page_size unless page_size.nil?
          command.query['pageToken'] = page_token unless page_token.nil?
          command.query['fields'] = fields unless fields.nil?
          command.query['quotaUser'] = quota_user unless quota_user.nil?
          execute_or_queue_command(command, &block)
        end
        
        # Update a tester. If the testers joins a group they gain access to all releases
        # that the group has access to.
        # @param [String] name
        #   The name of the tester resource. Format: `projects/`project_number`/testers/`
        #   email_address``
        # @param [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Tester] google_firebase_appdistro_v1_tester_object
        # @param [String] update_mask
        #   The list of fields to update.
        # @param [String] fields
        #   Selector specifying which fields to include in a partial response.
        # @param [String] quota_user
        #   Available to use for quota purposes for server-side applications. Can be any
        #   arbitrary string assigned to a user, but should not exceed 40 characters.
        # @param [Google::Apis::RequestOptions] options
        #   Request-specific options
        #
        # @yield [result, err] Result & error if block supplied
        # @yieldparam result [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Tester] parsed result object
        # @yieldparam err [StandardError] error object if request failed
        #
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Tester]
        #
        # @raise [Google::Apis::ServerError] An error occurred on the server and the request can be retried
        # @raise [Google::Apis::ClientError] The request is invalid and should not be retried without modification
        # @raise [Google::Apis::AuthorizationError] Authorization is required
        def patch_project_tester(name, google_firebase_appdistro_v1_tester_object = nil, update_mask: nil, fields: nil, quota_user: nil, options: nil, &block)
          command = make_simple_command(:patch, 'v1/{+name}', options)
          command.request_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Tester::Representation
          command.request_object = google_firebase_appdistro_v1_tester_object
          command.response_representation = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Tester::Representation
          command.response_class = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Tester
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
