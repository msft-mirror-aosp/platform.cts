/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.audio.cts.audiorecordpermissiontests;

import static android.media.audio.cts.audiorecordpermissiontests.common.ActionsKt.*;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.media.mediatestutils.TestUtils.getFutureForIntent;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class AudioRecordPermissionTests {
    // Keep in sync with test apps
    static final String API_34_PACKAGE = "android.media.audio.cts.CtsRecordServiceApi34";
    // Behavior changes with targetSdk >= 34, so test both cases
    static final String API_33_PACKAGE = "android.media.audio.cts.CtsRecordServiceApi33";
    static final String API_34_NO_CAP_PACKAGE =
            "android.media.audio.cts.CtsRecordServiceApi34NoCap";

    static final String SERVICE_NAME = ".RecordService";

    static final int FUTURE_WAIT_SECS = 15;
    static final int FALSE_NEG_SECS = 10;

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();

    // Used in teardown
    private List<String> mServiceStartedPackages = new ArrayList<>();
    private List<String> mActivityStartedPackages = new ArrayList<>();

    @Before
    public void setup() throws Exception {
        assumeTrue(
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE));
    }

    @After
    public void teardown() throws Exception {
        // Clean up any left-over activities, services
        for (var pack : mActivityStartedPackages) {
            stopActivity(pack);
        }
        for (var pack : mServiceStartedPackages) {
            stopService(pack);
        }
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToBackground_isSilenced() throws Exception {
        final var TEST_PACKAGE = API_34_PACKAGE;
        // Start an activity, then start recording in a background service
        startActivity(TEST_PACKAGE);
        // TODO(b/297259825) we never started recording unsilenced, due to avd sometime
        // providing only silenced mic data.
        assumeTrue(startServiceRecording(TEST_PACKAGE));
        // Prime future that the stream is silenced
        final var future = makeFuture(TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity(TEST_PACKAGE);

        // Future completes when silenced. If not, timeout and throw
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToSleep_isSilenced() throws Exception {
        final var TEST_PACKAGE = API_34_PACKAGE;
        // Start an activity, then start recording in a background service
        startActivity(TEST_PACKAGE);
        assumeTrue(startServiceRecording(TEST_PACKAGE));
        // Prime future that the stream is silenced
        final var future = makeFuture(TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        try {
            // Move out of TOP to TOP_SLEEPING
            SystemUtil.runShellCommand(mInstrumentation, "input keyevent KEYCODE_SLEEP");
            // Future completes when silenced. If not, timeout and throw
            future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        } finally {
            // Wait for unsilence after return to TOP
            final var receiveFuture = makeFuture(TEST_PACKAGE + ACTION_BEGAN_RECEIVE_AUDIO);
            SystemUtil.runShellCommand(mInstrumentation, "input keyevent KEYCODE_WAKEUP");
            SystemUtil.runShellCommand(mInstrumentation, "wm dismiss-keyguard");
            receiveFuture.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        }
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToForegroundServiceWithMicCapabilities_isNotSilenced()
            throws Exception {
        final var TEST_PACKAGE = API_34_PACKAGE;
        // Start an activity, then start recording in a fgs with mic caps
        startActivity(TEST_PACKAGE);
        startForeground(TEST_PACKAGE);
        assumeTrue(startServiceRecording(TEST_PACKAGE));
        // Prime future that the stream is silenced
        final var future = makeFuture(TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity(TEST_PACKAGE);

        // Assert that we timeout (future should not complete, since we should not be silenced)
        assertThrows(TimeoutException.class, () -> future.get(FALSE_NEG_SECS, TimeUnit.SECONDS));
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testMovingFromTopToForegroundServiceWithoutMicCapabilities_isSilenced()
            throws Exception {
        final var TEST_PACKAGE = API_34_NO_CAP_PACKAGE;
        // Start an activity, then start recording in a fgs WITHOUT mic caps
        startActivity(TEST_PACKAGE);
        startForeground(TEST_PACKAGE);
        assumeTrue(startServiceRecording(TEST_PACKAGE));
        // Prime future that the stream is silenced
        final var future = makeFuture(TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity(TEST_PACKAGE);

        // Future is completes when silenced. If not, timeout and throw
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
    }

    @AsbSecurityTest(cveBugId = 268724205)
    @Test
    public void testIfTargetPre34_MovingFromTopToBackground_isNotSilenced() throws Exception {
        final var TEST_PACKAGE = API_33_PACKAGE;
        // Start an activity, then start recording in a background service
        startActivity(TEST_PACKAGE);
        assumeTrue(startServiceRecording(TEST_PACKAGE));
        // Prime future that the stream is silenced
        final var future = makeFuture(TEST_PACKAGE + ACTION_BEGAN_RECEIVE_SILENCE);

        // Move out of TOP to a service state
        stopActivity(TEST_PACKAGE);

        // Assert that we timeout (future should not complete, since we should not be silenced)
        assertThrows(TimeoutException.class, () -> future.get(FALSE_NEG_SECS, TimeUnit.SECONDS));
    }

    @Test
    public void testRecordAudioNoRuntimePermission_fails() throws Exception {
        assertThrows(UnsupportedOperationException.class, this::buildRecord);
        runWithShellPermissionIdentity(this::buildRecord, Manifest.permission.RECORD_AUDIO);
    }

    private void buildRecord() throws Exception {
        new AudioRecord.Builder()
                .setAudioFormat(
                        new AudioFormat.Builder()
                                .setSampleRate(48000)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build())
                .build();
    }

    private void startForeground(String packageName) {
        mContext.startService(getIntentForAction(packageName, ACTION_START_FOREGROUND));
    }

    private void startActivity(String packageName) throws Exception {
        final var future = makeFuture(packageName + ACTION_ACTIVITY_STARTED);
        final var intent =
                new Intent(Intent.ACTION_MAIN)
                        .setClassName(packageName, packageName + ".SimpleActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mInstrumentation.getTargetContext().startActivity(intent);
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        SystemUtil.runShellCommand(mInstrumentation, "am unfreeze --sticky " + packageName);
        mActivityStartedPackages.add(packageName);
    }

    private void stopActivity(String packageName) throws Exception {
        final var future = makeFuture(packageName + ACTION_ACTIVITY_FINISHED);
        mContext.sendBroadcast(
                new Intent(packageName + ACTION_ACTIVITY_DO_FINISH).setPackage(packageName));
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        mActivityStartedPackages.remove(packageName);
    }

    // return true iff track starts unsilenced
    private boolean startServiceRecording(String packageName) throws Exception {
        final var future =
                getFutureForIntent(
                        mContext,
                        List.of(
                                packageName + ACTION_BEGAN_RECEIVE_AUDIO,
                                packageName + ACTION_BEGAN_RECEIVE_SILENCE),
                        x -> true);

        final Intent intent = getIntentForAction(packageName, ACTION_START_RECORD);

        mContext.startService(intent);
        final var result =
                future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS)
                        .getAction()
                        .equals(packageName + ACTION_BEGAN_RECEIVE_AUDIO);
        mServiceStartedPackages.add(packageName);
        SystemUtil.runShellCommand(mInstrumentation, "am unfreeze --sticky " + packageName);
        return result;
    }

    private Future makeFuture(String action) {
        return getFutureForIntent(mContext, action);
    }

    private Intent getIntentForAction(String packageName, String action) {
        return new Intent()
                .setClassName(packageName, packageName + SERVICE_NAME)
                .setAction(packageName + action);
    }

    private void stopService(String packageName) throws Exception {
        final var future = makeFuture(packageName + ACTION_FINISH_TEARDOWN);
        mContext.startService(getIntentForAction(packageName, ACTION_TEARDOWN));
        future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        mServiceStartedPackages.remove(packageName);
        // Just in case
        mInstrumentation
                .getTargetContext()
                .stopService(new Intent().setClassName(packageName, packageName + SERVICE_NAME));
    }
}
