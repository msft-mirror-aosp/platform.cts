# Biometric CTS Test Helper: Wallet App

This folder contains the helper application (`CtsBiometricServiceWalletTestApp`) used by the Biometric CTS tests to verify the behavior of the `getBiometricSensorStrengths` method in [**BiometricManager**](https://googleplex-android.git.corp.google.com/platform/frameworks/base/+/main/core/java/android/hardware/biometrics/BiometricManager.java) for applications holding the `android.app.role.WALLET` role.

## Purpose

The `getBiometricSensorStrengths` API allows specific Android roles to retrieve biometric sensor security strengths while in the foreground. This dedicated helper app is necessary because an app must possess basic wallet/payment features to be eligible for the `android.app.role.WALLET` role; without these features, the role cannot be granted, and the API's security constraints cannot be tested.

The primary goals of this app are to:
1.  **Qualify for the Wallet Role**: Implement mandatory payment components (such as `HostApduService`) to satisfy the requirements for role assignment.
2.  **Verify Access Control**: Provide distinct execution contexts (foreground vs. background, and various permission states) to validate that the system correctly enforces the API's security restrictions.

## Components

- [**TestHelperActivity.java**](./src/android/server/biometrics/wallet/TestHelperActivity.java): A foreground activity that triggers the API call to verify successful access while in the foreground.
- [**TestHelperBroadcastReceiver.java**](./src/android/server/biometrics/wallet/TestHelperBroadcastReceiver.java): Specifically intended for verifying background cases (it triggers the API call from a background context).
- [**TestHelperHostApduService.java**](./src/android/server/biometrics/wallet/TestHelperHostApduService.java): Implements a `HostApduService` using [**hce_aids.xml**](./res/xml/hce_aids.xml) to qualify the app as a wallet/payment application.
- [**AndroidManifest.xml**](./AndroidManifest.xml): The primary manifest for the wallet role holder app.
- **Permission Variants**: Auxiliary manifests [**AndroidManifest_NoApiPermission.xml**](./AndroidManifest_NoApiPermission.xml) and [**AndroidManifest_NoBioPermission.xml**](./AndroidManifest_NoBioPermission.xml) are used to test security rejection when required permissions are missing.

## Test Integration

The main test class [**BiometricSimpleTests.java**](../../src/android/server/biometrics/BiometricSimpleTests.java) interacts with this app to test various security scenarios:

- **Successful Retrieval**: Launching the helper activity after granting the wallet role to verify that biometric modalities and their corresponding strengths are correctly returned (see `testGetBiometricSensorStrengths_allowedWalletRoleHolder`).
- **Role Verification**: Launching the activity without granting the wallet role to verify that access is denied with a `SecurityException` (see `testGetBiometricSensorStrengths_nonRoleHolder_throwsSecurityException`).
- **API Permission Rejection**: Using the variant app that lacks the `ACCESS_BIOMETRIC_SENSOR_STRENGTHS` permission to verify rejection (see `testGetBiometricSensorStrengths_withoutApiPermission_throwsSecurityException`).
- **Biometric Permission Rejection**: Using the variant app that lacks the `USE_BIOMETRIC` permission to verify rejection (see `testGetBiometricSensorStrengths_withoutBioPermission_throwsSecurityException`).
- **Background Rejection**: Triggering the background receiver and verifying that the call results in a `SecurityException` (see `testGetBiometricSensorStrengths_backgroundCaller_throwsSecurityException`).
