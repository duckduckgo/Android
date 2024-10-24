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
  class Collection::Base
    #
    # Create a new collection object (proxy).
    #
    # @param [Class] klass
    #   the child class object
    # @param [Object] parent
    #   the parent object who created the collection
    # @param [Hash] options
    #   the list of options given by the parent
    # @param [Proc] block
    #   the block to evaluate for the instance
    #
    def initialize(klass, parent, options = {}, &block)
      @klass   = klass
      @parent  = parent
      @options = options
      @block   = block
    end

    #
    # Use method missing to delegate methods to the class object or instance
    # object.
    #
    def method_missing(m, *args, &block)
      if klass.respond_to?(m)
        if args.last.is_a?(Hash)
          args.last.merge(options)
        end

        klass.send(m, *args, &block)
      else
        instance.send(m, *args, &block)
      end
    end

    private

    attr_reader :klass
    attr_reader :parent
    attr_reader :options
    attr_reader :block

    def instance
      @instance ||= parent.instance_eval(&block)
    end
  end
end
