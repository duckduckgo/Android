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
    module PlaycustomappV1
      
      # This resource represents a custom app.
      class CustomApp
        include Google::Apis::Core::Hashable
      
        # Default listing language in BCP 47 format.
        # Corresponds to the JSON property `languageCode`
        # @return [String]
        attr_accessor :language_code
      
        # Organizations to which the custom app should be made available. If the request
        # contains any organizations, then the app will be restricted to only these
        # organizations. To support the organization linked to the developer account,
        # the organization ID should be provided explicitly together with other
        # organizations. If no organizations are provided, then the app is only
        # available to the organization linked to the developer account.
        # Corresponds to the JSON property `organizations`
        # @return [Array<Google::Apis::PlaycustomappV1::Organization>]
        attr_accessor :organizations
      
        # Output only. Package name of the created Android app. Only present in the API
        # response.
        # Corresponds to the JSON property `packageName`
        # @return [String]
        attr_accessor :package_name
      
        # Title for the Android app.
        # Corresponds to the JSON property `title`
        # @return [String]
        attr_accessor :title
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @language_code = args[:language_code] if args.key?(:language_code)
          @organizations = args[:organizations] if args.key?(:organizations)
          @package_name = args[:package_name] if args.key?(:package_name)
          @title = args[:title] if args.key?(:title)
        end
      end
      
      # Represents an organization that can access a custom app.
      class Organization
        include Google::Apis::Core::Hashable
      
        # Required. ID of the organization.
        # Corresponds to the JSON property `organizationId`
        # @return [String]
        attr_accessor :organization_id
      
        # Optional. A human-readable name of the organization, to help recognize the
        # organization.
        # Corresponds to the JSON property `organizationName`
        # @return [String]
        attr_accessor :organization_name
      
        def initialize(**args)
           update!(**args)
        end
      
        # Update properties of this object
        def update!(**args)
          @organization_id = args[:organization_id] if args.key?(:organization_id)
          @organization_name = args[:organization_name] if args.key?(:organization_name)
        end
      end
    end
  end
end
