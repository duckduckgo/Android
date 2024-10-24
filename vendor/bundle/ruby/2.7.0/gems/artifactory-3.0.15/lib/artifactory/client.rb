#
# Copyright 2014-2018 Chef Software, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

require "cgi"
require "json"
require "net/http"
require "uri"

module Artifactory
  #
  # Client for the Artifactory API.
  #
  # @see http://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API
  #
  class Client
    class << self
      #
      # @private
      #
      def proxy(klass)
        namespace = klass.name.split("::").last.downcase
        klass.singleton_methods(false).each do |name|
          define_method("#{namespace}_#{name}") do |*args|
            if args.last.is_a?(Hash)
              args.last[:client] = self
            else
              args << { client: self }
            end

            klass.send(name, *args)
          end
        end
      end
    end

    include Artifactory::Configurable

    proxy Resource::Artifact
    proxy Resource::Backup
    proxy Resource::Certificate
    proxy Resource::Layout
    proxy Resource::LDAPSetting
    proxy Resource::MailServer
    proxy Resource::PermissionTarget
    proxy Resource::Repository
    proxy Resource::System
    proxy Resource::URLBase
    proxy Resource::User

    #
    # Create a new Artifactory Client with the given options. Any options
    # given take precedence over the default options.
    #
    # @return [Artifactory::Client]
    #
    def initialize(options = {})
      # Use any options given, but fall back to the defaults set on the module
      Artifactory::Configurable.keys.each do |key|
        value = if options[key].nil?
                  Artifactory.instance_variable_get(:"@#{key}")
                else
                  options[key]
                end

        instance_variable_set(:"@#{key}", value)
      end
    end

    #
    # Determine if the given options are the same as ours.
    #
    # @return [Boolean]
    #
    def same_options?(opts)
      opts.hash == options.hash
    end

    #
    # Make a HTTP GET request
    #
    # If a block is provided the response body is yielded in chunks/fragments
    # as it is read from the undelrying socket.
    #
    # @param path (see Client#request)
    # @param [Hash] params
    #   the list of query params
    # @param headers (see Client#request)
    #
    # @yield [chunk] Partial piece of response body
    #
    # @raise (see Client#request)
    # @return (see Client#request)
    #
    def get(path, params = {}, headers = {}, &block)
      request(:get, path, params, headers, &block)
    end

    #
    # Make a HTTP POST request
    #
    # @param path (see Client#request)
    # @param [String, #read] data
    #   the body to use for the request
    # @param headers (see Client#request)
    #
    # @raise (see Client#request)
    # @return (see Client#request)
    #
    def post(path, data, headers = {})
      request(:post, path, data, headers)
    end

    #
    # Make a HTTP PUT request
    #
    # @param path (see Client#request)
    # @param data (see Client#post)
    # @param headers (see Client#request)
    #
    # @raise (see Client#request)
    # @return (see Client#request)
    #
    def put(path, data, headers = {})
      request(:put, path, data, headers)
    end

    #
    # Make a HTTP PATCH request
    #
    # @param path (see Client#request)
    # @param data (see Client#post)
    # @param headers (see Client#request)
    #
    # @raise (see Client#request)
    # @return (see Client#request)
    #
    def patch(path, data, headers = {})
      request(:patch, path, data, headers)
    end

    #
    # Make a HTTP DELETE request
    #
    # @param path (see Client#request)
    # @param params (see Client#get)
    # @param headers (see Client#request)
    #
    # @raise (see Client#request)
    # @return (see Client#request)
    #
    def delete(path, params = {}, headers = {})
      request(:delete, path, params, headers)
    end

    #
    # Make an HTTP request with the given verb, data, params, and headers. If
    # the response has a return type of JSON, the JSON is automatically parsed
    # and returned as a hash; otherwise it is returned as a string. If a block
    # is provided the response body is yielded in chunks/fragments as it is
    # read from the undelrying socket.
    #
    # @raise [Error::HTTPError]
    #   if the request is not an HTTP 200 OK
    #
    # @param [Symbol] verb
    #   the lowercase symbol of the HTTP verb (e.g. :get, :delete)
    # @param [String] path
    #   the absolute or relative path from {Defaults.endpoint} to make the
    #   request against
    # @param [#read, Hash, nil] data
    #   the data to use (varies based on the +verb+)
    # @param [Hash] headers
    #   the list of headers to use
    #
    # @yield [chunk] Partial piece of response body
    #
    # @return [String, Hash]
    #   the response body
    #
    def request(verb, path, data = {}, headers = {}, &block)
      # Build the URI and request object from the given information
      uri = build_uri(verb, path, data)
      request = class_for_request(verb).new(uri.request_uri)

      # Add headers
      default_headers.merge(headers).each do |key, value|
        request.add_field(key, value)
      end

      # Add basic authentication
      if username && password
        request.basic_auth(username, password)
      elsif api_key
        request.add_field("X-JFrog-Art-Api", api_key)
      end

      # Setup PATCH/POST/PUT
      if %i{patch post put}.include?(verb)
        if data.respond_to?(:read)
          request.content_length = data.size
          request.body_stream = data
        elsif data.is_a?(Hash)
          request.form_data = data
        else
          request.body = data
        end
      end

      # Create the HTTP connection object - since the proxy information defaults
      # to +nil+, we can just pass it to the initializer method instead of doing
      # crazy strange conditionals.
      connection = Net::HTTP.new(uri.host, uri.port,
        proxy_address, proxy_port, proxy_username, proxy_password)

      # The artifacts being uploaded might be large, so there’s a good chance
      # we'll need to bump this higher than the `Net::HTTP` default of 60
      # seconds.
      connection.read_timeout = read_timeout

      # Apply SSL, if applicable
      if uri.scheme == "https"
        require "net/https" unless defined?(Net::HTTPS)

        # Turn on SSL
        connection.use_ssl = true

        # Custom pem files, no problem!
        if ssl_pem_file
          pem = File.read(ssl_pem_file)
          connection.cert = OpenSSL::X509::Certificate.new(pem)
          connection.key = OpenSSL::PKey::RSA.new(pem)
          connection.verify_mode = OpenSSL::SSL::VERIFY_PEER
        end

        # Naughty, naughty, naughty! Don't blame when when someone hops in
        # and executes a MITM attack!
        unless ssl_verify
          connection.verify_mode = OpenSSL::SSL::VERIFY_NONE
        end
      end

      # Create a connection using the block form, which will ensure the socket
      # is properly closed in the event of an error.
      connection.start do |http|

        if block_given?
          http.request(request) do |response|
            case response
            when Net::HTTPRedirection
              redirect = response["location"]
              request(verb, redirect, data, headers, &block)
            when Net::HTTPSuccess
              response.read_body do |chunk|
                yield chunk
              end
            else
              error(response)
            end
          end
        else
          response = http.request(request)

          case response
          when Net::HTTPRedirection
            redirect = response["location"]
            request(verb, redirect, data, headers)
          when Net::HTTPSuccess
            success(response)
          else
            error(response)
          end
        end
      end
    rescue SocketError, Errno::ECONNREFUSED, EOFError
      raise Error::ConnectionError.new(endpoint)
    end

    #
    # The list of default headers (such as Keep-Alive and User-Agent) for the
    # client object.
    #
    # @return [Hash]
    #
    def default_headers
      {
        "Connection" => "keep-alive",
        "Keep-Alive" => "30",
        "User-Agent" => user_agent,
      }
    end

    #
    # Construct a URL from the given verb and path. If the request is a GET or
    # DELETE request, the params are assumed to be query params are are
    # converted as such using {Client#to_query_string}.
    #
    # If the path is relative, it is merged with the {Defaults.endpoint}
    # attribute. If the path is absolute, it is converted to a URI object and
    # returned.
    #
    # @param [Symbol] verb
    #   the lowercase HTTP verb (e.g. :+get+)
    # @param [String] path
    #   the absolute or relative HTTP path (url) to get
    # @param [Hash] params
    #   the list of params to build the URI with (for GET and DELETE requests)
    #
    # @return [URI]
    #
    def build_uri(verb, path, params = {})
      # Add any query string parameters
      if %i{delete get}.include?(verb)
        path = [path, to_query_string(params)].compact.join("?")
      end

      # Parse the URI
      uri = URI.parse(path)

      # Don't merge absolute URLs
      uri = URI.parse(File.join(endpoint, path)) unless uri.absolute?

      # Return the URI object
      uri
    end

    #
    # Helper method to get the corresponding {Net::HTTP} class from the given
    # HTTP verb.
    #
    # @param [#to_s] verb
    #   the HTTP verb to create a class from
    #
    # @return [Class]
    #
    def class_for_request(verb)
      Net::HTTP.const_get(verb.to_s.capitalize)
    end

    #
    # Convert the given hash to a list of query string parameters. Each key and
    # value in the hash is URI-escaped for safety.
    #
    # @param [Hash] hash
    #   the hash to create the query string from
    #
    # @return [String, nil]
    #   the query string as a string, or +nil+ if there are no params
    #
    def to_query_string(hash)
      hash.map do |key, value|
        "#{CGI.escape(key.to_s)}=#{CGI.escape(value.to_s)}"
      end.join("&")[/.+/]
    end

    #
    # Parse the response object and manipulate the result based on the given
    # +Content-Type+ header. For now, this method only parses JSON, but it
    # could be expanded in the future to accept other content types.
    #
    # @param [HTTP::Message] response
    #   the response object from the request
    #
    # @return [String, Hash]
    #   the parsed response, as an object
    #
    def success(response)
      if (response.content_type || "").include?("json")
        JSON.parse(response.body || "{}")
      else
        response.body || ""
      end
    end

    #
    # Raise a response error, extracting as much information from the server's
    # response as possible.
    #
    # @raise [Error::HTTPError]
    #
    # @param [HTTP::Message] response
    #   the response object from the request
    #
    def error(response)
      if (response.content_type || "").include?("json")
        # Attempt to parse the error as JSON
        begin
          json = JSON.parse(response.body)

          if json["errors"] && json["errors"].first
            raise Error::HTTPError.new(json["errors"].first)
          end
        rescue JSON::ParserError; end
      end

      raise Error::HTTPError.new(
        "status"  => response.code,
        "message" => response.body
      )
    end
  end
end
