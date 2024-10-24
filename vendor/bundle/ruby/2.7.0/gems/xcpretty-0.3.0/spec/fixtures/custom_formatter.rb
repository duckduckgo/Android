# encoding: utf-8
class EmojiFormatter < XCPretty::Formatter

  def format_compile(file_name, file_path)
    "😎 Compilation party time for #{file_name}"
  end

  def format_failing_test(suite, test_case, reason, file)
    "😞 #{test_case}"
  end

  def format_passing_test(suite, test_case, time)
    "😍 #{test_case} (#{time})"
  end
end

EmojiFormatter

