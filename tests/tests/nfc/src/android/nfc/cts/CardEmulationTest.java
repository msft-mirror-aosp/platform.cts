package android.nfc.cts;

import static android.nfc.cts.WalletRoleTestUtils.CTS_PACKAGE_NAME;
import static android.nfc.cts.WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME;
import static android.nfc.cts.WalletRoleTestUtils.WALLET_HOLDER_SERVICE_DESC;
import static android.nfc.cts.WalletRoleTestUtils.getWalletRoleHolderService;
import static android.nfc.cts.WalletRoleTestUtils.runWithRole;
import static android.nfc.cts.WalletRoleTestUtils.runWithRoleNone;

import static com.android.compatibility.common.util.PropertyUtil.getVsrApiLevel;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.nfc.Flags;
import android.nfc.INfcCardEmulation;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.AidGroup;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.cardemulation.PollingFrame;
import android.nfc.cardemulation.PollingFrame.PollingFrameType;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.testing.PollingCheck;
import android.view.KeyEvent;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.CommonTestUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.FieldReader;
import org.mockito.internal.util.reflection.FieldSetter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

@RunWith(JUnit4.class)
public class CardEmulationTest {
    private NfcAdapter mAdapter;

    private static final ComponentName mService =
        new ComponentName("android.nfc.cts", "android.nfc.cts.CtsMyHostApduService");

    private Context mContext;
    private INfcCardEmulation mOldService;
    @Mock private INfcCardEmulation mEmulation;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private boolean supportsHardware() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
    }

    private boolean supportsHardwareForEse() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC_OFF_HOST_CARD_EMULATION_ESE);
    }

    @Before
    public void setUp() throws NoSuchFieldException, RemoteException {
        MockitoAnnotations.initMocks(this);
        assumeTrue(supportsHardware());
        mContext = InstrumentationRegistry.getContext();
        mAdapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertNotNull(mAdapter);

        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        FieldReader serviceField = new FieldReader(instance,
                instance.getClass().getDeclaredField("sService"));
        mOldService = (INfcCardEmulation) serviceField.read();
    }

    @After
    public void tearDown() throws Exception {
        if (!supportsHardware()) return;
        restoreOriginalService();
    }

    private void restoreOriginalService() throws NoSuchFieldException {
        if (mAdapter != null) {
            CardEmulation instance = CardEmulation.getInstance(mAdapter);
            FieldSetter.setField(instance,
                    instance.getClass().getDeclaredField("sService"), mOldService);
        }
    }

    private void setMockService() throws NoSuchFieldException {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        FieldSetter.setField(instance, instance.getClass().getDeclaredField("sService"),
                mEmulation);
    }

    @Test
    public void getNonNullInstance() {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        Assert.assertNotNull(instance);
    }

    @Test
    public void testIsDefaultServiceForCategory() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.isDefaultServiceForCategory(anyInt(), any(ComponentName.class),
            anyString())).thenReturn(true);
        boolean result = instance.isDefaultServiceForCategory(mService,
            CardEmulation.CATEGORY_PAYMENT);
        Assert.assertTrue(result);
    }

    @Test
    public void testIsDefaultServiceForAid() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        String aid = "00000000000000";
        when(mEmulation.isDefaultServiceForAid(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(true);
        boolean result = instance.isDefaultServiceForAid(mService, aid);
        Assert.assertTrue(result);
    }

    @Test
    public void testCategoryAllowsForegroundPreferenceWithCategoryPayment() {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        boolean result
            = instance.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_PAYMENT);
        Assert.assertTrue(result);
    }

    @Test
    public void testCategoryAllowsForegroundPrefenceWithCategoryOther() {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        boolean result
            = instance.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_OTHER);
        Assert.assertTrue(result);
    }

    @Test
    public void testGetSelectionModeForCategoryWithCategoryPaymentAndPaymentRegistered()
        throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.isDefaultPaymentRegistered()).thenReturn(true);
        int result = instance.getSelectionModeForCategory(CardEmulation.CATEGORY_PAYMENT);
        Assert.assertEquals(CardEmulation.SELECTION_MODE_PREFER_DEFAULT, result);
    }

    @Test
    public void testGetSelectionModeForCategoryWithCategoryPaymentAndWithoutPaymentRegistered()
        throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.isDefaultPaymentRegistered()).thenReturn(false);
        int result = instance.getSelectionModeForCategory(CardEmulation.CATEGORY_PAYMENT);
        Assert.assertEquals(CardEmulation.SELECTION_MODE_ALWAYS_ASK, result);
    }

    @Test
    public void testGetSelectionModeForCategoryWithCategoryOther() {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        int result = instance.getSelectionModeForCategory(CardEmulation.CATEGORY_OTHER);
        Assert.assertEquals(CardEmulation.SELECTION_MODE_ASK_IF_CONFLICT, result);
    }

    @Test
    public void testRegisterAidsForService() throws NoSuchFieldException, RemoteException {
        ArrayList<String> aids = new ArrayList<String>();
        aids.add("00000000000000");
        CardEmulation cardEmulation = createMockedInstance();
        when(mEmulation.registerAidGroupForService(anyInt(), any(ComponentName.class), any()))
                .thenReturn(true);
        Assert.assertTrue(cardEmulation.registerAidsForService(mService,
                CardEmulation.CATEGORY_PAYMENT, aids));
    }

    @Test
    public void testUnsetOffHostForService() throws NoSuchFieldException, RemoteException {
        assumeTrue(supportsHardwareForEse());
        CardEmulation instance = createMockedInstance();
        when(mEmulation.unsetOffHostForService(anyInt(), any(ComponentName.class)))
            .thenReturn(true);
        boolean result = instance.unsetOffHostForService(mService);
        Assert.assertTrue(result);
    }

    @Test
    public void testSetOffHostForService() throws NoSuchFieldException, RemoteException {
        assumeTrue(supportsHardwareForEse());
        CardEmulation instance = createMockedInstance();
        String offHostSecureElement = "eSE";
        when(mEmulation.setOffHostForService(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(true);
        boolean result = instance.setOffHostForService(mService, offHostSecureElement);
        Assert.assertTrue(result);
    }

    @Test
    public void testGetAidsForService() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        ArrayList<String> aids = new ArrayList<String>();
        aids.add("00000000000000");
        AidGroup aidGroup = new AidGroup(aids, CardEmulation.CATEGORY_PAYMENT);
        when(mEmulation.getAidGroupForService(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(aidGroup);
        List<String> result = instance.getAidsForService(mService, CardEmulation.CATEGORY_PAYMENT);
        Assert.assertEquals(aids, result);
    }

    @Test
    public void testRemoveAidsForService() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.removeAidGroupForService(anyInt(), any(ComponentName.class), anyString()))
            .thenReturn(true);
        boolean result = instance.removeAidsForService(mService, CardEmulation.CATEGORY_PAYMENT);
        Assert.assertTrue(result);
    }

    @Test
    public void testSetPreferredService() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        Activity activity = createAndResumeActivity();
        when(mEmulation.setPreferredService(any(ComponentName.class))).thenReturn(true);
        boolean result = instance.setPreferredService(activity, mService);
        Assert.assertTrue(result);
    }

    @Test
    public void testUnsetPreferredService() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        Activity activity = createAndResumeActivity();
        when(mEmulation.unsetPreferredService()).thenReturn(true);
        boolean result = instance.unsetPreferredService(activity);
        Assert.assertTrue(result);
    }

    @Test
    public void testSupportsAidPrefixRegistration() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        when(mEmulation.supportsAidPrefixRegistration()).thenReturn(true);
        boolean result = instance.supportsAidPrefixRegistration();
        Assert.assertTrue(result);
    }

    @Test
    public void testGetAidsForPreferredPaymentService() throws NoSuchFieldException,
        RemoteException {
        CardEmulation instance = createMockedInstance();
        ArrayList<AidGroup> dynamicAidGroups = new ArrayList<AidGroup>();
        ArrayList<String> aids = new ArrayList<String>();
        aids.add("00000000000000");
        AidGroup aidGroup = new AidGroup(aids, CardEmulation.CATEGORY_PAYMENT);
        dynamicAidGroups.add(aidGroup);
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), false, "",
            new ArrayList<AidGroup>(), dynamicAidGroups, false, 0, 0, "", "", "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        List<String> result = instance.getAidsForPreferredPaymentService();
        Assert.assertEquals(aids, result);
    }

    @Test
    public void testGetRouteDestinationForPreferredPaymentServiceWithOnHost()
        throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), /* onHost = */ true,
            "", new ArrayList<AidGroup>(), new ArrayList<AidGroup>(), false, 0, 0, "", "", "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        String result = instance.getRouteDestinationForPreferredPaymentService();
        Assert.assertEquals("Host", result);
    }

    @Test
    public void testGetRouteDestinationForPreferredPaymentServiceWithOffHostAndNoSecureElement()
        throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), /* onHost = */ false,
            "", new ArrayList<AidGroup>(), new ArrayList<AidGroup>(), false, 0, 0, "",
            /* offHost = */ null, "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        String result = instance.getRouteDestinationForPreferredPaymentService();
        Assert.assertEquals("OffHost", result);
    }

    @Test
    public void testGetRouteDestinationForPreferredPaymentServiceWithOffHostAndSecureElement()
        throws NoSuchFieldException, RemoteException {
        assumeTrue(supportsHardwareForEse());
        CardEmulation instance = createMockedInstance();
        String offHostSecureElement = "OffHost Secure Element";
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), /* onHost = */ false,
            "", new ArrayList<AidGroup>(), new ArrayList<AidGroup>(), false, 0, 0, "",
            /* offHost = */ offHostSecureElement, "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        String result = instance.getRouteDestinationForPreferredPaymentService();
        Assert.assertEquals(offHostSecureElement, result);
    }

    @Test
    public void testGetDescriptionForPreferredPaymentService() throws NoSuchFieldException,
        RemoteException {
        CardEmulation instance = createMockedInstance();
        String description = "Preferred Payment Service Description";
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), false,
            /* description */ description, new ArrayList<AidGroup>(), new ArrayList<AidGroup>(),
            false, 0, 0, "", "", "");
        when(mEmulation.getPreferredPaymentService(anyInt())).thenReturn(serviceInfo);
        CharSequence result = instance.getDescriptionForPreferredPaymentService();
        Assert.assertEquals(description, result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
    public void testGetServices() throws NoSuchFieldException, RemoteException {
        CardEmulation instance = createMockedInstance();
        String description = "Preferred Payment Service Description";
        ApduServiceInfo serviceInfo = new ApduServiceInfo(new ResolveInfo(), false,
                /* description */ description, new ArrayList<AidGroup>(), new ArrayList<AidGroup>(),
                false, 0, 0, "", "", "");
        List<ApduServiceInfo> services = List.of(serviceInfo);
        when(mEmulation.getServices(anyInt(), anyString())).thenReturn(services);
        Assert.assertEquals(instance.getServices(CardEmulation.CATEGORY_PAYMENT, 0), services);
    }

    @Test
    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testGetPreferredPaymentService() {
        final String expectedPaymentService = "foo.bar/foo.bar.baz.Service";
        Settings.Secure.putString(ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Secure.NFC_PAYMENT_DEFAULT_COMPONENT, expectedPaymentService);

        ComponentName paymentService = CardEmulation.getPreferredPaymentService(
                ApplicationProvider.getApplicationContext());

        Assert.assertEquals(paymentService,
                ComponentName.unflattenFromString(expectedPaymentService));
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testTypeAPollingLoopToDefault() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        ComponentName originalDefault = null;
        mAdapter.notifyHceDeactivated();
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(6);
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
            ensurePreferredService(CustomHostApduService.class);
            notifyPollingLoopAndWait(new ArrayList<PollingFrame>(frames),
                    CustomHostApduService.class.getName());
        } finally {
            setDefaultPaymentService(originalDefault);
            mAdapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testTypeAPollingLoopToWalletHolder() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME,
                () -> {
                    NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
                    adapter.notifyHceDeactivated();
                    ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(6);
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
                    ensurePreferredService(WalletRoleTestUtils.WALLET_HOLDER_SERVICE_DESC);
                    notifyPollingLoopAndWait(new ArrayList<PollingFrame>(frames),
                            WalletRoleTestUtils.getWalletRoleHolderService().getClassName());
                    adapter.notifyHceDeactivated();
                });
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testCustomFrameToCustomInTwoFullLoops() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME,
                () -> {
                    NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
                    adapter.notifyHceDeactivated();
                    CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
                    ComponentName customServiceName = new ComponentName(mContext,
                            CustomHostApduService.class);
                    String testName = new Object() {
                    }.getClass().getEnclosingMethod().getName();
                    String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
                    Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                            customServiceName,
                            annotationStringHex, false));
                    ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(6);
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
                    frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                            HexFormat.of().parseHex(annotationStringHex)));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
                    frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                            HexFormat.of().parseHex(annotationStringHex)));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
                    ensurePreferredService(WalletRoleTestUtils.WALLET_HOLDER_SERVICE_DESC);
                    // Only the frames matching the filter should be delivered.
                    notifyPollingLoopAndWait(new ArrayList<PollingFrame>(
                                    Arrays.asList(frames.get(2), frames.get(6))),
                            CustomHostApduService.class.getName());
                    adapter.notifyHceDeactivated();
                });
    }


    class EventPollLoopReceiver extends PollLoopReceiver {
        static final int OBSERVE_MODE = 1;
        static final int PREFERRED_SERVICE = 2;
        class EventLogEntry {
            String mServiceClassName;
            int mEventType;
            boolean mState;
            EventLogEntry(String serviceClassName, int eventType, boolean state) {
                mServiceClassName = serviceClassName;
                mEventType = eventType;
                mState = state;
            }
        }
        EventPollLoopReceiver() {
            super(null, null);
        }
        ArrayList<EventLogEntry> mEvents = new ArrayList<EventLogEntry>();

        public void onObserveModeStateChanged(String className, boolean isEnabled) {
            synchronized (this) {
                mEvents.add(new EventLogEntry(className, OBSERVE_MODE, isEnabled));
                this.notify();
            }
        }

        public void onPreferredServiceChanged(String className, boolean isPreferred) {
            synchronized (this) {
                mEvents.add(new EventLogEntry(className, PREFERRED_SERVICE, isPreferred));
                this.notify();
            }
        }
    };

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_OBSERVE_MODE,
        android.nfc.Flags.FLAG_NFC_EVENT_LISTENER})
    public void testEventListener() throws InterruptedException {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        assumeTrue(adapter.isObserveModeSupported());
        adapter.notifyHceDeactivated();
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        EventPollLoopReceiver eventPollLoopReceiver = new EventPollLoopReceiver();
        sCurrentPollLoopReceiver = eventPollLoopReceiver;
        Activity activity = createAndResumeActivity();
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext,
                            CtsMyHostApduService.class)));
            synchronized (sCurrentPollLoopReceiver) {
                sCurrentPollLoopReceiver.wait(5000);
            }
            EventPollLoopReceiver.EventLogEntry event = eventPollLoopReceiver.mEvents.getLast();
            Assert.assertEquals(CtsMyHostApduService.class.getName(),
                    event.mServiceClassName);
            Assert.assertEquals(EventPollLoopReceiver.PREFERRED_SERVICE, event.mEventType);
            Assert.assertTrue(event.mState);

            Assert.assertFalse(adapter.isObserveModeEnabled());
            Assert.assertTrue(adapter.setObserveModeEnabled(true));
            synchronized (sCurrentPollLoopReceiver) {
                sCurrentPollLoopReceiver.wait(5000);
            }
            event = eventPollLoopReceiver.mEvents.getLast();
            Assert.assertEquals(CtsMyHostApduService.class.getName(),
                    event.mServiceClassName);
            Assert.assertEquals(EventPollLoopReceiver.OBSERVE_MODE, event.mEventType);
            Assert.assertTrue(event.mState);
            Assert.assertTrue(adapter.isObserveModeEnabled());

            Assert.assertTrue(adapter.setObserveModeEnabled(false));
            synchronized (sCurrentPollLoopReceiver) {
                sCurrentPollLoopReceiver.wait(5000);
            }
            event = eventPollLoopReceiver.mEvents.getLast();
            Assert.assertEquals(CtsMyHostApduService.class.getName(),
                    event.mServiceClassName);
            Assert.assertEquals(EventPollLoopReceiver.OBSERVE_MODE, event.mEventType);
            Assert.assertFalse(event.mState);
            Assert.assertFalse(adapter.isObserveModeEnabled());
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            synchronized (sCurrentPollLoopReceiver) {
                sCurrentPollLoopReceiver.wait(5000);
            }
            event = eventPollLoopReceiver.mEvents.getLast();
            Assert.assertEquals(CtsMyHostApduService.class.getName(),
                    event.mServiceClassName);
            Assert.assertEquals(EventPollLoopReceiver.PREFERRED_SERVICE, event.mEventType);
            Assert.assertFalse(event.mState);
        } finally {
            cardEmulation.unsetPreferredService(activity);
            activity.finish();
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_OBSERVE_MODE,
        android.nfc.Flags.FLAG_NFC_EVENT_LISTENER,
        android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testEventListener_WalletHolderToForegroundAndBack() throws InterruptedException {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        assumeTrue(adapter.isObserveModeSupported());
        adapter.notifyHceDeactivated();
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        EventPollLoopReceiver eventPollLoopReceiver = new EventPollLoopReceiver();
        sCurrentPollLoopReceiver = eventPollLoopReceiver;
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME,
                    () -> {
                EventPollLoopReceiver.EventLogEntry event = eventPollLoopReceiver.mEvents.getLast();
                Assert.assertEquals("com.android.test.walletroleholder.WalletRoleHolderApduService",
                        event.mServiceClassName);
                Assert.assertEquals(EventPollLoopReceiver.PREFERRED_SERVICE, event.mEventType);
                Assert.assertTrue(event.mState);

                Activity activity = createAndResumeActivity();
                try {
                    int numEvents = eventPollLoopReceiver.mEvents.size();
                    Assert.assertTrue(cardEmulation.setPreferredService(activity,
                            new ComponentName(mContext,
                                    CtsMyHostApduService.class)));
                    synchronized (sCurrentPollLoopReceiver) {
                        sCurrentPollLoopReceiver.wait(5000);
                    }
                    event = eventPollLoopReceiver.mEvents.get(numEvents);

                    Assert.assertEquals(
                            "com.android.test.walletroleholder.WalletRoleHolderApduService",
                            event.mServiceClassName);
                    Assert.assertEquals(EventPollLoopReceiver.PREFERRED_SERVICE, event.mEventType);
                    Assert.assertFalse(event.mState);

                    synchronized (sCurrentPollLoopReceiver) {
                        if (eventPollLoopReceiver.mEvents.size() == numEvents + 2) {
                            sCurrentPollLoopReceiver.wait(5000);
                        }
                    }
                    Assert.assertEquals(numEvents + 2, eventPollLoopReceiver.mEvents.size());
                    event = eventPollLoopReceiver.mEvents.get(numEvents + 1);

                    Assert.assertEquals(CtsMyHostApduService.class.getName(),
                            event.mServiceClassName);
                    Assert.assertEquals(EventPollLoopReceiver.PREFERRED_SERVICE, event.mEventType);
                    Assert.assertTrue(event.mState);

                    Assert.assertFalse(adapter.isObserveModeEnabled());
                    Assert.assertTrue(adapter.setObserveModeEnabled(true));
                    synchronized (sCurrentPollLoopReceiver) {
                        sCurrentPollLoopReceiver.wait(5000);
                    }
                    event = eventPollLoopReceiver.mEvents.getLast();
                    Assert.assertEquals(CtsMyHostApduService.class.getName(),
                            event.mServiceClassName);
                    Assert.assertEquals(EventPollLoopReceiver.OBSERVE_MODE, event.mEventType);
                    Assert.assertTrue(event.mState);
                    Assert.assertTrue(adapter.isObserveModeEnabled());

                    Assert.assertTrue(adapter.setObserveModeEnabled(false));
                    synchronized (sCurrentPollLoopReceiver) {
                        sCurrentPollLoopReceiver.wait(5000);
                    }
                    event = eventPollLoopReceiver.mEvents.getLast();
                    Assert.assertEquals(CtsMyHostApduService.class.getName(),
                            event.mServiceClassName);
                    Assert.assertEquals(EventPollLoopReceiver.OBSERVE_MODE, event.mEventType);
                    Assert.assertFalse(event.mState);
                    Assert.assertFalse(adapter.isObserveModeEnabled());
                    Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
                    synchronized (sCurrentPollLoopReceiver) {
                        sCurrentPollLoopReceiver.wait(5000);
                    }
                    event = eventPollLoopReceiver.mEvents.getLast();
                    Assert.assertEquals(CtsMyHostApduService.class.getName(),
                            event.mServiceClassName);
                    Assert.assertEquals(EventPollLoopReceiver.PREFERRED_SERVICE, event.mEventType);
                    Assert.assertFalse(event.mState);
                } catch (InterruptedException ie) {
                    throw new AssertionError("interrupted unexpectedly", ie);
                } finally {
                    cardEmulation.unsetPreferredService(activity);
                    activity.finish();
                    adapter.notifyHceDeactivated();
                }
            });
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testTypeAPollingLoopToForeground() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext,
                            CtsMyHostApduService.class)));
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(6);
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
            ensurePreferredService(CtsMyHostApduService.class);
            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            activity.finish();
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_OBSERVE_MODE)
    public void testSetShouldDefaultToObserveModeShouldDefaultToObserveMode()
            throws InterruptedException {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        assumeTrue(adapter.isObserveModeSupported());
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            ComponentName ctsService = new ComponentName(mContext, CtsMyHostApduService.class);
            Assert.assertTrue(cardEmulation.setShouldDefaultToObserveModeForService(ctsService,
                    false));

            Assert.assertTrue(cardEmulation.setPreferredService(activity, ctsService));
            ensurePreferredService(CtsMyHostApduService.class);

            Assert.assertFalse(adapter.isObserveModeEnabled());
            Assert.assertTrue(cardEmulation.setShouldDefaultToObserveModeForService(ctsService,
                    true));
            // Observe mode is set asynchronously, so just wait a bit to let it happen.
            try {
                CommonTestUtils.waitUntil("Observe mode hasn't been set", 1,
                        () -> adapter.isObserveModeEnabled());
            } catch (InterruptedException ie) { }
            Assert.assertTrue(adapter.isObserveModeEnabled());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            activity.finish();
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testTypeAOneLoopPollingLoopToForeground() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext,
                            CtsMyHostApduService.class)));
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(4);
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_B));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
            ensurePreferredService(CtsMyHostApduService.class);
            sCurrentPollLoopReceiver = new PollLoopReceiver(new ArrayList<PollingFrame>(0), null);
            for (PollingFrame frame : frames) {
                adapter.notifyPollingLoop(frame);
            }
            synchronized (sCurrentPollLoopReceiver) {
                try {
                    sCurrentPollLoopReceiver.wait(5000);
                } catch (InterruptedException ie) {
                    Assert.assertNull(ie);
                }
            }
            sCurrentPollLoopReceiver.test();
            sCurrentPollLoopReceiver = null;
        } finally {
            cardEmulation.unsetPreferredService(activity);
            activity.finish();
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testTypeABNoOffPollingLoopToDefault() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        ComponentName originalDefault = null;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(7);
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_B));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_B));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
            frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_B));
            ensurePreferredService(CustomHostApduService.class);
            notifyPollingLoopAndWait(new ArrayList<PollingFrame>(frames),
                    CustomHostApduService.class.getName());
        } finally {
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testTypeAPollingLoopToForegroundWithWalletHolder() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME,
                () -> {
                    Assert.assertTrue(cardEmulation.setPreferredService(activity,
                            new ComponentName(mContext,
                                    CtsMyHostApduService.class)));
                    ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(6);
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_ON));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_A));
                    frames.add(createFrame(PollingFrame.POLLING_LOOP_TYPE_OFF));
                    ensurePreferredService(CtsMyHostApduService.class);
                    notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
                    Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
                    activity.finish();
                    adapter.notifyHceDeactivated();
                });
    }

    void ensurePreferredService(Class serviceClass) {
        ensurePreferredService(serviceClass, mContext);
    }

    static void ensurePreferredService(Class serviceClass, Context context) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        int resId = (serviceClass == CtsMyHostApduService.class
                ? R.string.CtsPaymentService
                : (serviceClass == CustomHostApduService.class
                        ? R.string.CtsCustomPaymentService : R.string.CtsBackgroundPaymentService));
        final String desc = context.getResources().getString(resId);
        DefaultPaymentProviderTestUtils.ensurePreferredService(desc, context);
    }

    void ensurePreferredService(String serviceDesc) {
        DefaultPaymentProviderTestUtils.ensurePreferredService(serviceDesc, mContext);
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testTwoCustomPollingLoopToPreferredCustomAndBackgroundDynamic() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        assumeTrue(adapter.isObserveModeSupported());
        adapter.notifyHceDeactivated();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        Activity activity = createAndResumeActivity();
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CustomHostApduService.class)));

            ensurePreferredService(CustomHostApduService.class);
            Assert.assertTrue(adapter.setObserveModeEnabled(true));

            ComponentName backgroundServiceName = new ComponentName(mContext,
                    BackgroundHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex1 =
                    HexFormat.of().toHexDigits((testName + "background").hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                    backgroundServiceName, annotationStringHex1, false));
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(2);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex1)));

            ComponentName customServiceName = new ComponentName(mContext,
                    CustomHostApduService.class);

            String annotationStringHex2 =
                    HexFormat.of().toHexDigits((testName + "custom").hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                    customServiceName, annotationStringHex2, false));
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex2)));

            sCurrentPollLoopReceiver = new PollLoopReceiver(frames, null);
            for (PollingFrame frame : frames) {
                adapter.notifyPollingLoop(frame);
            }
            synchronized (sCurrentPollLoopReceiver) {
                try {
                    sCurrentPollLoopReceiver.wait(5000);
                } catch (InterruptedException ie) {
                    Assert.assertNull(ie);
                }
            }
            Assert.assertEquals(frames.size(), sCurrentPollLoopReceiver.mReceivedFrames.size());
            Assert.assertEquals(2, sCurrentPollLoopReceiver.mReceivedServiceNames.size());
            sCurrentPollLoopReceiver = null;
        } finally {
            cardEmulation.unsetPreferredService(activity);
            activity.finish();
            sCurrentPollLoopReceiver = null;
            adapter.notifyHceDeactivated();
        }
    }
    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testTwoCustomPollingLoopToCustomAndBackgroundDynamic() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        assumeTrue(adapter.isObserveModeSupported());
        adapter.notifyHceDeactivated();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        Activity activity = createAndResumeActivity();
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CtsMyHostApduService.class)));

            ensurePreferredService(CtsMyHostApduService.class);
            Assert.assertTrue(adapter.setObserveModeEnabled(true));

            ComponentName backgroundServiceName = new ComponentName(mContext,
                    BackgroundHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex1 =
                    HexFormat.of().toHexDigits((testName + "background").hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                    backgroundServiceName, annotationStringHex1, false));
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(2);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex1)));

            ComponentName customServiceName = new ComponentName(mContext,
                    CustomHostApduService.class);

            String annotationStringHex2 =
                    HexFormat.of().toHexDigits((testName + "custom").hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                    customServiceName, annotationStringHex2, false));
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex2)));

            sCurrentPollLoopReceiver = new PollLoopReceiver(frames, null);
            for (PollingFrame frame : frames) {
                adapter.notifyPollingLoop(frame);
            }
            synchronized (sCurrentPollLoopReceiver) {
                try {
                    sCurrentPollLoopReceiver.wait(5000);
                } catch (InterruptedException ie) {
                    Assert.assertNull(ie);
                }
            }
            Assert.assertEquals(frames.size(),
                    sCurrentPollLoopReceiver.mReceivedFrames.size());
            Assert.assertEquals(2, sCurrentPollLoopReceiver.mReceivedServiceNames.size());
            sCurrentPollLoopReceiver = null;
        } finally {
            cardEmulation.unsetPreferredService(activity);
            activity.finish();
            sCurrentPollLoopReceiver = null;
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testCustomPollingLoopToCustomDynamic() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        adapter.notifyHceDeactivated();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        ComponentName customServiceName = new ComponentName(mContext, CustomHostApduService.class);
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
        Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                annotationStringHex, false));
        ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
        frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        adapter.notifyHceDeactivated();
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testCustomPollingLoopToCustomDynamicAndRemove() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        Activity activity = createAndResumeActivity();
        ComponentName ctsServiceName = new ComponentName(mContext,
                CtsMyHostApduService.class);
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity, ctsServiceName));
            ensurePreferredService(CtsMyHostApduService.class);
            ComponentName customServiceName =
                    new ComponentName(mContext, CustomHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    annotationStringHex, false));

            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
            adapter.notifyHceDeactivated();

            Assert.assertTrue(cardEmulation.removePollingLoopFilterForService(customServiceName,
                    annotationStringHex));

            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());

        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            activity.finish();
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testCustomPollingLoopToCustomWithPrefixDynamic() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        ComponentName customServiceName = new ComponentName(mContext, CustomHostApduService.class);
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHexPrefix = HexFormat.of().toHexDigits(testName.hashCode());
        String annotationStringHex = annotationStringHexPrefix + "123456789ABCDF";
        String annotationStringHexPattern = annotationStringHexPrefix + ".*";
        Assert.assertTrue(cardEmulation.registerPollingLoopPatternFilterForService(
                customServiceName, annotationStringHexPattern, false));
        ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
        frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        Assert.assertTrue(cardEmulation.removePollingLoopPatternFilterForService(customServiceName,
                annotationStringHexPrefix));
        adapter.notifyHceDeactivated();
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testCustomPollingLoopToCustomWithPrefix() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHexPrefix = HexFormat.of().toHexDigits(testName.hashCode());
        String annotationStringHex = annotationStringHexPrefix + "123456789ABCDF";

        ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
        frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());

        adapter.notifyHceDeactivated();
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testThreeWayConflictPollingLoopToForegroundDynamic() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        ComponentName originalDefault = null;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            ComponentName ctsMyServiceName = new ComponentName(mContext,
                    CtsMyHostApduService.class);
            Assert.assertTrue(cardEmulation.setPreferredService(activity, ctsMyServiceName));
            ComponentName customServiceName = new ComponentName(mContext,
                    CustomHostApduService.class);
            ComponentName backgroundServiceName = new ComponentName(mContext,
                    BackgroundHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    annotationStringHex, false));
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                    backgroundServiceName, annotationStringHex, false));
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(ctsMyServiceName,
                    annotationStringHex, false));
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(CtsMyHostApduService.class);
            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            activity.finish();
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testBackgroundForegroundConflictPollingLoopToForegroundDynamic() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        Activity activity = createAndResumeActivity();
        ComponentName ctsServiceName = new ComponentName(mContext,
                CtsMyHostApduService.class);
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity, ctsServiceName));
            ComponentName backgroundServiceName = new ComponentName(mContext,
                    BackgroundHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(ctsServiceName,
                    annotationStringHex, false));
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                    backgroundServiceName, annotationStringHex, false));
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(CtsMyHostApduService.class);
            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            activity.finish();
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testBackgroundPaymentConflictPollingLoopToPaymentDynamic() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        ComponentName originalDefault = null;
        try {
            ComponentName customServiceName = new ComponentName(mContext,
                    CustomHostApduService.class);
            originalDefault = setDefaultPaymentService(customServiceName);
            CardEmulation cardEmulation = CardEmulation.getInstance(adapter);

            Assert.assertTrue(cardEmulation.isDefaultServiceForCategory(customServiceName,
                    CardEmulation.CATEGORY_PAYMENT));
            ComponentName backgroundServiceName = new ComponentName(mContext,
                    BackgroundHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    annotationStringHex, false));
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(
                    backgroundServiceName, annotationStringHex, false));
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(CustomHostApduService.class);
            notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        } finally {
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testCustomPollingLoopToCustom() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
        ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
        frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        adapter.notifyHceDeactivated();
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testThreeWayConflictPollingLoopToForeground() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        ComponentName originalDefault = null;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CtsMyHostApduService.class)));
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(CtsMyHostApduService.class);
            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            activity.finish();
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testThreeWayConflictPollingLoopToForegroundWithWalletHolder() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME,
                () -> {
                    Assert.assertTrue(cardEmulation.setPreferredService(activity,
                            new ComponentName(mContext, CtsMyHostApduService.class)));
                    String testName = new Object() {
                    }.getClass().getEnclosingMethod().getName();
                    String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
                    ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
                    frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                            HexFormat.of().parseHex(annotationStringHex)));
                    ensurePreferredService(CtsMyHostApduService.class);
                    notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
                    Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
                    activity.finish();
                    adapter.notifyHceDeactivated();
                });
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    public void testBackgroundForegroundConflictPollingLoopToForeground() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        Activity activity = createAndResumeActivity();
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CtsMyHostApduService.class)));
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(CtsMyHostApduService.class);
            notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
        } finally {
            Assert.assertTrue(cardEmulation.unsetPreferredService(activity));
            activity.finish();
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testBackgroundPaymentConflictPollingLoopToPayment() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        adapter.notifyHceDeactivated();
        ComponentName originalDefault = null;
        try {
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(CustomHostApduService.class);
            notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
        } finally {
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testBackgroundWalletConflictPollingLoopToWallet_walletRoleEnabled() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        runWithRole(mContext, WALLET_HOLDER_PACKAGE_NAME, () -> {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            adapter.notifyHceDeactivated();
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            ensurePreferredService(WALLET_HOLDER_SERVICE_DESC);
            notifyPollingLoopAndWait(frames, getWalletRoleHolderService().getClassName());
            adapter.notifyHceDeactivated();
        });
    }

    @Test
    @RequiresFlagsEnabled({com.android.nfc.flags.Flags.FLAG_AUTO_DISABLE_OBSERVE_MODE,
                           android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
                           Flags.FLAG_NFC_OBSERVE_MODE,
                           android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testAutoDisableObserveMode() throws Exception {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        runWithRole(mContext, CTS_PACKAGE_NAME, () -> {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Assert.assertTrue(NfcUtils.enableNfc(adapter, mContext));
            assumeTrue(adapter.isObserveModeSupported());
            adapter.notifyHceDeactivated();
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
            try {
                ensurePreferredService(CtsMyHostApduService.class);
                Assert.assertTrue(adapter.setObserveModeEnabled(true));
                Assert.assertTrue(adapter.isObserveModeEnabled());
                List<PollingFrame> receivedFrames =
                        notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
                Assert.assertFalse(receivedFrames.get(0).getTriggeredAutoTransact());
                PollingCheck.check("Observe mode not disabled", 4000,
                        () -> !adapter.isObserveModeEnabled());
                adapter.notifyHceDeactivated();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                adapter.setObserveModeEnabled(false);
            }
        });
    }

    @Test
    @RequiresFlagsEnabled({com.android.nfc.flags.Flags.FLAG_AUTO_DISABLE_OBSERVE_MODE,
                           android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
                           Flags.FLAG_NFC_OBSERVE_MODE})
    public void testDontAutoDisableObserveModeInForeground() throws Exception {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertTrue(NfcUtils.enableNfc(adapter, mContext));
        assumeTrue(adapter.isObserveModeSupported());
        adapter.notifyHceDeactivated();
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
        ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
        frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        final Activity activity = createAndResumeActivity();
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                new ComponentName(mContext, CtsMyHostApduService.class)));
            ensurePreferredService(CtsMyHostApduService.class);
            Assert.assertTrue(adapter.setObserveModeEnabled(true));
            Assert.assertTrue(adapter.isObserveModeEnabled());
            List<PollingFrame> receivedFrames =
                    notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
            Assert.assertFalse(receivedFrames.get(0).getTriggeredAutoTransact());
            Thread.currentThread().sleep(4000);
            Assert.assertTrue(adapter.isObserveModeEnabled());
            adapter.notifyHceDeactivated();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            adapter.setObserveModeEnabled(false);
        }
    }

    @Test
    @RequiresFlagsEnabled({com.android.nfc.flags.Flags.FLAG_AUTO_DISABLE_OBSERVE_MODE,
                           android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
                           Flags.FLAG_NFC_OBSERVE_MODE})
    public void testDontAutoDisableObserveModeInForegroundTwoServices() throws Exception {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertTrue(NfcUtils.enableNfc(adapter, mContext));
        assumeTrue(adapter.isObserveModeSupported());
        adapter.notifyHceDeactivated();
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        String annotationStringHex1 = "5cadc10f";
        ArrayList<PollingFrame> frames1 = new ArrayList<PollingFrame>(1);
        frames1.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex1)));
        ComponentName walletServiceName = WalletRoleTestUtils.getWalletRoleHolderService();
        String annotationStringHex2 = HexFormat.of().toHexDigits((testName).hashCode());
        ComponentName ctsComponentName = new ComponentName(mContext, CtsMyHostApduService.class);
        Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(ctsComponentName,
                        annotationStringHex2, false));
        ArrayList<PollingFrame> frames2 = new ArrayList<PollingFrame>(1);
        frames2.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                        HexFormat.of().parseHex(annotationStringHex2)));
        final Activity activity = createAndResumeActivity();
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity, ctsComponentName));
            ensurePreferredService(CtsMyHostApduService.class);
            Assert.assertTrue(adapter.setObserveModeEnabled(true));
            Assert.assertTrue(adapter.isObserveModeEnabled());
            List<PollingFrame> receivedFrames =
                    notifyPollingLoopAndWait(frames1,
                    WalletRoleTestUtils.getWalletRoleHolderService().getClassName());
            Assert.assertFalse(receivedFrames.get(0).getTriggeredAutoTransact());
            receivedFrames =
                    notifyPollingLoopAndWait(frames2, CtsMyHostApduService.class.getName());
            Assert.assertFalse(receivedFrames.get(0).getTriggeredAutoTransact());
            Thread.currentThread().sleep(5000);
            Assert.assertTrue(adapter.isObserveModeEnabled());
            adapter.notifyHceDeactivated();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            adapter.setObserveModeEnabled(false);
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            Flags.FLAG_NFC_OBSERVE_MODE})
    public void testAutoTransact() throws Exception {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertTrue(NfcUtils.enableNfc(adapter, mContext));
        assumeTrue(adapter.isObserveModeSupported());
        adapter.notifyHceDeactivated();
        final Activity activity = createAndResumeActivity();
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
        ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
        frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CtsMyHostApduService.class)));
            ensurePreferredService(CtsMyHostApduService.class);
            Assert.assertTrue(adapter.setObserveModeEnabled(true));
            Assert.assertTrue(adapter.isObserveModeEnabled());
            List<PollingFrame> receivedFrames =
                    notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
            Assert.assertTrue(receivedFrames.get(0).getTriggeredAutoTransact());
            PollingCheck.check("Observe mode not disabled", 200,
                    () -> !adapter.isObserveModeEnabled());
            adapter.notifyHceDeactivated();
            PollingCheck.check("Observe mode not enabled", 3000, adapter::isObserveModeEnabled);
        } finally {
            adapter.setObserveModeEnabled(false);
            cardEmulation.unsetPreferredService(activity);
            activity.finish();
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testAutoTransact_walletRoleEnabled() throws Exception {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        restoreOriginalService();
        runWithRole(mContext, CTS_PACKAGE_NAME, () -> {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Assert.assertTrue(NfcUtils.enableNfc(adapter, mContext));
            assumeTrue(adapter.isObserveModeSupported());
            adapter.notifyHceDeactivated();
            createAndResumeActivity();
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            Assert.assertTrue(adapter.setObserveModeEnabled(true));
            Assert.assertTrue(adapter.isObserveModeEnabled());
            List<PollingFrame> receivedFrames =
                    notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
            Assert.assertTrue(receivedFrames.get(0).getTriggeredAutoTransact());
            try {
                PollingCheck.check("Observe mode not disabled", 200,
                        () -> !adapter.isObserveModeEnabled());
                adapter.notifyHceDeactivated();
                PollingCheck.check("Observe mode not enabled", 3000, adapter::isObserveModeEnabled);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                adapter.setObserveModeEnabled(false);
            }
        });
        setMockService();
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            Flags.FLAG_NFC_OBSERVE_MODE})
    public void testAutoTransactDynamic() throws Exception {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertTrue(NfcUtils.enableNfc(adapter, mContext));
        assumeTrue(adapter.isObserveModeSupported());
        adapter.notifyHceDeactivated();
        final Activity activity = createAndResumeActivity();
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        ComponentName customServiceName = new ComponentName(mContext, CustomHostApduService.class);
        Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                annotationStringHex, true));
        ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
        frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex)));
        ComponentName ctsComponentName = new ComponentName(mContext, CtsMyHostApduService.class);
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity, ctsComponentName));
            ensurePreferredService(CtsMyHostApduService.class);
            Assert.assertTrue(adapter.setObserveModeEnabled(true));
            Assert.assertTrue(adapter.isObserveModeEnabled());
            List<PollingFrame> receivedFrames =
                    notifyPollingLoopAndWait(frames, CustomHostApduService.class.getName());
            Assert.assertTrue(receivedFrames.get(0).getTriggeredAutoTransact());
            PollingCheck.check("Observe mode not disabled", 200,
                    () -> !adapter.isObserveModeEnabled());
            adapter.notifyHceDeactivated();
            PollingCheck.check("Observe mode not enabled", 3000, adapter::isObserveModeEnabled);
        } finally {
            adapter.setObserveModeEnabled(false);
            cardEmulation.unsetPreferredService(activity);
            activity.finish();
        }
    }


    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            Flags.FLAG_NFC_OBSERVE_MODE})
    public void testOffHostAutoTransactDynamic() throws Exception {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertTrue(NfcUtils.enableNfc(adapter, mContext));
        assumeTrue(adapter.isObserveModeSupported());
        adapter.notifyHceDeactivated();
        final Activity activity = createAndResumeActivity();
        String testName = new Object() {
        }.getClass().getEnclosingMethod().getName();
        String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        ComponentName offhostServiceName = new ComponentName(mContext,
                CtsMyOffHostApduService.class);
        Assert.assertFalse(cardEmulation.registerPollingLoopFilterForService(offhostServiceName,
                "1234567890", false));
        Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(offhostServiceName,
                annotationStringHex, true));
        PollingFrame frame = createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                HexFormat.of().parseHex(annotationStringHex));
        ComponentName ctsComponentName = new ComponentName(mContext, CtsMyHostApduService.class);
        try {
            Assert.assertTrue(cardEmulation.setPreferredService(activity, ctsComponentName));
            ensurePreferredService(CtsMyHostApduService.class);
            Assert.assertTrue(adapter.setObserveModeEnabled(true));
            Assert.assertTrue(adapter.isObserveModeEnabled());
            adapter.notifyPollingLoop(frame);
            PollingCheck.check("Observe mode not disabled", 200,
                    () -> !adapter.isObserveModeEnabled());
            adapter.notifyHceDeactivated();
            PollingCheck.check("Observe mode not enabled", 3000, adapter::isObserveModeEnabled);
        } finally {
            adapter.setObserveModeEnabled(false);
            cardEmulation.unsetPreferredService(activity);
            activity.finish();
        }
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testDisallowNonDefaultSetObserveMode() throws NoSuchFieldException {
        restoreOriginalService();
        runWithRole(mContext,  WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME, () -> {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            Assert.assertTrue(NfcUtils.enableNfc(adapter, mContext));
            assumeTrue(adapter.isObserveModeSupported());
            adapter.notifyHceDeactivated();
            Assert.assertFalse(adapter.setObserveModeEnabled(true));
            Assert.assertFalse(adapter.isObserveModeEnabled());
        });
        setMockService();
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP,
            Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testAutoTransactDynamic_walletRoleEnabled() throws Exception {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        restoreOriginalService();
        runWithRole(mContext, CTS_PACKAGE_NAME, () -> {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
            assumeTrue(adapter.isObserveModeSupported());
            Assert.assertTrue(NfcUtils.enableNfc(adapter, mContext));
            adapter.notifyHceDeactivated();
            createAndResumeActivity();
            String testName = new Object() {
            }.getClass().getEnclosingMethod().getName();
            String annotationStringHex = HexFormat.of().toHexDigits(testName.hashCode());
            CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
            ComponentName customServiceName = new ComponentName(mContext,
                    CtsMyHostApduService.class);
            Assert.assertTrue(cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    annotationStringHex, true));
            ArrayList<PollingFrame> frames = new ArrayList<PollingFrame>(1);
            frames.add(createFrameWithData(PollingFrame.POLLING_LOOP_TYPE_UNKNOWN,
                    HexFormat.of().parseHex(annotationStringHex)));
            Assert.assertTrue(adapter.setObserveModeEnabled(true));
            Assert.assertTrue(adapter.isObserveModeEnabled());
            List<PollingFrame> receivedFrames =
                    notifyPollingLoopAndWait(frames, CtsMyHostApduService.class.getName());
            Assert.assertTrue(receivedFrames.get(0).getTriggeredAutoTransact());
            try {
                PollingCheck.check("Observe mode not disabled", 200,
                        () -> !adapter.isObserveModeEnabled());
                adapter.notifyHceDeactivated();
                PollingCheck.check("Observe mode not enabled", 3000, adapter::isObserveModeEnabled);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                adapter.setObserveModeEnabled(false);
            }
        });
        setMockService();
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_READ_POLLING_LOOP})
    public void testInvalidPollingLoopFilter() {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        ComponentName customServiceName = new ComponentName(mContext, CustomHostApduService.class);
        Assert.assertThrows(IllegalArgumentException.class,
                () -> cardEmulation.registerPollingLoopFilterForService(customServiceName,
                        "", false));
        Assert.assertThrows(IllegalArgumentException.class,
                () ->cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    "????", false));
        Assert.assertThrows(IllegalArgumentException.class,
                () ->cardEmulation.registerPollingLoopFilterForService(customServiceName,
                    "123", false));

    }

    static void ensureUnlocked() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final UserManager userManager = context.getSystemService(UserManager.class);
        assumeFalse(userManager.isHeadlessSystemUserMode());
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final PowerManager pm = context.getSystemService(PowerManager.class);
        final KeyguardManager km = context.getSystemService(KeyguardManager.class);
        try {
            if (pm != null && !pm.isInteractive()) {
                runShellCommand("input keyevent KEYCODE_WAKEUP");
                CommonTestUtils.waitUntil("Device does not wake up after 5 seconds", 5,
                        () -> pm != null && pm.isInteractive());
            }
            if (km != null && km.isKeyguardLocked()) {
                CommonTestUtils.waitUntil("Device does not unlock after 30 seconds", 30,
                        () -> {
                        SystemUtil.runWithShellPermissionIdentity(
                                () -> instrumentation.sendKeyDownUpSync(
                                        (KeyEvent.KEYCODE_MENU)));
                        return km != null && !km.isKeyguardLocked();
                    }
                );
            }
        } catch (InterruptedException ie) {
        }
    }

    private PollingFrame createFrame(@PollingFrameType int type) {
        if (type == PollingFrame.POLLING_LOOP_TYPE_ON
                || type == PollingFrame.POLLING_LOOP_TYPE_OFF) {
            return new PollingFrame(type,
                    new byte[] { ((type == PollingFrame.POLLING_LOOP_TYPE_ON)
                            ? (byte) 0x01 : (byte) 0x00) }, 8, 0,
                    false);
        }
        return new PollingFrame(type, null, 8, 0, false);
    }

    private PollingFrame createFrameWithData(@PollingFrameType int type, byte[] data) {
        return new PollingFrame(type, data, 8, (long) Integer.MAX_VALUE + 1L, false);
    }

    private ComponentName setDefaultPaymentService(Class serviceClass) {
        ComponentName componentName = setDefaultPaymentService(
                new ComponentName(mContext, serviceClass));
        return componentName;
    }

    ComponentName setDefaultPaymentService(ComponentName serviceName) {
        return DefaultPaymentProviderTestUtils.setDefaultPaymentService(serviceName, mContext);
    }

    static final class SettingsObserver extends ContentObserver {
        boolean mSeenChange = false;

        SettingsObserver(Handler handler) {
            super(handler);
        }
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            mSeenChange = true;
            synchronized (this) {
                this.notify();
            }
        }
    }

    static PollLoopReceiver sCurrentPollLoopReceiver = null;

    static class PollLoopReceiver  {
        int mFrameIndex = 0;
        ArrayList<PollingFrame> mFrames;
        String mServiceName;
        ArrayList<PollingFrame> mReceivedFrames;
        String mReceivedServiceName;
        ArrayList<String> mReceivedServiceNames;
        PollLoopReceiver(ArrayList<PollingFrame> frames, String serviceName) {
            mFrames = frames;
            mServiceName = serviceName;
            mReceivedFrames = new ArrayList<PollingFrame>();
            mReceivedServiceNames = new ArrayList<String>();
        }

        void notifyPollingLoop(String className, List<PollingFrame> receivedFrames) {
            if (receivedFrames == null || receivedFrames.isEmpty()) {
                return;
            }
            mReceivedFrames.addAll(receivedFrames);
            mReceivedServiceName = className;
            mReceivedServiceNames.add(className);
            if (mReceivedFrames.size() < mFrames.size()) {
                return;
            }
            synchronized (this) {
                this.notify();
            }
        }

        void test() {
            if (mReceivedFrames.size() > mFrames.size()) {
                Assert.fail("received more frames than sent");
            } else if (mReceivedFrames.size() < mFrames.size()) {
                Assert.fail("received fewer frames than sent");
            }
            for (PollingFrame receivedFrame : mReceivedFrames) {
                Assert.assertEquals(mFrames.get(mFrameIndex).getType(), receivedFrame.getType());
                Assert.assertEquals(mFrames.get(mFrameIndex).getVendorSpecificGain(),
                        receivedFrame.getVendorSpecificGain());
                Assert.assertEquals(mFrames.get(mFrameIndex).getTimestamp(),
                        receivedFrame.getTimestamp());
                Assert.assertArrayEquals(mFrames.get(mFrameIndex).getData(),
                        receivedFrame.getData());
                mFrameIndex++;
            }
            if (mServiceName != null) {
                Assert.assertEquals(mServiceName, mReceivedServiceName);
            }
        }
        public void onObserveModeStateChanged(String className, boolean isEnabled) {
        }

        public void onPreferredServiceChanged(String className, boolean isPreferred) {
        }
    }

    private List<PollingFrame> notifyPollingLoopAndWait(ArrayList<PollingFrame> frames,
            String serviceName) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        sCurrentPollLoopReceiver = new PollLoopReceiver(frames, serviceName);
        for (PollingFrame frame : frames) {
            adapter.notifyPollingLoop(frame);
        }
        synchronized (sCurrentPollLoopReceiver) {
            try {
                sCurrentPollLoopReceiver.wait(10000);
            } catch (InterruptedException ie) {
                Assert.assertNull(ie);
            }
        }
        sCurrentPollLoopReceiver.test();
        Assert.assertEquals(frames.size(), sCurrentPollLoopReceiver.mFrameIndex);
        List<PollingFrame> receivedFrames =  sCurrentPollLoopReceiver.mReceivedFrames;
        sCurrentPollLoopReceiver = null;
        return receivedFrames;
    }

    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    @Test
    public void testAidResolutionWithRoleHolder_anotherAppHoldsForeground()
            throws NoSuchFieldException {
        restoreOriginalService();
        Activity activity = createAndResumeActivity();
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        instance.setPreferredService(activity, WalletRoleTestUtils.getForegroundService());
        runWithRole(mContext, WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME, ()-> {
            /*
            * Aid Mapping:
            * Wallet Holder App: Service 1:     PAYMENT_AID_1, PAYMENT_AID_2
            * Wallet Holder App: Service 2:     PAYMENT_AID_1, PAYMENT_AID_2
            * Foreground App :   Only Service:  PAYMENT_AID_1
            * Non Payment App:   Only Service:  NON_PAYMENT_AID_1
            *
            * Scenario:
            * Wallet Role Holder is WalletRoleHolderApp
            * Foreground app: ForegroundApp
            *
            * Expected Outcome:
            * Both wallet role holder and the foreground app holds PAYMENT_AID_1.
            * So the foreground app is expected to get the routing for PAYMENT_AID_1.
            *
            * The foreground app does not have NON_PAYMENT_AID_1. Neither does the role holder.
            * So an app in the background (Non Payment App) gets the routing.
            **/
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getForegroundService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getForegroundService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getNonPaymentService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
            Assert.assertTrue(instance.unsetPreferredService(activity));
            activity.finish();
        });
        setMockService();
    }

    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    @Test
    public void testAidResolutionWithRoleHolder() throws NoSuchFieldException {
        restoreOriginalService();
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        runWithRole(mContext, WalletRoleTestUtils.WALLET_HOLDER_PACKAGE_NAME, ()-> {
            /*
             * Aid Mapping:
             * Wallet Holder App: Service 1:     PAYMENT_AID_1, PAYMENT_AID_2
             * Wallet Holder App: Service 2:     PAYMENT_AID_1, PAYMENT_AID_2
             * Foreground App :   Only Service:  PAYMENT_AID_1
             * Non Payment App:   Only Service:  NON_PAYMENT_AID_1
             *
             * Scenario:
             * Wallet Role Holder is WalletRoleHolderApp
             * Foreground app: None
             *
             * Expected Outcome:
             * Wallet role holder and a background app holds PAYMENT_AID_1.
             * So the Wallet role holder app is expected to get the routing for PAYMENT_AID_1.
             * The wallet role holder has two services holding PAYMENT_AID_1. Therefore the one
             * that has the priority based on alphabetical sorting of their names gets the routing
             * WalletRoleHolderService vs XWAlletRoleHolderService. WalletRoleHolderService gets it.
             *
             * Only the Wallet Role Holder holds PAYMENT_AID_2.
             * So the wallet role holder app gets the routing for PAYMENT_AID_2.
             *
             * A background app that is not the wallet role holder has the NON_PAYMENT_AID_1.
             * So that app gets the routing for NON_PAYMENT_AID_1.
             **/
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderXService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderXService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getForegroundService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getNonPaymentService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
        });
        setMockService();
    }

    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    @Test
    public void testAidResolutionWithRoleHolderSetToNone() throws NoSuchFieldException {
        restoreOriginalService();
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        runWithRoleNone(mContext, ()-> {
            /*
             * Aid Mapping:
             * Wallet Holder App: Service 1:     PAYMENT_AID_1, PAYMENT_AID_2
             * Wallet Holder App: Service 2:     PAYMENT_AID_1, PAYMENT_AID_2
             * Foreground App :   Only Service:  PAYMENT_AID_1
             * Non Payment App:   Only Service:  NON_PAYMENT_AID_1
             *
             * Scenario:
             * Wallet Role Holder is Set to None
             *
             * Expected Outcome:
             * Wallet role holder does not exist therefore routing is handled on the basis of
             * supported AIDs and overlapping services.
             *
             * Non Payment App is the only map holding the NON_PAYMENT_AID_1 and will be set
             * as the default service for that AID.
             *
             *  The rest of the apps will always need to disambig and will not be set as defaults.
             *
             **/
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderXService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderXService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getForegroundService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getNonPaymentService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
        });
    }

    @RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    @Test
    public void testAidResolutionWithRoleHolder_holderDoesNotSupportAid_overLappingAids()
            throws NoSuchFieldException {
        restoreOriginalService();
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        runWithRole(mContext, WalletRoleTestUtils.NON_PAYMENT_NFC_PACKAGE_NAME, ()-> {
            /*
             * Aid Mapping:
             * Wallet Holder App: Service 1:     PAYMENT_AID_1, PAYMENT_AID_2
             * Wallet Holder App: Service 2:     PAYMENT_AID_1, PAYMENT_AID_2
             * Foreground App :   Only Service:  PAYMENT_AID_1
             * Non Payment App:   Only Service:  NON_PAYMENT_AID_1
             *
             * Scenario:
             * Wallet Role Holder is Non Payment App
             * Foreground app: None
             *
             * Expected Outcome:
             * Wallet role holder holds NON_PAYMENT_AID_1
             * So wallet role holders gets the routing for NON_PAYMENT_AID_1.
             * The non wallet apps have overlapping aids and therefore no default services exist
             * for those AIDs.
             *
             **/
            Assert.assertTrue(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getNonPaymentService(),
                    WalletRoleTestUtils.NON_PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderXService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getWalletRoleHolderService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getForegroundService(),
                    WalletRoleTestUtils.PAYMENT_AID_1));
            Assert.assertFalse(instance.isDefaultServiceForAid(
                    WalletRoleTestUtils.getForegroundService(),
                    WalletRoleTestUtils.PAYMENT_AID_2));
        });
        setMockService();
    }

    @RequiresFlagsEnabled(Flags.FLAG_NFC_OVERRIDE_RECOVER_ROUTING_TABLE)
    @Test
    public void testOverrideRoutingTable() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertTrue(NfcUtils.enableNfc(adapter, mContext));
        final Activity activity = createAndResumeActivity();
        CardEmulation instance = CardEmulation.getInstance(adapter);
        Assert.assertThrows(SecurityException.class,
                () -> instance.overrideRoutingTable(activity,
                        CardEmulation.PROTOCOL_AND_TECHNOLOGY_ROUTE_DH,
                        CardEmulation.PROTOCOL_AND_TECHNOLOGY_ROUTE_UNSET));
        instance.setPreferredService(activity,
                new ComponentName(mContext, CtsMyHostApduService.class));
        instance.overrideRoutingTable(activity,
                CardEmulation.PROTOCOL_AND_TECHNOLOGY_ROUTE_DH,
                CardEmulation.PROTOCOL_AND_TECHNOLOGY_ROUTE_UNSET);
    }

    @RequiresFlagsEnabled(Flags.FLAG_NFC_OVERRIDE_RECOVER_ROUTING_TABLE)
    @Test
    public void testRecoverRoutingTable() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        Assert.assertTrue(NfcUtils.enableNfc(adapter, mContext));
        final Activity activity = createAndResumeActivity();
        CardEmulation instance = CardEmulation.getInstance(adapter);
        instance.recoverRoutingTable(activity);
    }

    private Activity createAndResumeActivity() {
        ensureUnlocked();
        Intent intent
            = new Intent(ApplicationProvider.getApplicationContext(),
                NfcFCardEmulationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().callActivityOnResume(activity);
        return activity;
    }

    private CardEmulation createMockedInstance() throws NoSuchFieldException {
        CardEmulation instance = CardEmulation.getInstance(mAdapter);
        FieldSetter.setField(instance, instance.getClass().getDeclaredField("sService"), mEmulation);
        return instance;
    }
}
