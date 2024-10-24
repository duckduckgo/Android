require 'shellwords'

module Security
  class Keychain
    DOMAINS = [:user, :system, :common, :dynamic]

    attr_reader :filename

    def initialize(filename)
      @filename = filename
    end

    def info
      system %{security show-keychain-info #{@filename.shellescape}}
    end

    def lock
      system %{security lock-keychain #{@filename.shellescape}}
    end

    def unlock(password)
      system %{security unlock-keychain -p #{password.shellescape} #{@filename.shellescape}}
    end

    def delete
      system %{security delete-keychain #{@filename.shellescape}}
    end

    class << self
      def create(filename, password)
        raise NotImplementedError
      end

      def list(domain = :user)
        raise ArgumentError "Invalid domain #{domain}, expected one of: #{DOMAINS}" unless DOMAINS.include?(domain)

        keychains_from_output(`security list-keychains -d #{domain}`)
      end

      def lock
        system %{security lock-keychain -a}
      end

      def unlock(password)
        system %{security unlock-keychain -p #{password.shellescape}}
      end

      def default_keychain
        keychains_from_output(`security default-keychain`).first
      end

      def login_keychain
        keychains_from_output(`security login-keychain`).first
      end

      private

      def keychains_from_output(output)
        output.split(/\n/).collect{|line| new(line.strip.gsub(/^\"|\"$/, ""))}
      end
    end
  end
end
