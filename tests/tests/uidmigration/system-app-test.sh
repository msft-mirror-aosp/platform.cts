#!/usr/bin/env bash

# Before running the script:
#
# * lunch with eng variant. For example:
#   lunch flame-trunk_staging-eng
# * Build the full system image and flash your device
#   m; vendor/google/tools/flashall
# * Remount the device to allow system rw.
#   adb remount; adb reboot
# * Build the test APKs
#   m CtsSharedUserMigrationInstallTestApp \
#     CtsSharedUserMigrationInstallTestApp3 \
#     CtsSharedUserMigrationInstallTestApp4 \
#     CtsSharedUserMigrationInstallTestApp5

# Note: This script assumes the device runs with NEW_INSTALL_ONLY strategy.
# The script will have to be updated when switching to BEST_EFFORT strategy.

# APK references
#
# InstallTestApp : APK with sharedUserId, version code = 0
# InstallTestApp3: APK without sharedUserId, version code = 0
# InstallTestApp4: APK with sharedUserId + sharedUserMaxSdkVersion, version code = 0
# InstallTestApp5: APK with sharedUserId + sharedUserMaxSdkVersion, version code = 1000

set -e

TEST_APP_PATH="$ANDROID_TARGET_OUT_TESTCASES/CtsSharedUserMigrationInstallTestApp"
PKGNAME='android.uidmigration.cts.InstallTestApp'
DUMPSYS_CMD="adb shell dumpsys package $PKGNAME | grep -o 'sharedUser=.*' | head -1"

########
# Utils
########

cleanup() {
  adb shell pm uninstall $PKGNAME >/dev/null 2>&1 || true
  adb shell pm uninstall-system-updates $PKGNAME >/dev/null 2>&1 || true
  adb shell stop || true
  adb shell setprop sys.boot_completed 0 || true
  adb shell rm -rf /system/app/InstallTestApp || true
  adb shell start || true
}

wait_boot_complete() {
  while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
    sleep 3
  done
}

adb_stop() {
  adb shell stop
  adb shell setprop sys.boot_completed 0
}

# $1 = APK
ota_install() {
  adb_stop
  adb shell mkdir -p /system/app/InstallTestApp
  adb push "$1" /system/app/InstallTestApp/InstallTestApp.apk
  adb shell start
  timeout 60 bash -c wait_boot_complete
}

# Install APK with sharedUserId as OTA
ota_install_shared() {
  ota_install ${TEST_APP_PATH}/*/*.apk
}

# Install APK without sharedUserId as OTA
ota_install_no_shared() {
  ota_install ${TEST_APP_PATH}3/*/*.apk
}

# Install APK with sharedUserId + sharedUserMaxSdkVersion as OTA
ota_install_shared_max() {
  ota_install ${TEST_APP_PATH}4/*/*.apk
}

# Install APK with sharedUserId + sharedUserMaxSdkVersion as upgrade
install_upgrade() {
  adb install -r ${TEST_APP_PATH}5/*/*.apk
}

uninstall_upgrade() {
  # BUG? For some reason this command always return 1
  adb shell pm uninstall-system-updates $PKGNAME || true
}

# $1 = error msg
assert_in_shared_uid() {
  SHARED_USER="$($DUMPSYS_CMD)"
  if [ -z "$SHARED_USER" ]; then
    echo $1
    exit 1
  fi
}

# $1 = error msg
assert_not_shared_uid() {
  SHARED_USER="$($DUMPSYS_CMD)"
  if [ -n "$SHARED_USER" ]; then
    echo $1
    exit 1
  fi
}

#############
# Test cases
#############

# Installing/Uninstalling upgrades with sharedUserMaxSdkVersion shall not change its UID
test_1() {
  ota_install_shared
  assert_in_shared_uid '! InstallTestApp is not installed properly'
  install_upgrade
  assert_in_shared_uid '! InstallTestApp should remain in shared UID after upgrade'
  uninstall_upgrade
  assert_in_shared_uid '! InstallTestApp should remain in shared UID after upgrade uninstallation'
}

# Removing sharedUserId after an OTA should work
test_2() {
  ota_install_shared
  assert_in_shared_uid  '! InstallTestApp is not installed properly'
  ota_install_no_shared
  assert_not_shared_uid '! InstallTestApp should not be in shared UID after removing sharedUserId'
}

# Adding sharedUserId after an OTA should work
test_3() {
  ota_install_no_shared
  assert_not_shared_uid '! InstallTestApp is not installed properly'
  ota_install_shared
  assert_in_shared_uid  '! InstallTestApp should be in shared UID after adding sharedUserId'
}

# Factory reset with sharedUserMaxSdkVersion shall not be in shared UID
test_4() {
  ota_install_shared_max
  assert_not_shared_uid '! InstallTestApp is not installed properly'
}

# Applying OTA with sharedUserMaxSdkVersion shall not change its UID
test_5() {
  ota_install_shared
  assert_in_shared_uid '! InstallTestApp is not installed properly'
  ota_install_shared_max
  assert_in_shared_uid '! InstallTestApp should remain in shared UID after OTA upgrade with sharedUserMaxSdkVersion'
}

# Complex combination, UID shall never change
# Base -> OTA with sharedUserMaxSdkVersion -> upgrade -> uninstall upgrade
test_6() {
  ota_install_shared
  assert_in_shared_uid '! InstallTestApp is not installed properly'
  ota_install_shared_max
  assert_in_shared_uid '! InstallTestApp should remain in shared UID after OTA upgrade with sharedUserMaxSdkVersion'
  install_upgrade
  assert_in_shared_uid '! InstallTestApp should remain in shared UID after upgrade'
  uninstall_upgrade
  assert_in_shared_uid '! InstallTestApp should remain in shared UID after upgrade uninstallation'
}

#############
# Entrypoint
#############

trap cleanup EXIT
export -f wait_boot_complete

# $1 = test number
run_test() {
  test_${1}
  echo '*****************'
  echo "* Test $1 PASSED *"
  echo '*****************'
  cleanup
  timeout 60 bash -c wait_boot_complete
}

# Make sure system is writable
adb root
adb remount

cleanup
timeout 60 bash -c wait_boot_complete

run_test 1
run_test 2
run_test 3
run_test 4
run_test 5
run_test 6
