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

module Artifactory
  module Util
    extend self

    #
    # Covert the given CaMelCaSeD string to under_score. Graciously borrowed
    # from http://stackoverflow.com/questions/1509915.
    #
    # @param [String] string
    #   the string to use for transformation
    #
    # @return [String]
    #
    def underscore(string)
      string
        .to_s
        .gsub(/::/, "/")
        .gsub(/([A-Z]+)([A-Z][a-z])/, '\1_\2')
        .gsub(/([a-z\d])([A-Z])/, '\1_\2')
        .tr("-", "_")
        .downcase
    end

    #
    # Convert an underscored string to it's camelcase equivalent constant.
    #
    # @param [String] string
    #   the string to convert
    #
    # @return [String]
    #
    def camelize(string, lowercase = false)
      result = string
        .to_s
        .split("_")
        .map(&:capitalize)
        .join

      if lowercase
        result[0, 1].downcase + result[1..-1]
      else
        result
      end
    end

    #
    # Truncate the given string to a certain number of characters.
    #
    # @param [String] string
    #   the string to truncate
    # @param [Hash] options
    #   the list of options (such as +length+)
    #
    def truncate(string, options = {})
      length = options[:length] || 30

      if string.length > length
        string[0..length - 3] + "..."
      else
        string
      end
    end

    #
    # Rename a list of keys to the given map.
    #
    # @example Rename the given keys
    #   rename_keys(hash, foo: :bar, zip: :zap)
    #
    # @param [Hash] options
    #   the options to map
    # @param [Hash] map
    #   the map of keys to map
    #
    # @return [Hash]
    #
    def rename_keys(options, map = {})
      Hash[options.map { |k, v| [map[k] || k, v] }]
    end

    #
    # Slice the given list of options with the given keys.
    #
    # @param [Hash] options
    #   the list of options to slice
    # @param [Array<Object>] keys
    #   the keys to slice
    #
    # @return [Hash]
    #   the sliced hash
    #
    def slice(options, *keys)
      keys.inject({}) do |hash, key|
        hash[key] = options[key] if options[key]
        hash
      end
    end

    #
    # Flatten an xml element with at most one child node with children
    # into a hash.
    #
    # @param [REXML] element
    #   xml element
    #
    def xml_to_hash(element, child_with_children = "", unique_children = true)
      properties = {}
      element.each_element_with_text do |e|
        if e.name.eql?(child_with_children)
          if unique_children
            e.each_element_with_text do |t|
              properties[t.name] = to_type(t.text)
            end
          else
            children = []
            e.each_element_with_text do |t|
              properties[t.name] = children.push(to_type(t.text))
            end
          end
        else
          properties[e.name] = to_type(e.text)
        end
      end
      properties
    end

    def to_type(string)
      return true if string.eql?("true")
      return false if string.eql?("false")
      return string.to_i if numeric?(string)

      string
    end

    private

    def numeric?(string)
      string.to_i.to_s == string || string.to_f.to_s == string
    end
  end
end
