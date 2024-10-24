require 'spec_helper'

RSpec.describe SimCtl do
  describe '#warmup' do
    it 'warms up and returns a device for given strings' do
      SimCtl.reset_device 'iPhone 8', SimCtl.devicetype(name: 'iPhone 8'), SimCtl.runtime(name: 'iOS 15.2')
      expect(SimCtl.warmup('iPhone 8', 'iOS 15.2')).to be_kind_of SimCtl::Device
    end

    it 'warms up and returns a device for given objects' do
      devicetype = SimCtl.devicetype(name: 'iPhone 8')
      runtime = SimCtl::Runtime.latest(:ios)
      SimCtl.reset_device 'iPhone 8', devicetype, runtime
      expect(SimCtl.warmup(devicetype, runtime)).to be_kind_of SimCtl::Device
    end
  end
end
