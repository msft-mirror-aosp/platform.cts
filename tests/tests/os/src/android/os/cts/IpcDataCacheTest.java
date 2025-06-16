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

package android.os.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.os.Flags;
import android.os.IpcDataCache;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test for verifying the behavior of {@link IpcDataCache}.  This test does
 * not use any actual binder calls - it is entirely self-contained.  This test also relies
 * on the test mode of {@link IpcDataCache} because Android SELinux rules do
 * not grant test processes the permission to set system properties.
 * <p>
 * Build/Install/Run:
 *  atest CtsOsTestCases:IpcDataCacheTest
 */
@SmallTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class IpcDataCacheTest {

    @Rule public final CheckFlagsRule mFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    // Configuration for creating caches
    private static final String MODULE = IpcDataCache.MODULE_TEST;
    private static final String API = "testApi";

    // This class is a proxy for binder calls.  It contains a counter that increments
    // every time the class is queried.
    private static class ServerProxy {
        // The number of times this class was queried.
        private int mCount = 0;

        // A single query.  The key behavior is that the query count is incremented.
        boolean query(int x) {
            mCount++;
            return value(x);
        }

        // Return the expected value of an input, without incrementing the query count.
        boolean value(int x) {
            return x % 3 == 0;
        }

        // Verify the count.
        void verify(int x) {
            assertEquals(x, mCount);
        }
    }

    // The functions for querying the server.
    private static class ServerQuery
            extends IpcDataCache.QueryHandler<Integer, Boolean> {
        private final ServerProxy mServer;

        ServerQuery(ServerProxy server) {
            mServer = server;
        }

        @Override
        public Boolean apply(Integer x) {
            return mServer.query(x);
        }

        @Override
        public boolean shouldBypassCache(Integer x) {
            return x % 13 == 0;
        }
    }

    // Prepare for test mode.
    @Before
    public void setUp() throws Exception {
        IpcDataCache.setTestMode(true);
    }

    // Clear the test mode after every test, in case this process is used for other
    // tests. This also resets the test property map.
    @After
    public void tearDown() throws Exception {
        IpcDataCache.setTestMode(false);
    }

    @Test
    public void testBasicCache() {

        // A stand-in for the binder.  The test verifies that calls are passed through to
        // this class properly.
        ServerProxy tester = new ServerProxy();

        // Create a cache that uses simple arithmetic to computer its values.
        IpcDataCache<Integer, Boolean> testCache =
                new IpcDataCache<>(4, MODULE, API, "testCache1",
                        new ServerQuery(tester));

        tester.verify(0);
        assertEquals(tester.value(3), testCache.query(3));
        tester.verify(1);
        assertEquals(tester.value(3), testCache.query(3));
        tester.verify(2);
        testCache.invalidateCache();
        assertEquals(tester.value(3), testCache.query(3));
        tester.verify(3);
        assertEquals(tester.value(5), testCache.query(5));
        tester.verify(4);
        assertEquals(tester.value(5), testCache.query(5));
        tester.verify(4);
        assertEquals(tester.value(3), testCache.query(3));
        tester.verify(4);

        // Invalidate the cache, and verify that the next read on 3 goes to the server.
        testCache.invalidateCache();
        assertEquals(tester.value(3), testCache.query(3));
        tester.verify(5);

        // Test bypass.  The query for 13 always bypasses the cache.
        assertEquals(tester.value(12), testCache.query(12));
        assertEquals(tester.value(13), testCache.query(13));
        assertEquals(tester.value(14), testCache.query(14));
        tester.verify(8);
        assertEquals(tester.value(12), testCache.query(12));
        assertEquals(tester.value(13), testCache.query(13));
        assertEquals(tester.value(14), testCache.query(14));
        tester.verify(9);
    }

    @Test
    public void testDisableCache() {

        // A stand-in for the binder.  The test verifies that calls are passed through to
        // this class properly.
        ServerProxy tester = new ServerProxy();

        // Three caches, all using the same system property but one uses a different name.
        IpcDataCache<Integer, Boolean> cache1 =
                new IpcDataCache<>(4, MODULE, API, "cacheA",
                        new ServerQuery(tester));
        IpcDataCache<Integer, Boolean> cache2 =
                new IpcDataCache<>(4, MODULE, API, "cacheA",
                        new ServerQuery(tester));
        IpcDataCache<Integer, Boolean> cache3 =
                new IpcDataCache<>(4, MODULE, API, "cacheB",
                        new ServerQuery(tester));

        // Caches are enabled upon creation.
        assertEquals(false, cache1.isDisabled());
        assertEquals(false, cache2.isDisabled());
        assertEquals(false, cache3.isDisabled());

        // Disable the cache1 instance.  Only cache1 is disabled
        cache1.disableInstance();
        assertEquals(true, cache1.isDisabled());
        assertEquals(false, cache2.isDisabled());
        assertEquals(false, cache3.isDisabled());

        // Disable cache1.  This will disable cache1 and cache2 because they share the
        // same name.  cache3 has a different name and will not be disabled.
        cache1.disableForCurrentProcess();
        assertEquals(true, cache1.isDisabled());
        assertEquals(true, cache2.isDisabled());
        assertEquals(false, cache3.isDisabled());

        // Create a new cache1.  Verify that the new instance is disabled.
        cache1 = new IpcDataCache<>(4, MODULE, API, "cacheA",
                new ServerQuery(tester));
        assertEquals(true, cache1.isDisabled());

        // Remove the record of caches being locally disabled.  This is a clean-up step.
        cache1.forgetDisableLocal();
        assertEquals(true, cache1.isDisabled());
        assertEquals(true, cache2.isDisabled());
        assertEquals(false, cache3.isDisabled());

        // Create a new cache1.  Verify that the new instance is not disabled.
        cache1 = new IpcDataCache<>(4, MODULE, API, "cacheA",
                new ServerQuery(tester));
        assertEquals(false, cache1.isDisabled());
    }

    private static class TestQuery
            extends IpcDataCache.QueryHandler<Integer, String> {

        private int mRecomputeCount = 0;

        @Override
        public String apply(Integer qv) {
            mRecomputeCount += 1;
            return "foo" + qv.toString();
        }

        int getRecomputeCount() {
            return mRecomputeCount;
        }
    }

    private static class TestCache extends IpcDataCache<Integer, String> {
        private final TestQuery mQuery;

        TestCache(String module, String api) {
            this(module, api, new TestQuery());
        }

        TestCache(String module, String api, TestQuery query) {
            super(4, module, api, api, query);
            mQuery = query;
        }

        int getRecomputeCount() {
            return mQuery.getRecomputeCount();
        }
    }

    @Test
    public void testCacheRecompute() {
        final String api = "testCacheRecompute";
        TestCache cache = new TestCache(MODULE, api);
        cache.invalidateCache();
        assertEquals(cache.isDisabled(), false);
        assertEquals("foo5", cache.query(5));
        assertEquals(1, cache.getRecomputeCount());
        assertEquals("foo5", cache.query(5));
        assertEquals(1, cache.getRecomputeCount());
        assertEquals("foo6", cache.query(6));
        assertEquals(2, cache.getRecomputeCount());
        cache.invalidateCache();
        assertEquals("foo5", cache.query(5));
        assertEquals("foo5", cache.query(5));
        assertEquals(3, cache.getRecomputeCount());
        // Invalidate the cache with a direct call to the property.
        IpcDataCache.invalidateCache(MODULE, api);
        assertEquals("foo5", cache.query(5));
        assertEquals("foo5", cache.query(5));
        assertEquals(4, cache.getRecomputeCount());
    }

    @Test
    public void testCacheInitialState() {
        final String api = "testCacheInitialState";
        TestCache cache = new TestCache(MODULE, api);
        assertEquals("foo5", cache.query(5));
        assertEquals("foo5", cache.query(5));
        assertEquals(2, cache.getRecomputeCount());
        cache.invalidateCache();
        assertEquals("foo5", cache.query(5));
        assertEquals("foo5", cache.query(5));
        assertEquals(3, cache.getRecomputeCount());
    }

    @Test
    public void testCachePropertyUnset() {
        final String api = "testCachePropertyUnset";
        TestCache cache = new TestCache(MODULE, api);
        assertEquals("foo5", cache.query(5));
        assertEquals("foo5", cache.query(5));
        assertEquals(2, cache.getRecomputeCount());
    }

    @Test
    public void testCacheDisableState() {
        final String api = "testCacheDisableState";
        TestCache cache = new TestCache(MODULE, api);
        assertEquals("foo5", cache.query(5));
        assertEquals("foo5", cache.query(5));
        assertEquals(2, cache.getRecomputeCount());
        cache.invalidateCache();
        assertEquals("foo5", cache.query(5));
        assertEquals("foo5", cache.query(5));
        assertEquals(3, cache.getRecomputeCount());
        cache.disableSystemWide();
        assertEquals("foo5", cache.query(5));
        assertEquals("foo5", cache.query(5));
        assertEquals(5, cache.getRecomputeCount());
        cache.invalidateCache();  // Should not reenable
        assertEquals("foo5", cache.query(5));
        assertEquals("foo5", cache.query(5));
        assertEquals(7, cache.getRecomputeCount());
    }

    // Validate the member method disableForCurrentProcess().
    @Test
    public void testLocalProcessDisable1() {
        final String api = "testLocalProcessDisable1";
        TestCache cache = new TestCache(MODULE, api);
        assertEquals(cache.isDisabled(), false);
        cache.invalidateCache();
        assertEquals("foo5", cache.query(5));
        assertEquals(1, cache.getRecomputeCount());
        assertEquals("foo5", cache.query(5));
        assertEquals(1, cache.getRecomputeCount());
        assertEquals(cache.isDisabled(), false);
        cache.disableForCurrentProcess();
        assertEquals(cache.isDisabled(), true);
        assertEquals("foo5", cache.query(5));
        assertEquals("foo5", cache.query(5));
        assertEquals(3, cache.getRecomputeCount());

        // Clean up so this test can be re-run
        cache.forgetDisableLocal();
    }

    // Validate the static method disableForCurrentProcess(String) and the static invalidateCache
    // method.
    @Test
    public void testLocalProcessDisable2() {
        final String api = "testLocalProcessDisable2";
        TestCache cache = new TestCache(MODULE, api);
        assertEquals(cache.isDisabled(), false);
        IpcDataCache.invalidateCache(MODULE, api);
        assertEquals("foo5", cache.query(5));
        assertEquals(1, cache.getRecomputeCount());
        assertEquals("foo5", cache.query(5));
        assertEquals(1, cache.getRecomputeCount());
        assertEquals(cache.isDisabled(), false);
        IpcDataCache.disableForCurrentProcess(api);
        assertEquals(cache.isDisabled(), true);
        assertEquals("foo5", cache.query(5));
        assertEquals("foo5", cache.query(5));
        assertEquals(3, cache.getRecomputeCount());

        // Clean up so this test can be re-run
        cache.forgetDisableLocal();
    }

    // setCacheTestMode() is preferred over setTestMode() (the former forwards to the latter) but
    // the API is flag-guarded and is therefore not guaranteed to be present in all builds.  This
    // test just verifies that the two APIs are interchangeable.
    @DisabledOnRavenwood(reason = "permissions are stubbed out in ravenwood")
    @RequiresFlagsEnabled(Flags.FLAG_IPC_DATA_CACHE_TESTMODE_APIS)
    @Test
    public void testCacheTestMode() {
        // Create a cache that will write a system nonce.
        TestCache sysCache = new TestCache(IpcDataCache.MODULE_SYSTEM, "mode1");

        // Invalidate the cache.  This must succeed because test mode is enabled in setup().
        sysCache.invalidateCache();

        // Create a cache that uses MODULE_TEST.  Invalidation succeeds regardless of test mode.
        TestCache testCache = new TestCache(IpcDataCache.MODULE_TEST, "mode2");
        testCache.invalidateCache();

        // Clear test mode.  This fails if test mode is not enabled.
        IpcDataCache.setCacheTestMode(false);
        try {
            IpcDataCache.setCacheTestMode(false);
            fail("expected an IllegalStateException");
        } catch (IllegalStateException e) {
            // The expected exception.
        }

        // The system cache cannot be invalidated with test mode disabled.
        try {
            sysCache.invalidateCache();
            fail("expected permission failure");
        } catch (RuntimeException e) {
            // The expected exception.
        }

        // The test cache can still be invalidated.
        testCache.invalidateCache();

        // Re-enable test mode (so that the cleanup for the test does not throw).
        IpcDataCache.setCacheTestMode(true);

        // Verify that system invalidation is now okay, since test mode is enabled.
        sysCache.invalidateCache();
    }

    // Create a cache in the specified module.
    private void testModule(String module) {
        TestCache cache = new TestCache(IpcDataCache.MODULE_SYSTEM, "system");
        cache.invalidateCache();
        IpcDataCache.setTestMode(false);
        try {
            cache.invalidateCache();
            fail("module " + module + " is writeable by outsiders");
        } catch (RuntimeException e) {
            // Expected exception
        } finally {
            IpcDataCache.setTestMode(true);
        }
    }

    // This test verifies that a cache can be created with every exported module.  It also
    // verifies that these modules cannot be invalidated outside test mode.  The TEST module is
    // specifically excluded because it can (by design) be invalidated outside test mode.  Modules
    // that are flag-guarded are tested in {@link #testModulesFlagged} until they are committed,
    // after which they should be moved into this test.
    @DisabledOnRavenwood(reason = "permissions are stubbed out in ravenwood")
    @Test
    public void testModules() {
        testModule(IpcDataCache.MODULE_BLUETOOTH);
        testModule(IpcDataCache.MODULE_SYSTEM);
    }

    // This is the same as testModules() except that it covers modules that are are currently
    // flag-guarded.  When the flag is committed, the module list here can be moved into
    // {@link #testModules}.
    @DisabledOnRavenwood(reason = "permissions are stubbed out in ravenwood")
    @RequiresFlagsEnabled(Flags.FLAG_IPC_DATA_CACHE_MODULE_ADSERVICES)
    @Test
    public void testModulesFlagged() {
        testModule(IpcDataCache.MODULE_ADSERVICES);
    }
}
