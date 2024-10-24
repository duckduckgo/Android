# frozen_string_literal: true

module Commander
  def configure(*configuration_opts, &configuration_block)
    configuration_module = Module.new
    configuration_module.extend Commander::Methods

    configuration_module.class_exec(*configuration_opts, &configuration_block)

    configuration_module.class_exec do
      run!
    end
  end

  module_function :configure
end
