Then(/^I should see a failed test node in my report$/) do
  junit_report_root.elements.to_a.detect do |node|
    element = node.elements.to_a.first
    element && element.name == "failure"
  end.should_not be_nil
end

Then(/^I should see a passing test node in my report$/) do
  junit_report_root.elements.to_a.detect do |node|
    node.attributes["time"] != nil
  end.should_not be_nil
end

Then(/^I should see a pending test node in my report$/) do
  junit_report_root.elements.to_a.detect do |node|
    node.elements.to_a.detect { |child| child.name == 'skipped' }
  end.should_not be_nil
end

Then(/^I should see a test suite node$/) do
  junit_report_root.elements.to_a.first.should_not be_nil
end

Then(/^I should see (\d+) tests in my report$/) do |test_count|
  junit_report_root.attributes["tests"].should == test_count
  junit_report_root.elements.to_a.size.should == test_count.to_i
end

Then(/^I should see (\d+) test suites$/) do |count|
  suites = junit_report.root.elements.to_a
  suites.size.should == count.to_i
  suites.count { |s| s.name == 'testsuite' }.should == count.to_i
end

Then(/^I should have a test report at "(.*?)"$/) do |path|
  doc = REXML::Document.new(File.open(path, 'r').read)
  doc.root.should_not be_nil
end

