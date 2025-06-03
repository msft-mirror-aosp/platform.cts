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

package android.telephony.satellite.cts;

import static android.telephony.TelephonyManager.EXTRA_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE;
import static android.telephony.TelephonyManager.EXTRA_EMERGENCY_CALL_TO_SATELLITE_LAUNCH_INTENT;
import static android.telephony.satellite.SatelliteManager.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS;
import static android.telephony.satellite.SatelliteManager.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;

import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telecom.Call;
import android.telecom.cts.TestUtils;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.InCallServiceStateValidator;
import android.telephony.cts.InCallServiceStateValidator.InCallServiceCallbacks;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.ims.cts.ImsServiceConnector;
import android.telephony.ims.cts.TestImsCallSessionImpl;
import android.telephony.ims.cts.TestImsService;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Base class for SatelliteImsCall test. */
public class SatelliteImsCallingBase extends CarrierRoamingSatelliteTestBase {
    private static final String LOG_TAG = "SatelliteImsCallingBase";

    protected static final String PACKAGE = "android.telephony.satellite.cts";
    protected static final String PACKAGE_CTS_DIALER = "android.telephony.cts";
    protected static final String COMMAND_SET_DEFAULT_DIALER = "telecom set-default-dialer ";
    protected static final String COMMAND_GET_DEFAULT_DIALER = "telecom get-default-dialer";
    protected static final String INCALL_COMPONENT =
            "android.telephony.cts/.InCallServiceStateValidator";

    // The timeout to wait in current state in milliseconds
    protected static final int WAIT_IN_CURRENT_STATE = 100;

    public static final int WAIT_FOR_SERVICE_TO_UNBOUND = 40000;
    public static final int WAIT_FOR_CONDITION = 3000;
    public static final int WAIT_FOR_CALL_STATE = 10000;
    public static final int WAIT_FOR_CALL_STATE_ACTIVE = 15000;
    public static final int LATCH_INCALL_SERVICE_BOUND = 1;
    public static final int LATCH_INCALL_SERVICE_UNBOUND = 2;
    public static final int LATCH_IS_ON_CALL_ADDED = 3;
    public static final int LATCH_IS_ON_CALL_REMOVED = 4;
    public static final int LATCH_IS_CALL_DIALING = 5;
    public static final int LATCH_IS_CALL_ACTIVE = 6;
    public static final int LATCH_IS_CALL_DISCONNECTING = 7;
    public static final int LATCH_IS_CALL_DISCONNECTED = 8;
    public static final int LATCH_IS_CALL_RINGING = 9;
    public static final int LATCH_IS_CALL_HOLDING = 10;
    public static final int LATCH_EVENT_DISPLAY_EMERGENCY_MESSAGE_RECEIVED = 11;
    public static final int LATCH_MAX = 12;

    protected static ImsServiceConnector sServiceConnector;
    protected static boolean sIsBound = false;
    protected static long sPreviousOptInStatus = 0;
    protected static long sPreviousEn4GMode = 0;
    protected static String sPreviousDefaultDialer;

    protected static final Object mLock = new Object();
    protected InCallServiceCallbacks mServiceCallBack;
    protected Context mContext;
    protected ConcurrentHashMap<String, Call> mCalls = new ConcurrentHashMap<String, Call>();
    protected String mCurrentCallId = null;
    protected static final CountDownLatch[] sLatches = new CountDownLatch[LATCH_MAX];
    private boolean mIsEmergencyCallingSetup = false;
    private Bundle mEventDisplayEmergencyMessageExtras = null;
    protected static final String[] sTestEmergencyNumbers = {
            "5553630", "5553631", "5553632", "5553633", "5553634"
    };
    protected static final Uri[] sTestEmergencyUris = {
            Uri.fromParts("tel", "5553630", null),
            Uri.fromParts("tel", "5553631", null),
            Uri.fromParts("tel", "5553632", null),
            Uri.fromParts("tel", "5553633", null),
            Uri.fromParts("tel", "5553634", null)
    };

    protected static void initializeLatches() {
        synchronized (mLock) {
            for (int i = 0; i < LATCH_MAX; i++) {
                sLatches[i] = new CountDownLatch(1);
            }
        }
    }


    public boolean callingTestLatchCountdown(int latchIndex, int waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // complete == false
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public void countDownLatch(int latchIndex) {
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }

    public interface Condition {
        Object expected();
        Object actual();
    }

    protected void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (Exception e) {
            Log.d(LOG_TAG, "InterruptedException");
        }
    }

    protected void waitUntilConditionIsTrueOrTimeout(
            Condition condition, long timeout, String description) {
        final long start = System.currentTimeMillis();
        while (!Objects.equals(condition.expected(), condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    public static void setUpImsCallingTestEnvironment(int slotId) throws Exception {
        logd(LOG_TAG, "setUpImsCallingTestEnvironment: slotId=" + slotId);
        int subId = SubscriptionManager.getSubscriptionId(slotId);
        // Remove all live ImsServices until after these tests are done
        sServiceConnector.clearAllActiveImsServices(slotId);

        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            // Get the default dialer and save it to restore after test ends.
            sPreviousDefaultDialer = getDefaultDialer(InstrumentationRegistry.getInstrumentation());
            // Set dialer as "android.telephony.cts"
            setDefaultDialer(InstrumentationRegistry.getInstrumentation(), PACKAGE_CTS_DIALER);

            // Get the default Subscription values and save it to restore after test ends.
            sPreviousOptInStatus =
                    sSubscriptionManager.getLongSubscriptionProperty(
                            subId, SubscriptionManager.VOIMS_OPT_IN_STATUS, 0, getContext());
            sPreviousEn4GMode =
                    sSubscriptionManager.getLongSubscriptionProperty(
                            subId,
                            SubscriptionManager.ENHANCED_4G_MODE_ENABLED,
                            0,
                            getContext());
            // Set the new Subscription values
            sSubscriptionManager.setSubscriptionProperty(
                    subId, SubscriptionManager.VOIMS_OPT_IN_STATUS, String.valueOf(1));
            sSubscriptionManager.setSubscriptionProperty(
                    subId, SubscriptionManager.ENHANCED_4G_MODE_ENABLED, String.valueOf(1));

            // Override the carrier configurations
            CarrierConfigManager configurationManager =
                    InstrumentationRegistry.getInstrumentation()
                            .getContext()
                            .getSystemService(CarrierConfigManager.class);
            PersistableBundle bundle = new PersistableBundle(1);
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true);
            bundle.putBoolean(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, true);
            bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, false);
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL, true);
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_IMS_GBA_REQUIRED_BOOL, false);
            overrideCarrierConfig(subId, bundle);
        } finally {
            ui.dropShellPermissionIdentity();
        }
    }

    public static void cleanUpImsCallingTestEnvironment(int slotId) throws Exception {
        logd(LOG_TAG, "cleanUpImsCallingTestEnvironment: slotId=" + slotId);
        int subId = SubscriptionManager.getSubscriptionId(slotId);
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            // Set the default Subscription values.
            sSubscriptionManager.setSubscriptionProperty(
                    subId,
                    SubscriptionManager.VOIMS_OPT_IN_STATUS,
                    String.valueOf(sPreviousOptInStatus));
            sSubscriptionManager.setSubscriptionProperty(
                    subId,
                    SubscriptionManager.ENHANCED_4G_MODE_ENABLED,
                    String.valueOf(sPreviousEn4GMode));
            // Set default dialer
            setDefaultDialer(InstrumentationRegistry.getInstrumentation(), sPreviousDefaultDialer);

            // Restore all ImsService configurations that existed before the test.
            if (sServiceConnector != null && sIsBound) {
                logd(LOG_TAG, "clearAllActiveImsServices");
                sServiceConnector.clearAllActiveImsServices(slotId);
                logd(LOG_TAG, "disconnectServices");
                sServiceConnector.disconnectServices();
                sIsBound = false;
            }
            overrideCarrierConfig(subId, null);
        } finally {
            ui.dropShellPermissionIdentity();
        }
    }

    public void bindImsService(int slotId) throws Exception {
        bindImsService(slotId, ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
    }

    public void bindImsService(int slotId, int radioTech) throws Exception {
        MmTelCapabilities capabilities =
                new MmTelCapabilities(MmTelCapabilities.CAPABILITY_TYPE_VOICE);
        // Set Registered and VoLTE capable
        bindImsServiceForCapabilities(slotId, radioTech, capabilities);
    }

    public void bindImsServiceForCapabilities(
        int slotId, int radioTech, MmTelCapabilities capabilities) throws Exception {
        logd(LOG_TAG, "bindImsServiceForCapabilities: slotId=" + slotId
            + ", radioTech=" + radioTech + ", capabilities=" + capabilities);
        int subId = SubscriptionManager.getSubscriptionId(slotId);
        // Connect to the ImsService with the MmTel feature.
        assertTrue(
                sServiceConnector.connectCarrierImsService(
                        new ImsFeatureConfiguration.Builder()
                                .addFeature(slotId, ImsFeature.FEATURE_MMTEL)
                                .addFeature(slotId, ImsFeature.FEATURE_EMERGENCY_MMTEL)
                                .build()));
        sIsBound = true;
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        sServiceConnector
                .getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_CREATE_MMTEL);
        assertNotNull(
                "ImsService created, but ImsService#createMmTelFeature was not called!",
                sServiceConnector.getCarrierService().getMmTelFeature());

        sServiceConnector
                .getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_MMTEL_CAP_SET);

        // Set Registered with given capabilities
        sServiceConnector
                .getCarrierService()
                .getImsService()
                .getRegistrationForSubscription(slotId, subId)
                .onRegistered(radioTech);
        sServiceConnector.getCarrierService().getMmTelFeature().setCapabilities(capabilities);
        sServiceConnector
                .getCarrierService()
                .getMmTelFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        // Wait a second for the notifyCapabilitiesStatusChanged indication to be processed on the
        // main telephony thread - currently no better way of knowing that telephony has processed
        // this command. SmsManager#isImsSmsSupported() is @hide and must be updated to use new API.
        TimeUnit.MILLISECONDS.sleep(3000);
    }

    public void waitForUnboundService() {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        InCallServiceStateValidator inCallService = mServiceCallBack.getService();
                        return (inCallService.isServiceUnBound()) ? true : false;
                    }
                },
                WAIT_FOR_SERVICE_TO_UNBOUND,
                "Service Unbound");
    }

    public void isCallActive(Call call, TestImsCallSessionImpl callsession) {
        if (call.getDetails().getState() != Call.STATE_ACTIVE) {
            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_ACTIVE, WAIT_FOR_CALL_STATE));
        }
        assertNotNull("Unable to get callSession, its null", callsession);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (callsession.isInCall()
                                        && call.getDetails().getState() == Call.STATE_ACTIVE)
                                ? true
                                : false;
                    }
                },
                WAIT_FOR_CONDITION,
                "Call Active");
    }

    public void isCallDisconnected(Call call, TestImsCallSessionImpl callsession) {
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTED, WAIT_FOR_CALL_STATE));
        assertNotNull("Unable to get callSession, its null", callsession);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (callsession.isInTerminated()
                                        && call.getDetails().getState() == Call.STATE_DISCONNECTED)
                                ? true
                                : false;
                    }
                }, WAIT_FOR_CONDITION,
                "session " + callsession.getState() + ", call "
                        + call.getDetails().getState() + ", Call Disconnected");
    }

    public void isCallHolding(Call call, TestImsCallSessionImpl callsession) {
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertNotNull("Unable to get callSession, its null", callsession);
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (callsession.isSessionOnHold()
                                && call.getDetails().getState() == Call.STATE_HOLDING) ? true
                                : false;
                    }
                }, WAIT_FOR_CONDITION, "Call Holding");
    }

    protected void setCallID(String callid) {
        assertNotNull("Call Id is set to null", callid);
        mCurrentCallId = callid;
    }

    public void addCall(Call call) {
        String callid = getCallId(call);
        setCallID(callid);
        synchronized (mCalls) {
            mCalls.put(callid, call);
        }
    }

    public String getCallId(Call call) {
        String str = call.toString();
        String[] arrofstr = str.split(",", 3);
        int index = arrofstr[0].indexOf(":");
        String callId = arrofstr[0].substring(index + 1);
        return callId;
    }

    public Call getCall(String callId) {
        synchronized (mCalls) {
            if (mCalls.isEmpty()) {
                return null;
            }

            for (Map.Entry<String, Call> entry : mCalls.entrySet()) {
                if (entry.getKey().equals(callId)) {
                    Call call = entry.getValue();
                    assertNotNull("Call is not added, its null", call);
                    return call;
                }
            }
        }
        return null;
    }

    protected void removeCall(Call call) {
        if (mCalls.isEmpty()) {
            return;
        }

        String callid = getCallId(call);
        Map.Entry<String, Call>[] entries = mCalls.entrySet().toArray(new Map.Entry[mCalls.size()]);
        for (Map.Entry<String, Call> entry : entries) {
            if (entry.getKey().equals(callid)) {
                mCalls.remove(entry.getKey());
                mCurrentCallId = null;
            }
        }
    }

    protected class ServiceCallBack extends InCallServiceCallbacks {

        @Override
        public void onCallAdded(Call call, int numCalls) {
            Log.i(LOG_TAG, "onCallAdded, Call: " + call + ", Num Calls: " + numCalls);
            addCall(call);
            countDownLatch(LATCH_IS_ON_CALL_ADDED);
        }

        @Override
        public void onCallRemoved(Call call, int numCalls) {
            Log.i(LOG_TAG, "onCallRemoved, Call: " + call + ", Num Calls: " + numCalls);
            removeCall(call);
            countDownLatch(LATCH_IS_ON_CALL_REMOVED);
        }

        @Override
        public void onCallStateChanged(Call call, int state) {
            Log.i(LOG_TAG, "onCallStateChanged " + state + "Call: " + call);

            switch (state) {
                case Call.STATE_DIALING:
                    countDownLatch(LATCH_IS_CALL_DIALING);
                    break;
                case Call.STATE_ACTIVE:
                    countDownLatch(LATCH_IS_CALL_ACTIVE);
                    break;
                case Call.STATE_DISCONNECTING:
                    countDownLatch(LATCH_IS_CALL_DISCONNECTING);
                    break;
                case Call.STATE_DISCONNECTED:
                    countDownLatch(LATCH_IS_CALL_DISCONNECTED);
                    break;
                case Call.STATE_RINGING:
                    countDownLatch(LATCH_IS_CALL_RINGING);
                    break;
                case Call.STATE_HOLDING:
                    countDownLatch(LATCH_IS_CALL_HOLDING);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onChildrenChanged(Call call, List<Call> children) {
            Log.i(LOG_TAG, "onChildrenChanged: call=" + call
                + ", numbe of children=" + children.size());
        }

        @Override
        public void onConnectionEvent(Call call, String event, Bundle extras) {
            Log.i(LOG_TAG, "onConnectionEvent, Call: " + call + " , event " + event);
            mEventDisplayEmergencyMessageExtras = null;
            if (event.equals(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE)) {
                logd(LOG_TAG, "onConnectionEvent: EVENT_DISPLAY_EMERGENCY_MESSAGE received");
                countDownLatch(LATCH_EVENT_DISPLAY_EMERGENCY_MESSAGE_RECEIVED);
                mEventDisplayEmergencyMessageExtras = extras;
            }
        }
    }

    protected static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    /** Checks whether the system feature is supported. */
    protected static boolean hasFeature(String feature) {
        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(feature)) {
            Log.d(LOG_TAG, "Skipping test that requires " + feature);
            return false;
        }
        return true;
    }

    protected static String setDefaultDialer(Instrumentation instrumentation, String packageName)
            throws Exception {
        String str =
                TelephonyUtils.executeShellCommand(
                        instrumentation, COMMAND_SET_DEFAULT_DIALER + packageName);
        return str;
    }

    protected static String getDefaultDialer(Instrumentation instrumentation) throws Exception {
        String str =
                TelephonyUtils.executeShellCommand(instrumentation, COMMAND_GET_DEFAULT_DIALER);
        return str;
    }

    protected void setupForEmergencyCalling(int slotId, String testNumber) throws Exception {
        logd(LOG_TAG, "setupForEmergencyCalling: slotId=" + slotId + ", testNumber=" + testNumber);
        enableCarrierUseImsFirstForEmergency(slotId);
        TestUtils.setSystemDialerOverride(
                InstrumentationRegistry.getInstrumentation(), INCALL_COMPONENT);
        TestUtils.addTestEmergencyNumber(InstrumentationRegistry.getInstrumentation(), testNumber);
        mIsEmergencyCallingSetup = true;
    }

    protected void tearDownEmergencyCalling() throws Exception {
        logd(LOG_TAG, "tearDownEmergencyCalling: mIsEmergencyCallingSetup="
            + mIsEmergencyCallingSetup);
        if (!mIsEmergencyCallingSetup) return;
        mIsEmergencyCallingSetup = false;
        TestUtils.clearSystemDialerOverride(InstrumentationRegistry.getInstrumentation());
        TestUtils.clearTestEmergencyNumbers(InstrumentationRegistry.getInstrumentation());
        TelephonyUtils.endBlockSuppression(InstrumentationRegistry.getInstrumentation());
    }

    private static void enableCarrierUseImsFirstForEmergency(int slotId) throws Exception {
        logd(LOG_TAG, "enableCarrierUseImsFirstForEmergency: slotId=" + slotId);
        int subId = SubscriptionManager.getSubscriptionId(slotId);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_USE_IMS_FIRST_FOR_EMERGENCY_BOOL, true);
        overrideCarrierConfig(subId, bundle);
    }

    protected void verifyHandoverMessage(int handoverType, String packageName, String className,
            String action, String uri, int slotId) {
        logd(LOG_TAG, "verifyHandoverMessage: handoverType=" + handoverType
                + ", packageName=" + packageName + ", className=" + className
                + ", action=" + action + ", uri=" + uri + ", slotId=" + slotId);
        assertNotNull(mEventDisplayEmergencyMessageExtras);

        int receivedHandoverType = mEventDisplayEmergencyMessageExtras.getInt(
                EXTRA_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE);
        assertEquals((long) handoverType, (long) receivedHandoverType);

        PendingIntent receivedPendingIntent = mEventDisplayEmergencyMessageExtras.getParcelable(
                    EXTRA_EMERGENCY_CALL_TO_SATELLITE_LAUNCH_INTENT, PendingIntent.class);
        assertNotNull(receivedPendingIntent);
        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.GET_INTENT_SENDER_INTENT");

            Intent receivedIntent = receivedPendingIntent.getIntent();
            logd(LOG_TAG, "verifyHandoverMessage: receivedIntent=" + receivedIntent);
            assertEquals(packageName, receivedIntent.getComponent().getPackageName());
            assertEquals(className, receivedIntent.getComponent().getClassName());
            assertEquals(action, receivedIntent.getAction());

            if (handoverType == EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911) {
                assertEquals(uri, receivedIntent.getDataString());
                int receivedSlotId = receivedIntent.getIntExtra(
                        TelephonyManager.EXTRA_SIM_SLOT_ID, -1);
                logd(LOG_TAG, "verifyHandoverMessage: receivedSlotId=" + receivedSlotId);
                assertEquals((long) slotId, (long) receivedSlotId);
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        }
    }
}
