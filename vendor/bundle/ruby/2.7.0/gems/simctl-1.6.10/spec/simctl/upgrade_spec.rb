require 'spec_helper'

RSpec.describe SimCtl do
  describe '#upgrade' do
    it 'upgrades a device to a newer runtime' do
      old_runtime = SimCtl.runtime(name: 'iOS 15.2')
      new_runtime = SimCtl.runtime(name: 'iOS 15.2')
      device = SimCtl.reset_device 'iPhone 8', SimCtl.devicetype(name: 'iPhone 8'), old_runtime
      expect(device.runtime.version).to be == old_runtime.version
      SimCtl.upgrade device, new_runtime
      device.wait { |d| d.runtime.version == new_runtime.version }
      device.delete
    end
  end
end
