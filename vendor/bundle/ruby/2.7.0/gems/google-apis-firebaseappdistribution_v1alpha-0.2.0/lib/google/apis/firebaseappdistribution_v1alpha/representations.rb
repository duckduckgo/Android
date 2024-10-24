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
    module FirebaseappdistributionV1alpha
      
      class GoogleFirebaseAppdistroV1Release
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1ReleaseNotes
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1UploadReleaseMetadata
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1UploadReleaseResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaAabCertificate
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaApp
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaAppCrash
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaCreateReleaseNotesRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaCreateReleaseNotesResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaDeviceExecution
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaEnableAccessOnReleaseRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaEnableAccessOnReleaseResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaGetReleaseByUploadHashResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaGetTesterUdidsResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaGetUploadStatusResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaJwt
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaListReleaseTestsResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaLoginCredential
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaLoginCredentialFieldHints
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaProvisionAppResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaRelease
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaReleaseNotes
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaReleaseTest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaRoboCrawler
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaRoboStats
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaTestConfig
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaTestDevice
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GoogleFirebaseAppdistroV1alphaTesterUdid
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
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
          property :release_notes, as: 'releaseNotes', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1ReleaseNotes, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1ReleaseNotes::Representation
      
          property :testing_uri, as: 'testingUri'
        end
      end
      
      class GoogleFirebaseAppdistroV1ReleaseNotes
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :text, as: 'text'
        end
      end
      
      class GoogleFirebaseAppdistroV1UploadReleaseMetadata
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class GoogleFirebaseAppdistroV1UploadReleaseResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :release, as: 'release', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1Release, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1Release::Representation
      
          property :result, as: 'result'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaAabCertificate
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :certificate_hash_md5, as: 'certificateHashMd5'
          property :certificate_hash_sha1, as: 'certificateHashSha1'
          property :certificate_hash_sha256, as: 'certificateHashSha256'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaApp
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :aab_certificate, as: 'aabCertificate', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaAabCertificate, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaAabCertificate::Representation
      
          property :aab_state, as: 'aabState'
          property :app_id, as: 'appId'
          property :bundle_id, as: 'bundleId'
          property :contact_email, as: 'contactEmail'
          property :platform, as: 'platform'
          property :project_number, as: 'projectNumber'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaAppCrash
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :message, as: 'message'
          property :stack_trace, as: 'stackTrace'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaCreateReleaseNotesRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :release_notes, as: 'releaseNotes', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseNotes, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseNotes::Representation
      
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaCreateReleaseNotesResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaDeviceExecution
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :app_crash, as: 'appCrash', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaAppCrash, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaAppCrash::Representation
      
          property :crawl_graph_uri, as: 'crawlGraphUri'
          property :device, as: 'device', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestDevice, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestDevice::Representation
      
          property :failed_reason, as: 'failedReason'
          property :inconclusive_reason, as: 'inconclusiveReason'
          property :results_storage_path, as: 'resultsStoragePath'
          property :robo_stats, as: 'roboStats', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaRoboStats, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaRoboStats::Representation
      
          collection :screenshot_uris, as: 'screenshotUris'
          property :state, as: 'state'
          property :video_uri, as: 'videoUri'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaEnableAccessOnReleaseRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :build_version, as: 'buildVersion'
          property :display_version, as: 'displayVersion'
          collection :emails, as: 'emails'
          collection :group_ids, as: 'groupIds'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaEnableAccessOnReleaseResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaGetReleaseByUploadHashResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :release, as: 'release', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaRelease, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaRelease::Representation
      
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaGetTesterUdidsResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :tester_udids, as: 'testerUdids', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTesterUdid, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTesterUdid::Representation
      
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaGetUploadStatusResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :error_code, as: 'errorCode'
          property :message, as: 'message'
          property :release, as: 'release', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaRelease, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaRelease::Representation
      
          property :status, as: 'status'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaJwt
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :token, as: 'token'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaListReleaseTestsResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :next_page_token, as: 'nextPageToken'
          collection :release_tests, as: 'releaseTests', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseTest, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseTest::Representation
      
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaLoginCredential
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :field_hints, as: 'fieldHints', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaLoginCredentialFieldHints, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaLoginCredentialFieldHints::Representation
      
          property :google, as: 'google'
          property :password, as: 'password'
          property :username, as: 'username'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaLoginCredentialFieldHints
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :password_resource_name, as: 'passwordResourceName'
          property :username_resource_name, as: 'usernameResourceName'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaProvisionAppResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaRelease
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :build_version, as: 'buildVersion'
          property :display_version, as: 'displayVersion'
          property :distributed_at, as: 'distributedAt'
          property :id, as: 'id'
          property :instance_id, as: 'instanceId'
          property :last_activity_at, as: 'lastActivityAt'
          property :open_invitation_count, as: 'openInvitationCount'
          property :received_at, as: 'receivedAt'
          property :release_notes_summary, as: 'releaseNotesSummary'
          property :tester_count, as: 'testerCount'
          property :tester_with_install_count, as: 'testerWithInstallCount'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaReleaseNotes
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :release_notes, as: 'releaseNotes'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaReleaseTest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :create_time, as: 'createTime'
          collection :device_executions, as: 'deviceExecutions', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaDeviceExecution, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaDeviceExecution::Representation
      
          property :login_credential, as: 'loginCredential', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaLoginCredential, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaLoginCredential::Representation
      
          property :name, as: 'name'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaRoboCrawler
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :login_credential, as: 'loginCredential', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaLoginCredential, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaLoginCredential::Representation
      
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaRoboStats
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :actions_performed, as: 'actionsPerformed'
          property :crawl_duration, as: 'crawlDuration'
          property :distinct_visited_screens, as: 'distinctVisitedScreens'
          property :main_activity_crawl_timed_out, as: 'mainActivityCrawlTimedOut'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaTestConfig
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :name, as: 'name'
          property :robo_crawler, as: 'roboCrawler', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaRoboCrawler, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaRoboCrawler::Representation
      
          collection :test_devices, as: 'testDevices', class: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestDevice, decorator: Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestDevice::Representation
      
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaTestDevice
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :locale, as: 'locale'
          property :model, as: 'model'
          property :orientation, as: 'orientation'
          property :version, as: 'version'
        end
      end
      
      class GoogleFirebaseAppdistroV1alphaTesterUdid
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :name, as: 'name'
          property :platform, as: 'platform'
          property :udid, as: 'udid'
        end
      end
    end
  end
end
