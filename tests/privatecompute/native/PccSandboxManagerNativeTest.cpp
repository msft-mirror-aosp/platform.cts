// cts/tests/privatecompute/native/PccSandboxManagerNativeTest.cpp
#include <android/app/privatecompute/IPccSandboxManagerNative.h>
#include <binder/IServiceManager.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <gtest/gtest.h>
#include <utils/String16.h>

namespace {
const char kPccSandboxManagerNativeService[] = "pcc_sandbox_native";
}  // namespace

using android::defaultServiceManager;
using android::interface_cast;
using android::IServiceManager;
using android::Parcel;
using android::sp;
using android::String16;
using android::app::privatecompute::IPccSandboxManagerNative;

class PccSandboxManagerNativeTest : public ::testing::Test {
protected:
    sp<IPccSandboxManagerNative> mService;

    void SetUp() override {
        sp<IServiceManager> sm = defaultServiceManager();
        ASSERT_NE(sm, nullptr);
        sp<android::IBinder> binder = sm->getService(String16(kPccSandboxManagerNativeService));
        ASSERT_NE(binder, nullptr);
        mService = interface_cast<IPccSandboxManagerNative>(binder);
        ASSERT_NE(mService, nullptr);
    }
};

// @ApiTest = android.app.privatecompute.IPccSandboxManagerNative#writeToAuditLog
TEST_F(PccSandboxManagerNativeTest, CanCallWriteToAuditLog) {
    android::os::PersistableBundle bundle;
    bundle.putString(String16("interface_name"), String16("example_interface"));
    bundle.putString(String16("method_name"), String16("example_method"));

    android::binder::Status status = mService->writeToAuditLog(bundle);

    ASSERT_TRUE(status.isOk());
}