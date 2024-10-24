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

require 'date'
require 'google/apis/core/base_service'
require 'google/apis/core/json_representation'
require 'google/apis/core/hashable'
require 'google/apis/errors'

module Google
  module Apis
    module FirebaseappdistributionV1
      
      class GdataBlobstore2Info
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GdataCompositeMedia
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GdataContentTypeInfo
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GdataDiffChecksumsResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GdataDiffDownloadResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GdataDiffUploadRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GdataDiffUploadResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GdataDiffVersionResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GdataDownloadParameters
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GdataMedia
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GdataObjectId
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1AabInfo
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1BatchAddTestersRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1BatchAddTestersResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1BatchDeleteReleasesRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1BatchJoinGroupRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1BatchLeaveGroupRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1BatchRemoveTestersRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1BatchRemoveTestersResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1DistributeReleaseRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1DistributeReleaseResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1FeedbackReport
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1Group
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1ListFeedbackReportsResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1ListGroupsResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1ListReleasesResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1ListTestersResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1Release
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1ReleaseNotes
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1TestCertificate
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1Tester
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1UploadReleaseMetadata
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1UploadReleaseRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1UploadReleaseResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleLongrunningCancelOperationRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleLongrunningListOperationsResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleLongrunningOperation
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleLongrunningWaitOperationRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleProtobufEmpty
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleRpcStatus
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GdataBlobstore2Info
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :blob_generation, :numeric_string => true, as: 'blobGeneration'
          property :blob_id, as: 'blobId'
          property :download_read_handle, :base64 => true, as: 'downloadReadHandle'
          property :read_token, as: 'readToken'
          property :upload_metadata_container, :base64 => true, as: 'uploadMetadataContainer'
        end
      end
      
      class GdataCompositeMedia
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :blob_ref, :base64 => true, as: 'blobRef'
          property :blobstore2_info, as: 'blobstore2Info', class: Google::Apis::FirebaseappdistributionV1::GdataBlobstore2Info, decorator: Google::Apis::FirebaseappdistributionV1::GdataBlobstore2Info::Representation
      
          property :cosmo_binary_reference, :base64 => true, as: 'cosmoBinaryReference'
          property :crc32c_hash, as: 'crc32cHash'
          property :inline, :base64 => true, as: 'inline'
          property :length, :numeric_string => true, as: 'length'
          property :md5_hash, :base64 => true, as: 'md5Hash'
          property :object_id_prop, as: 'objectId', class: Google::Apis::FirebaseappdistributionV1::GdataObjectId, decorator: Google::Apis::FirebaseappdistributionV1::GdataObjectId::Representation
      
          property :path, as: 'path'
          property :reference_type, as: 'referenceType'
          property :sha1_hash, :base64 => true, as: 'sha1Hash'
        end
      end
      
      class GdataContentTypeInfo
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :best_guess, as: 'bestGuess'
          property :from_bytes, as: 'fromBytes'
          property :from_file_name, as: 'fromFileName'
          property :from_header, as: 'fromHeader'
          property :from_url_path, as: 'fromUrlPath'
        end
      end
      
      class GdataDiffChecksumsResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :checksums_location, as: 'checksumsLocation', class: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia, decorator: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia::Representation
      
          property :chunk_size_bytes, :numeric_string => true, as: 'chunkSizeBytes'
          property :object_location, as: 'objectLocation', class: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia, decorator: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia::Representation
      
          property :object_size_bytes, :numeric_string => true, as: 'objectSizeBytes'
          property :object_version, as: 'objectVersion'
        end
      end
      
      class GdataDiffDownloadResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :object_location, as: 'objectLocation', class: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia, decorator: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia::Representation
      
        end
      end
      
      class GdataDiffUploadRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :checksums_info, as: 'checksumsInfo', class: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia, decorator: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia::Representation
      
          property :object_info, as: 'objectInfo', class: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia, decorator: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia::Representation
      
          property :object_version, as: 'objectVersion'
        end
      end
      
      class GdataDiffUploadResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :object_version, as: 'objectVersion'
          property :original_object, as: 'originalObject', class: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia, decorator: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia::Representation
      
        end
      end
      
      class GdataDiffVersionResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :object_size_bytes, :numeric_string => true, as: 'objectSizeBytes'
          property :object_version, as: 'objectVersion'
        end
      end
      
      class GdataDownloadParameters
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :allow_gzip_compression, as: 'allowGzipCompression'
          property :ignore_range, as: 'ignoreRange'
        end
      end
      
      class GdataMedia
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :algorithm, as: 'algorithm'
          property :bigstore_object_ref, :base64 => true, as: 'bigstoreObjectRef'
          property :blob_ref, :base64 => true, as: 'blobRef'
          property :blobstore2_info, as: 'blobstore2Info', class: Google::Apis::FirebaseappdistributionV1::GdataBlobstore2Info, decorator: Google::Apis::FirebaseappdistributionV1::GdataBlobstore2Info::Representation
      
          collection :composite_media, as: 'compositeMedia', class: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia, decorator: Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia::Representation
      
          property :content_type, as: 'contentType'
          property :content_type_info, as: 'contentTypeInfo', class: Google::Apis::FirebaseappdistributionV1::GdataContentTypeInfo, decorator: Google::Apis::FirebaseappdistributionV1::GdataContentTypeInfo::Representation
      
          property :cosmo_binary_reference, :base64 => true, as: 'cosmoBinaryReference'
          property :crc32c_hash, as: 'crc32cHash'
          property :diff_checksums_response, as: 'diffChecksumsResponse', class: Google::Apis::FirebaseappdistributionV1::GdataDiffChecksumsResponse, decorator: Google::Apis::FirebaseappdistributionV1::GdataDiffChecksumsResponse::Representation
      
          property :diff_download_response, as: 'diffDownloadResponse', class: Google::Apis::FirebaseappdistributionV1::GdataDiffDownloadResponse, decorator: Google::Apis::FirebaseappdistributionV1::GdataDiffDownloadResponse::Representation
      
          property :diff_upload_request, as: 'diffUploadRequest', class: Google::Apis::FirebaseappdistributionV1::GdataDiffUploadRequest, decorator: Google::Apis::FirebaseappdistributionV1::GdataDiffUploadRequest::Representation
      
          property :diff_upload_response, as: 'diffUploadResponse', class: Google::Apis::FirebaseappdistributionV1::GdataDiffUploadResponse, decorator: Google::Apis::FirebaseappdistributionV1::GdataDiffUploadResponse::Representation
      
          property :diff_version_response, as: 'diffVersionResponse', class: Google::Apis::FirebaseappdistributionV1::GdataDiffVersionResponse, decorator: Google::Apis::FirebaseappdistributionV1::GdataDiffVersionResponse::Representation
      
          property :download_parameters, as: 'downloadParameters', class: Google::Apis::FirebaseappdistributionV1::GdataDownloadParameters, decorator: Google::Apis::FirebaseappdistributionV1::GdataDownloadParameters::Representation
      
          property :filename, as: 'filename'
          property :hash_prop, as: 'hash'
          property :hash_verified, as: 'hashVerified'
          property :inline, :base64 => true, as: 'inline'
          property :is_potential_retry, as: 'isPotentialRetry'
          property :length, :numeric_string => true, as: 'length'
          property :md5_hash, :base64 => true, as: 'md5Hash'
          property :media_id, :base64 => true, as: 'mediaId'
          property :object_id_prop, as: 'objectId', class: Google::Apis::FirebaseappdistributionV1::GdataObjectId, decorator: Google::Apis::FirebaseappdistributionV1::GdataObjectId::Representation
      
          property :path, as: 'path'
          property :reference_type, as: 'referenceType'
          property :sha1_hash, :base64 => true, as: 'sha1Hash'
          property :sha256_hash, :base64 => true, as: 'sha256Hash'
          property :timestamp, :numeric_string => true, as: 'timestamp'
          property :token, as: 'token'
        end
      end
      
      class GdataObjectId
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :bucket_name, as: 'bucketName'
          property :generation, :numeric_string => true, as: 'generation'
          property :object_name, as: 'objectName'
        end
      end
      
      class GoogleFirebaseAppdistroV1AabInfo
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :integration_state, as: 'integrationState'
          property :name, as: 'name'
          property :test_certificate, as: 'testCertificate', class: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1TestCertificate, decorator: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1TestCertificate::Representation
      
        end
      end
      
      class GoogleFirebaseAppdistroV1BatchAddTestersRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :emails, as: 'emails'
        end
      end
      
      class GoogleFirebaseAppdistroV1BatchAddTestersResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :testers, as: 'testers', class: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Tester, decorator: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Tester::Representation
      
        end
      end
      
      class GoogleFirebaseAppdistroV1BatchDeleteReleasesRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :names, as: 'names'
        end
      end
      
      class GoogleFirebaseAppdistroV1BatchJoinGroupRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :create_missing_testers, as: 'createMissingTesters'
          collection :emails, as: 'emails'
        end
      end
      
      class GoogleFirebaseAppdistroV1BatchLeaveGroupRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :emails, as: 'emails'
        end
      end
      
      class GoogleFirebaseAppdistroV1BatchRemoveTestersRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :emails, as: 'emails'
        end
      end
      
      class GoogleFirebaseAppdistroV1BatchRemoveTestersResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :emails, as: 'emails'
        end
      end
      
      class GoogleFirebaseAppdistroV1DistributeReleaseRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :group_aliases, as: 'groupAliases'
          collection :tester_emails, as: 'testerEmails'
        end
      end
      
      class GoogleFirebaseAppdistroV1DistributeReleaseResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class GoogleFirebaseAppdistroV1FeedbackReport
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :create_time, as: 'createTime'
          property :firebase_console_uri, as: 'firebaseConsoleUri'
          property :name, as: 'name'
          property :screenshot_uri, as: 'screenshotUri'
          property :tester, as: 'tester'
          property :text, as: 'text'
        end
      end
      
      class GoogleFirebaseAppdistroV1Group
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :display_name, as: 'displayName'
          property :invite_link_count, as: 'inviteLinkCount'
          property :name, as: 'name'
          property :release_count, as: 'releaseCount'
          property :tester_count, as: 'testerCount'
        end
      end
      
      class GoogleFirebaseAppdistroV1ListFeedbackReportsResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :feedback_reports, as: 'feedbackReports', class: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1FeedbackReport, decorator: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1FeedbackReport::Representation
      
          property :next_page_token, as: 'nextPageToken'
        end
      end
      
      class GoogleFirebaseAppdistroV1ListGroupsResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :groups, as: 'groups', class: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group, decorator: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group::Representation
      
          property :next_page_token, as: 'nextPageToken'
        end
      end
      
      class GoogleFirebaseAppdistroV1ListReleasesResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :next_page_token, as: 'nextPageToken'
          collection :releases, as: 'releases', class: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release, decorator: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release::Representation
      
        end
      end
      
      class GoogleFirebaseAppdistroV1ListTestersResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :next_page_token, as: 'nextPageToken'
          collection :testers, as: 'testers', class: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Tester, decorator: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Tester::Representation
      
        end
      end
      
      class GoogleFirebaseAppdistroV1Release
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :binary_download_uri, as: 'binaryDownloadUri'
          property :build_version, as: 'buildVersion'
          property :create_time, as: 'createTime'
          property :display_version, as: 'displayVersion'
          property :firebase_console_uri, as: 'firebaseConsoleUri'
          property :name, as: 'name'
          property :release_notes, as: 'releaseNotes', class: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ReleaseNotes, decorator: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ReleaseNotes::Representation
      
          property :testing_uri, as: 'testingUri'
        end
      end
      
      class GoogleFirebaseAppdistroV1ReleaseNotes
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :text, as: 'text'
        end
      end
      
      class GoogleFirebaseAppdistroV1TestCertificate
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :hash_md5, as: 'hashMd5'
          property :hash_sha1, as: 'hashSha1'
          property :hash_sha256, as: 'hashSha256'
        end
      end
      
      class GoogleFirebaseAppdistroV1Tester
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :display_name, as: 'displayName'
          collection :groups, as: 'groups'
          property :last_activity_time, as: 'lastActivityTime'
          property :name, as: 'name'
        end
      end
      
      class GoogleFirebaseAppdistroV1UploadReleaseMetadata
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class GoogleFirebaseAppdistroV1UploadReleaseRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :blob, as: 'blob', class: Google::Apis::FirebaseappdistributionV1::GdataMedia, decorator: Google::Apis::FirebaseappdistributionV1::GdataMedia::Representation
      
        end
      end
      
      class GoogleFirebaseAppdistroV1UploadReleaseResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :release, as: 'release', class: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release, decorator: Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release::Representation
      
          property :result, as: 'result'
        end
      end
      
      class GoogleLongrunningCancelOperationRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class GoogleLongrunningListOperationsResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :next_page_token, as: 'nextPageToken'
          collection :operations, as: 'operations', class: Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation, decorator: Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation::Representation
      
        end
      end
      
      class GoogleLongrunningOperation
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :done, as: 'done'
          property :error, as: 'error', class: Google::Apis::FirebaseappdistributionV1::GoogleRpcStatus, decorator: Google::Apis::FirebaseappdistributionV1::GoogleRpcStatus::Representation
      
          hash :metadata, as: 'metadata'
          property :name, as: 'name'
          hash :response, as: 'response'
        end
      end
      
      class GoogleLongrunningWaitOperationRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :timeout, as: 'timeout'
        end
      end
      
      class GoogleProtobufEmpty
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class GoogleRpcStatus
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :code, as: 'code'
          collection :details, as: 'details'
          property :message, as: 'message'
        end
      end
    end
  end
end
