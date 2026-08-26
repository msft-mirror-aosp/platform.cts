/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.inputmethodservice.cts.devicetest;

import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.waitUntil;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.inputmethodservice.cts.ime.Watermark;

/** Provides utility methods to test whether test IMEs are visible to the user or not. */
final class InputMethodVisibilityVerifier {

  private InputMethodVisibilityVerifier() {}

  /**
   * Asserts that IME1 is visible to the user.
   *
   * @param timeout timeout in milliseconds.
   */
  static void assertIme1Visible(long timeout) {
    assertTrue(waitUntil(timeout, Watermark.IME1::isContainedIn));
  }

  /**
   * Assumes that IME1 is visible to the user.
   *
   * @param message message to be shown when the assumption is not satisfied.
   * @param timeout timeout in milliseconds.
   */
  static void assumeIme1Visible(String message, long timeout) {
    assumeTrue(message, waitUntil(timeout, Watermark.IME1::isContainedIn));
  }

  /**
   * Asserts that IME2 is visible to the user.
   *
   * @param timeout timeout in milliseconds.
   */
  static void assertIme2Visible(long timeout) {
    assertTrue(waitUntil(timeout, Watermark.IME2::isContainedIn));
    }
}
