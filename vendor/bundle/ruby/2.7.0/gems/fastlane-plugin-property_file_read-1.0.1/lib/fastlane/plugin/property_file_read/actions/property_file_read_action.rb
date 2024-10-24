module Fastlane
  module Actions
    class PropertyFileReadAction < Action
      def self.run(params)
        properties = {}
        IO.foreach(params[:file]) do |line|
          properties[$1.strip] = $2 if line =~ %r{([^=]*)=(.*)\/\/(.*)} || line =~ /([^=]*)=(.*)/
        end
        properties
      end

      def self.description
        "Reads property file into dictionary"
      end

      def self.authors
        ["Peter Turza"]
      end

      def self.return_value
        # If your method provides a return value, you can describe here what it does
      end

      def self.details
        # Optional:
        "Reads property file into dictionary. Used mostly in Android development as configuration files for gradle builds."
      end

      def self.available_options
        [
          FastlaneCore::ConfigItem.new(key: :file,
                                  env_name: "PROPERTY_FILE_READ_FILE",
                               description: "Property file to read",
                                  optional: false,
                                      type: String)
        ]
      end

      def self.is_supported?(platform)
        # Adjust this if your plugin only works for a particular platform (iOS vs. Android, for example)
        # See: https://github.com/fastlane/fastlane/blob/master/fastlane/docs/Platforms.md
        #
        # [:ios, :mac, :android].include?(platform)
        true
      end
    end
  end
end
