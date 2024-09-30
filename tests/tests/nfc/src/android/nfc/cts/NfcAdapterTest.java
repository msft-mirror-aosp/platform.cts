package android.nfc.cts;

import static android.Manifest.permission.NFC_SET_CONTROLLER_ALWAYS_ON;

import static com.android.compatibility.common.util.PropertyUtil.getVsrApiLevel;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.AvailableNfcAntenna;
import android.nfc.Flags;
import android.nfc.INfcAdapter;
import android.nfc.NfcAdapter;
import android.nfc.NfcAntennaInfo;
import android.nfc.NfcOemExtension;
import android.nfc.Tag;
import android.nfc.TechListParcel;
import android.nfc.cardemulation.CardEmulation;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RunWith(JUnit4.class)
public class NfcAdapterTest {

    @Mock private INfcAdapter mService;
    @Mock private DevicePolicyManager mDevicePolicyManager;
    private INfcAdapter mSavedService;
    private Context mContext;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private boolean supportsHardware() {
        final PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    @Before
    public void setUp() throws NoSuchFieldException {
        MockitoAnnotations.initMocks(this);
        mContext = spy(new ContextWrapper(InstrumentationRegistry.getContext()));
        assumeTrue(supportsHardware());
        // Backup the original service. It is being overridden
        // when creating a mocked adapter.
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        Assume.assumeNotNull(adapter);
        Assume.assumeTrue(NfcUtils.enableNfc(adapter, mContext));
        mSavedService = (INfcAdapter) (
            new FieldReader(adapter, adapter.getClass().getDeclaredField("sService")).read());
    }

    @After
    public void tearDown() throws NoSuchFieldException {
        if (!supportsHardware()) return;
        // Restore the original service.
        if (!supportsHardware()) {
            return;
        }
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        if (adapter != null) {
            FieldSetter.setField(adapter,
                    adapter.getClass().getDeclaredField("sService"), mSavedService);
        }
    }

    @Test
    public void testGetDefaultAdapter() {
        NfcAdapter adapter = getDefaultAdapter();
        Assert.assertNotNull(adapter);
    }

    @Test
    public void testAddNfcUnlockHandler() {
            NfcAdapter adapter = getDefaultAdapter();
            adapter.addNfcUnlockHandler(new CtsNfcUnlockHandler(), new String[]{"IsoDep"});
    }

    @Test
    public void testDisableWithNoParams() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        boolean result = adapter.disable();
        Assert.assertTrue(result);
        result = adapter.enable();
        Assert.assertTrue(result);
    }

    @Test
    public void testDisableWithParam() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        boolean result = adapter.disable(true);
        Assert.assertTrue(result);
    }

    @Test
    public void testDisableForegroundDispatch() {
            NfcAdapter adapter = getDefaultAdapter();
            Activity activity = createAndResumeActivity();
            adapter.disableForegroundDispatch(activity);
    }

    @Test
    public void testDisableReaderMode() {
            NfcAdapter adapter = getDefaultAdapter();
            Activity activity = createAndResumeActivity();
            adapter.disableReaderMode(activity);
    }

    @Test
    public void testEnable() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        boolean result = adapter.enable();
        Assert.assertTrue(result);
    }

    @Test
    public void testEnableForegroundDispatch() throws RemoteException {
            NfcAdapter adapter = getDefaultAdapter();
            Activity activity = createAndResumeActivity();
            Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NfcFCardEmulationActivity.class);
            PendingIntent pendingIntent
                = PendingIntent.getActivity(ApplicationProvider.getApplicationContext(),
                    0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            String[][] techLists = new String[][]{new String[]{}};
            doNothing().when(mService).setForegroundDispatch(any(PendingIntent.class),
                any(IntentFilter[].class), any(TechListParcel.class));
            adapter.enableForegroundDispatch(activity, pendingIntent, null, techLists);
    }

    @Test
    public void testEnableReaderMode() {
            NfcAdapter adapter = getDefaultAdapter();
            Activity activity = createAndResumeActivity();
            adapter.enableReaderMode(activity, new CtsReaderCallback(),
                NfcAdapter.FLAG_READER_NFC_A, new Bundle());
    }

    @Test
    public void testEnableReaderOption() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        adapter.enableReaderOption(true);
    }

    @Test
    public void testEnableSecureNfc() throws RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.setNfcSecure(anyBoolean())).thenReturn(true);
        boolean result = adapter.enableSecureNfc(true);
        Assert.assertTrue(result);
    }

    @Test
    public void testGetNfcAntennaInfo() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        NfcAntennaInfo info = new NfcAntennaInfo(0, 0, false,
            new ArrayList<AvailableNfcAntenna>());
        when(mService.getNfcAntennaInfo()).thenReturn(info);
        NfcAntennaInfo result = adapter.getNfcAntennaInfo();
        Assert.assertEquals(info, result);
        resetMockedInstance();
    }

    @Test
    public void testIgnore() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        Tag tag = new Tag(new byte[]{0x00}, new int[]{}, new Bundle[]{}, 0, 0L, null);
        when(mService.ignore(anyInt(), anyInt(), eq(null))).thenReturn(true);
        boolean result = adapter.ignore(tag, 0, null, null);
        Assert.assertTrue(result);
    }

    @Test
    public void testIsControllerAlwaysOn() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isControllerAlwaysOn()).thenReturn(true);
        boolean result = adapter.isControllerAlwaysOn();
        Assert.assertTrue(result);
        resetMockedInstance();
    }

    @Test
    public void testIsControllerAlwaysOnSupported() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isControllerAlwaysOnSupported()).thenReturn(true);
        boolean result = adapter.isControllerAlwaysOnSupported();
        Assert.assertTrue(result);
        resetMockedInstance();
    }

    @Test
    public void testIsEnabled() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.getState()).thenReturn(NfcAdapter.STATE_ON);
        boolean result = adapter.isEnabled();
        Assert.assertTrue(result);
        resetMockedInstance();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_READER_OPTION)
    public void testIsReaderOptionEnabled() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        boolean result = adapter.isReaderOptionEnabled();
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_READER_OPTION)
    public void testIsReaderOptionSupported() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isReaderOptionSupported()).thenReturn(true);
        boolean result = adapter.isReaderOptionSupported();
        Assert.assertTrue(result);
        resetMockedInstance();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
    public void testAdapterState() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.getState()).thenReturn(NfcAdapter.STATE_ON);
        Assert.assertEquals(adapter.getAdapterState(), NfcAdapter.STATE_ON);
        resetMockedInstance();
    }

    @Test
    public void testIsSecureNfcEnabled() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.isNfcSecureEnabled()).thenReturn(true);
        boolean result = adapter.isSecureNfcEnabled();
        Assert.assertTrue(result);
    }

    @Test
    public void testIsSecureNfcSupported() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = createMockedInstance();
        when(mService.deviceSupportsNfcSecure()).thenReturn(true);
        boolean result = adapter.isSecureNfcSupported();
        Assert.assertTrue(result);
        resetMockedInstance();
    }

    @Test
    public void testRemoveNfcUnlockHandler() {
        NfcAdapter adapter = getDefaultAdapter();
        boolean result = adapter.removeNfcUnlockHandler(new CtsNfcUnlockHandler());
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_SET_DISCOVERY_TECH)
    public void testResetDiscoveryTechnology() {
        NfcAdapter adapter = getDefaultAdapter();
        Activity activity = createAndResumeActivity();
        adapter.setDiscoveryTechnology(activity, NfcAdapter.FLAG_READER_KEEP,
                NfcAdapter.FLAG_LISTEN_KEEP);
        adapter.resetDiscoveryTechnology(activity);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_SET_DISCOVERY_TECH)
    public void testSetDiscoveryTechnology() {
        NfcAdapter adapter = getDefaultAdapter();
        Activity activity = createAndResumeActivity();
        adapter.setDiscoveryTechnology(activity,
                NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                | NfcAdapter.FLAG_READER_NFC_F,
                NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_A | NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_B
                | NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_F);
        adapter.resetDiscoveryTechnology(activity);
        adapter.setDiscoveryTechnology(activity, NfcAdapter.FLAG_READER_DISABLE,
                NfcAdapter.FLAG_LISTEN_KEEP);
        adapter.resetDiscoveryTechnology(activity);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_SET_DEFAULT_DISC_TECH)
    public void testSetDefaultDiscoveryTechnology() {
        NfcAdapter adapter = getDefaultAdapter();
        Activity activity = createAndResumeActivity();
        adapter.setDiscoveryTechnology(activity,
                NfcAdapter.FLAG_READER_KEEP,
                NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_B
                | NfcAdapter.FLAG_SET_DEFAULT_TECH);
        adapter.setDiscoveryTechnology(activity, NfcAdapter.FLAG_READER_KEEP,
                NfcAdapter.FLAG_LISTEN_KEEP | NfcAdapter.FLAG_SET_DEFAULT_TECH | 0xff);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
    public void testSetReaderMode() {
        NfcAdapter adapter = getDefaultAdapter();
        // Verify the API does not crash or throw any exceptions.
        adapter.setReaderModePollingEnabled(true);
        adapter.setReaderModePollingEnabled(false);
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testAllowTransaction() {
        ComponentName originalDefault = null;
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        try {
            originalDefault = setDefaultPaymentService(CtsMyHostApduService.class);
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
            assumeTrue(adapter.isObserveModeSupported());
            boolean result = adapter.setObserveModeEnabled(false);
            Assert.assertTrue(result);
        } finally {
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testDisallowTransaction() {
        ComponentName originalDefault = null;
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        try {
            originalDefault = setDefaultPaymentService(CtsMyHostApduService.class);

            assumeTrue(adapter.isObserveModeSupported());
            boolean result = adapter.setObserveModeEnabled(true);
            Assert.assertTrue(result);
        } finally {
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }


    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testDefaultObserveModePaymentDynamic() {
        ComponentName originalDefault = null;
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                    CustomHostApduService.class), true);
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            CardEmulationTest.ensurePreferredService(CustomHostApduService.class, mContext);
            Assert.assertTrue(adapter.isObserveModeEnabled());
            setDefaultPaymentService(CtsMyHostApduService.class);
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
            Assert.assertFalse(adapter.isObserveModeEnabled());
        } finally {
            cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                    CustomHostApduService.class), false);
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    public void testDefaultObserveModeForegroundDynamic() {
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        assumeTrue(adapter.isObserveModeSupported());
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            Activity activity = createAndResumeActivity();
            cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                    CustomHostApduService.class), true);
            cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                    CtsMyHostApduService.class), false);
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CustomHostApduService.class)));
            CardEmulationTest.ensurePreferredService(CustomHostApduService.class, mContext);
            Assert.assertTrue(adapter.isObserveModeEnabled());
            Assert.assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CtsMyHostApduService.class)));
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
            adapter.notifyHceDeactivated();
            Assert.assertFalse(adapter.isObserveModeEnabled());
        } finally {
            cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                    CustomHostApduService.class), false);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    public void testDefaultObserveModeOnlyWithServiceChange() {
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        assumeTrue(adapter.isObserveModeSupported());
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                CtsMyHostApduService.class), true);
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.CTS_PACKAGE_NAME, () -> {
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
            Assert.assertTrue(adapter.isObserveModeEnabled());
            Assert.assertTrue(adapter.setObserveModeEnabled(false));
            Assert.assertFalse(adapter.isObserveModeEnabled());
            try {
                Activity activity = createAndResumeActivity();
                Assert.assertTrue(cardEmulation.setPreferredService(activity,
                        new ComponentName(mContext, CtsMyHostApduService.class)));
                CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
                Assert.assertFalse(adapter.isObserveModeEnabled());
                Assert.assertTrue(adapter.setObserveModeEnabled(true));
                Assert.assertTrue(adapter.isObserveModeEnabled());
                Assert.assertTrue(cardEmulation.setPreferredService(activity,
                        new ComponentName(mContext, CustomHostApduService.class)));
                CardEmulationTest.ensurePreferredService(CustomHostApduService.class, mContext);
                Assert.assertFalse(adapter.isObserveModeEnabled());
            } finally {
                cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                        CustomHostApduService.class), false);
                cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                        CtsMyHostApduService.class), false);
                adapter.notifyHceDeactivated();
            }
        });
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testDefaultObserveModePayment() {
        ComponentName originalDefault = null;
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        try {
            originalDefault = setDefaultPaymentService(BackgroundHostApduService.class);
            CardEmulationTest.ensurePreferredService(BackgroundHostApduService.class, mContext);
            Assert.assertTrue(adapter.isObserveModeEnabled());
            setDefaultPaymentService(CtsMyHostApduService.class);
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
            Assert.assertFalse(adapter.isObserveModeEnabled());
        } finally {
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    public void testDefaultObserveModeForeground() {
        NfcAdapter adapter = getDefaultAdapter();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        cardEmulation.setShouldDefaultToObserveModeForService(
            new ComponentName(mContext, CtsMyHostApduService.class), false);
        Activity activity = createAndResumeActivity();
        adapter.notifyHceDeactivated();
        assumeTrue(adapter.isObserveModeSupported());
        Assert.assertTrue(cardEmulation.setPreferredService(activity,
                new ComponentName(mContext, BackgroundHostApduService.class)));
        CardEmulationTest.ensurePreferredService(BackgroundHostApduService.class, mContext);
        Assert.assertTrue(adapter.isObserveModeEnabled());
        Assert.assertTrue(cardEmulation.setPreferredService(activity,
                new ComponentName(mContext, CtsMyHostApduService.class)));
        CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
        Assert.assertFalse(adapter.isObserveModeEnabled());
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testAllowTransaction_walletRoleEnabled() {
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.CTS_PACKAGE_NAME, () -> {
            NfcAdapter adapter = getDefaultAdapter();
            adapter.notifyHceDeactivated();
            assumeTrue(adapter.isObserveModeSupported());
            adapter.setObserveModeEnabled(false);
            Assert.assertFalse(adapter.isObserveModeEnabled());
            adapter.notifyHceDeactivated();
        });
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testDisallowTransaction_walletRoleEnabled() {
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.CTS_PACKAGE_NAME, () -> {
            NfcAdapter adapter = getDefaultAdapter();
            adapter.notifyHceDeactivated();
            assumeTrue(adapter.isObserveModeSupported());
            adapter.setObserveModeEnabled(true);
            Assert.assertTrue(adapter.isObserveModeEnabled());
            adapter.notifyHceDeactivated();
        });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_CHARGING)
    public void testEnableNfcCharging() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        boolean result = adapter.setWlcEnabled(true);
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_CHARGING)
    public void testIsNfcChargingEnabled() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        boolean result = adapter.isWlcEnabled();
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_VENDOR_CMD)
    public void testSendVendorCmd() throws InterruptedException, RemoteException {
        assumeTrue(getVsrApiLevel() > Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        CountDownLatch rspCountDownLatch = new CountDownLatch(1);
        CountDownLatch ntfCountDownLatch = new CountDownLatch(1);
        NfcAdapter nfcAdapter = getDefaultAdapter();
        Assert.assertNotNull(nfcAdapter);
        NfcVendorNciCallback cb =
                new NfcVendorNciCallback(rspCountDownLatch, ntfCountDownLatch);
        try {
            nfcAdapter.registerNfcVendorNciCallback(
                    Executors.newSingleThreadExecutor(), cb);

            // Android GET_CAPS command
            int gid = 0xF;
            int oid = 0xC;
            byte[] payload = new byte[1];
            payload[0] = 0;
            nfcAdapter.sendVendorNciMessage(NfcAdapter.MESSAGE_TYPE_COMMAND, gid, oid, payload);

            // Wait for response.
            assertThat(rspCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(cb.gid).isEqualTo(gid);
            assertThat(cb.oid).isEqualTo(oid);
            assertThat(cb.payload).isNotEmpty();
        } finally {
            nfcAdapter.unregisterNfcVendorNciCallback(cb);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_STATE_CHANGE)
    public void testEnableByDeviceOwner() throws NoSuchFieldException, RemoteException {
        denyPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        when(mDevicePolicyManager.getDeviceOwnerUser())
                .thenReturn(new UserHandle(UserHandle.getCallingUserId()));
        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(ComponentName.createRelative("com.android.nfc", ".AdapterTest"));
        when(mContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);
        NfcAdapter adapter = getDefaultAdapter();
        boolean result = adapter.enable();
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_STATE_CHANGE)
    public void testDisableByDeviceOwner() throws NoSuchFieldException, RemoteException {
        denyPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        when(mDevicePolicyManager.getDeviceOwnerUser())
                .thenReturn(new UserHandle(UserHandle.getCallingUserId()));
        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(ComponentName.createRelative("com.android.nfc", ".AdapterTest"));
        when(mContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);
        NfcAdapter adapter = getDefaultAdapter();
        boolean result = adapter.disable();
        Assert.assertTrue(result);
        result = adapter.enable();
        Assert.assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_OBSERVE_MODE)
    public void testShouldDefaultToObserveModeAfterNfcOffOn() throws InterruptedException {
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        assumeTrue(adapter.isObserveModeSupported());
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        ComponentName ctsService = new ComponentName(mContext, CtsMyHostApduService.class);

        try {
            Assert.assertTrue(cardEmulation.setShouldDefaultToObserveModeForService(ctsService,
                    true));

            Assert.assertTrue(cardEmulation.setPreferredService(activity, ctsService));
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);

            Assert.assertTrue(adapter.isObserveModeEnabled());
            Assert.assertTrue(NfcUtils.disableNfc(adapter, mContext));
            Assert.assertTrue(NfcUtils.enableNfc(adapter, mContext));
            Assert.assertTrue(adapter.isObserveModeEnabled());
        } finally {
            cardEmulation.setShouldDefaultToObserveModeForService(ctsService,
                    false);
            cardEmulation.unsetPreferredService(activity);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_OBSERVE_MODE)
    public void testShouldDefaultToObserveModeWithNfcOff() throws InterruptedException {
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        assumeTrue(adapter.isObserveModeSupported());
        Activity activity = createAndResumeActivity();
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        ComponentName ctsService = new ComponentName(mContext, CtsMyHostApduService.class);
        try {
            Assert.assertTrue(NfcUtils.disableNfc(adapter, mContext));
            Assert.assertTrue(cardEmulation.setShouldDefaultToObserveModeForService(ctsService,
                    true));

            Assert.assertTrue(cardEmulation.setPreferredService(activity, ctsService));
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);

            Assert.assertTrue(NfcUtils.enableNfc(adapter, mContext));
            Assert.assertTrue(adapter.isObserveModeEnabled());
        } finally {
            cardEmulation.setShouldDefaultToObserveModeForService(ctsService,
                    false);
            cardEmulation.unsetPreferredService(activity);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_OEM_EXTENSION)
    public void testOemExtension() throws InterruptedException {
        CountDownLatch tagDetectedCountDownLatch = new CountDownLatch(3);
        NfcAdapter nfcAdapter = getDefaultAdapter();
        Assert.assertNotNull(nfcAdapter);
        NfcOemExtension nfcOemExtension = nfcAdapter.getNfcOemExtension();
        Assert.assertNotNull(nfcOemExtension);
        NfcOemExtensionCallback cb =
                new NfcOemExtensionCallback(tagDetectedCountDownLatch);
        try {
            nfcOemExtension.registerCallback(
                    Executors.newSingleThreadExecutor(), cb);
            tagDetectedCountDownLatch.await();

            // TODO: Fix these tests as we add more functionality to this API surface.
            nfcOemExtension.clearPreference();
            nfcOemExtension.synchronizeScreenState();
            assertThat(nfcOemExtension.getActiveNfceeList()).isNotEmpty();
            nfcOemExtension.triggerInitialization();
            nfcOemExtension.hasUserEnabledNfc();
            nfcOemExtension.isTagPresent();
            nfcOemExtension.pausePolling(1000);
            nfcOemExtension.resumePolling();
        } finally {
            nfcOemExtension.unregisterCallback(cb);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_OEM_EXTENSION)
    public void testOemExtensionSetControllerAlwaysOn() throws InterruptedException {
        NfcAdapter nfcAdapter = getDefaultAdapter();
        Assert.assertNotNull(nfcAdapter);
        NfcOemExtension nfcOemExtension = nfcAdapter.getNfcOemExtension();
        Assert.assertNotNull(nfcOemExtension);
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().adoptShellPermissionIdentity(NFC_SET_CONTROLLER_ALWAYS_ON);
        assumeTrue(nfcAdapter.isControllerAlwaysOnSupported());
        NfcControllerAlwaysOnListener cb = null;
        CountDownLatch countDownLatch;
        try {
            countDownLatch = new CountDownLatch(1);
            cb = new NfcControllerAlwaysOnListener(countDownLatch);
            nfcAdapter.registerControllerAlwaysOnListener(
                    Executors.newSingleThreadExecutor(), cb);
            nfcOemExtension.setControllerAlwaysOnMode(NfcOemExtension.ENABLE_TRANSPARENT);
            assertTrue(countDownLatch.await(1, TimeUnit.SECONDS));
            nfcAdapter.unregisterControllerAlwaysOnListener(cb);

            countDownLatch = new CountDownLatch(1);
            cb = new NfcControllerAlwaysOnListener(countDownLatch);
            nfcAdapter.registerControllerAlwaysOnListener(
                    Executors.newSingleThreadExecutor(), cb);
            nfcOemExtension.setControllerAlwaysOnMode(NfcOemExtension.DISABLE);
            assertTrue(countDownLatch.await(1, TimeUnit.SECONDS));
            nfcAdapter.unregisterControllerAlwaysOnListener(cb);
        } finally {
            if (cb != null) nfcAdapter.unregisterControllerAlwaysOnListener(cb);
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private class NfcControllerAlwaysOnListener implements NfcAdapter.ControllerAlwaysOnListener {
        private final CountDownLatch mCountDownLatch;

        NfcControllerAlwaysOnListener(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onControllerAlwaysOnChanged(boolean isEnabled) {
            mCountDownLatch.countDown();
        }
    }

    private class NfcOemExtensionCallback implements NfcOemExtension.Callback {
        private final CountDownLatch mTagDetectedCountDownLatch;

        NfcOemExtensionCallback(CountDownLatch countDownLatch) {
            mTagDetectedCountDownLatch = countDownLatch;
        }

        @Override
        public void onTagConnected(boolean connected, Tag tag) {
            mTagDetectedCountDownLatch.countDown();
        }

        @Override
        public void onStateUpdated(int state) {
        }

        @Override
        public void onApplyRouting(@NonNull Consumer<Boolean> isSkipped) {
        }

        @Override
        public void onNdefRead(@NonNull Consumer<Boolean> isSkipped) {
        }

        @Override
        public void onEnable(@NonNull Consumer<Boolean> isAllowed) {
        }

        @Override
        public void onDisable(@NonNull Consumer<Boolean> isAllowed) {
        }

        @Override
        public void onBootStarted() {
        }

        @Override
        public void onEnableStarted() {
        }

        @Override
        public void onDisableStarted() {
        }

        @Override
        public void onBootFinished(int status) {
        }

        @Override
        public void onEnableFinished(int status) {
        }

        @Override
        public void onDisableFinished(int status) {
        }

        @Override
        public void onTagDispatch(@NonNull Consumer<Boolean> isSkipped) {
        }

        @Override
        public void onRoutingChanged() {
        }

        @Override
        public void onHceEventReceived(int action) {
        }

        @Override
        public void onReaderOptionChanged(boolean enabled) {
        }

        public void onCardEmulationActivated(boolean isActivated) {
            mTagDetectedCountDownLatch.countDown();
        }

        @Override
        public void onRfFieldActivated(boolean isActivated) {
            mTagDetectedCountDownLatch.countDown();
        }

        @Override
        public void onRfDiscoveryStarted(boolean isDiscoveryStarted) {
            mTagDetectedCountDownLatch.countDown();
        }
    }

    private class NfcVendorNciCallback implements NfcAdapter.NfcVendorNciCallback {
        private final CountDownLatch mRspCountDownLatch;
        private final CountDownLatch mNtfCountDownLatch;

        public int gid;
        public int oid;
        public byte[] payload;

        NfcVendorNciCallback(CountDownLatch rspCountDownLatch, CountDownLatch ntfCountDownLatch) {
            mRspCountDownLatch = rspCountDownLatch;
            mNtfCountDownLatch = ntfCountDownLatch;
        }

        @Override
        public void onVendorNciResponse(int gid, int oid, byte[] payload) {
            this.gid = gid;
            this.oid = oid;
            this.payload = payload;
            mRspCountDownLatch.countDown();
        }

        @Override
        public void onVendorNciNotification(int gid, int oid, byte[] payload) {
            this.gid = gid;
            this.oid = oid;
            this.payload = payload;
            mNtfCountDownLatch.countDown();
        }
    }

    private class CtsReaderCallback implements NfcAdapter.ReaderCallback {
        @Override
        public void onTagDiscovered(Tag tag) {}
    }

    private class CtsNfcUnlockHandler implements NfcAdapter.NfcUnlockHandler {
        @Override
        public boolean onUnlockAttempted(Tag tag) {
            return true;
        }
    }

    private Activity createAndResumeActivity() {
        CardEmulationTest.ensureUnlocked();
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
            NfcFCardEmulationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().callActivityOnResume(activity);
        return activity;
    }

    private NfcAdapter getDefaultAdapter() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        try {
            FieldSetter.setField(adapter, adapter.getClass().getDeclaredField("sService"),
                    mSavedService);
        } catch (NoSuchFieldException nsfe) {
            throw new RuntimeException(nsfe);
        }
        return adapter;
    }

    private NfcAdapter createMockedInstance() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        try {
            FieldSetter.setField(adapter, adapter.getClass().getDeclaredField("sService"),
                    mService);
        } catch (NoSuchFieldException nsfe)  {
            throw new RuntimeException(nsfe);
        }
        return adapter;
    }

    private void resetMockedInstance() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        try {
            FieldSetter.setField(adapter, adapter.getClass().getDeclaredField("sService"),
                    mSavedService);
        } catch (NoSuchFieldException nsfe) {
            throw new RuntimeException(nsfe);
        }
    }

    private ComponentName setDefaultPaymentService(Class serviceClass) {
        ComponentName componentName = setDefaultPaymentService(
                new ComponentName(mContext, serviceClass));
        if (componentName == null) {
            return null;
        }
        return componentName;
    }

    private ComponentName setDefaultPaymentService(ComponentName serviceName) {
        if (serviceName == null) {
            return null;
        }
        return DefaultPaymentProviderTestUtils.setDefaultPaymentService(serviceName, mContext);
    }

    private void denyPermission(String permission) {
        when(mContext.checkCallingOrSelfPermission(permission))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(permission), anyString());
    }
}
