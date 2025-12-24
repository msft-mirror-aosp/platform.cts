/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.telephony.mockmodem;

import static android.telephony.mockmodem.MockSimService.EF_ICCID;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_DEFAULT;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_MAX;
import static android.telephony.mockmodem.MockVoiceService.MockCallInfo.CALL_TYPE_EMERGENCY;
import static android.telephony.mockmodem.MockVoiceService.MockCallInfo.CALL_TYPE_VOICE;

import android.content.Context;
import android.hardware.radio.config.PhoneCapability;
import android.hardware.radio.config.SimPortInfo;
import android.hardware.radio.config.SimSlotStatus;
import android.hardware.radio.config.SlotPortMapping;
import android.hardware.radio.modem.ImeiInfo;
import android.hardware.radio.sim.CardStatus;
import android.hardware.radio.voice.CdmaSignalInfoRecord;
import android.hardware.radio.voice.LastCallFailCauseInfo;
import android.hardware.radio.voice.UusInfo;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RegistrantList;
import android.telephony.Annotation;
import android.telephony.mockmodem.MockSimService.SimAppData;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import android.os.SystemProperties;

public class MockModemConfigBase implements MockModemConfigInterface {
    // ***** Instance Variables
    private static final int DEFAULT_SLOT_ID = 0;
    private static final int ESIM_SLOT_ID = 1;
    private static final int INVALID_LOGICAL_SLOT_ID = -1;
    private static final int PHYSICAL_SIM_SLOT_INVALID = -1;
    private static final int PORT_INDEX_INVALID = -1;
    //Device Identity array indices
    private static final int DEVICE_ID_IMEI = 0;
    private static final int DEVICE_ID_IMEISV = 1;
    private static final int DEVICE_ID_ESN = 2;
    private static final int DEVICE_ID_MEID = 3;

    // The port index for a Physical SIM (pSIM). pSIMs typically support only one port (Port 0).
    // This is used when configuring the pSIM slot in P+E mode.
    private static final int PSIM_PORT_INDEX = 0;

    // The primary port index (Port 0) for an eSIM.
    // This port is active in all eSIM configurations (P+E, E+E, and eSIM-only).
    private static final int FIRST_ESIM_PORT_INDEX = 0;

    // The secondary port index (Port 1) for an eSIM.
    // This is only relevant/active in MEP configurations (E+E or eSIM-only modes).
    // In standard P+E mode, this port is inactive.
    private static final int SECOND_ESIM_PORT_INDEX = 1;

    // Represents Logical Slot 0 (Phone 0).
    // In P+E mode, this maps to the pSIM. In E+E mode, it maps to one of the eSIM ports.
    private static final int FIRST_ESIM_LOGICAL_SLOT_ID = 0;

    // Represents Logical Slot 1 (Phone 1).
    // This generally maps to the eSIM (either Port 0 or Port 1 depending on the mode).
    private static final int SECOND_ESIM_LOGICAL_SLOT_ID = 1;

    // eSIM MEP (Multiple Enabled Profiles) mode allows a device (User Equipment) to have
    // multiple active eSIM profiles simultaneously.
    // If there is no jointly supported MEP mode, set supported MEP mode to NONE.
    private static final int SUPPORTED_MEP_MODE_NONE = 0;

    // In case of MEP-B, profiles are selected on eSIM ports 0 and higher, with the ISD-R being
    // selectable on any of these eSIM ports.
    private static final int SUPPORTED_MEP_MODE_MEP_B = 3;

    // This system property is set to 1 for eSIM-only devices.
    // For other devices, the value is not set (returning default -1).
    private static final int NUM_SLOTS_PROPERTY = SystemProperties.getInt(
        "ro.telephony.sim_slots.count", -1);
    // TODO(b/477553823): Decouple MockModem slot configuration from physical hardware
    // properties. Currently relies on ro.telephony.sim_slots.count to initialize slot number.
    // Future work should allow explicit configuration independent of the underlying device.
    private static final boolean IS_ESIM_ONLY_DEVICE = NUM_SLOTS_PROPERTY == 1;

    // TODO(b/477541239): Refactor to use structured data (Enum/Record) for slot mapping
    // configuration. Currently using boolean logic to support P+E and E+E modes for immediate
    // MEP validation. Future work will introduce a SimSlotConfig structure to support dynamic
    // mappings (setSimSlotsMapping).
    private enum SimSlotMapping {
        MODE_P_E,        // Physical SIM + eSIM (Default)
        MODE_E_E,        // Dual eSIM (MEP)
        MODE_ESIM_ONLY   // eSIM Only Device
    }
    private SimSlotMapping mCurrentMode;
    private final String mTAG = "MockModemConfigBase";
    private final Handler[] mHandler;
    private Context mContext;
    private int mSubId;
    private int mSimPhyicalId;
    private Object[] mConfigAccess;
    private final Object mSimMappingAccess = new Object();
    private int mNumOfPhysicalSlots = MockModemConfigInterface.MAX_NUM_OF_SIM_SLOT;
    private int mNumOfPhone = MockModemConfigInterface.MAX_NUM_OF_LOGICAL_MODEM;

    // ***** Events
    static final int EVENT_SET_RADIO_POWER = 1;
    static final int EVENT_CHANGE_SIM_PROFILE = 2;
    static final int EVENT_SERVICE_STATE_CHANGE = 3;
    static final int EVENT_SET_SIM_INFO = 4;
    static final int EVENT_CALL_STATE_CHANGE = 5;
    static final int EVENT_CURRENT_CALLS_RESPONSE = 6;
    static final int EVENT_CALL_INCOMING = 7;
    static final int EVENT_RINGBACK_TONE = 8;
    static final int EVENT_SET_SIMUL_CALLING_LOGICAL_SLOTS = 9;
    static final int EVENT_SET_MAX_ACTIVE_VOICE_SUBS = 10;
    static final int EVENT_SET_MULTI_SIM_CONFIG = 11;

    // ***** Modem config values
    private String mBasebandVersion = MockModemConfigInterface.DEFAULT_BASEBAND_VERSION;
    private String[] mImei;
    private String[] mImeiSv;
    private String[] mEsn;
    private String[] mMeid;
    private int[] mImeiType;
    private int mRadioState = MockModemConfigInterface.DEFAULT_RADIO_STATE;
    private byte mNumOfLiveModem = MockModemConfigInterface.DEFAULT_NUM_OF_LIVE_MODEM;
    private PhoneCapability mPhoneCapability = new PhoneCapability();
    private int[] mEnabledLogicalSlots;

    // ***** Sim config values
    private SimSlotStatus[] mSimSlotStatus;
    private CardStatus[] mCardStatus;
    private int[] mLogicalSimIdMap;
    private int[] mFdnStatus;
    private MockSimService[] mSimService;
    private List<SimAppData>[] mSimAppList;

    // **** Voice config values
    private MockVoiceService[] mVoiceService;
    private MockCallControlInfo mCallControlInfo;

    // ***** RegistrantLists
    // ***** IRadioConfig RegistrantLists
    private RegistrantList mNumOfLiveModemChangedRegistrants = new RegistrantList();
    private RegistrantList mPhoneCapabilityChangedRegistrants = new RegistrantList();
    private RegistrantList mSimSlotStatusChangedRegistrants = new RegistrantList();
    private RegistrantList mSimultaneousCallingSupportChangedRegistrants = new RegistrantList();
    // ***** IRadioModem RegistrantLists
    private RegistrantList mBasebandVersionChangedRegistrants = new RegistrantList();
    private RegistrantList[] mDeviceIdentityChangedRegistrants;
    private RegistrantList[] mDeviceImeiInfoChangedRegistrants;
    private RegistrantList mRadioStateChangedRegistrants = new RegistrantList();

    // ***** IRadioSim RegistrantLists
    private RegistrantList[] mCardStatusChangedRegistrants;
    private RegistrantList[] mSimAppDataChangedRegistrants;
    private RegistrantList[] mSimInfoChangedRegistrants;
    private RegistrantList[] mSimIoDataChangedRegistrants;

    // ***** IRadioNetwork RegistrantLists
    private RegistrantList[] mServiceStateChangedRegistrants;

    // ***** IRadioVoice RegistrantLists
    private RegistrantList[] mCallStateChangedRegistrants;
    private RegistrantList[] mCurrentCallsResponseRegistrants;
    private RegistrantList[] mCallIncomingRegistrants;
    private RegistrantList[] mRingbackToneRegistrants;

    public MockModemConfigBase(Context context, int numOfSim, int numOfPhone) {
        mContext = context;
        if (IS_ESIM_ONLY_DEVICE) {
            mNumOfPhysicalSlots = 1;
            Log.i(mTAG, "eSim Only Device: mNumOfPhysicalSlots=1");
        } else {
            if (numOfSim > MockModemConfigInterface.MAX_NUM_OF_SIM_SLOT) {
                throw new IllegalArgumentException("Requested number of SIM slots (" + numOfSim
                        + ") exceeds the maximum allowed ("
                                + MockModemConfigInterface.MAX_NUM_OF_SIM_SLOT + ")");
            }
            mNumOfPhysicalSlots = numOfSim;
            Log.i(mTAG, "Multi Slot Mode: mNumOfPhysicalSlots=" + mNumOfPhysicalSlots);
        }
        mNumOfPhone =
                (numOfPhone > MockModemConfigInterface.MAX_NUM_OF_LOGICAL_MODEM)
                        ? MockModemConfigInterface.MAX_NUM_OF_LOGICAL_MODEM
                        : numOfPhone;
        Log.i(mTAG, "mNumOfPhone: " + mNumOfPhone);
        mConfigAccess = new Object[mNumOfPhone];
        mHandler = new MockModemConfigHandler[mNumOfPhone];

        // Registrants initialization
        // IRadioModem registrants
        mDeviceIdentityChangedRegistrants = new RegistrantList[mNumOfPhone];
        mDeviceImeiInfoChangedRegistrants = new RegistrantList[mNumOfPhone];
        // IRadioSim registrants
        mCardStatusChangedRegistrants = new RegistrantList[mNumOfPhone];
        mSimAppDataChangedRegistrants = new RegistrantList[mNumOfPhone];
        mSimInfoChangedRegistrants = new RegistrantList[mNumOfPhone];
        mSimIoDataChangedRegistrants = new RegistrantList[mNumOfPhone];
        // IRadioNetwork registrants
        mServiceStateChangedRegistrants = new RegistrantList[mNumOfPhone];
        // IRadioVoice registrants
        mCallStateChangedRegistrants = new RegistrantList[mNumOfPhone];
        mCurrentCallsResponseRegistrants = new RegistrantList[mNumOfPhone];
        mCallIncomingRegistrants = new RegistrantList[mNumOfPhone];
        mRingbackToneRegistrants = new RegistrantList[mNumOfPhone];

        // IRadioModem caches
        mImei = new String[mNumOfPhone];
        mImeiSv = new String[mNumOfPhone];
        mEsn = new String[mNumOfPhone];
        mMeid = new String[mNumOfPhone];
        mImeiType = new int[mNumOfPhone];

        // IRadioSim caches
        mCardStatus = new CardStatus[mNumOfPhone];
        mSimSlotStatus = new SimSlotStatus[mNumOfPhysicalSlots];
        mLogicalSimIdMap = new int[mNumOfPhysicalSlots];
        mFdnStatus = new int[mNumOfPhysicalSlots];
        mSimService = new MockSimService[mNumOfPhysicalSlots];
        mSimAppList = (List<SimAppData>[]) new ArrayList[mNumOfPhone];

        // IRadioVoice caches
        mVoiceService = new MockVoiceService[mNumOfPhone];

        // Caches initializtion
        for (int i = 0; i < mNumOfPhone; i++) {
            if (mConfigAccess != null && mConfigAccess[i] == null) {
                mConfigAccess[i] = new Object();
            }

            if (mHandler != null && mHandler[i] == null) {
                mHandler[i] = new MockModemConfigHandler(i);
            }

            if (mDeviceIdentityChangedRegistrants != null
                    && mDeviceIdentityChangedRegistrants[i] == null) {
                mDeviceIdentityChangedRegistrants[i] = new RegistrantList();
            }

            if (mDeviceImeiInfoChangedRegistrants != null
                    && mDeviceImeiInfoChangedRegistrants[i] == null) {
                mDeviceImeiInfoChangedRegistrants[i] = new RegistrantList();
            }

            if (mCardStatusChangedRegistrants != null && mCardStatusChangedRegistrants[i] == null) {
                mCardStatusChangedRegistrants[i] = new RegistrantList();
            }

            if (mSimAppDataChangedRegistrants != null && mSimAppDataChangedRegistrants[i] == null) {
                mSimAppDataChangedRegistrants[i] = new RegistrantList();
            }

            if (mSimInfoChangedRegistrants != null && mSimInfoChangedRegistrants[i] == null) {
                mSimInfoChangedRegistrants[i] = new RegistrantList();
            }

            if (mSimIoDataChangedRegistrants != null && mSimIoDataChangedRegistrants[i] == null) {
                mSimIoDataChangedRegistrants[i] = new RegistrantList();
            }

            if (mServiceStateChangedRegistrants != null
                    && mServiceStateChangedRegistrants[i] == null) {
                mServiceStateChangedRegistrants[i] = new RegistrantList();
            }

            if (mCallStateChangedRegistrants != null && mCallStateChangedRegistrants[i] == null) {
                mCallStateChangedRegistrants[i] = new RegistrantList();
            }

            if (mCurrentCallsResponseRegistrants != null
                    && mCurrentCallsResponseRegistrants[i] == null) {
                mCurrentCallsResponseRegistrants[i] = new RegistrantList();
            }

            if (mCallIncomingRegistrants != null && mCallIncomingRegistrants[i] == null) {
                mCallIncomingRegistrants[i] = new RegistrantList();
            }

            if (mRingbackToneRegistrants != null && mRingbackToneRegistrants[i] == null) {
                mRingbackToneRegistrants[i] = new RegistrantList();
            }

            if (mImei != null && mImei[i] == null) {
                String imei;
                switch (i) {
                    case 0:
                        imei = new String(MockModemConfigInterface.DEFAULT_PHONE1_IMEI);
                        break;
                    case 1:
                        imei = new String(MockModemConfigInterface.DEFAULT_PHONE2_IMEI);
                        break;
                    case 2:
                        imei = new String(MockModemConfigInterface.DEFAULT_PHONE3_IMEI);
                        break;
                    default:
                        imei = new String(MockModemConfigInterface.DEFAULT_PHONE1_IMEI);
                        break;
                }
                mImei[i] = imei;
            }

            if (mImeiSv != null && mImeiSv[i] == null) {
                String imeisv;
                switch (i) {
                    case 0:
                        imeisv = new String(MockModemConfigInterface.DEFAULT_PHONE1_IMEISV);
                        break;
                    case 1:
                        imeisv = new String(MockModemConfigInterface.DEFAULT_PHONE2_IMEISV);
                        break;
                    case 2:
                        imeisv = new String(MockModemConfigInterface.DEFAULT_PHONE3_IMEISV);
                        break;
                    default:
                        imeisv = new String(MockModemConfigInterface.DEFAULT_PHONE1_IMEISV);
                        break;
                }
                mImeiSv[i] = imeisv;
            }

            if (mEsn != null && mEsn[i] == null) {
                String esn;
                switch (i) {
                    case 0:
                        esn = new String(MockModemConfigInterface.DEFAULT_PHONE1_ESN);
                        break;
                    case 1:
                        esn = new String(MockModemConfigInterface.DEFAULT_PHONE2_ESN);
                        break;
                    case 2:
                        esn = new String(MockModemConfigInterface.DEFAULT_PHONE3_ESN);
                        break;
                    default:
                        esn = new String(MockModemConfigInterface.DEFAULT_PHONE1_ESN);
                        break;
                }
                mEsn[i] = esn;
            }

            if (mMeid != null && mMeid[i] == null) {
                String meid;
                switch (i) {
                    case 0:
                        meid = new String(MockModemConfigInterface.DEFAULT_PHONE1_MEID);
                        break;
                    case 1:
                        meid = new String(MockModemConfigInterface.DEFAULT_PHONE2_MEID);
                        break;
                    case 2:
                        meid = new String(MockModemConfigInterface.DEFAULT_PHONE3_MEID);
                        break;
                    default:
                        meid = new String(MockModemConfigInterface.DEFAULT_PHONE1_MEID);
                        break;
                }
                mMeid[i] = meid;
            }

            if (mImeiType != null) {
                int imeiType;
                if (i == 0) {
                    imeiType = ImeiInfo.ImeiType.PRIMARY;
                } else {
                    imeiType = ImeiInfo.ImeiType.SECONDARY;
                }
                mImeiType[i] = imeiType;
            }

            if (mCardStatus != null && mCardStatus[i] == null) {
                mCardStatus[i] = new CardStatus();
            }

            if (mVoiceService != null && mVoiceService[i] == null) {
                mVoiceService[i] = new MockVoiceService(mHandler[i]);
            }
        }

        for (int i = 0; i < mNumOfPhysicalSlots; i++) {
            if (mSimSlotStatus != null && mSimSlotStatus[i] == null) {
                mSimSlotStatus[i] = new SimSlotStatus();
            }

            if (mLogicalSimIdMap != null) {
                mLogicalSimIdMap[i] = i;
            }

            if (mFdnStatus != null) {
                mFdnStatus[i] = 0;
            }

            if (mSimService != null && mSimService[i] == null) {
                mSimService[i] = new MockSimService(mContext, i);
            }
        }

        if (IS_ESIM_ONLY_DEVICE) {
            mCurrentMode = SimSlotMapping.MODE_ESIM_ONLY;
            configureSingleSlotMep();
        } else {
            mCurrentMode = SimSlotMapping.MODE_P_E;
        }

        setDefaultConfigValue();
    }

    /**
     * Configures the MockModem for an eSIM-only device with Multiple Enabled Profiles (MEP).
     *
     * <p>Intended Final State:
     * Physical Slot 0: Active (acting as the sole eSIM).
     * Port 0: Active and mapped to Logical Slot 0 (Phone 0).
     * Port 1: Active and mapped to Logical Slot 1 (Phone 1).
     *
     * This setup allows validating DSDS (Dual SIM Dual Standby) behaviors on hardware
     * that lacks a physical SIM slot.
     */
    private void configureSingleSlotMep() {
        if (mSimService == null || mSimService[0] == null || mNumOfPhone < 2) {
            Log.e(mTAG, "Cannot configure Single Slot MEP Mode");
            return;
        }
        Log.d(mTAG, "Configuring Single Slot MEP Mode mappings");

        MockSimService simService = mSimService[0];
        // Activate both ports on the single physical SIM
        simService.setSlotPortActive(FIRST_ESIM_PORT_INDEX, true);
        simService.setSlotPortActive(SECOND_ESIM_PORT_INDEX, true);
        // Map logical slots to ports
        simService.setLogicalSlotId(FIRST_ESIM_PORT_INDEX, FIRST_ESIM_LOGICAL_SLOT_ID);
        simService.setLogicalSlotId(SECOND_ESIM_PORT_INDEX, SECOND_ESIM_LOGICAL_SLOT_ID);
    }

    public static class SimInfoChangedResult {
        public static final int SIM_INFO_TYPE_MCC_MNC = 1;
        public static final int SIM_INFO_TYPE_IMSI = 2;
        public static final int SIM_INFO_TYPE_ATR = 3;

        public int mSimInfoType;
        public int mEfId;
        public String mAid;

        public SimInfoChangedResult(int type, int efid, String aid) {
            mSimInfoType = type;
            mEfId = efid;
            mAid = aid;
        }

        @Override
        public String toString() {
            return "SimInfoChangedResult:"
                    + " simInfoType="
                    + mSimInfoType
                    + " efId="
                    + mEfId
                    + " aId="
                    + mAid;
        }
    }

    public class MockModemConfigHandler extends Handler {
        private int mLogicalSlotId;

        MockModemConfigHandler(int slotId) {
            mLogicalSlotId = slotId;
        }

        // ***** Handler implementation
        @Override
        public void handleMessage(Message msg) {
            int physicalSimSlot = getSimPhysicalSlotId(mLogicalSlotId);

            // Handle global multi-SIM configuration changes (switching between P+E and E+E).
            // This is processed outside the per-slot synchronization block below because it
            // modifies the global slot mapping and affects state across multiple physical slots.
            if (msg.what == EVENT_SET_MULTI_SIM_CONFIG) {
                Log.d(mTAG, "EVENT_SET_MULTI_SIM_CONFIG: " + msg.arg1);
                if (!IS_ESIM_ONLY_DEVICE) {
                    handleSetMultiEsimConfiguration(msg.arg1 == 1);
                } else {
                    Log.d(mTAG, "Ignoring EVENT_SET_MULTI_SIM_CONFIG in eSIM only mode");
                }
                return;
            }

            synchronized (mConfigAccess[physicalSimSlot]) {
                switch (msg.what) {
                    case EVENT_SET_RADIO_POWER:
                        int state = msg.arg1;
                        if (state >= RADIO_STATE_UNAVAILABLE && state <= RADIO_STATE_ON) {
                            Log.d(
                                    mTAG,
                                    "EVENT_SET_RADIO_POWER: old("
                                            + mRadioState
                                            + "), new("
                                            + state
                                            + ")");
                            if (mRadioState != state) {
                                mRadioState = state;
                                mRadioStateChangedRegistrants.notifyRegistrants(
                                        new AsyncResult(null /* userObj */, mRadioState,
                                                null /* exception */));
                            }
                        } else {
                            Log.e(mTAG, "EVENT_SET_RADIO_POWER: invalid state(" + state + ")");
                            mRadioStateChangedRegistrants.notifyRegistrants(null);
                        }
                        break;
                    case EVENT_CHANGE_SIM_PROFILE:
                        int simprofileid =
                                msg.getData()
                                        .getInt(
                                                "changeSimProfile",
                                                MockSimService.MOCK_SIM_PROFILE_ID_DEFAULT);
                        Log.d(mTAG, "EVENT_CHANGE_SIM_PROFILE: sim profile(" + simprofileid + ")");
                        if (loadSIMCard(mLogicalSlotId, simprofileid)) {
                            updateSimSlotStatus();
                            // Notify IRadioConfigImpl to send UNSOL_SIM_SLOT_STATUS_CHANGED to the
                            // framework. This indicates changes to the physical slots, port states,
                            // or logical-to-physical mappings.
                            mSimSlotStatusChangedRegistrants.notifyRegistrants(
                                    new AsyncResult(null /* userObj */, mSimSlotStatus,
                                            null /* exception */));

                            updateCardStatus(mLogicalSlotId);
                            // Notify IRadioSimImpl to send UNSOL_RESPONSE_SIM_STATUS_CHANGED to the
                            // framework. This indicates changes to the specific card state (e.g.
                            // Present/Absent, PIN state, Apps).
                            mCardStatusChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                    new AsyncResult(null /* userObj */, mCardStatus[mLogicalSlotId],
                                            null /* exception */));

                            if (mSimAppList != null && mLogicalSlotId < mSimAppList.length) {
                                // Notify listeners that SIM Application Data (EF files) has changed
                                mSimAppDataChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                            new AsyncResult(null /* userObj */,
                                                mSimAppList[mLogicalSlotId], null /* exception */));
                            }

                            int portIndex = getSimPortIndex(mLogicalSlotId, physicalSimSlot);
                            if (physicalSimSlot != PHYSICAL_SIM_SLOT_INVALID
                                    && portIndex != PORT_INDEX_INVALID
                                    && mSimService[physicalSimSlot] != null) {
                                mSimIoDataChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                        new AsyncResult(null /* userObj */,
                                                mSimService[physicalSimSlot].getSimIoMap(portIndex),
                                                        null /* exception */));
                            }
                            Log.d(mTAG, "Sim card loaded and notifications sent for logicalSlot="
                                + mLogicalSlotId);
                        } else {
                            Log.e(mTAG, "Load Sim card failed for logicalSlot=" + mLogicalSlotId);
                        }
                        break;
                    case EVENT_SERVICE_STATE_CHANGE:
                        Log.d(mTAG, "EVENT_SERVICE_STATE_CHANGE");
                        // Notify object MockNetworkService
                        mServiceStateChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                new AsyncResult(null, msg.obj, null));
                        break;
                    case EVENT_SET_SIMUL_CALLING_LOGICAL_SLOTS:
                        Log.d(mTAG, "EVENT_SET_SIMUL_CALLING_LOGICAL_SLOTS");
                        mSimultaneousCallingSupportChangedRegistrants.notifyRegistrants(
                                new AsyncResult(null, msg.obj, null));
                        break;
                    case EVENT_SET_SIM_INFO:
                        int simInfoType = msg.getData().getInt("setSimInfo:type", -1);
                        String[] simInfoData = msg.getData().getStringArray("setSimInfo:data");
                        Log.d(
                                mTAG,
                                "EVENT_SET_SIM_INFO: type = "
                                        + simInfoType
                                        + " data length = "
                                        + simInfoData.length);
                        for (int i = 0; i < simInfoData.length; i++) {
                            Log.d(mTAG, "simInfoData[" + i + "] = " + simInfoData[i]);
                        }
                        SimInfoChangedResult simInfoChangeResult =
                                setSimInfo(mLogicalSlotId, simInfoType, simInfoData);
                        if (simInfoChangeResult != null) {
                            switch (simInfoChangeResult.mSimInfoType) {
                                case SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC:
                                case SimInfoChangedResult.SIM_INFO_TYPE_IMSI:
                                    mSimInfoChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                            new AsyncResult(null, simInfoChangeResult, null));
                                    mSimAppDataChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                            new AsyncResult(
                                                    null, mSimAppList[mLogicalSlotId], null));
                                    // Card status changed still needed for updating carrier config
                                    // in Telephony Framework
                                    if (mLogicalSlotId == DEFAULT_SLOT_ID) {
                                        mSimSlotStatusChangedRegistrants.notifyRegistrants(
                                                new AsyncResult(null, mSimSlotStatus, null));
                                    }
                                    mCardStatusChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                            new AsyncResult(
                                                    null, mCardStatus[mLogicalSlotId], null));
                                    break;
                                case SimInfoChangedResult.SIM_INFO_TYPE_ATR:
                                    if (mLogicalSlotId == DEFAULT_SLOT_ID) {
                                        mSimSlotStatusChangedRegistrants.notifyRegistrants(
                                                new AsyncResult(null, mSimSlotStatus, null));
                                    }
                                    mCardStatusChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                            new AsyncResult(
                                                    null, mCardStatus[mLogicalSlotId], null));
                                    break;
                            }
                        }
                        break;
                    case EVENT_CALL_STATE_CHANGE:
                        Log.d(mTAG, "EVENT_CALL_STATE_CHANGE");
                        mCallStateChangedRegistrants[mLogicalSlotId].notifyRegistrants(
                                new AsyncResult(null, msg.obj, null));
                        break;

                    case EVENT_CURRENT_CALLS_RESPONSE:
                        Log.d(mTAG, "EVENT_CURRENT_CALLS_RESPONSE");
                        mCurrentCallsResponseRegistrants[mLogicalSlotId].notifyRegistrants(
                                new AsyncResult(null, msg.obj, null));
                        break;

                    case EVENT_CALL_INCOMING:
                        Log.d(mTAG, "EVENT_CALL_INCOMING");
                        mCallIncomingRegistrants[mLogicalSlotId].notifyRegistrants(
                                new AsyncResult(null, msg.obj, null));
                        break;
                    case EVENT_RINGBACK_TONE:
                        Log.d(mTAG, "EVENT_RINGBACK_TONE");
                        mRingbackToneRegistrants[mLogicalSlotId].notifyRegistrants(
                                new AsyncResult(null, msg.obj, null));
                        break;
                }
            }
        }
    }

    /**
     * Handles switching the Multi-SIM configuration between P+E (Physical + eSIM) and
     * E+E (Dual eSIM / MEP) modes.
     *
     * <p>This method performs the following state changes:
     *
     * 1. Reset: Sets all logical slot mappings to {@code INVALID_LOGICAL_SLOT_ID}.
     * 2. Mode Selection:
     *    - E+E Mode (MEP): Deactivates the pSIM slot. Activates both ports on the
     *      eSIM slot. Maps Port 0 to Logical Slot 1 and Port 1 to Logical Slot 0 (Cross-mapping).
     *    - P+E Mode (Default): Activates the pSIM slot (mapped to Logical Slot 0).
     *      Activates only Port 0 on the eSIM slot (mapped to Logical Slot 1).
     * 3. Notification: Updates internal SimSlotStatus and CardStatus, then notifies
     *    all registrants (Framework) of the topology change.
     *
     * @param isEnabled true to enable E+E (MEP) mode; false to enable P+E mode.
     */
    private void handleSetMultiEsimConfiguration(boolean isEnabled) {
        // We require at least 2 physical slots (pSIM + eSIM) to support switching
        // between P+E and E+E modes in this configuration.
        if (mSimService == null || mSimService.length <= ESIM_SLOT_ID) {
            String msg = "handleSetMultiEsimConfiguration: Requires at least "
                    + (ESIM_SLOT_ID + 1) + " physical slots.";
            Log.e(mTAG, msg);
            throw new IllegalStateException(msg);
        }

        MockSimService pSimService = mSimService[DEFAULT_SLOT_ID];
        MockSimService eSimService = mSimService[ESIM_SLOT_ID];

        if (pSimService == null || eSimService == null) {
            String msg = "handleSetMultiEsimConfiguration: MockSimServices are not initialized.";
            Log.e(mTAG, msg);
            throw new IllegalStateException(msg);
        }

        mCurrentMode = isEnabled ? SimSlotMapping.MODE_E_E : SimSlotMapping.MODE_P_E;

        // Reset logical slot mappings
        for(int i = 0; i < mNumOfPhysicalSlots; i++) {
            mLogicalSimIdMap[i] = INVALID_LOGICAL_SLOT_ID;
        }
        pSimService.setLogicalSlotId(PSIM_PORT_INDEX, INVALID_LOGICAL_SLOT_ID);
        eSimService.setLogicalSlotId(FIRST_ESIM_PORT_INDEX, INVALID_LOGICAL_SLOT_ID);
        eSimService.setLogicalSlotId(SECOND_ESIM_PORT_INDEX, INVALID_LOGICAL_SLOT_ID);

        switch (mCurrentMode) {
            case MODE_E_E -> {
                Log.d(mTAG, "handleSetMultiSimConfiguration: Setting MODE_EE");
                // Disable pSim slot
                pSimService.setSlotPortActive(PSIM_PORT_INDEX, false);

                // Configure eSIM Ports
                eSimService.setSlotPortActive(FIRST_ESIM_PORT_INDEX, true);
                eSimService.setLogicalSlotId(FIRST_ESIM_PORT_INDEX, SECOND_ESIM_LOGICAL_SLOT_ID);
                eSimService.setSlotPortActive(SECOND_ESIM_PORT_INDEX, true);
                eSimService.setLogicalSlotId(SECOND_ESIM_PORT_INDEX, FIRST_ESIM_LOGICAL_SLOT_ID);

                mLogicalSimIdMap[ESIM_SLOT_ID] = FIRST_ESIM_LOGICAL_SLOT_ID;
            }
            case MODE_P_E -> {
                Log.d(mTAG, "handleSetMultiSimConfiguration: Setting MODE_PE");
                // Enable pSim slot
                pSimService.setSlotPortActive(PSIM_PORT_INDEX, true);
                pSimService.setLogicalSlotId(PSIM_PORT_INDEX, FIRST_ESIM_LOGICAL_SLOT_ID);

                // Reset eSIM Ports
                eSimService.setSlotPortActive(FIRST_ESIM_PORT_INDEX, true);
                eSimService.setLogicalSlotId(FIRST_ESIM_PORT_INDEX, SECOND_ESIM_LOGICAL_SLOT_ID);
                eSimService.setSlotPortActive(SECOND_ESIM_PORT_INDEX, false);

                mLogicalSimIdMap[DEFAULT_SLOT_ID] = FIRST_ESIM_LOGICAL_SLOT_ID;
                mLogicalSimIdMap[ESIM_SLOT_ID] = SECOND_ESIM_LOGICAL_SLOT_ID;
            }
            default -> Log.e(mTAG, "handleSetMultiSimConfiguration: Unsupported mode");
        }

        updateSimSlotStatus();
        for (int i = 0; i < mNumOfPhone; i++) {
            updateCardStatus(i);
            notifyAllRegistrantNotifications(i);
        }
    }

    private int getSimLogicalSlotId(int physicalSlotId) {
        int logicalSimId = DEFAULT_SLOT_ID;

        synchronized (mSimMappingAccess) {
            logicalSimId = mLogicalSimIdMap[physicalSlotId];
        }

        return logicalSimId;
    }

    private int getSimPhysicalSlotId(int logicalSlotId) {
        return switch (mCurrentMode) {
            case MODE_ESIM_ONLY -> DEFAULT_SLOT_ID;
            case MODE_E_E -> ESIM_SLOT_ID;
            case MODE_P_E -> {
                int physicalSlotId = DEFAULT_SLOT_ID;
                synchronized (mSimMappingAccess) {
                    for (int i = 0; i < mNumOfPhysicalSlots; i++) {
                        if (mLogicalSimIdMap[i] == logicalSlotId) {
                            physicalSlotId = i;
                        }
                    }
                }
                yield physicalSlotId;
            }
        };
    }

    private int getSimPortIndex(int logicalSlotId, int physicalSlotId) {
        if (physicalSlotId < 0) return PORT_INDEX_INVALID;

        return switch (mCurrentMode) {
            case MODE_ESIM_ONLY -> logicalSlotId;
            case MODE_E_E -> (logicalSlotId == 0) ? SECOND_ESIM_PORT_INDEX : FIRST_ESIM_PORT_INDEX;
            case MODE_P_E -> PSIM_PORT_INDEX;
        };
    }

    private void setDefaultConfigValue() {
        for (int i = 0; i < mNumOfPhone; i++) {
            synchronized (mConfigAccess[i]) {
                switch (i) {
                    case 0:
                        mImei[i] = MockModemConfigInterface.DEFAULT_PHONE1_IMEI;
                        mImeiSv[i] = MockModemConfigInterface.DEFAULT_PHONE1_IMEISV;
                        mEsn[i] = MockModemConfigInterface.DEFAULT_PHONE1_ESN;
                        mMeid[i] = MockModemConfigInterface.DEFAULT_PHONE1_MEID;
                        mImeiType[i] = MockModemConfigInterface.DEFAULT_PHONE1_IMEITYPE;
                        break;
                    case 1:
                        mImei[i] = MockModemConfigInterface.DEFAULT_PHONE2_IMEI;
                        mImeiSv[i] = MockModemConfigInterface.DEFAULT_PHONE2_IMEISV;
                        mEsn[i] = MockModemConfigInterface.DEFAULT_PHONE2_ESN;
                        mMeid[i] = MockModemConfigInterface.DEFAULT_PHONE2_MEID;
                        mImeiType[i] = MockModemConfigInterface.DEFAULT_PHONE2_IMEITYPE;
                        break;
                    case 2:
                        mImei[i] = MockModemConfigInterface.DEFAULT_PHONE3_IMEI;
                        mImeiSv[i] = MockModemConfigInterface.DEFAULT_PHONE3_IMEISV;
                        mEsn[i] = MockModemConfigInterface.DEFAULT_PHONE3_ESN;
                        mMeid[i] = MockModemConfigInterface.DEFAULT_PHONE3_MEID;
                        mImeiType[i] = MockModemConfigInterface.DEFAULT_PHONE3_IMEITYPE;
                        break;
                    default:
                        mImei[i] = MockModemConfigInterface.DEFAULT_PHONE1_IMEI;
                        mImeiSv[i] = MockModemConfigInterface.DEFAULT_PHONE1_IMEISV;
                        mEsn[i] = MockModemConfigInterface.DEFAULT_PHONE1_ESN;
                        mMeid[i] = MockModemConfigInterface.DEFAULT_PHONE1_MEID;
                        mImeiType[i] = MockModemConfigInterface.DEFAULT_PHONE1_IMEITYPE;
                        break;
                }

                if (i == DEFAULT_SLOT_ID) {
                    mBasebandVersion = MockModemConfigInterface.DEFAULT_BASEBAND_VERSION;
                    mRadioState = MockModemConfigInterface.DEFAULT_RADIO_STATE;
                    mNumOfLiveModem = MockModemConfigInterface.DEFAULT_NUM_OF_LIVE_MODEM;
                    setDefaultPhoneCapability(mPhoneCapability);
                    updateSimSlotStatus();
                }
                updateCardStatus(i);
            }
        }
    }

    private void setDefaultPhoneCapability(PhoneCapability phoneCapability) {
        phoneCapability.logicalModemIds =
                new byte[MockModemConfigInterface.MAX_NUM_OF_LOGICAL_MODEM];
        phoneCapability.maxActiveData = MockModemConfigInterface.DEFAULT_MAX_ACTIVE_DATA;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            phoneCapability.maxActiveVoice = MockModemConfigInterface.DEFAULT_MAX_ACTIVE_VOICE;
        }
        phoneCapability.maxActiveInternetData =
                MockModemConfigInterface.DEFAULT_MAX_ACTIVE_INTERNAL_DATA;
        phoneCapability.isInternetLingeringSupported =
                MockModemConfigInterface.DEFAULT_IS_INTERNAL_LINGERING_SUPPORTED;
        phoneCapability.logicalModemIds[0] = MockModemConfigInterface.DEFAULT_LOGICAL_MODEM1_ID;
        phoneCapability.logicalModemIds[1] = MockModemConfigInterface.DEFAULT_LOGICAL_MODEM2_ID;
        phoneCapability.logicalModemIds[2] = MockModemConfigInterface.DEFAULT_LOGICAL_MODEM3_ID;
    }

    private void updateSimSlotStatus() {
        if (mSimService == null) {
            Log.e(mTAG, "SIM service didn't be created yet.");
            return;
        }

        for (int physicalSimSlotId = 0; physicalSimSlotId < mNumOfPhysicalSlots;
                physicalSimSlotId++) {
            if (mSimService[physicalSimSlotId] == null) {
                Log.e(mTAG, "SIM service[" + physicalSimSlotId + "] didn't be created yet.");
                continue;
            }

            int numPorts = mSimService[physicalSimSlotId].getNumOfSimPortInfo();

            mSimSlotStatus[physicalSimSlotId] = new SimSlotStatus();
            mSimSlotStatus[physicalSimSlotId].cardState =
                    mSimService[physicalSimSlotId].isCardPresent()
                            ? CardStatus.STATE_PRESENT : CardStatus.STATE_ABSENT;
            mSimSlotStatus[physicalSimSlotId].atr = mSimService[physicalSimSlotId].getATR();
            mSimSlotStatus[physicalSimSlotId].eid = mSimService[physicalSimSlotId].getEID();

            mSimSlotStatus[physicalSimSlotId].portInfo = new SimPortInfo[numPorts];

            for (int portIndex = 0; portIndex < numPorts; portIndex++) {
                mSimSlotStatus[physicalSimSlotId].portInfo[portIndex] = new SimPortInfo();
                mSimSlotStatus[physicalSimSlotId].portInfo[portIndex].portActive =
                         mSimService[physicalSimSlotId].isSlotPortActive(portIndex);
                mSimSlotStatus[physicalSimSlotId].portInfo[portIndex].logicalSlotId =
                         mSimService[physicalSimSlotId].getLogicalSlotId(portIndex);
                mSimSlotStatus[physicalSimSlotId].portInfo[portIndex].iccId =
                         mSimService[physicalSimSlotId].getICCID(portIndex);
            }

            // For eSIM-only devices, the single physical slot (Slot 0) acts as the eSIM and
            // supports MEP. For P+E devices, the second slot (Slot 1) is the eSIM and supports MEP.
            boolean isMepSupportedSlot =
                (IS_ESIM_ONLY_DEVICE && physicalSimSlotId == DEFAULT_SLOT_ID)
                    || physicalSimSlotId == ESIM_SLOT_ID;

            mSimSlotStatus[physicalSimSlotId].supportedMepMode =
                isMepSupportedSlot ? SUPPORTED_MEP_MODE_MEP_B : SUPPORTED_MEP_MODE_NONE;
        }
    }

    private void updateCardStatus(int logicalSlotId) {
        int physicalSlotId = getSimPhysicalSlotId(logicalSlotId);
        int portIndex = getSimPortIndex(logicalSlotId, physicalSlotId);

        if (physicalSlotId < 0 || physicalSlotId >= mSimService.length
                || mSimService[physicalSlotId] == null) {
            Log.e(mTAG, "Invalid Physical Slot for Logical Slot " + logicalSlotId);
            return;
        }

        mCardStatus[logicalSlotId] = new CardStatus();
        mCardStatus[logicalSlotId].cardState =
                 mSimService[physicalSlotId].isCardPresent()
                          ? CardStatus.STATE_PRESENT : CardStatus.STATE_ABSENT;
        mCardStatus[logicalSlotId].universalPinState =
                 mSimService[physicalSlotId].getUniversalPinState();
        mCardStatus[logicalSlotId].gsmUmtsSubscriptionAppIndex =
                 mSimService[physicalSlotId].getGsmAppIndex(portIndex);
        mCardStatus[logicalSlotId].cdmaSubscriptionAppIndex =
                 mSimService[physicalSlotId].getCdmaAppIndex(portIndex);
        mCardStatus[logicalSlotId].imsSubscriptionAppIndex =
                 mSimService[physicalSlotId].getImsAppIndex(portIndex);
        mCardStatus[logicalSlotId].applications = mSimService[physicalSlotId].getSimApp(portIndex);
        mCardStatus[logicalSlotId].atr = mSimService[physicalSlotId].getATR();
        mCardStatus[logicalSlotId].iccid = mSimService[physicalSlotId].getICCID(portIndex);
        mCardStatus[logicalSlotId].eid = mSimService[physicalSlotId].getEID();

        boolean isMepSupported = (IS_ESIM_ONLY_DEVICE && physicalSlotId == DEFAULT_SLOT_ID)
                 || physicalSlotId == ESIM_SLOT_ID;

        mCardStatus[logicalSlotId].supportedMepMode =
                 isMepSupported ? SUPPORTED_MEP_MODE_MEP_B : SUPPORTED_MEP_MODE_NONE;

        mCardStatus[logicalSlotId].slotMap = new SlotPortMapping();
        mCardStatus[logicalSlotId].slotMap.physicalSlotId =
                 mSimService[physicalSlotId].getPhysicalSlotId();
        mCardStatus[logicalSlotId].slotMap.portId =
                 mSimService[physicalSlotId].getSlotPortId(portIndex);

        mSimAppList[logicalSlotId] = mSimService[physicalSlotId].getSimAppList(portIndex);
    }

    private boolean loadSIMCard(int logicalSlotId, int simProfileId) {
        Log.d(mTAG, "loadSIMCard: logicalSlot=" + logicalSlotId + " profileId=" + simProfileId);

        int physicalSlotId = getSimPhysicalSlotId(logicalSlotId);
        int portIndex = getSimPortIndex(logicalSlotId, physicalSlotId);

        if (physicalSlotId == PHYSICAL_SIM_SLOT_INVALID || physicalSlotId >= mSimService.length
                     || mSimService[physicalSlotId] == null || portIndex < 0) {
            Log.e(mTAG,
                     "loadSIMCard: Cannot find valid physical/port for logicalSlotId "
                              + logicalSlotId);
            return false;
        }

        Log.d(mTAG, "loadSIMCard: Mapping logicalSlot=" + logicalSlotId + " to physicalSlot="
                 + physicalSlotId + " port=" + portIndex);

        return mSimService[physicalSlotId].loadSimCard(simProfileId, portIndex);
    }

    private String generateRandomIccid(String baseIccid) {
        String newIccid;
        Random rnd = new Random();
        StringBuilder randomNum = new StringBuilder();

        // Generate random 12-digit account id
        for (int i = 0; i < 12; i++) {
            randomNum.append(rnd.nextInt(10));
        }

        Log.d(mTAG, "Random Num = " + randomNum.toString());

        // TODO: regenerate checksum
        // Simply modify account id from base Iccid
        newIccid =
                baseIccid.substring(0, 7)
                        + randomNum.toString()
                        + baseIccid.substring(baseIccid.length() - 1);

        Log.d(mTAG, "Generate new Iccid = " + newIccid);

        return newIccid;
    }

    private SimInfoChangedResult setSimInfo(int logicalSlotId, int simInfoType,
        String[] simInfoData) {
        SimInfoChangedResult result = null;

        if (simInfoData == null) {
            Log.e(mTAG, "simInfoData == null");
            return result;
        }

        int physicalSlotId = getSimPhysicalSlotId(logicalSlotId);
        int portIndex = getSimPortIndex(logicalSlotId, physicalSlotId);
        if (physicalSlotId == PHYSICAL_SIM_SLOT_INVALID || mSimService[physicalSlotId] == null) {
            Log.e(mTAG, "Invalid physical slot for logical slot " + logicalSlotId);
            return null;
        }

        switch (simInfoType) {
            case SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC -> {
                result = processMccMncSimInfoChange(physicalSlotId, portIndex, logicalSlotId,
                    simInfoData);
            }
            case SimInfoChangedResult.SIM_INFO_TYPE_IMSI -> {
                result = processImsiSimInfoChange(physicalSlotId, portIndex, logicalSlotId,
                    simInfoData);
            }
            case SimInfoChangedResult.SIM_INFO_TYPE_ATR -> {
                result = processAtrSimInfoChange(physicalSlotId, simInfoData);
            }
            default -> Log.e(mTAG, "Not support Sim info type(" + simInfoType + ") to modify");
        }
        return result;
    }

    /**
     * Handles SIM Info change for MCC/MNC type.
     */
    private SimInfoChangedResult processMccMncSimInfoChange(int physicalSlotId, int portIndex,
        int logicalSlotId, String[] simInfoData) {
        if (simInfoData.length != 2 || simInfoData[0] == null || simInfoData[1] == null) {
            Log.e(mTAG, "processMccMncSimInfoChange: Invalid simInfoData");
            return null;
        }

        String msin = mSimService[physicalSlotId].getMsin(portIndex);

        // Adjust msin length to make sure IMSI length is valid.
        if (simInfoData[1].length() == 3 && msin.length() == 10) {
            msin = msin.substring(0, msin.length() - 1);
            Log.d(mTAG, "Modify msin = " + msin);
        }
        mSimService[physicalSlotId].setImsi(simInfoData[0], simInfoData[1], msin,
                portIndex);

        // Auto-generate a new Iccid to change carrier config id in Android Framework
        mSimService[physicalSlotId].setICCID(
                generateRandomIccid(mSimService[physicalSlotId].getICCID(portIndex)), portIndex);
        updateSimSlotStatus();
        updateCardStatus(logicalSlotId);

        return new SimInfoChangedResult(
                SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC, EF_ICCID,
                        mSimService[physicalSlotId].getActiveSimAppId(portIndex));
    }

    /**
     * Handles SIM Info change for IMSI type.
     */
    private SimInfoChangedResult processImsiSimInfoChange(int physicalSlotId, int portIndex,
        int logicalSlotId, String[] simInfoData) {
        if (simInfoData.length != 3 || simInfoData[0] == null || simInfoData[1] == null
                || simInfoData[2] == null) {
            Log.e(mTAG, "processImsiSimInfoChange: Invalid simInfoData");
            return null;
        }

        mSimService[physicalSlotId].setImsi(simInfoData[0], simInfoData[1],
                simInfoData[2], portIndex);

        // Auto-generate a new Iccid to change carrier config id in Android Framework
        mSimService[physicalSlotId].setICCID(
                generateRandomIccid(mSimService[physicalSlotId].getICCID(portIndex)),
                        portIndex);

        updateSimSlotStatus();
        updateCardStatus(logicalSlotId);

        return new SimInfoChangedResult(
                SimInfoChangedResult.SIM_INFO_TYPE_IMSI, EF_ICCID,
                        mSimService[physicalSlotId].getActiveSimAppId(portIndex));
    }

    /**
     * Handles SIM Info change for ATR type.
     */
    private SimInfoChangedResult processAtrSimInfoChange(int physicalSlotId, String[] simInfoData) {
        if (simInfoData[0] == null) {
            Log.e(mTAG, "processAtrSimInfoChange: Invalid simInfoData");
            return null;
        }

        mSimService[physicalSlotId].setATR(simInfoData[0]);

        updateSimSlotStatus();
        for (int i = 0; i < mNumOfPhone; i++) {
            if (getSimPhysicalSlotId(i) == physicalSlotId) {
                updateCardStatus(i);
            }
        }
        return new SimInfoChangedResult(SimInfoChangedResult.SIM_INFO_TYPE_ATR, 0 /* efid */,
                "" /* aid */);
    }


    private int getArrayIndexForDeviceProperties(int logicalSlotId) {
        if (IS_ESIM_ONLY_DEVICE) {
            return logicalSlotId;
        } else {
            return getSimPhysicalSlotId(logicalSlotId);
        }
    }

    private void notifyDeviceIdentityChangedRegistrants(int logicalSlotId) {
        String[] deviceIdentity = new String[4];
        int index = getArrayIndexForDeviceProperties(logicalSlotId);

        if (index == INVALID_LOGICAL_SLOT_ID || index >= mNumOfPhone) {
            Log.e(mTAG, "notifyDeviceIdentityChangedRegistrants: Invalid index " + index
                    + " for logicalSlotId " + logicalSlotId);
            return;
        }

        synchronized (mConfigAccess[index]) {
            deviceIdentity[DEVICE_ID_IMEI] = mImei[index];
            deviceIdentity[DEVICE_ID_IMEISV] = mImeiSv[index];
            deviceIdentity[DEVICE_ID_ESN] = mEsn[index];
            deviceIdentity[DEVICE_ID_MEID] = mMeid[index];
        }
        AsyncResult ar = new AsyncResult(null /* userObj */, deviceIdentity, null /* exception */);
        if (mDeviceIdentityChangedRegistrants != null
                && logicalSlotId < mDeviceIdentityChangedRegistrants.length) {
            mDeviceIdentityChangedRegistrants[logicalSlotId].notifyRegistrants(ar);
        }
    }

    private void notifyDeviceImeiTypeChangedRegistrants(int logicalSlotId) {
        android.hardware.radio.modem.ImeiInfo imeiInfo =
            new android.hardware.radio.modem.ImeiInfo();
        int index = getArrayIndexForDeviceProperties(logicalSlotId);

        if (index < 0 || index >= mNumOfPhone) {
            Log.e(mTAG, "notifyDeviceImeiTypeChangedRegistrants: Invalid index " + index
                    + " for logicalSlotId " + logicalSlotId);
            return;
        }

        synchronized (mConfigAccess[index]) {
            imeiInfo.type = mImeiType[index];
            imeiInfo.imei = mImei[index];
            imeiInfo.svn = mImeiSv[index];
        }
        AsyncResult ar = new AsyncResult(null /* userObj */, imeiInfo, null /* exception */);
        if (mDeviceImeiInfoChangedRegistrants != null
                && logicalSlotId < mDeviceImeiInfoChangedRegistrants.length) {
            mDeviceImeiInfoChangedRegistrants[logicalSlotId].notifyRegistrants(ar);
        }
    }

    // ***** MockModemConfigInterface implementation
    @Override
    public void destroy() {
        // Mock Services destroy
        for (int i = 0; i < mNumOfPhone; i++) {
            // IRadioVoice
            if (mVoiceService != null && mVoiceService[i] != null) {
                mVoiceService[i].destroy();
            }
        }
    }

    @Override
    public Handler getMockModemConfigHandler(int logicalSlotId) {
        return mHandler[logicalSlotId];
    }

    @Override
    public void notifyAllRegistrantNotifications(int phoneId) {
        Log.d(mTAG, "notifyAllRegistrantNotifications");

        // IRadioConfig
        mNumOfLiveModemChangedRegistrants.notifyRegistrants(
                new AsyncResult(null, mNumOfLiveModem, null));
        mPhoneCapabilityChangedRegistrants.notifyRegistrants(
                new AsyncResult(null, mPhoneCapability, null));
        mSimSlotStatusChangedRegistrants.notifyRegistrants(
                new AsyncResult(null, mSimSlotStatus, null));
        mSimultaneousCallingSupportChangedRegistrants.notifyRegistrants(
                new AsyncResult(null, mEnabledLogicalSlots, null));

        // IRadioModem
        mBasebandVersionChangedRegistrants.notifyRegistrants(
                new AsyncResult(null, mBasebandVersion, null));
        mRadioStateChangedRegistrants.notifyRegistrants(new AsyncResult(null, mRadioState, null));

        int physicalSlotId = getSimPhysicalSlotId(phoneId);
        synchronized (mConfigAccess[physicalSlotId]) {
            // IRadioModem
            notifyDeviceIdentityChangedRegistrants(phoneId);
            notifyDeviceImeiTypeChangedRegistrants(phoneId);

            // IRadioSim
            mCardStatusChangedRegistrants[phoneId].notifyRegistrants(
                    new AsyncResult(null, mCardStatus[phoneId], null));
            mSimAppDataChangedRegistrants[phoneId].notifyRegistrants(
                    new AsyncResult(null, mSimAppList[phoneId], null));
        }
    }

    // ***** IRadioConfig notification implementation
    @Override
    public void registerForNumOfLiveModemChanged(
            int logicalSlotId, Handler h, int what, Object obj) {
        mNumOfLiveModemChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForNumOfLiveModemChanged(int logicalSlotId, Handler h) {
        mNumOfLiveModemChangedRegistrants.remove(h);
    }

    @Override
    public void registerForPhoneCapabilityChanged(
            int logicalSlotId, Handler h, int what, Object obj) {
        mPhoneCapabilityChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForPhoneCapabilityChanged(int logicalSlotId, Handler h) {
        mPhoneCapabilityChangedRegistrants.remove(h);
    }

    @Override
    public void registerForSimultaneousCallingSupportStatusChanged(
            int logicalSlotId, Handler h, int what, Object obj) {
        mSimultaneousCallingSupportChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForSimultaneousCallingSupportStatusChanged(Handler h) {
        mSimultaneousCallingSupportChangedRegistrants.remove(h);
    }

    @Override
    public void registerForSimSlotStatusChanged(
            int logicalSlotId, Handler h, int what, Object obj) {
        mSimSlotStatusChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForSimSlotStatusChanged(int logicalSlotId, Handler h) {
        mSimSlotStatusChangedRegistrants.remove(h);
    }

    // ***** IRadioModem notification implementation
    @Override
    public void registerForBasebandVersionChanged(
            int logicalSlotId, Handler h, int what, Object obj) {
        mBasebandVersionChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForBasebandVersionChanged(int logicalSlotId, Handler h) {
        mBasebandVersionChangedRegistrants.remove(h);
    }

    @Override
    public void registerForDeviceIdentityChanged(
            int logicalSlotId, Handler h, int what, Object obj) {
        mDeviceIdentityChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void registerForDeviceImeiInfoChanged(
            int logicalSlotId, Handler h, int what, Object obj) {
        mDeviceImeiInfoChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForDeviceIdentityChanged(int logicalSlotId, Handler h) {
        mDeviceIdentityChangedRegistrants[logicalSlotId].remove(h);
    }

    @Override
    public void registerForRadioStateChanged(int logicalSlotId, Handler h, int what, Object obj) {
        mRadioStateChangedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForRadioStateChanged(int logicalSlotId, Handler h) {
        mRadioStateChangedRegistrants.remove(h);
    }

    // ***** IRadioSim notification implementation
    @Override
    public void registerForCardStatusChanged(int logicalSlotId, Handler h, int what, Object obj) {
        mCardStatusChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForCardStatusChanged(int logicalSlotId, Handler h) {
        mCardStatusChangedRegistrants[logicalSlotId].remove(h);
    }

    @Override
    public void registerForSimAppDataChanged(int logicalSlotId, Handler h, int what, Object obj) {
        mSimAppDataChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForSimAppDataChanged(int logicalSlotId, Handler h) {
        mSimAppDataChangedRegistrants[logicalSlotId].remove(h);
    }

    @Override
    public void registerForSimInfoChanged(int logicalSlotId, Handler h, int what, Object obj) {
        mSimInfoChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForSimInfoChanged(int logicalSlotId, Handler h) {
        mSimInfoChangedRegistrants[logicalSlotId].remove(h);
    }

    @Override
    public void registerForSimIoDataChanged(int logicalSlotId, Handler h, int what, Object obj) {
        mSimIoDataChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForSimIoDataChanged(int logicalSlotId, Handler h) {
        mSimIoDataChangedRegistrants[logicalSlotId].remove(h);
    }

    // ***** IRadioNetwork notification implementation
    @Override
    public void registerForServiceStateChanged(int logicalSlotId, Handler h, int what, Object obj) {
        mServiceStateChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForServiceStateChanged(int logicalSlotId, Handler h) {
        mServiceStateChangedRegistrants[logicalSlotId].remove(h);
    }

    // ***** IRadioVoice notification implementation
    @Override
    public void registerForCallStateChanged(int logicalSlotId, Handler h, int what, Object obj) {
        mCallStateChangedRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForCallStateChanged(int logicalSlotId, Handler h) {
        mCallStateChangedRegistrants[logicalSlotId].remove(h);
    }

    @Override
    public void registerForCurrentCallsResponse(
            int logicalSlotId, Handler h, int what, Object obj) {
        mCurrentCallsResponseRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForCurrentCallsResponse(int logicalSlotId, Handler h) {
        mCurrentCallsResponseRegistrants[logicalSlotId].remove(h);
    }

    @Override
    public void registerForCallIncoming(int logicalSlotId, Handler h, int what, Object obj) {
        mCallIncomingRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterForCallIncoming(int logicalSlotId, Handler h) {
        mCallIncomingRegistrants[logicalSlotId].remove(h);
    }

    @Override
    public void registerRingbackTone(int logicalSlotId, Handler h, int what, Object obj) {
        mRingbackToneRegistrants[logicalSlotId].addUnique(h, what, obj);
    }

    @Override
    public void unregisterRingbackTone(int logicalSlotId, Handler h) {
        mRingbackToneRegistrants[logicalSlotId].remove(h);
    }

    // ***** IRadioConfig set APIs implementation

    // ***** IRadioModem set APIs implementation
    @Override
    public void setRadioState(int logicalSlotId, int state, String client) {
        Log.d(mTAG, "setRadioState[" + logicalSlotId + "] (" + state + ") from " + client);

        Message msg = mHandler[logicalSlotId].obtainMessage(EVENT_SET_RADIO_POWER);
        msg.arg1 = state;
        mHandler[logicalSlotId].sendMessage(msg);
    }

    // ***** IRadioSim set APIs implementation

    // ***** IRadioNetwork set APIs implementation

    // ***** IRadioVoice set APIs implementation
    @Override
    public boolean getCurrentCalls(int logicalSlotId, String client) {
        Log.d(mTAG, "getCurrentCalls[" + logicalSlotId + "] from: " + client);
        return mVoiceService[logicalSlotId].getCurrentCalls();
    }

    @Override
    public boolean dialVoiceCall(
            int logicalSlotId, String address, int clir, UusInfo[] uusInfo, String client) {
        return dialVoiceCall(logicalSlotId, address, clir, uusInfo, mCallControlInfo, client);
    }

    @Override
    public boolean dialVoiceCall(
            int logicalSlotId,
            String address,
            int clir,
            UusInfo[] uusInfo,
            MockCallControlInfo callControlInfo,
            String client) {
        Log.d(
                mTAG,
                "dialVoiceCall["
                        + logicalSlotId
                        + "]: address = "
                        + address
                        + " clir = "
                        + clir
                        + " from: "
                        + client);
        if (uusInfo == null) {
            Log.e(mTAG, "ussInfo == null!");
            return false;
        }

        int callType = CALL_TYPE_VOICE;
        return mVoiceService[logicalSlotId].dialVoiceCall(
                address, clir, uusInfo, callType, callControlInfo);
    }

    @Override
    public boolean dialEccVoiceCall(
            int logicalSlotId,
            String address,
            int categories,
            String[] urns,
            int routing,
            String client) {
        return dialEccVoiceCall(
                logicalSlotId, address, categories, urns, routing, mCallControlInfo, client);
    }

    @Override
    public boolean dialEccVoiceCall(
            int logicalSlotId,
            String address,
            int categories,
            String[] urns,
            int routing,
            MockCallControlInfo callControlInfo,
            String client) {
        Log.d(
                mTAG,
                "dialEccVoiceCall["
                        + logicalSlotId
                        + "]: address = "
                        + address
                        + " categories = "
                        + categories
                        + " from: "
                        + client);

        return mVoiceService[logicalSlotId].dialEccVoiceCall(
                address, categories, urns, routing, CALL_TYPE_EMERGENCY, callControlInfo);
    }

    @Override
    public boolean hangupVoiceCall(int logicalSlotId, int index, String client) {
        Log.d(
                mTAG,
                "hangupVoiceCall[" + logicalSlotId + "]: index = " + index + " from: " + client);
        return mVoiceService[logicalSlotId].hangupVoiceCall(index);
    }

    @Override
    public boolean rejectVoiceCall(int logicalSlotId, String client) {
        Log.d(mTAG, "rejectVoiceCall[" + logicalSlotId + "] from: " + client);
        return mVoiceService[logicalSlotId].rejectVoiceCall();
    }

    @Override
    public boolean acceptVoiceCall(int logicalSlotId, String client) {
        Log.d(mTAG, "acceptVoiceCall[" + logicalSlotId + "] from: " + client);
        return mVoiceService[logicalSlotId].acceptVoiceCall();
    }

    @Override
    public LastCallFailCauseInfo getLastCallFailCause(int logicalSlotId, String client) {
        Log.d(mTAG, "getLastCallFailCause[" + logicalSlotId + "] from: " + client);
        return mVoiceService[logicalSlotId].getLastCallEndInfo();
    }

    @Override
    public void setLastCallFailCause(
            int logicalSlotId, @Annotation.DisconnectCauses int cause, String client) {
        Log.d(
                mTAG,
                "setLastCallFailCause["
                        + logicalSlotId
                        + "]: cause = "
                        + cause
                        + "  from: "
                        + client);
        mVoiceService[logicalSlotId].setLastCallFailCause(cause);
    }

    @Override
    public void clearAllCalls(
            int logicalSlotId, @Annotation.DisconnectCauses int cause, String client) {
        Log.d(mTAG, "clearAllCalls[" + logicalSlotId + "]: cause = " + cause + "  from: " + client);
        mVoiceService[logicalSlotId].clearAllCalls(cause);
    }

    @Override
    public boolean getVoiceMuteMode(int logicalSlotId, String client) {
        Log.d(mTAG, "getVoiceMuteMode[" + logicalSlotId + "] from " + client);
        return mVoiceService[logicalSlotId].getMuteMode();
    }

    @Override
    public boolean setVoiceMuteMode(int logicalSlotId, boolean muteMode, String client) {
        Log.d(
                mTAG,
                "setVoiceMuteMode["
                        + logicalSlotId
                        + "]: muteMode = "
                        + muteMode
                        + " from: "
                        + client);
        mVoiceService[logicalSlotId].setMuteMode(muteMode);
        return true;
    }

    // ***** IRadioData set APIs implementation

    // ***** IRadioMessaging set APIs implementation

    // ***** Utility methods implementation
    @Override
    public int getSimPhysicalSlotId(int logicalSlotId, String client) {
        Log.d(mTAG, "getSimPhysicalSlotId[" + logicalSlotId + "] from: " + client);
        return getSimPhysicalSlotId(logicalSlotId);
    }

    @Override
    public boolean isSimCardPresent(int logicalSlotId, String client) {
        Log.d(mTAG, "isSimCardPresent[" + logicalSlotId + "] from: " + client);
        if (logicalSlotId < 0 || logicalSlotId >= mNumOfPhone) {
            Log.e(mTAG, "isSimCardPresent: Invalid logicalSlotId " + logicalSlotId);
            return false;
        }

        synchronized (mConfigAccess[logicalSlotId]) {
            if (mCardStatus[logicalSlotId] == null) {
                return false;
            }

            if (mCardStatus[logicalSlotId].cardState == CardStatus.STATE_ABSENT) {
                return false;
            }

            int physicalSlotId = getSimPhysicalSlotId(logicalSlotId);
            boolean isPotentiallyEsim =
                    (IS_ESIM_ONLY_DEVICE && physicalSlotId == DEFAULT_SLOT_ID) || (physicalSlotId
                            == ESIM_SLOT_ID);

            if (isPotentiallyEsim) {
                return mCardStatus[logicalSlotId].iccid != null
                        && mCardStatus[logicalSlotId].iccid.trim().length() > 0;
            } else {
                return true;
            }
        }
    }

    @Override
    public boolean changeSimProfile(int logicalSlotId, int simprofileid, String client) {
        boolean result = true;
        Log.d(
                mTAG,
                "changeSimProfile["
                        + logicalSlotId
                        + "]: profile id("
                        + simprofileid
                        + ") from: "
                        + client);

        if (simprofileid >= MOCK_SIM_PROFILE_ID_DEFAULT && simprofileid < MOCK_SIM_PROFILE_ID_MAX) {
            Message msg = mHandler[logicalSlotId].obtainMessage(EVENT_CHANGE_SIM_PROFILE);
            msg.getData().putInt("changeSimProfile", simprofileid);
            mHandler[logicalSlotId].sendMessage(msg);
        } else {
            result = false;
        }

        return result;
    }

    @Override
    public void setSimulCallingEnabledLogicalSlots(int logicalSlotId, int[] enabledLogicalSlots,
            String client) {
        Log.d(mTAG, "setSimulCallingEnabledLogicalSlots["
                + Arrays.toString(enabledLogicalSlots) + "] from: " + client);

        Message msg = mHandler[logicalSlotId].obtainMessage(EVENT_SET_SIMUL_CALLING_LOGICAL_SLOTS,
                enabledLogicalSlots);
        mHandler[logicalSlotId].sendMessage(msg);
    }

    @Override
    public void setSimInfo(int logicalSlotId, int type, String[] data, String client) {
        Log.d(mTAG, "setSimInfo[" + logicalSlotId + "]: type(" + type + ") from: " + client);

        Message msg = mHandler[logicalSlotId].obtainMessage(EVENT_SET_SIM_INFO);
        Bundle bundle = msg.getData();
        bundle.putInt("setSimInfo:type", type);
        bundle.putStringArray("setSimInfo:data", data);
        mHandler[logicalSlotId].sendMessage(msg);
    }

    @Override
    public String getSimInfo(int logicalSlotId, int type, String client) {
        Log.d(mTAG, "getSimInfo[" + logicalSlotId + "]: type(" + type + ") from: " + client);

        String result = "";
        int physicalSlotId = getSimPhysicalSlotId(logicalSlotId);

        int portIndex = getSimPortIndex(logicalSlotId, physicalSlotId);

        synchronized (mConfigAccess[physicalSlotId]) {
            switch (type) {
                case SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC:
                    result = mSimService[physicalSlotId].getMccMnc(portIndex);
                    break;
                case SimInfoChangedResult.SIM_INFO_TYPE_IMSI:
                    result = mSimService[physicalSlotId].getImsi(portIndex);
                    break;
                case SimInfoChangedResult.SIM_INFO_TYPE_ATR:
                    result = mCardStatus[logicalSlotId].atr;
                    break;
                default:
                    Log.e(mTAG, "Not support this type of SIM info.");
                    break;
            }
        }

        return result;
    }

    @Override
    public boolean setCallControlInfo(
            int logicalSlotId, MockCallControlInfo callControlInfo, String client) {
        Log.d(mTAG, "setCallControlInfo[" + logicalSlotId + " from: " + client);
        mCallControlInfo = callControlInfo;

        return true;
    }

    @Override
    public MockCallControlInfo getCallControlInfo(int logicalSlotId, String client) {
        Log.d(mTAG, "getCallControlInfo[" + logicalSlotId + " from: " + client);
        return mCallControlInfo;
    }

    @Override
    public boolean triggerIncomingVoiceCall(
            int logicalSlotId,
            String address,
            UusInfo[] uusInfo,
            CdmaSignalInfoRecord cdmaSignalInfoRecord,
            MockCallControlInfo callControlInfo,
            String client) {
        Log.d(
                mTAG,
                "triggerIncomingVoiceCall["
                        + logicalSlotId
                        + "]: address = "
                        + address
                        + " from: "
                        + client);
        if (uusInfo == null) {
            Log.e(mTAG, "ussInfo == null!");
            return false;
        }

        int callType = CALL_TYPE_VOICE;
        return mVoiceService[logicalSlotId].triggerIncomingVoiceCall(
                address, uusInfo, callType, cdmaSignalInfoRecord, callControlInfo);
    }

    @Override
    public int getNumberOfCalls(int logicalSlotId, String client) {
        Log.d(mTAG, "getNumberOfCalls[" + logicalSlotId + "] from: " + client);
        return mVoiceService[logicalSlotId].getNumberOfCalls();
    }

    @Override
    public void setMultiEsimConfiguration(boolean isEnabled) {
        Log.d(mTAG, "setMultiEsimConfiguration: mode=" + isEnabled);
        if (IS_ESIM_ONLY_DEVICE) {
            Log.d(mTAG, "Ignoring setMultiEsimConfiguration for eSim only device");
            return;
        }
        Message msg = mHandler[DEFAULT_SLOT_ID].obtainMessage(EVENT_SET_MULTI_SIM_CONFIG);
        msg.arg1 = isEnabled ? 1 : 0;
        msg.sendToTarget();
    }
}