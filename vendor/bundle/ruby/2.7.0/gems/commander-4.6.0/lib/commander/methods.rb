# frozen_string_literal: true

module Commander
  module Methods
    include Commander::UI
    include Commander::UI::AskForClass
    include Commander::Delegates

    if $stdin.tty? && (cols = HighLine.default_instance.output_cols) >= 40
      HighLine.default_instance.wrap_at = cols - 5
    end
  end
end
