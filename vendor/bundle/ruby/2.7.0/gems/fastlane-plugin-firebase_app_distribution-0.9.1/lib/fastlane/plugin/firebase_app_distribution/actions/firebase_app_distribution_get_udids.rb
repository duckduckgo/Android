require 'fastlane/action'
require 'open3'
require 'shellwords'
require 'googleauth'
require 'google/apis/firebaseappdistribution_v1alpha'
require_relative '../helper/firebase_app_distribution_helper'
require_relative '../helper/firebase_app_distribution_error_message'
require_relative '../helper/firebase_app_distribution_auth_client'

module Fastlane
  module Actions
    class FirebaseAppDistributionGetUdidsAction < Action
      extend Auth::FirebaseAppDistributionAuthClient
      extend Helper::FirebaseAppDistributionHelper

      def self.run(params)
        init_google_api_client(params[:debug])
        client = Google::Apis::FirebaseappdistributionV1alpha::FirebaseAppDistributionService.new
        client.authorization = get_authorization(params[:service_credentials_file], params[:firebase_cli_token], params[:service_credentials_json_data], params[:debug])

        project_number = params[:project_number]
        if blank?(project_number)
          app_id = params[:app]
          if blank?(app_id)
            UI.user_error!("Must specify `project_number`.")
          end
          project_number = project_number_from_app_id(app_id)
        end
        udids = client.get_project_tester_udids(project_name(project_number)).tester_udids

        if udids.to_a.empty?
          File.delete(params[:output_file]) if File.exist?(params[:output_file])
          UI.important("App Distribution fetched 0 tester UDIDs. Removed output file.")
        else
          write_udids_to_file(udids, params[:output_file])
          UI.success("🎉 App Distribution tester UDIDs written to: #{params[:output_file]}")
        end
      end

      def self.write_udids_to_file(udids, output_file)
        File.open(output_file, 'w') do |f|
          f.write("Device ID\tDevice Name\tDevice Platform\n")
          udids.each do |tester_udid|
            f.write("#{tester_udid.udid}\t#{tester_udid.name}\t#{tester_udid.platform}\n")
          end
        end
      end

      def self.description
        "Download the UDIDs of your Firebase App Distribution testers"
      end

      def self.authors
        ["Lee Kellogg"]
      end

      # supports markdown.
      def self.details
        "Export your testers' device identifiers in a CSV file, so you can add them your provisioning profile. This file can be imported into your Apple developer account using the Register Multiple Devices option. See the [App Distribution docs](https://firebase.google.com/docs/app-distribution/ios/distribute-console#register-tester-devices) for more info."
      end

      def self.available_options
        [
          FastlaneCore::ConfigItem.new(key: :project_number,
                                       conflicting_options: [:app],
                                       env_name: "FIREBASEAPPDISTRO_PROJECT_NUMBER",
                                       description: "Your Firebase project number. You can find the project number in the Firebase console, on the General Settings page",
                                       type: Integer,
                                       optional: true),
          FastlaneCore::ConfigItem.new(key: :app,
                                       conflicting_options: [:project_number],
                                       env_name: "FIREBASEAPPDISTRO_APP",
                                       description: "Your app's Firebase App ID. You can find the App ID in the Firebase console, on the General Settings page",
                                       optional: true,
                                       deprecated: "Use project_number (FIREBASEAPPDISTRO_PROJECT_NUMBER) instead",
                                       type: String),
          FastlaneCore::ConfigItem.new(key: :output_file,
                                       env_name: "FIREBASEAPPDISTRO_OUTPUT_FILE",
                                       description: "The path to the file where the tester UDIDs will be written",
                                       optional: false,
                                       type: String),
          FastlaneCore::ConfigItem.new(key: :firebase_cli_token,
                                       description: "Auth token generated using the Firebase CLI's login:ci command",
                                       optional: true,
                                       type: String),
          FastlaneCore::ConfigItem.new(key: :service_credentials_file,
                                       description: "Path to Google service account json",
                                       optional: true,
                                       type: String),
          FastlaneCore::ConfigItem.new(key: :service_credentials_json_data,
                                       description: "Google service account json file content",
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
        [:ios].include?(platform)
      end

      def self.example_code
        [
          <<-CODE
            firebase_app_distribution_get_udids(
              app: "<your Firebase app ID>",
              output_file: "tester_udids.txt",
            )
          CODE
        ]
      end
    end
  end
end
