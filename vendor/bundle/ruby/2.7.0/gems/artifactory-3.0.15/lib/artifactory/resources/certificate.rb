module Artifactory
  class Resource::Certificate < Resource::Base
    class << self
      #
      # Get a list of all certificates in the system.
      #
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [Array<Resource::Certificate>]
      #   the list of builds
      #
      def all(options = {})
        client = extract_client!(options)
        client.get("/api/system/security/certificates").map do |cert|
          from_hash(cert, client: client)
        end.compact
      end

      #
      # @see Artifactory::Resource::Base.from_hash
      #
      def from_hash(hash, options = {})
        super.tap do |instance|
          instance.issued_on   = Time.parse(instance.issued_on)   rescue nil
          instance.valid_until = Time.parse(instance.valid_until) rescue nil
        end
      end
    end

    attribute :certificate_alias, -> { raise "Certificate alias missing!" }
    attribute :fingerprint
    attribute :issued_by
    attribute :issued_on
    attribute :issued_to
    attribute :local_path, -> { raise "Local destination missing!" }
    attribute :valid_until

    #
    # Delete this certificate from artifactory, suppressing any +ResourceNotFound+
    # exceptions might occur.
    #
    # @return [Boolean]
    #   true if the object was deleted successfully, false otherwise
    #
    def delete
      client.delete(api_path)
      true
    rescue Error::HTTPError
      false
    end

    #
    # Upload a certificate. If the first parameter is a File object, that file
    # descriptor is passed to the uploader. If the first parameter is a string,
    # it is assumed to be a path to a local file on disk. This method will
    # automatically construct the File object from the given path.
    #
    # @example Upload a certificate from a File instance
    #   certificate = Certificate.new(local_path: '/path/to/cert.pem', certificate_alias: 'test')
    #   certificate.upload
    #
    # @return [Resource::Certificate]
    #
    def upload
      file = File.new(File.expand_path(local_path))
      headers = { "Content-Type" => "application/text" }

      response = client.post(api_path, file, headers)

      return unless response.is_a?(Hash)

      self.class.all.select { |x| x.certificate_alias.eql?(certificate_alias) }.first
    end

    private

    #
    # The path to this certificate on the server.
    #
    # @return [String]
    #
    def api_path
      "/api/system/security/certificates/#{url_safe(certificate_alias)}"
    end
  end
end
