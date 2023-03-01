/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.android.ui.message

// Proposal:
// The parameters for the new Message Component would be (names are up for debate):
// CloseIcon: Shows or Hides the X button
// HeaderImage: Image to be shown as header. Hidden if not provided
// Title: Title text
// Message: Message text
// Primary Button: (you need to decide in which side does the button sit): Button text
// Secondary Button: (you need to decide in which side does the button sit): Button text
// Buttons are hidden if no text is provided
// Content Orientation: Content aligned to left / center
//
// Scope:
// Create new Component
// Update usages:
// The current Notify Me Component becomes Message Component with the following variations selected:
// Aligned to the right
// No close icon
// Secondary button to Cancel / Dismiss
// The current Remote Message Component becomes Message Component with the following variations selected:
// Aligned to the center
// With close icon
