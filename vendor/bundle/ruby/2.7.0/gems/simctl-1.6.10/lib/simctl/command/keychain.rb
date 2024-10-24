require 'ostruct'

module SimCtl
  class Command
    module Keychain
      # Reset the keychain
      #
      # @param device [SimCtl::Device] the device
      # @return [void]
      def keychain_reset(device)
        unless Xcode::Version.gte? '11.4'
          raise UnsupportedCommandError, 'Needs at least Xcode 11.4'
        end
        Executor.execute(command_for('keychain', device.udid, 'reset'))
      end
    end
  end
end
