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

require 'google/apis/androidpublisher_v3/service.rb'
require 'google/apis/androidpublisher_v3/classes.rb'
require 'google/apis/androidpublisher_v3/representations.rb'
require 'google/apis/androidpublisher_v3/gem_version.rb'

module Google
  module Apis
    # Google Play Android Developer API
    #
    # Lets Android application developers access their Google Play accounts. At a
    # high level, the expected workflow is to "insert" an Edit, make changes as
    # necessary, and then "commit" it.
    #
    # @see https://developers.google.com/android-publisher
    module AndroidpublisherV3
      # Version of the Google Play Android Developer API this client connects to.
      # This is NOT the gem version.
      VERSION = 'V3'

      # View and manage your Google Play Developer account
      AUTH_ANDROIDPUBLISHER = 'https://www.googleapis.com/auth/androidpublisher'
    end
  end
end
