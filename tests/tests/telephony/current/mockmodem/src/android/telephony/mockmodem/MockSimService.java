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

import android.content.Context;
import android.hardware.radio.sim.AppStatus;
import android.hardware.radio.sim.PinState;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.os.SystemProperties;

public class MockSimService {
    /* Support SIM card identify */
    public static final int MOCK_SIM_PROFILE_ID_DEFAULT = 0; // SIM Absent
    public static final int MOCK_SIM_PROFILE_ID_TWN_CHT = 1;
    public static final int MOCK_SIM_PROFILE_ID_TWN_FET = 2;
    public static final int MOCK_SIM_PROFILE_ID_US_FI = 3;
    public static final int MOCK_SIM_PROFILE_ID_MAX = 4;

    /* Type of SIM IO command */
    public static final int COMMAND_READ_BINARY = 0xb0;
    public static final int COMMAND_GET_RESPONSE = 0xc0;
    public static final int COMMAND_SELECT = 0xa4;

    /* EF Id definition */
    public static final int EF_ICCID = 0x2FE2;
    public static final int EF_IMSI = 0x6F07;
    public static final int EF_GID1 = 0x6F3E;

    /* SIM profile XML TAG definition */
    private static final String MOCK_SIM_TAG = "MockSim";
    private static final String MOCK_SIM_PROFILE_TAG = "MockSimProfile";
    private static final String MOCK_PIN_PROFILE_TAG = "PinProfile";
    private static final String MOCK_PIN1_STATE_TAG = "Pin1State";
    private static final String MOCK_PIN2_STATE_TAG = "Pin2State";
    private static final String MOCK_FACILITY_LOCK_TAG = "FacilityLock";
    private static final String MOCK_FACILITY_LOCK_FD_TAG = "FD";
    private static final String MOCK_FACILITY_LOCK_SC_TAG = "SC";
    private static final String MOCK_MF_TAG = "MF";
    private static final String MOCK_EF_TAG = "EF";
    private static final String MOCK_EF_DIR_TAG = "EFDIR";
    private static final String MOCK_ADF_TAG = "ADF";
    // ADF used for SIM-IO through logical channel (iccOpenLogicalChannel) instead of iccIoForApp
    // with which MOCK_ADF_TAG is used
    private static final String MOCK_ADFLC_TAG = "ADF-LC";
    // Each tag represent a command-respond APDU IO
    private static final String MOCK_IO_TAG = "IO";

    /* Support SIM slot */
    private static final int MOCK_SIM_SLOT_1 = 0;
    private static final int MOCK_SIM_SLOT_2 = 1;
    private static final int MOCK_SIM_SLOT_3 = 2;
    public static final int MOCK_SIM_SLOT_MIN = 1;
    public static final int MOCK_SIM_SLOT_MAX = 3;

    /* Default value definition */
    private static final int MOCK_SIM_DEFAULT_SLOTID = MOCK_SIM_SLOT_1;
    private static final int DEFAULT_NUM_OF_SIM_APP = 0;
    private static final int DEFAULT_GSM_APP_IDX = -1;
    private static final int DEFAULT_CDMA_APP_IDX = -1;
    private static final int DEFAULT_IMS_APP_IDX = -1;
    private static final int DEFAULT_NUM_OF_SIM_PORT_INFO = 1;
    // SIM1 slot status - physical SIM
    private static final int DEFAULT_SIM1_PROFILE_ID = MOCK_SIM_PROFILE_ID_DEFAULT;
    private static final boolean DEFAULT_SIM1_CARD_PRESENT = false;
    private static final String DEFAULT_SIM1_ATR = "";
    private static final String DEFAULT_SIM1_EID = "";
    private static final boolean DEFAULT_SIM1_PORT_ACTIVE = true;
    private static final int DEFAULT_SIM1_PORT_ID = 0;
    private static final int DEFAULT_SIM1_LOGICAL_SLOT_ID = 0;
    private static final int DEFAULT_SIM1_UNIVERSAL_PIN_STATE = PinState.UNKNOWN;
    // SIM2 slot status - eSIM
    private static final int DEFAULT_SIM2_PROFILE_ID = MOCK_SIM_PROFILE_ID_DEFAULT;
    private static final boolean DEFAULT_SIM2_CARD_PRESENT = true;
    private static final String DEFAULT_SIM2_ATR =
            "3B9F97C00A3FC6828031E073FE211F65D002341512810F51";
    private static final String DEFAULT_SIM2_EID = "89033023426200000000005430099507";
    private static final boolean DEFAULT_SIM2_PORT_ACTIVE = true;
    private static final int DEFAULT_SIM2_PORT_ID = 0;
    private static final int DEFAULT_SIM2_LOGICAL_SLOT_ID = 1;
    private static final int DEFAULT_SIM2_UNIVERSAL_PIN_STATE = PinState.DISABLED;
    // SIM3 slot status - eSIM
    private static final int DEFAULT_SIM3_PROFILE_ID = MOCK_SIM_PROFILE_ID_DEFAULT;
    private static final boolean DEFAULT_SIM3_CARD_PRESENT = true;
    private static final String DEFAULT_SIM3_ATR =
            "3B9F97C00AB1FE453FC6838031E073FE211F65D002341569810F21";
    private static final String DEFAULT_SIM3_EID = "89033023427100000000007980324395";
    private static final boolean DEFAULT_SIM3_PORT_ACTIVE = false;
    private static final int DEFAULT_SIM3_PORT_ID = 0;
    private static final int DEFAULT_SIM3_LOGICAL_SLOT_ID = -1;
    private static final int DEFAULT_SIM3_UNIVERSAL_PIN_STATE = PinState.DISABLED;

    // Current implementation targets DSDS (Dual SIM Dual Standby) support,
    // which requires at most 2 ports per slot (e.g. for MEP).
    private static final int MAX_PORTS = 2;

    // This system property is set to 1 for eSIM-only devices.
    // For other devices, the value is not set (returning default -1).
    private static final int NUM_SLOTS_PROPERTY = SystemProperties.getInt(
        "ro.telephony.sim_slots.count", -1);
    // TODO(b/477553823): Decouple MockModem slot configuration from physical hardware
    // properties. Currently relies on ro.telephony.sim_slots.count to initialize slot number.
    // Future work should allow explicit configuration independent of the underlying device.
    private static final boolean IS_ESIM_ONLY_DEVICE = NUM_SLOTS_PROPERTY == 1;

    private String mTag = "MockSimService";
    private Context mContext;

    // SIM Slot status
    private int mPhysicalSlotId;

    // Default port index
    private static final int DEFAULT_PORT_INDEX = 0;
    // To store the Logical Slot ID mapped to each port (index).
    // Index: Port Index (0 or 1), Value: Logical Slot ID.
    private int[] mLogicalSlotIds = new int[MAX_PORTS];
    // To store the Port ID for each port index,Index: Port Index, Value: Port ID.
    private int[] mSlotPortIds = new int[MAX_PORTS];
    // To store the active status of each port.
    // Index: Port Index, Value: true if active, false otherwise
    private boolean[] mIsSlotPortActives = new boolean[MAX_PORTS];
    private boolean mIsCardPresent;

    /* SIM profile info */
    private SimProfileInfo[] mSimProfileInfoList;

    // SIM card data
    private int[] mSimProfileIds = new int[MAX_PORTS];
    private String mEID;
    private String mATR;
    private int mUniversalPinState;

    // Per-port data
    private AppStatus[][] mSimApps = new AppStatus[MAX_PORTS][];
    private ArrayList<SimAppData>[] mSimAppLists = (ArrayList<SimAppData>[]) new ArrayList[MAX_PORTS];

    // Key: AID Value: list of SIM IO (command-respond) data
    private final Map<String, List<SimIoData>>[] mSimIoDataMaps = new Map[MAX_PORTS];

    // TODO: switch to @AutoValue?
    public static final class SimIoData {
        public final String mFileId;
        public final String mCommand;
        public final String mSw1;
        public final String mSw2;
        public final String mResponse;

        public SimIoData(String fileid, String command, String sw1, String sw2, String response) {
            mFileId = fileid;
            mCommand = command;
            mSw1 = sw1;
            mSw2 = sw2;
            mResponse = response;
        }

        public String toString() {
            return "SimIoData: "
                    + "fileid=" + mFileId
                    + ", cmd=" + mCommand
                    + ", sw1=" + mSw1
                    + ", sw2=" + mSw2
                    + ", response=" + mResponse;
        }
    }

    public class SimAppData {
        private static final int EF_INFO_DATA = 0;
        private static final int EF_BINARY_DATA = 1;

        private int mSimAppId;
        private String mAid;
        private boolean mIsCurrentActive;
        private String mPath;
        private int mFdnStatus;
        private int mPin1State;
        private String mImsi;
        private String mMcc;
        private String mMnc;
        private String mMsin;
        private String[] mIccid;
        private String[] mGid1;

        private void initSimAppData(int simappid, String aid, String path, boolean status) {
            mSimAppId = simappid;
            mAid = aid;
            mIsCurrentActive = status;
            mPath = path;
            mIccid = new String[2];
            mGid1 = new String[2];
        }

        public SimAppData(int simappid, String aid, String path) {
            initSimAppData(simappid, aid, path, false);
        }

        public SimAppData(int simappid, String aid, String path, boolean status) {
            initSimAppData(simappid, aid, path, status);
        }

        public int getSimAppId() {
            return mSimAppId;
        }

        public String getAid() {
            return mAid;
        }

        public boolean isCurrentActive() {
            return mIsCurrentActive;
        }

        public String getPath() {
            return mPath;
        }

        public int getFdnStatus() {
            return mFdnStatus;
        }

        public void setFdnStatus(int status) {
            mFdnStatus = status;
        }

        public int getPin1State() {
            return mPin1State;
        }

        public void setPin1State(int state) {
            mPin1State = state;
        }

        public String getImsi() {
            return mMcc + mMnc + mMsin;
        }

        public void setImsi(String mcc, String mnc, String msin) {
            setMcc(mcc);
            setMnc(mnc);
            setMsin(msin);
        }

        public String getMcc() {
            return mMcc;
        }

        public void setMcc(String mcc) {
            mMcc = mcc;
        }

        public String getMnc() {
            return mMnc;
        }

        public void setMnc(String mnc) {
            mMnc = mnc;
        }

        public String getMsin() {
            return mMsin;
        }

        public void setMsin(String msin) {
            mMsin = msin;
        }

        public String getIccidInfo() {
            return mIccid[EF_INFO_DATA];
        }

        public void setIccidInfo(String info) {
            mIccid[EF_INFO_DATA] = info;
        }

        public String getIccid() {
            return mIccid[EF_BINARY_DATA];
        }

        public void setIccid(String iccid) {
            mIccid[EF_BINARY_DATA] = iccid;
        }

        public String getGid1Info() {
            return mGid1[EF_INFO_DATA];
        }

        public void setGid1Info(String info) {
            mGid1[EF_INFO_DATA] = info;
        }

        public String getGid1() {
            return mGid1[EF_BINARY_DATA];
        }

        public void setGid1(String gid1) {
            mGid1[EF_BINARY_DATA] = gid1;
        }
    }

    public class SimProfileInfo {
        private int mSimProfileId;
        private int mNumOfSimApp;
        private int mGsmAppIndex;
        private int mCdmaAppIndex;
        private int mImsAppIndex;
        private String mXmlFile;

        public SimProfileInfo(int profileid) {
            mSimProfileId = profileid;
            mNumOfSimApp = DEFAULT_NUM_OF_SIM_APP;
            mGsmAppIndex = DEFAULT_GSM_APP_IDX;
            mCdmaAppIndex = DEFAULT_CDMA_APP_IDX;
            mImsAppIndex = DEFAULT_IMS_APP_IDX;
            mXmlFile = "";
        }

        public int getNumOfSimApp() {
            return mNumOfSimApp;
        }

        public int getGsmAppIndex() {
            return mGsmAppIndex;
        }

        public int getCdmaAppIndex() {
            return mCdmaAppIndex;
        }

        public int getImsAppIndex() {
            return mImsAppIndex;
        }

        public String getXmlFile() {
            return mXmlFile;
        }

        public void setNumOfSimApp(int number) {
            mNumOfSimApp = number;
        }

        public void setGsmAppIndex(int index) {
            mGsmAppIndex = index;
        }

        public void setCdmaAppIndex(int index) {
            mCdmaAppIndex = index;
        }

        public void setImsAppIndex(int index) {
            mImsAppIndex = index;
        }

        public void setXmlFile(String file) {
            mXmlFile = file;
        }
    }

    public MockSimService(Context context, int slotId) {
        mContext = context;
        int simprofile = DEFAULT_SIM1_PROFILE_ID;

        if (slotId >= MOCK_SIM_SLOT_MAX) {
            Log.e(
                    mTag,
                    "Invalid slot id("
                            + slotId
                            + "). Using default slot id("
                            + MOCK_SIM_DEFAULT_SLOTID
                            + ").");
            slotId = MOCK_SIM_DEFAULT_SLOTID;
        }

        // Init default SIM profile id
        mTag = mTag + "-" + slotId;
        switch (slotId) {
            case MOCK_SIM_SLOT_1:
                simprofile = DEFAULT_SIM1_PROFILE_ID;
                break;
            case MOCK_SIM_SLOT_2:
                simprofile = DEFAULT_SIM2_PROFILE_ID;
                break;
            case MOCK_SIM_SLOT_3:
                simprofile = DEFAULT_SIM3_PROFILE_ID;
                break;
        }

        // Initialize arrays for ports
        for (int portId = 0; portId < MAX_PORTS; portId++) {
            mSimProfileIds[portId] = MOCK_SIM_PROFILE_ID_DEFAULT;
            mLogicalSlotIds[portId] = -1;
            mSlotPortIds[portId] = portId;
            mIsSlotPortActives[portId] = false;
            mSimAppLists[portId] = new ArrayList<>();
            mSimIoDataMaps[portId] = new HashMap<>();
            mSimApps[portId] = new AppStatus[0]; // Initialize inner array to empty
        }

        // Initial support SIM profile list
        mSimProfileInfoList = new SimProfileInfo[MOCK_SIM_PROFILE_ID_MAX];
        for (int idx = 0; idx < MOCK_SIM_PROFILE_ID_MAX; idx++) {
            Log.d(mTag, "Create sim profile id = " + idx);
            mSimProfileInfoList[idx] = new SimProfileInfo(idx);
            switch (idx) {
                case MOCK_SIM_PROFILE_ID_TWN_CHT:
                    mSimProfileInfoList[idx].setXmlFile("mock_sim_tw_cht.xml");
                    break;
                case MOCK_SIM_PROFILE_ID_TWN_FET:
                    mSimProfileInfoList[idx].setXmlFile("mock_sim_tw_fet.xml");
                    break;
                case MOCK_SIM_PROFILE_ID_US_FI:
                    mSimProfileInfoList[idx].setXmlFile("mock_sim_us_fi.xml");
                    break;
                default:
                    break;
            }
        }

        // Initiate SIM card with default profile for Port 0
        initMockSimCard(slotId, simprofile, DEFAULT_PORT_INDEX);
    }

    private void initMockSimCard(int slotId, int simProfileId, int portIndex) {
        if (portIndex < DEFAULT_PORT_INDEX || portIndex >= MAX_PORTS) return;

        if (slotId > MockModemConfigInterface.MAX_NUM_OF_SIM_SLOT) {
            Log.e(
                    mTag,
                    "Physical slot id("
                            + slotId
                            + ") is invalid. Using default slot id("
                            + MOCK_SIM_DEFAULT_SLOTID
                            + ").");
            mPhysicalSlotId = MOCK_SIM_DEFAULT_SLOTID;
        } else {
            mPhysicalSlotId = slotId;
        }

        if (simProfileId >= 0 && simProfileId < MOCK_SIM_PROFILE_ID_MAX) {
            mSimProfileIds[portIndex] = simProfileId;
            Log.i(
                    mTag,
                    "Load SIM profile ID: "
                            + mSimProfileIds[portIndex]
                            + " into physical slot"
                            + "[" + mPhysicalSlotId
                            + "] Port " + portIndex);
        } else {
            mSimProfileIds[portIndex] = MOCK_SIM_PROFILE_ID_DEFAULT;
            Log.e(
                    mTag,
                    "SIM Absent on physical slot"
                            + "[" + mPhysicalSlotId
                            + "] Port " + portIndex
                            + ". Not support SIM card ID: "
                            + simProfileId);
        }

        // Initiate slot status
        initMockSimSlot(portIndex);

        // Load SIM profile data
        loadMockSimCard(portIndex);
    }

    private void initMockSimSlot(int portIndex) {
        // We only apply standard hardware defaults (like setting the slot to active)
        // for the primary port (0). Secondary ports (1) are managed explicitly
        // by the MEP configuration logic and should start inactive.
        if (portIndex != DEFAULT_PORT_INDEX) {
            return;
        }
        switch (mPhysicalSlotId) {
            case MOCK_SIM_SLOT_1 -> {
                // Initialize Physical Slot 1. This is typically the pSIM slot.
                mLogicalSlotIds[0] = DEFAULT_SIM1_LOGICAL_SLOT_ID;
                mSlotPortIds[0] = DEFAULT_SIM1_PORT_ID;
                mIsSlotPortActives[0] = DEFAULT_SIM1_PORT_ACTIVE;
                // If the device is eSIM-only, there is no pSIM.
                // The single physical slot (Slot 1) acts as the eSIM.
                // Therefore, we use the default eSIM presence (SIM2) if it's an eSIM-only device.
                if (IS_ESIM_ONLY_DEVICE) {
                    mIsCardPresent = DEFAULT_SIM2_CARD_PRESENT;
                } else {
                    mIsCardPresent = DEFAULT_SIM1_CARD_PRESENT;
                }
                Log.d(mTag, "[initMockSimSlot] mIsCardPresent " + mIsCardPresent);
            }
            case MOCK_SIM_SLOT_2 -> {
                // Initialize Physical Slot 2. This is typically the eSIM slot.
                mLogicalSlotIds[0] = DEFAULT_SIM2_LOGICAL_SLOT_ID;
                mSlotPortIds[0] = DEFAULT_SIM2_PORT_ID;
                mIsSlotPortActives[0] = DEFAULT_SIM2_PORT_ACTIVE;
                mIsCardPresent = DEFAULT_SIM2_CARD_PRESENT;
            }
            case MOCK_SIM_SLOT_3 -> {
                // Initialize Physical Slot 3 (if applicable).
                mLogicalSlotIds[0] = DEFAULT_SIM3_LOGICAL_SLOT_ID;
                mSlotPortIds[0] = DEFAULT_SIM3_PORT_ID;
                mIsSlotPortActives[0] = DEFAULT_SIM3_PORT_ACTIVE;
                mIsCardPresent = DEFAULT_SIM3_CARD_PRESENT;
            }
        }
    }

    private int convertMockSimPinState(String pinstate) {
        int mocksim_pinstate = PinState.UNKNOWN;
        switch (pinstate) {
            case "PINSTATE_UNKNOWN":
                mocksim_pinstate = PinState.UNKNOWN;
                break;
            case "PINSTATE_ENABLED_NOT_VERIFIED":
                mocksim_pinstate = PinState.ENABLED_NOT_VERIFIED;
                break;
            case "PINSTATE_ENABLED_VERIFIED":
                mocksim_pinstate = PinState.ENABLED_VERIFIED;
                break;
            case "PINSTATE_DISABLED":
                mocksim_pinstate = PinState.DISABLED;
                break;
            case "PINSTATE_ENABLED_BLOCKED":
                mocksim_pinstate = PinState.ENABLED_BLOCKED;
                break;
            case "PINSTATE_ENABLED_PERM_BLOCKED":
                mocksim_pinstate = PinState.ENABLED_PERM_BLOCKED;
                break;
        }

        return mocksim_pinstate;
    }

    private int convertMockSimAppType(String apptype) {
        int mocksim_apptype = AppStatus.APP_TYPE_UNKNOWN;
        switch (apptype) {
            case "APPTYPE_UNKNOWN":
                mocksim_apptype = AppStatus.APP_TYPE_UNKNOWN;
                break;
            case "APPTYPE_SIM":
                mocksim_apptype = AppStatus.APP_TYPE_SIM;
                break;
            case "APPTYPE_USIM":
                mocksim_apptype = AppStatus.APP_TYPE_USIM;
                break;
            case "APPTYPE_RUIM":
                mocksim_apptype = AppStatus.APP_TYPE_RUIM;
                break;
            case "APPTYPE_CSIM":
                mocksim_apptype = AppStatus.APP_TYPE_CSIM;
                break;
            case "APPTYPE_ISIM":
                mocksim_apptype = AppStatus.APP_TYPE_ISIM;
                break;
        }

        return mocksim_apptype;
    }

    private int convertMockSimAppState(String appstate) {
        int mocksim_appstate = AppStatus.APP_STATE_UNKNOWN;
        switch (appstate) {
            case "APPSTATE_UNKNOWN":
                mocksim_appstate = AppStatus.APP_STATE_UNKNOWN;
                break;
            case "APPSTATE_DETECTED":
                mocksim_appstate = AppStatus.APP_STATE_DETECTED;
                break;
            case "APPSTATE_PIN":
                mocksim_appstate = AppStatus.APP_STATE_PIN;
                break;
            case "APPSTATE_PUK":
                mocksim_appstate = AppStatus.APP_STATE_PUK;
                break;
            case "APPSTATE_SUBSCRIPTION_PERSO":
                mocksim_appstate = AppStatus.APP_STATE_SUBSCRIPTION_PERSO;
                break;
            case "APPSTATE_READY":
                mocksim_appstate = AppStatus.APP_STATE_READY;
                break;
        }
        return mocksim_appstate;
    }

    private int convertMockSimFacilityLock(String lock) {
        int facilitylock = 0;
        switch (lock) {
            case "LOCK_ENABLED":
                facilitylock = 1;
                break;
            case "LOCK_DISABLED":
                facilitylock = 0;
                break;
        }
        return facilitylock;
    }

    private int getSimAppDataIndexByAid(String aid, int portIndex) {
        for (int idx = 0; idx < mSimAppLists[portIndex].size(); idx++) {
            if (aid.equals(mSimAppLists[portIndex].get(idx).getAid())) {
                return idx;
            }
        }
        return -1;
    }

    private String[] extractImsi(String imsi, int mncDigit) {
        String[] result = null;

        Log.d(mTag, "IMSI = " + imsi + ", mnc-digit = " + mncDigit);

        if (imsi.length() > 15 && imsi.length() < 5) {
            Log.d(mTag, "Invalid IMSI length.");
            return result;
        }

        if (mncDigit != 2 && mncDigit != 3) {
            Log.d(mTag, "Invalid mnc length.");
            return result;
        }

        result = new String[3];
        result[0] = imsi.substring(0, 3); // MCC
        result[1] = imsi.substring(3, 3 + mncDigit); // MNC
        result[2] = imsi.substring(3 + mncDigit, imsi.length()); // MSIN

        Log.d(mTag, "MCC = " + result[0] + " MNC = " + result[1] + " MSIN = " + result[2]);

        return result;
    }

    /**
     * Stores EF (Elementary File) data for a specific SIM application.
     *
     * @return true if the EF data was successfully stored; false if the data was invalid,
     *         the application index was not found, or the EF type is not supported.
     */
    private boolean storeEfData(
            int portIndex, String aid, String name, String id, String command, String[] value) {
        boolean result = true;

        if (value == null) {
            Log.e(mTag, "Invalid value of EF field - " + name + "(" + id + ")");
            return false;
        }

        int appIdx = getSimAppDataIndexByAid(aid, portIndex);
        if (appIdx < 0 || appIdx >= mSimAppLists[portIndex].size()) {
            Log.e(mTag, "Invalid App Index for AID: " + aid);
            return false;
        }

        switch (name) {
            case "EF_IMSI":
                // Verify IMSI format (MCC + MNC + MSIN)
                // value[0] = MCC (Mobile Country Code)
                // value[1] = MNC (Mobile Network Code)
                // value[2] = MSIN (Mobile Subscription Identification Number)
                if (value.length == 3
                        && value[0] != null
                        && value[0].length() == 3
                        && value[1] != null
                        && (value[1].length() == 2 || value[1].length() == 3)
                        && value[2] != null
                        && value[2].length() > 0
                        && (value[0].length() + value[1].length() + value[2].length() <= 15)) {
                    mSimAppLists[portIndex]
                            .get(appIdx)
                            .setImsi(value[0], value[1], value[2]);
                } else {
                    result = false;
                    Log.e(mTag, "Invalid value for EF field - " + name + "(" + id + ")");
                }
                break;
            case "EF_ICCID":
                if (command.length() > 2
                        && Integer.parseInt(command.substring(2), 16) == COMMAND_READ_BINARY) {
                    mSimAppLists[portIndex].get(appIdx).setIccid(value[0]);
                } else if (command.length() > 2
                        && Integer.parseInt(command.substring(2), 16) == COMMAND_GET_RESPONSE) {
                    mSimAppLists[portIndex].get(appIdx).setIccidInfo(value[0]);
                } else {
                    Log.e(mTag, "No valid Iccid data found");
                    result = false;
                }
                break;
            case "EF_GID1":
                if (command.length() > 2
                        && Integer.parseInt(command.substring(2), 16) == COMMAND_READ_BINARY) {
                    mSimAppLists[portIndex].get(appIdx).setGid1(value[0]);
                } else if (command.length() > 2
                        && Integer.parseInt(command.substring(2), 16) == COMMAND_GET_RESPONSE) {
                    mSimAppLists[portIndex].get(appIdx).setGid1Info(value[0]);
                } else {
                    Log.e(mTag, "No valid GID1 found");
                    result = false;
                }
                break;
            default:
                result = false;
                Log.w(mTag, "Not support EF field - " + name + "(" + id + ")");
                break;
        }
        return result;
    }

    private boolean loadSimProfileFromXml(int portIndex) {
        boolean result = true;

        if (mSimProfileInfoList == null) {
            Log.e(mTag, "No support SIM profile list.");
            return false;
        }

        int profileId = mSimProfileIds[portIndex];

        try {
            String file = mSimProfileInfoList[profileId].getXmlFile();
            int event;
            XmlPullParser parser = Xml.newPullParser();
            InputStream input;
            boolean mocksim_validation = false;
            boolean mocksim_pf_validatiion = false;
            boolean mocksim_mf_validation = false;
            boolean mocksim_adflc_validation = false;
            int appidx = 0;
            int fd_lock = 0;
            int sc_lock = 0;
            String adf_aid = "";
            String adflc_aid = "";

            input = mContext.getAssets().open(file);
            parser.setInput(input, null);
            while (result && (event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                switch (event) {
                    case XmlPullParser.START_TAG:
                        if (MOCK_SIM_TAG.equals(parser.getName())) {
                            int numofapp = Integer.parseInt(parser.getAttributeValue(0));
                            mATR = parser.getAttributeValue(1);
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_SIM_TAG
                                            + ": numofapp = "
                                            + numofapp
                                            + " atr = "
                                            + mATR);
                            mSimApps[portIndex] = new AppStatus[numofapp];
                            if (mSimApps[portIndex] == null) {
                                Log.e(mTag, "Create SIM app failed!");
                                result = false;
                                break;
                            }
                            mocksim_validation = true;
                        } else if (mocksim_validation
                                && MOCK_SIM_PROFILE_TAG.equals(parser.getName())
                                && appidx < mSimApps[portIndex].length) {
                            int id = Integer.parseInt(parser.getAttributeValue(0));
                            int type = convertMockSimAppType(parser.getAttributeValue(1));
                            mSimApps[portIndex][appidx] = new AppStatus();
                            mSimApps[portIndex][appidx].appType = type;
                            switch (type) {
                                case AppStatus.APP_TYPE_SIM:
                                case AppStatus.APP_TYPE_USIM:
                                    mSimProfileInfoList[profileId].setGsmAppIndex(id);
                                    break;
                                case AppStatus.APP_TYPE_CSIM:
                                case AppStatus.APP_TYPE_RUIM:
                                    mSimProfileInfoList[profileId].setCdmaAppIndex(id);
                                    break;
                                case AppStatus.APP_TYPE_ISIM:
                                    mSimProfileInfoList[profileId].setImsAppIndex(id);
                                    break;
                            }
                            Log.d(
                                    mTag,
                                    "Found "
                                            + "[" + MOCK_SIM_PROFILE_TAG
                                            + "]: id = "
                                            + id
                                            + " type = "
                                            + parser.getAttributeValue(1)
                                            + " ("
                                            + type
                                            + ")========");
                            mocksim_pf_validatiion = true;
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && MOCK_PIN_PROFILE_TAG.equals(parser.getName())) {
                            int appstate = convertMockSimAppState(parser.getAttributeValue(0));
                            mSimApps[portIndex][appidx].appState = appstate;
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_PIN_PROFILE_TAG
                                            + ": appstate = "
                                            + parser.getAttributeValue(0)
                                            + " ("
                                            + appstate
                                            + ")");
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && MOCK_PIN1_STATE_TAG.equals(parser.getName())) {
                            String state = parser.nextText();
                            int pin1state = convertMockSimPinState(state);
                            mSimApps[portIndex][appidx].pin1 = pin1state;
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_PIN1_STATE_TAG
                                            + " = "
                                            + state
                                            + " ("
                                            + pin1state
                                            + ")");
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && MOCK_PIN2_STATE_TAG.equals(parser.getName())) {
                            String state = parser.nextText();
                            int pin2state = convertMockSimPinState(state);
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_PIN2_STATE_TAG
                                            + " = "
                                            + state
                                            + " ("
                                            + pin2state
                                            + ")");
                            mSimApps[portIndex][appidx].pin2 = pin2state;
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && MOCK_FACILITY_LOCK_FD_TAG.equals(parser.getName())) {
                            fd_lock = convertMockSimFacilityLock(parser.nextText());
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_FACILITY_LOCK_FD_TAG
                                            + ": fd lock = "
                                            + fd_lock);
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && MOCK_FACILITY_LOCK_SC_TAG.equals(parser.getName())) {
                            sc_lock = convertMockSimFacilityLock(parser.nextText());
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_FACILITY_LOCK_SC_TAG
                                            + ": sc lock = "
                                            + sc_lock);
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && MOCK_MF_TAG.equals(parser.getName())) {
                            SimAppData simAppData;
                            String name = parser.getAttributeValue(0);
                            String path = parser.getAttributeValue(1);
                            simAppData = new SimAppData(appidx, name, path);
                            if (simAppData == null) {
                                Log.e(mTag, "Create SIM app data failed!");
                                result = false;
                                break;
                            }
                            mSimAppLists[portIndex].add(simAppData);
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_MF_TAG
                                            + ": name = "
                                            + name
                                            + " path = "
                                            + path);
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && !mocksim_mf_validation
                                && MOCK_EF_DIR_TAG.equals(parser.getName())) {
                            SimAppData simAppData;
                            String name = parser.getAttributeValue(0);
                            boolean curr_active = Boolean.parseBoolean(parser.getAttributeValue(1));
                            String aid = parser.nextText();
                            simAppData = new SimAppData(appidx, aid, name, curr_active);
                            if (simAppData == null) {
                                Log.e(mTag, "Create SIM app data failed!");
                                result = false;
                                break;
                            }
                            simAppData.setFdnStatus(fd_lock);
                            simAppData.setPin1State(sc_lock);
                            mSimAppLists[portIndex].add(simAppData);
                            if (curr_active) {
                                mSimApps[portIndex][appidx].aidPtr = aid;
                            }
                            Log.d(
                                    mTag,
                                    "Found "
                                            + MOCK_EF_DIR_TAG
                                            + ": name = "
                                            + name
                                            + ": curr_active = "
                                            + curr_active
                                            + " aid = "
                                            + aid);
                            mocksim_mf_validation = true;
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && mocksim_mf_validation
                                && MOCK_ADF_TAG.equals(parser.getName())) {
                            String aid = parser.getAttributeValue(0);
                            Log.d(mTag, "Found " + MOCK_ADF_TAG + ": aid = " + aid);
                            adf_aid = aid;
                        } else if (mocksim_validation
                                && mocksim_pf_validatiion
                                && mocksim_mf_validation
                                && (adf_aid.length() > 0)
                                && MOCK_EF_TAG.equals(parser.getName())) {
                            String name = parser.getAttributeValue(0);
                            String id = parser.getAttributeValue(1);
                            String command = parser.getAttributeValue(2);
                            String[] value;
                            switch (id) {
                                case "6F07": // EF_IMSI
                                    int mncDigit = Integer.parseInt(parser.getAttributeValue(3));
                                    String imsi = parser.nextText();
                                    value = extractImsi(imsi, mncDigit);
                                    if (value != null
                                            && storeEfData(portIndex, adf_aid, name, id, command, value)) {
                                        Log.d(
                                                mTag,
                                                "Found "
                                                        + MOCK_EF_TAG
                                                        + ": name = "
                                                        + name
                                                        + " id = "
                                                        + id
                                                        + " command = "
                                                        + command
                                                        + " value = "
                                                        + imsi
                                                        + " with mnc-digit = "
                                                        + mncDigit);
                                    }
                                    break;
                                default:
                                    value = new String[1];
                                    if (value != null) {
                                        value[0] = parser.nextText();
                                        if (storeEfData(portIndex, adf_aid, name, id, command, value)) {
                                            Log.d(
                                                    mTag,
                                                    "Found "
                                                            + MOCK_EF_TAG
                                                            + ": name = "
                                                            + name
                                                            + " id = "
                                                            + id
                                                            + " command = "
                                                            + command
                                                            + " value = "
                                                            + value[0]);
                                        }
                                    }
                                    break;
                            }
                        } else if (mocksim_validation && MOCK_ADFLC_TAG.equals(parser.getName())) {
                            String aid = parser.getAttributeValue(0);
                            String name = parser.getAttributeValue(1);
                            Log.d(mTag,
                                    "Found " + MOCK_ADFLC_TAG + ", aid=" + aid + ", name=" + name);
                            if (!TextUtils.isEmpty(aid)) {
                                adflc_aid = aid;
                                synchronized (mSimIoDataMaps[portIndex]) {
                                    mSimIoDataMaps[portIndex].put(aid, new ArrayList<>());
                                }
                            }
                            mocksim_adflc_validation = true;
                        } else if (mocksim_adflc_validation
                                && MOCK_IO_TAG.equals(parser.getName())) {
                            String fileid = parser.getAttributeValue(0);
                            String command = parser.getAttributeValue(1);
                            String sw1 = parser.getAttributeValue(2);
                            String sw2 = parser.getAttributeValue(3);
                            String response = parser.nextText();
                            synchronized (mSimIoDataMaps[portIndex]) {
                                mSimIoDataMaps[portIndex].get(adflc_aid).add(
                                        new SimIoData(fileid, command, sw1, sw2, response));
                            }
                            Log.d(mTag, "Found " + MOCK_IO_TAG + ": fileid=" + fileid + " command="
                                    + command + " adflc_aid=" + adflc_aid);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (mocksim_validation && MOCK_SIM_PROFILE_TAG.equals(parser.getName())) {
                            appidx++;
                            mocksim_pf_validatiion = false;
                            mocksim_mf_validation = false;
                        } else if (mocksim_validation && MOCK_ADF_TAG.equals(parser.getName())) {
                            adf_aid = "";
                        } else if (mocksim_validation && MOCK_ADFLC_TAG.equals(parser.getName())) {
                            mocksim_adflc_validation = false;
                            adflc_aid = "";
                        }
                        break;
                }
            }
            Log.d(mTag, "Totally create " + Math.min(mSimApps[portIndex].length, appidx) + " SIM profiles");
            mSimProfileInfoList[profileId].setNumOfSimApp(appidx);
            input.close();
        } catch (Exception e) {
            Log.e(mTag, "Exception error: " + e);
            result = false;
        }

        return result;
    }

    private boolean loadSimApp(int portIndex) {
        boolean result = true;

        if (mSimAppLists[portIndex] == null) {
            mSimAppLists[portIndex] = new ArrayList<SimAppData>();
        } else {
            mSimAppLists[portIndex].clear();
        }

        if (mSimProfileIds[portIndex] == MOCK_SIM_PROFILE_ID_DEFAULT
                || mSimProfileInfoList[mSimProfileIds[portIndex]].getXmlFile().length() == 0) {
            Log.d(mTag, "SIM absent case");
            mSimApps[portIndex] = new AppStatus[0];
            if (mSimApps[portIndex] == null) {
                Log.e(mTag, "Create SIM app failed!");
                result = false;
            }
        } else {
            result = loadSimProfileFromXml(portIndex);
        }

        return result;
    }

    /**
     * Loads the SIM card profile for a specific port.
     *
     * <p>This method handles the initialization of card-level properties (ATR, EID, Presence)
     * and triggers the loading of SIM applications. It supports MEP (Multiple Enabled Profiles)
     * by checking the state of all ports on the physical slot.
     *
     * @param portIndex The index of the port to load (0 or 1).
     * @return true if the operation is successful, otherwise false.
     */
    private boolean loadMockSimCard(int portIndex) {
        int profileId = mSimProfileIds[portIndex];

        // For MEP (E+E), the card is physically present if at least one profile is active,
        // even if the specific port being modified is set to "Default" (No Profile).
        boolean anyProfile = false;
        for (int id : mSimProfileIds) {
            if (id != MOCK_SIM_PROFILE_ID_DEFAULT) anyProfile = true;
        }
        mIsCardPresent = anyProfile;

        if (profileId != MOCK_SIM_PROFILE_ID_DEFAULT) {
            switch (mPhysicalSlotId) {
                case MOCK_SIM_SLOT_1 -> { // pSIM or eSIM
                    // eSIM-only mode: Physical Slot 0 acts as the eSIM
                    // These values represent the physical characteristics of the card (EID, ATR)
                    // and its default state (Present/Absent) when no profiles are active.
                    if (IS_ESIM_ONLY_DEVICE) {
                        mEID = DEFAULT_SIM2_EID;
                        mATR = DEFAULT_SIM2_EID;
                    } else {
                        // Standard pSIM: Physical Slot 0 acts as the pSIM (Slot 1).
                        mEID = DEFAULT_SIM1_EID;
                    }
                }
                case MOCK_SIM_SLOT_2 -> { // eSIM
                    // Standard eSIM: Physical Slot 1.
                    mATR = DEFAULT_SIM2_ATR;
                    mEID = DEFAULT_SIM2_EID;
                }
                case MOCK_SIM_SLOT_3 -> { // eSIM
                    // Additional eSIM slot (if supported).
                    mATR = DEFAULT_SIM3_ATR;
                    mEID = DEFAULT_SIM3_EID;
                }
            }
            mUniversalPinState = PinState.DISABLED;
            mIsCardPresent = true;
        } else {
            // If defaulting a port, check if entire card is absent/default
            if (!anyProfile) {
                switch (mPhysicalSlotId) {
                    case MOCK_SIM_SLOT_1 -> {
                        mUniversalPinState = DEFAULT_SIM1_UNIVERSAL_PIN_STATE;
                        if (IS_ESIM_ONLY_DEVICE) {
                            mATR = DEFAULT_SIM2_ATR;
                            mEID = DEFAULT_SIM2_EID;
                            mIsCardPresent = DEFAULT_SIM2_CARD_PRESENT;
                        } else {
                            mATR = DEFAULT_SIM1_ATR;
                            mEID = DEFAULT_SIM1_EID;
                            mIsCardPresent = DEFAULT_SIM1_CARD_PRESENT;
                        }
                    }
                    case MOCK_SIM_SLOT_2 -> {
                        mATR = DEFAULT_SIM2_ATR;
                        mEID = DEFAULT_SIM2_EID;
                        mUniversalPinState = DEFAULT_SIM2_UNIVERSAL_PIN_STATE;
                        mIsCardPresent = DEFAULT_SIM2_CARD_PRESENT;
                    }
                    case MOCK_SIM_SLOT_3 -> {
                        mATR = DEFAULT_SIM3_ATR;
                        mEID = DEFAULT_SIM3_EID;
                        mUniversalPinState = DEFAULT_SIM3_UNIVERSAL_PIN_STATE;
                        mIsCardPresent = DEFAULT_SIM3_CARD_PRESENT;
                    }
                }
            }
        }
        Log.i(mTag, "Load SIM profile ID: " + profileId
            + " into physical slot[" + mPhysicalSlotId + "] Port " + portIndex
            + " mIsCardPresent: " + mIsCardPresent
            + "mEID: " + mEID);
        return loadSimApp(portIndex);
    }

    public boolean loadSimCard(int simprofileid, int portIndex) {
        if (portIndex < DEFAULT_PORT_INDEX || portIndex >= MAX_PORTS) return false;
        boolean result = true;
        if (simprofileid >= MOCK_SIM_PROFILE_ID_DEFAULT
                && simprofileid < MOCK_SIM_PROFILE_ID_MAX) {
            mSimProfileIds[portIndex] = simprofileid;
            result = loadMockSimCard(portIndex);
        } else {
            result = false;
        }
        return result;
    }

    public boolean isSlotPortActive(int portIndex) {
        return (portIndex < MAX_PORTS) ? mIsSlotPortActives[portIndex] : false;
    }

    public void setSlotPortActive(int portIndex, boolean active) {
        if (portIndex < MAX_PORTS) {
            mIsSlotPortActives[portIndex] = active;
        }
    }

    public boolean isCardPresent() {
        return mIsCardPresent;
    }

    public int getNumOfSimPortInfo() {
        if (mPhysicalSlotId == MOCK_SIM_SLOT_1) {
            return (IS_ESIM_ONLY_DEVICE) ? MAX_PORTS : 1;
        }
        if (mPhysicalSlotId == MOCK_SIM_SLOT_2 || mPhysicalSlotId == MOCK_SIM_SLOT_3) {
            return MAX_PORTS;
        }
        return 1;
    }

    public int getPhysicalSlotId() {
        return mPhysicalSlotId;
    }

    public int getLogicalSlotId(int portIndex) {
        return (portIndex < MAX_PORTS) ? mLogicalSlotIds[portIndex] : -1;
    }

    public void setLogicalSlotId(int portIndex, int logicalId) {
        if (portIndex < MAX_PORTS) {
            mLogicalSlotIds[portIndex] = logicalId;
        }
    }

    public int getSlotPortId(int portIndex) {
        return (portIndex < MAX_PORTS) ? mSlotPortIds[portIndex] : -1;
    }

    public String getEID() {
        return mEID;
    }

    public boolean setATR(String atr) {
        // TODO: add any ATR format check
        mATR = atr;
        return true;
    }

    public String getATR() {
        return mATR;
    }

    public boolean setICCID(String iccid, int portIndex) {
        if (portIndex < DEFAULT_PORT_INDEX || portIndex >= MAX_PORTS) return false;
        boolean result = false;
        SimAppData activeSimAppData = getActiveSimAppData(portIndex);

        // TODO: add iccid format check
        if (activeSimAppData != null) {
            String iccidInfo = activeSimAppData.getIccidInfo();
            int dataFileSize = iccid.length() / 2;
            String dataFileSizeStr = Integer.toString(dataFileSize, 16);

            Log.d(mTag, "Data file size = " + dataFileSizeStr);
            if (dataFileSizeStr.length() <= 4) {
                dataFileSizeStr = String.format("%04x", dataFileSize).toUpperCase(Locale.ROOT);
                // Data file size index is 2 and 3 in byte array of iccid info data.
                iccidInfo = iccidInfo.substring(0, 4) + dataFileSizeStr + iccidInfo.substring(8);
                Log.d(mTag, "Update iccid info = " + iccidInfo);
                activeSimAppData.setIccidInfo(iccidInfo);
                activeSimAppData.setIccid(iccid);
                result = true;
            } else {
                Log.e(mTag, "Data file size(" + iccidInfo.length() + ") is too large.");
            }
        } else {
            Log.e(mTag, "activeSimAppData = null");
        }

        return result;
    }

    public String getICCID(int portIndex) {
        String iccid = "";
        SimAppData activeSimAppData = getActiveSimAppData(portIndex);

        if (activeSimAppData != null) {
            iccid = activeSimAppData.getIccid();
        }

        return iccid;
    }

    public int getUniversalPinState() {
        return mUniversalPinState;
    }

    public int getGsmAppIndex(int portIndex) {
        if (portIndex < DEFAULT_PORT_INDEX || portIndex >= MAX_PORTS) return DEFAULT_GSM_APP_IDX;
        return mSimProfileInfoList[mSimProfileIds[portIndex]].getGsmAppIndex();
    }

    public int getCdmaAppIndex(int portIndex) {
        if (portIndex < DEFAULT_PORT_INDEX || portIndex >= MAX_PORTS) return DEFAULT_CDMA_APP_IDX;
        return mSimProfileInfoList[mSimProfileIds[portIndex]].getCdmaAppIndex();
    }

    public int getImsAppIndex(int portIndex) {
        if (portIndex < DEFAULT_PORT_INDEX || portIndex >= MAX_PORTS) return DEFAULT_IMS_APP_IDX;
        return mSimProfileInfoList[mSimProfileIds[portIndex]].getImsAppIndex();
    }

    public int getNumOfSimApp(int portIndex) {
        if (portIndex < DEFAULT_PORT_INDEX || portIndex >= MAX_PORTS) return DEFAULT_NUM_OF_SIM_APP;
        return mSimProfileInfoList[mSimProfileIds[portIndex]].getNumOfSimApp();
    }

    public AppStatus[] getSimApp(int portIndex) {
        if (portIndex < DEFAULT_PORT_INDEX || portIndex >= MAX_PORTS) return new AppStatus[0];
        return mSimApps[portIndex];
    }

    public List<SimAppData> getSimAppList(int portIndex) {
        if (portIndex < DEFAULT_PORT_INDEX || portIndex >= MAX_PORTS) return new ArrayList<>();
        return mSimAppLists[portIndex];
    }

    public SimAppData getActiveSimAppData(int portIndex) {
        if (portIndex < DEFAULT_PORT_INDEX || portIndex >= MAX_PORTS) return null;
        SimAppData activeSimAppData = null;

        for (int simAppIdx = 0; simAppIdx < mSimAppLists[portIndex].size(); simAppIdx++) {
            if (mSimAppLists[portIndex].get(simAppIdx).isCurrentActive()) {
                activeSimAppData = mSimAppLists[portIndex].get(simAppIdx);
                break;
            }
        }

        return activeSimAppData;
    }

    public String getActiveSimAppId(int portIndex) {
        String aid = "";
        SimAppData activeSimAppData = getActiveSimAppData(portIndex);

        if (activeSimAppData != null) {
            aid = activeSimAppData.getAid();
        }

        return aid;
    }

    private boolean setMcc(String mcc, int portIndex) {
        boolean result = false;

        if (mcc.length() == 3) {
            SimAppData activeSimAppData = getActiveSimAppData(portIndex);
            if (activeSimAppData != null) {
                activeSimAppData.setMcc(mcc);
                result = true;
            }
        }

        return result;
    }

    private boolean setMnc(String mnc, int portIndex) {
        boolean result = false;

        if (mnc.length() == 2 || mnc.length() == 3) {
            SimAppData activeSimAppData = getActiveSimAppData(portIndex);
            if (activeSimAppData != null) {
                activeSimAppData.setMnc(mnc);
                result = true;
            }
        }

        return result;
    }

    public String getMccMnc(int portIndex) {
        String mcc;
        String mnc;
        String result = "";
        SimAppData activeSimAppData = getActiveSimAppData(portIndex);

        if (activeSimAppData != null) {
            mcc = activeSimAppData.getMcc();
            mnc = activeSimAppData.getMnc();
            if (mcc != null
                    && mcc.length() == 3
                    && mnc != null
                    && (mnc.length() == 2 || mnc.length() == 3)) {
                result = mcc + mnc;
            } else {
                Log.e(mTag, "Invalid Mcc or Mnc.");
            }
        }
        return result;
    }

    public String getMsin(int portIndex) {
        String result = "";
        SimAppData activeSimAppData = getActiveSimAppData(portIndex);

        if (activeSimAppData != null) {
            result = activeSimAppData.getMsin();
            if (result.length() <= 0 || result.length() > 10) {
                Log.e(mTag, "Invalid Msin.");
            }
        }

        return result;
    }

    public boolean setImsi(String mcc, String mnc, String msin, int portIndex) {
        boolean result = false;

        if (msin.length() > 0 && (mcc.length() + mnc.length() + msin.length()) <= 15) {
            SimAppData activeSimAppData = getActiveSimAppData(portIndex);
            if (activeSimAppData != null) {
                setMcc(mcc, portIndex);
                setMnc(mnc, portIndex);
                activeSimAppData.setMsin(msin);
                result = true;
            } else {
                Log.e(mTag, "activeSimAppData = null");
            }
        } else {
            Log.e(mTag, "Invalid IMSI");
        }

        return result;
    }

    public String getImsi(int portIndex) {
        String imsi = "";
        String mccmnc;
        String msin;
        SimAppData activeSimAppData = getActiveSimAppData(portIndex);

        if (activeSimAppData != null) {
            mccmnc = getMccMnc(portIndex);
            msin = activeSimAppData.getMsin();
            if (mccmnc.length() > 0
                    && msin != null
                    && msin.length() > 0
                    && (mccmnc.length() + msin.length()) <= 15) {
                imsi = mccmnc + msin;
            } else {
                Log.e(mTag, "Invalid Imsi.");
            }
        }
        return imsi;
    }

    public Map<String, List<SimIoData>> getSimIoMap(int portIndex) {
        if (portIndex < MAX_PORTS) {
            synchronized (mSimIoDataMaps[portIndex]) {
                return Collections.unmodifiableMap(mSimIoDataMaps[portIndex]);
            }
        }
        return Collections.emptyMap();
    }
}