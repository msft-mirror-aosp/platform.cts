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

package android.telephony.cts;

import static android.service.messaging.AlternativeMessageTransportService.UPGRADE_STATUS_ACCEPTED;
import static android.service.messaging.AlternativeMessageTransportService.UPGRADE_STATUS_REJECTED;

import static androidx.test.InstrumentationRegistry.getContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.CarrierConfigManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.DefaultSmsAppHelper;
import android.text.TextUtils;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;

import com.google.android.mms.ContentType;
import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.GenericPdu;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduComposer;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.SendConf;
import com.google.android.mms.pdu.SendReq;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test sending MMS using {@link android.telephony.SmsManager}.
 */
public class MmsTest {
    private static final String TAG = "MmsTest";

    private static final String ACTION_MMS_SENT = "CTS_MMS_SENT_ACTION";
    private static final String ACTION_MMS_DOWNLOAD = "CTS_MMS_DOWNLOAD_ACTION";
    public static final String ACTION_WAP_PUSH_DELIVER_DEFAULT_APP =
            "CTS_WAP_PUSH_DELIVER_DEFAULT_APP_ACTION";
    public static final String MESSAGE_UPGRADE_APP = "android.telephony.cts.msgupgrade";
    private static final String ACTION_MESSAGE_UPGRADE_RECEIVED =
            "android.telephony.cts.msgupgrade.ACTION_MESSAGE_UPGRADE_RECEIVED";
    private static final String EXTRA_UPGRADE_STATUS =
            "android.telephony.cts.msgupgrade.EXTRA_UPGRADE_STATUS";
    private static final long DEFAULT_EXPIRY_TIME = 7 * 24 * 60 * 60;
    private static final int DEFAULT_PRIORITY = PduHeaders.PRIORITY_NORMAL;
    private static final long MESSAGE_ID = 912412L;

    private static final String SUBJECT = "CTS MMS Test";
    private static final String SUBJECT_UPGRADE_ACCEPTED =
            "TEST_UPGRADE:delay=1000;status=" + UPGRADE_STATUS_ACCEPTED;
    private static final String SUBJECT_UPGRADE_REJECTED =
            "TEST_UPGRADE:delay=1000;status=" + UPGRADE_STATUS_REJECTED;
    private static final String MESSAGE_BODY = "CTS MMS test message body";
    private static final String TEXT_PART_FILENAME = "text_0.txt";
    private static final String sSmilText =
            "<smil><head><layout><root-layout/><region height=\"100%%\" id=\"Text\" left=\"0%%\""
                + " top=\"0%%\" width=\"100%%\"/></layout></head><body><par dur=\"8000ms\"><text"
                + " src=\"%s\" region=\"Text\"/></par></body></smil>";
    private static final String IMAGE_PART_FILENAME = "image_0.jpg";
    private static final String sSmilWithImageText =
            "<smil><head><layout><root-layout width=\"320\" height=\"480\"/><region id=\"Image\""
                    + " left=\"0\" top=\"0\" width=\"320\" height=\"240\" fit=\"meet\"/><region"
                    + " id=\"Text\" left=\"0\" top=\"240\" width=\"320\" height=\"240\""
                    + " fit=\"meet\"/></layout></head><body><par dur=\"8000ms\"><img src=\""
                    + IMAGE_PART_FILENAME
                    + "\" region=\"Image\"/>"
                    + "<text src=\""
                    + TEXT_PART_FILENAME
                    + "\" region=\"Text\"/>"
                    + "</par></body></smil>";

    private static final long SENT_TIMEOUT = 1000 * 60 * 5; // 5 minutes
    private static final int SHORT_TIME_OUT = 1000 * 5; // 5 seconds
    private static final long NO_CALLS_TIMEOUT = 1000; // 1 second
    // TODO(b/492408141): Remove this delay once DMA broadcasts are deterministic.
    private static final int DMA_CHANGE_PROPAGATION_DELAY = 100;

    private static final String PROVIDER_AUTHORITY = "telephonyctstest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private Random mRandom;
    private SentReceiver mSentReceiver;
    private SentReceiver mDeliveryReceiver;
    private MessageUpgradeBroadcastReceiver mMessageUpgradeReceiver;
    private TelephonyManager mTelephonyManager;
    @Nullable private String mOriginalDefaultSmsApp;
    private static CarrierConfigReceiver sCarrierConfigReceiver;

    private static class SentReceiver extends BroadcastReceiver {
        private final Object mLock;
        private boolean mSuccess;
        private boolean mDone;
        private int mExpectedErrorResultCode;
        private String mAction;
        private boolean mSkipSendConfPduParsing;

        SentReceiver(String action) {
            mLock = new Object();
            mSuccess = false;
            mDone = false;
            mExpectedErrorResultCode = Activity.RESULT_OK;
            mAction = action;
            mSkipSendConfPduParsing = false;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "onReceive Action " + intent.getAction() + ", mAction " + mAction);

            switch (intent.getAction()) {
                case ACTION_MMS_SENT:
                    final int resultCode = getResultCode();
                    if (resultCode == Activity.RESULT_OK) {
                        if (mSkipSendConfPduParsing) {
                            mSuccess = true;
                            break;
                        }
                        final byte[] response = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA);
                        if (response != null) {
                            final GenericPdu pdu = new PduParser(
                                    response, shouldParseContentDisposition()).parse();
                            if (pdu != null && pdu instanceof SendConf) {
                                final SendConf sendConf = (SendConf) pdu;
                                if (sendConf.getResponseStatus() == PduHeaders.RESPONSE_STATUS_OK) {
                                    mSuccess = true;
                                } else {
                                    Log.e(TAG,
                                            "SendConf response status="
                                                    + sendConf.getResponseStatus());
                                }
                            } else {
                                Log.e(TAG, "Not a SendConf: " + (pdu != null
                                        ? pdu.getClass().getCanonicalName() : "NULL"));
                            }
                        } else {
                            Log.e(TAG, "Empty response");
                        }
                    } else {
                        Log.e(TAG, "Failure result=" + resultCode);
                        if (resultCode == mExpectedErrorResultCode) {
                            mSuccess = true;
                        }
                        if (resultCode == SmsManager.MMS_ERROR_HTTP_FAILURE) {
                            final int httpError = intent.getIntExtra(
                                    SmsManager.EXTRA_MMS_HTTP_STATUS,
                                    0);
                            Log.e(TAG, "HTTP failure=" + httpError);
                        }
                    }
                    break;
                case ACTION_WAP_PUSH_DELIVER_DEFAULT_APP:
                    mSuccess = true;
                    break;
            }

            if (intent.getAction().equals(mAction)) {
                synchronized (mLock) {
                    mDone = true;
                    mLock.notify();
                }
            }
        }

        public boolean waitForSuccess(long timeout) {
            synchronized(mLock) {
                final long startTime = SystemClock.elapsedRealtime();
                long waitTime = timeout;
                while (!mDone && waitTime > 0) {
                    try {
                        mLock.wait(waitTime);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    waitTime = timeout - (SystemClock.elapsedRealtime() - startTime);
                }
                Log.i(TAG, "Wait for sent: done=" + mDone + ", success=" + mSuccess);
                return mDone && mSuccess;
            }
        }

        public boolean verifyNoCalls(long timeout) {
            synchronized (mLock) {
                try {
                    mLock.wait(timeout);
                } catch (InterruptedException e) {
                    // Ignore
                }
                return (!mDone && !mSuccess);
            }
        }

        private void setExpectedErrorResultCode(int expectedErrorResultCode) {
            mExpectedErrorResultCode = expectedErrorResultCode;
        }

        private void setSkipSendConfPduParsing(boolean skipSendConfPduParsing) {
            mSkipSendConfPduParsing = skipSendConfPduParsing;
        }

        private void reset() {
            mSuccess = false;
            mDone = false;
            mExpectedErrorResultCode = Activity.RESULT_OK;
            mSkipSendConfPduParsing = false;
        }
    }

    /**
     * Setup before all tests.
     */
    @BeforeClass
    public static void beforeAllTests() {
        Log.i(TAG, "beforeAllTests");
        sCarrierConfigReceiver = new CarrierConfigReceiver();
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        getInstrumentation().getContext().registerReceiver(sCarrierConfigReceiver, filter);
    }

    /**
     * Clean up resources after all tests.
     */
    @AfterClass
    public static void afterAllTests() {
        Log.i(TAG, "afterAllTests");

        // Ensure there are no CarrierConfig overrides.
        clearOverrideCarrierConfig();

        if (sCarrierConfigReceiver != null) {
            getInstrumentation().getContext().unregisterReceiver(sCarrierConfigReceiver);
            sCarrierConfigReceiver = null;
        }
    }

    @Before
    public void setUp() throws Exception {
        mContext = getContext();
        mRandom = new Random();
        IntentFilter messageUpgradeIntentFilter = new IntentFilter(ACTION_MESSAGE_UPGRADE_RECEIVED);

        mSentReceiver = new SentReceiver(ACTION_MMS_SENT);
        mDeliveryReceiver = new SentReceiver(ACTION_WAP_PUSH_DELIVER_DEFAULT_APP);
        mMessageUpgradeReceiver =
                new MessageUpgradeBroadcastReceiver(ACTION_MESSAGE_UPGRADE_RECEIVED);

        mContext.registerReceiver(
                mSentReceiver, new IntentFilter(ACTION_MMS_SENT), Context.RECEIVER_EXPORTED);
        mContext.registerReceiver(
                mDeliveryReceiver,
                new IntentFilter(ACTION_WAP_PUSH_DELIVER_DEFAULT_APP),
                Context.RECEIVER_EXPORTED);
        mContext.registerReceiver(
                mMessageUpgradeReceiver,
                messageUpgradeIntentFilter,
                Context.RECEIVER_EXPORTED_UNAUDITED);

        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        assumeTrue(
                "Device does not have FEATURE_TELEPHONY_MESSAGING",
                mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING));
        mOriginalDefaultSmsApp = DefaultSmsAppHelper.getDefaultSmsApp(mContext);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @After
    public void tearDown() throws Exception {
        if (!TextUtils.isEmpty(mOriginalDefaultSmsApp)) {
            assertTrue(DefaultSmsAppHelper.setDefaultSmsApp(mContext, mOriginalDefaultSmsApp));
        }
        if (mSentReceiver != null) {
            mContext.unregisterReceiver(mSentReceiver);
            mSentReceiver = null;
        }
        if (mDeliveryReceiver != null) {
            mContext.unregisterReceiver(mDeliveryReceiver);
            mDeliveryReceiver = null;
        }
        if (mMessageUpgradeReceiver != null) {
            mContext.unregisterReceiver(mMessageUpgradeReceiver);
            mMessageUpgradeReceiver = null;
        }
    }

    @Test
    @Ignore("b/443345141 - Need to fix and re-enable this test.")
    @ApiTest(apis = "android.telephony.SmsManager#sendMultimediaMessage")
    public void testSendMmsMessage() throws Exception {
        Log.i("MmsTest", "testSendMmsMessage");
        SmsManager smsManager = mContext.getSystemService(SmsManager.class);

        // Testing the flow with CTS set as DMA
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        sendMmsMessage(0L /* messageId */, Activity.RESULT_OK, smsManager, true);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();

        // Testing the flow with MessageUpgradeApp set as DMA, which will promote the message
        Assume.assumeTrue("Skipping message upgrade: Flag is OFF", Flags.messagePromotion());
        try {
            DefaultSmsAppHelper.setDefaultSmsApp(mContext, MESSAGE_UPGRADE_APP);
            SystemClock.sleep(DMA_CHANGE_PROPAGATION_DELAY);

            // Message upgraded by DMA
            sendUpgradeMmsMessage(smsManager, SUBJECT_UPGRADE_ACCEPTED, UPGRADE_STATUS_ACCEPTED);

            // Message not upgraded, fallback to standard mms
            sendUpgradeMmsMessage(smsManager, SUBJECT_UPGRADE_REJECTED, UPGRADE_STATUS_REJECTED);
        } finally {
            DefaultSmsAppHelper.removeDefaultSmsAppRole(MESSAGE_UPGRADE_APP);
        }
    }

    @Test
    @ApiTest(apis = "android.telephony.SmsManager#sendMultimediaMessage")
    public void testSendMmsMessageWithInactiveSubscriptionId() {
        int inactiveSubId = 127;

        // Test non-default SMS app
        sendMmsMessage(0L /* messageId */, SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION,
                SmsManager.getSmsManagerForSubscriptionId(inactiveSubId), false);

        // Test default SMS app
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        sendMmsMessage(0L /* messageId */, SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION,
                SmsManager.getSmsManagerForSubscriptionId(inactiveSubId), true);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @Test
    @ApiTest(apis = "android.telephony.SmsManager#sendMultimediaMessage")
    public void testSendMmsMessageWithMmsDisabled() {
        if (!Flags.mmsDisabledError()) {
            Log.i(TAG, "testSendMmsMessageWithMmsDisabled: mmsDisabledError is not enabled");
            return;
        }
        Log.i(TAG, "testSendMmsMessageWithMmsDisabled");

        // Disable MMS carrier config
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, false);
        assertTrue(
                "Failed to override carrier config",
                overrideCarrierConfig(SmsManager.getDefaultSmsSubscriptionId(), bundle));
        assertFalse(doesSupportMMS());

        // It takes some time for the new carrier config loaded to MmsConfigManager
        waitFor(TimeUnit.SECONDS.toMillis(2));

        // Test non-default SMS app
        sendMmsMessage(0L /* messageId */, SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER,
                SmsManager.getDefault(), false);

        // Test default SMS app
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        sendMmsMessage(0L /* messageId */, SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER,
                SmsManager.getDefault(), true);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();

        // Restore MMS config, Clear the overrides
        clearOverrideCarrierConfig();

    }

    @Test
    @ApiTest(apis = "android.telephony.SmsManager#sendMultimediaMessage")
    public void testSendMmsMessageWithMessageId() {
        // Test non-default SMS app
        sendMmsMessage(MESSAGE_ID, Activity.RESULT_OK, SmsManager.getDefault(), false);

        // Test default SMS app
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        sendMmsMessage(MESSAGE_ID, Activity.RESULT_OK, SmsManager.getDefault(), true);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MESSAGE_PROMOTION)
    @Ignore("b/443345141 - Need to fix and re-enable this test.")
    public void testSendMmsMessageWithImageAttachment() throws Exception {
        Log.i("MmsTest", "testSendMmsMessageWithImageAttachment");
        SmsManager smsManager = mContext.getSystemService(SmsManager.class);
        try {
            DefaultSmsAppHelper.setDefaultSmsApp(mContext, MESSAGE_UPGRADE_APP);
            waitFor(DMA_CHANGE_PROPAGATION_DELAY);

            sendUpgradeMmsWithImageAttachment(
                    smsManager, SUBJECT_UPGRADE_ACCEPTED, UPGRADE_STATUS_ACCEPTED);
            //            TODO(b/496425299): Fix NO_SUITABLE_DATA_PROFILE issue
            //            sendUpgradeMmsWithImageAttachment(
            //                    smsManager, SUBJECT_UPGRADE_REJECTED, UPGRADE_STATUS_REJECTED);
        } finally {
            DefaultSmsAppHelper.removeDefaultSmsAppRole(MESSAGE_UPGRADE_APP);
        }
    }

    private void sendMmsMessage(long messageId, int expectedErrorResultCode,
            SmsManager smsManager, boolean defaultSmsApp) {
        if (!doesSupportMMS()
                && expectedErrorResultCode != SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER) {
            Log.i(TAG, "sendMmsMessage skipped: no telephony available or MMS not supported");
            return;
        }
        resetBroadcastReceivers();
        mSentReceiver.setExpectedErrorResultCode(expectedErrorResultCode);
        mDeliveryReceiver.setExpectedErrorResultCode(expectedErrorResultCode);

        String selfNumber = getValidSelfNumber();

        Log.i(TAG, "sendMmsMessage");

        // Create local provider file for sending PDU
        final String fileName = "send." + Long.toUnsignedString(mRandom.nextLong()) + ".dat";
        final File sendFile = new File(mContext.getCacheDir(), fileName);
        final Uri contentUri = setupMmsPdu(sendFile, selfNumber, SUBJECT);
        final PendingIntent pendingIntent = getMmsSentPendingIntent();
        // Send
        if (messageId == 0L) {
            smsManager.sendMultimediaMessage(
                    mContext,
                    contentUri,
                    null /*locationUrl*/,
                    null /*configOverrides*/,
                    pendingIntent);
        } else {
            smsManager.sendMultimediaMessage(
                    mContext,
                    contentUri,
                    null /*locationUrl*/,
                    null /*configOverrides*/,
                    pendingIntent,
                    messageId);
        }
        assertTrue("Timeout waiting for MMS sent", mSentReceiver.waitForSuccess(SENT_TIMEOUT));
        assertEquals(expectedErrorResultCode, mSentReceiver.getResultCode());

        if (expectedErrorResultCode == Activity.RESULT_OK) {
            int carrierId = mTelephonyManager.getSimCarrierId();
            assumeFalse("Carrier [carrier-id: " + carrierId + "] does not support "
                            + "loop back messages. Use another carrier.",
                    CarrierCapability.UNSUPPORT_LOOP_BACK_MESSAGES.contains(carrierId));
        }

        if (defaultSmsApp && expectedErrorResultCode == Activity.RESULT_OK) {
            // Default SMS App should receive android.provider.Telephony.WAP_PUSH_DELIVER
            assertTrue(
                    "Timeout waiting for MMS delivery",
                    mDeliveryReceiver.waitForSuccess(SENT_TIMEOUT));
        } else {
            // Non-default SMS App should not receive android.provider.Telephony.WAP_PUSH_DELIVER.
            // Default SMS App will not receive android.provider.Telephony.WAP_PUSH_DELIVER in case
            // of fail to send a message.
            assertTrue(
                    "Delivery receiver should not be called",
                    mDeliveryReceiver.verifyNoCalls(NO_CALLS_TIMEOUT));
        }
        sendFile.delete();
    }

    private void sendUpgradeMmsMessage(
            SmsManager smsManager, String subject, int expectedUpgradeStatus) {
        if (!doesSupportMMS()) {
            Log.i(
                    TAG,
                    "sendUpgradeMmsMessage skipped: no telephony available or MMS not supported");
            return;
        }
        Log.i(TAG, "sendUpgradeMmsMessage");
        resetBroadcastReceivers();
        mSentReceiver.setSkipSendConfPduParsing(expectedUpgradeStatus == UPGRADE_STATUS_ACCEPTED);

        String selfNumber = getValidSelfNumber();

        // Create local provider file for sending PDU
        final String fileName = "send." + Long.toUnsignedString(mRandom.nextLong()) + ".dat";
        final File sendFile = new File(mContext.getCacheDir(), fileName);
        final Uri contentUri = setupMmsPdu(sendFile, selfNumber, subject);
        final PendingIntent pendingIntent = getMmsSentPendingIntent();

        // Send
        smsManager.sendMultimediaMessage(
                mContext,
                contentUri,
                null /*locationUrl*/,
                null /*configOverrides*/,
                pendingIntent);

        assertTrue(
                "Message upgrade broadcast not received.",
                mMessageUpgradeReceiver.waitForUpgrade(SHORT_TIME_OUT));
        assertEquals(
                "Incorrect message upgrade received: " + mMessageUpgradeReceiver.mUpgradeStatus,
                expectedUpgradeStatus,
                mMessageUpgradeReceiver.mUpgradeStatus.get());

        assertTrue(
                "Could not send MMS message. Check signal.",
                mSentReceiver.waitForSuccess(SENT_TIMEOUT));

        if (expectedUpgradeStatus == UPGRADE_STATUS_REJECTED) {
            int carrierId = mTelephonyManager.getSimCarrierId();
            assumeFalse(
                    "Carrier [carrier-id: "
                            + carrierId
                            + "] does not support "
                            + "loop back messages. Use another carrier.",
                    CarrierCapability.UNSUPPORT_LOOP_BACK_MESSAGES.contains(carrierId));
        }
        assertTrue(mDeliveryReceiver.verifyNoCalls(NO_CALLS_TIMEOUT));
        sendFile.delete();
    }

    private void sendUpgradeMmsWithImageAttachment(
            SmsManager smsManager, String subject, int expectedUpgradeStatus) throws Exception {
        if (!doesSupportMMS()) {
            Log.i(TAG, "testSendMmsMessageWithImageAttachment skipped: MMS not supported");
            return;
        }

        File imageFile = null;
        File pduFile = null;
        try {
            imageFile = createImageFile(IMAGE_PART_FILENAME);
            byte[] imageData = readFileAsBytes(imageFile);
            assertNotNull("Failed to read image data", imageData);

            final String fileName =
                    "send_with_image." + Long.toUnsignedString(mRandom.nextLong()) + ".dat";
            pduFile = new File(mContext.getCacheDir(), fileName);
            String selfNumber = getValidSelfNumber();
            final Uri contentUri =
                    setupMmsPduWithImage(pduFile, selfNumber, subject, MESSAGE_BODY, imageData);

            resetBroadcastReceivers();
            mSentReceiver.setSkipSendConfPduParsing(
                    expectedUpgradeStatus == UPGRADE_STATUS_ACCEPTED);
            final PendingIntent pendingIntent = getMmsSentPendingIntent();

            smsManager.sendMultimediaMessage(
                    mContext,
                    contentUri,
                    null /*locationUrl*/,
                    null /*configOverrides*/,
                    pendingIntent);

            assertTrue(
                    "Message upgrade broadcast not received.",
                    mMessageUpgradeReceiver.waitForUpgrade(SHORT_TIME_OUT));
            assertEquals(
                    "Incorrect message upgrade received: " + mMessageUpgradeReceiver.mUpgradeStatus,
                    expectedUpgradeStatus,
                    mMessageUpgradeReceiver.mUpgradeStatus.get());
            assertTrue(
                    "Could not send MMS message. Check signal.",
                    mSentReceiver.waitForSuccess(SENT_TIMEOUT));
            if (expectedUpgradeStatus == UPGRADE_STATUS_REJECTED) {
                int carrierId = mTelephonyManager.getSimCarrierId();
                assumeFalse(
                        "Carrier [carrier-id: "
                                + carrierId
                                + "] does not support "
                                + "loop back messages. Use another carrier.",
                        CarrierCapability.UNSUPPORT_LOOP_BACK_MESSAGES.contains(carrierId));
            }
            assertTrue(mDeliveryReceiver.verifyNoCalls(NO_CALLS_TIMEOUT));

        } finally {
            if (imageFile != null) {
                imageFile.delete();
            }
            if (pduFile != null) {
                pduFile.delete();
            }
        }
    }

    /** Retrieves the device's phone number and verifies it exists. */
    private String getValidSelfNumber() {
        String selfNumber;
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        try {
            int subId = mTelephonyManager.getSubscriptionId();
            SubscriptionManager subscriptionManager =
                    mContext.getSystemService(SubscriptionManager.class);
            selfNumber = subscriptionManager.getPhoneNumber(subId);
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }

        assumeFalse(
                "SIM card does not provide phone number. Use a suitable SIM Card.",
                TextUtils.isEmpty(selfNumber));

        return selfNumber;
    }

    /** Builds the PDU, writes it to the provided file, and returns the Content Uri. */
    private Uri setupMmsPdu(File sendFile, String selfNumber, String subject) {
        final byte[] pdu = buildPdu(mContext, selfNumber, subject, MESSAGE_BODY);
        assertNotNull(pdu);
        assertTrue(writePdu(sendFile, pdu));

        return new Uri.Builder()
                .authority(PROVIDER_AUTHORITY)
                .path(sendFile.getName())
                .scheme(ContentResolver.SCHEME_CONTENT)
                .build();
    }

    /** Creates the PendingIntent used to track the MMS sent status. */
    private PendingIntent getMmsSentPendingIntent() {
        return PendingIntent.getBroadcast(
                mContext,
                0,
                new Intent(ACTION_MMS_SENT).setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_MUTABLE);
    }

    private void resetBroadcastReceivers() {
        mSentReceiver.reset();
        mDeliveryReceiver.reset();
        mMessageUpgradeReceiver.reset();
    }

    private static boolean writePdu(File file, byte[] pdu) {
        FileOutputStream writer = null;
        try {
            writer = new FileOutputStream(file);
            writer.write(pdu);
            return true;
        } catch (final IOException e) {
            return false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private byte[] buildPdu(Context context, String selfNumber, String subject, String text) {
        final SendReq req = new SendReq();
        // From, per spec
        req.setFrom(new EncodedStringValue(selfNumber));
        // To
        final String[] recipients = new String[1];
        recipients[0] = selfNumber;
        final EncodedStringValue[] encodedNumbers = EncodedStringValue.encodeStrings(recipients);
        if (encodedNumbers != null) {
            req.setTo(encodedNumbers);
        }
        // Subject
        if (!TextUtils.isEmpty(subject)) {
            req.setSubject(new EncodedStringValue(subject));
        }
        // Date
        req.setDate(System.currentTimeMillis() / 1000);
        // Body
        final PduBody body = new PduBody();
        // Add text part. Always add a smil part for compatibility, without it there
        // may be issues on some carriers/client apps
        final int size = addTextPart(body, text, true/* add text smil */);
        req.setBody(body);
        // Message size
        req.setMessageSize(size);
        // Message class
        req.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes());
        // Expiry
        req.setExpiry(DEFAULT_EXPIRY_TIME);
        // The following set methods throw InvalidHeaderValueException
        try {
            // Priority
            req.setPriority(DEFAULT_PRIORITY);
            // Delivery report
            req.setDeliveryReport(PduHeaders.VALUE_NO);
            // Read report
            req.setReadReport(PduHeaders.VALUE_NO);
        } catch (InvalidHeaderValueException e) {
            return null;
        }

        return new PduComposer(context, req).make();
    }

    private static int addTextPart(PduBody pb, String message, boolean addTextSmil) {
        final PduPart part = new PduPart();
        // Set Charset if it's a text media.
        part.setCharset(CharacterSets.UTF_8);
        // Set Content-Type.
        part.setContentType(ContentType.TEXT_PLAIN.getBytes());
        // Set Content-Location.
        part.setContentLocation(TEXT_PART_FILENAME.getBytes());
        int index = TEXT_PART_FILENAME.lastIndexOf(".");
        String contentId = (index == -1) ? TEXT_PART_FILENAME
                : TEXT_PART_FILENAME.substring(0, index);
        part.setContentId(contentId.getBytes());
        part.setData(message.getBytes());
        pb.addPart(part);
        if (addTextSmil) {
            final String smil = String.format(sSmilText, TEXT_PART_FILENAME);
            addSmilPart(pb, smil);
        }
        return part.getData().length;
    }

    private static void addSmilPart(PduBody pb, String smil) {
        final PduPart smilPart = new PduPart();
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType(ContentType.APP_SMIL.getBytes());
        smilPart.setData(smil.getBytes());
        pb.addPart(0, smilPart);
    }

    private static boolean shouldParseContentDisposition() {
        return SmsManager
                .getDefault()
                .getCarrierConfigValues()
                .getBoolean(SmsManager.MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION, true);
    }

    private static boolean doesSupportMMS() {
        return SmsManager
                .getDefault()
                .getCarrierConfigValues()
                .getBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, true);
    }

    @Test
    public void testDownloadMultimediaMessage() {
        downloadMultimediaMessage(0L /* messageId */);
    }

    @Test
    public void testDownloadMultimediaMessageWithMessageId() {
        downloadMultimediaMessage(MESSAGE_ID);
    }

    private void downloadMultimediaMessage(long messageId) {
        if (!doesSupportMMS()) {
            Log.i(TAG, "testSendMmsMessage skipped: no telephony available or MMS not supported");
            return;
        }

        Log.i(TAG, "testSendMmsMessage");
        // Prime the MmsService so that MMS config is loaded
        final SmsManager smsManager = SmsManager.getDefault();
        smsManager.getCarrierConfigValues();
        // MMS config is loaded asynchronously. Wait a bit so it will be loaded.
        waitFor(TimeUnit.SECONDS.toMillis(1));

        // Create local provider file
        final String fileName = "download." + Long.toUnsignedString(mRandom.nextLong()) + ".dat";
        final Uri contentUri = (new Uri.Builder())
                .authority(PROVIDER_AUTHORITY)
                .path(fileName)
                .scheme(ContentResolver.SCHEME_CONTENT)
                .build();

        final PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        new Intent(ACTION_MMS_DOWNLOAD).setPackage(mContext.getPackageName()),
                        PendingIntent.FLAG_MUTABLE);

        if (messageId == 0L) {
            // Verify the downloadMultimediaMessage function without messageId exists. This test
            // doesn't actually verify downloading is successful, just that the function to
            // initiate the downloading has been implemented.
            smsManager.downloadMultimediaMessage(
                    mContext, "foo/fake", contentUri, null /* configOverrides */, pendingIntent);
        } else {
            // Verify the downloadMultimediaMessage function with messageId exists. This test
            // doesn't actually verify downloading is successful, just that the function to
            // initiate the downloading has been implemented.
            smsManager.downloadMultimediaMessage(
                    mContext,
                    "foo/fake",
                    contentUri,
                    null /* configOverrides */,
                    pendingIntent,
                    MESSAGE_ID);
        }
    }

    /** Creates a PDU with text and an image, writes it to a file, and returns the Content Uri. */
    private Uri setupMmsPduWithImage(
            File pduFile, String selfNumber, String subject, String text, byte[] imageData) {
        final byte[] pdu = buildPduWithImage(mContext, selfNumber, subject, text, imageData);
        assertNotNull("PDU with image should not be null", pdu);
        assertTrue("Failed to write PDU file", writePdu(pduFile, pdu));

        return new Uri.Builder()
                .authority(PROVIDER_AUTHORITY)
                .path(pduFile.getName())
                .scheme(ContentResolver.SCHEME_CONTENT)
                .build();
    }

    /** Builds a PDU with both a text part and an image part. */
    private byte[] buildPduWithImage(
            Context context, String selfNumber, String subject, String text, byte[] imageData) {
        final SendReq req = new SendReq();
        req.setFrom(new EncodedStringValue(selfNumber));
        final EncodedStringValue[] encodedNumbers =
                EncodedStringValue.encodeStrings(new String[] {selfNumber});
        if (encodedNumbers != null) {
            req.setTo(encodedNumbers);
        }
        if (!TextUtils.isEmpty(subject)) {
            req.setSubject(new EncodedStringValue(subject));
        }
        req.setDate(System.currentTimeMillis() / 1000);

        final PduBody body = new PduBody();
        int totalSize = 0;

        // Add text part, but don't add a SMIL part yet.
        totalSize += addTextPart(body, text, false /* addTextSmil */);

        // Add image part
        totalSize += addImagePart(body, imageData, IMAGE_PART_FILENAME);

        // Add a SMIL part that includes both text and image.
        addSmilPart(body, sSmilWithImageText);

        req.setBody(body);
        req.setMessageSize(totalSize);
        req.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes());
        req.setExpiry(DEFAULT_EXPIRY_TIME);
        try {
            req.setPriority(DEFAULT_PRIORITY);
            req.setDeliveryReport(PduHeaders.VALUE_NO);
            req.setReadReport(PduHeaders.VALUE_NO);
        } catch (InvalidHeaderValueException e) {
            Log.e(TAG, "Invalid PDU header value", e);
            return null;
        }

        return new PduComposer(context, req).make();
    }

    /** Adds an image part to the PDU body. */
    private static int addImagePart(PduBody pb, byte[] data, String filename) {
        final PduPart part = new PduPart();
        part.setContentType(ContentType.IMAGE_JPEG.getBytes());
        part.setData(data);
        part.setFilename(filename.getBytes());
        part.setContentLocation(filename.getBytes());
        int index = filename.lastIndexOf(".");
        String contentId = (index == -1) ? filename : filename.substring(0, index);
        part.setContentId(("<" + contentId + ">").getBytes());
        pb.addPart(part);
        return data.length;
    }

    /** Creates a simple 1x1 pixel JPEG file for testing. */
    private File createImageFile(String fileName) throws IOException {
        File imageFile = new File(mContext.getCacheDir(), fileName);
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.setPixel(0, 0, Color.RED);
        try (FileOutputStream out = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        }
        return imageFile;
    }

    /** Reads a file into a byte array. */
    private static byte[] readFileAsBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        }
    }

    private abstract static class BaseReceiver extends BroadcastReceiver {
        protected CountDownLatch mLatch = new CountDownLatch(1);

        void clearQueue() {
            mLatch = new CountDownLatch(1);
        }

        boolean waitForChanged() throws Exception {
            return mLatch.await(5000, TimeUnit.MILLISECONDS);
        }
    }

    private static class CarrierConfigReceiver extends BaseReceiver {
        private int mSubId;

        CarrierConfigReceiver() {}

        public void setSubId(int subId) {
            mSubId = subId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, -1);
                Log.d(TAG, "Carrier config changed for subId=" + subId
                        + ", mSubId=" + mSubId);
                if (mSubId == subId) {
                    mLatch.countDown();
                }
            }
        }
    }

    private static void clearOverrideCarrierConfig(){
        try {
            overrideCarrierConfig(SmsManager.getDefaultSmsSubscriptionId(), null);
             // MMS config is loaded asynchronously. Wait a bit so it will be loaded.
            waitFor(TimeUnit.SECONDS.toMillis(2));
        } catch (UnsupportedOperationException ex) {
            // this device doesn't support messaging
        }
    }

    private static boolean overrideCarrierConfig(int subId, PersistableBundle bundle) {
        try {
            CarrierConfigManager carrierConfigManager =
                    getInstrumentation().getContext().getSystemService(CarrierConfigManager.class);
            if (carrierConfigManager == null) {
                Log.d(TAG, "CarrierConfigManager is not present on this device.");
                return false;
            }
            sCarrierConfigReceiver.clearQueue();
            sCarrierConfigReceiver.setSubId(subId);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    carrierConfigManager, (m) -> m.overrideConfig(subId, bundle));
            return sCarrierConfigReceiver.waitForChanged();
        } catch (Exception ex) {
            Log.e(TAG, "overrideCarrierConfig()", ex);
            return false;
        }
    }

    private static void waitFor(long timeoutMillis) {
        Object delayTimeout = new Object();
        synchronized (delayTimeout) {
            try {
                delayTimeout.wait(timeoutMillis);
            } catch (InterruptedException ex) {
                // Ignore the exception
                Log.d(TAG, "waitFor: delayTimeout ex=" + ex);
            }
        }
    }

    private static class MessageUpgradeBroadcastReceiver extends BroadcastReceiver {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private final String mAction;
        private final AtomicInteger mUpgradeStatus = new AtomicInteger(-1);

        MessageUpgradeBroadcastReceiver(String action) {
            mAction = action;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mAction.equals(action)) {
                int status = intent.getIntExtra(EXTRA_UPGRADE_STATUS, -1);
                Log.i(TAG, "onReceive: " + action + ", status: " + status);
                mUpgradeStatus.set(status);
                mLatch.countDown();
            }
        }

        private boolean waitForUpgrade(long timeoutMs) {
            try {
                return mLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for upgrade", e);
                return false;
            }
        }

        private void reset() {
            mLatch = new CountDownLatch(1);
        }
    }
}
