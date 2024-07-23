/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;



/*
 * These tests exercise kernel UAPIs for memory control groups (memcg) version 2, which Android may
 * use if configured to do so.
 *
 * No Android interfaces are tested here.
 *
 * If the device does not have memcg v2 enabled the tests will be skipped, but they will fail if
 * we cannot determine whether memcg v2 is enabled or not.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class MemcgV2HostTests extends BaseHostJUnit4Test {

    // For the remote possibility that it has been mounted somewhere other than the default
    private String getCgroupV2MountPoint() {
        try {
            CommandResult commandResult = getDevice().executeShellV2Command("mount | grep cgroup2");
            if (commandResult.getExitCode() == 0) {
                String[] tokens = commandResult.getStdout().split("\\s+");
                return tokens[2];
            }
        } catch (Exception ignore) { }

        // Hope the default is correct
        return "/sys/fs/cgroup";
    }

    private Boolean isMemcgV2Enabled() {
        try {
            CommandResult commandResult = getDevice().executeShellV2Command(
                    "grep memory /proc/cgroups");

            if (commandResult.getExitCode() == 0) {
                String[] tokens = commandResult.getStdout().split("\\s+");
                boolean memcg_enabled = tokens[3].equals("1");
                if (!memcg_enabled) return false;
                return tokens[1].equals("0"); // 0 == default hierarchy == v2
            } else if (commandResult.getExitCode() == 1) { // "memory" not found by grep
                // We know for sure it's not enabled, either because it is mounted as v1
                // (cgroups.json override), or because it was intentionally disabled via kernel
                // command line (cgroup_disable=memory), or because it's not built in to the
                // kernel (CONFIG_MEMCG is not set).
                return false;
            } else { // Problems accessing /proc/cgroups
                CLog.w("Could not read /proc/cgroups: " + commandResult.getStderr());
                // We don't really know if it's enabled or not. Try checking the root
                // cgroup.controllers file directly.
                String cg_controllers_path = mCgroupV2Root + "/cgroup.controllers";

                commandResult = getDevice().executeShellV2Command(
                    "grep memory " + cg_controllers_path);
                if (commandResult.getExitCode() == 0) return true;
                if (commandResult.getExitCode() == 1) return false;

                CLog.e("Could not determine if memcg v2 is enabled: " + commandResult.getStderr());
            }
        } catch (Exception e) {
            CLog.e(e.toString());
        }
        return null;
    }

    // Record the original subtree state for memcg so we can restore it after the test if necessary
    private Boolean checkRootSubtreeState() {
        try {
            CommandResult commandResult = getDevice().executeShellV2Command(
                    "grep memory " + mCgroupV2Root + "/cgroup.subtree_control");
            if (commandResult.getExitCode() == 0) return true;
            if (commandResult.getExitCode() == 1) return false;

            CLog.e("Could not determine if root memcg subtree control is enabled: "
                    + commandResult.getStderr());
        } catch (Exception e) {
            CLog.e(e.toString());
        }
        return null;
    }

    private String mCgroupV2Root;
    private Boolean mMemcgV2Enabled;
    private Boolean mRootSubtreeStateWasEnabled;
    private String mChildCgroup;

    @Before
    public void initialize() {
        mCgroupV2Root = getCgroupV2MountPoint();
        mMemcgV2Enabled = isMemcgV2Enabled();
        mRootSubtreeStateWasEnabled = checkRootSubtreeState();

        assertNotNull(mMemcgV2Enabled);
        assertNotNull(mRootSubtreeStateWasEnabled);
    }

    @After
    public void cleanup() {
        try {
            if (mChildCgroup != null) getDevice().executeShellV2Command("rmdir " + mChildCgroup);

            if (mRootSubtreeStateWasEnabled != null && !mRootSubtreeStateWasEnabled) {
                getDevice().executeShellV2Command(
                        "echo -memory > " + mCgroupV2Root + "/cgroup.subtree_control");
            }
        } catch (Exception ignore) { }
    }

    @Test
    public void testCanActivateMemcgV2Cgroup() throws Exception {
        assumeTrue(mMemcgV2Enabled);

        // The root has to have memcg in the subtree control to activate it in children
        if (!mRootSubtreeStateWasEnabled) {
            CommandResult commandResult = getDevice().executeShellV2Command(
                    "echo +memory > " + mCgroupV2Root + "/cgroup.subtree_control");
            assertTrue("Could not activate memcg under root", commandResult.getExitCode() == 0);
        }

        // Make a new, temporary, randomly-named v2 cgroup in which we will attempt to activate
        // memcg
        CommandResult commandResult = getDevice().executeShellV2Command(
                "mktemp -d -p " + mCgroupV2Root + " " + this.getClass().getSimpleName()
                + ".XXXXXXXXXX");
        assertTrue("Could not make child cgroup", commandResult.getExitCode() == 0);

        mChildCgroup = commandResult.getStdout();


        commandResult = getDevice().executeShellV2Command(
            "grep memory " + mChildCgroup + "/cgroup.controllers");
        assertTrue("Memcg was not activated in child cgroup", commandResult.getExitCode() == 0);


        commandResult = getDevice().executeShellV2Command(
                "echo +memory > " + mChildCgroup + "/cgroup.subtree_control");
        assertTrue("Could not activate memcg for child cgroup subtree",
                commandResult.getExitCode() == 0);
    }


    // Test for fix: mm: memcg: use larger batches for proactive reclaim
    // https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git/commit/?id=287d5fedb377ddc232b216b882723305b27ae31a
    @Test(timeout = 20000)
    public void testProactiveReclaimDoesntTakeForever() throws Exception {
        // Not all kernels have memory.reclaim
        CommandResult commandResult = getDevice().executeShellV2Command(
                "test -f " + mCgroupV2Root + "/memory.reclaim");
        assumeTrue(commandResult.getExitCode() == 0);

        getDevice().executeShellV2Command(
                "echo \"\" > " + mCgroupV2Root + "/memory.reclaim");
        // This is a test for completion within the timeout. The command is likely to "fail" with
        // exit code 1 since we are asking to reclaim more memory than probably exists.
    }

}
