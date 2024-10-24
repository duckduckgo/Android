Given(/^some big input$/) do
  add_run_input File.open('features/fixtures/xcodebuild.log', 'r').read
end

Then(/^I should have a JSON compilation database in a custom path$/) do
  step("I should have a JSON compilation database at \"#{custom_report_path}\"")
end

Then(/^I should have a JSON compilation database at "(.*?)"$/) do |path|
  json = JSON.parse(File.open(path, 'r').read)
  json.should_not be_nil
end

Then(/^I should have JSON compilation databases in two custom paths$/) do
  step("I should have a JSON compilation database at \"#{custom_report_path}\"")
  step("I should have a JSON compilation database at \"#{other_custom_report_path}\"")
end

Then(/^the JSON compilation database should contain an entry with a command$/) do
  json_db.length.should == 1
  json_db[0]['command'].should start_with('/')
  json_db[0]['command'].should include('clang ')
  json_db[0]['command'].should include(' -c ')
  json_db[0]['command'].should include(' -o ')
  json_db[0]['command'].should end_with('.o')
end

Then(/^the JSON compilation database should contain an entry with a file$/) do
  json_db[0]['file'].should end_with('.m')
end

Then(/^the JSON compilation database should contain an entry with a directory$/) do
  json_db[0]['directory'].should start_with('/')
end

Then(/^the JSON compilation database should be complete$/) do
  entries = json_db.select { |entry| entry['command'] && entry['file'] && entry['directory'] }
  entries.length.should == JSON_DB_FIXTURE_COMMAND_COUNT
end

Then(/^entries with a command shouldn't have malformed "-include" directives$/) do
  entries = json_db.select { |entry| entry['command'].match(/-include\s+-/) }
  entries.length.should == 0
end

