require 'fastlane/action'
require 'fastlane_core/ui/ui'
require 'google/apis/firebaseappdistribution_v1'
require_relative '../helper/firebase_app_distribution_helper'
require_relative '../helper/firebase_app_distribution_auth_client'

module Fastlane
  module Actions
    class FirebaseAppDistributionDeleteGroupAction < Action
      extend Auth::FirebaseAppDistributionAuthClient
      extend Helper::FirebaseAppDistributionHelper

      def self.run(params)
        init_google_api_client(params[:debug])
        client = Google::Apis::FirebaseappdistributionV1::FirebaseAppDistributionService.new
        client.authorization = get_authorization(params[:service_credentials_file], params[:firebase_cli_token], params[:service_credentials_json_data], params[:debug])

        if blank?(params[:alias])
          UI.user_error!("Must specify `alias`.")
        end

        project_number = params[:project_number]
        group_alias = params[:alias]

        UI.message("⏳ Deleting tester group '#{group_alias}' in project #{project_number}...")

        begin
          client.delete_project_group(group_name(project_number, group_alias))
        rescue Google::Apis::Error => err
          case err.status_code.to_i
          when 404
            UI.user_error!(ErrorMessage::INVALID_TESTER_GROUP)
          else
            UI.crash!(err)
          end
        end

        UI.success("✅ Group deleted successfully.")
      end

      def self.description
        "Delete a tester group"
      end

      def self.authors
        ["Garry Jeromson"]
      end

      # supports markdown.
      def self.details
        "Delete a tester group"
      end

      def self.available_options
        [
          FastlaneCore::ConfigItem.new(key: :project_number,
                                       env_name: "FIREBASEAPPDISTRO_PROJECT_NUMBER",
                                       description: "Your Firebase project number. You can find the project number in the Firebase console, on the General Settings page",
                                       type: Integer,
                                       optional: false),
          FastlaneCore::ConfigItem.new(key: :alias,
                                       env_name: "FIREBASEAPPDISTRO_DELETE_GROUP_ALIAS",
                                       description: "Alias of the group to be deleted",
                                       optional: false,
                                       type: String),
          FastlaneCore::ConfigItem.new(key: :service_credentials_file,
                                       description: "Path to Google service credentials file",
                                       optional: true,
                                       type: String),
          FastlaneCore::ConfigItem.new(key: :service_credentials_json_data,
                                       description: "Google service account json file content",
                                       optional: true,
                                       type: String),
          FastlaneCore::ConfigItem.new(key: :firebase_cli_token,
                                       description: "Auth token generated using the Firebase CLI's login:ci command",
                                       optional: true,
                                       type: String),
          FastlaneCore::ConfigItem.new(key: :debug,
                                       description: "Print verbose debug output",
                                       optional: true,
                                       default_value: false,
                                       is_string: false)
        ]
      end

      def self.is_supported?(platform)
        true
      end
    end
  end
end
