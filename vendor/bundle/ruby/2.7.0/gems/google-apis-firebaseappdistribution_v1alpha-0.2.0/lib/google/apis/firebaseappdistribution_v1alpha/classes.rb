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
      
      # A release of a Firebase app.
      class GoogleFirebaseAppdistroV1Release
        include Google::Apis::Core::Hashable
      
        # Output only. A signed link (which expires in one hour) to directly download
        # the app binary (IPA/APK/AAB) file.
        # Corresponds to the JSON property `binaryDownloadUri`
        # @return [String]
        attr_accessor :binary_download_uri
      
        # Output only. Build version of the release. For an Android release, the build
        # version is the `versionCode`. For an iOS release, the build version is the `
        # CFBundleVersion`.
        # Corresponds to the JSON property `buildVersion`
        # @return [String]
        attr_accessor :build_version
      
        # Output only. The time the release was created.
        # Corresponds to the JSON property `createTime`
        # @return [String]
        attr_accessor :create_time
      
        # Output only. Display version of the release. For an Android release, the
        # display version is the `versionName`. For an iOS release, the display version
        # is the `CFBundleShortVersionString`.
        # Corresponds to the JSON property `displayVersion`
        # @return [String]
        attr_accessor :display_version
      
        # Output only. A link to the Firebase console displaying a single release.
        # Corresponds to the JSON property `firebaseConsoleUri`
        # @return [String]
        attr_accessor :firebase_console_uri
      
        # The name of the release resource. Format: `projects/`project_number`/apps/`
        # app_id`/releases/`release_id``
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        # Notes that belong to a release.
        # Corresponds to the JSON property `releaseNotes`
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1ReleaseNotes]
        attr_accessor :release_notes
      
        # Output only. A link to the release in the tester web clip or Android app that
        # lets testers (which were granted access to the app) view release notes and
        # install the app onto their devices.
        # Corresponds to the JSON property `testingUri`
        # @return [String]
        attr_accessor :testing_uri
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @binary_download_uri = args[:binary_download_uri] if args.key?(:binary_download_uri)
          @build_version = args[:build_version] if args.key?(:build_version)
          @create_time = args[:create_time] if args.key?(:create_time)
          @display_version = args[:display_version] if args.key?(:display_version)
          @firebase_console_uri = args[:firebase_console_uri] if args.key?(:firebase_console_uri)
          @name = args[:name] if args.key?(:name)
          @release_notes = args[:release_notes] if args.key?(:release_notes)
          @testing_uri = args[:testing_uri] if args.key?(:testing_uri)
        end
      end
      
      # Notes that belong to a release.
      class GoogleFirebaseAppdistroV1ReleaseNotes
        include Google::Apis::Core::Hashable
      
        # The text of the release notes.
        # Corresponds to the JSON property `text`
        # @return [String]
        attr_accessor :text
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @text = args[:text] if args.key?(:text)
        end
      end
      
      # Operation metadata for `UploadRelease`.
      class GoogleFirebaseAppdistroV1UploadReleaseMetadata
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # Response message for `UploadRelease`.
      class GoogleFirebaseAppdistroV1UploadReleaseResponse
        include Google::Apis::Core::Hashable
      
        # A release of a Firebase app.
        # Corresponds to the JSON property `release`
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1Release]
        attr_accessor :release
      
        # Result of upload release.
        # Corresponds to the JSON property `result`
        # @return [String]
        attr_accessor :result
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @release = args[:release] if args.key?(:release)
          @result = args[:result] if args.key?(:result)
        end
      end
      
      # App bundle test certificate
      class GoogleFirebaseAppdistroV1alphaAabCertificate
        include Google::Apis::Core::Hashable
      
        # MD5 hash of the certificate used to resign the AAB
        # Corresponds to the JSON property `certificateHashMd5`
        # @return [String]
        attr_accessor :certificate_hash_md5
      
        # SHA1 hash of the certificate used to resign the AAB
        # Corresponds to the JSON property `certificateHashSha1`
        # @return [String]
        attr_accessor :certificate_hash_sha1
      
        # SHA256 hash of the certificate used to resign the AAB
        # Corresponds to the JSON property `certificateHashSha256`
        # @return [String]
        attr_accessor :certificate_hash_sha256
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @certificate_hash_md5 = args[:certificate_hash_md5] if args.key?(:certificate_hash_md5)
          @certificate_hash_sha1 = args[:certificate_hash_sha1] if args.key?(:certificate_hash_sha1)
          @certificate_hash_sha256 = args[:certificate_hash_sha256] if args.key?(:certificate_hash_sha256)
        end
      end
      
      # 
      class GoogleFirebaseAppdistroV1alphaApp
        include Google::Apis::Core::Hashable
      
        # App bundle test certificate
        # Corresponds to the JSON property `aabCertificate`
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaAabCertificate]
        attr_accessor :aab_certificate
      
        # App bundle state. Only valid for android apps. The app_view field in the
        # request must be set to FULL in order for this to be populated.
        # Corresponds to the JSON property `aabState`
        # @return [String]
        attr_accessor :aab_state
      
        # Firebase gmp app id
        # Corresponds to the JSON property `appId`
        # @return [String]
        attr_accessor :app_id
      
        # Bundle identifier
        # Corresponds to the JSON property `bundleId`
        # @return [String]
        attr_accessor :bundle_id
      
        # Developer contact email for testers to reach out to about privacy or support
        # issues.
        # Corresponds to the JSON property `contactEmail`
        # @return [String]
        attr_accessor :contact_email
      
        # iOS or Android
        # Corresponds to the JSON property `platform`
        # @return [String]
        attr_accessor :platform
      
        # Project number of the Firebase project, for example 300830567303.
        # Corresponds to the JSON property `projectNumber`
        # @return [String]
        attr_accessor :project_number
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @aab_certificate = args[:aab_certificate] if args.key?(:aab_certificate)
          @aab_state = args[:aab_state] if args.key?(:aab_state)
          @app_id = args[:app_id] if args.key?(:app_id)
          @bundle_id = args[:bundle_id] if args.key?(:bundle_id)
          @contact_email = args[:contact_email] if args.key?(:contact_email)
          @platform = args[:platform] if args.key?(:platform)
          @project_number = args[:project_number] if args.key?(:project_number)
        end
      end
      
      # An app crash that occurred during an automated test.
      class GoogleFirebaseAppdistroV1alphaAppCrash
        include Google::Apis::Core::Hashable
      
        # Output only. The message associated with the crash.
        # Corresponds to the JSON property `message`
        # @return [String]
        attr_accessor :message
      
        # Output only. The raw stack trace.
        # Corresponds to the JSON property `stackTrace`
        # @return [String]
        attr_accessor :stack_trace
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @message = args[:message] if args.key?(:message)
          @stack_trace = args[:stack_trace] if args.key?(:stack_trace)
        end
      end
      
      # 
      class GoogleFirebaseAppdistroV1alphaCreateReleaseNotesRequest
        include Google::Apis::Core::Hashable
      
        # The actual release notes body from the user
        # Corresponds to the JSON property `releaseNotes`
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseNotes]
        attr_accessor :release_notes
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @release_notes = args[:release_notes] if args.key?(:release_notes)
        end
      end
      
      # 
      class GoogleFirebaseAppdistroV1alphaCreateReleaseNotesResponse
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # The results of running an automated test on a particular device.
      class GoogleFirebaseAppdistroV1alphaDeviceExecution
        include Google::Apis::Core::Hashable
      
        # An app crash that occurred during an automated test.
        # Corresponds to the JSON property `appCrash`
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaAppCrash]
        attr_accessor :app_crash
      
        # Output only. A URI to an image of the Robo crawl graph.
        # Corresponds to the JSON property `crawlGraphUri`
        # @return [String]
        attr_accessor :crawl_graph_uri
      
        # A device on which automated tests can be run.
        # Corresponds to the JSON property `device`
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestDevice]
        attr_accessor :device
      
        # Output only. The reason why the test failed.
        # Corresponds to the JSON property `failedReason`
        # @return [String]
        attr_accessor :failed_reason
      
        # Output only. The reason why the test was inconclusive.
        # Corresponds to the JSON property `inconclusiveReason`
        # @return [String]
        attr_accessor :inconclusive_reason
      
        # Output only. The path to a directory in Cloud Storage that will eventually
        # contain the results for this execution. For example, gs://bucket/Nexus5-18-en-
        # portrait.
        # Corresponds to the JSON property `resultsStoragePath`
        # @return [String]
        attr_accessor :results_storage_path
      
        # Statistics collected during a Robo test.
        # Corresponds to the JSON property `roboStats`
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaRoboStats]
        attr_accessor :robo_stats
      
        # Output only. A list of screenshot image URIs taken from the Robo crawl. The
        # file names are numbered by the order in which they were taken.
        # Corresponds to the JSON property `screenshotUris`
        # @return [Array<String>]
        attr_accessor :screenshot_uris
      
        # Output only. The state of the test.
        # Corresponds to the JSON property `state`
        # @return [String]
        attr_accessor :state
      
        # Output only. A URI to a video of the test run.
        # Corresponds to the JSON property `videoUri`
        # @return [String]
        attr_accessor :video_uri
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @app_crash = args[:app_crash] if args.key?(:app_crash)
          @crawl_graph_uri = args[:crawl_graph_uri] if args.key?(:crawl_graph_uri)
          @device = args[:device] if args.key?(:device)
          @failed_reason = args[:failed_reason] if args.key?(:failed_reason)
          @inconclusive_reason = args[:inconclusive_reason] if args.key?(:inconclusive_reason)
          @results_storage_path = args[:results_storage_path] if args.key?(:results_storage_path)
          @robo_stats = args[:robo_stats] if args.key?(:robo_stats)
          @screenshot_uris = args[:screenshot_uris] if args.key?(:screenshot_uris)
          @state = args[:state] if args.key?(:state)
          @video_uri = args[:video_uri] if args.key?(:video_uri)
        end
      end
      
      # 
      class GoogleFirebaseAppdistroV1alphaEnableAccessOnReleaseRequest
        include Google::Apis::Core::Hashable
      
        # Optional. Ignored. Used to be build version of the app release if an instance
        # identifier was provided for the release_id.
        # Corresponds to the JSON property `buildVersion`
        # @return [String]
        attr_accessor :build_version
      
        # Optional. Ignored. Used to be display version of the app release if an
        # instance identifier was provided for the release_id.
        # Corresponds to the JSON property `displayVersion`
        # @return [String]
        attr_accessor :display_version
      
        # Optional. An email address which should get access to this release, for
        # example rebeccahe@google.com
        # Corresponds to the JSON property `emails`
        # @return [Array<String>]
        attr_accessor :emails
      
        # Optional. A repeated list of group aliases to enable access to a release for
        # Note: This field is misnamed, but can't be changed because we need to maintain
        # compatibility with old build tools
        # Corresponds to the JSON property `groupIds`
        # @return [Array<String>]
        attr_accessor :group_ids
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @build_version = args[:build_version] if args.key?(:build_version)
          @display_version = args[:display_version] if args.key?(:display_version)
          @emails = args[:emails] if args.key?(:emails)
          @group_ids = args[:group_ids] if args.key?(:group_ids)
        end
      end
      
      # 
      class GoogleFirebaseAppdistroV1alphaEnableAccessOnReleaseResponse
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # Response object to get the release given a upload hash
      class GoogleFirebaseAppdistroV1alphaGetReleaseByUploadHashResponse
        include Google::Apis::Core::Hashable
      
        # Proto defining a release object
        # Corresponds to the JSON property `release`
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaRelease]
        attr_accessor :release
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @release = args[:release] if args.key?(:release)
        end
      end
      
      # Response containing the UDIDs of tester iOS devices in a project
      class GoogleFirebaseAppdistroV1alphaGetTesterUdidsResponse
        include Google::Apis::Core::Hashable
      
        # The UDIDs of tester iOS devices in a project
        # Corresponds to the JSON property `testerUdids`
        # @return [Array<Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTesterUdid>]
        attr_accessor :tester_udids
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @tester_udids = args[:tester_udids] if args.key?(:tester_udids)
        end
      end
      
      # 
      class GoogleFirebaseAppdistroV1alphaGetUploadStatusResponse
        include Google::Apis::Core::Hashable
      
        # The error code associated with (only set on "FAILURE")
        # Corresponds to the JSON property `errorCode`
        # @return [String]
        attr_accessor :error_code
      
        # Any additional context for the given upload status (e.g. error message) Meant
        # to be displayed to the client
        # Corresponds to the JSON property `message`
        # @return [String]
        attr_accessor :message
      
        # Proto defining a release object
        # Corresponds to the JSON property `release`
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaRelease]
        attr_accessor :release
      
        # The status of the upload
        # Corresponds to the JSON property `status`
        # @return [String]
        attr_accessor :status
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @error_code = args[:error_code] if args.key?(:error_code)
          @message = args[:message] if args.key?(:message)
          @release = args[:release] if args.key?(:release)
          @status = args[:status] if args.key?(:status)
        end
      end
      
      # 
      class GoogleFirebaseAppdistroV1alphaJwt
        include Google::Apis::Core::Hashable
      
        # 
        # Corresponds to the JSON property `token`
        # @return [String]
        attr_accessor :token
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @token = args[:token] if args.key?(:token)
        end
      end
      
      # The response message for `ListReleaseTests`.
      class GoogleFirebaseAppdistroV1alphaListReleaseTestsResponse
        include Google::Apis::Core::Hashable
      
        # A short-lived token, which can be sent as `pageToken` to retrieve the next
        # page. If this field is omitted, there are no subsequent pages.
        # Corresponds to the JSON property `nextPageToken`
        # @return [String]
        attr_accessor :next_page_token
      
        # The tests listed.
        # Corresponds to the JSON property `releaseTests`
        # @return [Array<Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaReleaseTest>]
        attr_accessor :release_tests
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @next_page_token = args[:next_page_token] if args.key?(:next_page_token)
          @release_tests = args[:release_tests] if args.key?(:release_tests)
        end
      end
      
      # Login credential for automated tests
      class GoogleFirebaseAppdistroV1alphaLoginCredential
        include Google::Apis::Core::Hashable
      
        # Hints to the crawler for identifying input fields
        # Corresponds to the JSON property `fieldHints`
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaLoginCredentialFieldHints]
        attr_accessor :field_hints
      
        # Optional. Are these credentials for Google?
        # Corresponds to the JSON property `google`
        # @return [Boolean]
        attr_accessor :google
        alias_method :google?, :google
      
        # Optional. Password for automated tests
        # Corresponds to the JSON property `password`
        # @return [String]
        attr_accessor :password
      
        # Optional. Username for automated tests
        # Corresponds to the JSON property `username`
        # @return [String]
        attr_accessor :username
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @field_hints = args[:field_hints] if args.key?(:field_hints)
          @google = args[:google] if args.key?(:google)
          @password = args[:password] if args.key?(:password)
          @username = args[:username] if args.key?(:username)
        end
      end
      
      # Hints to the crawler for identifying input fields
      class GoogleFirebaseAppdistroV1alphaLoginCredentialFieldHints
        include Google::Apis::Core::Hashable
      
        # Required. The Android resource name of the password UI element. For example,
        # in Java: R.string.foo in xml: @string/foo Only the "foo" part is needed.
        # Reference doc: https://developer.android.com/guide/topics/resources/accessing-
        # resources.html
        # Corresponds to the JSON property `passwordResourceName`
        # @return [String]
        attr_accessor :password_resource_name
      
        # Required. The Android resource name of the username UI element. For example,
        # in Java: R.string.foo in xml: @string/foo Only the "foo" part is needed.
        # Reference doc: https://developer.android.com/guide/topics/resources/accessing-
        # resources.html
        # Corresponds to the JSON property `usernameResourceName`
        # @return [String]
        attr_accessor :username_resource_name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @password_resource_name = args[:password_resource_name] if args.key?(:password_resource_name)
          @username_resource_name = args[:username_resource_name] if args.key?(:username_resource_name)
        end
      end
      
      # 
      class GoogleFirebaseAppdistroV1alphaProvisionAppResponse
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # Proto defining a release object
      class GoogleFirebaseAppdistroV1alphaRelease
        include Google::Apis::Core::Hashable
      
        # Release build version
        # Corresponds to the JSON property `buildVersion`
        # @return [String]
        attr_accessor :build_version
      
        # Release version
        # Corresponds to the JSON property `displayVersion`
        # @return [String]
        attr_accessor :display_version
      
        # Timestamp when the release was created
        # Corresponds to the JSON property `distributedAt`
        # @return [String]
        attr_accessor :distributed_at
      
        # Release Id
        # Corresponds to the JSON property `id`
        # @return [String]
        attr_accessor :id
      
        # Instance id of the release
        # Corresponds to the JSON property `instanceId`
        # @return [String]
        attr_accessor :instance_id
      
        # Last activity timestamp
        # Corresponds to the JSON property `lastActivityAt`
        # @return [String]
        attr_accessor :last_activity_at
      
        # Number of testers who have open invitations for the release
        # Corresponds to the JSON property `openInvitationCount`
        # @return [Fixnum]
        attr_accessor :open_invitation_count
      
        # unused.
        # Corresponds to the JSON property `receivedAt`
        # @return [String]
        attr_accessor :received_at
      
        # Release notes summary
        # Corresponds to the JSON property `releaseNotesSummary`
        # @return [String]
        attr_accessor :release_notes_summary
      
        # Count of testers added to the release
        # Corresponds to the JSON property `testerCount`
        # @return [Fixnum]
        attr_accessor :tester_count
      
        # Number of testers who have installed the release
        # Corresponds to the JSON property `testerWithInstallCount`
        # @return [Fixnum]
        attr_accessor :tester_with_install_count
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @build_version = args[:build_version] if args.key?(:build_version)
          @display_version = args[:display_version] if args.key?(:display_version)
          @distributed_at = args[:distributed_at] if args.key?(:distributed_at)
          @id = args[:id] if args.key?(:id)
          @instance_id = args[:instance_id] if args.key?(:instance_id)
          @last_activity_at = args[:last_activity_at] if args.key?(:last_activity_at)
          @open_invitation_count = args[:open_invitation_count] if args.key?(:open_invitation_count)
          @received_at = args[:received_at] if args.key?(:received_at)
          @release_notes_summary = args[:release_notes_summary] if args.key?(:release_notes_summary)
          @tester_count = args[:tester_count] if args.key?(:tester_count)
          @tester_with_install_count = args[:tester_with_install_count] if args.key?(:tester_with_install_count)
        end
      end
      
      # 
      class GoogleFirebaseAppdistroV1alphaReleaseNotes
        include Google::Apis::Core::Hashable
      
        # 
        # Corresponds to the JSON property `releaseNotes`
        # @return [String]
        attr_accessor :release_notes
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @release_notes = args[:release_notes] if args.key?(:release_notes)
        end
      end
      
      # The results of running an automated test on a release.
      class GoogleFirebaseAppdistroV1alphaReleaseTest
        include Google::Apis::Core::Hashable
      
        # Output only. Timestamp when the test was run.
        # Corresponds to the JSON property `createTime`
        # @return [String]
        attr_accessor :create_time
      
        # Required. The results of the test on each device.
        # Corresponds to the JSON property `deviceExecutions`
        # @return [Array<Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaDeviceExecution>]
        attr_accessor :device_executions
      
        # Login credential for automated tests
        # Corresponds to the JSON property `loginCredential`
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaLoginCredential]
        attr_accessor :login_credential
      
        # The name of the release test resource. Format: `projects/`project_number`/apps/
        # `app_id`/releases/`release_id`/tests/`test_id``
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @create_time = args[:create_time] if args.key?(:create_time)
          @device_executions = args[:device_executions] if args.key?(:device_executions)
          @login_credential = args[:login_credential] if args.key?(:login_credential)
          @name = args[:name] if args.key?(:name)
        end
      end
      
      # Configuration for Robo crawler
      class GoogleFirebaseAppdistroV1alphaRoboCrawler
        include Google::Apis::Core::Hashable
      
        # Login credential for automated tests
        # Corresponds to the JSON property `loginCredential`
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaLoginCredential]
        attr_accessor :login_credential
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @login_credential = args[:login_credential] if args.key?(:login_credential)
        end
      end
      
      # Statistics collected during a Robo test.
      class GoogleFirebaseAppdistroV1alphaRoboStats
        include Google::Apis::Core::Hashable
      
        # Output only. Number of actions that crawler performed.
        # Corresponds to the JSON property `actionsPerformed`
        # @return [Fixnum]
        attr_accessor :actions_performed
      
        # Output only. Duration of crawl.
        # Corresponds to the JSON property `crawlDuration`
        # @return [String]
        attr_accessor :crawl_duration
      
        # Output only. Number of distinct screens visited.
        # Corresponds to the JSON property `distinctVisitedScreens`
        # @return [Fixnum]
        attr_accessor :distinct_visited_screens
      
        # Output only. Whether the main activity crawl timed out.
        # Corresponds to the JSON property `mainActivityCrawlTimedOut`
        # @return [Boolean]
        attr_accessor :main_activity_crawl_timed_out
        alias_method :main_activity_crawl_timed_out?, :main_activity_crawl_timed_out
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @actions_performed = args[:actions_performed] if args.key?(:actions_performed)
          @crawl_duration = args[:crawl_duration] if args.key?(:crawl_duration)
          @distinct_visited_screens = args[:distinct_visited_screens] if args.key?(:distinct_visited_screens)
          @main_activity_crawl_timed_out = args[:main_activity_crawl_timed_out] if args.key?(:main_activity_crawl_timed_out)
        end
      end
      
      # Configuration for automated tests
      class GoogleFirebaseAppdistroV1alphaTestConfig
        include Google::Apis::Core::Hashable
      
        # Identifier. The name of the test configuration resource. Format: `projects/`
        # project_number`/apps/`app_id`/testConfig`
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        # Configuration for Robo crawler
        # Corresponds to the JSON property `roboCrawler`
        # @return [Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaRoboCrawler]
        attr_accessor :robo_crawler
      
        # Optional. Tests will be run on this list of devices
        # Corresponds to the JSON property `testDevices`
        # @return [Array<Google::Apis::FirebaseappdistributionV1alpha::GoogleFirebaseAppdistroV1alphaTestDevice>]
        attr_accessor :test_devices
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @name = args[:name] if args.key?(:name)
          @robo_crawler = args[:robo_crawler] if args.key?(:robo_crawler)
          @test_devices = args[:test_devices] if args.key?(:test_devices)
        end
      end
      
      # A device on which automated tests can be run.
      class GoogleFirebaseAppdistroV1alphaTestDevice
        include Google::Apis::Core::Hashable
      
        # Optional. The locale of the device (e.g. "en_US" for US English) during the
        # test.
        # Corresponds to the JSON property `locale`
        # @return [String]
        attr_accessor :locale
      
        # Required. The device model.
        # Corresponds to the JSON property `model`
        # @return [String]
        attr_accessor :model
      
        # Optional. The orientation of the device during the test.
        # Corresponds to the JSON property `orientation`
        # @return [String]
        attr_accessor :orientation
      
        # Required. The version of the device (API level on Android).
        # Corresponds to the JSON property `version`
        # @return [String]
        attr_accessor :version
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @locale = args[:locale] if args.key?(:locale)
          @model = args[:model] if args.key?(:model)
          @orientation = args[:orientation] if args.key?(:orientation)
          @version = args[:version] if args.key?(:version)
        end
      end
      
      # The UDIDs of a tester's iOS device
      class GoogleFirebaseAppdistroV1alphaTesterUdid
        include Google::Apis::Core::Hashable
      
        # The name of the tester's device
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        # The platform of the tester's device
        # Corresponds to the JSON property `platform`
        # @return [String]
        attr_accessor :platform
      
        # The UDID of the tester's device
        # Corresponds to the JSON property `udid`
        # @return [String]
        attr_accessor :udid
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @name = args[:name] if args.key?(:name)
          @platform = args[:platform] if args.key?(:platform)
          @udid = args[:udid] if args.key?(:udid)
        end
      end
    end
  end
end
