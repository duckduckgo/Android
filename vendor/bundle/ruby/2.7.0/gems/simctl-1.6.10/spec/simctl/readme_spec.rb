require 'spec_helper'

RSpec.describe SimCtl do
  it 'executes example code from readme' do
    # Select the iOS 15.2 runtime
    runtime = SimCtl.runtime(name: 'iOS 15.2')

    # Select the iPhone 8 device type
    devicetype = SimCtl.devicetype(name: 'iPhone 8')

    # Create a new device
    device = SimCtl.create_device 'Unit Tests @ iPhone 8 - 15.2', devicetype, runtime

    # Boot the device
    device.boot

    # Launch a new Simulator.app instance
    device.launch

    # Wait for the device to be booted
    device.wait { |d| d.state == :booted }

    # Kill the Simulator.app instance again
    device.shutdown
    device.kill

    # Wait until it did shutdown
    device.wait { |d| d.state == :shutdown }

    # Delete the device
    device.delete
  end
end
