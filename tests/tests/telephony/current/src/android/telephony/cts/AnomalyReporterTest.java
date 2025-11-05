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

package android.telephony.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.ParcelUuid;
import android.provider.DeviceConfig;
import android.telephony.AnomalyReporter;
import android.telephony.TelephonyManager;

import com.android.bedstead.nene.utils.UndoableContext;
import com.android.bedstead.permissions.Permissions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Unit tests for the {@link AnomalyReporter} class. */
public class AnomalyReporterTest {

    @Mock private Context mMockContext;
    @Mock private PackageManager mMockPackageManager;

    private static final String MOCK_PACKAGE_NAME = "com.android.phone.mock";
    private UndoableContext mIgnorePermissionsContext;

    /** Set up common test environment. */
    @Before
    public void setUp() throws Exception {
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
    }

    /** Set up DeviceConfig to enable anomaly reporting. */
    @Before
    public void setUpDeviceConfigAnomalyReportEnabled() {
        MockitoAnnotations.initMocks(this);
        mIgnorePermissionsContext = Permissions.ignoringPermissions();
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_TELEPHONY,
                "is_telephony_anomaly_report_enabled",
                "true",
                false /* makeDefault */);
    }

    /** Tear down common test environment. */
    @After
    public void tearDown() throws Exception {
        resetAnomalyReporter();
    }

    /** Tear down DeviceConfig settings. */
    @After
    public void tearDownDeviceConfigAnomalyReportEnabled() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_TELEPHONY,
                "is_telephony_anomaly_report_enabled",
                null,
                false /* makeDefault */);
        if (mIgnorePermissionsContext != null) {
            mIgnorePermissionsContext.close();
        }
    }

    /**
     * Verifies that calling {@link AnomalyReporter#initialize(Context)} with a null context throws
     * an {@link IllegalArgumentException}.
     */
    @Test
    public void testInitialize_nullContext() {
        assertThrows(IllegalArgumentException.class, () -> AnomalyReporter.initialize(null));
    }

    /**
     * Verifies that calling {@link AnomalyReporter#initialize(Context)} without the {@link
     * Manifest.permission#MODIFY_PHONE_STATE} permission throws a {@link SecurityException}.
     */
    @Test
    public void testInitialize_noModifyPhoneStatePermission() {
        doThrow(new SecurityException())
                .when(mMockContext)
                .enforceCallingOrSelfPermission(
                        eq(Manifest.permission.MODIFY_PHONE_STATE), anyString());
        assertThrows(SecurityException.class, () -> AnomalyReporter.initialize(mMockContext));
    }

    /**
     * Verifies that {@link AnomalyReporter#initialize(Context)} handles a null {@link
     * PackageManager} gracefully.
     */
    @Test
    public void testInitialize_nullPackageManager() {
        when(mMockContext.getPackageManager()).thenReturn(null);
        try {
            AnomalyReporter.initialize(mMockContext);
            // No exception should be thrown, and sDebugPackageName should be null.
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
        assertDebugPackageName(null);
    }

    /**
     * Verifies that {@link AnomalyReporter#initialize(Context)} handles the case where no broadcast
     * receivers are found.
     */
    @Test
    public void testInitialize_noReceivers() {
        when(mMockPackageManager.queryBroadcastReceivers(any(), anyInt()))
                .thenReturn(Collections.emptyList());
        try {
            AnomalyReporter.initialize(mMockContext);
            // No exception should be thrown, and sDebugPackageName should be null.
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
        assertDebugPackageName(null);
    }

    /**
     * Verifies that {@link AnomalyReporter#initialize(Context)} handles a {@link ResolveInfo} with
     * a null {@link ActivityInfo}.
     */
    @Test
    public void testInitialize_oneReceiver_nullActivityInfo() {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = null;
        when(mMockPackageManager.queryBroadcastReceivers(any(), anyInt()))
                .thenReturn(Collections.singletonList(resolveInfo));
        try {
            AnomalyReporter.initialize(mMockContext);
            // No exception should be thrown, and sDebugPackageName should be null.
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
        assertDebugPackageName(null);
    }

    /**
     * Verifies that {@link AnomalyReporter#initialize(Context)} handles a receiver that does not
     * have the {@link Manifest.permission#READ_PRIVILEGED_PHONE_STATE} permission.
     */
    @Test
    public void testInitialize_oneReceiver_noReadPrivilegedPhoneStatePermission() {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = MOCK_PACKAGE_NAME;
        when(mMockPackageManager.checkPermission(
                        eq(Manifest.permission.READ_PRIVILEGED_PHONE_STATE), eq(MOCK_PACKAGE_NAME)))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mMockPackageManager.queryBroadcastReceivers(any(), anyInt()))
                .thenReturn(Collections.singletonList(resolveInfo));
        try {
            AnomalyReporter.initialize(mMockContext);
            // No exception should be thrown, and sDebugPackageName should be null.
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
        assertDebugPackageName(null);
    }

    /**
     * Verifies that {@link AnomalyReporter#initialize(Context)} correctly identifies a valid
     * receiver with all the necessary permissions.
     */
    @Test
    public void testInitialize_oneReceiver_allPermissions() {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = MOCK_PACKAGE_NAME;
        when(mMockPackageManager.checkPermission(
                        eq(Manifest.permission.READ_PRIVILEGED_PHONE_STATE), eq(MOCK_PACKAGE_NAME)))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mMockPackageManager.queryBroadcastReceivers(any(), anyInt()))
                .thenReturn(Collections.singletonList(resolveInfo));
        try {
            AnomalyReporter.initialize(mMockContext);
            // No exception should be thrown, and sDebugPackageName should be null.
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
        assertDebugPackageName(MOCK_PACKAGE_NAME);
        assertsContext(mMockContext);
    }

    /**
     * Verifies that {@link AnomalyReporter#initialize(Context)} selects the first valid receiver
     * when multiple are present.
     */
    @Test
    public void testInitialize_multipleReceivers() {
        ResolveInfo validResolveInfo1 = new ResolveInfo();
        validResolveInfo1.activityInfo = new ActivityInfo();
        validResolveInfo1.activityInfo.packageName = MOCK_PACKAGE_NAME;

        ResolveInfo validResolveInfo2 = new ResolveInfo();
        validResolveInfo2.activityInfo = new ActivityInfo();
        validResolveInfo2.activityInfo.packageName = "com.android.phone.anothermock";

        when(mMockPackageManager.checkPermission(
                        eq(Manifest.permission.READ_PRIVILEGED_PHONE_STATE), anyString()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        List<ResolveInfo> receivers = new ArrayList<>();
        receivers.add(validResolveInfo1);
        receivers.add(validResolveInfo2);

        when(mMockPackageManager.queryBroadcastReceivers(any(), anyInt())).thenReturn(receivers);
        try {
            AnomalyReporter.initialize(mMockContext);
            // No exception should be thrown, and sDebugPackageName should be null.
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
        // The first valid package should be chosen.
        assertDebugPackageName(MOCK_PACKAGE_NAME);
    }

    /**
     * Verifies that {@link AnomalyReporter#reportAnomaly(UUID, String)} does not send a broadcast
     * if the reporter has not been initialized.
     */
    @Test
    public void testReportAnomaly_notInitialized() {
        UUID eventId = UUID.randomUUID();
        String description = "Test Description";
        AnomalyReporter.reportAnomaly(eventId, description);
        // No broadcast should be sent.
        verify(mMockContext, never()).sendBroadcast(any(), anyString());
    }

    /**
     * Verifies that {@link AnomalyReporter#reportAnomaly(UUID, String)} does not send a broadcast
     * for the same event more than once.
     */
    @Test
    public void testReportAnomaly_duplicateEvent() {
        initializeWithPackage(MOCK_PACKAGE_NAME);
        UUID eventId = UUID.randomUUID();
        String description = "Test Description";

        // Reporting the same event twice.
        // In a unit test, we can't rely on DeviceConfig, so we assume it passes.
        AnomalyReporter.reportAnomaly(eventId, description);
        AnomalyReporter.reportAnomaly(eventId, description);

        // The broadcast should only be sent once.
        verify(mMockContext, times(1)).sendBroadcast(any(Intent.class), anyString());
    }

    /**
     * Verifies a successful call to {@link AnomalyReporter#reportAnomaly(UUID, String)}, checking
     * for correct logging and broadcast intent.
     */
    @Test
    public void testReportAnomaly() {
        initializeWithPackage(MOCK_PACKAGE_NAME);
        UUID eventId = UUID.randomUUID();
        String description = "Test Description";
        AnomalyReporter.reportAnomaly(eventId, description);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockContext)
                .sendBroadcast(
                        intentCaptor.capture(),
                        eq(Manifest.permission.READ_PRIVILEGED_PHONE_STATE));

        Intent intent = intentCaptor.getValue();
        assertEquals(TelephonyManager.ACTION_ANOMALY_REPORTED, intent.getAction());
        assertEquals(MOCK_PACKAGE_NAME, intent.getPackage());
        assertEquals(
                new ParcelUuid(eventId),
                intent.getParcelableExtra(TelephonyManager.EXTRA_ANOMALY_ID));
        assertEquals(
                description, intent.getStringExtra(TelephonyManager.EXTRA_ANOMALY_DESCRIPTION));
    }


    /**
     * Verifies that {@link AnomalyReporter#reportAnomaly(UUID, String)} does not send a broadcast
     * if initialization failed to find a valid broadcast receiver package.
     */
    @Test
    public void testReportAnomaly_NullPackage() {
        initializeWithPackage(null);
        UUID eventId = UUID.randomUUID();
        String description = "Test Description";
        AnomalyReporter.reportAnomaly(eventId, description);

        verify(mMockContext, never()).sendBroadcast(any(), anyString());
    }

    /**
     * Verifies that {@link AnomalyReporter#reportAnomaly(UUID, String)} throws a
     * {@link NullPointerException} when the event ID is null.
     */
    @Test
    public void testReportAnomaly_nullEventId() {
        initializeWithPackage(MOCK_PACKAGE_NAME);
        String description = "Test Description";
        assertThrows(NullPointerException.class,
                () -> AnomalyReporter.reportAnomaly(null, description));
    }

    /**
     * Sets up {@link PackageManager} mocks and initializes the {@link AnomalyReporter}.
     *
     * @param packageName The package name for the mock receiver. Can be {@code null} to test cases
     * where no valid receiver is found.
     */
    private void initializeWithPackage(String packageName) {
        try {
            Field contextField = AnomalyReporter.class.getDeclaredField("sContext");
            contextField.setAccessible(true);
            contextField.set(null, mMockContext);

            Field packageNameField = AnomalyReporter.class.getDeclaredField("sDebugPackageName");
            packageNameField.setAccessible(true);
            packageNameField.set(null, packageName);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to set static fields in AnomalyReporter: " + e.getMessage());
        }
    }

    /** Resets the static fields in the {@link AnomalyReporter} class. */
    private void resetAnomalyReporter() {
        try {
            Field contextField = AnomalyReporter.class.getDeclaredField("sContext");
            contextField.setAccessible(true);
            contextField.set(null, null);

            Field packageNameField = AnomalyReporter.class.getDeclaredField("sDebugPackageName");
            packageNameField.setAccessible(true);
            packageNameField.set(null, null);

            Field reportedEventsField = AnomalyReporter.class.getDeclaredField("sReportedEventIds");
            reportedEventsField.setAccessible(true);
            reportedEventsField.set(null, null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // This can be ignored in tests that don't need to reset the fields.
        }
    }

    /**
     * Asserts that the static {@code sDebugPackageName} field in {@link AnomalyReporter} has the
     * expected value.
     *
     * @param expected The expected value of {@code sDebugPackageName}.
     */
    private void assertDebugPackageName(String expected) {
        try {
            Field field = AnomalyReporter.class.getDeclaredField("sDebugPackageName");
            field.setAccessible(true);
            assertEquals(expected, field.get(null));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to access sDebugPackageName: " + e.getMessage());
        }
    }

    /**
     * Asserts that the static {@code sContext} field in {@link AnomalyReporter} has the expected
     * value.
     *
     * @param expected The expected value of {@code sContext}.
     */
    private void assertsContext(Context expected) {
        try {
            Field field = AnomalyReporter.class.getDeclaredField("sContext");
            field.setAccessible(true);
            assertEquals(expected, field.get(null));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to access sContext: " + e.getMessage());
        }
    }
}
