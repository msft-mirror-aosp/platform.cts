/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.cts.nfc.multidevice.emulator.service;

import android.content.ComponentName;
import android.content.Intent;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import com.android.cts.nfc.multidevice.emulator.BaseEmulatorActivity;
import com.android.cts.nfc.multidevice.utils.CommandApdu;
import com.android.cts.nfc.multidevice.utils.HceUtils;

import java.util.Arrays;

public abstract class HceService extends HostApduService {
    private static final String TAG = "HceService";

    private static final int STATE_IDLE = 0;
    private static final int STATE_IN_PROGRESS = 1;
    private static final int STATE_FAILED = 2;

    // Variables below only used on main thread
    CommandApdu[] mCommandApdus = null;
    String[] mResponseApdus = null;
    int mApduIndex = 0;
    int mState = STATE_IDLE;
    long mStartTime;

    /**
     * Initializes the service
     *
     * @param commandApdus - list of expected command APDUs
     * @param responseApdus - corresponding list of response APDUs to send
     */
    public HceService(CommandApdu[] commandApdus, String[] responseApdus) {
        mCommandApdus = commandApdus;
        mResponseApdus = responseApdus;
    }

    /** Called when service is deactivated */
    @Override
    public void onDeactivated(int arg0) {
        mApduIndex = 0;
        mState = STATE_IDLE;
    }

    /** Return component name of this service */
    public abstract ComponentName getComponent();

    /** Callback when entire apdu sequence is successfully completed. */
    public void onApduSequenceComplete() {
        Intent completionIntent = new Intent(BaseEmulatorActivity.ACTION_APDU_SEQUENCE_COMPLETE);
        completionIntent.putExtra(BaseEmulatorActivity.EXTRA_COMPONENT, getComponent());
        completionIntent.putExtra(
                BaseEmulatorActivity.EXTRA_DURATION, System.currentTimeMillis() - mStartTime);
        sendBroadcast(completionIntent);
        Log.d(TAG, "Successful APDU sequence. Sent broadcast");
    }

    /**
     * Implementation of processCommandApdu. Verifies correct APDU command is received and sends
     * response. Triggers onApduSequenceComplete if all APDUs are received.
     */
    @Override
    public byte[] processCommandApdu(byte[] arg0, Bundle arg1) {
        Log.d(TAG, "processCommandApdu called");
        if (mState == STATE_FAILED) {
            // Don't accept any more APDUs until deactivated
            return null;
        }

        if (mState == STATE_IDLE) {
            mState = STATE_IN_PROGRESS;
            mStartTime = System.currentTimeMillis();
        }

        if (mApduIndex >= mCommandApdus.length) {
            // Skip all APDUs which aren't supposed to reach us
            return null;
        }

        do {
            if (!mCommandApdus[mApduIndex].isReachable()) {
                mApduIndex++;
            } else {
                break;
            }
        } while (mApduIndex < mCommandApdus.length);

        if (mApduIndex >= mCommandApdus.length) {
            Log.d(TAG, "Ignoring command APDU; protocol complete.");
            // Ignore new APDUs after completion
            return null;
        } else {

            if (!Arrays.equals(
                    HceUtils.hexStringToBytes(mCommandApdus[mApduIndex].getApdu()), arg0)) {
                Log.d(TAG, "Unexpected command APDU: " + HceUtils.getHexBytes("", arg0));
                return null;
            } else {
                // Send corresponding response APDU
                byte[] responseApdu = HceUtils.hexStringToBytes(mResponseApdus[mApduIndex]);
                mApduIndex++;
                if (mApduIndex == mCommandApdus.length) {
                    onApduSequenceComplete();
                }
                return responseApdu;
            }
        }
    }
}
