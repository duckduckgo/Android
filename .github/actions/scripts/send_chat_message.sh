#!/usr/bin/env -S bash -euo pipefail

#
# Copyright (c) 2024 DuckDuckGo
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

if [ -z "${MM_AUTH_TOKEN:-}" ]; then
  echo 'MM_AUTH_TOKEN is not set!'
  exit 1
fi

if [ -z "${MM_TEAM_ID:-}" ]; then
  echo 'MM_TEAM_ID is not set!'
  exit 1
fi

message="$1"
channel_name="${2:-thermostat}"
username="${3:-dax}"
priority="${4:-empty}" # empty, important, or urgent
request_ack="${5:-false}"
# Same icon as https://dub.duckduckgo.com/orgs/duckduckgo/teams/ci/edit. Uploaded with `s3cmd put --acl-public dax-ci-avatar.png 's3://ddg-chef/dax-ci-avatar.png`
icon_url='https://ddg-chef.s3.amazonaws.com/dax-ci-avatar.png'

# API docs at https://api.mattermost.com/#tag/posts/operation/CreatePost
MM_API='https://chat.duckduckgo.com/api/v4'
MM_AUTH="Authorization: Bearer ${MM_AUTH_TOKEN}"

echo "> Getting id of https://chat.duckduckgo.com/ddg/channels/${channel_name}..."
channel_id=$(curl -sS -H "$MM_AUTH" "${MM_API}/teams/${MM_TEAM_ID}/channels/name/${channel_name}" | jq -r '.id')
echo "> Found https://chat.duckduckgo.com/ddg/channels/${channel_id}"

if [ "${GITHUB_ACTIONS:-}" ]; then
  now=$(date '+%H:%M %Z')
  single_line_message=$(echo -n "${message}" | sed -z 's_\n_%0A_g')
  echo "::notice title=${username} to ~${channel_name} at ${now}::${single_line_message}"
fi

json_message=$(echo -n "${message}" | jq -Rs .) # includes double-quotes around message
echo "> Sending ${json_message} with priority '${priority}'..."
res=$(curl -sS --fail -H "$MM_AUTH" -X POST "${MM_API}/posts" -d '{
  "channel_id":"'"${channel_id}"'",
  "message":'"${json_message}"',
  "metadata": {
    "priority": {
      "priority": "'"${priority}"'",
      "requested_ack": '"${request_ack}"'
    }
  },
  "props":{
    "override_username": "'"${username}"'",
    "override_icon_url":"'"${icon_url}"'",
    "from_webhook": "true"
  }
}')

post_id=$(echo "$res" | jq -r '.id')
echo "✔ Sent https://chat.duckduckgo.com/ddg/pl/${post_id}"