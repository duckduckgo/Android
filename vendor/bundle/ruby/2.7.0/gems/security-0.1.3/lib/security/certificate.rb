module Security
  class Certificate
    private_class_method :new

    def delete!
      raise NotImplementedError
    end

    def verified?
      raise NotImplementedError
    end

    class << self
      def find
        raise NotImplementedError
      end
    end
  end
end
