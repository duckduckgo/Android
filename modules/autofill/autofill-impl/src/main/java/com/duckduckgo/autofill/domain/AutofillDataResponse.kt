/*
 * Copyright (c) 2022 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.autofill.domain

data class AutofillDataResponse(
    val type: String = "getAutofillDataResponse",
    val success: CredentialSuccessResponse
)

data class CredentialSuccessResponse(
    val username: String = "",
    val password: String? = null
)

/*
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAutofillDataResponse",
  "title": "GetAutofillDataResponse",
  "type": "object",
  "oneOf": [
    {
      "title": "GetAutofillDataResponse Success Response",
      "type": "object",
      "properties": {
        "type": {
          "title": "Response name",
          "description": "Required on Android + Windows devices, optional on iOS",
          "type": "string",
          "const": "getAutofillDataResponse"
        },
        "success": {
          "type": "object",
          "oneOf": [
            { "$ref": "#/definitions/Credentials" }
          ]
        }
      },
      "required": [
        "success"
      ]
    },
    {
      "$ref": "#/definitions/GenericError"
    }
  ]
}
 */
