require 'fastlane/action'
require 'fastlane_core/ui/ui'
require 'google/apis/firebaseappdistribution_v1'
require_relative '../helper/firebase_app_distribution_helper'
require_relative '../helper/firebase_app_distribution_auth_client'

module Fastlane
  module Actions
    class FirebaseAppDistributionAddTestersAction < Action
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
          UI.user_error!("A maximum of 1000 testers can be added at a time.")
        end

        if present?(group_alias)
          add_testers_to_group(client, project_number, emails, group_alias)
        else
          add_testers_to_project(client, emails, project_number)
        end

        # The add_testers response lists all the testers from the request
        # regardless of whether or not they were created or if they already
        # exists so can't get an accurate count of the number of newly created testers
        UI.success("✅ Tester(s) successfully added.")
      end

      def self.description
        "Create testers in bulk from a comma-separated list or a file"
      end

      def self.authors
        ["Tunde Agboola"]
      end

      # supports markdown.
      def self.details
        "Create testers in bulk from a comma-separated list or a file"
      end

      def self.available_options
        [
          FastlaneCore::ConfigItem.new(key: :project_number,
                                      env_name: "FIREBASEAPPDISTRO_PROJECT_NUMBER",
                                      description: "Your Firebase project number. You can find the project number in the Firebase console, on the General Settings page",
                                      type: Integer,
                                       optional: false),
          FastlaneCore::ConfigItem.new(key: :emails,
                                      env_name: "FIREBASEAPPDISTRO_ADD_TESTERS_EMAILS",
                                      description: "Comma separated list of tester emails to be created. A maximum of 1000 testers can be created at a time",
                                      optional: true,
                                      type: String),
          FastlaneCore::ConfigItem.new(key: :file,
                                      env_name: "FIREBASEAPPDISTRO_ADD_TESTERS_FILE",
                                      description: "Path to a file containing a comma separated list of tester emails to be created. A maximum of 1000 testers can be deleted at a time",
                                      optional: true,
                                      type: String),
          FastlaneCore::ConfigItem.new(key: :group_alias,
                                       env_name: "FIREBASEAPPDISTRO_ADD_TESTERS_GROUP_ALIAS",
                                       description: "Alias of the group to add the specified testers to. The group must already exist. If not specified, testers will not be added to a group",
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

      def self.add_testers_to_project(client, emails, project_number)
        UI.message("⏳ Adding #{emails.count} testers to project #{project_number}...")
        request = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchAddTestersRequest.new(
          emails: emails
        )

        begin
          client.batch_project_tester_add(project_name(project_number), request)
        rescue Google::Apis::Error => err
          case err.status_code.to_i
          when 400
            UI.user_error!(ErrorMessage::INVALID_EMAIL_ADDRESS)
          when 404
            UI.user_error!(ErrorMessage::INVALID_PROJECT)
          when 429
            UI.user_error!(ErrorMessage::TESTER_LIMIT_VIOLATION)
          else
            UI.crash!(err)
          end
        end
      end

      def self.add_testers_to_group(client, project_number, emails, group_alias)
        UI.message("⏳ Adding testers to group #{group_alias}...")
        request = Google::Apis::FirebaseappdistributionV1::GoogleFirebaseAppdistroV1BatchJoinGroupRequest.new(
          emails: emails,
          create_missing_testers: true
        )

        begin
          client.batch_project_group_join(group_name(project_number, group_alias), request)
        rescue Google::Apis::Error => err
          case err.status_code.to_i
          when 400
            UI.user_error!(ErrorMessage::INVALID_EMAIL_ADDRESS)
          when 404
            UI.user_error!(ErrorMessage::INVALID_TESTER_GROUP)
          when 429
            UI.user_error!(ErrorMessage::TESTER_LIMIT_VIOLATION)
          else
            UI.crash!(err)
          end
        end
      end
    end
  end
end
