# Test Build

This directory contains tests that are meant to be ran locally

1. Install dependencies: `npm i`

2. Setup Environment Variables. Create a `.env` file within the root of the project directory with the following information

```
PERSONAL_ACCESS_TOKEN=<YOUR_ASANA_PERSONAL_ACCESS_TOKEN>
TEAM_GID=<YOUR_TEAM_GID>
TEXT_CUSTOM_FIELD_GID=<YOUR_TEXT_CUSTOM_FIELD_GID> -> NOTE: make sure that there is at least one task that has this custom field and the value of the custom field on that task is `custom_value`
USER_GID=<YOUR_USER_GID>
WORKSPACE_GID=<YOUR_WORKSPACE_GID>
```

3. Run tests: `npm run testbuild`

TIP: to debug, add `debugger;` to the location of the test code you want to debug and re-run `npm run testbuild`
