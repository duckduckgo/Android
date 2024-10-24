module ErrorMessage
  MISSING_CREDENTIALS = "Missing authentication credentials. Set up Application Default Credentials, your Firebase refresh token, or sign in with the Firebase CLI, and try again."
  MISSING_APP_ID = "Missing app id. Please check that the app parameter is set and try again."
  SERVICE_CREDENTIALS_NOT_FOUND = "Service credentials file does not exist. Please check the service credentials path and try again."
  PARSE_SERVICE_CREDENTIALS_ERROR = "Failed to extract service account information from the service credentials file."
  PARSE_FIREBASE_TOOLS_JSON_ERROR = "Encountered error parsing json file. Ensure the firebase-tools.json file is formatted correctly."
  UPLOAD_RELEASE_NOTES_ERROR = "App Distribution halted because it had a problem uploading release notes."
  UPLOAD_TESTERS_ERROR = "App Distribution halted because it had a problem adding testers/groups."
  GET_RELEASE_TIMEOUT = "App Distribution failed to fetch release information."
  REFRESH_TOKEN_ERROR = "App Distribution could not generate credentials from the refresh token specified."
  APP_NOT_ONBOARDED_ERROR = "App Distribution not onboarded."
  INVALID_APP_ID = "App Distribution could not find your app. Make sure to onboard your app by pressing the \"Get started\" button on the App Distribution page in the Firebase console: https://console.firebase.google.com/project/_/appdistribution. App ID"
  INVALID_PROJECT = "App Distribution could not find your Firebase project. Make sure to onboard an app in your project by pressing the \"Get started\" button on the App Distribution page in the Firebase console: https://console.firebase.google.com/project/_/appdistribution."
  INVALID_PATH = "Could not read content from"
  INVALID_TESTERS = "Could not enable access for testers. Check that the tester emails are formatted correctly, the groups exist and you are using group aliases (not group names) for specifying groups."
  INVALID_TESTER_GROUP = "App Distribution could not find your tester group. Make sure that it exists before trying to add testers, and that the group alias is specified correctly."
  INVALID_TESTER_GROUP_NAME = "The tester group name should be 4-63 characters, and valid characters are /[a-z][0-9]-/."
  INVALID_RELEASE_NOTES = "Failed to set release notes."
  SERVICE_CREDENTIALS_ERROR = "App Distribution could not generate credentials from the service credentials file specified."
  PLAY_ACCOUNT_NOT_LINKED = "This project is not linked to a Google Play account."
  APP_NOT_PUBLISHED = "This app is not published in the Google Play console."
  NO_APP_WITH_GIVEN_BUNDLE_ID_IN_PLAY_ACCOUNT = "App with matching package name does not exist in Google Play."
  PLAY_IAS_TERMS_NOT_ACCEPTED = "You must accept the Play Internal App Sharing (IAS) terms to upload AABs."
  INVALID_EMAIL_ADDRESS = "You passed an invalid email address."
  TESTER_LIMIT_VIOLATION = "Creating testers would exceed tester limit."

  def self.aab_upload_error(aab_state)
    "Failed to process the AAB: #{aab_state}"
  end

  def self.binary_not_found(binary_type)
    "Could not find the #{binary_type}. Make sure you set the #{binary_type} path parameter to point to your #{binary_type}."
  end

  def self.parse_binary_metadata_error(binary_type)
    "Failed to extract #{binary_type} metadata from the #{binary_type} path."
  end

  def self.upload_binary_error(binary_type)
    "App Distribution halted because it had a problem uploading the #{binary_type}."
  end

  def self.binary_processing_error(binary_type)
    "App Distribution failed to process the #{binary_type}."
  end
end
