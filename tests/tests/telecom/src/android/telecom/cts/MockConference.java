/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.net.Uri;
import android.os.Bundle;
import android.telecom.AudioState;
import android.telecom.CallAudioState;
import android.telecom.CallEndpoint;
import android.telecom.Conference;
import android.telecom.Conferenceable;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConference;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link Conference} subclass that immediately performs any state changes that are a result of
 * callbacks sent from Telecom.
 */
public class MockConference extends Conference {

    private RemoteConference mRemoteConference = null;
    private String mDtmfString = "";
    public TestUtils.InvokeCounter mOnExtrasChanged =
            new TestUtils.InvokeCounter("onExtrasChanged");
    public TestUtils.InvokeCounter mOnCallAudioStateChanged =
            new TestUtils.InvokeCounter("mOnCallAudioStateChanged");
    public TestUtils.InvokeCounter mCurrentCallEndpoint =
            new TestUtils.InvokeCounter("mCurrentCallEndpoint");
    public List<Uri> mParticipants = new ArrayList<>();
    public CompletableFuture<Void> mLock = new CompletableFuture<>();
    private int mVideoState = VideoProfile.STATE_AUDIO_ONLY;
    private LinkedBlockingQueue<Conferenceable> mOnMergeQueue;
    private LinkedBlockingQueue<Connection> mOnConnectionAddedQueue;
    private LinkedBlockingQueue<Boolean> mOnMuteSateChanged;

    public MockConference(PhoneAccountHandle phoneAccount) {
        super(phoneAccount);
    }

    public MockConference(MockConnection a, MockConnection b) {
        super(a.getMockPhoneAccountHandle());
        addConnection(a);
        addConnection(b);
        // a is the primary connection, so inherit the properties from it.
        setConnectionCapabilities(a.getConnectionCapabilities());
        setStatusHints(a.getStatusHints());
        setExtras(a.getExtras());
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        for (Connection c : getConnections()) {
            c.setDisconnected(new DisconnectCause(DisconnectCause.REMOTE));
            c.destroy();
        }
        destroy();
        if (mRemoteConference != null) {
            mRemoteConference.disconnect();
        }
    }

    @Override
    public void onReject() {
        super.onReject();
        for (Connection c : getConnections()) {
            c.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
            c.destroy();
        }
        destroy();
        if (mRemoteConference != null) {
            mRemoteConference.disconnect();
        }
        mLock.complete(null);
    }

    @Override
    public void onSeparate(Connection connection) {
        super.onSeparate(connection);
        if (getConnections().contains(connection)) {
            removeConnection(connection);
        }
        if (mRemoteConference != null) {
            mRemoteConference.separate(((MockConnection)connection).getRemoteConnection());
        }
    }

    @Override
    public void onMerge() {
        super.onMerge();
        // Let's just change the connection display name for testing that onMerge was invoked.
        for (Connection c : getConnections()) {
            c.setCallerDisplayName(
                    TestUtils.MERGE_CALLER_NAME, TelecomManager.PRESENTATION_ALLOWED);
        }
        if (mRemoteConference != null) {
            mRemoteConference.merge();
        }
    }

    @Override
    public void onMerge(Connection connection) {
        super.onMerge(connection);
        if (mOnMergeQueue != null) {
            mOnMergeQueue.offer(connection);
        }
    }

    /**
     * Use this to provide a queue which will receive the results of {@link #onMerge(Conference)}
     * for this {@link Conference}.
     *
     * @param onMergeQueue the queue.
     */
    public void setOnMergeQueue(LinkedBlockingQueue<Conferenceable> onMergeQueue) {
        this.mOnMergeQueue = onMergeQueue;
    }

    @Override
    public void onConnectionAdded(Connection connection) {
        super.onConnectionAdded(connection);
        if (mOnConnectionAddedQueue != null) {
            mOnConnectionAddedQueue.offer(connection);
        }
    }

    /**
     * Use to provide a queue which will receive the results of {@link
     * #onConnectionAdded(Connection)}.
     *
     * @param queue The queue.
     */
    public void setOnConnectionAddedQueue(LinkedBlockingQueue<Connection> queue) {
        mOnConnectionAddedQueue = queue;
    }

    /**
     * Notifies the {@link Conference} that its audio mute state has been changed.
     *
     * @param isMuted The new mute state.
     */
    @Override
    public void onMuteStateChanged(boolean isMuted) {
        super.onMuteStateChanged(isMuted);
        if (mOnMuteSateChanged != null) {
            mOnMuteSateChanged.offer(isMuted);
        }
    }

    public void setOnMuteSateChangedQueue(LinkedBlockingQueue<Boolean> queue) {
        mOnMuteSateChanged = queue;
    }

    /***
     * Handles incoming requests to merge this {@link Conference} with another {@link Conference} by
     * offering the conference to the mOnMergeQueue if one was provided.
     * @param mergeWith The {@code Conference} to merge.
     */
    @Override
    public void onMerge(Conference mergeWith) {
        super.onMerge(mergeWith);
        if (mOnMergeQueue != null) {
            mOnMergeQueue.offer(mergeWith);
        }
    }

    @Override
    public void onSwap() {
        super.onSwap();
        // Let's just change the connection display name for testing that onSwap was invoked.
        for (Connection c : getConnections()) {
            c.setCallerDisplayName(
                    TestUtils.SWAP_CALLER_NAME, TelecomManager.PRESENTATION_ALLOWED);
        }
        if (mRemoteConference != null) {
            mRemoteConference.swap();
        }
    }

    @Override
    public void onHold() {
        super.onHold();
        for (Connection c : getConnections()) {
            c.setOnHold();
        }
        setOnHold();
        if (mRemoteConference != null) {
            mRemoteConference.hold();
        }
    }

    @Override
    public void onUnhold() {
        super.onUnhold();
        for (Connection c : getConnections()) {
            c.setActive();
        }
        setActive();
        if (mRemoteConference != null) {
            mRemoteConference.unhold();
        }
    }

    @Override
    public void onPlayDtmfTone(char c) {
        super.onPlayDtmfTone(c);
        mDtmfString += c;
        if (mRemoteConference != null) {
            mRemoteConference.playDtmfTone(c);
        }
    }

    @Override
    public void onStopDtmfTone() {
        super.onStopDtmfTone();
        mDtmfString += ".";
        if (mRemoteConference != null) {
            mRemoteConference.stopDtmfTone();
        }
    }

    @Override
    public void onAddConferenceParticipants(List<Uri> participants) {
        super.onAddConferenceParticipants(participants);
        mParticipants.addAll(participants);
        mLock.complete(null);
    }

    @Override
    public void onAnswer(int videoState) {
        super.onAnswer(videoState);
        mVideoState = videoState;
        mLock.complete(null);
    }

    // Deprecated and not used, pass through for coverage.
    @Override
    public void onAudioStateChanged(AudioState state) {
        super.onAudioStateChanged(state);
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState state) {
        super.onCallAudioStateChanged(state);
        mOnCallAudioStateChanged.invoke(state);
    }

    // Note: Note possible to test this as we can't change the available endpoints in CTS.
    @Override
    public void onAvailableCallEndpointsChanged(List<CallEndpoint> availableEndpoints) {
        super.onAvailableCallEndpointsChanged(availableEndpoints);
    }

    public void setRemoteConference(RemoteConference remoteConference) {
        mRemoteConference = remoteConference;
        Bundle bundle = remoteConference.getExtras();
        if (bundle != null) {
            this.putExtras(bundle);
        }
    }

    public RemoteConference getRemoteConference() {
        return mRemoteConference;
    }

    public String getDtmfString() {
        return mDtmfString;
    }

    public int getVideoState() {
        return mVideoState;
    }

    @Override
    public void onExtrasChanged(Bundle extras) {
        super.onExtrasChanged(extras);
        setExtras(extras);
        mOnExtrasChanged.invoke(extras);
    }

    @Override
    public void onCallEndpointChanged(CallEndpoint callEndpoint) {
        super.onCallEndpointChanged(callEndpoint);
        mCurrentCallEndpoint.invoke(callEndpoint);
    }

    public boolean acquireLock(long time, TimeUnit unit) {
        try {
            mLock.get(time, unit);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return false;
        }
        return true;
    }

    public void resetLock() {
        mLock = new CompletableFuture<>();
    }
}
