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
      
      class Abi
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class AbiTargeting
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class AcquisitionTargetingRule
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ActivateBasePlanRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ActivateSubscriptionOfferRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Apk
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ApkBinary
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ApkDescription
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ApkSet
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ApkTargeting
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ApksAddExternallyHostedRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ApksAddExternallyHostedResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ApksListResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class AppDetails
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class AppEdit
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ArchiveSubscriptionRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class AssetModuleMetadata
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class AssetSliceSet
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class AutoRenewingBasePlanType
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class AutoRenewingPlan
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class BasePlan
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Bundle
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class BundlesListResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class CancelSurveyResult
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class CanceledStateContext
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Comment
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ConvertRegionPricesRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ConvertRegionPricesResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ConvertedOtherRegionsPrice
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ConvertedRegionPrice
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class CountryTargeting
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeactivateBasePlanRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeactivateSubscriptionOfferRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeferredItemReplacement
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeobfuscationFile
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeobfuscationFilesUploadResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeveloperComment
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeveloperInitiatedCancellation
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeviceFeature
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeviceFeatureTargeting
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeviceGroup
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeviceId
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeviceMetadata
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeviceRam
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeviceSelector
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeviceSpec
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeviceTier
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeviceTierConfig
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class DeviceTierSet
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ExpansionFile
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ExpansionFilesUploadResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ExternalAccountIdentifiers
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ExternalSubscription
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ExternalTransaction
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ExternalTransactionAddress
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ExternalTransactionTestPurchase
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ExternallyHostedApk
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class FullRefund
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GeneratedApksListResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GeneratedApksPerSigningKey
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GeneratedAssetPackSlice
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GeneratedSplitApk
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GeneratedStandaloneApk
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class GeneratedUniversalApk
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Grant
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Image
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ImagesDeleteAllResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ImagesListResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ImagesUploadResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class InAppProduct
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class InAppProductListing
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class InappproductsListResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class InternalAppSharingArtifact
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class IntroductoryPriceInfo
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class LanguageTargeting
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ListDeviceTierConfigsResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ListSubscriptionOffersResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ListSubscriptionsResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ListUsersResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Listing
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ListingsListResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class LocalizedText
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ManagedProductTaxAndComplianceSettings
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class MigrateBasePlanPricesRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class MigrateBasePlanPricesResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ModuleMetadata
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ModuleTargeting
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Money
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class MultiAbi
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class MultiAbiTargeting
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class OfferDetails
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class OfferTag
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class OneTimeExternalTransaction
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class OtherRegionsBasePlanConfig
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class OtherRegionsSubscriptionOfferConfig
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class OtherRegionsSubscriptionOfferPhaseConfig
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class OtherRegionsSubscriptionOfferPhasePrices
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class PageInfo
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class PartialRefund
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class PausedStateContext
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class PrepaidBasePlanType
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class PrepaidPlan
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Price
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ProductPurchase
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ProductPurchasesAcknowledgeRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class RecurringExternalTransaction
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class RefundExternalTransactionRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class RegionalBasePlanConfig
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class RegionalPriceMigrationConfig
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class RegionalSubscriptionOfferConfig
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class RegionalSubscriptionOfferPhaseConfig
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class RegionalTaxRateInfo
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class RegionsVersion
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ReplacementCancellation
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Review
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ReviewReplyResult
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ReviewsListResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ReviewsReplyRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ReviewsReplyResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ScreenDensity
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class ScreenDensityTargeting
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SdkVersion
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SdkVersionTargeting
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SplitApkMetadata
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SplitApkVariant
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class StandaloneApkMetadata
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscribeWithGoogleInfo
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Subscription
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionCancelSurveyResult
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionDeferralInfo
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionItemPriceChangeDetails
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionListing
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionOffer
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionOfferPhase
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionOfferTargeting
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionPriceChange
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionPurchase
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionPurchaseLineItem
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionPurchaseV2
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionPurchasesAcknowledgeRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionPurchasesDeferRequest
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionPurchasesDeferResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SubscriptionTaxAndComplianceSettings
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SystemApkOptions
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SystemApksListResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SystemFeature
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class SystemInitiatedCancellation
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class TargetingInfo
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class TargetingRuleScope
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class TestPurchase
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Testers
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class TextureCompressionFormat
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class TextureCompressionFormatTargeting
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Timestamp
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class TokenPagination
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Track
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class TrackConfig
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class TrackCountryAvailability
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class TrackRelease
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class TrackTargetedCountry
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class TracksListResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class UpgradeTargetingRule
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class User
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class UserComment
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class UserCountriesTargeting
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class UserCountrySet
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class UserInitiatedCancellation
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class UsesPermission
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Variant
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class VariantTargeting
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class VoidedPurchase
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class VoidedPurchasesListResponse
        class Representation < Google::Apis::Core::JsonRepresentation; end
      
        include Google::Apis::Core::JsonObjectSupport
      end
      
      class Abi
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :alias, as: 'alias'
        end
      end
      
      class AbiTargeting
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :alternatives, as: 'alternatives', class: Google::Apis::AndroidpublisherV3::Abi, decorator: Google::Apis::AndroidpublisherV3::Abi::Representation
      
          collection :value, as: 'value', class: Google::Apis::AndroidpublisherV3::Abi, decorator: Google::Apis::AndroidpublisherV3::Abi::Representation
      
        end
      end
      
      class AcquisitionTargetingRule
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :scope, as: 'scope', class: Google::Apis::AndroidpublisherV3::TargetingRuleScope, decorator: Google::Apis::AndroidpublisherV3::TargetingRuleScope::Representation
      
        end
      end
      
      class ActivateBasePlanRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class ActivateSubscriptionOfferRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class Apk
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :binary, as: 'binary', class: Google::Apis::AndroidpublisherV3::ApkBinary, decorator: Google::Apis::AndroidpublisherV3::ApkBinary::Representation
      
          property :version_code, as: 'versionCode'
        end
      end
      
      class ApkBinary
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :sha1, as: 'sha1'
          property :sha256, as: 'sha256'
        end
      end
      
      class ApkDescription
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :asset_slice_metadata, as: 'assetSliceMetadata', class: Google::Apis::AndroidpublisherV3::SplitApkMetadata, decorator: Google::Apis::AndroidpublisherV3::SplitApkMetadata::Representation
      
          property :instant_apk_metadata, as: 'instantApkMetadata', class: Google::Apis::AndroidpublisherV3::SplitApkMetadata, decorator: Google::Apis::AndroidpublisherV3::SplitApkMetadata::Representation
      
          property :path, as: 'path'
          property :split_apk_metadata, as: 'splitApkMetadata', class: Google::Apis::AndroidpublisherV3::SplitApkMetadata, decorator: Google::Apis::AndroidpublisherV3::SplitApkMetadata::Representation
      
          property :standalone_apk_metadata, as: 'standaloneApkMetadata', class: Google::Apis::AndroidpublisherV3::StandaloneApkMetadata, decorator: Google::Apis::AndroidpublisherV3::StandaloneApkMetadata::Representation
      
          property :targeting, as: 'targeting', class: Google::Apis::AndroidpublisherV3::ApkTargeting, decorator: Google::Apis::AndroidpublisherV3::ApkTargeting::Representation
      
        end
      end
      
      class ApkSet
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :apk_description, as: 'apkDescription', class: Google::Apis::AndroidpublisherV3::ApkDescription, decorator: Google::Apis::AndroidpublisherV3::ApkDescription::Representation
      
          property :module_metadata, as: 'moduleMetadata', class: Google::Apis::AndroidpublisherV3::ModuleMetadata, decorator: Google::Apis::AndroidpublisherV3::ModuleMetadata::Representation
      
        end
      end
      
      class ApkTargeting
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :abi_targeting, as: 'abiTargeting', class: Google::Apis::AndroidpublisherV3::AbiTargeting, decorator: Google::Apis::AndroidpublisherV3::AbiTargeting::Representation
      
          property :language_targeting, as: 'languageTargeting', class: Google::Apis::AndroidpublisherV3::LanguageTargeting, decorator: Google::Apis::AndroidpublisherV3::LanguageTargeting::Representation
      
          property :multi_abi_targeting, as: 'multiAbiTargeting', class: Google::Apis::AndroidpublisherV3::MultiAbiTargeting, decorator: Google::Apis::AndroidpublisherV3::MultiAbiTargeting::Representation
      
          property :screen_density_targeting, as: 'screenDensityTargeting', class: Google::Apis::AndroidpublisherV3::ScreenDensityTargeting, decorator: Google::Apis::AndroidpublisherV3::ScreenDensityTargeting::Representation
      
          property :sdk_version_targeting, as: 'sdkVersionTargeting', class: Google::Apis::AndroidpublisherV3::SdkVersionTargeting, decorator: Google::Apis::AndroidpublisherV3::SdkVersionTargeting::Representation
      
          property :texture_compression_format_targeting, as: 'textureCompressionFormatTargeting', class: Google::Apis::AndroidpublisherV3::TextureCompressionFormatTargeting, decorator: Google::Apis::AndroidpublisherV3::TextureCompressionFormatTargeting::Representation
      
        end
      end
      
      class ApksAddExternallyHostedRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :externally_hosted_apk, as: 'externallyHostedApk', class: Google::Apis::AndroidpublisherV3::ExternallyHostedApk, decorator: Google::Apis::AndroidpublisherV3::ExternallyHostedApk::Representation
      
        end
      end
      
      class ApksAddExternallyHostedResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :externally_hosted_apk, as: 'externallyHostedApk', class: Google::Apis::AndroidpublisherV3::ExternallyHostedApk, decorator: Google::Apis::AndroidpublisherV3::ExternallyHostedApk::Representation
      
        end
      end
      
      class ApksListResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :apks, as: 'apks', class: Google::Apis::AndroidpublisherV3::Apk, decorator: Google::Apis::AndroidpublisherV3::Apk::Representation
      
          property :kind, as: 'kind'
        end
      end
      
      class AppDetails
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :contact_email, as: 'contactEmail'
          property :contact_phone, as: 'contactPhone'
          property :contact_website, as: 'contactWebsite'
          property :default_language, as: 'defaultLanguage'
        end
      end
      
      class AppEdit
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :expiry_time_seconds, as: 'expiryTimeSeconds'
          property :id, as: 'id'
        end
      end
      
      class ArchiveSubscriptionRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class AssetModuleMetadata
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :delivery_type, as: 'deliveryType'
          property :name, as: 'name'
        end
      end
      
      class AssetSliceSet
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :apk_description, as: 'apkDescription', class: Google::Apis::AndroidpublisherV3::ApkDescription, decorator: Google::Apis::AndroidpublisherV3::ApkDescription::Representation
      
          property :asset_module_metadata, as: 'assetModuleMetadata', class: Google::Apis::AndroidpublisherV3::AssetModuleMetadata, decorator: Google::Apis::AndroidpublisherV3::AssetModuleMetadata::Representation
      
        end
      end
      
      class AutoRenewingBasePlanType
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :billing_period_duration, as: 'billingPeriodDuration'
          property :grace_period_duration, as: 'gracePeriodDuration'
          property :legacy_compatible, as: 'legacyCompatible'
          property :legacy_compatible_subscription_offer_id, as: 'legacyCompatibleSubscriptionOfferId'
          property :proration_mode, as: 'prorationMode'
          property :resubscribe_state, as: 'resubscribeState'
        end
      end
      
      class AutoRenewingPlan
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :auto_renew_enabled, as: 'autoRenewEnabled'
          property :price_change_details, as: 'priceChangeDetails', class: Google::Apis::AndroidpublisherV3::SubscriptionItemPriceChangeDetails, decorator: Google::Apis::AndroidpublisherV3::SubscriptionItemPriceChangeDetails::Representation
      
        end
      end
      
      class BasePlan
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :auto_renewing_base_plan_type, as: 'autoRenewingBasePlanType', class: Google::Apis::AndroidpublisherV3::AutoRenewingBasePlanType, decorator: Google::Apis::AndroidpublisherV3::AutoRenewingBasePlanType::Representation
      
          property :base_plan_id, as: 'basePlanId'
          collection :offer_tags, as: 'offerTags', class: Google::Apis::AndroidpublisherV3::OfferTag, decorator: Google::Apis::AndroidpublisherV3::OfferTag::Representation
      
          property :other_regions_config, as: 'otherRegionsConfig', class: Google::Apis::AndroidpublisherV3::OtherRegionsBasePlanConfig, decorator: Google::Apis::AndroidpublisherV3::OtherRegionsBasePlanConfig::Representation
      
          property :prepaid_base_plan_type, as: 'prepaidBasePlanType', class: Google::Apis::AndroidpublisherV3::PrepaidBasePlanType, decorator: Google::Apis::AndroidpublisherV3::PrepaidBasePlanType::Representation
      
          collection :regional_configs, as: 'regionalConfigs', class: Google::Apis::AndroidpublisherV3::RegionalBasePlanConfig, decorator: Google::Apis::AndroidpublisherV3::RegionalBasePlanConfig::Representation
      
          property :state, as: 'state'
        end
      end
      
      class Bundle
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :sha1, as: 'sha1'
          property :sha256, as: 'sha256'
          property :version_code, as: 'versionCode'
        end
      end
      
      class BundlesListResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :bundles, as: 'bundles', class: Google::Apis::AndroidpublisherV3::Bundle, decorator: Google::Apis::AndroidpublisherV3::Bundle::Representation
      
          property :kind, as: 'kind'
        end
      end
      
      class CancelSurveyResult
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :reason, as: 'reason'
          property :reason_user_input, as: 'reasonUserInput'
        end
      end
      
      class CanceledStateContext
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :developer_initiated_cancellation, as: 'developerInitiatedCancellation', class: Google::Apis::AndroidpublisherV3::DeveloperInitiatedCancellation, decorator: Google::Apis::AndroidpublisherV3::DeveloperInitiatedCancellation::Representation
      
          property :replacement_cancellation, as: 'replacementCancellation', class: Google::Apis::AndroidpublisherV3::ReplacementCancellation, decorator: Google::Apis::AndroidpublisherV3::ReplacementCancellation::Representation
      
          property :system_initiated_cancellation, as: 'systemInitiatedCancellation', class: Google::Apis::AndroidpublisherV3::SystemInitiatedCancellation, decorator: Google::Apis::AndroidpublisherV3::SystemInitiatedCancellation::Representation
      
          property :user_initiated_cancellation, as: 'userInitiatedCancellation', class: Google::Apis::AndroidpublisherV3::UserInitiatedCancellation, decorator: Google::Apis::AndroidpublisherV3::UserInitiatedCancellation::Representation
      
        end
      end
      
      class Comment
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :developer_comment, as: 'developerComment', class: Google::Apis::AndroidpublisherV3::DeveloperComment, decorator: Google::Apis::AndroidpublisherV3::DeveloperComment::Representation
      
          property :user_comment, as: 'userComment', class: Google::Apis::AndroidpublisherV3::UserComment, decorator: Google::Apis::AndroidpublisherV3::UserComment::Representation
      
        end
      end
      
      class ConvertRegionPricesRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :price, as: 'price', class: Google::Apis::AndroidpublisherV3::Money, decorator: Google::Apis::AndroidpublisherV3::Money::Representation
      
        end
      end
      
      class ConvertRegionPricesResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :converted_other_regions_price, as: 'convertedOtherRegionsPrice', class: Google::Apis::AndroidpublisherV3::ConvertedOtherRegionsPrice, decorator: Google::Apis::AndroidpublisherV3::ConvertedOtherRegionsPrice::Representation
      
          hash :converted_region_prices, as: 'convertedRegionPrices', class: Google::Apis::AndroidpublisherV3::ConvertedRegionPrice, decorator: Google::Apis::AndroidpublisherV3::ConvertedRegionPrice::Representation
      
        end
      end
      
      class ConvertedOtherRegionsPrice
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :eur_price, as: 'eurPrice', class: Google::Apis::AndroidpublisherV3::Money, decorator: Google::Apis::AndroidpublisherV3::Money::Representation
      
          property :usd_price, as: 'usdPrice', class: Google::Apis::AndroidpublisherV3::Money, decorator: Google::Apis::AndroidpublisherV3::Money::Representation
      
        end
      end
      
      class ConvertedRegionPrice
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :price, as: 'price', class: Google::Apis::AndroidpublisherV3::Money, decorator: Google::Apis::AndroidpublisherV3::Money::Representation
      
          property :region_code, as: 'regionCode'
          property :tax_amount, as: 'taxAmount', class: Google::Apis::AndroidpublisherV3::Money, decorator: Google::Apis::AndroidpublisherV3::Money::Representation
      
        end
      end
      
      class CountryTargeting
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :countries, as: 'countries'
          property :include_rest_of_world, as: 'includeRestOfWorld'
        end
      end
      
      class DeactivateBasePlanRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class DeactivateSubscriptionOfferRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class DeferredItemReplacement
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :product_id, as: 'productId'
        end
      end
      
      class DeobfuscationFile
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :symbol_type, as: 'symbolType'
        end
      end
      
      class DeobfuscationFilesUploadResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :deobfuscation_file, as: 'deobfuscationFile', class: Google::Apis::AndroidpublisherV3::DeobfuscationFile, decorator: Google::Apis::AndroidpublisherV3::DeobfuscationFile::Representation
      
        end
      end
      
      class DeveloperComment
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :last_modified, as: 'lastModified', class: Google::Apis::AndroidpublisherV3::Timestamp, decorator: Google::Apis::AndroidpublisherV3::Timestamp::Representation
      
          property :text, as: 'text'
        end
      end
      
      class DeveloperInitiatedCancellation
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class DeviceFeature
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :feature_name, as: 'featureName'
          property :feature_version, as: 'featureVersion'
        end
      end
      
      class DeviceFeatureTargeting
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :required_feature, as: 'requiredFeature', class: Google::Apis::AndroidpublisherV3::DeviceFeature, decorator: Google::Apis::AndroidpublisherV3::DeviceFeature::Representation
      
        end
      end
      
      class DeviceGroup
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :device_selectors, as: 'deviceSelectors', class: Google::Apis::AndroidpublisherV3::DeviceSelector, decorator: Google::Apis::AndroidpublisherV3::DeviceSelector::Representation
      
          property :name, as: 'name'
        end
      end
      
      class DeviceId
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :build_brand, as: 'buildBrand'
          property :build_device, as: 'buildDevice'
        end
      end
      
      class DeviceMetadata
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :cpu_make, as: 'cpuMake'
          property :cpu_model, as: 'cpuModel'
          property :device_class, as: 'deviceClass'
          property :gl_es_version, as: 'glEsVersion'
          property :manufacturer, as: 'manufacturer'
          property :native_platform, as: 'nativePlatform'
          property :product_name, as: 'productName'
          property :ram_mb, as: 'ramMb'
          property :screen_density_dpi, as: 'screenDensityDpi'
          property :screen_height_px, as: 'screenHeightPx'
          property :screen_width_px, as: 'screenWidthPx'
        end
      end
      
      class DeviceRam
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :max_bytes, :numeric_string => true, as: 'maxBytes'
          property :min_bytes, :numeric_string => true, as: 'minBytes'
        end
      end
      
      class DeviceSelector
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :device_ram, as: 'deviceRam', class: Google::Apis::AndroidpublisherV3::DeviceRam, decorator: Google::Apis::AndroidpublisherV3::DeviceRam::Representation
      
          collection :excluded_device_ids, as: 'excludedDeviceIds', class: Google::Apis::AndroidpublisherV3::DeviceId, decorator: Google::Apis::AndroidpublisherV3::DeviceId::Representation
      
          collection :forbidden_system_features, as: 'forbiddenSystemFeatures', class: Google::Apis::AndroidpublisherV3::SystemFeature, decorator: Google::Apis::AndroidpublisherV3::SystemFeature::Representation
      
          collection :included_device_ids, as: 'includedDeviceIds', class: Google::Apis::AndroidpublisherV3::DeviceId, decorator: Google::Apis::AndroidpublisherV3::DeviceId::Representation
      
          collection :required_system_features, as: 'requiredSystemFeatures', class: Google::Apis::AndroidpublisherV3::SystemFeature, decorator: Google::Apis::AndroidpublisherV3::SystemFeature::Representation
      
        end
      end
      
      class DeviceSpec
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :screen_density, as: 'screenDensity'
          collection :supported_abis, as: 'supportedAbis'
          collection :supported_locales, as: 'supportedLocales'
        end
      end
      
      class DeviceTier
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :device_group_names, as: 'deviceGroupNames'
          property :level, as: 'level'
        end
      end
      
      class DeviceTierConfig
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :device_groups, as: 'deviceGroups', class: Google::Apis::AndroidpublisherV3::DeviceGroup, decorator: Google::Apis::AndroidpublisherV3::DeviceGroup::Representation
      
          property :device_tier_config_id, :numeric_string => true, as: 'deviceTierConfigId'
          property :device_tier_set, as: 'deviceTierSet', class: Google::Apis::AndroidpublisherV3::DeviceTierSet, decorator: Google::Apis::AndroidpublisherV3::DeviceTierSet::Representation
      
          collection :user_country_sets, as: 'userCountrySets', class: Google::Apis::AndroidpublisherV3::UserCountrySet, decorator: Google::Apis::AndroidpublisherV3::UserCountrySet::Representation
      
        end
      end
      
      class DeviceTierSet
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :device_tiers, as: 'deviceTiers', class: Google::Apis::AndroidpublisherV3::DeviceTier, decorator: Google::Apis::AndroidpublisherV3::DeviceTier::Representation
      
        end
      end
      
      class ExpansionFile
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :file_size, :numeric_string => true, as: 'fileSize'
          property :references_version, as: 'referencesVersion'
        end
      end
      
      class ExpansionFilesUploadResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :expansion_file, as: 'expansionFile', class: Google::Apis::AndroidpublisherV3::ExpansionFile, decorator: Google::Apis::AndroidpublisherV3::ExpansionFile::Representation
      
        end
      end
      
      class ExternalAccountIdentifiers
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :external_account_id, as: 'externalAccountId'
          property :obfuscated_external_account_id, as: 'obfuscatedExternalAccountId'
          property :obfuscated_external_profile_id, as: 'obfuscatedExternalProfileId'
        end
      end
      
      class ExternalSubscription
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :subscription_type, as: 'subscriptionType'
        end
      end
      
      class ExternalTransaction
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :create_time, as: 'createTime'
          property :current_pre_tax_amount, as: 'currentPreTaxAmount', class: Google::Apis::AndroidpublisherV3::Price, decorator: Google::Apis::AndroidpublisherV3::Price::Representation
      
          property :current_tax_amount, as: 'currentTaxAmount', class: Google::Apis::AndroidpublisherV3::Price, decorator: Google::Apis::AndroidpublisherV3::Price::Representation
      
          property :external_transaction_id, as: 'externalTransactionId'
          property :one_time_transaction, as: 'oneTimeTransaction', class: Google::Apis::AndroidpublisherV3::OneTimeExternalTransaction, decorator: Google::Apis::AndroidpublisherV3::OneTimeExternalTransaction::Representation
      
          property :original_pre_tax_amount, as: 'originalPreTaxAmount', class: Google::Apis::AndroidpublisherV3::Price, decorator: Google::Apis::AndroidpublisherV3::Price::Representation
      
          property :original_tax_amount, as: 'originalTaxAmount', class: Google::Apis::AndroidpublisherV3::Price, decorator: Google::Apis::AndroidpublisherV3::Price::Representation
      
          property :package_name, as: 'packageName'
          property :recurring_transaction, as: 'recurringTransaction', class: Google::Apis::AndroidpublisherV3::RecurringExternalTransaction, decorator: Google::Apis::AndroidpublisherV3::RecurringExternalTransaction::Representation
      
          property :test_purchase, as: 'testPurchase', class: Google::Apis::AndroidpublisherV3::ExternalTransactionTestPurchase, decorator: Google::Apis::AndroidpublisherV3::ExternalTransactionTestPurchase::Representation
      
          property :transaction_state, as: 'transactionState'
          property :transaction_time, as: 'transactionTime'
          property :user_tax_address, as: 'userTaxAddress', class: Google::Apis::AndroidpublisherV3::ExternalTransactionAddress, decorator: Google::Apis::AndroidpublisherV3::ExternalTransactionAddress::Representation
      
        end
      end
      
      class ExternalTransactionAddress
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :administrative_area, as: 'administrativeArea'
          property :region_code, as: 'regionCode'
        end
      end
      
      class ExternalTransactionTestPurchase
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class ExternallyHostedApk
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :application_label, as: 'applicationLabel'
          collection :certificate_base64s, as: 'certificateBase64s'
          property :externally_hosted_url, as: 'externallyHostedUrl'
          property :file_sha1_base64, as: 'fileSha1Base64'
          property :file_sha256_base64, as: 'fileSha256Base64'
          property :file_size, :numeric_string => true, as: 'fileSize'
          property :icon_base64, as: 'iconBase64'
          property :maximum_sdk, as: 'maximumSdk'
          property :minimum_sdk, as: 'minimumSdk'
          collection :native_codes, as: 'nativeCodes'
          property :package_name, as: 'packageName'
          collection :uses_features, as: 'usesFeatures'
          collection :uses_permissions, as: 'usesPermissions', class: Google::Apis::AndroidpublisherV3::UsesPermission, decorator: Google::Apis::AndroidpublisherV3::UsesPermission::Representation
      
          property :version_code, as: 'versionCode'
          property :version_name, as: 'versionName'
        end
      end
      
      class FullRefund
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class GeneratedApksListResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :generated_apks, as: 'generatedApks', class: Google::Apis::AndroidpublisherV3::GeneratedApksPerSigningKey, decorator: Google::Apis::AndroidpublisherV3::GeneratedApksPerSigningKey::Representation
      
        end
      end
      
      class GeneratedApksPerSigningKey
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :certificate_sha256_hash, as: 'certificateSha256Hash'
          collection :generated_asset_pack_slices, as: 'generatedAssetPackSlices', class: Google::Apis::AndroidpublisherV3::GeneratedAssetPackSlice, decorator: Google::Apis::AndroidpublisherV3::GeneratedAssetPackSlice::Representation
      
          collection :generated_split_apks, as: 'generatedSplitApks', class: Google::Apis::AndroidpublisherV3::GeneratedSplitApk, decorator: Google::Apis::AndroidpublisherV3::GeneratedSplitApk::Representation
      
          collection :generated_standalone_apks, as: 'generatedStandaloneApks', class: Google::Apis::AndroidpublisherV3::GeneratedStandaloneApk, decorator: Google::Apis::AndroidpublisherV3::GeneratedStandaloneApk::Representation
      
          property :generated_universal_apk, as: 'generatedUniversalApk', class: Google::Apis::AndroidpublisherV3::GeneratedUniversalApk, decorator: Google::Apis::AndroidpublisherV3::GeneratedUniversalApk::Representation
      
          property :targeting_info, as: 'targetingInfo', class: Google::Apis::AndroidpublisherV3::TargetingInfo, decorator: Google::Apis::AndroidpublisherV3::TargetingInfo::Representation
      
        end
      end
      
      class GeneratedAssetPackSlice
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :download_id, as: 'downloadId'
          property :module_name, as: 'moduleName'
          property :slice_id, as: 'sliceId'
          property :version, :numeric_string => true, as: 'version'
        end
      end
      
      class GeneratedSplitApk
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :download_id, as: 'downloadId'
          property :module_name, as: 'moduleName'
          property :split_id, as: 'splitId'
          property :variant_id, as: 'variantId'
        end
      end
      
      class GeneratedStandaloneApk
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :download_id, as: 'downloadId'
          property :variant_id, as: 'variantId'
        end
      end
      
      class GeneratedUniversalApk
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :download_id, as: 'downloadId'
        end
      end
      
      class Grant
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :app_level_permissions, as: 'appLevelPermissions'
          property :name, as: 'name'
          property :package_name, as: 'packageName'
        end
      end
      
      class Image
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :id, as: 'id'
          property :sha1, as: 'sha1'
          property :sha256, as: 'sha256'
          property :url, as: 'url'
        end
      end
      
      class ImagesDeleteAllResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :deleted, as: 'deleted', class: Google::Apis::AndroidpublisherV3::Image, decorator: Google::Apis::AndroidpublisherV3::Image::Representation
      
        end
      end
      
      class ImagesListResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :images, as: 'images', class: Google::Apis::AndroidpublisherV3::Image, decorator: Google::Apis::AndroidpublisherV3::Image::Representation
      
        end
      end
      
      class ImagesUploadResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :image, as: 'image', class: Google::Apis::AndroidpublisherV3::Image, decorator: Google::Apis::AndroidpublisherV3::Image::Representation
      
        end
      end
      
      class InAppProduct
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :default_language, as: 'defaultLanguage'
          property :default_price, as: 'defaultPrice', class: Google::Apis::AndroidpublisherV3::Price, decorator: Google::Apis::AndroidpublisherV3::Price::Representation
      
          property :grace_period, as: 'gracePeriod'
          hash :listings, as: 'listings', class: Google::Apis::AndroidpublisherV3::InAppProductListing, decorator: Google::Apis::AndroidpublisherV3::InAppProductListing::Representation
      
          property :managed_product_taxes_and_compliance_settings, as: 'managedProductTaxesAndComplianceSettings', class: Google::Apis::AndroidpublisherV3::ManagedProductTaxAndComplianceSettings, decorator: Google::Apis::AndroidpublisherV3::ManagedProductTaxAndComplianceSettings::Representation
      
          property :package_name, as: 'packageName'
          hash :prices, as: 'prices', class: Google::Apis::AndroidpublisherV3::Price, decorator: Google::Apis::AndroidpublisherV3::Price::Representation
      
          property :purchase_type, as: 'purchaseType'
          property :sku, as: 'sku'
          property :status, as: 'status'
          property :subscription_period, as: 'subscriptionPeriod'
          property :subscription_taxes_and_compliance_settings, as: 'subscriptionTaxesAndComplianceSettings', class: Google::Apis::AndroidpublisherV3::SubscriptionTaxAndComplianceSettings, decorator: Google::Apis::AndroidpublisherV3::SubscriptionTaxAndComplianceSettings::Representation
      
          property :trial_period, as: 'trialPeriod'
        end
      end
      
      class InAppProductListing
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :benefits, as: 'benefits'
          property :description, as: 'description'
          property :title, as: 'title'
        end
      end
      
      class InappproductsListResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :inappproduct, as: 'inappproduct', class: Google::Apis::AndroidpublisherV3::InAppProduct, decorator: Google::Apis::AndroidpublisherV3::InAppProduct::Representation
      
          property :kind, as: 'kind'
          property :page_info, as: 'pageInfo', class: Google::Apis::AndroidpublisherV3::PageInfo, decorator: Google::Apis::AndroidpublisherV3::PageInfo::Representation
      
          property :token_pagination, as: 'tokenPagination', class: Google::Apis::AndroidpublisherV3::TokenPagination, decorator: Google::Apis::AndroidpublisherV3::TokenPagination::Representation
      
        end
      end
      
      class InternalAppSharingArtifact
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :certificate_fingerprint, as: 'certificateFingerprint'
          property :download_url, as: 'downloadUrl'
          property :sha256, as: 'sha256'
        end
      end
      
      class IntroductoryPriceInfo
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :introductory_price_amount_micros, :numeric_string => true, as: 'introductoryPriceAmountMicros'
          property :introductory_price_currency_code, as: 'introductoryPriceCurrencyCode'
          property :introductory_price_cycles, as: 'introductoryPriceCycles'
          property :introductory_price_period, as: 'introductoryPricePeriod'
        end
      end
      
      class LanguageTargeting
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :alternatives, as: 'alternatives'
          collection :value, as: 'value'
        end
      end
      
      class ListDeviceTierConfigsResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :device_tier_configs, as: 'deviceTierConfigs', class: Google::Apis::AndroidpublisherV3::DeviceTierConfig, decorator: Google::Apis::AndroidpublisherV3::DeviceTierConfig::Representation
      
          property :next_page_token, as: 'nextPageToken'
        end
      end
      
      class ListSubscriptionOffersResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :next_page_token, as: 'nextPageToken'
          collection :subscription_offers, as: 'subscriptionOffers', class: Google::Apis::AndroidpublisherV3::SubscriptionOffer, decorator: Google::Apis::AndroidpublisherV3::SubscriptionOffer::Representation
      
        end
      end
      
      class ListSubscriptionsResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :next_page_token, as: 'nextPageToken'
          collection :subscriptions, as: 'subscriptions', class: Google::Apis::AndroidpublisherV3::Subscription, decorator: Google::Apis::AndroidpublisherV3::Subscription::Representation
      
        end
      end
      
      class ListUsersResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :next_page_token, as: 'nextPageToken'
          collection :users, as: 'users', class: Google::Apis::AndroidpublisherV3::User, decorator: Google::Apis::AndroidpublisherV3::User::Representation
      
        end
      end
      
      class Listing
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :full_description, as: 'fullDescription'
          property :language, as: 'language'
          property :short_description, as: 'shortDescription'
          property :title, as: 'title'
          property :video, as: 'video'
        end
      end
      
      class ListingsListResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :kind, as: 'kind'
          collection :listings, as: 'listings', class: Google::Apis::AndroidpublisherV3::Listing, decorator: Google::Apis::AndroidpublisherV3::Listing::Representation
      
        end
      end
      
      class LocalizedText
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :language, as: 'language'
          property :text, as: 'text'
        end
      end
      
      class ManagedProductTaxAndComplianceSettings
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :eea_withdrawal_right_type, as: 'eeaWithdrawalRightType'
          property :is_tokenized_digital_asset, as: 'isTokenizedDigitalAsset'
          hash :tax_rate_info_by_region_code, as: 'taxRateInfoByRegionCode', class: Google::Apis::AndroidpublisherV3::RegionalTaxRateInfo, decorator: Google::Apis::AndroidpublisherV3::RegionalTaxRateInfo::Representation
      
        end
      end
      
      class MigrateBasePlanPricesRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :regional_price_migrations, as: 'regionalPriceMigrations', class: Google::Apis::AndroidpublisherV3::RegionalPriceMigrationConfig, decorator: Google::Apis::AndroidpublisherV3::RegionalPriceMigrationConfig::Representation
      
          property :regions_version, as: 'regionsVersion', class: Google::Apis::AndroidpublisherV3::RegionsVersion, decorator: Google::Apis::AndroidpublisherV3::RegionsVersion::Representation
      
        end
      end
      
      class MigrateBasePlanPricesResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class ModuleMetadata
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :delivery_type, as: 'deliveryType'
          collection :dependencies, as: 'dependencies'
          property :module_type, as: 'moduleType'
          property :name, as: 'name'
          property :targeting, as: 'targeting', class: Google::Apis::AndroidpublisherV3::ModuleTargeting, decorator: Google::Apis::AndroidpublisherV3::ModuleTargeting::Representation
      
        end
      end
      
      class ModuleTargeting
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :device_feature_targeting, as: 'deviceFeatureTargeting', class: Google::Apis::AndroidpublisherV3::DeviceFeatureTargeting, decorator: Google::Apis::AndroidpublisherV3::DeviceFeatureTargeting::Representation
      
          property :sdk_version_targeting, as: 'sdkVersionTargeting', class: Google::Apis::AndroidpublisherV3::SdkVersionTargeting, decorator: Google::Apis::AndroidpublisherV3::SdkVersionTargeting::Representation
      
          property :user_countries_targeting, as: 'userCountriesTargeting', class: Google::Apis::AndroidpublisherV3::UserCountriesTargeting, decorator: Google::Apis::AndroidpublisherV3::UserCountriesTargeting::Representation
      
        end
      end
      
      class Money
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :currency_code, as: 'currencyCode'
          property :nanos, as: 'nanos'
          property :units, :numeric_string => true, as: 'units'
        end
      end
      
      class MultiAbi
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :abi, as: 'abi', class: Google::Apis::AndroidpublisherV3::Abi, decorator: Google::Apis::AndroidpublisherV3::Abi::Representation
      
        end
      end
      
      class MultiAbiTargeting
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :alternatives, as: 'alternatives', class: Google::Apis::AndroidpublisherV3::MultiAbi, decorator: Google::Apis::AndroidpublisherV3::MultiAbi::Representation
      
          collection :value, as: 'value', class: Google::Apis::AndroidpublisherV3::MultiAbi, decorator: Google::Apis::AndroidpublisherV3::MultiAbi::Representation
      
        end
      end
      
      class OfferDetails
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :base_plan_id, as: 'basePlanId'
          property :offer_id, as: 'offerId'
          collection :offer_tags, as: 'offerTags'
        end
      end
      
      class OfferTag
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :tag, as: 'tag'
        end
      end
      
      class OneTimeExternalTransaction
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :external_transaction_token, as: 'externalTransactionToken'
        end
      end
      
      class OtherRegionsBasePlanConfig
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :eur_price, as: 'eurPrice', class: Google::Apis::AndroidpublisherV3::Money, decorator: Google::Apis::AndroidpublisherV3::Money::Representation
      
          property :new_subscriber_availability, as: 'newSubscriberAvailability'
          property :usd_price, as: 'usdPrice', class: Google::Apis::AndroidpublisherV3::Money, decorator: Google::Apis::AndroidpublisherV3::Money::Representation
      
        end
      end
      
      class OtherRegionsSubscriptionOfferConfig
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :other_regions_new_subscriber_availability, as: 'otherRegionsNewSubscriberAvailability'
        end
      end
      
      class OtherRegionsSubscriptionOfferPhaseConfig
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :absolute_discounts, as: 'absoluteDiscounts', class: Google::Apis::AndroidpublisherV3::OtherRegionsSubscriptionOfferPhasePrices, decorator: Google::Apis::AndroidpublisherV3::OtherRegionsSubscriptionOfferPhasePrices::Representation
      
          property :other_regions_prices, as: 'otherRegionsPrices', class: Google::Apis::AndroidpublisherV3::OtherRegionsSubscriptionOfferPhasePrices, decorator: Google::Apis::AndroidpublisherV3::OtherRegionsSubscriptionOfferPhasePrices::Representation
      
          property :relative_discount, as: 'relativeDiscount'
        end
      end
      
      class OtherRegionsSubscriptionOfferPhasePrices
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :eur_price, as: 'eurPrice', class: Google::Apis::AndroidpublisherV3::Money, decorator: Google::Apis::AndroidpublisherV3::Money::Representation
      
          property :usd_price, as: 'usdPrice', class: Google::Apis::AndroidpublisherV3::Money, decorator: Google::Apis::AndroidpublisherV3::Money::Representation
      
        end
      end
      
      class PageInfo
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :result_per_page, as: 'resultPerPage'
          property :start_index, as: 'startIndex'
          property :total_results, as: 'totalResults'
        end
      end
      
      class PartialRefund
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :refund_id, as: 'refundId'
          property :refund_pre_tax_amount, as: 'refundPreTaxAmount', class: Google::Apis::AndroidpublisherV3::Price, decorator: Google::Apis::AndroidpublisherV3::Price::Representation
      
        end
      end
      
      class PausedStateContext
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :auto_resume_time, as: 'autoResumeTime'
        end
      end
      
      class PrepaidBasePlanType
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :billing_period_duration, as: 'billingPeriodDuration'
          property :time_extension, as: 'timeExtension'
        end
      end
      
      class PrepaidPlan
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :allow_extend_after_time, as: 'allowExtendAfterTime'
        end
      end
      
      class Price
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :currency, as: 'currency'
          property :price_micros, as: 'priceMicros'
        end
      end
      
      class ProductPurchase
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :acknowledgement_state, as: 'acknowledgementState'
          property :consumption_state, as: 'consumptionState'
          property :developer_payload, as: 'developerPayload'
          property :kind, as: 'kind'
          property :obfuscated_external_account_id, as: 'obfuscatedExternalAccountId'
          property :obfuscated_external_profile_id, as: 'obfuscatedExternalProfileId'
          property :order_id, as: 'orderId'
          property :product_id, as: 'productId'
          property :purchase_state, as: 'purchaseState'
          property :purchase_time_millis, :numeric_string => true, as: 'purchaseTimeMillis'
          property :purchase_token, as: 'purchaseToken'
          property :purchase_type, as: 'purchaseType'
          property :quantity, as: 'quantity'
          property :region_code, as: 'regionCode'
        end
      end
      
      class ProductPurchasesAcknowledgeRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :developer_payload, as: 'developerPayload'
        end
      end
      
      class RecurringExternalTransaction
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :external_subscription, as: 'externalSubscription', class: Google::Apis::AndroidpublisherV3::ExternalSubscription, decorator: Google::Apis::AndroidpublisherV3::ExternalSubscription::Representation
      
          property :external_transaction_token, as: 'externalTransactionToken'
          property :initial_external_transaction_id, as: 'initialExternalTransactionId'
          property :migrated_transaction_program, as: 'migratedTransactionProgram'
        end
      end
      
      class RefundExternalTransactionRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :full_refund, as: 'fullRefund', class: Google::Apis::AndroidpublisherV3::FullRefund, decorator: Google::Apis::AndroidpublisherV3::FullRefund::Representation
      
          property :partial_refund, as: 'partialRefund', class: Google::Apis::AndroidpublisherV3::PartialRefund, decorator: Google::Apis::AndroidpublisherV3::PartialRefund::Representation
      
          property :refund_time, as: 'refundTime'
        end
      end
      
      class RegionalBasePlanConfig
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :new_subscriber_availability, as: 'newSubscriberAvailability'
          property :price, as: 'price', class: Google::Apis::AndroidpublisherV3::Money, decorator: Google::Apis::AndroidpublisherV3::Money::Representation
      
          property :region_code, as: 'regionCode'
        end
      end
      
      class RegionalPriceMigrationConfig
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :oldest_allowed_price_version_time, as: 'oldestAllowedPriceVersionTime'
          property :price_increase_type, as: 'priceIncreaseType'
          property :region_code, as: 'regionCode'
        end
      end
      
      class RegionalSubscriptionOfferConfig
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :new_subscriber_availability, as: 'newSubscriberAvailability'
          property :region_code, as: 'regionCode'
        end
      end
      
      class RegionalSubscriptionOfferPhaseConfig
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :absolute_discount, as: 'absoluteDiscount', class: Google::Apis::AndroidpublisherV3::Money, decorator: Google::Apis::AndroidpublisherV3::Money::Representation
      
          property :price, as: 'price', class: Google::Apis::AndroidpublisherV3::Money, decorator: Google::Apis::AndroidpublisherV3::Money::Representation
      
          property :region_code, as: 'regionCode'
          property :relative_discount, as: 'relativeDiscount'
        end
      end
      
      class RegionalTaxRateInfo
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :eligible_for_streaming_service_tax_rate, as: 'eligibleForStreamingServiceTaxRate'
          property :streaming_tax_type, as: 'streamingTaxType'
          property :tax_tier, as: 'taxTier'
        end
      end
      
      class RegionsVersion
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :version, as: 'version'
        end
      end
      
      class ReplacementCancellation
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class Review
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :author_name, as: 'authorName'
          collection :comments, as: 'comments', class: Google::Apis::AndroidpublisherV3::Comment, decorator: Google::Apis::AndroidpublisherV3::Comment::Representation
      
          property :review_id, as: 'reviewId'
        end
      end
      
      class ReviewReplyResult
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :last_edited, as: 'lastEdited', class: Google::Apis::AndroidpublisherV3::Timestamp, decorator: Google::Apis::AndroidpublisherV3::Timestamp::Representation
      
          property :reply_text, as: 'replyText'
        end
      end
      
      class ReviewsListResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :page_info, as: 'pageInfo', class: Google::Apis::AndroidpublisherV3::PageInfo, decorator: Google::Apis::AndroidpublisherV3::PageInfo::Representation
      
          collection :reviews, as: 'reviews', class: Google::Apis::AndroidpublisherV3::Review, decorator: Google::Apis::AndroidpublisherV3::Review::Representation
      
          property :token_pagination, as: 'tokenPagination', class: Google::Apis::AndroidpublisherV3::TokenPagination, decorator: Google::Apis::AndroidpublisherV3::TokenPagination::Representation
      
        end
      end
      
      class ReviewsReplyRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :reply_text, as: 'replyText'
        end
      end
      
      class ReviewsReplyResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :result, as: 'result', class: Google::Apis::AndroidpublisherV3::ReviewReplyResult, decorator: Google::Apis::AndroidpublisherV3::ReviewReplyResult::Representation
      
        end
      end
      
      class ScreenDensity
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :density_alias, as: 'densityAlias'
          property :density_dpi, as: 'densityDpi'
        end
      end
      
      class ScreenDensityTargeting
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :alternatives, as: 'alternatives', class: Google::Apis::AndroidpublisherV3::ScreenDensity, decorator: Google::Apis::AndroidpublisherV3::ScreenDensity::Representation
      
          collection :value, as: 'value', class: Google::Apis::AndroidpublisherV3::ScreenDensity, decorator: Google::Apis::AndroidpublisherV3::ScreenDensity::Representation
      
        end
      end
      
      class SdkVersion
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :min, as: 'min'
        end
      end
      
      class SdkVersionTargeting
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :alternatives, as: 'alternatives', class: Google::Apis::AndroidpublisherV3::SdkVersion, decorator: Google::Apis::AndroidpublisherV3::SdkVersion::Representation
      
          collection :value, as: 'value', class: Google::Apis::AndroidpublisherV3::SdkVersion, decorator: Google::Apis::AndroidpublisherV3::SdkVersion::Representation
      
        end
      end
      
      class SplitApkMetadata
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :is_master_split, as: 'isMasterSplit'
          property :split_id, as: 'splitId'
        end
      end
      
      class SplitApkVariant
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :apk_set, as: 'apkSet', class: Google::Apis::AndroidpublisherV3::ApkSet, decorator: Google::Apis::AndroidpublisherV3::ApkSet::Representation
      
          property :targeting, as: 'targeting', class: Google::Apis::AndroidpublisherV3::VariantTargeting, decorator: Google::Apis::AndroidpublisherV3::VariantTargeting::Representation
      
          property :variant_number, as: 'variantNumber'
        end
      end
      
      class StandaloneApkMetadata
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :fused_module_name, as: 'fusedModuleName'
        end
      end
      
      class SubscribeWithGoogleInfo
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :email_address, as: 'emailAddress'
          property :family_name, as: 'familyName'
          property :given_name, as: 'givenName'
          property :profile_id, as: 'profileId'
          property :profile_name, as: 'profileName'
        end
      end
      
      class Subscription
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :archived, as: 'archived'
          collection :base_plans, as: 'basePlans', class: Google::Apis::AndroidpublisherV3::BasePlan, decorator: Google::Apis::AndroidpublisherV3::BasePlan::Representation
      
          collection :listings, as: 'listings', class: Google::Apis::AndroidpublisherV3::SubscriptionListing, decorator: Google::Apis::AndroidpublisherV3::SubscriptionListing::Representation
      
          property :package_name, as: 'packageName'
          property :product_id, as: 'productId'
          property :tax_and_compliance_settings, as: 'taxAndComplianceSettings', class: Google::Apis::AndroidpublisherV3::SubscriptionTaxAndComplianceSettings, decorator: Google::Apis::AndroidpublisherV3::SubscriptionTaxAndComplianceSettings::Representation
      
        end
      end
      
      class SubscriptionCancelSurveyResult
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :cancel_survey_reason, as: 'cancelSurveyReason'
          property :user_input_cancel_reason, as: 'userInputCancelReason'
        end
      end
      
      class SubscriptionDeferralInfo
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :desired_expiry_time_millis, :numeric_string => true, as: 'desiredExpiryTimeMillis'
          property :expected_expiry_time_millis, :numeric_string => true, as: 'expectedExpiryTimeMillis'
        end
      end
      
      class SubscriptionItemPriceChangeDetails
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :expected_new_price_charge_time, as: 'expectedNewPriceChargeTime'
          property :new_price, as: 'newPrice', class: Google::Apis::AndroidpublisherV3::Money, decorator: Google::Apis::AndroidpublisherV3::Money::Representation
      
          property :price_change_mode, as: 'priceChangeMode'
          property :price_change_state, as: 'priceChangeState'
        end
      end
      
      class SubscriptionListing
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :benefits, as: 'benefits'
          property :description, as: 'description'
          property :language_code, as: 'languageCode'
          property :title, as: 'title'
        end
      end
      
      class SubscriptionOffer
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :base_plan_id, as: 'basePlanId'
          property :offer_id, as: 'offerId'
          collection :offer_tags, as: 'offerTags', class: Google::Apis::AndroidpublisherV3::OfferTag, decorator: Google::Apis::AndroidpublisherV3::OfferTag::Representation
      
          property :other_regions_config, as: 'otherRegionsConfig', class: Google::Apis::AndroidpublisherV3::OtherRegionsSubscriptionOfferConfig, decorator: Google::Apis::AndroidpublisherV3::OtherRegionsSubscriptionOfferConfig::Representation
      
          property :package_name, as: 'packageName'
          collection :phases, as: 'phases', class: Google::Apis::AndroidpublisherV3::SubscriptionOfferPhase, decorator: Google::Apis::AndroidpublisherV3::SubscriptionOfferPhase::Representation
      
          property :product_id, as: 'productId'
          collection :regional_configs, as: 'regionalConfigs', class: Google::Apis::AndroidpublisherV3::RegionalSubscriptionOfferConfig, decorator: Google::Apis::AndroidpublisherV3::RegionalSubscriptionOfferConfig::Representation
      
          property :state, as: 'state'
          property :targeting, as: 'targeting', class: Google::Apis::AndroidpublisherV3::SubscriptionOfferTargeting, decorator: Google::Apis::AndroidpublisherV3::SubscriptionOfferTargeting::Representation
      
        end
      end
      
      class SubscriptionOfferPhase
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :duration, as: 'duration'
          property :other_regions_config, as: 'otherRegionsConfig', class: Google::Apis::AndroidpublisherV3::OtherRegionsSubscriptionOfferPhaseConfig, decorator: Google::Apis::AndroidpublisherV3::OtherRegionsSubscriptionOfferPhaseConfig::Representation
      
          property :recurrence_count, as: 'recurrenceCount'
          collection :regional_configs, as: 'regionalConfigs', class: Google::Apis::AndroidpublisherV3::RegionalSubscriptionOfferPhaseConfig, decorator: Google::Apis::AndroidpublisherV3::RegionalSubscriptionOfferPhaseConfig::Representation
      
        end
      end
      
      class SubscriptionOfferTargeting
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :acquisition_rule, as: 'acquisitionRule', class: Google::Apis::AndroidpublisherV3::AcquisitionTargetingRule, decorator: Google::Apis::AndroidpublisherV3::AcquisitionTargetingRule::Representation
      
          property :upgrade_rule, as: 'upgradeRule', class: Google::Apis::AndroidpublisherV3::UpgradeTargetingRule, decorator: Google::Apis::AndroidpublisherV3::UpgradeTargetingRule::Representation
      
        end
      end
      
      class SubscriptionPriceChange
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :new_price, as: 'newPrice', class: Google::Apis::AndroidpublisherV3::Price, decorator: Google::Apis::AndroidpublisherV3::Price::Representation
      
          property :state, as: 'state'
        end
      end
      
      class SubscriptionPurchase
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :acknowledgement_state, as: 'acknowledgementState'
          property :auto_renewing, as: 'autoRenewing'
          property :auto_resume_time_millis, :numeric_string => true, as: 'autoResumeTimeMillis'
          property :cancel_reason, as: 'cancelReason'
          property :cancel_survey_result, as: 'cancelSurveyResult', class: Google::Apis::AndroidpublisherV3::SubscriptionCancelSurveyResult, decorator: Google::Apis::AndroidpublisherV3::SubscriptionCancelSurveyResult::Representation
      
          property :country_code, as: 'countryCode'
          property :developer_payload, as: 'developerPayload'
          property :email_address, as: 'emailAddress'
          property :expiry_time_millis, :numeric_string => true, as: 'expiryTimeMillis'
          property :external_account_id, as: 'externalAccountId'
          property :family_name, as: 'familyName'
          property :given_name, as: 'givenName'
          property :introductory_price_info, as: 'introductoryPriceInfo', class: Google::Apis::AndroidpublisherV3::IntroductoryPriceInfo, decorator: Google::Apis::AndroidpublisherV3::IntroductoryPriceInfo::Representation
      
          property :kind, as: 'kind'
          property :linked_purchase_token, as: 'linkedPurchaseToken'
          property :obfuscated_external_account_id, as: 'obfuscatedExternalAccountId'
          property :obfuscated_external_profile_id, as: 'obfuscatedExternalProfileId'
          property :order_id, as: 'orderId'
          property :payment_state, as: 'paymentState'
          property :price_amount_micros, :numeric_string => true, as: 'priceAmountMicros'
          property :price_change, as: 'priceChange', class: Google::Apis::AndroidpublisherV3::SubscriptionPriceChange, decorator: Google::Apis::AndroidpublisherV3::SubscriptionPriceChange::Representation
      
          property :price_currency_code, as: 'priceCurrencyCode'
          property :profile_id, as: 'profileId'
          property :profile_name, as: 'profileName'
          property :promotion_code, as: 'promotionCode'
          property :promotion_type, as: 'promotionType'
          property :purchase_type, as: 'purchaseType'
          property :start_time_millis, :numeric_string => true, as: 'startTimeMillis'
          property :user_cancellation_time_millis, :numeric_string => true, as: 'userCancellationTimeMillis'
        end
      end
      
      class SubscriptionPurchaseLineItem
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :auto_renewing_plan, as: 'autoRenewingPlan', class: Google::Apis::AndroidpublisherV3::AutoRenewingPlan, decorator: Google::Apis::AndroidpublisherV3::AutoRenewingPlan::Representation
      
          property :deferred_item_replacement, as: 'deferredItemReplacement', class: Google::Apis::AndroidpublisherV3::DeferredItemReplacement, decorator: Google::Apis::AndroidpublisherV3::DeferredItemReplacement::Representation
      
          property :expiry_time, as: 'expiryTime'
          property :offer_details, as: 'offerDetails', class: Google::Apis::AndroidpublisherV3::OfferDetails, decorator: Google::Apis::AndroidpublisherV3::OfferDetails::Representation
      
          property :prepaid_plan, as: 'prepaidPlan', class: Google::Apis::AndroidpublisherV3::PrepaidPlan, decorator: Google::Apis::AndroidpublisherV3::PrepaidPlan::Representation
      
          property :product_id, as: 'productId'
        end
      end
      
      class SubscriptionPurchaseV2
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :acknowledgement_state, as: 'acknowledgementState'
          property :canceled_state_context, as: 'canceledStateContext', class: Google::Apis::AndroidpublisherV3::CanceledStateContext, decorator: Google::Apis::AndroidpublisherV3::CanceledStateContext::Representation
      
          property :external_account_identifiers, as: 'externalAccountIdentifiers', class: Google::Apis::AndroidpublisherV3::ExternalAccountIdentifiers, decorator: Google::Apis::AndroidpublisherV3::ExternalAccountIdentifiers::Representation
      
          property :kind, as: 'kind'
          property :latest_order_id, as: 'latestOrderId'
          collection :line_items, as: 'lineItems', class: Google::Apis::AndroidpublisherV3::SubscriptionPurchaseLineItem, decorator: Google::Apis::AndroidpublisherV3::SubscriptionPurchaseLineItem::Representation
      
          property :linked_purchase_token, as: 'linkedPurchaseToken'
          property :paused_state_context, as: 'pausedStateContext', class: Google::Apis::AndroidpublisherV3::PausedStateContext, decorator: Google::Apis::AndroidpublisherV3::PausedStateContext::Representation
      
          property :region_code, as: 'regionCode'
          property :start_time, as: 'startTime'
          property :subscribe_with_google_info, as: 'subscribeWithGoogleInfo', class: Google::Apis::AndroidpublisherV3::SubscribeWithGoogleInfo, decorator: Google::Apis::AndroidpublisherV3::SubscribeWithGoogleInfo::Representation
      
          property :subscription_state, as: 'subscriptionState'
          property :test_purchase, as: 'testPurchase', class: Google::Apis::AndroidpublisherV3::TestPurchase, decorator: Google::Apis::AndroidpublisherV3::TestPurchase::Representation
      
        end
      end
      
      class SubscriptionPurchasesAcknowledgeRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :developer_payload, as: 'developerPayload'
        end
      end
      
      class SubscriptionPurchasesDeferRequest
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :deferral_info, as: 'deferralInfo', class: Google::Apis::AndroidpublisherV3::SubscriptionDeferralInfo, decorator: Google::Apis::AndroidpublisherV3::SubscriptionDeferralInfo::Representation
      
        end
      end
      
      class SubscriptionPurchasesDeferResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :new_expiry_time_millis, :numeric_string => true, as: 'newExpiryTimeMillis'
        end
      end
      
      class SubscriptionTaxAndComplianceSettings
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :eea_withdrawal_right_type, as: 'eeaWithdrawalRightType'
          property :is_tokenized_digital_asset, as: 'isTokenizedDigitalAsset'
          hash :tax_rate_info_by_region_code, as: 'taxRateInfoByRegionCode', class: Google::Apis::AndroidpublisherV3::RegionalTaxRateInfo, decorator: Google::Apis::AndroidpublisherV3::RegionalTaxRateInfo::Representation
      
        end
      end
      
      class SystemApkOptions
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :rotated, as: 'rotated'
          property :uncompressed_dex_files, as: 'uncompressedDexFiles'
          property :uncompressed_native_libraries, as: 'uncompressedNativeLibraries'
        end
      end
      
      class SystemApksListResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :variants, as: 'variants', class: Google::Apis::AndroidpublisherV3::Variant, decorator: Google::Apis::AndroidpublisherV3::Variant::Representation
      
        end
      end
      
      class SystemFeature
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :name, as: 'name'
        end
      end
      
      class SystemInitiatedCancellation
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class TargetingInfo
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :asset_slice_set, as: 'assetSliceSet', class: Google::Apis::AndroidpublisherV3::AssetSliceSet, decorator: Google::Apis::AndroidpublisherV3::AssetSliceSet::Representation
      
          property :package_name, as: 'packageName'
          collection :variant, as: 'variant', class: Google::Apis::AndroidpublisherV3::SplitApkVariant, decorator: Google::Apis::AndroidpublisherV3::SplitApkVariant::Representation
      
        end
      end
      
      class TargetingRuleScope
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :specific_subscription_in_app, as: 'specificSubscriptionInApp'
        end
      end
      
      class TestPurchase
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
        end
      end
      
      class Testers
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :google_groups, as: 'googleGroups'
        end
      end
      
      class TextureCompressionFormat
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :alias, as: 'alias'
        end
      end
      
      class TextureCompressionFormatTargeting
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :alternatives, as: 'alternatives', class: Google::Apis::AndroidpublisherV3::TextureCompressionFormat, decorator: Google::Apis::AndroidpublisherV3::TextureCompressionFormat::Representation
      
          collection :value, as: 'value', class: Google::Apis::AndroidpublisherV3::TextureCompressionFormat, decorator: Google::Apis::AndroidpublisherV3::TextureCompressionFormat::Representation
      
        end
      end
      
      class Timestamp
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :nanos, as: 'nanos'
          property :seconds, :numeric_string => true, as: 'seconds'
        end
      end
      
      class TokenPagination
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :next_page_token, as: 'nextPageToken'
          property :previous_page_token, as: 'previousPageToken'
        end
      end
      
      class Track
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :releases, as: 'releases', class: Google::Apis::AndroidpublisherV3::TrackRelease, decorator: Google::Apis::AndroidpublisherV3::TrackRelease::Representation
      
          property :track, as: 'track'
        end
      end
      
      class TrackConfig
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :form_factor, as: 'formFactor'
          property :track, as: 'track'
          property :type, as: 'type'
        end
      end
      
      class TrackCountryAvailability
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :countries, as: 'countries', class: Google::Apis::AndroidpublisherV3::TrackTargetedCountry, decorator: Google::Apis::AndroidpublisherV3::TrackTargetedCountry::Representation
      
          property :rest_of_world, as: 'restOfWorld'
          property :sync_with_production, as: 'syncWithProduction'
        end
      end
      
      class TrackRelease
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :country_targeting, as: 'countryTargeting', class: Google::Apis::AndroidpublisherV3::CountryTargeting, decorator: Google::Apis::AndroidpublisherV3::CountryTargeting::Representation
      
          property :in_app_update_priority, as: 'inAppUpdatePriority'
          property :name, as: 'name'
          collection :release_notes, as: 'releaseNotes', class: Google::Apis::AndroidpublisherV3::LocalizedText, decorator: Google::Apis::AndroidpublisherV3::LocalizedText::Representation
      
          property :status, as: 'status'
          property :user_fraction, as: 'userFraction'
          collection :version_codes, as: 'versionCodes'
        end
      end
      
      class TrackTargetedCountry
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :country_code, as: 'countryCode'
        end
      end
      
      class TracksListResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :kind, as: 'kind'
          collection :tracks, as: 'tracks', class: Google::Apis::AndroidpublisherV3::Track, decorator: Google::Apis::AndroidpublisherV3::Track::Representation
      
        end
      end
      
      class UpgradeTargetingRule
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :billing_period_duration, as: 'billingPeriodDuration'
          property :once_per_user, as: 'oncePerUser'
          property :scope, as: 'scope', class: Google::Apis::AndroidpublisherV3::TargetingRuleScope, decorator: Google::Apis::AndroidpublisherV3::TargetingRuleScope::Representation
      
        end
      end
      
      class User
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :access_state, as: 'accessState'
          collection :developer_account_permissions, as: 'developerAccountPermissions'
          property :email, as: 'email'
          property :expiration_time, as: 'expirationTime'
          collection :grants, as: 'grants', class: Google::Apis::AndroidpublisherV3::Grant, decorator: Google::Apis::AndroidpublisherV3::Grant::Representation
      
          property :name, as: 'name'
          property :partial, as: 'partial'
        end
      end
      
      class UserComment
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :android_os_version, as: 'androidOsVersion'
          property :app_version_code, as: 'appVersionCode'
          property :app_version_name, as: 'appVersionName'
          property :device, as: 'device'
          property :device_metadata, as: 'deviceMetadata', class: Google::Apis::AndroidpublisherV3::DeviceMetadata, decorator: Google::Apis::AndroidpublisherV3::DeviceMetadata::Representation
      
          property :last_modified, as: 'lastModified', class: Google::Apis::AndroidpublisherV3::Timestamp, decorator: Google::Apis::AndroidpublisherV3::Timestamp::Representation
      
          property :original_text, as: 'originalText'
          property :reviewer_language, as: 'reviewerLanguage'
          property :star_rating, as: 'starRating'
          property :text, as: 'text'
          property :thumbs_down_count, as: 'thumbsDownCount'
          property :thumbs_up_count, as: 'thumbsUpCount'
        end
      end
      
      class UserCountriesTargeting
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :country_codes, as: 'countryCodes'
          property :exclude, as: 'exclude'
        end
      end
      
      class UserCountrySet
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          collection :country_codes, as: 'countryCodes'
          property :name, as: 'name'
        end
      end
      
      class UserInitiatedCancellation
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :cancel_survey_result, as: 'cancelSurveyResult', class: Google::Apis::AndroidpublisherV3::CancelSurveyResult, decorator: Google::Apis::AndroidpublisherV3::CancelSurveyResult::Representation
      
          property :cancel_time, as: 'cancelTime'
        end
      end
      
      class UsesPermission
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :max_sdk_version, as: 'maxSdkVersion'
          property :name, as: 'name'
        end
      end
      
      class Variant
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :device_spec, as: 'deviceSpec', class: Google::Apis::AndroidpublisherV3::DeviceSpec, decorator: Google::Apis::AndroidpublisherV3::DeviceSpec::Representation
      
          property :options, as: 'options', class: Google::Apis::AndroidpublisherV3::SystemApkOptions, decorator: Google::Apis::AndroidpublisherV3::SystemApkOptions::Representation
      
          property :variant_id, as: 'variantId'
        end
      end
      
      class VariantTargeting
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :abi_targeting, as: 'abiTargeting', class: Google::Apis::AndroidpublisherV3::AbiTargeting, decorator: Google::Apis::AndroidpublisherV3::AbiTargeting::Representation
      
          property :multi_abi_targeting, as: 'multiAbiTargeting', class: Google::Apis::AndroidpublisherV3::MultiAbiTargeting, decorator: Google::Apis::AndroidpublisherV3::MultiAbiTargeting::Representation
      
          property :screen_density_targeting, as: 'screenDensityTargeting', class: Google::Apis::AndroidpublisherV3::ScreenDensityTargeting, decorator: Google::Apis::AndroidpublisherV3::ScreenDensityTargeting::Representation
      
          property :sdk_version_targeting, as: 'sdkVersionTargeting', class: Google::Apis::AndroidpublisherV3::SdkVersionTargeting, decorator: Google::Apis::AndroidpublisherV3::SdkVersionTargeting::Representation
      
          property :texture_compression_format_targeting, as: 'textureCompressionFormatTargeting', class: Google::Apis::AndroidpublisherV3::TextureCompressionFormatTargeting, decorator: Google::Apis::AndroidpublisherV3::TextureCompressionFormatTargeting::Representation
      
        end
      end
      
      class VoidedPurchase
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :kind, as: 'kind'
          property :order_id, as: 'orderId'
          property :purchase_time_millis, :numeric_string => true, as: 'purchaseTimeMillis'
          property :purchase_token, as: 'purchaseToken'
          property :voided_reason, as: 'voidedReason'
          property :voided_source, as: 'voidedSource'
          property :voided_time_millis, :numeric_string => true, as: 'voidedTimeMillis'
        end
      end
      
      class VoidedPurchasesListResponse
        # @private
        class Representation < Google::Apis::Core::JsonRepresentation
          property :page_info, as: 'pageInfo', class: Google::Apis::AndroidpublisherV3::PageInfo, decorator: Google::Apis::AndroidpublisherV3::PageInfo::Representation
      
          property :token_pagination, as: 'tokenPagination', class: Google::Apis::AndroidpublisherV3::TokenPagination, decorator: Google::Apis::AndroidpublisherV3::TokenPagination::Representation
      
          collection :voided_purchases, as: 'voidedPurchases', class: Google::Apis::AndroidpublisherV3::VoidedPurchase, decorator: Google::Apis::AndroidpublisherV3::VoidedPurchase::Representation
      
        end
      end
    end
  end
end
