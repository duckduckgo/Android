module SimCtl
  class Keychain
    def initialize(device)
      @device = device
    end

    # Reset the keychain
    #
    # @return [void]
    def reset
      SimCtl.keychain_reset(device)
    end

    private

    attr_reader :device
  end
end
