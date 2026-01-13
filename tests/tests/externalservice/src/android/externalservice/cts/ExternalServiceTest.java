/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.externalservice.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.externalservice.common.RunningServiceInfo;
import android.externalservice.common.ServiceMessages;
import android.nativeservice.simple.ISimpleNativeService;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExternalServiceTest {
    private static final String TAG = "ExternalServiceTest";

    private static final String SERVICE_PACKAGE = "android.externalservice.service";

    private final Connection mConnection = new Connection();

    private final ConditionVariable mCondition = new ConditionVariable(false);

    private static final int CONDITION_TIMEOUT = 10 * 1000; // 10 seconds
    private Context mContext;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @After
    public void tearDown() {
        if (mConnection.service != null) {
            mContext.unbindService(mConnection);
        }
    }

    /** Tests that an isolatedProcess service cannot be bound to by an external package. */
    @Test
    public void testFailBindIsolated() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".IsolatedService"));
        assertThrows(
                "Should not be able to bind to non-exported, non-external service",
                SecurityException.class,
                () -> mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE));
    }

    /** Tests that BIND_EXTERNAL_SERVICE does not work with plain isolatedProcess services. */
    @Test
    public void testFailBindExternalIsolated() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".IsolatedService"));
        assertThrows(
                "Should not be able to BIND_EXTERNAL_SERVICE to non-exported, non-external service",
                SecurityException.class,
                () ->
                        mContext.bindService(
                                intent,
                                mConnection,
                                Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));
    }

    /**
     * Tests that BIND_EXTERNAL_SERVICE does not work with exported, isolatedProcess services (
     * requires externalService as well).
     */
    @Test
    public void testFailBindExternalExported() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".ExportedService"));
        assertThrows(
                "Should not be able to BIND_EXTERNAL_SERVICE to non-external service",
                SecurityException.class,
                () ->
                        mContext.bindService(
                                intent,
                                mConnection,
                                Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));
    }

    /** Tests that BIND_EXTERNAL_SERVICE requires that an externalService be exported. */
    @Test
    public void testFailBindExternalNonExported() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(
                        SERVICE_PACKAGE, SERVICE_PACKAGE + ".ExternalNonExportedService"));
        assertThrows(
                "Should not be able to BIND_EXTERNAL_SERVICE to non-exported service",
                SecurityException.class,
                () ->
                        mContext.bindService(
                                intent,
                                mConnection,
                                Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));
    }

    /** Tests that BIND_EXTERNAL_SERVICE requires the service be an isolatedProcess. */
    @Test
    public void testFailBindExternalNonIsolated() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(
                        SERVICE_PACKAGE, SERVICE_PACKAGE + ".ExternalNonIsolatedService"));
        assertThrows(
                "Should not be able to BIND_EXTERNAL_SERVICE to non-isolated service",
                SecurityException.class,
                () ->
                        mContext.bindService(
                                intent,
                                mConnection,
                                Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));
    }

    /** Tests that an externalService can only be bound with BIND_EXTERNAL_SERVICE. */
    @Test
    public void testFailBindWithoutBindExternal() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".ExternalService"));
        assertThrows(
                "Should not be able to bind to an external service without BIND_EXTERNAL_SERVICE",
                SecurityException.class,
                () -> mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE));
    }

    /** Tests that an external service can be bound, and that it runs as a different principal. */
    @Test
    public void testBindExternalService() {
        // Start the service and wait for connection.
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".ExternalService"));

        mCondition.close();
        assertTrue(
                mContext.bindService(
                        intent,
                        mConnection,
                        Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));

        assertTrue(mCondition.block(CONDITION_TIMEOUT));
        assertEquals(mContext.getPackageName(), mConnection.name.getPackageName());
        assertNotSame(SERVICE_PACKAGE, mConnection.name.getPackageName());

        // Check the identity of the service.
        Messenger remote = new Messenger(mConnection.service);
        RunningServiceInfo id = identifyService(remote);
        assertNotNull(id);

        assertFalse(id.uid == 0 || id.pid == 0);
        assertNotEquals(Process.myUid(), id.uid);
        assertNotEquals(Process.myPid(), id.pid);
        assertEquals(mContext.getPackageName(), id.packageName);
        assertEquals(-1, id.zygotePid);
    }

    /**
     * Tests that the APK providing the externalService can bind the service itself, and that other
     * APKs bind to a different instance of it.
     */
    @Test
    public void testBindExternalServiceWithRunningOwn() {
        // Start the service that will create the externalService.
        final Connection creatorConnection = new Connection();
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".ServiceCreator"));

        mCondition.close();
        assertTrue(mContext.bindService(intent, creatorConnection, Context.BIND_AUTO_CREATE));
        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        // Get the identity of the creator.
        Messenger remoteCreator = new Messenger(creatorConnection.service);
        RunningServiceInfo creatorId = identifyService(remoteCreator);
        assertNotNull(creatorId);
        assertFalse(creatorId.uid == 0 || creatorId.pid == 0);

        // Have the creator actually start its service.
        final Message creatorMsg =
                Message.obtain(null, ServiceMessages.MSG_CREATE_EXTERNAL_SERVICE);
        Handler creatorHandler =
                new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        Log.d(TAG, "Received message: " + msg);
                        if (msg.what == ServiceMessages.MSG_CREATE_EXTERNAL_SERVICE_RESPONSE) {
                            creatorMsg.copyFrom(msg);
                            mCondition.open();
                        }
                        super.handleMessage(msg);
                    }
                };
        Messenger localCreator = new Messenger(creatorHandler);
        creatorMsg.replyTo = localCreator;
        try {
            mCondition.close();
            remoteCreator.send(creatorMsg);
        } catch (RemoteException e) {
            fail("Unexpected remote exception" + e);
            return;
        }
        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        // Get the connection to the creator's service.
        assertNotNull(creatorMsg.obj);
        Messenger remoteCreatorService = (Messenger) creatorMsg.obj;
        RunningServiceInfo creatorServiceId = identifyService(remoteCreatorService);
        assertNotNull(creatorServiceId);
        assertFalse(creatorServiceId.uid == 0 || creatorId.pid == 0);

        // Create an external service from this (the test) process.
        intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".ExternalService"));

        mCondition.close();
        assertTrue(
                mContext.bindService(
                        intent,
                        mConnection,
                        Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));
        assertTrue(mCondition.block(CONDITION_TIMEOUT));
        RunningServiceInfo serviceId = identifyService(new Messenger(mConnection.service));
        assertNotNull(serviceId);
        assertFalse(serviceId.uid == 0 || serviceId.pid == 0);

        // Make sure that all the processes are unique.
        int myUid = Process.myUid();
        int myPid = Process.myPid();
        String myPkg = mContext.getPackageName();

        assertNotEquals(myUid, creatorId.uid);
        assertNotEquals(myUid, creatorServiceId.uid);
        assertNotEquals(myUid, serviceId.uid);
        assertNotEquals(myPid, creatorId.pid);
        assertNotEquals(myPid, creatorServiceId.pid);
        assertNotEquals(myPid, serviceId.pid);

        assertNotEquals(creatorId.uid, creatorServiceId.uid);
        assertNotEquals(creatorId.uid, serviceId.uid);
        assertNotEquals(creatorId.pid, creatorServiceId.pid);
        assertNotEquals(creatorId.pid, serviceId.pid);

        assertNotEquals(creatorServiceId.uid, serviceId.uid);
        assertNotEquals(creatorServiceId.pid, serviceId.pid);

        assertEquals(-1, creatorServiceId.zygotePid);
        assertEquals(-1, serviceId.zygotePid);
        assertNull(creatorServiceId.zygotePackage);
        assertNull(serviceId.zygotePackage);

        assertNotEquals(myPkg, creatorId.packageName);
        assertNotEquals(myPkg, creatorServiceId.packageName);
        assertEquals(creatorId.packageName, creatorServiceId.packageName);
        assertEquals(myPkg, serviceId.packageName);

        mContext.unbindService(creatorConnection);
    }

    /** Tests that the binding to an externalService can be changed. */
    @Test
    public void testBindExternalAboveClient() {
        // Start the service and wait for connection.
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".ExternalService"));

        mCondition.close();
        Connection initialConn = new Connection();
        assertTrue(
                mContext.bindService(
                        intent,
                        initialConn,
                        Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));

        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        RunningServiceInfo idOne = identifyService(new Messenger(initialConn.service));
        assertNotNull(idOne);
        assertFalse(idOne.uid == 0 || idOne.pid == 0);

        // Bind the service with a different priority.
        mCondition.close();
        Connection prioConn = new Connection();
        assertTrue(
                mContext.bindService(
                        intent,
                        prioConn,
                        Context.BIND_AUTO_CREATE
                                | Context.BIND_EXTERNAL_SERVICE
                                | Context.BIND_ABOVE_CLIENT));

        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        RunningServiceInfo idTwo = identifyService(new Messenger(prioConn.service));
        assertNotNull(idTwo);
        assertFalse(idTwo.uid == 0 || idTwo.pid == 0);

        assertEquals(idOne.uid, idTwo.uid);
        assertEquals(idOne.pid, idTwo.pid);
        assertEquals(idOne.packageName, idTwo.packageName);
        assertNotEquals(Process.myUid(), idOne.uid);
        assertNotEquals(Process.myPid(), idOne.pid);
        assertEquals(mContext.getPackageName(), idOne.packageName);

        mContext.unbindService(prioConn);
        mContext.unbindService(initialConn);
    }

    /** Tests binding an externalService that is started by an app zygote. */
    @Test
    public void testBindExternalServiceWithZygote() {
        // Start the service and wait for connection.
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".ExternalServiceWithZygote"));

        mCondition.close();
        assertTrue(
                mContext.bindService(
                        intent,
                        mConnection,
                        Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));

        assertTrue(mCondition.block(CONDITION_TIMEOUT));
        assertEquals(mContext.getPackageName(), mConnection.name.getPackageName());
        assertNotSame(SERVICE_PACKAGE, mConnection.name.getPackageName());

        // Check the identity of the service.
        Messenger remote = new Messenger(mConnection.service);
        RunningServiceInfo id = identifyService(remote);
        assertNotNull(id);

        assertFalse(id.uid == 0 || id.pid == 0);
        assertNotEquals(Process.myUid(), id.uid);
        assertNotEquals(Process.myPid(), id.pid);
        assertEquals(mContext.getPackageName(), id.packageName);
        assertNotEquals(id.pid, id.zygotePid);
        assertNotEquals(Process.myPid(), id.zygotePid);
        assertNotEquals(-1, id.zygotePid);
        assertEquals(SERVICE_PACKAGE, id.zygotePackage);
    }

    /** Tests binding an externalService that is started by the native zygote. */
    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_NATIVE_FRAMEWORK_PROTOTYPE)
    public void testBindExternalNativeService() {
        // Start the service and wait for connection.
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".ExternalNativeService"));

        mCondition.close();
        assertTrue(
                mContext.bindService(
                        intent,
                        mConnection,
                        Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));

        assertTrue(mCondition.block(CONDITION_TIMEOUT));
        assertEquals(mContext.getPackageName(), mConnection.name.getPackageName());
        assertNotSame(SERVICE_PACKAGE, mConnection.name.getPackageName());

        // Check the identity of the service.
        final ISimpleNativeService service =
                ISimpleNativeService.Stub.asInterface(mConnection.service);
        int pid = 0;
        int uid = 0;
        try {
            pid = service.getPid();
            uid = service.getUid();
        } catch (RemoteException e) {
            fail("Unexpected remote exception" + e);
        }

        assertFalse(uid == 0 || pid == 0);
        assertNotEquals(Process.myUid(), uid);
        assertNotEquals(Process.myPid(), pid);
    }

    /**
     * Tests that an externalService that also is also useAppZygote=true shares the same zygote,
     * even when bound by different packages..
     */
    @Test
    public void testBindExternalServiceWithZygoteWithRunningOwn() {
        // Start the service that will create the externalService.
        final Connection creatorConnection = new Connection();
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".ServiceCreator"));

        mCondition.close();
        assertTrue(mContext.bindService(intent, creatorConnection, Context.BIND_AUTO_CREATE));
        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        // Have the creator actually start its service.
        Messenger remoteCreator = new Messenger(creatorConnection.service);
        final Message creatorMsg = Message.obtain(null, ServiceMessages.MSG_CREATE_EXTERNAL_SERVICE,
                        /* use zygote */ 1, 0);
        Handler creatorHandler =
                new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        Log.d(TAG, "Received message: " + msg);
                        if (msg.what == ServiceMessages.MSG_CREATE_EXTERNAL_SERVICE_RESPONSE) {
                            creatorMsg.copyFrom(msg);
                            mCondition.open();
                        }
                        super.handleMessage(msg);
                    }
                };
        Messenger localCreator = new Messenger(creatorHandler);
        creatorMsg.replyTo = localCreator;
        try {
            mCondition.close();
            remoteCreator.send(creatorMsg);
        } catch (RemoteException e) {
            fail("Unexpected remote exception" + e);
            return;
        }
        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        // Get the connection to the creator's service.
        assertNotNull(creatorMsg.obj);
        Messenger remoteCreatorService = (Messenger) creatorMsg.obj;
        RunningServiceInfo creatorServiceId = identifyService(remoteCreatorService);
        assertNotNull(creatorServiceId);
        Assert.assertNotEquals(0, creatorServiceId.uid);

        // Create an external service from this (the test) process.
        intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".ExternalServiceWithZygote"));

        mCondition.close();
        assertTrue(
                mContext.bindService(
                        intent,
                        mConnection,
                        Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));
        assertTrue(mCondition.block(CONDITION_TIMEOUT));
        RunningServiceInfo serviceId = identifyService(new Messenger(mConnection.service));
        assertNotNull(serviceId);
        assertFalse(serviceId.uid == 0 || serviceId.pid == 0);

        // Make sure that both services were started by the same zygote.
        assertNotEquals(-1, creatorServiceId.zygotePid);
        assertNotEquals(-1, serviceId.zygotePid);
        assertNotEquals(creatorServiceId.pid, creatorServiceId.zygotePid);
        assertNotEquals(serviceId.pid, serviceId.zygotePid);
        assertEquals(SERVICE_PACKAGE, creatorServiceId.zygotePackage);
        assertEquals(SERVICE_PACKAGE, serviceId.zygotePackage);
        assertEquals(creatorServiceId.zygotePid, serviceId.zygotePid);

        int myUid = Process.myUid();
        int myPid = Process.myPid();
        String myPkg = mContext.getPackageName();

        assertNotEquals(myUid, creatorServiceId.uid);
        assertNotEquals(myUid, serviceId.uid);
        assertNotEquals(myPid, creatorServiceId.pid);
        assertNotEquals(myPid, serviceId.pid);

        assertNotEquals(creatorServiceId.uid, serviceId.uid);
        assertNotEquals(creatorServiceId.pid, serviceId.pid);

        assertNotEquals(myPkg, creatorServiceId.packageName);
        assertEquals(myPkg, serviceId.packageName);

        mContext.unbindService(creatorConnection);
    }

    /**
     * Test when the flag BIND_EXTERNAL_SERVICE(0x80000000) is set in 64 bits long flags, an
     * IllegalArgumentException is thrown. The reason is that integer 0x80000000 is automatically
     * converted to long 0xffff_ffff_8000_000, it is not a correct flag any more. In 64 bits long
     * flags, use BIND_EXTERNAL_SERVICE_LONG(0x4000_0000_0000_0000L) instead.
     */
    @Test
    public void testFailBindExternalServiceLongFlags() {
        // Start the service and wait for connection.
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".ExternalService"));

        mCondition.close();

        long longFlags = Context.BIND_EXTERNAL_SERVICE | Context.BIND_AUTO_CREATE;
        assertThrows(
                "Context.BIND_EXTERNAL_SERVICE can not be used in 64 bits bindService() flags",
                IllegalArgumentException.class,
                () ->
                        mContext.bindService(
                                intent,
                                mConnection,
                                Context.BindServiceFlags.of(longFlags) /* 64 bits long flag */));
    }

    /**
     * Test 64 bits long flag BIND_EXTERNAL_SERVICE_LONG(0x4000_0000_0000_0000L), it deprecates 32
     * bits flag BIND_EXTERNAL_SERVICE(0x80000000) when 64 bits long flags is used.
     */
    @Test
    public void testBindExternalServiceLongFlags() {
        // Start the service and wait for connection.
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".ExternalService"));

        mCondition.close();

        long longFlags = Context.BIND_EXTERNAL_SERVICE_LONG | Context.BIND_AUTO_CREATE;

        assertTrue(
                mContext.bindService(
                        intent,
                        mConnection,
                        Context.BindServiceFlags.of(longFlags) /* 64 bits long flag */));
        assertTrue(mCondition.block(CONDITION_TIMEOUT));
        assertEquals(mContext.getPackageName(), mConnection.name.getPackageName());
        assertNotSame(SERVICE_PACKAGE, mConnection.name.getPackageName());
    }

    /** Given a Messenger, this will message the service to retrieve its UID, PID, and package name.
     * On success, returns a RunningServiceInfo. On failure, returns null. */
    private RunningServiceInfo identifyService(Messenger service) {
        try {
            return RunningServiceInfo.identifyService(service, TAG, mCondition);
        } catch (RemoteException e) {
            fail("Unexpected remote exception: " + e);
            return null;
        }
    }

    private class Connection implements ServiceConnection {
        IBinder service = null;
        ComponentName name = null;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected " + name);
            this.service = service;
            this.name = name;
            mCondition.open();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected " + name);
        }
    }

    private <T> void assertNotEquals(T expected, T actual) {
        Assert.assertNotEquals(
                "Expected <" + expected + "> should not be equal to actual <" + actual + ">",
                expected,
                actual);
    }
}
