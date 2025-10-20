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

package android.cts.compatchanges.host;

import android.compat.cts.CompatChangeGatingTestCase;

import com.google.common.collect.ImmutableSet;

public class LimitRequestUrisHostTest extends CompatChangeGatingTestCase {
    protected static final String TEST_APK = "CtsLimitRequestUrisTestsApp.apk";
    protected static final String TEST_PKG = "android.cts.compatchanges.device.limitrequesturis";
    protected static final String TEST_CLASS_NAME = ".LimitRequestUrisTest";
    protected static final long LIMIT_CREATE_REQUEST_URIS = 203408344L;

    @Override
    protected void setUp() throws Exception {
        installPackage(TEST_APK, true);
    }

    public void testIsChangeEnabled() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "verifyCompatChangeIsEnabled",
                /*enabledChanges*/ ImmutableSet.of(LIMIT_CREATE_REQUEST_URIS),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testIsChangeDisabled() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "verifyCompatChangeIsDisabled",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(LIMIT_CREATE_REQUEST_URIS));
    }

    public void testCreateWriteRequestLimitUris_throwsIllegalArgumentException() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "testCreateWriteRequestLimitUris_throwsIllegalArgumentException",
                /*enabledChanges*/ ImmutableSet.of(LIMIT_CREATE_REQUEST_URIS),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testCreateDeleteRequestLimitUris_throwsIllegalArgumentException() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/
                "testCreateDeleteRequestLimitUris_throwsIllegalArgumentException",
                /*enabledChanges*/ ImmutableSet.of(LIMIT_CREATE_REQUEST_URIS),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testCreateFavoriteRequestLimitUris_throwsIllegalArgumentException()
            throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/
                "testCreateFavoriteRequestLimitUris_throwsIllegalArgumentException",
                /*enabledChanges*/ ImmutableSet.of(LIMIT_CREATE_REQUEST_URIS),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testCreateTrashRequestLimitUris_throwsIllegalArgumentException() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "testCreateTrashRequestLimitUris_throwsIllegalArgumentException",
                /*enabledChanges*/ ImmutableSet.of(LIMIT_CREATE_REQUEST_URIS),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testCreateWriteRequestLimitUris_success() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "testCreateWriteRequestLimitUris_success",
                /*enabledChanges*/ ImmutableSet.of(LIMIT_CREATE_REQUEST_URIS),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testCreateDeleteRequestLimitUris_success() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "testCreateDeleteRequestLimitUris_success",
                /*enabledChanges*/ ImmutableSet.of(LIMIT_CREATE_REQUEST_URIS),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testCreateFavoriteRequestLimitUris_success() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "testCreateFavoriteRequestLimitUris_success",
                /*enabledChanges*/ ImmutableSet.of(LIMIT_CREATE_REQUEST_URIS),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testCreateTrashRequestLimitUris_success() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                TEST_CLASS_NAME,
                /*testMethodName*/ "testCreateTrashRequestLimitUris_success",
                /*enabledChanges*/ ImmutableSet.of(LIMIT_CREATE_REQUEST_URIS),
                /*disabledChanges*/ ImmutableSet.of());
    }
}
