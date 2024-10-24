require 'fastlane/action'
require 'fastlane_core/ui/ui'
require 'google/apis/firebaseappdistribution_v1'
require_relative '../helper/firebase_app_distribution_helper'
require_relative '../helper/firebase_app_distribution_auth_client'

module Fastlane
  module Actions
    class FirebaseAppDistributionRemoveTestersAction < Action
      extend Auth::FirebaseAppDistributionAuthClient
      extend Helper::FirebaseAppDistributionHelper

      def self.run(params)
        init_google_api_client(params[:debug])
        client = Google::Apis::FirebaseappdistributionV1::FirebaseAppDistributionService.new
        client.authorization = get_authorization(params[:service_credentials_file], params[:firebase_cli_token], params[:service_credentials_json_data], params[:debug])

        if blank?(params[:emails]) && blank?(params[:file])
          UI.user_error!("Must specify `emails` or `file`.")
        end

        emails = string_to_array(get_value_from_value_or_file(params[:emails], params[:file]))
        project_number = params[:project_number]
        group_alias = params[:group_alias]

        UI.user_error!("Must pass at least one email") if blank?(emails)

        if emails.count > 1000
          UI.user_error!("A maximum of 1000 testers can be removed at a time.")
        end

        if present?(group_alias)
          remove_testers_from_group(client, project_number, group_alias, emails)
        else
          remove_testers_from_project(client, project_number, emails)
        end
      end

      def self.description
        "Delete testers in bulk from a comma-separated list or a file"
      end

      def self.authors
        ["Tunde Agboola"]
      end

      # supports markdown.
      def self.details
        "Delete testers in bulk from a comma-separated list or a file"
      end

      def self.available_options
        [
          FastlaneCore::ConfigItem.new(key: :project_number,
                                      env_name: "FIREBASEAPPDISTRO_PROJECT_NUMBER",
                                      description: "Your Firebase project number. You can find the project number in the Firebase console, on the General Settings page",
                                      type: Integer,
                                       optional: false),
          FastlaneCore::ConfigItem.new(key: :emails,
                                      env_name: "FIREBASEAPPDISTRO_REMOVE_TESTERS_EMAILS",
                                      description: "Comma separated list of tester emails to be deleted (or removed from a group if a group alias is specified). A maximum of 1000 testers can be deleted/removed at a time",
                                      optional: true,
                                      type: String),
          FastlaneCore::ConfigItem.new(key: :file,
                                      env_name: "FIREBASEAPPDISTRO_REMOVE_TESTERS_FILE",
                                      description: "Path to a file containing a comma separated list of tester emails to be deleted (or removed from a group if a group alias is specified). A maximum of 1000 testers can be deleted/removed at a time",
                                      optional: true,
                                      type: String),
          FastlaneCore::ConfigItem.new(key: :group_alias,
                                       env_name: "FIREBASEAPPDISTRO_REMOVE_TESTERS_GROUP_ALIAS",
                                       description: "Alias of the group to remove the specified testers from. Testers will not be deleted from the project",
                                       optional: true,
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

      def self.remove_testers_from_project(client, project_number, emails)
        UI.message("⏳ Removing #{emails.count} testers from project #{project_number}...")
        request = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchRemoveTestersRequest.new(
          emails: emails
        )

        begin
          response = client.batch_project_tester_remove(project_name(project_number), request)
        rescue Google::Apis::Error => err
          case err.status_code.to_i
          when 404
            UI.user_error!(ErrorMessage::INVALID_PROJECT)
          else
            UI.crash!(err)
          end
        end

        UI.success("✅ #{response.emails.count} tester(s) removed successfully.")
      end

      def self.remove_testers_from_group(client, project_number, group_alias, emails)
        UI.message("⏳ Removing #{emails.count} testers from group #group_alias}...")
        request = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchLeaveGroupRequest.new(
          emails: emails
        )

        begin
          client.batch_project_group_leave(group_name(project_number, group_alias), request)
        rescue Google::Apis::Error => err
          case err.status_code.to_i
          when 400
            UI.user_error!(ErrorMessage::INVALID_EMAIL_ADDRESS)
          when 404
            UI.user_error!(ErrorMessage::INVALID_TESTER_GROUP)
          else
            UI.crash!(err)
          end
        end

        UI.success("✅ Tester(s) removed successfully.")
      end
    end
  end
end
