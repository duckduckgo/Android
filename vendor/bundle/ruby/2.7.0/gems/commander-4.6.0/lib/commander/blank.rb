# frozen_string_literal: true

module Blank
  def self.included(base)
    base.class_eval do
      instance_methods.each { |m| undef_method m unless m =~ /^__|object_id/ }
    end
  end
end
