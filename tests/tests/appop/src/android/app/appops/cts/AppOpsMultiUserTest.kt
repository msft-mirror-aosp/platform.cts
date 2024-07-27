package android.app.appops.cts

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.os.SystemClock
import android.os.UserManager
import android.permission.flags.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test

private const val APK_PATH = "/data/local/tmp/cts/appops/"
private const val APK = "AppInstalledOnMultipleUsers.apk"
private const val PKG = "android.app.appops.cts.apponmultipleusers"

private const val APPOPS_UPDATE_WAIT_PERIOD = 2000L

@AppModeFull
class AppOpsMultiUserTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager: PackageManager = context.packageManager
    private val userManager: UserManager = context.getSystemService(UserManager::class.java)!!
    private val appOpsManager: AppOpsManager = context.getSystemService(AppOpsManager::class.java)!!
    private val activityManager: ActivityManager =
        context.getSystemService(ActivityManager::class.java)!!

    private val preExistingUsers: MutableList<UserInfo> = mutableListOf()
    private val newUsers: MutableList<UserInfo> = mutableListOf()

    private fun installApkForAllUsers(apk: String) {
        val result = runCommand("pm install -r --force-queryable $APK_PATH$apk")
        Assert.assertEquals("Success", result.trim())
    }

    @Before
    fun setUp() {
        Assume.assumeTrue(UserManager.supportsMultipleUsers())
        SystemUtil.runWithShellPermissionIdentity {
            preExistingUsers.addAll(userManager.users)
        }
        SystemUtil.runShellCommandOrThrow("pm create-user test-user")
        SystemUtil.runWithShellPermissionIdentity {
            newUsers.addAll(userManager.users)
            newUsers.removeIf { newUser ->
                preExistingUsers.any { filterUser ->
                    newUser.id == filterUser.id
                }
            }
        }
        // Some users aren't in running state in the secondary_user option test.
        // The AppOpsService doesn't receive ACTION_PACKAGE_ADDED for that user.
        SystemUtil.runWithShellPermissionIdentity {
            preExistingUsers.removeAll { userInfo ->
                !activityManager.isUserRunning(userInfo.id)
            }
        }
    }

    @After
    fun tearDown() {
        newUsers.forEach {
            SystemUtil.runShellCommandOrThrow("pm remove-user ${it.id}")
        }

        SystemUtil.runShellCommandOrThrow("cmd appops reset --user ${context.userId}")
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RUNTIME_PERMISSION_APPOPS_MAPPING_ENABLED)
    fun testUninstallDoesntAffectOtherUsers() {
        installApkForAllUsers(APK)

        runWithShellPermissionIdentity {
            preExistingUsers.forEach {
                val uid = packageManager.getPackageUidAsUser(PKG, it.id)
                eventually {
                    appOpsManager.setMode(
                        AppOpsManager.OPSTR_RESERVED_FOR_TESTING,
                        uid,
                        PKG,
                        AppOpsManager.MODE_IGNORED
                    )

                    val mode = appOpsManager.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_RESERVED_FOR_TESTING,
                        uid,
                        PKG
                    )
                    Assert.assertEquals(AppOpsManager.MODE_IGNORED, mode)
                }
            }
        }

        // Uninstall the package from the test users and ensure the other users aren't affected
        newUsers.forEach {
            SystemUtil.runShellCommandOrThrow("pm uninstall --user ${it.id} $PKG")
        }

        runWithShellPermissionIdentity {
            // Due to async nature appops may not update immediately so we wait for any bug to show
            val start = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - start < APPOPS_UPDATE_WAIT_PERIOD) {
                preExistingUsers.forEach {
                    val uid = packageManager.getPackageUidAsUser(PKG, it.id)
                    val mode = appOpsManager.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_RESERVED_FOR_TESTING,
                        uid,
                        PKG
                    )
                    Assert.assertEquals(AppOpsManager.MODE_IGNORED, mode)
                }
            }
        }
    }
}
