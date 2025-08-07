/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.telecom.cts;

import static android.telecom.CallAttributes.DIRECTION_OUTGOING;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.telecom.Call;
import android.telecom.CallAttributes;
import android.telecom.CallControl;
import android.telecom.CallControlCallback;
import android.telecom.CallEndpoint;
import android.telecom.CallEventCallback;
import android.telecom.CallException;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StreamingCall;
import android.telecom.cts.streamingtestapp.CtsCallStreamingService;
import android.telecom.cts.streamingtestapp.ICtsCallStreamingServiceControl;
import android.util.Log;

import com.android.server.telecom.flags.Flags;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CallStreamingTest extends BaseTelecomTestWithMockServices {
    private static final String TAG = CallStreamingTest.class.getSimpleName();
    private static final String CALL_CHANNEL_ID = "test_calls_not";
    private static final int NOTIFICATION_ID = 1;
    private static final String TEL_CLEAN_STUCK_CALLS_CMD = "telecom cleanup-stuck-calls";
    private static final long ASYNC_TIMEOUT_MS = 5000L;
    private static final String FAKE_INTENT_ACTION = "action new t-call";
    private static final ComponentName TEST_COMPONENT_NAME =
            new ComponentName(TestUtils.PACKAGE, TestUtils.PACKAGE);
    private static final String TEST_DISPLAY_NAME = "Test User";
    private static final Uri TEST_ADDRESS = Uri.parse("tel:123-456-7890");
    private static final String TEST_EXTRA_KEY = "test_extra_key";
    private static final String TEST_EXTRA_VALUE = "test_extra_value";
    // Callback identifiers
    private static final String ON_SET_ACTIVE = "OnSetActive";
    private static final String ON_SET_INACTIVE = "OnSetInactive";
    private static final String ON_DISCONNECT = "OnDisconnect";
    private static final String CALL_STREAMING_PACKAGE_NAME =
            "android.telecom.cts.streamingtestapp";
    private static final PhoneAccountHandle ACCOUNT_HANDLE =
            new PhoneAccountHandle(new ComponentName(TestUtils.PACKAGE, TestUtils.PACKAGE), TAG);
    private static final PhoneAccount ACCOUNT =
            PhoneAccount.builder(ACCOUNT_HANDLE, TestUtils.ACCOUNT_LABEL)
                    .setCapabilities(PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS)
                    .build();
    private CtsRoleManagerAdapter mCtsRoleManagerAdapter;
    private String mPreviousRoleHolder;
    private NotificationManager mNotificationManager;
    private ICtsCallStreamingServiceControl mStreamingServiceControl;
    private ServiceConnection mServiceConnection;
    private StreamingCallSession mCurrentSession;

    /**
     * Helper class to manage a single transactional call session, simplifying test logic. It
     * encapsulates call setup, control, and asynchronous callbacks.
     */
    private class StreamingCallSession {
        private final String mCallId;
        private CallControl mCallControl;
        private final Map<String, CountDownLatch> mCallbackLatches = new ConcurrentHashMap<>();

        StreamingCallSession(String callId) {
            mCallId = callId;
        }

        private final CallControlCallback mCallControlCallback =
                new CallControlCallback() {
                    @Override
                    public void onSetActive(Consumer<Boolean> wasCompleted) {
                        Log.i(TAG, "CallControlCallback#onSetActive called");
                        getCallbackLatch(ON_SET_ACTIVE).countDown();
                        wasCompleted.accept(true);
                    }

                    @Override
                    public void onSetInactive(Consumer<Boolean> wasCompleted) {
                        Log.i(TAG, "CallControlCallback#onSetInactive called");
                        getCallbackLatch(ON_SET_INACTIVE).countDown();
                        wasCompleted.accept(true);
                    }

                    @Override
                    public void onAnswer(int videoState, Consumer<Boolean> wasCompleted) {
                        wasCompleted.accept(true);
                    }

                    @Override
                    public void onDisconnect(
                            DisconnectCause cause, Consumer<Boolean> wasCompleted) {
                        Log.i(TAG, "CallControlCallback#onDisconnect called");
                        getCallbackLatch(ON_DISCONNECT).countDown();
                        wasCompleted.accept(true);
                    }

                    @Override
                    public void onCallStreamingStarted(Consumer<Boolean> wasCompleted) {
                        wasCompleted.accept(true);
                    }
                };

        private final CallEventCallback mCallEventCallback =
                new CallEventCallback() {
                    @Override
                    public void onCallEndpointChanged(CallEndpoint newCallEndpoint) {}

                    @Override
                    public void onAvailableCallEndpointsChanged(
                            List<CallEndpoint> availableEndpoints) {}

                    @Override
                    public void onMuteStateChanged(boolean isMuted) {}

                    @Override
                    public void onVideoStateChanged(int videoState) {
                        CallEventCallback.super.onVideoStateChanged(videoState);
                    }

                    @Override
                    public void onCallStreamingFailed(int reason) {}

                    @Override
                    public void onEvent(String event, Bundle extras) {}
                };

        public void addCall(CallAttributes attributes) throws InterruptedException {
            final CountDownLatch latch = new CountDownLatch(1);
            postCallNotification(); // FGS requirement for VoIP apps

            mTelecomManager.addCall(
                    attributes,
                    Runnable::run,
                    new OutcomeReceiver<CallControl, CallException>() {
                        @Override
                        public void onResult(CallControl callControl) {
                            assertNotNull("onResult: callControl must not be null", callControl);
                            mCallControl = callControl;
                            latch.countDown();
                        }

                        @Override
                        public void onError(CallException exception) {
                            fail("addCall failed with exception: " + exception);
                        }
                    },
                    mCallControlCallback,
                    mCallEventCallback);

            assertLatchSucceeds(latch, "Failed to receive onResult for addCall");
        }

        private void invokeCallControlMethod(
                Consumer<OutcomeReceiver<Void, CallException>> method, String failureMessage)
                throws InterruptedException {
            assertNotNull("CallControl must be initialized first", mCallControl);
            final CountDownLatch latch = new CountDownLatch(1);
            method.accept(new LatchedOutcomeReceiver<>(latch));
            assertLatchSucceeds(latch, failureMessage);
        }

        public void startCallStreaming() throws InterruptedException {
            invokeCallControlMethod(
                    outcome -> mCallControl.startCallStreaming(Runnable::run, outcome),
                    "startCallStreaming did not complete successfully");
        }

        public void disconnect() throws InterruptedException {
            if (mCallControl != null) {
                invokeCallControlMethod(
                        outcome ->
                                mCallControl.disconnect(
                                        new DisconnectCause(DisconnectCause.LOCAL),
                                        Runnable::run,
                                        outcome),
                        "disconnect did not complete successfully");
            }
        }

        public void setActive() throws InterruptedException {
            invokeCallControlMethod(
                    outcome -> mCallControl.setActive(Runnable::run, outcome),
                    "setActive did not complete successfully");
        }

        public CountDownLatch getCallbackLatch(String callbackName) {
            return mCallbackLatches.computeIfAbsent(callbackName, k -> new CountDownLatch(1));
        }

        public void resetCallbackLatch(String callbackName) {
            mCallbackLatches.put(callbackName, new CountDownLatch(1));
        }

        public boolean wasDisconnectCallbackCalled() {
            return getCallbackLatch(ON_DISCONNECT).getCount() == 0;
        }

        public ParcelUuid getTelecomCallId() {
            return (mCallControl != null) ? mCallControl.getCallId() : null;
        }
    }

    /** A simple OutcomeReceiver that counts down a latch on result or fails the test on error. */
    private static class LatchedOutcomeReceiver<R, E extends Throwable>
            implements OutcomeReceiver<R, E> {
        private final CountDownLatch mLatch;

        LatchedOutcomeReceiver(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onResult(R result) {
            mLatch.countDown();
        }

        @Override
        public void onError(E exception) {
            fail("OutcomeReceiver received an error: " + exception);
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        if (!mShouldTestTelecom) return;

        mCtsRoleManagerAdapter = new CtsRoleManagerAdapter(getInstrumentation());
        mPreviousRoleHolder =
                mCtsRoleManagerAdapter
                        .getRoleHolder(RoleManager.ROLE_SYSTEM_CALL_STREAMING)
                        .stream()
                        .findFirst()
                        .orElse(null);
        mCtsRoleManagerAdapter.setByPassRoleQualification(true);
        mCtsRoleManagerAdapter.setRoleHolder(RoleManager.ROLE_SYSTEM_CALL_STREAMING,
                CALL_STREAMING_PACKAGE_NAME);

        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        NewOutgoingCallBroadcastReceiver.reset();
        mTelecomManager.registerPhoneAccount(ACCOUNT);
        mStreamingServiceControl = bindToStreamingService();
        configureNotificationChannel();
        cleanup(); // Initial cleanup
    }

    @Override
    public void tearDown() throws Exception {
        if (!mShouldTestTelecom) return;

        cleanup(); // Disconnects active call sessions
        mTelecomManager.unregisterPhoneAccount(ACCOUNT_HANDLE);

        if (mPreviousRoleHolder == null) {
            mCtsRoleManagerAdapter.removeRoleHolder(
                    RoleManager.ROLE_SYSTEM_CALL_STREAMING, CALL_STREAMING_PACKAGE_NAME);
        } else {
            mCtsRoleManagerAdapter.setRoleHolder(
                    RoleManager.ROLE_SYSTEM_CALL_STREAMING, mPreviousRoleHolder);
        }
        mCtsRoleManagerAdapter.setByPassRoleQualification(false);
        if (mServiceConnection != null) {
            mContext.unbindService(mServiceConnection);
        }
        super.tearDown();
    }

    public void testStartCallStreaming() throws Exception {
        if (!mShouldTestTelecom) return;

        setupStreamingCall(
                new CallAttributes.Builder(
                                ACCOUNT_HANDLE,
                                DIRECTION_OUTGOING,
                                "testName",
                                Uri.parse("tel:123-TEST"))
                        .setCallType(CallAttributes.AUDIO_CALL)
                        .build());

        mCurrentSession.startCallStreaming();

        // Verify the streaming service received the call and its details
        Bundle bundle = mStreamingServiceControl.waitForCallAdded();
        assertFalse(
                "Streaming service reported failure",
                bundle.containsKey(CtsCallStreamingService.EXTRA_FAILED));
        Bundle theExtras = bundle.getBundle(CtsCallStreamingService.EXTRA_CALL_EXTRAS);
        assertNotNull("Call extras not found in service bundle", theExtras);

        String extraKey =
                Flags.callDetailsIdChanges()
                        ? StreamingCall.EXTRA_CALL_ID
                        : "android.telecom.extra.CALL_ID";
        assertEquals(
                "StreamingCall has incorrect call ID",
                mCurrentSession.getTelecomCallId().toString(),
                theExtras.getString(extraKey));

        // Confirm audio mode is for communication redirect
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertAudioMode(audioManager, AudioManager.MODE_COMMUNICATION_REDIRECT);
    }

    public void testStreamingCallCreationAndGetters() {
        Bundle extras = new Bundle();
        extras.putString(TEST_EXTRA_KEY, TEST_EXTRA_VALUE);
        StreamingCall call =
                new StreamingCall(TEST_COMPONENT_NAME, TEST_DISPLAY_NAME, TEST_ADDRESS, extras);

        assertNotNull("StreamingCall object should not be null", call);
        assertEquals("Component name does not match", TEST_COMPONENT_NAME, call.getComponentName());
        assertEquals("Display name does not match", TEST_DISPLAY_NAME, call.getDisplayName());
        assertEquals("Address does not match", TEST_ADDRESS, call.getAddress());
        assertEquals(
                "Initial state should be STATE_STREAMING",
                StreamingCall.STATE_STREAMING,
                call.getState());
        assertEquals(
                "Extra value does not match",
                TEST_EXTRA_VALUE,
                call.getExtras().getString(TEST_EXTRA_KEY));
    }

    public void testStreamingCallParcelable() {
        Bundle extras = new Bundle();
        extras.putString(TEST_EXTRA_KEY, TEST_EXTRA_VALUE);
        StreamingCall originalCall =
                new StreamingCall(TEST_COMPONENT_NAME, TEST_DISPLAY_NAME, TEST_ADDRESS, extras);

        Parcel parcel = Parcel.obtain();
        originalCall.writeToParcel(parcel, originalCall.describeContents());
        parcel.setDataPosition(0);
        StreamingCall createdFromParcel = StreamingCall.CREATOR.createFromParcel(parcel);

        assertNotNull("Parcelled StreamingCall object should not be null", createdFromParcel);
        assertEquals(
                "Component name mismatch after parceling",
                originalCall.getComponentName(),
                createdFromParcel.getComponentName());
        assertEquals(
                "Display name mismatch after parceling",
                originalCall.getDisplayName(),
                createdFromParcel.getDisplayName());
        assertEquals(
                "Address mismatch after parceling",
                originalCall.getAddress(),
                createdFromParcel.getAddress());
        assertEquals(
                "State mismatch after parceling",
                originalCall.getState(),
                createdFromParcel.getState());
        assertEquals(
                "Extra value mismatch after parceling",
                TEST_EXTRA_VALUE,
                createdFromParcel.getExtras().getString(TEST_EXTRA_KEY));

        parcel.recycle();
    }

    public void testStreamingCallApi_RequestStateChanges() throws Exception {
        if (!mShouldTestTelecom) return;

        setupStreamingCall(
                new CallAttributes.Builder(
                                ACCOUNT_HANDLE,
                                DIRECTION_OUTGOING,
                                "ApiTest",
                                Uri.parse("tel:555-0101"))
                        .setCallType(CallAttributes.AUDIO_CALL)
                        .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
                        .build());

        mCurrentSession.startCallStreaming();
        mCurrentSession.setActive();
        mStreamingServiceControl.waitForCallAdded();
        assertCallState(getInCallService().getLastCall(), Call.STATE_ACTIVE);

        try {
            // Request HOLD from the streaming device --> verify onSetInactive is called on client.
            mStreamingServiceControl.requestCallStreamingState(StreamingCall.STATE_HOLDING);
            verifyCallbackTriggered(
                    mCurrentSession,
                    ON_SET_INACTIVE,
                    "onSetInactive was not called for STATE_HOLDING");

            // Request STREAMING from the streaming device --> verify onSetActive is called.
            mCurrentSession.resetCallbackLatch(ON_SET_ACTIVE);
            mStreamingServiceControl.requestCallStreamingState(StreamingCall.STATE_STREAMING);
            verifyCallbackTriggered(
                    mCurrentSession,
                    ON_SET_ACTIVE,
                    "onSetActive was not called for STATE_STREAMING");

            // Request DISCONNECT from the streaming device --> verify onDisconnect is called.
            mStreamingServiceControl.requestCallStreamingState(StreamingCall.STATE_DISCONNECTED);
            verifyCallbackTriggered(
                    mCurrentSession,
                    ON_DISCONNECT,
                    "onDisconnect was not called for STATE_DISCONNECTED");
        } catch (RemoteException e) {
            fail("RemoteException while controlling test service: " + e);
        }
    }

    public void testStreamingCallStateConstants() {
        assertEquals("STATE_STREAMING should be 1", 1, StreamingCall.STATE_STREAMING);
        assertEquals("STATE_HOLDING should be 2", 2, StreamingCall.STATE_HOLDING);
        assertEquals("STATE_DISCONNECTED should be 3", 3, StreamingCall.STATE_DISCONNECTED);
    }

    public void testStreamingCallbacks_StoppedAndStateChanged() throws Exception {
        if (!mShouldTestTelecom) return;

        try {
            // Setup the call and start streaming it.
            setupStreamingCall(
                    new CallAttributes.Builder(
                                    ACCOUNT_HANDLE,
                                    DIRECTION_OUTGOING,
                                    "CallbackTest",
                                    Uri.parse("tel:555-0102"))
                            .setCallType(CallAttributes.AUDIO_CALL)
                            .setCallCapabilities(CallAttributes.SUPPORTS_SET_INACTIVE)
                            .build());
            mCurrentSession.startCallStreaming();
            mStreamingServiceControl.waitForCallAdded();

            // Test onCallStreamingStateChanged: setting the call active will trigger the callback.
            mCurrentSession.setActive();
            assertCallState(getInCallService().getLastCall(), Call.STATE_ACTIVE);
            int initialState = mStreamingServiceControl.waitForCallStreamingStateChanged();
            assertEquals(
                    "Streaming service should receive STATE_STREAMING after call becomes active",
                    StreamingCall.STATE_STREAMING,
                    initialState);

            // Test onCallStreamingStopped: Disconnecting stops the stream.
            mCurrentSession.disconnect();

            // Wait for the streaming service to report it was stopped.
            mStreamingServiceControl.waitForCallStreamingStopped();

            // clear the session object so the session is not disconnected in cleanup
            mCurrentSession = null;
        } catch (RemoteException e) {
            fail("RemoteException while controlling test service: " + e);
        }
    }

    // Helper Methods

    private void setupStreamingCall(CallAttributes attributes) throws InterruptedException {
        mCurrentSession = new StreamingCallSession("streaming_call");
        mCurrentSession.addCall(attributes);
    }

    private void cleanup() throws Exception {
        Log.i(TAG, "cleanup: running...");
        if (mCurrentSession != null) {
            // Only disconnect if the test hasn't already requested a disconnect.
            if (!mCurrentSession.wasDisconnectCallbackCalled()) {
                mCurrentSession.disconnect();
            }
            mCurrentSession = null;
        }
        mNotificationManager.cancel(NOTIFICATION_ID);
        if (mInCallCallbacks.getService() != null) {
            mInCallCallbacks.getService().disconnectAllCalls();
            mInCallCallbacks.getService().clearCallList();
        }
        TestUtils.executeShellCommand(getInstrumentation(), TEL_CLEAN_STUCK_CALLS_CMD);
    }

    private ICtsCallStreamingServiceControl bindToStreamingService() throws Exception {
        Intent bindIntent = new Intent(
                android.telecom.cts.streamingtestapp.CtsCallStreamingServiceControl
                        .CONTROL_INTERFACE_ACTION);
        bindIntent.setPackage(CALL_STREAMING_PACKAGE_NAME);
        CompletableFuture<ICtsCallStreamingServiceControl> future = new CompletableFuture<>();
        mServiceConnection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        future.complete(
                                android.telecom.cts.streamingtestapp.ICtsCallStreamingServiceControl
                                        .Stub.asInterface(service));
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        future.complete(null);
                    }
                };

        if (!mContext.bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
            fail("Failed to get control interface -- bind error");
        }
        ICtsCallStreamingServiceControl control =
                future.get(ASYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull("Failed to bind to CtsCallStreamingService", control);
        return control;
    }

    private void assertLatchSucceeds(CountDownLatch latch, String message)
            throws InterruptedException {
        assertTrue(message, latch.await(ASYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private void configureNotificationChannel() {
        NotificationChannel callsChannel =
                new NotificationChannel(
                        CALL_CHANNEL_ID, "Calls", NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(callsChannel);
    }

    private void postCallNotification() {
        Person person = new Person.Builder().setName("Max Powers").build();
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        mContext, 0, new Intent(FAKE_INTENT_ACTION), PendingIntent.FLAG_IMMUTABLE);
        Notification callNot =
                new Notification.Builder(mContext, CALL_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_phone_24dp)
                        .setStyle(Notification.CallStyle.forOngoingCall(person, pendingIntent))
                        .setFullScreenIntent(pendingIntent, true)
                        .build();
        mNotificationManager.notify(NOTIFICATION_ID, callNot);
    }

    private void verifyCallbackTriggered(
            StreamingCallSession call, String callbackName, String message)
            throws InterruptedException {
        assertLatchSucceeds(call.getCallbackLatch(callbackName), message);
        // Reset the latch for the next operation
        call.resetCallbackLatch(callbackName);
    }
}
