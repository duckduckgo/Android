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
      
      # Information to read/write to blobstore2.
      class GdataBlobstore2Info
        include Google::Apis::Core::Hashable
      
        # The blob generation id.
        # Corresponds to the JSON property `blobGeneration`
        # @return [Fixnum]
        attr_accessor :blob_generation
      
        # The blob id, e.g., /blobstore/prod/playground/scotty
        # Corresponds to the JSON property `blobId`
        # @return [String]
        attr_accessor :blob_id
      
        # Read handle passed from Bigstore -> Scotty for a GCS download. This is a
        # signed, serialized blobstore2.ReadHandle proto which must never be set outside
        # of Bigstore, and is not applicable to non-GCS media downloads.
        # Corresponds to the JSON property `downloadReadHandle`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :download_read_handle
      
        # The blob read token. Needed to read blobs that have not been replicated. Might
        # not be available until the final call.
        # Corresponds to the JSON property `readToken`
        # @return [String]
        attr_accessor :read_token
      
        # Metadata passed from Blobstore -> Scotty for a new GCS upload. This is a
        # signed, serialized blobstore2.BlobMetadataContainer proto which must never be
        # consumed outside of Bigstore, and is not applicable to non-GCS media uploads.
        # Corresponds to the JSON property `uploadMetadataContainer`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :upload_metadata_container
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @blob_generation = args[:blob_generation] if args.key?(:blob_generation)
          @blob_id = args[:blob_id] if args.key?(:blob_id)
          @download_read_handle = args[:download_read_handle] if args.key?(:download_read_handle)
          @read_token = args[:read_token] if args.key?(:read_token)
          @upload_metadata_container = args[:upload_metadata_container] if args.key?(:upload_metadata_container)
        end
      end
      
      # A sequence of media data references representing composite data. Introduced to
      # support Bigstore composite objects. For details, visit http://go/bigstore-
      # composites.
      class GdataCompositeMedia
        include Google::Apis::Core::Hashable
      
        # Blobstore v1 reference, set if reference_type is BLOBSTORE_REF This should be
        # the byte representation of a blobstore.BlobRef. Since Blobstore is deprecating
        # v1, use blobstore2_info instead. For now, any v2 blob will also be represented
        # in this field as v1 BlobRef.
        # Corresponds to the JSON property `blobRef`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :blob_ref
      
        # Information to read/write to blobstore2.
        # Corresponds to the JSON property `blobstore2Info`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataBlobstore2Info]
        attr_accessor :blobstore2_info
      
        # A binary data reference for a media download. Serves as a technology-agnostic
        # binary reference in some Google infrastructure. This value is a serialized
        # storage_cosmo.BinaryReference proto. Storing it as bytes is a hack to get
        # around the fact that the cosmo proto (as well as others it includes) doesn't
        # support JavaScript. This prevents us from including the actual type of this
        # field.
        # Corresponds to the JSON property `cosmoBinaryReference`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :cosmo_binary_reference
      
        # crc32.c hash for the payload.
        # Corresponds to the JSON property `crc32cHash`
        # @return [Fixnum]
        attr_accessor :crc32c_hash
      
        # Media data, set if reference_type is INLINE
        # Corresponds to the JSON property `inline`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :inline
      
        # Size of the data, in bytes
        # Corresponds to the JSON property `length`
        # @return [Fixnum]
        attr_accessor :length
      
        # MD5 hash for the payload.
        # Corresponds to the JSON property `md5Hash`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :md5_hash
      
        # This is a copy of the tech.blob.ObjectId proto, which could not be used
        # directly here due to transitive closure issues with JavaScript support; see
        # http://b/8801763.
        # Corresponds to the JSON property `objectId`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataObjectId]
        attr_accessor :object_id_prop
      
        # Path to the data, set if reference_type is PATH
        # Corresponds to the JSON property `path`
        # @return [String]
        attr_accessor :path
      
        # Describes what the field reference contains.
        # Corresponds to the JSON property `referenceType`
        # @return [String]
        attr_accessor :reference_type
      
        # SHA-1 hash for the payload.
        # Corresponds to the JSON property `sha1Hash`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :sha1_hash
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @blob_ref = args[:blob_ref] if args.key?(:blob_ref)
          @blobstore2_info = args[:blobstore2_info] if args.key?(:blobstore2_info)
          @cosmo_binary_reference = args[:cosmo_binary_reference] if args.key?(:cosmo_binary_reference)
          @crc32c_hash = args[:crc32c_hash] if args.key?(:crc32c_hash)
          @inline = args[:inline] if args.key?(:inline)
          @length = args[:length] if args.key?(:length)
          @md5_hash = args[:md5_hash] if args.key?(:md5_hash)
          @object_id_prop = args[:object_id_prop] if args.key?(:object_id_prop)
          @path = args[:path] if args.key?(:path)
          @reference_type = args[:reference_type] if args.key?(:reference_type)
          @sha1_hash = args[:sha1_hash] if args.key?(:sha1_hash)
        end
      end
      
      # Detailed Content-Type information from Scotty. The Content-Type of the media
      # will typically be filled in by the header or Scotty's best_guess, but this
      # extended information provides the backend with more information so that it can
      # make a better decision if needed. This is only used on media upload requests
      # from Scotty.
      class GdataContentTypeInfo
        include Google::Apis::Core::Hashable
      
        # Scotty's best guess of what the content type of the file is.
        # Corresponds to the JSON property `bestGuess`
        # @return [String]
        attr_accessor :best_guess
      
        # The content type of the file derived by looking at specific bytes (i.e. "magic
        # bytes") of the actual file.
        # Corresponds to the JSON property `fromBytes`
        # @return [String]
        attr_accessor :from_bytes
      
        # The content type of the file derived from the file extension of the original
        # file name used by the client.
        # Corresponds to the JSON property `fromFileName`
        # @return [String]
        attr_accessor :from_file_name
      
        # The content type of the file as specified in the request headers, multipart
        # headers, or RUPIO start request.
        # Corresponds to the JSON property `fromHeader`
        # @return [String]
        attr_accessor :from_header
      
        # The content type of the file derived from the file extension of the URL path.
        # The URL path is assumed to represent a file name (which is typically only true
        # for agents that are providing a REST API).
        # Corresponds to the JSON property `fromUrlPath`
        # @return [String]
        attr_accessor :from_url_path
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @best_guess = args[:best_guess] if args.key?(:best_guess)
          @from_bytes = args[:from_bytes] if args.key?(:from_bytes)
          @from_file_name = args[:from_file_name] if args.key?(:from_file_name)
          @from_header = args[:from_header] if args.key?(:from_header)
          @from_url_path = args[:from_url_path] if args.key?(:from_url_path)
        end
      end
      
      # Backend response for a Diff get checksums response. For details on the Scotty
      # Diff protocol, visit http://go/scotty-diff-protocol.
      class GdataDiffChecksumsResponse
        include Google::Apis::Core::Hashable
      
        # A sequence of media data references representing composite data. Introduced to
        # support Bigstore composite objects. For details, visit http://go/bigstore-
        # composites.
        # Corresponds to the JSON property `checksumsLocation`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia]
        attr_accessor :checksums_location
      
        # The chunk size of checksums. Must be a multiple of 256KB.
        # Corresponds to the JSON property `chunkSizeBytes`
        # @return [Fixnum]
        attr_accessor :chunk_size_bytes
      
        # A sequence of media data references representing composite data. Introduced to
        # support Bigstore composite objects. For details, visit http://go/bigstore-
        # composites.
        # Corresponds to the JSON property `objectLocation`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia]
        attr_accessor :object_location
      
        # The total size of the server object.
        # Corresponds to the JSON property `objectSizeBytes`
        # @return [Fixnum]
        attr_accessor :object_size_bytes
      
        # The object version of the object the checksums are being returned for.
        # Corresponds to the JSON property `objectVersion`
        # @return [String]
        attr_accessor :object_version
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @checksums_location = args[:checksums_location] if args.key?(:checksums_location)
          @chunk_size_bytes = args[:chunk_size_bytes] if args.key?(:chunk_size_bytes)
          @object_location = args[:object_location] if args.key?(:object_location)
          @object_size_bytes = args[:object_size_bytes] if args.key?(:object_size_bytes)
          @object_version = args[:object_version] if args.key?(:object_version)
        end
      end
      
      # Backend response for a Diff download response. For details on the Scotty Diff
      # protocol, visit http://go/scotty-diff-protocol.
      class GdataDiffDownloadResponse
        include Google::Apis::Core::Hashable
      
        # A sequence of media data references representing composite data. Introduced to
        # support Bigstore composite objects. For details, visit http://go/bigstore-
        # composites.
        # Corresponds to the JSON property `objectLocation`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia]
        attr_accessor :object_location
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @object_location = args[:object_location] if args.key?(:object_location)
        end
      end
      
      # A Diff upload request. For details on the Scotty Diff protocol, visit http://
      # go/scotty-diff-protocol.
      class GdataDiffUploadRequest
        include Google::Apis::Core::Hashable
      
        # A sequence of media data references representing composite data. Introduced to
        # support Bigstore composite objects. For details, visit http://go/bigstore-
        # composites.
        # Corresponds to the JSON property `checksumsInfo`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia]
        attr_accessor :checksums_info
      
        # A sequence of media data references representing composite data. Introduced to
        # support Bigstore composite objects. For details, visit http://go/bigstore-
        # composites.
        # Corresponds to the JSON property `objectInfo`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia]
        attr_accessor :object_info
      
        # The object version of the object that is the base version the incoming diff
        # script will be applied to. This field will always be filled in.
        # Corresponds to the JSON property `objectVersion`
        # @return [String]
        attr_accessor :object_version
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @checksums_info = args[:checksums_info] if args.key?(:checksums_info)
          @object_info = args[:object_info] if args.key?(:object_info)
          @object_version = args[:object_version] if args.key?(:object_version)
        end
      end
      
      # Backend response for a Diff upload request. For details on the Scotty Diff
      # protocol, visit http://go/scotty-diff-protocol.
      class GdataDiffUploadResponse
        include Google::Apis::Core::Hashable
      
        # The object version of the object at the server. Must be included in the end
        # notification response. The version in the end notification response must
        # correspond to the new version of the object that is now stored at the server,
        # after the upload.
        # Corresponds to the JSON property `objectVersion`
        # @return [String]
        attr_accessor :object_version
      
        # A sequence of media data references representing composite data. Introduced to
        # support Bigstore composite objects. For details, visit http://go/bigstore-
        # composites.
        # Corresponds to the JSON property `originalObject`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia]
        attr_accessor :original_object
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @object_version = args[:object_version] if args.key?(:object_version)
          @original_object = args[:original_object] if args.key?(:original_object)
        end
      end
      
      # Backend response for a Diff get version response. For details on the Scotty
      # Diff protocol, visit http://go/scotty-diff-protocol.
      class GdataDiffVersionResponse
        include Google::Apis::Core::Hashable
      
        # The total size of the server object.
        # Corresponds to the JSON property `objectSizeBytes`
        # @return [Fixnum]
        attr_accessor :object_size_bytes
      
        # The version of the object stored at the server.
        # Corresponds to the JSON property `objectVersion`
        # @return [String]
        attr_accessor :object_version
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @object_size_bytes = args[:object_size_bytes] if args.key?(:object_size_bytes)
          @object_version = args[:object_version] if args.key?(:object_version)
        end
      end
      
      # Parameters specific to media downloads.
      class GdataDownloadParameters
        include Google::Apis::Core::Hashable
      
        # A boolean to be returned in the response to Scotty. Allows/disallows gzip
        # encoding of the payload content when the server thinks it's advantageous (
        # hence, does not guarantee compression) which allows Scotty to GZip the
        # response to the client.
        # Corresponds to the JSON property `allowGzipCompression`
        # @return [Boolean]
        attr_accessor :allow_gzip_compression
        alias_method :allow_gzip_compression?, :allow_gzip_compression
      
        # Determining whether or not Apiary should skip the inclusion of any Content-
        # Range header on its response to Scotty.
        # Corresponds to the JSON property `ignoreRange`
        # @return [Boolean]
        attr_accessor :ignore_range
        alias_method :ignore_range?, :ignore_range
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @allow_gzip_compression = args[:allow_gzip_compression] if args.key?(:allow_gzip_compression)
          @ignore_range = args[:ignore_range] if args.key?(:ignore_range)
        end
      end
      
      # A reference to data stored on the filesystem, on GFS or in blobstore.
      class GdataMedia
        include Google::Apis::Core::Hashable
      
        # Deprecated, use one of explicit hash type fields instead. Algorithm used for
        # calculating the hash. As of 2011/01/21, "MD5" is the only possible value for
        # this field. New values may be added at any time.
        # Corresponds to the JSON property `algorithm`
        # @return [String]
        attr_accessor :algorithm
      
        # Use object_id instead.
        # Corresponds to the JSON property `bigstoreObjectRef`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :bigstore_object_ref
      
        # Blobstore v1 reference, set if reference_type is BLOBSTORE_REF This should be
        # the byte representation of a blobstore.BlobRef. Since Blobstore is deprecating
        # v1, use blobstore2_info instead. For now, any v2 blob will also be represented
        # in this field as v1 BlobRef.
        # Corresponds to the JSON property `blobRef`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :blob_ref
      
        # Information to read/write to blobstore2.
        # Corresponds to the JSON property `blobstore2Info`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataBlobstore2Info]
        attr_accessor :blobstore2_info
      
        # A composite media composed of one or more media objects, set if reference_type
        # is COMPOSITE_MEDIA. The media length field must be set to the sum of the
        # lengths of all composite media objects. Note: All composite media must have
        # length specified.
        # Corresponds to the JSON property `compositeMedia`
        # @return [Array<Google::Apis::FirebaseappdistributionV1::GdataCompositeMedia>]
        attr_accessor :composite_media
      
        # MIME type of the data
        # Corresponds to the JSON property `contentType`
        # @return [String]
        attr_accessor :content_type
      
        # Detailed Content-Type information from Scotty. The Content-Type of the media
        # will typically be filled in by the header or Scotty's best_guess, but this
        # extended information provides the backend with more information so that it can
        # make a better decision if needed. This is only used on media upload requests
        # from Scotty.
        # Corresponds to the JSON property `contentTypeInfo`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataContentTypeInfo]
        attr_accessor :content_type_info
      
        # A binary data reference for a media download. Serves as a technology-agnostic
        # binary reference in some Google infrastructure. This value is a serialized
        # storage_cosmo.BinaryReference proto. Storing it as bytes is a hack to get
        # around the fact that the cosmo proto (as well as others it includes) doesn't
        # support JavaScript. This prevents us from including the actual type of this
        # field.
        # Corresponds to the JSON property `cosmoBinaryReference`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :cosmo_binary_reference
      
        # For Scotty Uploads: Scotty-provided hashes for uploads For Scotty Downloads: (
        # WARNING: DO NOT USE WITHOUT PERMISSION FROM THE SCOTTY TEAM.) A Hash provided
        # by the agent to be used to verify the data being downloaded. Currently only
        # supported for inline payloads. Further, only crc32c_hash is currently
        # supported.
        # Corresponds to the JSON property `crc32cHash`
        # @return [Fixnum]
        attr_accessor :crc32c_hash
      
        # Backend response for a Diff get checksums response. For details on the Scotty
        # Diff protocol, visit http://go/scotty-diff-protocol.
        # Corresponds to the JSON property `diffChecksumsResponse`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataDiffChecksumsResponse]
        attr_accessor :diff_checksums_response
      
        # Backend response for a Diff download response. For details on the Scotty Diff
        # protocol, visit http://go/scotty-diff-protocol.
        # Corresponds to the JSON property `diffDownloadResponse`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataDiffDownloadResponse]
        attr_accessor :diff_download_response
      
        # A Diff upload request. For details on the Scotty Diff protocol, visit http://
        # go/scotty-diff-protocol.
        # Corresponds to the JSON property `diffUploadRequest`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataDiffUploadRequest]
        attr_accessor :diff_upload_request
      
        # Backend response for a Diff upload request. For details on the Scotty Diff
        # protocol, visit http://go/scotty-diff-protocol.
        # Corresponds to the JSON property `diffUploadResponse`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataDiffUploadResponse]
        attr_accessor :diff_upload_response
      
        # Backend response for a Diff get version response. For details on the Scotty
        # Diff protocol, visit http://go/scotty-diff-protocol.
        # Corresponds to the JSON property `diffVersionResponse`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataDiffVersionResponse]
        attr_accessor :diff_version_response
      
        # Parameters specific to media downloads.
        # Corresponds to the JSON property `downloadParameters`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataDownloadParameters]
        attr_accessor :download_parameters
      
        # Original file name
        # Corresponds to the JSON property `filename`
        # @return [String]
        attr_accessor :filename
      
        # Deprecated, use one of explicit hash type fields instead. These two hash
        # related fields will only be populated on Scotty based media uploads and will
        # contain the content of the hash group in the NotificationRequest: http://cs/#
        # google3/uploader/service/proto/upload_listener.proto&q=class:Hash Hex encoded
        # hash value of the uploaded media.
        # Corresponds to the JSON property `hash`
        # @return [String]
        attr_accessor :hash_prop
      
        # For Scotty uploads only. If a user sends a hash code and the backend has
        # requested that Scotty verify the upload against the client hash, Scotty will
        # perform the check on behalf of the backend and will reject it if the hashes
        # don't match. This is set to true if Scotty performed this verification.
        # Corresponds to the JSON property `hashVerified`
        # @return [Boolean]
        attr_accessor :hash_verified
        alias_method :hash_verified?, :hash_verified
      
        # Media data, set if reference_type is INLINE
        # Corresponds to the JSON property `inline`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :inline
      
        # |is_potential_retry| is set false only when Scotty is certain that it has not
        # sent the request before. When a client resumes an upload, this field must be
        # set true in agent calls, because Scotty cannot be certain that it has never
        # sent the request before due to potential failure in the session state
        # persistence.
        # Corresponds to the JSON property `isPotentialRetry`
        # @return [Boolean]
        attr_accessor :is_potential_retry
        alias_method :is_potential_retry?, :is_potential_retry
      
        # Size of the data, in bytes
        # Corresponds to the JSON property `length`
        # @return [Fixnum]
        attr_accessor :length
      
        # Scotty-provided MD5 hash for an upload.
        # Corresponds to the JSON property `md5Hash`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :md5_hash
      
        # Media id to forward to the operation GetMedia. Can be set if reference_type is
        # GET_MEDIA.
        # Corresponds to the JSON property `mediaId`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :media_id
      
        # This is a copy of the tech.blob.ObjectId proto, which could not be used
        # directly here due to transitive closure issues with JavaScript support; see
        # http://b/8801763.
        # Corresponds to the JSON property `objectId`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataObjectId]
        attr_accessor :object_id_prop
      
        # Path to the data, set if reference_type is PATH
        # Corresponds to the JSON property `path`
        # @return [String]
        attr_accessor :path
      
        # Describes what the field reference contains.
        # Corresponds to the JSON property `referenceType`
        # @return [String]
        attr_accessor :reference_type
      
        # Scotty-provided SHA1 hash for an upload.
        # Corresponds to the JSON property `sha1Hash`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :sha1_hash
      
        # Scotty-provided SHA256 hash for an upload.
        # Corresponds to the JSON property `sha256Hash`
        # NOTE: Values are automatically base64 encoded/decoded in the client library.
        # @return [String]
        attr_accessor :sha256_hash
      
        # Time at which the media data was last updated, in milliseconds since UNIX
        # epoch
        # Corresponds to the JSON property `timestamp`
        # @return [Fixnum]
        attr_accessor :timestamp
      
        # A unique fingerprint/version id for the media data
        # Corresponds to the JSON property `token`
        # @return [String]
        attr_accessor :token
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @algorithm = args[:algorithm] if args.key?(:algorithm)
          @bigstore_object_ref = args[:bigstore_object_ref] if args.key?(:bigstore_object_ref)
          @blob_ref = args[:blob_ref] if args.key?(:blob_ref)
          @blobstore2_info = args[:blobstore2_info] if args.key?(:blobstore2_info)
          @composite_media = args[:composite_media] if args.key?(:composite_media)
          @content_type = args[:content_type] if args.key?(:content_type)
          @content_type_info = args[:content_type_info] if args.key?(:content_type_info)
          @cosmo_binary_reference = args[:cosmo_binary_reference] if args.key?(:cosmo_binary_reference)
          @crc32c_hash = args[:crc32c_hash] if args.key?(:crc32c_hash)
          @diff_checksums_response = args[:diff_checksums_response] if args.key?(:diff_checksums_response)
          @diff_download_response = args[:diff_download_response] if args.key?(:diff_download_response)
          @diff_upload_request = args[:diff_upload_request] if args.key?(:diff_upload_request)
          @diff_upload_response = args[:diff_upload_response] if args.key?(:diff_upload_response)
          @diff_version_response = args[:diff_version_response] if args.key?(:diff_version_response)
          @download_parameters = args[:download_parameters] if args.key?(:download_parameters)
          @filename = args[:filename] if args.key?(:filename)
          @hash_prop = args[:hash_prop] if args.key?(:hash_prop)
          @hash_verified = args[:hash_verified] if args.key?(:hash_verified)
          @inline = args[:inline] if args.key?(:inline)
          @is_potential_retry = args[:is_potential_retry] if args.key?(:is_potential_retry)
          @length = args[:length] if args.key?(:length)
          @md5_hash = args[:md5_hash] if args.key?(:md5_hash)
          @media_id = args[:media_id] if args.key?(:media_id)
          @object_id_prop = args[:object_id_prop] if args.key?(:object_id_prop)
          @path = args[:path] if args.key?(:path)
          @reference_type = args[:reference_type] if args.key?(:reference_type)
          @sha1_hash = args[:sha1_hash] if args.key?(:sha1_hash)
          @sha256_hash = args[:sha256_hash] if args.key?(:sha256_hash)
          @timestamp = args[:timestamp] if args.key?(:timestamp)
          @token = args[:token] if args.key?(:token)
        end
      end
      
      # This is a copy of the tech.blob.ObjectId proto, which could not be used
      # directly here due to transitive closure issues with JavaScript support; see
      # http://b/8801763.
      class GdataObjectId
        include Google::Apis::Core::Hashable
      
        # The name of the bucket to which this object belongs.
        # Corresponds to the JSON property `bucketName`
        # @return [String]
        attr_accessor :bucket_name
      
        # Generation of the object. Generations are monotonically increasing across
        # writes, allowing them to be be compared to determine which generation is newer.
        # If this is omitted in a request, then you are requesting the live object. See
        # http://go/bigstore-versions
        # Corresponds to the JSON property `generation`
        # @return [Fixnum]
        attr_accessor :generation
      
        # The name of the object.
        # Corresponds to the JSON property `objectName`
        # @return [String]
        attr_accessor :object_name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @bucket_name = args[:bucket_name] if args.key?(:bucket_name)
          @generation = args[:generation] if args.key?(:generation)
          @object_name = args[:object_name] if args.key?(:object_name)
        end
      end
      
      # Android App Bundle (AAB) information for a Firebase app.
      class GoogleFirebaseAppdistroV1AabInfo
        include Google::Apis::Core::Hashable
      
        # App bundle integration state. Only valid for android apps.
        # Corresponds to the JSON property `integrationState`
        # @return [String]
        attr_accessor :integration_state
      
        # The name of the `AabInfo` resource. Format: `projects/`project_number`/apps/`
        # app`/aabInfo`
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        # App bundle test certificate
        # Corresponds to the JSON property `testCertificate`
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1TestCertificate]
        attr_accessor :test_certificate
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @integration_state = args[:integration_state] if args.key?(:integration_state)
          @name = args[:name] if args.key?(:name)
          @test_certificate = args[:test_certificate] if args.key?(:test_certificate)
        end
      end
      
      # The Request message for batch adding testers
      class GoogleFirebaseAppdistroV1BatchAddTestersRequest
        include Google::Apis::Core::Hashable
      
        # Required. The email addresses of the tester resources to create. A maximum of
        # 999 and a minimum of 1 tester can be created in a batch.
        # Corresponds to the JSON property `emails`
        # @return [Array<String>]
        attr_accessor :emails
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @emails = args[:emails] if args.key?(:emails)
        end
      end
      
      # The Response message for `BatchAddTesters`.
      class GoogleFirebaseAppdistroV1BatchAddTestersResponse
        include Google::Apis::Core::Hashable
      
        # The testers which are created and/or already exist
        # Corresponds to the JSON property `testers`
        # @return [Array<Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Tester>]
        attr_accessor :testers
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @testers = args[:testers] if args.key?(:testers)
        end
      end
      
      # The request message for `BatchDeleteReleases`.
      class GoogleFirebaseAppdistroV1BatchDeleteReleasesRequest
        include Google::Apis::Core::Hashable
      
        # Required. The names of the release resources to delete. Format: `projects/`
        # project_number`/apps/`app_id`/releases/`release_id`` A maximum of 100 releases
        # can be deleted per request.
        # Corresponds to the JSON property `names`
        # @return [Array<String>]
        attr_accessor :names
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @names = args[:names] if args.key?(:names)
        end
      end
      
      # The request message for `BatchJoinGroup`
      class GoogleFirebaseAppdistroV1BatchJoinGroupRequest
        include Google::Apis::Core::Hashable
      
        # Indicates whether to create tester resources based on `emails` if they don't
        # exist yet.
        # Corresponds to the JSON property `createMissingTesters`
        # @return [Boolean]
        attr_accessor :create_missing_testers
        alias_method :create_missing_testers?, :create_missing_testers
      
        # Required. The emails of the testers to be added to the group. A maximum of 999
        # and a minimum of 1 tester can be created in a batch.
        # Corresponds to the JSON property `emails`
        # @return [Array<String>]
        attr_accessor :emails
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @create_missing_testers = args[:create_missing_testers] if args.key?(:create_missing_testers)
          @emails = args[:emails] if args.key?(:emails)
        end
      end
      
      # Request message for `BatchLeaveGroup`
      class GoogleFirebaseAppdistroV1BatchLeaveGroupRequest
        include Google::Apis::Core::Hashable
      
        # Required. The email addresses of the testers to be removed from the group. A
        # maximum of 999 and a minimum of 1 testers can be removed in a batch.
        # Corresponds to the JSON property `emails`
        # @return [Array<String>]
        attr_accessor :emails
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @emails = args[:emails] if args.key?(:emails)
        end
      end
      
      # The request message for `BatchRemoveTesters`.
      class GoogleFirebaseAppdistroV1BatchRemoveTestersRequest
        include Google::Apis::Core::Hashable
      
        # Required. The email addresses of the tester resources to removed. A maximum of
        # 999 and a minimum of 1 testers can be deleted in a batch.
        # Corresponds to the JSON property `emails`
        # @return [Array<String>]
        attr_accessor :emails
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @emails = args[:emails] if args.key?(:emails)
        end
      end
      
      # The response message for `BatchRemoveTesters`
      class GoogleFirebaseAppdistroV1BatchRemoveTestersResponse
        include Google::Apis::Core::Hashable
      
        # List of deleted tester emails
        # Corresponds to the JSON property `emails`
        # @return [Array<String>]
        attr_accessor :emails
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @emails = args[:emails] if args.key?(:emails)
        end
      end
      
      # The request message for `DistributeRelease`.
      class GoogleFirebaseAppdistroV1DistributeReleaseRequest
        include Google::Apis::Core::Hashable
      
        # A list of group aliases (IDs) to be given access to this release. A combined
        # maximum of 999 `testerEmails` and `groupAliases` can be specified in a single
        # request.
        # Corresponds to the JSON property `groupAliases`
        # @return [Array<String>]
        attr_accessor :group_aliases
      
        # A list of tester email addresses to be given access to this release. A
        # combined maximum of 999 `testerEmails` and `groupAliases` can be specified in
        # a single request.
        # Corresponds to the JSON property `testerEmails`
        # @return [Array<String>]
        attr_accessor :tester_emails
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @group_aliases = args[:group_aliases] if args.key?(:group_aliases)
          @tester_emails = args[:tester_emails] if args.key?(:tester_emails)
        end
      end
      
      # The response message for `DistributeRelease`.
      class GoogleFirebaseAppdistroV1DistributeReleaseResponse
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # A feedback report submitted by a tester for a release.
      class GoogleFirebaseAppdistroV1FeedbackReport
        include Google::Apis::Core::Hashable
      
        # Output only. The time when the feedback report was created.
        # Corresponds to the JSON property `createTime`
        # @return [String]
        attr_accessor :create_time
      
        # Output only. A link to the Firebase console displaying the feedback report.
        # Corresponds to the JSON property `firebaseConsoleUri`
        # @return [String]
        attr_accessor :firebase_console_uri
      
        # The name of the feedback report resource. Format: `projects/`project_number`/
        # apps/`app`/releases/`release`/feedbackReports/`feedback_report``
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        # Output only. A signed link (which expires in one hour) that lets you directly
        # download the screenshot.
        # Corresponds to the JSON property `screenshotUri`
        # @return [String]
        attr_accessor :screenshot_uri
      
        # Output only. The resource name of the tester who submitted the feedback report.
        # Corresponds to the JSON property `tester`
        # @return [String]
        attr_accessor :tester
      
        # Output only. The text of the feedback report.
        # Corresponds to the JSON property `text`
        # @return [String]
        attr_accessor :text
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @create_time = args[:create_time] if args.key?(:create_time)
          @firebase_console_uri = args[:firebase_console_uri] if args.key?(:firebase_console_uri)
          @name = args[:name] if args.key?(:name)
          @screenshot_uri = args[:screenshot_uri] if args.key?(:screenshot_uri)
          @tester = args[:tester] if args.key?(:tester)
          @text = args[:text] if args.key?(:text)
        end
      end
      
      # A group which can contain testers. A group can be invited to test apps in a
      # Firebase project.
      class GoogleFirebaseAppdistroV1Group
        include Google::Apis::Core::Hashable
      
        # Required. The display name of the group.
        # Corresponds to the JSON property `displayName`
        # @return [String]
        attr_accessor :display_name
      
        # Output only. The number of invite links for this group.
        # Corresponds to the JSON property `inviteLinkCount`
        # @return [Fixnum]
        attr_accessor :invite_link_count
      
        # The name of the group resource. Format: `projects/`project_number`/groups/`
        # group_alias``
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        # Output only. The number of releases this group is permitted to access.
        # Corresponds to the JSON property `releaseCount`
        # @return [Fixnum]
        attr_accessor :release_count
      
        # Output only. The number of testers who are members of this group.
        # Corresponds to the JSON property `testerCount`
        # @return [Fixnum]
        attr_accessor :tester_count
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @display_name = args[:display_name] if args.key?(:display_name)
          @invite_link_count = args[:invite_link_count] if args.key?(:invite_link_count)
          @name = args[:name] if args.key?(:name)
          @release_count = args[:release_count] if args.key?(:release_count)
          @tester_count = args[:tester_count] if args.key?(:tester_count)
        end
      end
      
      # The response message for `ListFeedbackReports`.
      class GoogleFirebaseAppdistroV1ListFeedbackReportsResponse
        include Google::Apis::Core::Hashable
      
        # The feedback reports
        # Corresponds to the JSON property `feedbackReports`
        # @return [Array<Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1FeedbackReport>]
        attr_accessor :feedback_reports
      
        # A short-lived token, which can be sent as `pageToken` to retrieve the next
        # page. If this field is omitted, there are no subsequent pages.
        # Corresponds to the JSON property `nextPageToken`
        # @return [String]
        attr_accessor :next_page_token
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @feedback_reports = args[:feedback_reports] if args.key?(:feedback_reports)
          @next_page_token = args[:next_page_token] if args.key?(:next_page_token)
        end
      end
      
      # The response message for `ListGroups`.
      class GoogleFirebaseAppdistroV1ListGroupsResponse
        include Google::Apis::Core::Hashable
      
        # The groups listed.
        # Corresponds to the JSON property `groups`
        # @return [Array<Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Group>]
        attr_accessor :groups
      
        # A short-lived token, which can be sent as `pageToken` to retrieve the next
        # page. If this field is omitted, there are no subsequent pages.
        # Corresponds to the JSON property `nextPageToken`
        # @return [String]
        attr_accessor :next_page_token
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @groups = args[:groups] if args.key?(:groups)
          @next_page_token = args[:next_page_token] if args.key?(:next_page_token)
        end
      end
      
      # The response message for `ListReleases`.
      class GoogleFirebaseAppdistroV1ListReleasesResponse
        include Google::Apis::Core::Hashable
      
        # A short-lived token, which can be sent as `pageToken` to retrieve the next
        # page. If this field is omitted, there are no subsequent pages.
        # Corresponds to the JSON property `nextPageToken`
        # @return [String]
        attr_accessor :next_page_token
      
        # The releases
        # Corresponds to the JSON property `releases`
        # @return [Array<Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release>]
        attr_accessor :releases
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @next_page_token = args[:next_page_token] if args.key?(:next_page_token)
          @releases = args[:releases] if args.key?(:releases)
        end
      end
      
      # The response message for `ListTesters`.
      class GoogleFirebaseAppdistroV1ListTestersResponse
        include Google::Apis::Core::Hashable
      
        # A short-lived token, which can be sent as `pageToken` to retrieve the next
        # page. If this field is omitted, there are no subsequent pages.
        # Corresponds to the JSON property `nextPageToken`
        # @return [String]
        attr_accessor :next_page_token
      
        # The testers listed.
        # Corresponds to the JSON property `testers`
        # @return [Array<Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Tester>]
        attr_accessor :testers
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @next_page_token = args[:next_page_token] if args.key?(:next_page_token)
          @testers = args[:testers] if args.key?(:testers)
        end
      end
      
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
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1ReleaseNotes]
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
      
      # App bundle test certificate
      class GoogleFirebaseAppdistroV1TestCertificate
        include Google::Apis::Core::Hashable
      
        # Hex string of MD5 hash of the test certificate used to resign the AAB
        # Corresponds to the JSON property `hashMd5`
        # @return [String]
        attr_accessor :hash_md5
      
        # Hex string of SHA1 hash of the test certificate used to resign the AAB
        # Corresponds to the JSON property `hashSha1`
        # @return [String]
        attr_accessor :hash_sha1
      
        # Hex string of SHA256 hash of the test certificate used to resign the AAB
        # Corresponds to the JSON property `hashSha256`
        # @return [String]
        attr_accessor :hash_sha256
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @hash_md5 = args[:hash_md5] if args.key?(:hash_md5)
          @hash_sha1 = args[:hash_sha1] if args.key?(:hash_sha1)
          @hash_sha256 = args[:hash_sha256] if args.key?(:hash_sha256)
        end
      end
      
      # A person that can be invited to test apps in a Firebase project.
      class GoogleFirebaseAppdistroV1Tester
        include Google::Apis::Core::Hashable
      
        # The name of the tester associated with the Google account used to accept the
        # tester invitation.
        # Corresponds to the JSON property `displayName`
        # @return [String]
        attr_accessor :display_name
      
        # The resource names of the groups this tester belongs to.
        # Corresponds to the JSON property `groups`
        # @return [Array<String>]
        attr_accessor :groups
      
        # Output only. The time the tester was last active. This is the most recent time
        # the tester installed one of the apps. If they've never installed one or if the
        # release no longer exists, this is the time the tester was added to the project.
        # Corresponds to the JSON property `lastActivityTime`
        # @return [String]
        attr_accessor :last_activity_time
      
        # The name of the tester resource. Format: `projects/`project_number`/testers/`
        # email_address``
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @display_name = args[:display_name] if args.key?(:display_name)
          @groups = args[:groups] if args.key?(:groups)
          @last_activity_time = args[:last_activity_time] if args.key?(:last_activity_time)
          @name = args[:name] if args.key?(:name)
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
      
      # Request message for `UploadRelease`.
      class GoogleFirebaseAppdistroV1UploadReleaseRequest
        include Google::Apis::Core::Hashable
      
        # A reference to data stored on the filesystem, on GFS or in blobstore.
        # Corresponds to the JSON property `blob`
        # @return [Google::Apis::FirebaseappdistributionV1::GdataMedia]
        attr_accessor :blob
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @blob = args[:blob] if args.key?(:blob)
        end
      end
      
      # Response message for `UploadRelease`.
      class GoogleFirebaseAppdistroV1UploadReleaseResponse
        include Google::Apis::Core::Hashable
      
        # A release of a Firebase app.
        # Corresponds to the JSON property `release`
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1Release]
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
      
      # The request message for Operations.CancelOperation.
      class GoogleLongrunningCancelOperationRequest
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # The response message for Operations.ListOperations.
      class GoogleLongrunningListOperationsResponse
        include Google::Apis::Core::Hashable
      
        # The standard List next-page token.
        # Corresponds to the JSON property `nextPageToken`
        # @return [String]
        attr_accessor :next_page_token
      
        # A list of operations that matches the specified filter in the request.
        # Corresponds to the JSON property `operations`
        # @return [Array<Google::Apis::FirebaseappdistributionV1::GoogleLongrunningOperation>]
        attr_accessor :operations
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @next_page_token = args[:next_page_token] if args.key?(:next_page_token)
          @operations = args[:operations] if args.key?(:operations)
        end
      end
      
      # This resource represents a long-running operation that is the result of a
      # network API call.
      class GoogleLongrunningOperation
        include Google::Apis::Core::Hashable
      
        # If the value is `false`, it means the operation is still in progress. If `true`
        # , the operation is completed, and either `error` or `response` is available.
        # Corresponds to the JSON property `done`
        # @return [Boolean]
        attr_accessor :done
        alias_method :done?, :done
      
        # The `Status` type defines a logical error model that is suitable for different
        # programming environments, including REST APIs and RPC APIs. It is used by [
        # gRPC](https://github.com/grpc). Each `Status` message contains three pieces of
        # data: error code, error message, and error details. You can find out more
        # about this error model and how to work with it in the [API Design Guide](https:
        # //cloud.google.com/apis/design/errors).
        # Corresponds to the JSON property `error`
        # @return [Google::Apis::FirebaseappdistributionV1::GoogleRpcStatus]
        attr_accessor :error
      
        # Service-specific metadata associated with the operation. It typically contains
        # progress information and common metadata such as create time. Some services
        # might not provide such metadata. Any method that returns a long-running
        # operation should document the metadata type, if any.
        # Corresponds to the JSON property `metadata`
        # @return [Hash<String,Object>]
        attr_accessor :metadata
      
        # The server-assigned name, which is only unique within the same service that
        # originally returns it. If you use the default HTTP mapping, the `name` should
        # be a resource name ending with `operations/`unique_id``.
        # Corresponds to the JSON property `name`
        # @return [String]
        attr_accessor :name
      
        # The normal response of the operation in case of success. If the original
        # method returns no data on success, such as `Delete`, the response is `google.
        # protobuf.Empty`. If the original method is standard `Get`/`Create`/`Update`,
        # the response should be the resource. For other methods, the response should
        # have the type `XxxResponse`, where `Xxx` is the original method name. For
        # example, if the original method name is `TakeSnapshot()`, the inferred
        # response type is `TakeSnapshotResponse`.
        # Corresponds to the JSON property `response`
        # @return [Hash<String,Object>]
        attr_accessor :response
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @done = args[:done] if args.key?(:done)
          @error = args[:error] if args.key?(:error)
          @metadata = args[:metadata] if args.key?(:metadata)
          @name = args[:name] if args.key?(:name)
          @response = args[:response] if args.key?(:response)
        end
      end
      
      # The request message for Operations.WaitOperation.
      class GoogleLongrunningWaitOperationRequest
        include Google::Apis::Core::Hashable
      
        # The maximum duration to wait before timing out. If left blank, the wait will
        # be at most the time permitted by the underlying HTTP/RPC protocol. If RPC
        # context deadline is also specified, the shorter one will be used.
        # Corresponds to the JSON property `timeout`
        # @return [String]
        attr_accessor :timeout
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @timeout = args[:timeout] if args.key?(:timeout)
        end
      end
      
      # A generic empty message that you can re-use to avoid defining duplicated empty
      # messages in your APIs. A typical example is to use it as the request or the
      # response type of an API method. For instance: service Foo ` rpc Bar(google.
      # protobuf.Empty) returns (google.protobuf.Empty); `
      class GoogleProtobufEmpty
        include Google::Apis::Core::Hashable
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
        end
      end
      
      # The `Status` type defines a logical error model that is suitable for different
      # programming environments, including REST APIs and RPC APIs. It is used by [
      # gRPC](https://github.com/grpc). Each `Status` message contains three pieces of
      # data: error code, error message, and error details. You can find out more
      # about this error model and how to work with it in the [API Design Guide](https:
      # //cloud.google.com/apis/design/errors).
      class GoogleRpcStatus
        include Google::Apis::Core::Hashable
      
        # The status code, which should be an enum value of google.rpc.Code.
        # Corresponds to the JSON property `code`
        # @return [Fixnum]
        attr_accessor :code
      
        # A list of messages that carry the error details. There is a common set of
        # message types for APIs to use.
        # Corresponds to the JSON property `details`
        # @return [Array<Hash<String,Object>>]
        attr_accessor :details
      
        # A developer-facing error message, which should be in English. Any user-facing
        # error message should be localized and sent in the google.rpc.Status.details
        # field, or localized by the client.
        # Corresponds to the JSON property `message`
        # @return [String]
        attr_accessor :message
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @code = args[:code] if args.key?(:code)
          @details = args[:details] if args.key?(:details)
          @message = args[:message] if args.key?(:message)
        end
      end
    end
  end
end
