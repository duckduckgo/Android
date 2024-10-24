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
    module AndroidpublisherV3
      
      # Represents an Abi.
      class Abi
        include Google::Apis::Core::Hashable
      
        # Alias for an abi.
        # Corresponds to the JSON property `alias`
        # @return [String]
        attr_accessor :alias
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @alias = args[:alias] if args.key?(:alias)
        end
      end
      
      # Targeting based on Abi.
      class AbiTargeting
        include Google::Apis::Core::Hashable
      
        # Targeting of other sibling directories that were in the Bundle. For main
        # splits this is targeting of other main splits.
        # Corresponds to the JSON property `alternatives`
        # @return [Array<Google::Apis::AndroidpublisherV3::Abi>]
        attr_accessor :alternatives
      
        # Value of an abi.
        # Corresponds to the JSON property `value`
        # @return [Array<Google::Apis::AndroidpublisherV3::Abi>]
        attr_accessor :value
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @alternatives = args[:alternatives] if args.key?(:alternatives)
          @value = args[:value] if args.key?(:value)
        end
      end
      
      # Represents a targeting rule of the form: User never had `scope` before.
      class AcquisitionTargetingRule
        include Google::Apis::Core::Hashable
      
        # Defines the scope of subscriptions which a targeting rule can match to target
        # offers to users based on past or current entitlement.
        # Corresponds to the JSON property `scope`
        # @return [Google::Apis::AndroidpublisherV3::TargetingRuleScope]
        attr_accessor :scope
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @scope = args[:scope] if args.key?(:scope)
        end
      end
      
      # Request message for ActivateBasePlan.
      class ActivateBasePlanRequest
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # Request message for ActivateSubscriptionOffer.
      class ActivateSubscriptionOfferRequest
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # Information about an APK. The resource for ApksService.
      class Apk
        include Google::Apis::Core::Hashable
      
        # Represents the binary payload of an APK.
        # Corresponds to the JSON property `binary`
        # @return [Google::Apis::AndroidpublisherV3::ApkBinary]
        attr_accessor :binary
      
        # The version code of the APK, as specified in the manifest file.
        # Corresponds to the JSON property `versionCode`
        # @return [Fixnum]
        attr_accessor :version_code
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @binary = args[:binary] if args.key?(:binary)
          @version_code = args[:version_code] if args.key?(:version_code)
        end
      end
      
      # Represents the binary payload of an APK.
      class ApkBinary
        include Google::Apis::Core::Hashable
      
        # A sha1 hash of the APK payload, encoded as a hex string and matching the
        # output of the sha1sum command.
        # Corresponds to the JSON property `sha1`
        # @return [String]
        attr_accessor :sha1
      
        # A sha256 hash of the APK payload, encoded as a hex string and matching the
        # output of the sha256sum command.
        # Corresponds to the JSON property `sha256`
        # @return [String]
        attr_accessor :sha256
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @sha1 = args[:sha1] if args.key?(:sha1)
          @sha256 = args[:sha256] if args.key?(:sha256)
        end
      end
      
      # Description of the created apks.
      class ApkDescription
        include Google::Apis::Core::Hashable
      
        # Holds data specific to Split APKs.
        # Corresponds to the JSON property `assetSliceMetadata`
        # @return [Google::Apis::AndroidpublisherV3::SplitApkMetadata]
        attr_accessor :asset_slice_metadata
      
        # Holds data specific to Split APKs.
        # Corresponds to the JSON property `instantApkMetadata`
        # @return [Google::Apis::AndroidpublisherV3::SplitApkMetadata]
        attr_accessor :instant_apk_metadata
      
        # Path of the Apk, will be in the following format: .apk where DownloadId is the
        # ID used to download the apk using GeneratedApks.Download API.
        # Corresponds to the JSON property `path`
        # @return [String]
        attr_accessor :path
      
        # Holds data specific to Split APKs.
        # Corresponds to the JSON property `splitApkMetadata`
        # @return [Google::Apis::AndroidpublisherV3::SplitApkMetadata]
        attr_accessor :split_apk_metadata
      
        # Holds data specific to Standalone APKs.
        # Corresponds to the JSON property `standaloneApkMetadata`
        # @return [Google::Apis::AndroidpublisherV3::StandaloneApkMetadata]
        attr_accessor :standalone_apk_metadata
      
        # Represents a set of apk-level targetings.
        # Corresponds to the JSON property `targeting`
        # @return [Google::Apis::AndroidpublisherV3::ApkTargeting]
        attr_accessor :targeting
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @asset_slice_metadata = args[:asset_slice_metadata] if args.key?(:asset_slice_metadata)
          @instant_apk_metadata = args[:instant_apk_metadata] if args.key?(:instant_apk_metadata)
          @path = args[:path] if args.key?(:path)
          @split_apk_metadata = args[:split_apk_metadata] if args.key?(:split_apk_metadata)
          @standalone_apk_metadata = args[:standalone_apk_metadata] if args.key?(:standalone_apk_metadata)
          @targeting = args[:targeting] if args.key?(:targeting)
        end
      end
      
      # A set of apks representing a module.
      class ApkSet
        include Google::Apis::Core::Hashable
      
        # Description of the generated apks.
        # Corresponds to the JSON property `apkDescription`
        # @return [Array<Google::Apis::AndroidpublisherV3::ApkDescription>]
        attr_accessor :apk_description
      
        # Metadata of a module.
        # Corresponds to the JSON property `moduleMetadata`
        # @return [Google::Apis::AndroidpublisherV3::ModuleMetadata]
        attr_accessor :module_metadata
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @apk_description = args[:apk_description] if args.key?(:apk_description)
          @module_metadata = args[:module_metadata] if args.key?(:module_metadata)
        end
      end
      
      # Represents a set of apk-level targetings.
      class ApkTargeting
        include Google::Apis::Core::Hashable
      
        # Targeting based on Abi.
        # Corresponds to the JSON property `abiTargeting`
        # @return [Google::Apis::AndroidpublisherV3::AbiTargeting]
        attr_accessor :abi_targeting
      
        # Targeting based on language.
        # Corresponds to the JSON property `languageTargeting`
        # @return [Google::Apis::AndroidpublisherV3::LanguageTargeting]
        attr_accessor :language_targeting
      
        # Targeting based on multiple abis.
        # Corresponds to the JSON property `multiAbiTargeting`
        # @return [Google::Apis::AndroidpublisherV3::MultiAbiTargeting]
        attr_accessor :multi_abi_targeting
      
        # Targeting based on screen density.
        # Corresponds to the JSON property `screenDensityTargeting`
        # @return [Google::Apis::AndroidpublisherV3::ScreenDensityTargeting]
        attr_accessor :screen_density_targeting
      
        # Targeting based on sdk version.
        # Corresponds to the JSON property `sdkVersionTargeting`
        # @return [Google::Apis::AndroidpublisherV3::SdkVersionTargeting]
        attr_accessor :sdk_version_targeting
      
        # Targeting by a texture compression format.
        # Corresponds to the JSON property `textureCompressionFormatTargeting`
        # @return [Google::Apis::AndroidpublisherV3::TextureCompressionFormatTargeting]
        attr_accessor :texture_compression_format_targeting
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @abi_targeting = args[:abi_targeting] if args.key?(:abi_targeting)
          @language_targeting = args[:language_targeting] if args.key?(:language_targeting)
          @multi_abi_targeting = args[:multi_abi_targeting] if args.key?(:multi_abi_targeting)
          @screen_density_targeting = args[:screen_density_targeting] if args.key?(:screen_density_targeting)
          @sdk_version_targeting = args[:sdk_version_targeting] if args.key?(:sdk_version_targeting)
          @texture_compression_format_targeting = args[:texture_compression_format_targeting] if args.key?(:texture_compression_format_targeting)
        end
      end
      
      # Request to create a new externally hosted APK.
      class ApksAddExternallyHostedRequest
        include Google::Apis::Core::Hashable
      
        # Defines an APK available for this application that is hosted externally and
        # not uploaded to Google Play. This function is only available to organizations
        # using Managed Play whose application is configured to restrict distribution to
        # the organizations.
        # Corresponds to the JSON property `externallyHostedApk`
        # @return [Google::Apis::AndroidpublisherV3::ExternallyHostedApk]
        attr_accessor :externally_hosted_apk
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @externally_hosted_apk = args[:externally_hosted_apk] if args.key?(:externally_hosted_apk)
        end
      end
      
      # Response for creating a new externally hosted APK.
      class ApksAddExternallyHostedResponse
        include Google::Apis::Core::Hashable
      
        # Defines an APK available for this application that is hosted externally and
        # not uploaded to Google Play. This function is only available to organizations
        # using Managed Play whose application is configured to restrict distribution to
        # the organizations.
        # Corresponds to the JSON property `externallyHostedApk`
        # @return [Google::Apis::AndroidpublisherV3::ExternallyHostedApk]
        attr_accessor :externally_hosted_apk
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @externally_hosted_apk = args[:externally_hosted_apk] if args.key?(:externally_hosted_apk)
        end
      end
      
      # Response listing all APKs.
      class ApksListResponse
        include Google::Apis::Core::Hashable
      
        # All APKs.
        # Corresponds to the JSON property `apks`
        # @return [Array<Google::Apis::AndroidpublisherV3::Apk>]
        attr_accessor :apks
      
        # The kind of this response ("androidpublisher#apksListResponse").
        # Corresponds to the JSON property `kind`
        # @return [String]
        attr_accessor :kind
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @apks = args[:apks] if args.key?(:apks)
          @kind = args[:kind] if args.key?(:kind)
        end
      end
      
      # The app details. The resource for DetailsService.
      class AppDetails
        include Google::Apis::Core::Hashable
      
        # The user-visible support email for this app.
        # Corresponds to the JSON property `contactEmail`
        # @return [String]
        attr_accessor :contact_email
      
        # The user-visible support telephone number for this app.
        # Corresponds to the JSON property `contactPhone`
        # @return [String]
        attr_accessor :contact_phone
      
        # The user-visible website for this app.
        # Corresponds to the JSON property `contactWebsite`
        # @return [String]
        attr_accessor :contact_website
      
        # Default language code, in BCP 47 format (eg "en-US").
        # Corresponds to the JSON property `defaultLanguage`
        # @return [String]
        attr_accessor :default_language
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @contact_email = args[:contact_email] if args.key?(:contact_email)
          @contact_phone = args[:contact_phone] if args.key?(:contact_phone)
          @contact_website = args[:contact_website] if args.key?(:contact_website)
          @default_language = args[:default_language] if args.key?(:default_language)
        end
      end
      
      # An app edit. The resource for EditsService.
      class AppEdit
        include Google::Apis::Core::Hashable
      
        # Output only. The time (as seconds since Epoch) at which the edit will expire
        # and will be no longer valid for use.
        # Corresponds to the JSON property `expiryTimeSeconds`
        # @return [String]
        attr_accessor :expiry_time_seconds
      
        # Output only. Identifier of the edit. Can be used in subsequent API calls.
        # Corresponds to the JSON property `id`
        # @return [String]
        attr_accessor :id
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @expiry_time_seconds = args[:expiry_time_seconds] if args.key?(:expiry_time_seconds)
          @id = args[:id] if args.key?(:id)
        end
      end
      
      # Request message for ArchiveSubscription.
      class ArchiveSubscriptionRequest
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # Metadata of an asset module.
      class AssetModuleMetadata
        include Google::Apis::Core::Hashable
      
        # Indicates the delivery type for persistent install.
        # Corresponds to the JSON property `deliveryType`
        # @return [String]
        attr_accessor :delivery_type
      
        # Module name.
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @delivery_type = args[:delivery_type] if args.key?(:delivery_type)
          @name = args[:name] if args.key?(:name)
        end
      end
      
      # Set of asset slices belonging to a single asset module.
      class AssetSliceSet
        include Google::Apis::Core::Hashable
      
        # Asset slices.
        # Corresponds to the JSON property `apkDescription`
        # @return [Array<Google::Apis::AndroidpublisherV3::ApkDescription>]
        attr_accessor :apk_description
      
        # Metadata of an asset module.
        # Corresponds to the JSON property `assetModuleMetadata`
        # @return [Google::Apis::AndroidpublisherV3::AssetModuleMetadata]
        attr_accessor :asset_module_metadata
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @apk_description = args[:apk_description] if args.key?(:apk_description)
          @asset_module_metadata = args[:asset_module_metadata] if args.key?(:asset_module_metadata)
        end
      end
      
      # Represents a base plan that automatically renews at the end of its
      # subscription period.
      class AutoRenewingBasePlanType
        include Google::Apis::Core::Hashable
      
        # Required. Subscription period, specified in ISO 8601 format. For a list of
        # acceptable billing periods, refer to the help center.
        # Corresponds to the JSON property `billingPeriodDuration`
        # @return [String]
        attr_accessor :billing_period_duration
      
        # Grace period of the subscription, specified in ISO 8601 format. Acceptable
        # values are P0D (zero days), P3D (3 days), P7D (7 days), P14D (14 days), and
        # P30D (30 days). If not specified, a default value will be used based on the
        # recurring period duration.
        # Corresponds to the JSON property `gracePeriodDuration`
        # @return [String]
        attr_accessor :grace_period_duration
      
        # Whether the renewing base plan is backward compatible. The backward compatible
        # base plan is returned by the Google Play Billing Library deprecated method
        # querySkuDetailsAsync(). Only one renewing base plan can be marked as legacy
        # compatible for a given subscription.
        # Corresponds to the JSON property `legacyCompatible`
        # @return [Boolean]
        attr_accessor :legacy_compatible
        alias_method :legacy_compatible?, :legacy_compatible
      
        # Subscription offer id which is legacy compatible. The backward compatible
        # subscription offer is returned by the Google Play Billing Library deprecated
        # method querySkuDetailsAsync(). Only one subscription offer can be marked as
        # legacy compatible for a given renewing base plan. To have no Subscription
        # offer as legacy compatible set this field as empty string.
        # Corresponds to the JSON property `legacyCompatibleSubscriptionOfferId`
        # @return [String]
        attr_accessor :legacy_compatible_subscription_offer_id
      
        # The proration mode for the base plan determines what happens when a user
        # switches to this plan from another base plan. If unspecified, defaults to
        # CHARGE_ON_NEXT_BILLING_DATE.
        # Corresponds to the JSON property `prorationMode`
        # @return [String]
        attr_accessor :proration_mode
      
        # Whether users should be able to resubscribe to this base plan in Google Play
        # surfaces. Defaults to RESUBSCRIBE_STATE_ACTIVE if not specified.
        # Corresponds to the JSON property `resubscribeState`
        # @return [String]
        attr_accessor :resubscribe_state
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @billing_period_duration = args[:billing_period_duration] if args.key?(:billing_period_duration)
          @grace_period_duration = args[:grace_period_duration] if args.key?(:grace_period_duration)
          @legacy_compatible = args[:legacy_compatible] if args.key?(:legacy_compatible)
          @legacy_compatible_subscription_offer_id = args[:legacy_compatible_subscription_offer_id] if args.key?(:legacy_compatible_subscription_offer_id)
          @proration_mode = args[:proration_mode] if args.key?(:proration_mode)
          @resubscribe_state = args[:resubscribe_state] if args.key?(:resubscribe_state)
        end
      end
      
      # Information related to an auto renewing plan.
      class AutoRenewingPlan
        include Google::Apis::Core::Hashable
      
        # If the subscription is currently set to auto-renew, e.g. the user has not
        # canceled the subscription
        # Corresponds to the JSON property `autoRenewEnabled`
        # @return [Boolean]
        attr_accessor :auto_renew_enabled
        alias_method :auto_renew_enabled?, :auto_renew_enabled
      
        # Price change related information of a subscription item.
        # Corresponds to the JSON property `priceChangeDetails`
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionItemPriceChangeDetails]
        attr_accessor :price_change_details
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @auto_renew_enabled = args[:auto_renew_enabled] if args.key?(:auto_renew_enabled)
          @price_change_details = args[:price_change_details] if args.key?(:price_change_details)
        end
      end
      
      # A single base plan for a subscription.
      class BasePlan
        include Google::Apis::Core::Hashable
      
        # Represents a base plan that automatically renews at the end of its
        # subscription period.
        # Corresponds to the JSON property `autoRenewingBasePlanType`
        # @return [Google::Apis::AndroidpublisherV3::AutoRenewingBasePlanType]
        attr_accessor :auto_renewing_base_plan_type
      
        # Required. Immutable. The unique identifier of this base plan. Must be unique
        # within the subscription, and conform with RFC-1034. That is, this ID can only
        # contain lower-case letters (a-z), numbers (0-9), and hyphens (-), and be at
        # most 63 characters.
        # Corresponds to the JSON property `basePlanId`
        # @return [String]
        attr_accessor :base_plan_id
      
        # List of up to 20 custom tags specified for this base plan, and returned to the
        # app through the billing library. Subscription offers for this base plan will
        # also receive these offer tags in the billing library.
        # Corresponds to the JSON property `offerTags`
        # @return [Array<Google::Apis::AndroidpublisherV3::OfferTag>]
        attr_accessor :offer_tags
      
        # Pricing information for any new locations Play may launch in.
        # Corresponds to the JSON property `otherRegionsConfig`
        # @return [Google::Apis::AndroidpublisherV3::OtherRegionsBasePlanConfig]
        attr_accessor :other_regions_config
      
        # Represents a base plan that does not automatically renew at the end of the
        # base plan, and must be manually renewed by the user.
        # Corresponds to the JSON property `prepaidBasePlanType`
        # @return [Google::Apis::AndroidpublisherV3::PrepaidBasePlanType]
        attr_accessor :prepaid_base_plan_type
      
        # Region-specific information for this base plan.
        # Corresponds to the JSON property `regionalConfigs`
        # @return [Array<Google::Apis::AndroidpublisherV3::RegionalBasePlanConfig>]
        attr_accessor :regional_configs
      
        # Output only. The state of the base plan, i.e. whether it's active. Draft and
        # inactive base plans can be activated or deleted. Active base plans can be made
        # inactive. Inactive base plans can be canceled. This field cannot be changed by
        # updating the resource. Use the dedicated endpoints instead.
        # Corresponds to the JSON property `state`
        # @return [String]
        attr_accessor :state
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @auto_renewing_base_plan_type = args[:auto_renewing_base_plan_type] if args.key?(:auto_renewing_base_plan_type)
          @base_plan_id = args[:base_plan_id] if args.key?(:base_plan_id)
          @offer_tags = args[:offer_tags] if args.key?(:offer_tags)
          @other_regions_config = args[:other_regions_config] if args.key?(:other_regions_config)
          @prepaid_base_plan_type = args[:prepaid_base_plan_type] if args.key?(:prepaid_base_plan_type)
          @regional_configs = args[:regional_configs] if args.key?(:regional_configs)
          @state = args[:state] if args.key?(:state)
        end
      end
      
      # Information about an app bundle. The resource for BundlesService.
      class Bundle
        include Google::Apis::Core::Hashable
      
        # A sha1 hash of the upload payload, encoded as a hex string and matching the
        # output of the sha1sum command.
        # Corresponds to the JSON property `sha1`
        # @return [String]
        attr_accessor :sha1
      
        # A sha256 hash of the upload payload, encoded as a hex string and matching the
        # output of the sha256sum command.
        # Corresponds to the JSON property `sha256`
        # @return [String]
        attr_accessor :sha256
      
        # The version code of the Android App Bundle, as specified in the Android App
        # Bundle's base module APK manifest file.
        # Corresponds to the JSON property `versionCode`
        # @return [Fixnum]
        attr_accessor :version_code
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @sha1 = args[:sha1] if args.key?(:sha1)
          @sha256 = args[:sha256] if args.key?(:sha256)
          @version_code = args[:version_code] if args.key?(:version_code)
        end
      end
      
      # Response listing all app bundles.
      class BundlesListResponse
        include Google::Apis::Core::Hashable
      
        # All app bundles.
        # Corresponds to the JSON property `bundles`
        # @return [Array<Google::Apis::AndroidpublisherV3::Bundle>]
        attr_accessor :bundles
      
        # The kind of this response ("androidpublisher#bundlesListResponse").
        # Corresponds to the JSON property `kind`
        # @return [String]
        attr_accessor :kind
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @bundles = args[:bundles] if args.key?(:bundles)
          @kind = args[:kind] if args.key?(:kind)
        end
      end
      
      # Result of the cancel survey when the subscription was canceled by the user.
      class CancelSurveyResult
        include Google::Apis::Core::Hashable
      
        # The reason the user selected in the cancel survey.
        # Corresponds to the JSON property `reason`
        # @return [String]
        attr_accessor :reason
      
        # Only set for CANCEL_SURVEY_REASON_OTHERS. This is the user's freeform response
        # to the survey.
        # Corresponds to the JSON property `reasonUserInput`
        # @return [String]
        attr_accessor :reason_user_input
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @reason = args[:reason] if args.key?(:reason)
          @reason_user_input = args[:reason_user_input] if args.key?(:reason_user_input)
        end
      end
      
      # Information specific to a subscription in canceled state.
      class CanceledStateContext
        include Google::Apis::Core::Hashable
      
        # Information specific to cancellations initiated by developers.
        # Corresponds to the JSON property `developerInitiatedCancellation`
        # @return [Google::Apis::AndroidpublisherV3::DeveloperInitiatedCancellation]
        attr_accessor :developer_initiated_cancellation
      
        # Information specific to cancellations caused by subscription replacement.
        # Corresponds to the JSON property `replacementCancellation`
        # @return [Google::Apis::AndroidpublisherV3::ReplacementCancellation]
        attr_accessor :replacement_cancellation
      
        # Information specific to cancellations initiated by Google system.
        # Corresponds to the JSON property `systemInitiatedCancellation`
        # @return [Google::Apis::AndroidpublisherV3::SystemInitiatedCancellation]
        attr_accessor :system_initiated_cancellation
      
        # Information specific to cancellations initiated by users.
        # Corresponds to the JSON property `userInitiatedCancellation`
        # @return [Google::Apis::AndroidpublisherV3::UserInitiatedCancellation]
        attr_accessor :user_initiated_cancellation
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @developer_initiated_cancellation = args[:developer_initiated_cancellation] if args.key?(:developer_initiated_cancellation)
          @replacement_cancellation = args[:replacement_cancellation] if args.key?(:replacement_cancellation)
          @system_initiated_cancellation = args[:system_initiated_cancellation] if args.key?(:system_initiated_cancellation)
          @user_initiated_cancellation = args[:user_initiated_cancellation] if args.key?(:user_initiated_cancellation)
        end
      end
      
      # An entry of conversation between user and developer.
      class Comment
        include Google::Apis::Core::Hashable
      
        # Developer entry from conversation between user and developer.
        # Corresponds to the JSON property `developerComment`
        # @return [Google::Apis::AndroidpublisherV3::DeveloperComment]
        attr_accessor :developer_comment
      
        # User entry from conversation between user and developer.
        # Corresponds to the JSON property `userComment`
        # @return [Google::Apis::AndroidpublisherV3::UserComment]
        attr_accessor :user_comment
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @developer_comment = args[:developer_comment] if args.key?(:developer_comment)
          @user_comment = args[:user_comment] if args.key?(:user_comment)
        end
      end
      
      # Request message for ConvertRegionPrices.
      class ConvertRegionPricesRequest
        include Google::Apis::Core::Hashable
      
        # Represents an amount of money with its currency type.
        # Corresponds to the JSON property `price`
        # @return [Google::Apis::AndroidpublisherV3::Money]
        attr_accessor :price
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @price = args[:price] if args.key?(:price)
        end
      end
      
      # Response message for ConvertRegionPrices.
      class ConvertRegionPricesResponse
        include Google::Apis::Core::Hashable
      
        # Converted other regions prices.
        # Corresponds to the JSON property `convertedOtherRegionsPrice`
        # @return [Google::Apis::AndroidpublisherV3::ConvertedOtherRegionsPrice]
        attr_accessor :converted_other_regions_price
      
        # Map from region code to converted region price.
        # Corresponds to the JSON property `convertedRegionPrices`
        # @return [Hash<String,Google::Apis::AndroidpublisherV3::ConvertedRegionPrice>]
        attr_accessor :converted_region_prices
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @converted_other_regions_price = args[:converted_other_regions_price] if args.key?(:converted_other_regions_price)
          @converted_region_prices = args[:converted_region_prices] if args.key?(:converted_region_prices)
        end
      end
      
      # Converted other regions prices.
      class ConvertedOtherRegionsPrice
        include Google::Apis::Core::Hashable
      
        # Represents an amount of money with its currency type.
        # Corresponds to the JSON property `eurPrice`
        # @return [Google::Apis::AndroidpublisherV3::Money]
        attr_accessor :eur_price
      
        # Represents an amount of money with its currency type.
        # Corresponds to the JSON property `usdPrice`
        # @return [Google::Apis::AndroidpublisherV3::Money]
        attr_accessor :usd_price
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @eur_price = args[:eur_price] if args.key?(:eur_price)
          @usd_price = args[:usd_price] if args.key?(:usd_price)
        end
      end
      
      # A converted region price.
      class ConvertedRegionPrice
        include Google::Apis::Core::Hashable
      
        # Represents an amount of money with its currency type.
        # Corresponds to the JSON property `price`
        # @return [Google::Apis::AndroidpublisherV3::Money]
        attr_accessor :price
      
        # The region code of the region.
        # Corresponds to the JSON property `regionCode`
        # @return [String]
        attr_accessor :region_code
      
        # Represents an amount of money with its currency type.
        # Corresponds to the JSON property `taxAmount`
        # @return [Google::Apis::AndroidpublisherV3::Money]
        attr_accessor :tax_amount
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @price = args[:price] if args.key?(:price)
          @region_code = args[:region_code] if args.key?(:region_code)
          @tax_amount = args[:tax_amount] if args.key?(:tax_amount)
        end
      end
      
      # Country targeting specification.
      class CountryTargeting
        include Google::Apis::Core::Hashable
      
        # Countries to target, specified as two letter [CLDR codes](https://unicode.org/
        # cldr/charts/latest/supplemental/territory_containment_un_m_49.html).
        # Corresponds to the JSON property `countries`
        # @return [Array<String>]
        attr_accessor :countries
      
        # Include "rest of world" as well as explicitly targeted countries.
        # Corresponds to the JSON property `includeRestOfWorld`
        # @return [Boolean]
        attr_accessor :include_rest_of_world
        alias_method :include_rest_of_world?, :include_rest_of_world
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @countries = args[:countries] if args.key?(:countries)
          @include_rest_of_world = args[:include_rest_of_world] if args.key?(:include_rest_of_world)
        end
      end
      
      # Request message for DeactivateBasePlan.
      class DeactivateBasePlanRequest
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # Request message for DeactivateSubscriptionOffer.
      class DeactivateSubscriptionOfferRequest
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # Information related to deferred item replacement.
      class DeferredItemReplacement
        include Google::Apis::Core::Hashable
      
        # The product_id going to replace the existing product_id.
        # Corresponds to the JSON property `productId`
        # @return [String]
        attr_accessor :product_id
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @product_id = args[:product_id] if args.key?(:product_id)
        end
      end
      
      # Represents a deobfuscation file.
      class DeobfuscationFile
        include Google::Apis::Core::Hashable
      
        # The type of the deobfuscation file.
        # Corresponds to the JSON property `symbolType`
        # @return [String]
        attr_accessor :symbol_type
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @symbol_type = args[:symbol_type] if args.key?(:symbol_type)
        end
      end
      
      # Responses for the upload.
      class DeobfuscationFilesUploadResponse
        include Google::Apis::Core::Hashable
      
        # Represents a deobfuscation file.
        # Corresponds to the JSON property `deobfuscationFile`
        # @return [Google::Apis::AndroidpublisherV3::DeobfuscationFile]
        attr_accessor :deobfuscation_file
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @deobfuscation_file = args[:deobfuscation_file] if args.key?(:deobfuscation_file)
        end
      end
      
      # Developer entry from conversation between user and developer.
      class DeveloperComment
        include Google::Apis::Core::Hashable
      
        # A Timestamp represents a point in time independent of any time zone or local
        # calendar, encoded as a count of seconds and fractions of seconds at nanosecond
        # resolution. The count is relative to an epoch at UTC midnight on January 1,
        # 1970.
        # Corresponds to the JSON property `lastModified`
        # @return [Google::Apis::AndroidpublisherV3::Timestamp]
        attr_accessor :last_modified
      
        # The content of the comment, i.e. reply body.
        # Corresponds to the JSON property `text`
        # @return [String]
        attr_accessor :text
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @last_modified = args[:last_modified] if args.key?(:last_modified)
          @text = args[:text] if args.key?(:text)
        end
      end
      
      # Information specific to cancellations initiated by developers.
      class DeveloperInitiatedCancellation
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # Represents a device feature.
      class DeviceFeature
        include Google::Apis::Core::Hashable
      
        # Name of the feature.
        # Corresponds to the JSON property `featureName`
        # @return [String]
        attr_accessor :feature_name
      
        # The feature version specified by android:glEsVersion or android:version in in
        # the AndroidManifest.
        # Corresponds to the JSON property `featureVersion`
        # @return [Fixnum]
        attr_accessor :feature_version
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @feature_name = args[:feature_name] if args.key?(:feature_name)
          @feature_version = args[:feature_version] if args.key?(:feature_version)
        end
      end
      
      # Targeting for a device feature.
      class DeviceFeatureTargeting
        include Google::Apis::Core::Hashable
      
        # Represents a device feature.
        # Corresponds to the JSON property `requiredFeature`
        # @return [Google::Apis::AndroidpublisherV3::DeviceFeature]
        attr_accessor :required_feature
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @required_feature = args[:required_feature] if args.key?(:required_feature)
        end
      end
      
      # A group of devices. A group is defined by a set of device selectors. A device
      # belongs to the group if it matches any selector (logical OR).
      class DeviceGroup
        include Google::Apis::Core::Hashable
      
        # Device selectors for this group. A device matching any of the selectors is
        # included in this group.
        # Corresponds to the JSON property `deviceSelectors`
        # @return [Array<Google::Apis::AndroidpublisherV3::DeviceSelector>]
        attr_accessor :device_selectors
      
        # The name of the group.
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @device_selectors = args[:device_selectors] if args.key?(:device_selectors)
          @name = args[:name] if args.key?(:name)
        end
      end
      
      # Identifier of a device.
      class DeviceId
        include Google::Apis::Core::Hashable
      
        # Value of Build.BRAND.
        # Corresponds to the JSON property `buildBrand`
        # @return [String]
        attr_accessor :build_brand
      
        # Value of Build.DEVICE.
        # Corresponds to the JSON property `buildDevice`
        # @return [String]
        attr_accessor :build_device
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @build_brand = args[:build_brand] if args.key?(:build_brand)
          @build_device = args[:build_device] if args.key?(:build_device)
        end
      end
      
      # Characteristics of the user's device.
      class DeviceMetadata
        include Google::Apis::Core::Hashable
      
        # Device CPU make, e.g. "Qualcomm"
        # Corresponds to the JSON property `cpuMake`
        # @return [String]
        attr_accessor :cpu_make
      
        # Device CPU model, e.g. "MSM8974"
        # Corresponds to the JSON property `cpuModel`
        # @return [String]
        attr_accessor :cpu_model
      
        # Device class (e.g. tablet)
        # Corresponds to the JSON property `deviceClass`
        # @return [String]
        attr_accessor :device_class
      
        # OpenGL version
        # Corresponds to the JSON property `glEsVersion`
        # @return [Fixnum]
        attr_accessor :gl_es_version
      
        # Device manufacturer (e.g. Motorola)
        # Corresponds to the JSON property `manufacturer`
        # @return [String]
        attr_accessor :manufacturer
      
        # Comma separated list of native platforms (e.g. "arm", "arm7")
        # Corresponds to the JSON property `nativePlatform`
        # @return [String]
        attr_accessor :native_platform
      
        # Device model name (e.g. Droid)
        # Corresponds to the JSON property `productName`
        # @return [String]
        attr_accessor :product_name
      
        # Device RAM in Megabytes, e.g. "2048"
        # Corresponds to the JSON property `ramMb`
        # @return [Fixnum]
        attr_accessor :ram_mb
      
        # Screen density in DPI
        # Corresponds to the JSON property `screenDensityDpi`
        # @return [Fixnum]
        attr_accessor :screen_density_dpi
      
        # Screen height in pixels
        # Corresponds to the JSON property `screenHeightPx`
        # @return [Fixnum]
        attr_accessor :screen_height_px
      
        # Screen width in pixels
        # Corresponds to the JSON property `screenWidthPx`
        # @return [Fixnum]
        attr_accessor :screen_width_px
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @cpu_make = args[:cpu_make] if args.key?(:cpu_make)
          @cpu_model = args[:cpu_model] if args.key?(:cpu_model)
          @device_class = args[:device_class] if args.key?(:device_class)
          @gl_es_version = args[:gl_es_version] if args.key?(:gl_es_version)
          @manufacturer = args[:manufacturer] if args.key?(:manufacturer)
          @native_platform = args[:native_platform] if args.key?(:native_platform)
          @product_name = args[:product_name] if args.key?(:product_name)
          @ram_mb = args[:ram_mb] if args.key?(:ram_mb)
          @screen_density_dpi = args[:screen_density_dpi] if args.key?(:screen_density_dpi)
          @screen_height_px = args[:screen_height_px] if args.key?(:screen_height_px)
          @screen_width_px = args[:screen_width_px] if args.key?(:screen_width_px)
        end
      end
      
      # Conditions about a device's RAM capabilities.
      class DeviceRam
        include Google::Apis::Core::Hashable
      
        # Maximum RAM in bytes (bound excluded).
        # Corresponds to the JSON property `maxBytes`
        # @return [Fixnum]
        attr_accessor :max_bytes
      
        # Minimum RAM in bytes (bound included).
        # Corresponds to the JSON property `minBytes`
        # @return [Fixnum]
        attr_accessor :min_bytes
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @max_bytes = args[:max_bytes] if args.key?(:max_bytes)
          @min_bytes = args[:min_bytes] if args.key?(:min_bytes)
        end
      end
      
      # Selector for a device group. A selector consists of a set of conditions on the
      # device that should all match (logical AND) to determine a device group
      # eligibility. For instance, if a selector specifies RAM conditions, device
      # model inclusion and device model exclusion, a device is considered to match if:
      # device matches RAM conditions AND device matches one of the included device
      # models AND device doesn't match excluded device models
      class DeviceSelector
        include Google::Apis::Core::Hashable
      
        # Conditions about a device's RAM capabilities.
        # Corresponds to the JSON property `deviceRam`
        # @return [Google::Apis::AndroidpublisherV3::DeviceRam]
        attr_accessor :device_ram
      
        # Device models excluded by this selector, even if they match all other
        # conditions.
        # Corresponds to the JSON property `excludedDeviceIds`
        # @return [Array<Google::Apis::AndroidpublisherV3::DeviceId>]
        attr_accessor :excluded_device_ids
      
        # A device that has any of these system features is excluded by this selector,
        # even if it matches all other conditions.
        # Corresponds to the JSON property `forbiddenSystemFeatures`
        # @return [Array<Google::Apis::AndroidpublisherV3::SystemFeature>]
        attr_accessor :forbidden_system_features
      
        # Device models included by this selector.
        # Corresponds to the JSON property `includedDeviceIds`
        # @return [Array<Google::Apis::AndroidpublisherV3::DeviceId>]
        attr_accessor :included_device_ids
      
        # A device needs to have all these system features to be included by the
        # selector.
        # Corresponds to the JSON property `requiredSystemFeatures`
        # @return [Array<Google::Apis::AndroidpublisherV3::SystemFeature>]
        attr_accessor :required_system_features
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @device_ram = args[:device_ram] if args.key?(:device_ram)
          @excluded_device_ids = args[:excluded_device_ids] if args.key?(:excluded_device_ids)
          @forbidden_system_features = args[:forbidden_system_features] if args.key?(:forbidden_system_features)
          @included_device_ids = args[:included_device_ids] if args.key?(:included_device_ids)
          @required_system_features = args[:required_system_features] if args.key?(:required_system_features)
        end
      end
      
      # The device spec used to generate a system APK.
      class DeviceSpec
        include Google::Apis::Core::Hashable
      
        # Screen dpi.
        # Corresponds to the JSON property `screenDensity`
        # @return [Fixnum]
        attr_accessor :screen_density
      
        # Supported ABI architectures in the order of preference. The values should be
        # the string as reported by the platform, e.g. "armeabi-v7a", "x86_64".
        # Corresponds to the JSON property `supportedAbis`
        # @return [Array<String>]
        attr_accessor :supported_abis
      
        # All installed locales represented as BCP-47 strings, e.g. "en-US".
        # Corresponds to the JSON property `supportedLocales`
        # @return [Array<String>]
        attr_accessor :supported_locales
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @screen_density = args[:screen_density] if args.key?(:screen_density)
          @supported_abis = args[:supported_abis] if args.key?(:supported_abis)
          @supported_locales = args[:supported_locales] if args.key?(:supported_locales)
        end
      end
      
      # A single device tier. Devices matching any of the device groups in
      # device_group_names are considered to match the tier.
      class DeviceTier
        include Google::Apis::Core::Hashable
      
        # Groups of devices included in this tier. These groups must be defined
        # explicitly under device_groups in this configuration.
        # Corresponds to the JSON property `deviceGroupNames`
        # @return [Array<String>]
        attr_accessor :device_group_names
      
        # The priority level of the tier. Tiers are evaluated in descending order of
        # level: the highest level tier has the highest priority. The highest tier
        # matching a given device is selected for that device. You should use a
        # contiguous range of levels for your tiers in a tier set; tier levels in a tier
        # set must be unique. For instance, if your tier set has 4 tiers (including the
        # global fallback), you should define tiers 1, 2 and 3 in this configuration.
        # Note: tier 0 is implicitly defined as a global fallback and selected for
        # devices that don't match any of the tiers explicitly defined here. You mustn't
        # define level 0 explicitly in this configuration.
        # Corresponds to the JSON property `level`
        # @return [Fixnum]
        attr_accessor :level
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @device_group_names = args[:device_group_names] if args.key?(:device_group_names)
          @level = args[:level] if args.key?(:level)
        end
      end
      
      # Configuration describing device targeting criteria for the content of an app.
      class DeviceTierConfig
        include Google::Apis::Core::Hashable
      
        # Definition of device groups for the app.
        # Corresponds to the JSON property `deviceGroups`
        # @return [Array<Google::Apis::AndroidpublisherV3::DeviceGroup>]
        attr_accessor :device_groups
      
        # Output only. The device tier config ID.
        # Corresponds to the JSON property `deviceTierConfigId`
        # @return [Fixnum]
        attr_accessor :device_tier_config_id
      
        # A set of device tiers. A tier set determines what variation of app content
        # gets served to a specific device, for device-targeted content. You should
        # assign a priority level to each tier, which determines the ordering by which
        # they are evaluated by Play. See the documentation of DeviceTier.level for more
        # details.
        # Corresponds to the JSON property `deviceTierSet`
        # @return [Google::Apis::AndroidpublisherV3::DeviceTierSet]
        attr_accessor :device_tier_set
      
        # Definition of user country sets for the app.
        # Corresponds to the JSON property `userCountrySets`
        # @return [Array<Google::Apis::AndroidpublisherV3::UserCountrySet>]
        attr_accessor :user_country_sets
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @device_groups = args[:device_groups] if args.key?(:device_groups)
          @device_tier_config_id = args[:device_tier_config_id] if args.key?(:device_tier_config_id)
          @device_tier_set = args[:device_tier_set] if args.key?(:device_tier_set)
          @user_country_sets = args[:user_country_sets] if args.key?(:user_country_sets)
        end
      end
      
      # A set of device tiers. A tier set determines what variation of app content
      # gets served to a specific device, for device-targeted content. You should
      # assign a priority level to each tier, which determines the ordering by which
      # they are evaluated by Play. See the documentation of DeviceTier.level for more
      # details.
      class DeviceTierSet
        include Google::Apis::Core::Hashable
      
        # Device tiers belonging to the set.
        # Corresponds to the JSON property `deviceTiers`
        # @return [Array<Google::Apis::AndroidpublisherV3::DeviceTier>]
        attr_accessor :device_tiers
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @device_tiers = args[:device_tiers] if args.key?(:device_tiers)
        end
      end
      
      # An expansion file. The resource for ExpansionFilesService.
      class ExpansionFile
        include Google::Apis::Core::Hashable
      
        # If set, this field indicates that this APK has an expansion file uploaded to
        # it: this APK does not reference another APK's expansion file. The field's
        # value is the size of the uploaded expansion file in bytes.
        # Corresponds to the JSON property `fileSize`
        # @return [Fixnum]
        attr_accessor :file_size
      
        # If set, this APK's expansion file references another APK's expansion file. The
        # file_size field will not be set.
        # Corresponds to the JSON property `referencesVersion`
        # @return [Fixnum]
        attr_accessor :references_version
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @file_size = args[:file_size] if args.key?(:file_size)
          @references_version = args[:references_version] if args.key?(:references_version)
        end
      end
      
      # Response for uploading an expansion file.
      class ExpansionFilesUploadResponse
        include Google::Apis::Core::Hashable
      
        # An expansion file. The resource for ExpansionFilesService.
        # Corresponds to the JSON property `expansionFile`
        # @return [Google::Apis::AndroidpublisherV3::ExpansionFile]
        attr_accessor :expansion_file
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @expansion_file = args[:expansion_file] if args.key?(:expansion_file)
        end
      end
      
      # User account identifier in the third-party service.
      class ExternalAccountIdentifiers
        include Google::Apis::Core::Hashable
      
        # User account identifier in the third-party service. Only present if account
        # linking happened as part of the subscription purchase flow.
        # Corresponds to the JSON property `externalAccountId`
        # @return [String]
        attr_accessor :external_account_id
      
        # An obfuscated version of the id that is uniquely associated with the user's
        # account in your app. Present for the following purchases: * If account linking
        # happened as part of the subscription purchase flow. * It was specified using
        # https://developer.android.com/reference/com/android/billingclient/api/
        # BillingFlowParams.Builder#setobfuscatedaccountid when the purchase was made.
        # Corresponds to the JSON property `obfuscatedExternalAccountId`
        # @return [String]
        attr_accessor :obfuscated_external_account_id
      
        # An obfuscated version of the id that is uniquely associated with the user's
        # profile in your app. Only present if specified using https://developer.android.
        # com/reference/com/android/billingclient/api/BillingFlowParams.Builder#
        # setobfuscatedprofileid when the purchase was made.
        # Corresponds to the JSON property `obfuscatedExternalProfileId`
        # @return [String]
        attr_accessor :obfuscated_external_profile_id
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @external_account_id = args[:external_account_id] if args.key?(:external_account_id)
          @obfuscated_external_account_id = args[:obfuscated_external_account_id] if args.key?(:obfuscated_external_account_id)
          @obfuscated_external_profile_id = args[:obfuscated_external_profile_id] if args.key?(:obfuscated_external_profile_id)
        end
      end
      
      # Details of an external subscription.
      class ExternalSubscription
        include Google::Apis::Core::Hashable
      
        # Required. The type of the external subscription.
        # Corresponds to the JSON property `subscriptionType`
        # @return [String]
        attr_accessor :subscription_type
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @subscription_type = args[:subscription_type] if args.key?(:subscription_type)
        end
      end
      
      # The details of an external transaction.
      class ExternalTransaction
        include Google::Apis::Core::Hashable
      
        # Output only. The time when this transaction was created. This is the time when
        # Google was notified of the transaction.
        # Corresponds to the JSON property `createTime`
        # @return [String]
        attr_accessor :create_time
      
        # Definition of a price, i.e. currency and units.
        # Corresponds to the JSON property `currentPreTaxAmount`
        # @return [Google::Apis::AndroidpublisherV3::Price]
        attr_accessor :current_pre_tax_amount
      
        # Definition of a price, i.e. currency and units.
        # Corresponds to the JSON property `currentTaxAmount`
        # @return [Google::Apis::AndroidpublisherV3::Price]
        attr_accessor :current_tax_amount
      
        # Output only. The id of this transaction. All transaction ids under the same
        # package name must be unique. Set when creating the external transaction.
        # Corresponds to the JSON property `externalTransactionId`
        # @return [String]
        attr_accessor :external_transaction_id
      
        # Represents a one-time transaction.
        # Corresponds to the JSON property `oneTimeTransaction`
        # @return [Google::Apis::AndroidpublisherV3::OneTimeExternalTransaction]
        attr_accessor :one_time_transaction
      
        # Definition of a price, i.e. currency and units.
        # Corresponds to the JSON property `originalPreTaxAmount`
        # @return [Google::Apis::AndroidpublisherV3::Price]
        attr_accessor :original_pre_tax_amount
      
        # Definition of a price, i.e. currency and units.
        # Corresponds to the JSON property `originalTaxAmount`
        # @return [Google::Apis::AndroidpublisherV3::Price]
        attr_accessor :original_tax_amount
      
        # Output only. The resource name of the external transaction. The package name
        # of the application the inapp products were sold (for example, 'com.some.app').
        # Corresponds to the JSON property `packageName`
        # @return [String]
        attr_accessor :package_name
      
        # Represents a transaction that is part of a recurring series of payments. This
        # can be a subscription or a one-time product with multiple payments (such as
        # preorder).
        # Corresponds to the JSON property `recurringTransaction`
        # @return [Google::Apis::AndroidpublisherV3::RecurringExternalTransaction]
        attr_accessor :recurring_transaction
      
        # Represents a transaction performed using a test account. These transactions
        # will not be charged by Google.
        # Corresponds to the JSON property `testPurchase`
        # @return [Google::Apis::AndroidpublisherV3::ExternalTransactionTestPurchase]
        attr_accessor :test_purchase
      
        # Output only. The current state of the transaction.
        # Corresponds to the JSON property `transactionState`
        # @return [String]
        attr_accessor :transaction_state
      
        # Required. The time when the transaction was completed.
        # Corresponds to the JSON property `transactionTime`
        # @return [String]
        attr_accessor :transaction_time
      
        # User's address for the external transaction.
        # Corresponds to the JSON property `userTaxAddress`
        # @return [Google::Apis::AndroidpublisherV3::ExternalTransactionAddress]
        attr_accessor :user_tax_address
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @create_time = args[:create_time] if args.key?(:create_time)
          @current_pre_tax_amount = args[:current_pre_tax_amount] if args.key?(:current_pre_tax_amount)
          @current_tax_amount = args[:current_tax_amount] if args.key?(:current_tax_amount)
          @external_transaction_id = args[:external_transaction_id] if args.key?(:external_transaction_id)
          @one_time_transaction = args[:one_time_transaction] if args.key?(:one_time_transaction)
          @original_pre_tax_amount = args[:original_pre_tax_amount] if args.key?(:original_pre_tax_amount)
          @original_tax_amount = args[:original_tax_amount] if args.key?(:original_tax_amount)
          @package_name = args[:package_name] if args.key?(:package_name)
          @recurring_transaction = args[:recurring_transaction] if args.key?(:recurring_transaction)
          @test_purchase = args[:test_purchase] if args.key?(:test_purchase)
          @transaction_state = args[:transaction_state] if args.key?(:transaction_state)
          @transaction_time = args[:transaction_time] if args.key?(:transaction_time)
          @user_tax_address = args[:user_tax_address] if args.key?(:user_tax_address)
        end
      end
      
      # User's address for the external transaction.
      class ExternalTransactionAddress
        include Google::Apis::Core::Hashable
      
        # Optional. Top-level administrative subdivision of the country/region. Only
        # required for transactions in India. Valid values are "ANDAMAN AND NICOBAR
        # ISLANDS", "ANDHRA PRADESH", "ARUNACHAL PRADESH", "ASSAM", "BIHAR", "CHANDIGARH"
        # , "CHHATTISGARH", "DADRA AND NAGAR HAVELI", "DADRA AND NAGAR HAVELI AND DAMAN
        # AND DIU", "DAMAN AND DIU", "DELHI", "GOA", "GUJARAT", "HARYANA", "HIMACHAL
        # PRADESH", "JAMMU AND KASHMIR", "JHARKHAND", "KARNATAKA", "KERALA", "LADAKH", "
        # LAKSHADWEEP", "MADHYA PRADESH", "MAHARASHTRA", "MANIPUR", "MEGHALAYA", "
        # MIZORAM", "NAGALAND", "ODISHA", "PUDUCHERRY", "PUNJAB", "RAJASTHAN", "SIKKIM",
        # "TAMIL NADU", "TELANGANA", "TRIPURA", "UTTAR PRADESH", "UTTARAKHAND", and "
        # WEST BENGAL".
        # Corresponds to the JSON property `administrativeArea`
        # @return [String]
        attr_accessor :administrative_area
      
        # Required. Two letter region code based on ISO-3166-1 Alpha-2 (UN region codes).
        # Corresponds to the JSON property `regionCode`
        # @return [String]
        attr_accessor :region_code
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @administrative_area = args[:administrative_area] if args.key?(:administrative_area)
          @region_code = args[:region_code] if args.key?(:region_code)
        end
      end
      
      # Represents a transaction performed using a test account. These transactions
      # will not be charged by Google.
      class ExternalTransactionTestPurchase
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # Defines an APK available for this application that is hosted externally and
      # not uploaded to Google Play. This function is only available to organizations
      # using Managed Play whose application is configured to restrict distribution to
      # the organizations.
      class ExternallyHostedApk
        include Google::Apis::Core::Hashable
      
        # The application label.
        # Corresponds to the JSON property `applicationLabel`
        # @return [String]
        attr_accessor :application_label
      
        # A certificate (or array of certificates if a certificate-chain is used) used
        # to sign this APK, represented as a base64 encoded byte array.
        # Corresponds to the JSON property `certificateBase64s`
        # @return [Array<String>]
        attr_accessor :certificate_base64s
      
        # The URL at which the APK is hosted. This must be an https URL.
        # Corresponds to the JSON property `externallyHostedUrl`
        # @return [String]
        attr_accessor :externally_hosted_url
      
        # The sha1 checksum of this APK, represented as a base64 encoded byte array.
        # Corresponds to the JSON property `fileSha1Base64`
        # @return [String]
        attr_accessor :file_sha1_base64
      
        # The sha256 checksum of this APK, represented as a base64 encoded byte array.
        # Corresponds to the JSON property `fileSha256Base64`
        # @return [String]
        attr_accessor :file_sha256_base64
      
        # The file size in bytes of this APK.
        # Corresponds to the JSON property `fileSize`
        # @return [Fixnum]
        attr_accessor :file_size
      
        # The icon image from the APK, as a base64 encoded byte array.
        # Corresponds to the JSON property `iconBase64`
        # @return [String]
        attr_accessor :icon_base64
      
        # The maximum SDK supported by this APK (optional).
        # Corresponds to the JSON property `maximumSdk`
        # @return [Fixnum]
        attr_accessor :maximum_sdk
      
        # The minimum SDK targeted by this APK.
        # Corresponds to the JSON property `minimumSdk`
        # @return [Fixnum]
        attr_accessor :minimum_sdk
      
        # The native code environments supported by this APK (optional).
        # Corresponds to the JSON property `nativeCodes`
        # @return [Array<String>]
        attr_accessor :native_codes
      
        # The package name.
        # Corresponds to the JSON property `packageName`
        # @return [String]
        attr_accessor :package_name
      
        # The features required by this APK (optional).
        # Corresponds to the JSON property `usesFeatures`
        # @return [Array<String>]
        attr_accessor :uses_features
      
        # The permissions requested by this APK.
        # Corresponds to the JSON property `usesPermissions`
        # @return [Array<Google::Apis::AndroidpublisherV3::UsesPermission>]
        attr_accessor :uses_permissions
      
        # The version code of this APK.
        # Corresponds to the JSON property `versionCode`
        # @return [Fixnum]
        attr_accessor :version_code
      
        # The version name of this APK.
        # Corresponds to the JSON property `versionName`
        # @return [String]
        attr_accessor :version_name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @application_label = args[:application_label] if args.key?(:application_label)
          @certificate_base64s = args[:certificate_base64s] if args.key?(:certificate_base64s)
          @externally_hosted_url = args[:externally_hosted_url] if args.key?(:externally_hosted_url)
          @file_sha1_base64 = args[:file_sha1_base64] if args.key?(:file_sha1_base64)
          @file_sha256_base64 = args[:file_sha256_base64] if args.key?(:file_sha256_base64)
          @file_size = args[:file_size] if args.key?(:file_size)
          @icon_base64 = args[:icon_base64] if args.key?(:icon_base64)
          @maximum_sdk = args[:maximum_sdk] if args.key?(:maximum_sdk)
          @minimum_sdk = args[:minimum_sdk] if args.key?(:minimum_sdk)
          @native_codes = args[:native_codes] if args.key?(:native_codes)
          @package_name = args[:package_name] if args.key?(:package_name)
          @uses_features = args[:uses_features] if args.key?(:uses_features)
          @uses_permissions = args[:uses_permissions] if args.key?(:uses_permissions)
          @version_code = args[:version_code] if args.key?(:version_code)
          @version_name = args[:version_name] if args.key?(:version_name)
        end
      end
      
      # A full refund of the remaining amount of a transaction.
      class FullRefund
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # Response to list generated APKs.
      class GeneratedApksListResponse
        include Google::Apis::Core::Hashable
      
        # All generated APKs, grouped by the APK signing key.
        # Corresponds to the JSON property `generatedApks`
        # @return [Array<Google::Apis::AndroidpublisherV3::GeneratedApksPerSigningKey>]
        attr_accessor :generated_apks
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @generated_apks = args[:generated_apks] if args.key?(:generated_apks)
        end
      end
      
      # Download metadata for split, standalone and universal APKs, as well as asset
      # pack slices, signed with a given key.
      class GeneratedApksPerSigningKey
        include Google::Apis::Core::Hashable
      
        # SHA256 hash of the APK signing public key certificate.
        # Corresponds to the JSON property `certificateSha256Hash`
        # @return [String]
        attr_accessor :certificate_sha256_hash
      
        # List of asset pack slices which will be served for this app bundle, signed
        # with a key corresponding to certificate_sha256_hash.
        # Corresponds to the JSON property `generatedAssetPackSlices`
        # @return [Array<Google::Apis::AndroidpublisherV3::GeneratedAssetPackSlice>]
        attr_accessor :generated_asset_pack_slices
      
        # List of generated split APKs, signed with a key corresponding to
        # certificate_sha256_hash.
        # Corresponds to the JSON property `generatedSplitApks`
        # @return [Array<Google::Apis::AndroidpublisherV3::GeneratedSplitApk>]
        attr_accessor :generated_split_apks
      
        # List of generated standalone APKs, signed with a key corresponding to
        # certificate_sha256_hash.
        # Corresponds to the JSON property `generatedStandaloneApks`
        # @return [Array<Google::Apis::AndroidpublisherV3::GeneratedStandaloneApk>]
        attr_accessor :generated_standalone_apks
      
        # Download metadata for a universal APK.
        # Corresponds to the JSON property `generatedUniversalApk`
        # @return [Google::Apis::AndroidpublisherV3::GeneratedUniversalApk]
        attr_accessor :generated_universal_apk
      
        # Targeting information about the generated apks.
        # Corresponds to the JSON property `targetingInfo`
        # @return [Google::Apis::AndroidpublisherV3::TargetingInfo]
        attr_accessor :targeting_info
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @certificate_sha256_hash = args[:certificate_sha256_hash] if args.key?(:certificate_sha256_hash)
          @generated_asset_pack_slices = args[:generated_asset_pack_slices] if args.key?(:generated_asset_pack_slices)
          @generated_split_apks = args[:generated_split_apks] if args.key?(:generated_split_apks)
          @generated_standalone_apks = args[:generated_standalone_apks] if args.key?(:generated_standalone_apks)
          @generated_universal_apk = args[:generated_universal_apk] if args.key?(:generated_universal_apk)
          @targeting_info = args[:targeting_info] if args.key?(:targeting_info)
        end
      end
      
      # Download metadata for an asset pack slice.
      class GeneratedAssetPackSlice
        include Google::Apis::Core::Hashable
      
        # Download ID, which uniquely identifies the APK to download. Should be supplied
        # to `generatedapks.download` method.
        # Corresponds to the JSON property `downloadId`
        # @return [String]
        attr_accessor :download_id
      
        # Name of the module that this asset slice belongs to.
        # Corresponds to the JSON property `moduleName`
        # @return [String]
        attr_accessor :module_name
      
        # Asset slice ID.
        # Corresponds to the JSON property `sliceId`
        # @return [String]
        attr_accessor :slice_id
      
        # Asset module version.
        # Corresponds to the JSON property `version`
        # @return [Fixnum]
        attr_accessor :version
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @download_id = args[:download_id] if args.key?(:download_id)
          @module_name = args[:module_name] if args.key?(:module_name)
          @slice_id = args[:slice_id] if args.key?(:slice_id)
          @version = args[:version] if args.key?(:version)
        end
      end
      
      # Download metadata for a split APK.
      class GeneratedSplitApk
        include Google::Apis::Core::Hashable
      
        # Download ID, which uniquely identifies the APK to download. Should be supplied
        # to `generatedapks.download` method.
        # Corresponds to the JSON property `downloadId`
        # @return [String]
        attr_accessor :download_id
      
        # Name of the module that this APK belongs to.
        # Corresponds to the JSON property `moduleName`
        # @return [String]
        attr_accessor :module_name
      
        # Split ID. Empty for the main split of the base module.
        # Corresponds to the JSON property `splitId`
        # @return [String]
        attr_accessor :split_id
      
        # ID of the generated variant.
        # Corresponds to the JSON property `variantId`
        # @return [Fixnum]
        attr_accessor :variant_id
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @download_id = args[:download_id] if args.key?(:download_id)
          @module_name = args[:module_name] if args.key?(:module_name)
          @split_id = args[:split_id] if args.key?(:split_id)
          @variant_id = args[:variant_id] if args.key?(:variant_id)
        end
      end
      
      # Download metadata for a standalone APK.
      class GeneratedStandaloneApk
        include Google::Apis::Core::Hashable
      
        # Download ID, which uniquely identifies the APK to download. Should be supplied
        # to `generatedapks.download` method.
        # Corresponds to the JSON property `downloadId`
        # @return [String]
        attr_accessor :download_id
      
        # ID of the generated variant.
        # Corresponds to the JSON property `variantId`
        # @return [Fixnum]
        attr_accessor :variant_id
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @download_id = args[:download_id] if args.key?(:download_id)
          @variant_id = args[:variant_id] if args.key?(:variant_id)
        end
      end
      
      # Download metadata for a universal APK.
      class GeneratedUniversalApk
        include Google::Apis::Core::Hashable
      
        # Download ID, which uniquely identifies the APK to download. Should be supplied
        # to `generatedapks.download` method.
        # Corresponds to the JSON property `downloadId`
        # @return [String]
        attr_accessor :download_id
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @download_id = args[:download_id] if args.key?(:download_id)
        end
      end
      
      # An access grant resource.
      class Grant
        include Google::Apis::Core::Hashable
      
        # The permissions granted to the user for this app.
        # Corresponds to the JSON property `appLevelPermissions`
        # @return [Array<String>]
        attr_accessor :app_level_permissions
      
        # Required. Resource name for this grant, following the pattern "developers/`
        # developer`/users/`email`/grants/`package_name`". If this grant is for a draft
        # app, the app ID will be used in this resource name instead of the package name.
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        # Immutable. The package name of the app. This will be empty for draft apps.
        # Corresponds to the JSON property `packageName`
        # @return [String]
        attr_accessor :package_name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @app_level_permissions = args[:app_level_permissions] if args.key?(:app_level_permissions)
          @name = args[:name] if args.key?(:name)
          @package_name = args[:package_name] if args.key?(:package_name)
        end
      end
      
      # An uploaded image. The resource for ImagesService.
      class Image
        include Google::Apis::Core::Hashable
      
        # A unique id representing this image.
        # Corresponds to the JSON property `id`
        # @return [String]
        attr_accessor :id
      
        # A sha1 hash of the image.
        # Corresponds to the JSON property `sha1`
        # @return [String]
        attr_accessor :sha1
      
        # A sha256 hash of the image.
        # Corresponds to the JSON property `sha256`
        # @return [String]
        attr_accessor :sha256
      
        # A URL that will serve a preview of the image.
        # Corresponds to the JSON property `url`
        # @return [String]
        attr_accessor :url
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @id = args[:id] if args.key?(:id)
          @sha1 = args[:sha1] if args.key?(:sha1)
          @sha256 = args[:sha256] if args.key?(:sha256)
          @url = args[:url] if args.key?(:url)
        end
      end
      
      # Response for deleting all images.
      class ImagesDeleteAllResponse
        include Google::Apis::Core::Hashable
      
        # The deleted images.
        # Corresponds to the JSON property `deleted`
        # @return [Array<Google::Apis::AndroidpublisherV3::Image>]
        attr_accessor :deleted
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @deleted = args[:deleted] if args.key?(:deleted)
        end
      end
      
      # Response listing all images.
      class ImagesListResponse
        include Google::Apis::Core::Hashable
      
        # All listed Images.
        # Corresponds to the JSON property `images`
        # @return [Array<Google::Apis::AndroidpublisherV3::Image>]
        attr_accessor :images
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @images = args[:images] if args.key?(:images)
        end
      end
      
      # Response for uploading an image.
      class ImagesUploadResponse
        include Google::Apis::Core::Hashable
      
        # An uploaded image. The resource for ImagesService.
        # Corresponds to the JSON property `image`
        # @return [Google::Apis::AndroidpublisherV3::Image]
        attr_accessor :image
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @image = args[:image] if args.key?(:image)
        end
      end
      
      # An in-app product. The resource for InappproductsService.
      class InAppProduct
        include Google::Apis::Core::Hashable
      
        # Default language of the localized data, as defined by BCP-47. e.g. "en-US".
        # Corresponds to the JSON property `defaultLanguage`
        # @return [String]
        attr_accessor :default_language
      
        # Definition of a price, i.e. currency and units.
        # Corresponds to the JSON property `defaultPrice`
        # @return [Google::Apis::AndroidpublisherV3::Price]
        attr_accessor :default_price
      
        # Grace period of the subscription, specified in ISO 8601 format. Allows
        # developers to give their subscribers a grace period when the payment for the
        # new recurrence period is declined. Acceptable values are P0D (zero days), P3D (
        # three days), P7D (seven days), P14D (14 days), and P30D (30 days).
        # Corresponds to the JSON property `gracePeriod`
        # @return [String]
        attr_accessor :grace_period
      
        # List of localized title and description data. Map key is the language of the
        # localized data, as defined by BCP-47, e.g. "en-US".
        # Corresponds to the JSON property `listings`
        # @return [Hash<String,Google::Apis::AndroidpublisherV3::InAppProductListing>]
        attr_accessor :listings
      
        # Details about taxation and legal compliance for managed products.
        # Corresponds to the JSON property `managedProductTaxesAndComplianceSettings`
        # @return [Google::Apis::AndroidpublisherV3::ManagedProductTaxAndComplianceSettings]
        attr_accessor :managed_product_taxes_and_compliance_settings
      
        # Package name of the parent app.
        # Corresponds to the JSON property `packageName`
        # @return [String]
        attr_accessor :package_name
      
        # Prices per buyer region. None of these can be zero, as in-app products are
        # never free. Map key is region code, as defined by ISO 3166-2.
        # Corresponds to the JSON property `prices`
        # @return [Hash<String,Google::Apis::AndroidpublisherV3::Price>]
        attr_accessor :prices
      
        # The type of the product, e.g. a recurring subscription.
        # Corresponds to the JSON property `purchaseType`
        # @return [String]
        attr_accessor :purchase_type
      
        # Stock-keeping-unit (SKU) of the product, unique within an app.
        # Corresponds to the JSON property `sku`
        # @return [String]
        attr_accessor :sku
      
        # The status of the product, e.g. whether it's active.
        # Corresponds to the JSON property `status`
        # @return [String]
        attr_accessor :status
      
        # Subscription period, specified in ISO 8601 format. Acceptable values are P1W (
        # one week), P1M (one month), P3M (three months), P6M (six months), and P1Y (one
        # year).
        # Corresponds to the JSON property `subscriptionPeriod`
        # @return [String]
        attr_accessor :subscription_period
      
        # Details about taxation, Google Play policy and legal compliance for
        # subscription products.
        # Corresponds to the JSON property `subscriptionTaxesAndComplianceSettings`
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionTaxAndComplianceSettings]
        attr_accessor :subscription_taxes_and_compliance_settings
      
        # Trial period, specified in ISO 8601 format. Acceptable values are anything
        # between P7D (seven days) and P999D (999 days).
        # Corresponds to the JSON property `trialPeriod`
        # @return [String]
        attr_accessor :trial_period
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @default_language = args[:default_language] if args.key?(:default_language)
          @default_price = args[:default_price] if args.key?(:default_price)
          @grace_period = args[:grace_period] if args.key?(:grace_period)
          @listings = args[:listings] if args.key?(:listings)
          @managed_product_taxes_and_compliance_settings = args[:managed_product_taxes_and_compliance_settings] if args.key?(:managed_product_taxes_and_compliance_settings)
          @package_name = args[:package_name] if args.key?(:package_name)
          @prices = args[:prices] if args.key?(:prices)
          @purchase_type = args[:purchase_type] if args.key?(:purchase_type)
          @sku = args[:sku] if args.key?(:sku)
          @status = args[:status] if args.key?(:status)
          @subscription_period = args[:subscription_period] if args.key?(:subscription_period)
          @subscription_taxes_and_compliance_settings = args[:subscription_taxes_and_compliance_settings] if args.key?(:subscription_taxes_and_compliance_settings)
          @trial_period = args[:trial_period] if args.key?(:trial_period)
        end
      end
      
      # Store listing of a single in-app product.
      class InAppProductListing
        include Google::Apis::Core::Hashable
      
        # Localized entitlement benefits for a subscription.
        # Corresponds to the JSON property `benefits`
        # @return [Array<String>]
        attr_accessor :benefits
      
        # Description for the store listing.
        # Corresponds to the JSON property `description`
        # @return [String]
        attr_accessor :description
      
        # Title for the store listing.
        # Corresponds to the JSON property `title`
        # @return [String]
        attr_accessor :title
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @benefits = args[:benefits] if args.key?(:benefits)
          @description = args[:description] if args.key?(:description)
          @title = args[:title] if args.key?(:title)
        end
      end
      
      # Response listing all in-app products.
      class InappproductsListResponse
        include Google::Apis::Core::Hashable
      
        # All in-app products.
        # Corresponds to the JSON property `inappproduct`
        # @return [Array<Google::Apis::AndroidpublisherV3::InAppProduct>]
        attr_accessor :inappproduct
      
        # The kind of this response ("androidpublisher#inappproductsListResponse").
        # Corresponds to the JSON property `kind`
        # @return [String]
        attr_accessor :kind
      
        # Information about the current page. List operations that supports paging
        # return only one "page" of results. This protocol buffer message describes the
        # page that has been returned.
        # Corresponds to the JSON property `pageInfo`
        # @return [Google::Apis::AndroidpublisherV3::PageInfo]
        attr_accessor :page_info
      
        # Pagination information returned by a List operation when token pagination is
        # enabled. List operations that supports paging return only one "page" of
        # results. This protocol buffer message describes the page that has been
        # returned. When using token pagination, clients should use the next/previous
        # token to get another page of the result. The presence or absence of next/
        # previous token indicates whether a next/previous page is available and
        # provides a mean of accessing this page. ListRequest.page_token should be set
        # to either next_page_token or previous_page_token to access another page.
        # Corresponds to the JSON property `tokenPagination`
        # @return [Google::Apis::AndroidpublisherV3::TokenPagination]
        attr_accessor :token_pagination
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @inappproduct = args[:inappproduct] if args.key?(:inappproduct)
          @kind = args[:kind] if args.key?(:kind)
          @page_info = args[:page_info] if args.key?(:page_info)
          @token_pagination = args[:token_pagination] if args.key?(:token_pagination)
        end
      end
      
      # An artifact resource which gets created when uploading an APK or Android App
      # Bundle through internal app sharing.
      class InternalAppSharingArtifact
        include Google::Apis::Core::Hashable
      
        # The sha256 fingerprint of the certificate used to sign the generated artifact.
        # Corresponds to the JSON property `certificateFingerprint`
        # @return [String]
        attr_accessor :certificate_fingerprint
      
        # The download URL generated for the uploaded artifact. Users that are
        # authorized to download can follow the link to the Play Store app to install it.
        # Corresponds to the JSON property `downloadUrl`
        # @return [String]
        attr_accessor :download_url
      
        # The sha256 hash of the artifact represented as a lowercase hexadecimal number,
        # matching the output of the sha256sum command.
        # Corresponds to the JSON property `sha256`
        # @return [String]
        attr_accessor :sha256
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @certificate_fingerprint = args[:certificate_fingerprint] if args.key?(:certificate_fingerprint)
          @download_url = args[:download_url] if args.key?(:download_url)
          @sha256 = args[:sha256] if args.key?(:sha256)
        end
      end
      
      # Contains the introductory price information for a subscription.
      class IntroductoryPriceInfo
        include Google::Apis::Core::Hashable
      
        # Introductory price of the subscription, not including tax. The currency is the
        # same as price_currency_code. Price is expressed in micro-units, where 1,000,
        # 000 micro-units represents one unit of the currency. For example, if the
        # subscription price is €1.99, price_amount_micros is 1990000.
        # Corresponds to the JSON property `introductoryPriceAmountMicros`
        # @return [Fixnum]
        attr_accessor :introductory_price_amount_micros
      
        # ISO 4217 currency code for the introductory subscription price. For example,
        # if the price is specified in British pounds sterling, price_currency_code is "
        # GBP".
        # Corresponds to the JSON property `introductoryPriceCurrencyCode`
        # @return [String]
        attr_accessor :introductory_price_currency_code
      
        # The number of billing period to offer introductory pricing.
        # Corresponds to the JSON property `introductoryPriceCycles`
        # @return [Fixnum]
        attr_accessor :introductory_price_cycles
      
        # Introductory price period, specified in ISO 8601 format. Common values are (
        # but not limited to) "P1W" (one week), "P1M" (one month), "P3M" (three months),
        # "P6M" (six months), and "P1Y" (one year).
        # Corresponds to the JSON property `introductoryPricePeriod`
        # @return [String]
        attr_accessor :introductory_price_period
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @introductory_price_amount_micros = args[:introductory_price_amount_micros] if args.key?(:introductory_price_amount_micros)
          @introductory_price_currency_code = args[:introductory_price_currency_code] if args.key?(:introductory_price_currency_code)
          @introductory_price_cycles = args[:introductory_price_cycles] if args.key?(:introductory_price_cycles)
          @introductory_price_period = args[:introductory_price_period] if args.key?(:introductory_price_period)
        end
      end
      
      # Targeting based on language.
      class LanguageTargeting
        include Google::Apis::Core::Hashable
      
        # Alternative languages.
        # Corresponds to the JSON property `alternatives`
        # @return [Array<String>]
        attr_accessor :alternatives
      
        # ISO-639: 2 or 3 letter language code.
        # Corresponds to the JSON property `value`
        # @return [Array<String>]
        attr_accessor :value
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @alternatives = args[:alternatives] if args.key?(:alternatives)
          @value = args[:value] if args.key?(:value)
        end
      end
      
      # Response listing existing device tier configs.
      class ListDeviceTierConfigsResponse
        include Google::Apis::Core::Hashable
      
        # Device tier configs created by the developer.
        # Corresponds to the JSON property `deviceTierConfigs`
        # @return [Array<Google::Apis::AndroidpublisherV3::DeviceTierConfig>]
        attr_accessor :device_tier_configs
      
        # A token, which can be sent as `page_token` to retrieve the next page. If this
        # field is omitted, there are no subsequent pages.
        # Corresponds to the JSON property `nextPageToken`
        # @return [String]
        attr_accessor :next_page_token
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @device_tier_configs = args[:device_tier_configs] if args.key?(:device_tier_configs)
          @next_page_token = args[:next_page_token] if args.key?(:next_page_token)
        end
      end
      
      # Response message for ListSubscriptionOffers.
      class ListSubscriptionOffersResponse
        include Google::Apis::Core::Hashable
      
        # A token, which can be sent as `page_token` to retrieve the next page. If this
        # field is omitted, there are no subsequent pages.
        # Corresponds to the JSON property `nextPageToken`
        # @return [String]
        attr_accessor :next_page_token
      
        # The subscription offers from the specified subscription.
        # Corresponds to the JSON property `subscriptionOffers`
        # @return [Array<Google::Apis::AndroidpublisherV3::SubscriptionOffer>]
        attr_accessor :subscription_offers
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @next_page_token = args[:next_page_token] if args.key?(:next_page_token)
          @subscription_offers = args[:subscription_offers] if args.key?(:subscription_offers)
        end
      end
      
      # Response message for ListSubscriptions.
      class ListSubscriptionsResponse
        include Google::Apis::Core::Hashable
      
        # A token, which can be sent as `page_token` to retrieve the next page. If this
        # field is omitted, there are no subsequent pages.
        # Corresponds to the JSON property `nextPageToken`
        # @return [String]
        attr_accessor :next_page_token
      
        # The subscriptions from the specified app.
        # Corresponds to the JSON property `subscriptions`
        # @return [Array<Google::Apis::AndroidpublisherV3::Subscription>]
        attr_accessor :subscriptions
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @next_page_token = args[:next_page_token] if args.key?(:next_page_token)
          @subscriptions = args[:subscriptions] if args.key?(:subscriptions)
        end
      end
      
      # A response containing one or more users with access to an account.
      class ListUsersResponse
        include Google::Apis::Core::Hashable
      
        # A token to pass to subsequent calls in order to retrieve subsequent results.
        # This will not be set if there are no more results to return.
        # Corresponds to the JSON property `nextPageToken`
        # @return [String]
        attr_accessor :next_page_token
      
        # The resulting users.
        # Corresponds to the JSON property `users`
        # @return [Array<Google::Apis::AndroidpublisherV3::User>]
        attr_accessor :users
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @next_page_token = args[:next_page_token] if args.key?(:next_page_token)
          @users = args[:users] if args.key?(:users)
        end
      end
      
      # A localized store listing. The resource for ListingsService.
      class Listing
        include Google::Apis::Core::Hashable
      
        # Full description of the app.
        # Corresponds to the JSON property `fullDescription`
        # @return [String]
        attr_accessor :full_description
      
        # Language localization code (a BCP-47 language tag; for example, "de-AT" for
        # Austrian German).
        # Corresponds to the JSON property `language`
        # @return [String]
        attr_accessor :language
      
        # Short description of the app.
        # Corresponds to the JSON property `shortDescription`
        # @return [String]
        attr_accessor :short_description
      
        # Localized title of the app.
        # Corresponds to the JSON property `title`
        # @return [String]
        attr_accessor :title
      
        # URL of a promotional YouTube video for the app.
        # Corresponds to the JSON property `video`
        # @return [String]
        attr_accessor :video
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @full_description = args[:full_description] if args.key?(:full_description)
          @language = args[:language] if args.key?(:language)
          @short_description = args[:short_description] if args.key?(:short_description)
          @title = args[:title] if args.key?(:title)
          @video = args[:video] if args.key?(:video)
        end
      end
      
      # Response listing all localized listings.
      class ListingsListResponse
        include Google::Apis::Core::Hashable
      
        # The kind of this response ("androidpublisher#listingsListResponse").
        # Corresponds to the JSON property `kind`
        # @return [String]
        attr_accessor :kind
      
        # All localized listings.
        # Corresponds to the JSON property `listings`
        # @return [Array<Google::Apis::AndroidpublisherV3::Listing>]
        attr_accessor :listings
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @kind = args[:kind] if args.key?(:kind)
          @listings = args[:listings] if args.key?(:listings)
        end
      end
      
      # Localized text in given language.
      class LocalizedText
        include Google::Apis::Core::Hashable
      
        # Language localization code (a BCP-47 language tag; for example, "de-AT" for
        # Austrian German).
        # Corresponds to the JSON property `language`
        # @return [String]
        attr_accessor :language
      
        # The text in the given language.
        # Corresponds to the JSON property `text`
        # @return [String]
        attr_accessor :text
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @language = args[:language] if args.key?(:language)
          @text = args[:text] if args.key?(:text)
        end
      end
      
      # Details about taxation and legal compliance for managed products.
      class ManagedProductTaxAndComplianceSettings
        include Google::Apis::Core::Hashable
      
        # Digital content or service classification for products distributed to users in
        # the European Economic Area (EEA). The withdrawal regime under EEA consumer
        # laws depends on this classification. Refer to the [Help Center article](https:/
        # /support.google.com/googleplay/android-developer/answer/10463498) for more
        # information.
        # Corresponds to the JSON property `eeaWithdrawalRightType`
        # @return [String]
        attr_accessor :eea_withdrawal_right_type
      
        # Whether this in-app product is declared as a product representing a tokenized
        # digital asset.
        # Corresponds to the JSON property `isTokenizedDigitalAsset`
        # @return [Boolean]
        attr_accessor :is_tokenized_digital_asset
        alias_method :is_tokenized_digital_asset?, :is_tokenized_digital_asset
      
        # A mapping from region code to tax rate details. The keys are region codes as
        # defined by Unicode's "CLDR".
        # Corresponds to the JSON property `taxRateInfoByRegionCode`
        # @return [Hash<String,Google::Apis::AndroidpublisherV3::RegionalTaxRateInfo>]
        attr_accessor :tax_rate_info_by_region_code
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @eea_withdrawal_right_type = args[:eea_withdrawal_right_type] if args.key?(:eea_withdrawal_right_type)
          @is_tokenized_digital_asset = args[:is_tokenized_digital_asset] if args.key?(:is_tokenized_digital_asset)
          @tax_rate_info_by_region_code = args[:tax_rate_info_by_region_code] if args.key?(:tax_rate_info_by_region_code)
        end
      end
      
      # Request message for MigrateBasePlanPrices.
      class MigrateBasePlanPricesRequest
        include Google::Apis::Core::Hashable
      
        # Required. The regional prices to update.
        # Corresponds to the JSON property `regionalPriceMigrations`
        # @return [Array<Google::Apis::AndroidpublisherV3::RegionalPriceMigrationConfig>]
        attr_accessor :regional_price_migrations
      
        # The version of the available regions being used for the specified resource.
        # Corresponds to the JSON property `regionsVersion`
        # @return [Google::Apis::AndroidpublisherV3::RegionsVersion]
        attr_accessor :regions_version
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @regional_price_migrations = args[:regional_price_migrations] if args.key?(:regional_price_migrations)
          @regions_version = args[:regions_version] if args.key?(:regions_version)
        end
      end
      
      # Response message for MigrateBasePlanPrices.
      class MigrateBasePlanPricesResponse
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # Metadata of a module.
      class ModuleMetadata
        include Google::Apis::Core::Hashable
      
        # Indicates the delivery type (e.g. on-demand) of the module.
        # Corresponds to the JSON property `deliveryType`
        # @return [String]
        attr_accessor :delivery_type
      
        # Names of the modules that this module directly depends on. Each module
        # implicitly depends on the base module.
        # Corresponds to the JSON property `dependencies`
        # @return [Array<String>]
        attr_accessor :dependencies
      
        # Indicates the type of this feature module.
        # Corresponds to the JSON property `moduleType`
        # @return [String]
        attr_accessor :module_type
      
        # Module name.
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        # Targeting on the module level.
        # Corresponds to the JSON property `targeting`
        # @return [Google::Apis::AndroidpublisherV3::ModuleTargeting]
        attr_accessor :targeting
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @delivery_type = args[:delivery_type] if args.key?(:delivery_type)
          @dependencies = args[:dependencies] if args.key?(:dependencies)
          @module_type = args[:module_type] if args.key?(:module_type)
          @name = args[:name] if args.key?(:name)
          @targeting = args[:targeting] if args.key?(:targeting)
        end
      end
      
      # Targeting on the module level.
      class ModuleTargeting
        include Google::Apis::Core::Hashable
      
        # Targeting for device features.
        # Corresponds to the JSON property `deviceFeatureTargeting`
        # @return [Array<Google::Apis::AndroidpublisherV3::DeviceFeatureTargeting>]
        attr_accessor :device_feature_targeting
      
        # Targeting based on sdk version.
        # Corresponds to the JSON property `sdkVersionTargeting`
        # @return [Google::Apis::AndroidpublisherV3::SdkVersionTargeting]
        attr_accessor :sdk_version_targeting
      
        # Describes an inclusive/exclusive list of country codes that module targets.
        # Corresponds to the JSON property `userCountriesTargeting`
        # @return [Google::Apis::AndroidpublisherV3::UserCountriesTargeting]
        attr_accessor :user_countries_targeting
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @device_feature_targeting = args[:device_feature_targeting] if args.key?(:device_feature_targeting)
          @sdk_version_targeting = args[:sdk_version_targeting] if args.key?(:sdk_version_targeting)
          @user_countries_targeting = args[:user_countries_targeting] if args.key?(:user_countries_targeting)
        end
      end
      
      # Represents an amount of money with its currency type.
      class Money
        include Google::Apis::Core::Hashable
      
        # The three-letter currency code defined in ISO 4217.
        # Corresponds to the JSON property `currencyCode`
        # @return [String]
        attr_accessor :currency_code
      
        # Number of nano (10^-9) units of the amount. The value must be between -999,999,
        # 999 and +999,999,999 inclusive. If `units` is positive, `nanos` must be
        # positive or zero. If `units` is zero, `nanos` can be positive, zero, or
        # negative. If `units` is negative, `nanos` must be negative or zero. For
        # example $-1.75 is represented as `units`=-1 and `nanos`=-750,000,000.
        # Corresponds to the JSON property `nanos`
        # @return [Fixnum]
        attr_accessor :nanos
      
        # The whole units of the amount. For example if `currencyCode` is `"USD"`, then
        # 1 unit is one US dollar.
        # Corresponds to the JSON property `units`
        # @return [Fixnum]
        attr_accessor :units
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @currency_code = args[:currency_code] if args.key?(:currency_code)
          @nanos = args[:nanos] if args.key?(:nanos)
          @units = args[:units] if args.key?(:units)
        end
      end
      
      # Represents a list of apis.
      class MultiAbi
        include Google::Apis::Core::Hashable
      
        # A list of targeted ABIs, as represented by the Android Platform
        # Corresponds to the JSON property `abi`
        # @return [Array<Google::Apis::AndroidpublisherV3::Abi>]
        attr_accessor :abi
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @abi = args[:abi] if args.key?(:abi)
        end
      end
      
      # Targeting based on multiple abis.
      class MultiAbiTargeting
        include Google::Apis::Core::Hashable
      
        # Targeting of other sibling directories that were in the Bundle. For main
        # splits this is targeting of other main splits.
        # Corresponds to the JSON property `alternatives`
        # @return [Array<Google::Apis::AndroidpublisherV3::MultiAbi>]
        attr_accessor :alternatives
      
        # Value of a multi abi.
        # Corresponds to the JSON property `value`
        # @return [Array<Google::Apis::AndroidpublisherV3::MultiAbi>]
        attr_accessor :value
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @alternatives = args[:alternatives] if args.key?(:alternatives)
          @value = args[:value] if args.key?(:value)
        end
      end
      
      # Offer details information related to a purchase line item.
      class OfferDetails
        include Google::Apis::Core::Hashable
      
        # The base plan ID. Present for all base plan and offers.
        # Corresponds to the JSON property `basePlanId`
        # @return [String]
        attr_accessor :base_plan_id
      
        # The offer ID. Only present for discounted offers.
        # Corresponds to the JSON property `offerId`
        # @return [String]
        attr_accessor :offer_id
      
        # The latest offer tags associated with the offer. It includes tags inherited
        # from the base plan.
        # Corresponds to the JSON property `offerTags`
        # @return [Array<String>]
        attr_accessor :offer_tags
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @base_plan_id = args[:base_plan_id] if args.key?(:base_plan_id)
          @offer_id = args[:offer_id] if args.key?(:offer_id)
          @offer_tags = args[:offer_tags] if args.key?(:offer_tags)
        end
      end
      
      # Represents a custom tag specified for base plans and subscription offers.
      class OfferTag
        include Google::Apis::Core::Hashable
      
        # Must conform with RFC-1034. That is, this string can only contain lower-case
        # letters (a-z), numbers (0-9), and hyphens (-), and be at most 20 characters.
        # Corresponds to the JSON property `tag`
        # @return [String]
        attr_accessor :tag
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @tag = args[:tag] if args.key?(:tag)
        end
      end
      
      # Represents a one-time transaction.
      class OneTimeExternalTransaction
        include Google::Apis::Core::Hashable
      
        # Input only. Provided during the call to Create. Retrieved from the client when
        # the alternative billing flow is launched.
        # Corresponds to the JSON property `externalTransactionToken`
        # @return [String]
        attr_accessor :external_transaction_token
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @external_transaction_token = args[:external_transaction_token] if args.key?(:external_transaction_token)
        end
      end
      
      # Pricing information for any new locations Play may launch in.
      class OtherRegionsBasePlanConfig
        include Google::Apis::Core::Hashable
      
        # Represents an amount of money with its currency type.
        # Corresponds to the JSON property `eurPrice`
        # @return [Google::Apis::AndroidpublisherV3::Money]
        attr_accessor :eur_price
      
        # Whether the base plan is available for new subscribers in any new locations
        # Play may launch in. If not specified, this will default to false.
        # Corresponds to the JSON property `newSubscriberAvailability`
        # @return [Boolean]
        attr_accessor :new_subscriber_availability
        alias_method :new_subscriber_availability?, :new_subscriber_availability
      
        # Represents an amount of money with its currency type.
        # Corresponds to the JSON property `usdPrice`
        # @return [Google::Apis::AndroidpublisherV3::Money]
        attr_accessor :usd_price
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @eur_price = args[:eur_price] if args.key?(:eur_price)
          @new_subscriber_availability = args[:new_subscriber_availability] if args.key?(:new_subscriber_availability)
          @usd_price = args[:usd_price] if args.key?(:usd_price)
        end
      end
      
      # Configuration for any new locations Play may launch in specified on a
      # subscription offer.
      class OtherRegionsSubscriptionOfferConfig
        include Google::Apis::Core::Hashable
      
        # Whether the subscription offer in any new locations Play may launch in the
        # future. If not specified, this will default to false.
        # Corresponds to the JSON property `otherRegionsNewSubscriberAvailability`
        # @return [Boolean]
        attr_accessor :other_regions_new_subscriber_availability
        alias_method :other_regions_new_subscriber_availability?, :other_regions_new_subscriber_availability
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @other_regions_new_subscriber_availability = args[:other_regions_new_subscriber_availability] if args.key?(:other_regions_new_subscriber_availability)
        end
      end
      
      # Configuration for any new locations Play may launch in for a single offer
      # phase.
      class OtherRegionsSubscriptionOfferPhaseConfig
        include Google::Apis::Core::Hashable
      
        # Pricing information for any new locations Play may launch in.
        # Corresponds to the JSON property `absoluteDiscounts`
        # @return [Google::Apis::AndroidpublisherV3::OtherRegionsSubscriptionOfferPhasePrices]
        attr_accessor :absolute_discounts
      
        # Pricing information for any new locations Play may launch in.
        # Corresponds to the JSON property `otherRegionsPrices`
        # @return [Google::Apis::AndroidpublisherV3::OtherRegionsSubscriptionOfferPhasePrices]
        attr_accessor :other_regions_prices
      
        # The fraction of the base plan price prorated over the phase duration that the
        # user pays for this offer phase. For example, if the base plan price for this
        # region is $12 for a period of 1 year, then a 50% discount for a phase of a
        # duration of 3 months would correspond to a price of $1.50. The discount must
        # be specified as a fraction strictly larger than 0 and strictly smaller than 1.
        # The resulting price will be rounded to the nearest billable unit (e.g. cents
        # for USD). The relative discount is considered invalid if the discounted price
        # ends up being smaller than the minimum price allowed in any new locations Play
        # may launch in.
        # Corresponds to the JSON property `relativeDiscount`
        # @return [Float]
        attr_accessor :relative_discount
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @absolute_discounts = args[:absolute_discounts] if args.key?(:absolute_discounts)
          @other_regions_prices = args[:other_regions_prices] if args.key?(:other_regions_prices)
          @relative_discount = args[:relative_discount] if args.key?(:relative_discount)
        end
      end
      
      # Pricing information for any new locations Play may launch in.
      class OtherRegionsSubscriptionOfferPhasePrices
        include Google::Apis::Core::Hashable
      
        # Represents an amount of money with its currency type.
        # Corresponds to the JSON property `eurPrice`
        # @return [Google::Apis::AndroidpublisherV3::Money]
        attr_accessor :eur_price
      
        # Represents an amount of money with its currency type.
        # Corresponds to the JSON property `usdPrice`
        # @return [Google::Apis::AndroidpublisherV3::Money]
        attr_accessor :usd_price
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @eur_price = args[:eur_price] if args.key?(:eur_price)
          @usd_price = args[:usd_price] if args.key?(:usd_price)
        end
      end
      
      # Information about the current page. List operations that supports paging
      # return only one "page" of results. This protocol buffer message describes the
      # page that has been returned.
      class PageInfo
        include Google::Apis::Core::Hashable
      
        # Maximum number of results returned in one page. ! The number of results
        # included in the API response.
        # Corresponds to the JSON property `resultPerPage`
        # @return [Fixnum]
        attr_accessor :result_per_page
      
        # Index of the first result returned in the current page.
        # Corresponds to the JSON property `startIndex`
        # @return [Fixnum]
        attr_accessor :start_index
      
        # Total number of results available on the backend ! The total number of results
        # in the result set.
        # Corresponds to the JSON property `totalResults`
        # @return [Fixnum]
        attr_accessor :total_results
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @result_per_page = args[:result_per_page] if args.key?(:result_per_page)
          @start_index = args[:start_index] if args.key?(:start_index)
          @total_results = args[:total_results] if args.key?(:total_results)
        end
      end
      
      # A partial refund of a transaction.
      class PartialRefund
        include Google::Apis::Core::Hashable
      
        # Required. A unique id distinguishing this partial refund. If the refund is
        # successful, subsequent refunds with the same id will fail. Must be unique
        # across refunds for one individual transaction.
        # Corresponds to the JSON property `refundId`
        # @return [String]
        attr_accessor :refund_id
      
        # Definition of a price, i.e. currency and units.
        # Corresponds to the JSON property `refundPreTaxAmount`
        # @return [Google::Apis::AndroidpublisherV3::Price]
        attr_accessor :refund_pre_tax_amount
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @refund_id = args[:refund_id] if args.key?(:refund_id)
          @refund_pre_tax_amount = args[:refund_pre_tax_amount] if args.key?(:refund_pre_tax_amount)
        end
      end
      
      # Information specific to a subscription in paused state.
      class PausedStateContext
        include Google::Apis::Core::Hashable
      
        # Time at which the subscription will be automatically resumed.
        # Corresponds to the JSON property `autoResumeTime`
        # @return [String]
        attr_accessor :auto_resume_time
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @auto_resume_time = args[:auto_resume_time] if args.key?(:auto_resume_time)
        end
      end
      
      # Represents a base plan that does not automatically renew at the end of the
      # base plan, and must be manually renewed by the user.
      class PrepaidBasePlanType
        include Google::Apis::Core::Hashable
      
        # Required. Subscription period, specified in ISO 8601 format. For a list of
        # acceptable billing periods, refer to the help center.
        # Corresponds to the JSON property `billingPeriodDuration`
        # @return [String]
        attr_accessor :billing_period_duration
      
        # Whether users should be able to extend this prepaid base plan in Google Play
        # surfaces. Defaults to TIME_EXTENSION_ACTIVE if not specified.
        # Corresponds to the JSON property `timeExtension`
        # @return [String]
        attr_accessor :time_extension
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @billing_period_duration = args[:billing_period_duration] if args.key?(:billing_period_duration)
          @time_extension = args[:time_extension] if args.key?(:time_extension)
        end
      end
      
      # Information related to a prepaid plan.
      class PrepaidPlan
        include Google::Apis::Core::Hashable
      
        # If present, this is the time after which top up purchases are allowed for the
        # prepaid plan. Will not be present for expired prepaid plans.
        # Corresponds to the JSON property `allowExtendAfterTime`
        # @return [String]
        attr_accessor :allow_extend_after_time
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @allow_extend_after_time = args[:allow_extend_after_time] if args.key?(:allow_extend_after_time)
        end
      end
      
      # Definition of a price, i.e. currency and units.
      class Price
        include Google::Apis::Core::Hashable
      
        # 3 letter Currency code, as defined by ISO 4217. See java/com/google/common/
        # money/CurrencyCode.java
        # Corresponds to the JSON property `currency`
        # @return [String]
        attr_accessor :currency
      
        # Price in 1/million of the currency base unit, represented as a string.
        # Corresponds to the JSON property `priceMicros`
        # @return [String]
        attr_accessor :price_micros
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @currency = args[:currency] if args.key?(:currency)
          @price_micros = args[:price_micros] if args.key?(:price_micros)
        end
      end
      
      # A ProductPurchase resource indicates the status of a user's inapp product
      # purchase.
      class ProductPurchase
        include Google::Apis::Core::Hashable
      
        # The acknowledgement state of the inapp product. Possible values are: 0. Yet to
        # be acknowledged 1. Acknowledged
        # Corresponds to the JSON property `acknowledgementState`
        # @return [Fixnum]
        attr_accessor :acknowledgement_state
      
        # The consumption state of the inapp product. Possible values are: 0. Yet to be
        # consumed 1. Consumed
        # Corresponds to the JSON property `consumptionState`
        # @return [Fixnum]
        attr_accessor :consumption_state
      
        # A developer-specified string that contains supplemental information about an
        # order.
        # Corresponds to the JSON property `developerPayload`
        # @return [String]
        attr_accessor :developer_payload
      
        # This kind represents an inappPurchase object in the androidpublisher service.
        # Corresponds to the JSON property `kind`
        # @return [String]
        attr_accessor :kind
      
        # An obfuscated version of the id that is uniquely associated with the user's
        # account in your app. Only present if specified using https://developer.android.
        # com/reference/com/android/billingclient/api/BillingFlowParams.Builder#
        # setobfuscatedaccountid when the purchase was made.
        # Corresponds to the JSON property `obfuscatedExternalAccountId`
        # @return [String]
        attr_accessor :obfuscated_external_account_id
      
        # An obfuscated version of the id that is uniquely associated with the user's
        # profile in your app. Only present if specified using https://developer.android.
        # com/reference/com/android/billingclient/api/BillingFlowParams.Builder#
        # setobfuscatedprofileid when the purchase was made.
        # Corresponds to the JSON property `obfuscatedExternalProfileId`
        # @return [String]
        attr_accessor :obfuscated_external_profile_id
      
        # The order id associated with the purchase of the inapp product.
        # Corresponds to the JSON property `orderId`
        # @return [String]
        attr_accessor :order_id
      
        # The inapp product SKU. May not be present.
        # Corresponds to the JSON property `productId`
        # @return [String]
        attr_accessor :product_id
      
        # The purchase state of the order. Possible values are: 0. Purchased 1. Canceled
        # 2. Pending
        # Corresponds to the JSON property `purchaseState`
        # @return [Fixnum]
        attr_accessor :purchase_state
      
        # The time the product was purchased, in milliseconds since the epoch (Jan 1,
        # 1970).
        # Corresponds to the JSON property `purchaseTimeMillis`
        # @return [Fixnum]
        attr_accessor :purchase_time_millis
      
        # The purchase token generated to identify this purchase. May not be present.
        # Corresponds to the JSON property `purchaseToken`
        # @return [String]
        attr_accessor :purchase_token
      
        # The type of purchase of the inapp product. This field is only set if this
        # purchase was not made using the standard in-app billing flow. Possible values
        # are: 0. Test (i.e. purchased from a license testing account) 1. Promo (i.e.
        # purchased using a promo code) 2. Rewarded (i.e. from watching a video ad
        # instead of paying)
        # Corresponds to the JSON property `purchaseType`
        # @return [Fixnum]
        attr_accessor :purchase_type
      
        # The quantity associated with the purchase of the inapp product. If not present,
        # the quantity is 1.
        # Corresponds to the JSON property `quantity`
        # @return [Fixnum]
        attr_accessor :quantity
      
        # ISO 3166-1 alpha-2 billing region code of the user at the time the product was
        # granted.
        # Corresponds to the JSON property `regionCode`
        # @return [String]
        attr_accessor :region_code
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @acknowledgement_state = args[:acknowledgement_state] if args.key?(:acknowledgement_state)
          @consumption_state = args[:consumption_state] if args.key?(:consumption_state)
          @developer_payload = args[:developer_payload] if args.key?(:developer_payload)
          @kind = args[:kind] if args.key?(:kind)
          @obfuscated_external_account_id = args[:obfuscated_external_account_id] if args.key?(:obfuscated_external_account_id)
          @obfuscated_external_profile_id = args[:obfuscated_external_profile_id] if args.key?(:obfuscated_external_profile_id)
          @order_id = args[:order_id] if args.key?(:order_id)
          @product_id = args[:product_id] if args.key?(:product_id)
          @purchase_state = args[:purchase_state] if args.key?(:purchase_state)
          @purchase_time_millis = args[:purchase_time_millis] if args.key?(:purchase_time_millis)
          @purchase_token = args[:purchase_token] if args.key?(:purchase_token)
          @purchase_type = args[:purchase_type] if args.key?(:purchase_type)
          @quantity = args[:quantity] if args.key?(:quantity)
          @region_code = args[:region_code] if args.key?(:region_code)
        end
      end
      
      # Request for the product.purchases.acknowledge API.
      class ProductPurchasesAcknowledgeRequest
        include Google::Apis::Core::Hashable
      
        # Payload to attach to the purchase.
        # Corresponds to the JSON property `developerPayload`
        # @return [String]
        attr_accessor :developer_payload
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @developer_payload = args[:developer_payload] if args.key?(:developer_payload)
        end
      end
      
      # Represents a transaction that is part of a recurring series of payments. This
      # can be a subscription or a one-time product with multiple payments (such as
      # preorder).
      class RecurringExternalTransaction
        include Google::Apis::Core::Hashable
      
        # Details of an external subscription.
        # Corresponds to the JSON property `externalSubscription`
        # @return [Google::Apis::AndroidpublisherV3::ExternalSubscription]
        attr_accessor :external_subscription
      
        # Input only. Provided during the call to Create. Retrieved from the client when
        # the alternative billing flow is launched. Required only for the initial
        # purchase.
        # Corresponds to the JSON property `externalTransactionToken`
        # @return [String]
        attr_accessor :external_transaction_token
      
        # The external transaction id of the first transaction of this recurring series
        # of transactions. For example, for a subscription this would be the transaction
        # id of the first payment. Required when creating recurring external
        # transactions.
        # Corresponds to the JSON property `initialExternalTransactionId`
        # @return [String]
        attr_accessor :initial_external_transaction_id
      
        # Input only. Provided during the call to Create. Must only be used when
        # migrating a subscription from manual monthly reporting to automated reporting.
        # Corresponds to the JSON property `migratedTransactionProgram`
        # @return [String]
        attr_accessor :migrated_transaction_program
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @external_subscription = args[:external_subscription] if args.key?(:external_subscription)
          @external_transaction_token = args[:external_transaction_token] if args.key?(:external_transaction_token)
          @initial_external_transaction_id = args[:initial_external_transaction_id] if args.key?(:initial_external_transaction_id)
          @migrated_transaction_program = args[:migrated_transaction_program] if args.key?(:migrated_transaction_program)
        end
      end
      
      # A request to refund an existing external transaction.
      class RefundExternalTransactionRequest
        include Google::Apis::Core::Hashable
      
        # A full refund of the remaining amount of a transaction.
        # Corresponds to the JSON property `fullRefund`
        # @return [Google::Apis::AndroidpublisherV3::FullRefund]
        attr_accessor :full_refund
      
        # A partial refund of a transaction.
        # Corresponds to the JSON property `partialRefund`
        # @return [Google::Apis::AndroidpublisherV3::PartialRefund]
        attr_accessor :partial_refund
      
        # Required. The time that the transaction was refunded.
        # Corresponds to the JSON property `refundTime`
        # @return [String]
        attr_accessor :refund_time
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @full_refund = args[:full_refund] if args.key?(:full_refund)
          @partial_refund = args[:partial_refund] if args.key?(:partial_refund)
          @refund_time = args[:refund_time] if args.key?(:refund_time)
        end
      end
      
      # Configuration for a base plan specific to a region.
      class RegionalBasePlanConfig
        include Google::Apis::Core::Hashable
      
        # Whether the base plan in the specified region is available for new subscribers.
        # Existing subscribers will not have their subscription canceled if this value
        # is set to false. If not specified, this will default to false.
        # Corresponds to the JSON property `newSubscriberAvailability`
        # @return [Boolean]
        attr_accessor :new_subscriber_availability
        alias_method :new_subscriber_availability?, :new_subscriber_availability
      
        # Represents an amount of money with its currency type.
        # Corresponds to the JSON property `price`
        # @return [Google::Apis::AndroidpublisherV3::Money]
        attr_accessor :price
      
        # Required. Region code this configuration applies to, as defined by ISO 3166-2,
        # e.g. "US".
        # Corresponds to the JSON property `regionCode`
        # @return [String]
        attr_accessor :region_code
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @new_subscriber_availability = args[:new_subscriber_availability] if args.key?(:new_subscriber_availability)
          @price = args[:price] if args.key?(:price)
          @region_code = args[:region_code] if args.key?(:region_code)
        end
      end
      
      # Configuration for a price migration.
      class RegionalPriceMigrationConfig
        include Google::Apis::Core::Hashable
      
        # Required. The cutoff time for historical prices that subscribers can remain
        # paying. Subscribers on prices which were available at this cutoff time or
        # later will stay on their existing price. Subscribers on older prices will be
        # migrated to the currently-offered price. The migrated subscribers will receive
        # a notification that they will be paying a different price. Subscribers who do
        # not agree to the new price will have their subscription ended at the next
        # renewal.
        # Corresponds to the JSON property `oldestAllowedPriceVersionTime`
        # @return [String]
        attr_accessor :oldest_allowed_price_version_time
      
        # Optional. The behavior the caller wants users to see when there is a price
        # increase during migration. If left unset, the behavior defaults to
        # PRICE_INCREASE_TYPE_OPT_IN. Note that the first opt-out price increase
        # migration for each app must be initiated in Play Console.
        # Corresponds to the JSON property `priceIncreaseType`
        # @return [String]
        attr_accessor :price_increase_type
      
        # Required. Region code this configuration applies to, as defined by ISO 3166-2,
        # e.g. "US".
        # Corresponds to the JSON property `regionCode`
        # @return [String]
        attr_accessor :region_code
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @oldest_allowed_price_version_time = args[:oldest_allowed_price_version_time] if args.key?(:oldest_allowed_price_version_time)
          @price_increase_type = args[:price_increase_type] if args.key?(:price_increase_type)
          @region_code = args[:region_code] if args.key?(:region_code)
        end
      end
      
      # Configuration for a subscription offer in a single region.
      class RegionalSubscriptionOfferConfig
        include Google::Apis::Core::Hashable
      
        # Whether the subscription offer in the specified region is available for new
        # subscribers. Existing subscribers will not have their subscription cancelled
        # if this value is set to false. If not specified, this will default to false.
        # Corresponds to the JSON property `newSubscriberAvailability`
        # @return [Boolean]
        attr_accessor :new_subscriber_availability
        alias_method :new_subscriber_availability?, :new_subscriber_availability
      
        # Required. Immutable. Region code this configuration applies to, as defined by
        # ISO 3166-2, e.g. "US".
        # Corresponds to the JSON property `regionCode`
        # @return [String]
        attr_accessor :region_code
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @new_subscriber_availability = args[:new_subscriber_availability] if args.key?(:new_subscriber_availability)
          @region_code = args[:region_code] if args.key?(:region_code)
        end
      end
      
      # Configuration for a single phase of a subscription offer in a single region.
      class RegionalSubscriptionOfferPhaseConfig
        include Google::Apis::Core::Hashable
      
        # Represents an amount of money with its currency type.
        # Corresponds to the JSON property `absoluteDiscount`
        # @return [Google::Apis::AndroidpublisherV3::Money]
        attr_accessor :absolute_discount
      
        # Represents an amount of money with its currency type.
        # Corresponds to the JSON property `price`
        # @return [Google::Apis::AndroidpublisherV3::Money]
        attr_accessor :price
      
        # Required. Immutable. The region to which this config applies.
        # Corresponds to the JSON property `regionCode`
        # @return [String]
        attr_accessor :region_code
      
        # The fraction of the base plan price prorated over the phase duration that the
        # user pays for this offer phase. For example, if the base plan price for this
        # region is $12 for a period of 1 year, then a 50% discount for a phase of a
        # duration of 3 months would correspond to a price of $1.50. The discount must
        # be specified as a fraction strictly larger than 0 and strictly smaller than 1.
        # The resulting price will be rounded to the nearest billable unit (e.g. cents
        # for USD). The relative discount is considered invalid if the discounted price
        # ends up being smaller than the minimum price allowed in this region.
        # Corresponds to the JSON property `relativeDiscount`
        # @return [Float]
        attr_accessor :relative_discount
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @absolute_discount = args[:absolute_discount] if args.key?(:absolute_discount)
          @price = args[:price] if args.key?(:price)
          @region_code = args[:region_code] if args.key?(:region_code)
          @relative_discount = args[:relative_discount] if args.key?(:relative_discount)
        end
      end
      
      # Specified details about taxation in a given geographical region.
      class RegionalTaxRateInfo
        include Google::Apis::Core::Hashable
      
        # You must tell us if your app contains streaming products to correctly charge
        # US state and local sales tax. Field only supported in United States.
        # Corresponds to the JSON property `eligibleForStreamingServiceTaxRate`
        # @return [Boolean]
        attr_accessor :eligible_for_streaming_service_tax_rate
        alias_method :eligible_for_streaming_service_tax_rate?, :eligible_for_streaming_service_tax_rate
      
        # To collect communications or amusement taxes in the United States, choose the
        # appropriate tax category. [Learn more](https://support.google.com/googleplay/
        # android-developer/answer/10463498#streaming_tax).
        # Corresponds to the JSON property `streamingTaxType`
        # @return [String]
        attr_accessor :streaming_tax_type
      
        # Tax tier to specify reduced tax rate. Developers who sell digital news,
        # magazines, newspapers, books, or audiobooks in various regions may be eligible
        # for reduced tax rates. [Learn more](https://support.google.com/googleplay/
        # android-developer/answer/10463498).
        # Corresponds to the JSON property `taxTier`
        # @return [String]
        attr_accessor :tax_tier
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @eligible_for_streaming_service_tax_rate = args[:eligible_for_streaming_service_tax_rate] if args.key?(:eligible_for_streaming_service_tax_rate)
          @streaming_tax_type = args[:streaming_tax_type] if args.key?(:streaming_tax_type)
          @tax_tier = args[:tax_tier] if args.key?(:tax_tier)
        end
      end
      
      # The version of the available regions being used for the specified resource.
      class RegionsVersion
        include Google::Apis::Core::Hashable
      
        # Required. A string representing the version of available regions being used
        # for the specified resource. Regional prices for the resource have to be
        # specified according to the information published in [this article](https://
        # support.google.com/googleplay/android-developer/answer/10532353). Each time
        # the supported locations substantially change, the version will be incremented.
        # Using this field will ensure that creating and updating the resource with an
        # older region's version and set of regional prices and currencies will succeed
        # even though a new version is available. The latest version is 2022/02.
        # Corresponds to the JSON property `version`
        # @return [String]
        attr_accessor :version
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @version = args[:version] if args.key?(:version)
        end
      end
      
      # Information specific to cancellations caused by subscription replacement.
      class ReplacementCancellation
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # An Android app review.
      class Review
        include Google::Apis::Core::Hashable
      
        # The name of the user who wrote the review.
        # Corresponds to the JSON property `authorName`
        # @return [String]
        attr_accessor :author_name
      
        # A repeated field containing comments for the review.
        # Corresponds to the JSON property `comments`
        # @return [Array<Google::Apis::AndroidpublisherV3::Comment>]
        attr_accessor :comments
      
        # Unique identifier for this review.
        # Corresponds to the JSON property `reviewId`
        # @return [String]
        attr_accessor :review_id
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @author_name = args[:author_name] if args.key?(:author_name)
          @comments = args[:comments] if args.key?(:comments)
          @review_id = args[:review_id] if args.key?(:review_id)
        end
      end
      
      # The result of replying/updating a reply to review.
      class ReviewReplyResult
        include Google::Apis::Core::Hashable
      
        # A Timestamp represents a point in time independent of any time zone or local
        # calendar, encoded as a count of seconds and fractions of seconds at nanosecond
        # resolution. The count is relative to an epoch at UTC midnight on January 1,
        # 1970.
        # Corresponds to the JSON property `lastEdited`
        # @return [Google::Apis::AndroidpublisherV3::Timestamp]
        attr_accessor :last_edited
      
        # The reply text that was applied.
        # Corresponds to the JSON property `replyText`
        # @return [String]
        attr_accessor :reply_text
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @last_edited = args[:last_edited] if args.key?(:last_edited)
          @reply_text = args[:reply_text] if args.key?(:reply_text)
        end
      end
      
      # Response listing reviews.
      class ReviewsListResponse
        include Google::Apis::Core::Hashable
      
        # Information about the current page. List operations that supports paging
        # return only one "page" of results. This protocol buffer message describes the
        # page that has been returned.
        # Corresponds to the JSON property `pageInfo`
        # @return [Google::Apis::AndroidpublisherV3::PageInfo]
        attr_accessor :page_info
      
        # List of reviews.
        # Corresponds to the JSON property `reviews`
        # @return [Array<Google::Apis::AndroidpublisherV3::Review>]
        attr_accessor :reviews
      
        # Pagination information returned by a List operation when token pagination is
        # enabled. List operations that supports paging return only one "page" of
        # results. This protocol buffer message describes the page that has been
        # returned. When using token pagination, clients should use the next/previous
        # token to get another page of the result. The presence or absence of next/
        # previous token indicates whether a next/previous page is available and
        # provides a mean of accessing this page. ListRequest.page_token should be set
        # to either next_page_token or previous_page_token to access another page.
        # Corresponds to the JSON property `tokenPagination`
        # @return [Google::Apis::AndroidpublisherV3::TokenPagination]
        attr_accessor :token_pagination
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @page_info = args[:page_info] if args.key?(:page_info)
          @reviews = args[:reviews] if args.key?(:reviews)
          @token_pagination = args[:token_pagination] if args.key?(:token_pagination)
        end
      end
      
      # Request to reply to review or update existing reply.
      class ReviewsReplyRequest
        include Google::Apis::Core::Hashable
      
        # The text to set as the reply. Replies of more than approximately 350
        # characters will be rejected. HTML tags will be stripped.
        # Corresponds to the JSON property `replyText`
        # @return [String]
        attr_accessor :reply_text
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @reply_text = args[:reply_text] if args.key?(:reply_text)
        end
      end
      
      # Response on status of replying to a review.
      class ReviewsReplyResponse
        include Google::Apis::Core::Hashable
      
        # The result of replying/updating a reply to review.
        # Corresponds to the JSON property `result`
        # @return [Google::Apis::AndroidpublisherV3::ReviewReplyResult]
        attr_accessor :result
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @result = args[:result] if args.key?(:result)
        end
      end
      
      # Represents a screen density.
      class ScreenDensity
        include Google::Apis::Core::Hashable
      
        # Alias for a screen density.
        # Corresponds to the JSON property `densityAlias`
        # @return [String]
        attr_accessor :density_alias
      
        # Value for density dpi.
        # Corresponds to the JSON property `densityDpi`
        # @return [Fixnum]
        attr_accessor :density_dpi
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @density_alias = args[:density_alias] if args.key?(:density_alias)
          @density_dpi = args[:density_dpi] if args.key?(:density_dpi)
        end
      end
      
      # Targeting based on screen density.
      class ScreenDensityTargeting
        include Google::Apis::Core::Hashable
      
        # Targeting of other sibling directories that were in the Bundle. For main
        # splits this is targeting of other main splits.
        # Corresponds to the JSON property `alternatives`
        # @return [Array<Google::Apis::AndroidpublisherV3::ScreenDensity>]
        attr_accessor :alternatives
      
        # Value of a screen density.
        # Corresponds to the JSON property `value`
        # @return [Array<Google::Apis::AndroidpublisherV3::ScreenDensity>]
        attr_accessor :value
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @alternatives = args[:alternatives] if args.key?(:alternatives)
          @value = args[:value] if args.key?(:value)
        end
      end
      
      # Represents an sdk version.
      class SdkVersion
        include Google::Apis::Core::Hashable
      
        # Inclusive minimum value of an sdk version.
        # Corresponds to the JSON property `min`
        # @return [Fixnum]
        attr_accessor :min
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @min = args[:min] if args.key?(:min)
        end
      end
      
      # Targeting based on sdk version.
      class SdkVersionTargeting
        include Google::Apis::Core::Hashable
      
        # Targeting of other sibling directories that were in the Bundle. For main
        # splits this is targeting of other main splits.
        # Corresponds to the JSON property `alternatives`
        # @return [Array<Google::Apis::AndroidpublisherV3::SdkVersion>]
        attr_accessor :alternatives
      
        # Value of an sdk version.
        # Corresponds to the JSON property `value`
        # @return [Array<Google::Apis::AndroidpublisherV3::SdkVersion>]
        attr_accessor :value
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @alternatives = args[:alternatives] if args.key?(:alternatives)
          @value = args[:value] if args.key?(:value)
        end
      end
      
      # Holds data specific to Split APKs.
      class SplitApkMetadata
        include Google::Apis::Core::Hashable
      
        # Indicates whether this APK is the main split of the module.
        # Corresponds to the JSON property `isMasterSplit`
        # @return [Boolean]
        attr_accessor :is_master_split
        alias_method :is_master_split?, :is_master_split
      
        # Id of the split.
        # Corresponds to the JSON property `splitId`
        # @return [String]
        attr_accessor :split_id
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @is_master_split = args[:is_master_split] if args.key?(:is_master_split)
          @split_id = args[:split_id] if args.key?(:split_id)
        end
      end
      
      # Variant is a group of APKs that covers a part of the device configuration
      # space. APKs from multiple variants are never combined on one device.
      class SplitApkVariant
        include Google::Apis::Core::Hashable
      
        # Set of APKs, one set per module.
        # Corresponds to the JSON property `apkSet`
        # @return [Array<Google::Apis::AndroidpublisherV3::ApkSet>]
        attr_accessor :apk_set
      
        # Targeting on the level of variants.
        # Corresponds to the JSON property `targeting`
        # @return [Google::Apis::AndroidpublisherV3::VariantTargeting]
        attr_accessor :targeting
      
        # Number of the variant, starting at 0 (unless overridden). A device will
        # receive APKs from the first variant that matches the device configuration,
        # with higher variant numbers having priority over lower variant numbers.
        # Corresponds to the JSON property `variantNumber`
        # @return [Fixnum]
        attr_accessor :variant_number
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @apk_set = args[:apk_set] if args.key?(:apk_set)
          @targeting = args[:targeting] if args.key?(:targeting)
          @variant_number = args[:variant_number] if args.key?(:variant_number)
        end
      end
      
      # Holds data specific to Standalone APKs.
      class StandaloneApkMetadata
        include Google::Apis::Core::Hashable
      
        # Names of the modules fused in this standalone APK.
        # Corresponds to the JSON property `fusedModuleName`
        # @return [Array<String>]
        attr_accessor :fused_module_name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @fused_module_name = args[:fused_module_name] if args.key?(:fused_module_name)
        end
      end
      
      # Information associated with purchases made with 'Subscribe with Google'.
      class SubscribeWithGoogleInfo
        include Google::Apis::Core::Hashable
      
        # The email address of the user when the subscription was purchased.
        # Corresponds to the JSON property `emailAddress`
        # @return [String]
        attr_accessor :email_address
      
        # The family name of the user when the subscription was purchased.
        # Corresponds to the JSON property `familyName`
        # @return [String]
        attr_accessor :family_name
      
        # The given name of the user when the subscription was purchased.
        # Corresponds to the JSON property `givenName`
        # @return [String]
        attr_accessor :given_name
      
        # The Google profile id of the user when the subscription was purchased.
        # Corresponds to the JSON property `profileId`
        # @return [String]
        attr_accessor :profile_id
      
        # The profile name of the user when the subscription was purchased.
        # Corresponds to the JSON property `profileName`
        # @return [String]
        attr_accessor :profile_name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @email_address = args[:email_address] if args.key?(:email_address)
          @family_name = args[:family_name] if args.key?(:family_name)
          @given_name = args[:given_name] if args.key?(:given_name)
          @profile_id = args[:profile_id] if args.key?(:profile_id)
          @profile_name = args[:profile_name] if args.key?(:profile_name)
        end
      end
      
      # A single subscription for an app.
      class Subscription
        include Google::Apis::Core::Hashable
      
        # Output only. Whether this subscription is archived. Archived subscriptions are
        # not available to any subscriber any longer, cannot be updated, and are not
        # returned in list requests unless the show archived flag is passed in.
        # Corresponds to the JSON property `archived`
        # @return [Boolean]
        attr_accessor :archived
        alias_method :archived?, :archived
      
        # The set of base plans for this subscription. Represents the prices and
        # duration of the subscription if no other offers apply.
        # Corresponds to the JSON property `basePlans`
        # @return [Array<Google::Apis::AndroidpublisherV3::BasePlan>]
        attr_accessor :base_plans
      
        # Required. List of localized listings for this subscription. Must contain at
        # least an entry for the default language of the parent app.
        # Corresponds to the JSON property `listings`
        # @return [Array<Google::Apis::AndroidpublisherV3::SubscriptionListing>]
        attr_accessor :listings
      
        # Immutable. Package name of the parent app.
        # Corresponds to the JSON property `packageName`
        # @return [String]
        attr_accessor :package_name
      
        # Immutable. Unique product ID of the product. Unique within the parent app.
        # Product IDs must be composed of lower-case letters (a-z), numbers (0-9),
        # underscores (_) and dots (.). It must start with a lower-case letter or number,
        # and be between 1 and 40 (inclusive) characters in length.
        # Corresponds to the JSON property `productId`
        # @return [String]
        attr_accessor :product_id
      
        # Details about taxation, Google Play policy and legal compliance for
        # subscription products.
        # Corresponds to the JSON property `taxAndComplianceSettings`
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionTaxAndComplianceSettings]
        attr_accessor :tax_and_compliance_settings
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @archived = args[:archived] if args.key?(:archived)
          @base_plans = args[:base_plans] if args.key?(:base_plans)
          @listings = args[:listings] if args.key?(:listings)
          @package_name = args[:package_name] if args.key?(:package_name)
          @product_id = args[:product_id] if args.key?(:product_id)
          @tax_and_compliance_settings = args[:tax_and_compliance_settings] if args.key?(:tax_and_compliance_settings)
        end
      end
      
      # Information provided by the user when they complete the subscription
      # cancellation flow (cancellation reason survey).
      class SubscriptionCancelSurveyResult
        include Google::Apis::Core::Hashable
      
        # The cancellation reason the user chose in the survey. Possible values are: 0.
        # Other 1. I don't use this service enough 2. Technical issues 3. Cost-related
        # reasons 4. I found a better app
        # Corresponds to the JSON property `cancelSurveyReason`
        # @return [Fixnum]
        attr_accessor :cancel_survey_reason
      
        # The customized input cancel reason from the user. Only present when
        # cancelReason is 0.
        # Corresponds to the JSON property `userInputCancelReason`
        # @return [String]
        attr_accessor :user_input_cancel_reason
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @cancel_survey_reason = args[:cancel_survey_reason] if args.key?(:cancel_survey_reason)
          @user_input_cancel_reason = args[:user_input_cancel_reason] if args.key?(:user_input_cancel_reason)
        end
      end
      
      # A SubscriptionDeferralInfo contains the data needed to defer a subscription
      # purchase to a future expiry time.
      class SubscriptionDeferralInfo
        include Google::Apis::Core::Hashable
      
        # The desired next expiry time to assign to the subscription, in milliseconds
        # since the Epoch. The given time must be later/greater than the current expiry
        # time for the subscription.
        # Corresponds to the JSON property `desiredExpiryTimeMillis`
        # @return [Fixnum]
        attr_accessor :desired_expiry_time_millis
      
        # The expected expiry time for the subscription. If the current expiry time for
        # the subscription is not the value specified here, the deferral will not occur.
        # Corresponds to the JSON property `expectedExpiryTimeMillis`
        # @return [Fixnum]
        attr_accessor :expected_expiry_time_millis
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @desired_expiry_time_millis = args[:desired_expiry_time_millis] if args.key?(:desired_expiry_time_millis)
          @expected_expiry_time_millis = args[:expected_expiry_time_millis] if args.key?(:expected_expiry_time_millis)
        end
      end
      
      # Price change related information of a subscription item.
      class SubscriptionItemPriceChangeDetails
        include Google::Apis::Core::Hashable
      
        # The renewal time at which the price change will become effective for the user.
        # This is subject to change(to a future time) due to cases where the renewal
        # time shifts like pause. This field is only populated if the price change has
        # not taken effect.
        # Corresponds to the JSON property `expectedNewPriceChargeTime`
        # @return [String]
        attr_accessor :expected_new_price_charge_time
      
        # Represents an amount of money with its currency type.
        # Corresponds to the JSON property `newPrice`
        # @return [Google::Apis::AndroidpublisherV3::Money]
        attr_accessor :new_price
      
        # Price change mode specifies how the subscription item price is changing.
        # Corresponds to the JSON property `priceChangeMode`
        # @return [String]
        attr_accessor :price_change_mode
      
        # State the price change is currently in.
        # Corresponds to the JSON property `priceChangeState`
        # @return [String]
        attr_accessor :price_change_state
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @expected_new_price_charge_time = args[:expected_new_price_charge_time] if args.key?(:expected_new_price_charge_time)
          @new_price = args[:new_price] if args.key?(:new_price)
          @price_change_mode = args[:price_change_mode] if args.key?(:price_change_mode)
          @price_change_state = args[:price_change_state] if args.key?(:price_change_state)
        end
      end
      
      # The consumer-visible metadata of a subscription.
      class SubscriptionListing
        include Google::Apis::Core::Hashable
      
        # A list of benefits shown to the user on platforms such as the Play Store and
        # in restoration flows in the language of this listing. Plain text. Ordered list
        # of at most four benefits.
        # Corresponds to the JSON property `benefits`
        # @return [Array<String>]
        attr_accessor :benefits
      
        # The description of this subscription in the language of this listing. Maximum
        # length - 80 characters. Plain text.
        # Corresponds to the JSON property `description`
        # @return [String]
        attr_accessor :description
      
        # Required. The language of this listing, as defined by BCP-47, e.g. "en-US".
        # Corresponds to the JSON property `languageCode`
        # @return [String]
        attr_accessor :language_code
      
        # Required. The title of this subscription in the language of this listing.
        # Plain text.
        # Corresponds to the JSON property `title`
        # @return [String]
        attr_accessor :title
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @benefits = args[:benefits] if args.key?(:benefits)
          @description = args[:description] if args.key?(:description)
          @language_code = args[:language_code] if args.key?(:language_code)
          @title = args[:title] if args.key?(:title)
        end
      end
      
      # A single, temporary offer
      class SubscriptionOffer
        include Google::Apis::Core::Hashable
      
        # Required. Immutable. The ID of the base plan to which this offer is an
        # extension.
        # Corresponds to the JSON property `basePlanId`
        # @return [String]
        attr_accessor :base_plan_id
      
        # Required. Immutable. Unique ID of this subscription offer. Must be unique
        # within the base plan.
        # Corresponds to the JSON property `offerId`
        # @return [String]
        attr_accessor :offer_id
      
        # List of up to 20 custom tags specified for this offer, and returned to the app
        # through the billing library.
        # Corresponds to the JSON property `offerTags`
        # @return [Array<Google::Apis::AndroidpublisherV3::OfferTag>]
        attr_accessor :offer_tags
      
        # Configuration for any new locations Play may launch in specified on a
        # subscription offer.
        # Corresponds to the JSON property `otherRegionsConfig`
        # @return [Google::Apis::AndroidpublisherV3::OtherRegionsSubscriptionOfferConfig]
        attr_accessor :other_regions_config
      
        # Required. Immutable. The package name of the app the parent subscription
        # belongs to.
        # Corresponds to the JSON property `packageName`
        # @return [String]
        attr_accessor :package_name
      
        # Required. The phases of this subscription offer. Must contain at least one
        # entry, and may contain at most five. Users will always receive all these
        # phases in the specified order. Phases may not be added, removed, or reordered
        # after initial creation.
        # Corresponds to the JSON property `phases`
        # @return [Array<Google::Apis::AndroidpublisherV3::SubscriptionOfferPhase>]
        attr_accessor :phases
      
        # Required. Immutable. The ID of the parent subscription this offer belongs to.
        # Corresponds to the JSON property `productId`
        # @return [String]
        attr_accessor :product_id
      
        # Required. The region-specific configuration of this offer. Must contain at
        # least one entry.
        # Corresponds to the JSON property `regionalConfigs`
        # @return [Array<Google::Apis::AndroidpublisherV3::RegionalSubscriptionOfferConfig>]
        attr_accessor :regional_configs
      
        # Output only. The current state of this offer. Can be changed using Activate
        # and Deactivate actions. NB: the base plan state supersedes this state, so an
        # active offer may not be available if the base plan is not active.
        # Corresponds to the JSON property `state`
        # @return [String]
        attr_accessor :state
      
        # Defines the rule a user needs to satisfy to receive this offer.
        # Corresponds to the JSON property `targeting`
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionOfferTargeting]
        attr_accessor :targeting
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @base_plan_id = args[:base_plan_id] if args.key?(:base_plan_id)
          @offer_id = args[:offer_id] if args.key?(:offer_id)
          @offer_tags = args[:offer_tags] if args.key?(:offer_tags)
          @other_regions_config = args[:other_regions_config] if args.key?(:other_regions_config)
          @package_name = args[:package_name] if args.key?(:package_name)
          @phases = args[:phases] if args.key?(:phases)
          @product_id = args[:product_id] if args.key?(:product_id)
          @regional_configs = args[:regional_configs] if args.key?(:regional_configs)
          @state = args[:state] if args.key?(:state)
          @targeting = args[:targeting] if args.key?(:targeting)
        end
      end
      
      # A single phase of a subscription offer.
      class SubscriptionOfferPhase
        include Google::Apis::Core::Hashable
      
        # Required. The duration of a single recurrence of this phase. Specified in ISO
        # 8601 format.
        # Corresponds to the JSON property `duration`
        # @return [String]
        attr_accessor :duration
      
        # Configuration for any new locations Play may launch in for a single offer
        # phase.
        # Corresponds to the JSON property `otherRegionsConfig`
        # @return [Google::Apis::AndroidpublisherV3::OtherRegionsSubscriptionOfferPhaseConfig]
        attr_accessor :other_regions_config
      
        # Required. The number of times this phase repeats. If this offer phase is not
        # free, each recurrence charges the user the price of this offer phase.
        # Corresponds to the JSON property `recurrenceCount`
        # @return [Fixnum]
        attr_accessor :recurrence_count
      
        # Required. The region-specific configuration of this offer phase. This list
        # must contain exactly one entry for each region for which the subscription
        # offer has a regional config.
        # Corresponds to the JSON property `regionalConfigs`
        # @return [Array<Google::Apis::AndroidpublisherV3::RegionalSubscriptionOfferPhaseConfig>]
        attr_accessor :regional_configs
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @duration = args[:duration] if args.key?(:duration)
          @other_regions_config = args[:other_regions_config] if args.key?(:other_regions_config)
          @recurrence_count = args[:recurrence_count] if args.key?(:recurrence_count)
          @regional_configs = args[:regional_configs] if args.key?(:regional_configs)
        end
      end
      
      # Defines the rule a user needs to satisfy to receive this offer.
      class SubscriptionOfferTargeting
        include Google::Apis::Core::Hashable
      
        # Represents a targeting rule of the form: User never had `scope` before.
        # Corresponds to the JSON property `acquisitionRule`
        # @return [Google::Apis::AndroidpublisherV3::AcquisitionTargetingRule]
        attr_accessor :acquisition_rule
      
        # Represents a targeting rule of the form: User currently has `scope` [with
        # billing period `billing_period`].
        # Corresponds to the JSON property `upgradeRule`
        # @return [Google::Apis::AndroidpublisherV3::UpgradeTargetingRule]
        attr_accessor :upgrade_rule
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @acquisition_rule = args[:acquisition_rule] if args.key?(:acquisition_rule)
          @upgrade_rule = args[:upgrade_rule] if args.key?(:upgrade_rule)
        end
      end
      
      # Contains the price change information for a subscription that can be used to
      # control the user journey for the price change in the app. This can be in the
      # form of seeking confirmation from the user or tailoring the experience for a
      # successful conversion.
      class SubscriptionPriceChange
        include Google::Apis::Core::Hashable
      
        # Definition of a price, i.e. currency and units.
        # Corresponds to the JSON property `newPrice`
        # @return [Google::Apis::AndroidpublisherV3::Price]
        attr_accessor :new_price
      
        # The current state of the price change. Possible values are: 0. Outstanding:
        # State for a pending price change waiting for the user to agree. In this state,
        # you can optionally seek confirmation from the user using the In-App API. 1.
        # Accepted: State for an accepted price change that the subscription will renew
        # with unless it's canceled. The price change takes effect on a future date when
        # the subscription renews. Note that the change might not occur when the
        # subscription is renewed next.
        # Corresponds to the JSON property `state`
        # @return [Fixnum]
        attr_accessor :state
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @new_price = args[:new_price] if args.key?(:new_price)
          @state = args[:state] if args.key?(:state)
        end
      end
      
      # A SubscriptionPurchase resource indicates the status of a user's subscription
      # purchase.
      class SubscriptionPurchase
        include Google::Apis::Core::Hashable
      
        # The acknowledgement state of the subscription product. Possible values are: 0.
        # Yet to be acknowledged 1. Acknowledged
        # Corresponds to the JSON property `acknowledgementState`
        # @return [Fixnum]
        attr_accessor :acknowledgement_state
      
        # Whether the subscription will automatically be renewed when it reaches its
        # current expiry time.
        # Corresponds to the JSON property `autoRenewing`
        # @return [Boolean]
        attr_accessor :auto_renewing
        alias_method :auto_renewing?, :auto_renewing
      
        # Time at which the subscription will be automatically resumed, in milliseconds
        # since the Epoch. Only present if the user has requested to pause the
        # subscription.
        # Corresponds to the JSON property `autoResumeTimeMillis`
        # @return [Fixnum]
        attr_accessor :auto_resume_time_millis
      
        # The reason why a subscription was canceled or is not auto-renewing. Possible
        # values are: 0. User canceled the subscription 1. Subscription was canceled by
        # the system, for example because of a billing problem 2. Subscription was
        # replaced with a new subscription 3. Subscription was canceled by the developer
        # Corresponds to the JSON property `cancelReason`
        # @return [Fixnum]
        attr_accessor :cancel_reason
      
        # Information provided by the user when they complete the subscription
        # cancellation flow (cancellation reason survey).
        # Corresponds to the JSON property `cancelSurveyResult`
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionCancelSurveyResult]
        attr_accessor :cancel_survey_result
      
        # ISO 3166-1 alpha-2 billing country/region code of the user at the time the
        # subscription was granted.
        # Corresponds to the JSON property `countryCode`
        # @return [String]
        attr_accessor :country_code
      
        # A developer-specified string that contains supplemental information about an
        # order.
        # Corresponds to the JSON property `developerPayload`
        # @return [String]
        attr_accessor :developer_payload
      
        # The email address of the user when the subscription was purchased. Only
        # present for purchases made with 'Subscribe with Google'.
        # Corresponds to the JSON property `emailAddress`
        # @return [String]
        attr_accessor :email_address
      
        # Time at which the subscription will expire, in milliseconds since the Epoch.
        # Corresponds to the JSON property `expiryTimeMillis`
        # @return [Fixnum]
        attr_accessor :expiry_time_millis
      
        # User account identifier in the third-party service. Only present if account
        # linking happened as part of the subscription purchase flow.
        # Corresponds to the JSON property `externalAccountId`
        # @return [String]
        attr_accessor :external_account_id
      
        # The family name of the user when the subscription was purchased. Only present
        # for purchases made with 'Subscribe with Google'.
        # Corresponds to the JSON property `familyName`
        # @return [String]
        attr_accessor :family_name
      
        # The given name of the user when the subscription was purchased. Only present
        # for purchases made with 'Subscribe with Google'.
        # Corresponds to the JSON property `givenName`
        # @return [String]
        attr_accessor :given_name
      
        # Contains the introductory price information for a subscription.
        # Corresponds to the JSON property `introductoryPriceInfo`
        # @return [Google::Apis::AndroidpublisherV3::IntroductoryPriceInfo]
        attr_accessor :introductory_price_info
      
        # This kind represents a subscriptionPurchase object in the androidpublisher
        # service.
        # Corresponds to the JSON property `kind`
        # @return [String]
        attr_accessor :kind
      
        # The purchase token of the originating purchase if this subscription is one of
        # the following: 0. Re-signup of a canceled but non-lapsed subscription 1.
        # Upgrade/downgrade from a previous subscription For example, suppose a user
        # originally signs up and you receive purchase token X, then the user cancels
        # and goes through the resignup flow (before their subscription lapses) and you
        # receive purchase token Y, and finally the user upgrades their subscription and
        # you receive purchase token Z. If you call this API with purchase token Z, this
        # field will be set to Y. If you call this API with purchase token Y, this field
        # will be set to X. If you call this API with purchase token X, this field will
        # not be set.
        # Corresponds to the JSON property `linkedPurchaseToken`
        # @return [String]
        attr_accessor :linked_purchase_token
      
        # An obfuscated version of the id that is uniquely associated with the user's
        # account in your app. Present for the following purchases: * If account linking
        # happened as part of the subscription purchase flow. * It was specified using
        # https://developer.android.com/reference/com/android/billingclient/api/
        # BillingFlowParams.Builder#setobfuscatedaccountid when the purchase was made.
        # Corresponds to the JSON property `obfuscatedExternalAccountId`
        # @return [String]
        attr_accessor :obfuscated_external_account_id
      
        # An obfuscated version of the id that is uniquely associated with the user's
        # profile in your app. Only present if specified using https://developer.android.
        # com/reference/com/android/billingclient/api/BillingFlowParams.Builder#
        # setobfuscatedprofileid when the purchase was made.
        # Corresponds to the JSON property `obfuscatedExternalProfileId`
        # @return [String]
        attr_accessor :obfuscated_external_profile_id
      
        # The order id of the latest recurring order associated with the purchase of the
        # subscription. If the subscription was canceled because payment was declined,
        # this will be the order id from the payment declined order.
        # Corresponds to the JSON property `orderId`
        # @return [String]
        attr_accessor :order_id
      
        # The payment state of the subscription. Possible values are: 0. Payment pending
        # 1. Payment received 2. Free trial 3. Pending deferred upgrade/downgrade Not
        # present for canceled, expired subscriptions.
        # Corresponds to the JSON property `paymentState`
        # @return [Fixnum]
        attr_accessor :payment_state
      
        # Price of the subscription, For tax exclusive countries, the price doesn't
        # include tax. For tax inclusive countries, the price includes tax. Price is
        # expressed in micro-units, where 1,000,000 micro-units represents one unit of
        # the currency. For example, if the subscription price is €1.99,
        # price_amount_micros is 1990000.
        # Corresponds to the JSON property `priceAmountMicros`
        # @return [Fixnum]
        attr_accessor :price_amount_micros
      
        # Contains the price change information for a subscription that can be used to
        # control the user journey for the price change in the app. This can be in the
        # form of seeking confirmation from the user or tailoring the experience for a
        # successful conversion.
        # Corresponds to the JSON property `priceChange`
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionPriceChange]
        attr_accessor :price_change
      
        # ISO 4217 currency code for the subscription price. For example, if the price
        # is specified in British pounds sterling, price_currency_code is "GBP".
        # Corresponds to the JSON property `priceCurrencyCode`
        # @return [String]
        attr_accessor :price_currency_code
      
        # The Google profile id of the user when the subscription was purchased. Only
        # present for purchases made with 'Subscribe with Google'.
        # Corresponds to the JSON property `profileId`
        # @return [String]
        attr_accessor :profile_id
      
        # The profile name of the user when the subscription was purchased. Only present
        # for purchases made with 'Subscribe with Google'.
        # Corresponds to the JSON property `profileName`
        # @return [String]
        attr_accessor :profile_name
      
        # The promotion code applied on this purchase. This field is only set if a
        # vanity code promotion is applied when the subscription was purchased.
        # Corresponds to the JSON property `promotionCode`
        # @return [String]
        attr_accessor :promotion_code
      
        # The type of promotion applied on this purchase. This field is only set if a
        # promotion is applied when the subscription was purchased. Possible values are:
        # 0. One time code 1. Vanity code
        # Corresponds to the JSON property `promotionType`
        # @return [Fixnum]
        attr_accessor :promotion_type
      
        # The type of purchase of the subscription. This field is only set if this
        # purchase was not made using the standard in-app billing flow. Possible values
        # are: 0. Test (i.e. purchased from a license testing account) 1. Promo (i.e.
        # purchased using a promo code)
        # Corresponds to the JSON property `purchaseType`
        # @return [Fixnum]
        attr_accessor :purchase_type
      
        # Time at which the subscription was granted, in milliseconds since the Epoch.
        # Corresponds to the JSON property `startTimeMillis`
        # @return [Fixnum]
        attr_accessor :start_time_millis
      
        # The time at which the subscription was canceled by the user, in milliseconds
        # since the epoch. Only present if cancelReason is 0.
        # Corresponds to the JSON property `userCancellationTimeMillis`
        # @return [Fixnum]
        attr_accessor :user_cancellation_time_millis
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @acknowledgement_state = args[:acknowledgement_state] if args.key?(:acknowledgement_state)
          @auto_renewing = args[:auto_renewing] if args.key?(:auto_renewing)
          @auto_resume_time_millis = args[:auto_resume_time_millis] if args.key?(:auto_resume_time_millis)
          @cancel_reason = args[:cancel_reason] if args.key?(:cancel_reason)
          @cancel_survey_result = args[:cancel_survey_result] if args.key?(:cancel_survey_result)
          @country_code = args[:country_code] if args.key?(:country_code)
          @developer_payload = args[:developer_payload] if args.key?(:developer_payload)
          @email_address = args[:email_address] if args.key?(:email_address)
          @expiry_time_millis = args[:expiry_time_millis] if args.key?(:expiry_time_millis)
          @external_account_id = args[:external_account_id] if args.key?(:external_account_id)
          @family_name = args[:family_name] if args.key?(:family_name)
          @given_name = args[:given_name] if args.key?(:given_name)
          @introductory_price_info = args[:introductory_price_info] if args.key?(:introductory_price_info)
          @kind = args[:kind] if args.key?(:kind)
          @linked_purchase_token = args[:linked_purchase_token] if args.key?(:linked_purchase_token)
          @obfuscated_external_account_id = args[:obfuscated_external_account_id] if args.key?(:obfuscated_external_account_id)
          @obfuscated_external_profile_id = args[:obfuscated_external_profile_id] if args.key?(:obfuscated_external_profile_id)
          @order_id = args[:order_id] if args.key?(:order_id)
          @payment_state = args[:payment_state] if args.key?(:payment_state)
          @price_amount_micros = args[:price_amount_micros] if args.key?(:price_amount_micros)
          @price_change = args[:price_change] if args.key?(:price_change)
          @price_currency_code = args[:price_currency_code] if args.key?(:price_currency_code)
          @profile_id = args[:profile_id] if args.key?(:profile_id)
          @profile_name = args[:profile_name] if args.key?(:profile_name)
          @promotion_code = args[:promotion_code] if args.key?(:promotion_code)
          @promotion_type = args[:promotion_type] if args.key?(:promotion_type)
          @purchase_type = args[:purchase_type] if args.key?(:purchase_type)
          @start_time_millis = args[:start_time_millis] if args.key?(:start_time_millis)
          @user_cancellation_time_millis = args[:user_cancellation_time_millis] if args.key?(:user_cancellation_time_millis)
        end
      end
      
      # Item-level info for a subscription purchase.
      class SubscriptionPurchaseLineItem
        include Google::Apis::Core::Hashable
      
        # Information related to an auto renewing plan.
        # Corresponds to the JSON property `autoRenewingPlan`
        # @return [Google::Apis::AndroidpublisherV3::AutoRenewingPlan]
        attr_accessor :auto_renewing_plan
      
        # Information related to deferred item replacement.
        # Corresponds to the JSON property `deferredItemReplacement`
        # @return [Google::Apis::AndroidpublisherV3::DeferredItemReplacement]
        attr_accessor :deferred_item_replacement
      
        # Time at which the subscription expired or will expire unless the access is
        # extended (ex. renews).
        # Corresponds to the JSON property `expiryTime`
        # @return [String]
        attr_accessor :expiry_time
      
        # Offer details information related to a purchase line item.
        # Corresponds to the JSON property `offerDetails`
        # @return [Google::Apis::AndroidpublisherV3::OfferDetails]
        attr_accessor :offer_details
      
        # Information related to a prepaid plan.
        # Corresponds to the JSON property `prepaidPlan`
        # @return [Google::Apis::AndroidpublisherV3::PrepaidPlan]
        attr_accessor :prepaid_plan
      
        # The purchased product ID (for example, 'monthly001').
        # Corresponds to the JSON property `productId`
        # @return [String]
        attr_accessor :product_id
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @auto_renewing_plan = args[:auto_renewing_plan] if args.key?(:auto_renewing_plan)
          @deferred_item_replacement = args[:deferred_item_replacement] if args.key?(:deferred_item_replacement)
          @expiry_time = args[:expiry_time] if args.key?(:expiry_time)
          @offer_details = args[:offer_details] if args.key?(:offer_details)
          @prepaid_plan = args[:prepaid_plan] if args.key?(:prepaid_plan)
          @product_id = args[:product_id] if args.key?(:product_id)
        end
      end
      
      # Indicates the status of a user's subscription purchase.
      class SubscriptionPurchaseV2
        include Google::Apis::Core::Hashable
      
        # The acknowledgement state of the subscription.
        # Corresponds to the JSON property `acknowledgementState`
        # @return [String]
        attr_accessor :acknowledgement_state
      
        # Information specific to a subscription in canceled state.
        # Corresponds to the JSON property `canceledStateContext`
        # @return [Google::Apis::AndroidpublisherV3::CanceledStateContext]
        attr_accessor :canceled_state_context
      
        # User account identifier in the third-party service.
        # Corresponds to the JSON property `externalAccountIdentifiers`
        # @return [Google::Apis::AndroidpublisherV3::ExternalAccountIdentifiers]
        attr_accessor :external_account_identifiers
      
        # This kind represents a SubscriptionPurchaseV2 object in the androidpublisher
        # service.
        # Corresponds to the JSON property `kind`
        # @return [String]
        attr_accessor :kind
      
        # The order id of the latest order associated with the purchase of the
        # subscription. For autoRenewing subscription, this is the order id of signup
        # order if it is not renewed yet, or the last recurring order id (success,
        # pending, or declined order). For prepaid subscription, this is the order id
        # associated with the queried purchase token.
        # Corresponds to the JSON property `latestOrderId`
        # @return [String]
        attr_accessor :latest_order_id
      
        # Item-level info for a subscription purchase. The items in the same purchase
        # should be either all with AutoRenewingPlan or all with PrepaidPlan.
        # Corresponds to the JSON property `lineItems`
        # @return [Array<Google::Apis::AndroidpublisherV3::SubscriptionPurchaseLineItem>]
        attr_accessor :line_items
      
        # The purchase token of the old subscription if this subscription is one of the
        # following: * Re-signup of a canceled but non-lapsed subscription * Upgrade/
        # downgrade from a previous subscription. * Convert from prepaid to auto
        # renewing subscription. * Convert from an auto renewing subscription to prepaid.
        # * Topup a prepaid subscription.
        # Corresponds to the JSON property `linkedPurchaseToken`
        # @return [String]
        attr_accessor :linked_purchase_token
      
        # Information specific to a subscription in paused state.
        # Corresponds to the JSON property `pausedStateContext`
        # @return [Google::Apis::AndroidpublisherV3::PausedStateContext]
        attr_accessor :paused_state_context
      
        # ISO 3166-1 alpha-2 billing country/region code of the user at the time the
        # subscription was granted.
        # Corresponds to the JSON property `regionCode`
        # @return [String]
        attr_accessor :region_code
      
        # Time at which the subscription was granted. Not set for pending subscriptions (
        # subscription was created but awaiting payment during signup).
        # Corresponds to the JSON property `startTime`
        # @return [String]
        attr_accessor :start_time
      
        # Information associated with purchases made with 'Subscribe with Google'.
        # Corresponds to the JSON property `subscribeWithGoogleInfo`
        # @return [Google::Apis::AndroidpublisherV3::SubscribeWithGoogleInfo]
        attr_accessor :subscribe_with_google_info
      
        # The current state of the subscription.
        # Corresponds to the JSON property `subscriptionState`
        # @return [String]
        attr_accessor :subscription_state
      
        # Whether this subscription purchase is a test purchase.
        # Corresponds to the JSON property `testPurchase`
        # @return [Google::Apis::AndroidpublisherV3::TestPurchase]
        attr_accessor :test_purchase
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @acknowledgement_state = args[:acknowledgement_state] if args.key?(:acknowledgement_state)
          @canceled_state_context = args[:canceled_state_context] if args.key?(:canceled_state_context)
          @external_account_identifiers = args[:external_account_identifiers] if args.key?(:external_account_identifiers)
          @kind = args[:kind] if args.key?(:kind)
          @latest_order_id = args[:latest_order_id] if args.key?(:latest_order_id)
          @line_items = args[:line_items] if args.key?(:line_items)
          @linked_purchase_token = args[:linked_purchase_token] if args.key?(:linked_purchase_token)
          @paused_state_context = args[:paused_state_context] if args.key?(:paused_state_context)
          @region_code = args[:region_code] if args.key?(:region_code)
          @start_time = args[:start_time] if args.key?(:start_time)
          @subscribe_with_google_info = args[:subscribe_with_google_info] if args.key?(:subscribe_with_google_info)
          @subscription_state = args[:subscription_state] if args.key?(:subscription_state)
          @test_purchase = args[:test_purchase] if args.key?(:test_purchase)
        end
      end
      
      # Request for the purchases.subscriptions.acknowledge API.
      class SubscriptionPurchasesAcknowledgeRequest
        include Google::Apis::Core::Hashable
      
        # Payload to attach to the purchase.
        # Corresponds to the JSON property `developerPayload`
        # @return [String]
        attr_accessor :developer_payload
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @developer_payload = args[:developer_payload] if args.key?(:developer_payload)
        end
      end
      
      # Request for the purchases.subscriptions.defer API.
      class SubscriptionPurchasesDeferRequest
        include Google::Apis::Core::Hashable
      
        # A SubscriptionDeferralInfo contains the data needed to defer a subscription
        # purchase to a future expiry time.
        # Corresponds to the JSON property `deferralInfo`
        # @return [Google::Apis::AndroidpublisherV3::SubscriptionDeferralInfo]
        attr_accessor :deferral_info
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @deferral_info = args[:deferral_info] if args.key?(:deferral_info)
        end
      end
      
      # Response for the purchases.subscriptions.defer API.
      class SubscriptionPurchasesDeferResponse
        include Google::Apis::Core::Hashable
      
        # The new expiry time for the subscription in milliseconds since the Epoch.
        # Corresponds to the JSON property `newExpiryTimeMillis`
        # @return [Fixnum]
        attr_accessor :new_expiry_time_millis
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @new_expiry_time_millis = args[:new_expiry_time_millis] if args.key?(:new_expiry_time_millis)
        end
      end
      
      # Details about taxation, Google Play policy and legal compliance for
      # subscription products.
      class SubscriptionTaxAndComplianceSettings
        include Google::Apis::Core::Hashable
      
        # Digital content or service classification for products distributed to users in
        # the European Economic Area (EEA). The withdrawal regime under EEA consumer
        # laws depends on this classification. Refer to the [Help Center article](https:/
        # /support.google.com/googleplay/android-developer/answer/10463498) for more
        # information.
        # Corresponds to the JSON property `eeaWithdrawalRightType`
        # @return [String]
        attr_accessor :eea_withdrawal_right_type
      
        # Whether this subscription is declared as a product representing a tokenized
        # digital asset.
        # Corresponds to the JSON property `isTokenizedDigitalAsset`
        # @return [Boolean]
        attr_accessor :is_tokenized_digital_asset
        alias_method :is_tokenized_digital_asset?, :is_tokenized_digital_asset
      
        # A mapping from region code to tax rate details. The keys are region codes as
        # defined by Unicode's "CLDR".
        # Corresponds to the JSON property `taxRateInfoByRegionCode`
        # @return [Hash<String,Google::Apis::AndroidpublisherV3::RegionalTaxRateInfo>]
        attr_accessor :tax_rate_info_by_region_code
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @eea_withdrawal_right_type = args[:eea_withdrawal_right_type] if args.key?(:eea_withdrawal_right_type)
          @is_tokenized_digital_asset = args[:is_tokenized_digital_asset] if args.key?(:is_tokenized_digital_asset)
          @tax_rate_info_by_region_code = args[:tax_rate_info_by_region_code] if args.key?(:tax_rate_info_by_region_code)
        end
      end
      
      # Options for system APKs.
      class SystemApkOptions
        include Google::Apis::Core::Hashable
      
        # Whether to use the rotated key for signing the system APK.
        # Corresponds to the JSON property `rotated`
        # @return [Boolean]
        attr_accessor :rotated
        alias_method :rotated?, :rotated
      
        # Whether system APK was generated with uncompressed dex files.
        # Corresponds to the JSON property `uncompressedDexFiles`
        # @return [Boolean]
        attr_accessor :uncompressed_dex_files
        alias_method :uncompressed_dex_files?, :uncompressed_dex_files
      
        # Whether system APK was generated with uncompressed native libraries.
        # Corresponds to the JSON property `uncompressedNativeLibraries`
        # @return [Boolean]
        attr_accessor :uncompressed_native_libraries
        alias_method :uncompressed_native_libraries?, :uncompressed_native_libraries
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @rotated = args[:rotated] if args.key?(:rotated)
          @uncompressed_dex_files = args[:uncompressed_dex_files] if args.key?(:uncompressed_dex_files)
          @uncompressed_native_libraries = args[:uncompressed_native_libraries] if args.key?(:uncompressed_native_libraries)
        end
      end
      
      # Response to list previously created system APK variants.
      class SystemApksListResponse
        include Google::Apis::Core::Hashable
      
        # All system APK variants created.
        # Corresponds to the JSON property `variants`
        # @return [Array<Google::Apis::AndroidpublisherV3::Variant>]
        attr_accessor :variants
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @variants = args[:variants] if args.key?(:variants)
        end
      end
      
      # Representation of a system feature.
      class SystemFeature
        include Google::Apis::Core::Hashable
      
        # The name of the feature.
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @name = args[:name] if args.key?(:name)
        end
      end
      
      # Information specific to cancellations initiated by Google system.
      class SystemInitiatedCancellation
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # Targeting information about the generated apks.
      class TargetingInfo
        include Google::Apis::Core::Hashable
      
        # List of created asset slices.
        # Corresponds to the JSON property `assetSliceSet`
        # @return [Array<Google::Apis::AndroidpublisherV3::AssetSliceSet>]
        attr_accessor :asset_slice_set
      
        # The package name of this app.
        # Corresponds to the JSON property `packageName`
        # @return [String]
        attr_accessor :package_name
      
        # List of the created variants.
        # Corresponds to the JSON property `variant`
        # @return [Array<Google::Apis::AndroidpublisherV3::SplitApkVariant>]
        attr_accessor :variant
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @asset_slice_set = args[:asset_slice_set] if args.key?(:asset_slice_set)
          @package_name = args[:package_name] if args.key?(:package_name)
          @variant = args[:variant] if args.key?(:variant)
        end
      end
      
      # Defines the scope of subscriptions which a targeting rule can match to target
      # offers to users based on past or current entitlement.
      class TargetingRuleScope
        include Google::Apis::Core::Hashable
      
        # The scope of the current targeting rule is the subscription with the specified
        # subscription ID. Must be a subscription within the same parent app.
        # Corresponds to the JSON property `specificSubscriptionInApp`
        # @return [String]
        attr_accessor :specific_subscription_in_app
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @specific_subscription_in_app = args[:specific_subscription_in_app] if args.key?(:specific_subscription_in_app)
        end
      end
      
      # Whether this subscription purchase is a test purchase.
      class TestPurchase
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # The testers of an app. The resource for TestersService. Note: while it is
      # possible in the Play Console UI to add testers via email lists, email lists
      # are not supported by this resource.
      class Testers
        include Google::Apis::Core::Hashable
      
        # All testing Google Groups, as email addresses.
        # Corresponds to the JSON property `googleGroups`
        # @return [Array<String>]
        attr_accessor :google_groups
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @google_groups = args[:google_groups] if args.key?(:google_groups)
        end
      end
      
      # Represents texture compression format.
      class TextureCompressionFormat
        include Google::Apis::Core::Hashable
      
        # Alias for texture compression format.
        # Corresponds to the JSON property `alias`
        # @return [String]
        attr_accessor :alias
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @alias = args[:alias] if args.key?(:alias)
        end
      end
      
      # Targeting by a texture compression format.
      class TextureCompressionFormatTargeting
        include Google::Apis::Core::Hashable
      
        # List of alternative TCFs (TCFs targeted by the sibling splits).
        # Corresponds to the JSON property `alternatives`
        # @return [Array<Google::Apis::AndroidpublisherV3::TextureCompressionFormat>]
        attr_accessor :alternatives
      
        # The list of targeted TCFs. Should not be empty.
        # Corresponds to the JSON property `value`
        # @return [Array<Google::Apis::AndroidpublisherV3::TextureCompressionFormat>]
        attr_accessor :value
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @alternatives = args[:alternatives] if args.key?(:alternatives)
          @value = args[:value] if args.key?(:value)
        end
      end
      
      # A Timestamp represents a point in time independent of any time zone or local
      # calendar, encoded as a count of seconds and fractions of seconds at nanosecond
      # resolution. The count is relative to an epoch at UTC midnight on January 1,
      # 1970.
      class Timestamp
        include Google::Apis::Core::Hashable
      
        # Non-negative fractions of a second at nanosecond resolution. Must be from 0 to
        # 999,999,999 inclusive.
        # Corresponds to the JSON property `nanos`
        # @return [Fixnum]
        attr_accessor :nanos
      
        # Represents seconds of UTC time since Unix epoch.
        # Corresponds to the JSON property `seconds`
        # @return [Fixnum]
        attr_accessor :seconds
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @nanos = args[:nanos] if args.key?(:nanos)
          @seconds = args[:seconds] if args.key?(:seconds)
        end
      end
      
      # Pagination information returned by a List operation when token pagination is
      # enabled. List operations that supports paging return only one "page" of
      # results. This protocol buffer message describes the page that has been
      # returned. When using token pagination, clients should use the next/previous
      # token to get another page of the result. The presence or absence of next/
      # previous token indicates whether a next/previous page is available and
      # provides a mean of accessing this page. ListRequest.page_token should be set
      # to either next_page_token or previous_page_token to access another page.
      class TokenPagination
        include Google::Apis::Core::Hashable
      
        # Tokens to pass to the standard list field 'page_token'. Whenever available,
        # tokens are preferred over manipulating start_index.
        # Corresponds to the JSON property `nextPageToken`
        # @return [String]
        attr_accessor :next_page_token
      
        # 
        # Corresponds to the JSON property `previousPageToken`
        # @return [String]
        attr_accessor :previous_page_token
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @next_page_token = args[:next_page_token] if args.key?(:next_page_token)
          @previous_page_token = args[:previous_page_token] if args.key?(:previous_page_token)
        end
      end
      
      # A track configuration. The resource for TracksService.
      class Track
        include Google::Apis::Core::Hashable
      
        # In a read request, represents all active releases in the track. In an update
        # request, represents desired changes.
        # Corresponds to the JSON property `releases`
        # @return [Array<Google::Apis::AndroidpublisherV3::TrackRelease>]
        attr_accessor :releases
      
        # Identifier of the track. Form factor tracks have a special prefix as an
        # identifier, for example `wear:production`, `automotive:production`. [More on
        # track name](https://developers.google.com/android-publisher/tracks#ff-track-
        # name)
        # Corresponds to the JSON property `track`
        # @return [String]
        attr_accessor :track
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @releases = args[:releases] if args.key?(:releases)
          @track = args[:track] if args.key?(:track)
        end
      end
      
      # Configurations of the new track.
      class TrackConfig
        include Google::Apis::Core::Hashable
      
        # Required. Form factor of the new track. Defaults to the default track.
        # Corresponds to the JSON property `formFactor`
        # @return [String]
        attr_accessor :form_factor
      
        # Required. Identifier of the new track. For default tracks, this field consists
        # of the track alias only. Form factor tracks have a special prefix as an
        # identifier, for example `wear:production`, `automotive:production`. This
        # prefix must match the value of the `form_factor` field, if it is not a default
        # track. [More on track name](https://developers.google.com/android-publisher/
        # tracks#ff-track-name)
        # Corresponds to the JSON property `track`
        # @return [String]
        attr_accessor :track
      
        # Required. Type of the new track. Currently, the only supported value is
        # closedTesting.
        # Corresponds to the JSON property `type`
        # @return [String]
        attr_accessor :type
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @form_factor = args[:form_factor] if args.key?(:form_factor)
          @track = args[:track] if args.key?(:track)
          @type = args[:type] if args.key?(:type)
        end
      end
      
      # Resource for per-track country availability information.
      class TrackCountryAvailability
        include Google::Apis::Core::Hashable
      
        # A list of one or more countries where artifacts in this track are available.
        # This list includes all countries that are targeted by the track, even if only
        # specific carriers are targeted in that country.
        # Corresponds to the JSON property `countries`
        # @return [Array<Google::Apis::AndroidpublisherV3::TrackTargetedCountry>]
        attr_accessor :countries
      
        # Whether artifacts in this track are available to "rest of the world" countries.
        # Corresponds to the JSON property `restOfWorld`
        # @return [Boolean]
        attr_accessor :rest_of_world
        alias_method :rest_of_world?, :rest_of_world
      
        # Whether this track's availability is synced with the default production track.
        # See https://support.google.com/googleplay/android-developer/answer/7550024 for
        # more information on syncing country availability with production. Note that if
        # this is true, the returned "countries" and "rest_of_world" fields will reflect
        # the values for the default production track.
        # Corresponds to the JSON property `syncWithProduction`
        # @return [Boolean]
        attr_accessor :sync_with_production
        alias_method :sync_with_production?, :sync_with_production
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @countries = args[:countries] if args.key?(:countries)
          @rest_of_world = args[:rest_of_world] if args.key?(:rest_of_world)
          @sync_with_production = args[:sync_with_production] if args.key?(:sync_with_production)
        end
      end
      
      # A release within a track.
      class TrackRelease
        include Google::Apis::Core::Hashable
      
        # Country targeting specification.
        # Corresponds to the JSON property `countryTargeting`
        # @return [Google::Apis::AndroidpublisherV3::CountryTargeting]
        attr_accessor :country_targeting
      
        # In-app update priority of the release. All newly added APKs in the release
        # will be considered at this priority. Can take values in the range [0, 5], with
        # 5 the highest priority. Defaults to 0. in_app_update_priority can not be
        # updated once the release is rolled out. See https://developer.android.com/
        # guide/playcore/in-app-updates.
        # Corresponds to the JSON property `inAppUpdatePriority`
        # @return [Fixnum]
        attr_accessor :in_app_update_priority
      
        # The release name. Not required to be unique. If not set, the name is generated
        # from the APK's version_name. If the release contains multiple APKs, the name
        # is generated from the date.
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        # A description of what is new in this release.
        # Corresponds to the JSON property `releaseNotes`
        # @return [Array<Google::Apis::AndroidpublisherV3::LocalizedText>]
        attr_accessor :release_notes
      
        # The status of the release.
        # Corresponds to the JSON property `status`
        # @return [String]
        attr_accessor :status
      
        # Fraction of users who are eligible for a staged release. 0 < fraction < 1. Can
        # only be set when status is "inProgress" or "halted".
        # Corresponds to the JSON property `userFraction`
        # @return [Float]
        attr_accessor :user_fraction
      
        # Version codes of all APKs in the release. Must include version codes to retain
        # from previous releases.
        # Corresponds to the JSON property `versionCodes`
        # @return [Array<Fixnum>]
        attr_accessor :version_codes
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @country_targeting = args[:country_targeting] if args.key?(:country_targeting)
          @in_app_update_priority = args[:in_app_update_priority] if args.key?(:in_app_update_priority)
          @name = args[:name] if args.key?(:name)
          @release_notes = args[:release_notes] if args.key?(:release_notes)
          @status = args[:status] if args.key?(:status)
          @user_fraction = args[:user_fraction] if args.key?(:user_fraction)
          @version_codes = args[:version_codes] if args.key?(:version_codes)
        end
      end
      
      # Representation of a single country where the contents of a track are available.
      class TrackTargetedCountry
        include Google::Apis::Core::Hashable
      
        # The country to target, as a two-letter CLDR code.
        # Corresponds to the JSON property `countryCode`
        # @return [String]
        attr_accessor :country_code
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @country_code = args[:country_code] if args.key?(:country_code)
        end
      end
      
      # Response listing all tracks.
      class TracksListResponse
        include Google::Apis::Core::Hashable
      
        # The kind of this response ("androidpublisher#tracksListResponse").
        # Corresponds to the JSON property `kind`
        # @return [String]
        attr_accessor :kind
      
        # All tracks (including tracks with no releases).
        # Corresponds to the JSON property `tracks`
        # @return [Array<Google::Apis::AndroidpublisherV3::Track>]
        attr_accessor :tracks
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @kind = args[:kind] if args.key?(:kind)
          @tracks = args[:tracks] if args.key?(:tracks)
        end
      end
      
      # Represents a targeting rule of the form: User currently has `scope` [with
      # billing period `billing_period`].
      class UpgradeTargetingRule
        include Google::Apis::Core::Hashable
      
        # The specific billing period duration, specified in ISO 8601 format, that a
        # user must be currently subscribed to to be eligible for this rule. If not
        # specified, users subscribed to any billing period are matched.
        # Corresponds to the JSON property `billingPeriodDuration`
        # @return [String]
        attr_accessor :billing_period_duration
      
        # Limit this offer to only once per user. If set to true, a user can never be
        # eligible for this offer again if they ever subscribed to this offer.
        # Corresponds to the JSON property `oncePerUser`
        # @return [Boolean]
        attr_accessor :once_per_user
        alias_method :once_per_user?, :once_per_user
      
        # Defines the scope of subscriptions which a targeting rule can match to target
        # offers to users based on past or current entitlement.
        # Corresponds to the JSON property `scope`
        # @return [Google::Apis::AndroidpublisherV3::TargetingRuleScope]
        attr_accessor :scope
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @billing_period_duration = args[:billing_period_duration] if args.key?(:billing_period_duration)
          @once_per_user = args[:once_per_user] if args.key?(:once_per_user)
          @scope = args[:scope] if args.key?(:scope)
        end
      end
      
      # A user resource.
      class User
        include Google::Apis::Core::Hashable
      
        # Output only. The state of the user's access to the Play Console.
        # Corresponds to the JSON property `accessState`
        # @return [String]
        attr_accessor :access_state
      
        # Permissions for the user which apply across the developer account.
        # Corresponds to the JSON property `developerAccountPermissions`
        # @return [Array<String>]
        attr_accessor :developer_account_permissions
      
        # Immutable. The user's email address.
        # Corresponds to the JSON property `email`
        # @return [String]
        attr_accessor :email
      
        # The time at which the user's access expires, if set. When setting this value,
        # it must always be in the future.
        # Corresponds to the JSON property `expirationTime`
        # @return [String]
        attr_accessor :expiration_time
      
        # Output only. Per-app permissions for the user.
        # Corresponds to the JSON property `grants`
        # @return [Array<Google::Apis::AndroidpublisherV3::Grant>]
        attr_accessor :grants
      
        # Required. Resource name for this user, following the pattern "developers/`
        # developer`/users/`email`".
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        # Output only. Whether there are more permissions for the user that are not
        # represented here. This can happen if the caller does not have permission to
        # manage all apps in the account. This is also `true` if this user is the
        # account owner. If this field is `true`, it should be taken as a signal that
        # this user cannot be fully managed via the API. That is, the API caller is not
        # be able to manage all of the permissions this user holds, either because it
        # doesn't know about them or because the user is the account owner.
        # Corresponds to the JSON property `partial`
        # @return [Boolean]
        attr_accessor :partial
        alias_method :partial?, :partial
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @access_state = args[:access_state] if args.key?(:access_state)
          @developer_account_permissions = args[:developer_account_permissions] if args.key?(:developer_account_permissions)
          @email = args[:email] if args.key?(:email)
          @expiration_time = args[:expiration_time] if args.key?(:expiration_time)
          @grants = args[:grants] if args.key?(:grants)
          @name = args[:name] if args.key?(:name)
          @partial = args[:partial] if args.key?(:partial)
        end
      end
      
      # User entry from conversation between user and developer.
      class UserComment
        include Google::Apis::Core::Hashable
      
        # Integer Android SDK version of the user's device at the time the review was
        # written, e.g. 23 is Marshmallow. May be absent.
        # Corresponds to the JSON property `androidOsVersion`
        # @return [Fixnum]
        attr_accessor :android_os_version
      
        # Integer version code of the app as installed at the time the review was
        # written. May be absent.
        # Corresponds to the JSON property `appVersionCode`
        # @return [Fixnum]
        attr_accessor :app_version_code
      
        # String version name of the app as installed at the time the review was written.
        # May be absent.
        # Corresponds to the JSON property `appVersionName`
        # @return [String]
        attr_accessor :app_version_name
      
        # Codename for the reviewer's device, e.g. klte, flounder. May be absent.
        # Corresponds to the JSON property `device`
        # @return [String]
        attr_accessor :device
      
        # Characteristics of the user's device.
        # Corresponds to the JSON property `deviceMetadata`
        # @return [Google::Apis::AndroidpublisherV3::DeviceMetadata]
        attr_accessor :device_metadata
      
        # A Timestamp represents a point in time independent of any time zone or local
        # calendar, encoded as a count of seconds and fractions of seconds at nanosecond
        # resolution. The count is relative to an epoch at UTC midnight on January 1,
        # 1970.
        # Corresponds to the JSON property `lastModified`
        # @return [Google::Apis::AndroidpublisherV3::Timestamp]
        attr_accessor :last_modified
      
        # Untranslated text of the review, where the review was translated. If the
        # review was not translated this is left blank.
        # Corresponds to the JSON property `originalText`
        # @return [String]
        attr_accessor :original_text
      
        # Language code for the reviewer. This is taken from the device settings so is
        # not guaranteed to match the language the review is written in. May be absent.
        # Corresponds to the JSON property `reviewerLanguage`
        # @return [String]
        attr_accessor :reviewer_language
      
        # The star rating associated with the review, from 1 to 5.
        # Corresponds to the JSON property `starRating`
        # @return [Fixnum]
        attr_accessor :star_rating
      
        # The content of the comment, i.e. review body. In some cases users have been
        # able to write a review with separate title and body; in those cases the title
        # and body are concatenated and separated by a tab character.
        # Corresponds to the JSON property `text`
        # @return [String]
        attr_accessor :text
      
        # Number of users who have given this review a thumbs down.
        # Corresponds to the JSON property `thumbsDownCount`
        # @return [Fixnum]
        attr_accessor :thumbs_down_count
      
        # Number of users who have given this review a thumbs up.
        # Corresponds to the JSON property `thumbsUpCount`
        # @return [Fixnum]
        attr_accessor :thumbs_up_count
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @android_os_version = args[:android_os_version] if args.key?(:android_os_version)
          @app_version_code = args[:app_version_code] if args.key?(:app_version_code)
          @app_version_name = args[:app_version_name] if args.key?(:app_version_name)
          @device = args[:device] if args.key?(:device)
          @device_metadata = args[:device_metadata] if args.key?(:device_metadata)
          @last_modified = args[:last_modified] if args.key?(:last_modified)
          @original_text = args[:original_text] if args.key?(:original_text)
          @reviewer_language = args[:reviewer_language] if args.key?(:reviewer_language)
          @star_rating = args[:star_rating] if args.key?(:star_rating)
          @text = args[:text] if args.key?(:text)
          @thumbs_down_count = args[:thumbs_down_count] if args.key?(:thumbs_down_count)
          @thumbs_up_count = args[:thumbs_up_count] if args.key?(:thumbs_up_count)
        end
      end
      
      # Describes an inclusive/exclusive list of country codes that module targets.
      class UserCountriesTargeting
        include Google::Apis::Core::Hashable
      
        # List of country codes in the two-letter CLDR territory format.
        # Corresponds to the JSON property `countryCodes`
        # @return [Array<String>]
        attr_accessor :country_codes
      
        # Indicates if the list above is exclusive.
        # Corresponds to the JSON property `exclude`
        # @return [Boolean]
        attr_accessor :exclude
        alias_method :exclude?, :exclude
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @country_codes = args[:country_codes] if args.key?(:country_codes)
          @exclude = args[:exclude] if args.key?(:exclude)
        end
      end
      
      # A set of user countries. A country set determines what variation of app
      # content gets served to a specific location.
      class UserCountrySet
        include Google::Apis::Core::Hashable
      
        # List of country codes representing countries. A Country code is represented in
        # ISO 3166 alpha-2 format. For Example:- "IT" for Italy, "GE" for Georgia.
        # Corresponds to the JSON property `countryCodes`
        # @return [Array<String>]
        attr_accessor :country_codes
      
        # Country set name.
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @country_codes = args[:country_codes] if args.key?(:country_codes)
          @name = args[:name] if args.key?(:name)
        end
      end
      
      # Information specific to cancellations initiated by users.
      class UserInitiatedCancellation
        include Google::Apis::Core::Hashable
      
        # Result of the cancel survey when the subscription was canceled by the user.
        # Corresponds to the JSON property `cancelSurveyResult`
        # @return [Google::Apis::AndroidpublisherV3::CancelSurveyResult]
        attr_accessor :cancel_survey_result
      
        # The time at which the subscription was canceled by the user. The user might
        # still have access to the subscription after this time. Use line_items.
        # expiry_time to determine if a user still has access.
        # Corresponds to the JSON property `cancelTime`
        # @return [String]
        attr_accessor :cancel_time
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @cancel_survey_result = args[:cancel_survey_result] if args.key?(:cancel_survey_result)
          @cancel_time = args[:cancel_time] if args.key?(:cancel_time)
        end
      end
      
      # A permission used by this APK.
      class UsesPermission
        include Google::Apis::Core::Hashable
      
        # Optionally, the maximum SDK version for which the permission is required.
        # Corresponds to the JSON property `maxSdkVersion`
        # @return [Fixnum]
        attr_accessor :max_sdk_version
      
        # The name of the permission requested.
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @max_sdk_version = args[:max_sdk_version] if args.key?(:max_sdk_version)
          @name = args[:name] if args.key?(:name)
        end
      end
      
      # APK that is suitable for inclusion in a system image. The resource of
      # SystemApksService.
      class Variant
        include Google::Apis::Core::Hashable
      
        # The device spec used to generate a system APK.
        # Corresponds to the JSON property `deviceSpec`
        # @return [Google::Apis::AndroidpublisherV3::DeviceSpec]
        attr_accessor :device_spec
      
        # Options for system APKs.
        # Corresponds to the JSON property `options`
        # @return [Google::Apis::AndroidpublisherV3::SystemApkOptions]
        attr_accessor :options
      
        # Output only. The ID of a previously created system APK variant.
        # Corresponds to the JSON property `variantId`
        # @return [Fixnum]
        attr_accessor :variant_id
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @device_spec = args[:device_spec] if args.key?(:device_spec)
          @options = args[:options] if args.key?(:options)
          @variant_id = args[:variant_id] if args.key?(:variant_id)
        end
      end
      
      # Targeting on the level of variants.
      class VariantTargeting
        include Google::Apis::Core::Hashable
      
        # Targeting based on Abi.
        # Corresponds to the JSON property `abiTargeting`
        # @return [Google::Apis::AndroidpublisherV3::AbiTargeting]
        attr_accessor :abi_targeting
      
        # Targeting based on multiple abis.
        # Corresponds to the JSON property `multiAbiTargeting`
        # @return [Google::Apis::AndroidpublisherV3::MultiAbiTargeting]
        attr_accessor :multi_abi_targeting
      
        # Targeting based on screen density.
        # Corresponds to the JSON property `screenDensityTargeting`
        # @return [Google::Apis::AndroidpublisherV3::ScreenDensityTargeting]
        attr_accessor :screen_density_targeting
      
        # Targeting based on sdk version.
        # Corresponds to the JSON property `sdkVersionTargeting`
        # @return [Google::Apis::AndroidpublisherV3::SdkVersionTargeting]
        attr_accessor :sdk_version_targeting
      
        # Targeting by a texture compression format.
        # Corresponds to the JSON property `textureCompressionFormatTargeting`
        # @return [Google::Apis::AndroidpublisherV3::TextureCompressionFormatTargeting]
        attr_accessor :texture_compression_format_targeting
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @abi_targeting = args[:abi_targeting] if args.key?(:abi_targeting)
          @multi_abi_targeting = args[:multi_abi_targeting] if args.key?(:multi_abi_targeting)
          @screen_density_targeting = args[:screen_density_targeting] if args.key?(:screen_density_targeting)
          @sdk_version_targeting = args[:sdk_version_targeting] if args.key?(:sdk_version_targeting)
          @texture_compression_format_targeting = args[:texture_compression_format_targeting] if args.key?(:texture_compression_format_targeting)
        end
      end
      
      # A VoidedPurchase resource indicates a purchase that was either canceled/
      # refunded/charged-back.
      class VoidedPurchase
        include Google::Apis::Core::Hashable
      
        # This kind represents a voided purchase object in the androidpublisher service.
        # Corresponds to the JSON property `kind`
        # @return [String]
        attr_accessor :kind
      
        # The order id which uniquely identifies a one-time purchase, subscription
        # purchase, or subscription renewal.
        # Corresponds to the JSON property `orderId`
        # @return [String]
        attr_accessor :order_id
      
        # The time at which the purchase was made, in milliseconds since the epoch (Jan
        # 1, 1970).
        # Corresponds to the JSON property `purchaseTimeMillis`
        # @return [Fixnum]
        attr_accessor :purchase_time_millis
      
        # The token which uniquely identifies a one-time purchase or subscription. To
        # uniquely identify subscription renewals use order_id (available starting from
        # version 3 of the API).
        # Corresponds to the JSON property `purchaseToken`
        # @return [String]
        attr_accessor :purchase_token
      
        # The reason why the purchase was voided, possible values are: 0. Other 1.
        # Remorse 2. Not_received 3. Defective 4. Accidental_purchase 5. Fraud 6.
        # Friendly_fraud 7. Chargeback
        # Corresponds to the JSON property `voidedReason`
        # @return [Fixnum]
        attr_accessor :voided_reason
      
        # The initiator of voided purchase, possible values are: 0. User 1. Developer 2.
        # Google
        # Corresponds to the JSON property `voidedSource`
        # @return [Fixnum]
        attr_accessor :voided_source
      
        # The time at which the purchase was canceled/refunded/charged-back, in
        # milliseconds since the epoch (Jan 1, 1970).
        # Corresponds to the JSON property `voidedTimeMillis`
        # @return [Fixnum]
        attr_accessor :voided_time_millis
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @kind = args[:kind] if args.key?(:kind)
          @order_id = args[:order_id] if args.key?(:order_id)
          @purchase_time_millis = args[:purchase_time_millis] if args.key?(:purchase_time_millis)
          @purchase_token = args[:purchase_token] if args.key?(:purchase_token)
          @voided_reason = args[:voided_reason] if args.key?(:voided_reason)
          @voided_source = args[:voided_source] if args.key?(:voided_source)
          @voided_time_millis = args[:voided_time_millis] if args.key?(:voided_time_millis)
        end
      end
      
      # Response for the voidedpurchases.list API.
      class VoidedPurchasesListResponse
        include Google::Apis::Core::Hashable
      
        # Information about the current page. List operations that supports paging
        # return only one "page" of results. This protocol buffer message describes the
        # page that has been returned.
        # Corresponds to the JSON property `pageInfo`
        # @return [Google::Apis::AndroidpublisherV3::PageInfo]
        attr_accessor :page_info
      
        # Pagination information returned by a List operation when token pagination is
        # enabled. List operations that supports paging return only one "page" of
        # results. This protocol buffer message describes the page that has been
        # returned. When using token pagination, clients should use the next/previous
        # token to get another page of the result. The presence or absence of next/
        # previous token indicates whether a next/previous page is available and
        # provides a mean of accessing this page. ListRequest.page_token should be set
        # to either next_page_token or previous_page_token to access another page.
        # Corresponds to the JSON property `tokenPagination`
        # @return [Google::Apis::AndroidpublisherV3::TokenPagination]
        attr_accessor :token_pagination
      
        # 
        # Corresponds to the JSON property `voidedPurchases`
        # @return [Array<Google::Apis::AndroidpublisherV3::VoidedPurchase>]
        attr_accessor :voided_purchases
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @page_info = args[:page_info] if args.key?(:page_info)
          @token_pagination = args[:token_pagination] if args.key?(:token_pagination)
          @voided_purchases = args[:voided_purchases] if args.key?(:voided_purchases)
        end
      end
    end
  end
end
