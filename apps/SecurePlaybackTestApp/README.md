# Secure Playback Test

This test verifies secure video playback on an Android device using a host-side script and a Playback Analysis Tool (PAT).

## Prerequisites

1.  **patctl and dependencies** Set up patctl according to the README. TODO: b/462781250 - add PAT README here.
After creating the venv, please install [mobly](https://github.com/google/mobly) using pip.
    ```bash
    pip install mobly
    ```

2.  **Environment Setup:** Before running the test, you need to ensure that the test videos are accessible.
From this directory, run:
    ```bash
    source build/envsetup.sh
    ```

## Hardware Setup: Playback Analysis Tool

Proper alignment of the Playback Analysis Tool is crucial for this test.

1.  **Connect the Tool:** Connect the Playback Analysis Tool to your host machine via USB.

2.  **Device setup:** Make sure that the device is connected to the internet and that the brightness is set to the maximum value.

3.  **Screen Alignment:** When the test starts, a pattern of piano keys (bars) will be displayed on the device screen.
    *   Place the Playback Analysis Tool on the device's screen.
    *   **Horizontally align the grooves on the tool with the piano keys (bars) on the screen.**
    *   If the bars are too large to fit the grooves, please enter a scaling factor between 0.0 and 1.0 and re-check.
After everything is scaled and aligned, confirm alignment the host and the test will automatically continue.

4.  **Alignment Indicator Light:** The tool has an indicator light to confirm alignment:
    *   **Green Light:** After you have confirmed alignment on the host, the indicator light will turn green after a second or two to indicate that the tool is aligned correctly.
    *   **Red Light:** Indicates misalignment. If you see a continuous red light, please lift the tool, realign it with the on-screen pattern, and place it back on the screen. Then, restart the test.

## Running the Test

Once the prerequisites and hardware setup are complete, you can run the test from your terminal.

1.  Navigate to the `SecurePlaybackTestApp` directory:
    ```bash
    cd cts/apps/SecurePlaybackTestApp
    ```

2.  Execute the test script:
    ```bash
    python3 secure_playback_test.py -c config.yml
    ```

The script will communicate with the CTS Verifier app on the device, run the secure playback test, and report the results.
