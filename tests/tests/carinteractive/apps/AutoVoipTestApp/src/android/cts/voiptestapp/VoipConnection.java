/*
 * Copyright 2026 The Android Open Source Project
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
package android.cts.voiptestapp;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.util.Log;

/** {@link Connection} with extensions for VOIP call functionality. */
public class VoipConnection extends Connection {

    private static final String TAG = "VTA.VoipConnection";

    /**
     * String value (name of current speaker). Null will not be displayed, empty strings will be
     * indicative of no current speaker but that the app still wishes to display speaker info.
     */
    private static final String EXTRA_CURRENT_SPEAKER = "android.telecom.extra.CURRENT_SPEAKER";

    /**
     * Integer value. Null values will not be displayed, values >= 0 will be shown by supported
     * UI’s.
     */
    private static final String EXTRA_PARTICIPANT_COUNT = "android.telecom.extra.PARTICIPANT_COUNT";

    /**
     * URI value for an image to be displayed to represent the current call (overrides contact image
     * in Auto). Supported URI types will be resource URI’s and content provider URI’s.
     */
    private static final String EXTRA_CALL_IMAGE_URI = "android.telecom.extra.CALL_IMAGE_URI";

    /** Extra associated with the {@code int} version number. */
    private static final String EXTRA_VOIP_API_VERSION = "android.telecom.extra.VOIP_API_VERSION";

    /**
     * Version number of the VOIP call added API's to allow InCallService code to support future
     * updates.
     */
    private static final int VOIP_API_VERSION = 1;

    private Context mContext;

    public VoipConnection(Context context) {
        setApiVersion();
        setConnectionProperties(PROPERTY_SELF_MANAGED);
        setCallerDisplayName(
                context.getString(R.string.caller_name), TelecomManager.PRESENTATION_ALLOWED);
        setConnectionCapabilities(CAPABILITY_SUPPORT_HOLD | CAPABILITY_MUTE | CAPABILITY_HOLD);
        mContext = context;
    }

    @Override
    public void onShowIncomingCallUi() {
        Log.i(TAG, "onShowIncomingCallUi");
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState state) {
        Log.i(TAG, "onCallAudioStateChanged");
    }

    @Override
    public void onHold() {
        Log.i(TAG, "onHold");
        setOnHold();
    }

    @Override
    public void onUnhold() {
        Log.i(TAG, "onUnhold");
        setActive();
    }

    @Override
    public void onAnswer() {
        Log.i(TAG, "onAnswer");
        VoipCallManager.getInstance(mContext).stopNotificationService();
        setActive();
    }

    @Override
    public void onReject() {
        Log.i(TAG, "onReject");
        VoipCallManager.getInstance(mContext).stopNotificationService();
        onDisconnect();
    }

    @Override
    public void onDisconnect() {
        Log.i(TAG, "onDisconnect");
        setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        destroy();
    }

    /** Sets the current speaker for the call. */
    public void setCurrentSpeaker(String currentSpeaker) {
        setApiVersion();
        Bundle extras = new Bundle();
        extras.putString(EXTRA_CURRENT_SPEAKER, currentSpeaker);
        putExtras(extras);
    }

    /** Removes current speaker, the speaker will not be displayed in call UI's. */
    public void clearCurrentSpeaker() {
        removeExtras(EXTRA_CURRENT_SPEAKER);
    }

    /** Sets the participant count for the call. */
    public void setParticipantCount(int participantCount) {
        setApiVersion();
        Bundle extras = new Bundle();
        extras.putInt(EXTRA_PARTICIPANT_COUNT, participantCount);
        putExtras(extras);
    }

    /**
     * Clears the participant count for the call, the participant count will not be displayed in any
     * call UI's.
     */
    public void clearParticipantCount() {
        removeExtras(EXTRA_PARTICIPANT_COUNT);
    }

    /**
     * Sets the call image {@link Uri}. Supported URI types are resource URI’s and content provider
     * URI’s
     */
    public void setCallImageUri(Uri callImageUri) {
        setApiVersion();
        Bundle extras = new Bundle();
        extras.putParcelable(EXTRA_CALL_IMAGE_URI, callImageUri);
        putExtras(extras);
    }

    /**
     * Clears the call image {@link Uri}. Surfaces showing the call will use a default image where
     * needed.
     */
    public void clearCallImageUri() {
        removeExtras(EXTRA_CALL_IMAGE_URI);
    }

    private void setApiVersion() {
        if (getExtras() != null
                && getExtras().getInt(EXTRA_VOIP_API_VERSION, -1) == VOIP_API_VERSION) {
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putInt(EXTRA_VOIP_API_VERSION, VOIP_API_VERSION);
        putExtras(bundle);
    }

    /** Sets the current speaker and number of participants of a voip connection */
    public void updateSpeakerAndParticipants(String speaker, int participants) {
        Log.i(TAG, "updateSpeakerAndParticipants: " + speaker + " : " + participants);
        Bundle moreExtras = new Bundle();
        setCurrentSpeaker(speaker);
        setParticipantCount(participants);
        // setCallImageUri(Uri.parse(CALLER_IMAGE_URL));
        putExtras(moreExtras);
    }
}
