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
require "uri"

module Artifactory
  class Resource::Base
    class << self
      #
      # @macro attribute
      #   @method $1
      #     Return this object's +$1+
      #
      #     @return [Object]
      #
      #
      #   @method $1=(value)
      #     Set this object's +$1+
      #
      #     @param [Object] value
      #       the value to set for +$1+
      #     @param [Object] default
      #       the default value for this attribute
      #
      #   @method $1?
      #     Determines if the +$1+ value exists and is truthy
      #
      #     @return [Boolean]
      #
      def attribute(key, default = nil)
        key = key.to_sym unless key.is_a?(Symbol)

        # Set this attribute in the top-level hash
        attributes[key] = nil

        define_method(key) do
          value = attributes[key]
          return value unless value.nil?

          if default.nil?
            value
          elsif default.is_a?(Proc)
            default.call
          else
            default
          end
        end

        define_method("#{key}?") do
          !!attributes[key]
        end

        define_method("#{key}=") do |value|
          set(key, value)
        end
      end

      #
      # The list of attributes defined by this class.
      #
      # @return [Array<Symbol>]
      #
      def attributes
        @attributes ||= {}
      end

      #
      # Determine if this class has a given attribute.
      #
      # @param [#to_sym] key
      #   the key to check as an attribute
      #
      # @return [true, false]
      #
      def has_attribute?(key)
        attributes.key?(key.to_sym)
      end

      #
      # Construct a new object from the given URL.
      #
      # @param [String] url
      #   the URL to find the user from
      # @param [Hash] options
      #   the list of options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [~Resource::Base]
      #
      def from_url(url, options = {})
        # Parse the URL and only use the path so the configured
        # endpoint/proxy/SSL settings are used in the GET request.
        path = URI.parse(url_safe(url)).path
        client = extract_client!(options)
        # If the endpoint contains a path part, we must remove the
        # endpoint path part from path, because the client uses
        # endpoint + path as its full URI.
        endpoint_path = URI.parse(client.endpoint).path
        path.slice!(endpoint_path)
        from_hash(client.get(path), client: client)
      end

      #
      # List all the child text elements in the Artifactory configuration file
      # of a node matching the specified xpath
      #
      # @param [String] xpath
      #   xpath expression for the parent element whose children are to be listed
      #
      # @param [REXML] config
      #   Artifactory config as an REXML file
      #
      # @param [Hash] options
      #   the list of options
      #
      def list_from_config(xpath, config, options = {})
        REXML::XPath.match(config, xpath).map do |r|
          hash = {}

          r.each_element_with_text do |l|
            hash[l.name] = l.get_text
          end
          from_hash(hash, options)
        end
      end

      #
      # Find the text elements matching a giving xpath
      #
      # @param [String] xpath
      #   xpath expression
      #
      # @param [REXML] config
      #   Artifactory configuration file as an REXML doc
      #
      # @param [Hash] options
      #   the list of options
      #
      def find_from_config(xpath, config, options = {})
        name_node = REXML::XPath.match(config, xpath)
        return nil if name_node.empty?

        properties = {}
        name_node[0].parent.each_element_with_text do |e|
          properties[e.name] = Util.to_type(e.text)
        end

        from_hash(properties, options)
      end

      #
      # Construct a new object from the hash.
      #
      # @param [Hash] hash
      #   the hash to create the object with
      # @param [Hash] options
      #   the list options
      #
      # @option options [Artifactory::Client] :client
      #   the client object to make the request with
      #
      # @return [~Resource::Base]
      #
      def from_hash(hash, options = {})
        instance = new
        instance.client = extract_client!(options)

        hash.inject(instance) do |instance, (key, value)|
          method = :"#{Util.underscore(key)}="

          if instance.respond_to?(method)
            instance.send(method, value)
          end

          instance
        end
      end

      #
      # Get the client (connection) object from the given options. If the
      # +:client+ key is preset in the hash, it is assumed to contain the
      # connection object to use for the request. If the +:client+ key is not
      # present, the default {Artifactory.client} is used.
      #
      # Warning, the value of {Artifactory.client} is **not** threadsafe! If
      # multiple threads or processes are modifying the connection information,
      # the same request _could_ use a different client object. If you use the
      # {Artifactory::Client} proxy methods, this is handled for you.
      #
      # Warning, this method will **remove** the +:client+ key from the hash if
      # it exists.
      #
      # @param [Hash] options
      #   the list of options passed to the method
      #
      # @option options [Artifactory::Client] :client
      #   the client object to use for requests
      #
      def extract_client!(options)
        options.delete(:client) || Artifactory.client
      end

      #
      # Format the repos list from the given options. This method will modify
      # the given Hash parameter!
      #
      # Warning, this method will modify the given hash if it exists.
      #
      # @param [Hash] options
      #   the list of options to extract the repos from
      #
      def format_repos!(options)
        return options if options[:repos].nil? || options[:repos].empty?

        options[:repos] = Array(options[:repos]).compact.join(",")
        options
      end

      #
      # Generate a URL-safe string from the given value.
      #
      # @param [#to_s] value
      #   the value to sanitize
      #
      # @return [String]
      #   the URL-safe version of the string
      #
      def url_safe(value)
        uri_parser.escape(uri_parser.unescape(value.to_s))
      end

      #
      # Generate a URI parser
      #
      # @return [URI::Parser]
      def uri_parser
        @uri_parser ||= URI::Parser.new
      end
    end

    attribute :client, -> { Artifactory.client }

    #
    # Create a new instance
    #
    def initialize(attributes = {})
      attributes.each do |key, value|
        set(key, value)
      end
    end

    #
    # The list of attributes for this resource.
    #
    # @return [hash]
    #
    def attributes
      @attributes ||= self.class.attributes.dup
    end

    #
    # Set a given attribute on this resource.
    #
    # @param [#to_sym] key
    #   the attribute to set
    # @param [Object] value
    #   the value to set
    #
    # @return [Object]
    #   the set value
    #
    def set(key, value)
      attributes[key.to_sym] = value
    end

    # @see Resource::Base.extract_client!
    def extract_client!(options)
      self.class.extract_client!(options)
    end

    # @see Resource::Base.format_repos!
    def format_repos!(options)
      self.class.format_repos!(options)
    end

    # @see Resource::Base.url_safe
    def url_safe(value)
      self.class.url_safe(value)
    end

    #
    # The hash representation
    #
    # @example An example hash response
    #   { 'key' => 'local-repo1', 'includesPattern' => '**/*' }
    #
    # @return [Hash]
    #
    def to_hash
      attributes.inject({}) do |hash, (key, value)|
        unless Resource::Base.has_attribute?(key)
          hash[Util.camelize(key, true)] = send(key.to_sym)
        end

        hash
      end
    end

    #
    # The JSON representation of this object.
    #
    # @see Artifactory::Resource::Base#to_json
    #
    # @return [String]
    #
    def to_json
      JSON.fast_generate(to_hash)
    end

    #
    # Create CGI-escaped string from matrix properties
    #
    # @see http://bit.ly/1qeVYQl
    #
    def to_matrix_properties(hash = {})
      properties = hash.map do |k, v|
        key   = CGI.escape(k.to_s)
        value = CGI.escape(v.to_s)

        "#{key}=#{value}"
      end

      if properties.empty?
        nil
      else
        ";#{properties.join(";")}"
      end
    end

    #
    # Create URI-escaped querystring parameters
    #
    # @see http://bit.ly/1qeVYQl
    #
    def to_query_string_parameters(hash = {})
      properties = hash.map do |k, v|
        key   = self.class.uri_parser.escape(k.to_s)
        value = self.class.uri_parser.escape(v.to_s)

        "#{key}=#{value}"
      end

      if properties.empty?
        nil
      else
        properties.join("&")
      end
    end

    # @private
    def to_s
      "#<#{short_classname}>"
    end

    # @private
    def inspect
      list = attributes.collect do |key, value|
        unless Resource::Base.has_attribute?(key)
          "#{key}: #{value.inspect}"
        end
      end.compact

      "#<#{short_classname} #{list.join(", ")}>"
    end

    private

    def short_classname
      @short_classname ||= self.class.name.split("::").last
    end
  end
end
