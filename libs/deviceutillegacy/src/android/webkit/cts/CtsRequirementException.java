/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.webkit.cts;

/**
 * This error indicates that the device failed to meet one of the basic device setup requirements
 * for CTS. Refer to <a href="https://source.android.com/docs/compatibility/cts/setup">this
 * document</a> for the full list of requirements and refer to this exception's message for the
 * specific requirement which was violated.
 *
 * <p>This error generally indicates a problem with how the device was set up, not a bug in the
 * device under test, the code under test, or the test case itself. If this is being thrown on
 * automated test infrastructure, then this indicates an infrastructure problem.
 */
public final class CtsRequirementException extends RuntimeException {
    public CtsRequirementException(String message) {
        super(message);
    }

    public CtsRequirementException(String message, Throwable cause) {
        super(message, cause);
    }
}
