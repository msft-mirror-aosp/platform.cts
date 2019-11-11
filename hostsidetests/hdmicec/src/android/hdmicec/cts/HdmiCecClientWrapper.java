/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.hdmicec.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Rule;
import org.junit.rules.ExternalResource;

/** Class that helps communicate with the cec-client */
public final class HdmiCecClientWrapper extends ExternalResource {

    private static final String CEC_CONSOLE_READY = "waiting for input";
    private static final int MILLISECONDS_TO_READY = 5000;
    private static final int DEFAULT_TIMEOUT = 20000;
    private static final String HDMI_CEC_FEATURE = "feature:android.hardware.hdmi.cec";
    private static final int HEXADECIMAL_RADIX = 16;

    private Process mCecClient;
    private BufferedWriter mOutputConsole;
    private BufferedReader mInputConsole;
    private boolean mCecClientInitialised = false;

    private CecDevice targetDevice;
    private BaseHostJUnit4Test testObject;

    public HdmiCecClientWrapper(CecDevice targetDevice, BaseHostJUnit4Test testObject) {
        this.targetDevice = targetDevice;
        this.testObject = testObject;
    }

    @Override
    protected void before() throws Throwable {
        ITestDevice testDevice;
        testDevice = testObject.getDevice();
        assertNotNull("Device not set", testDevice);

        assumeTrue(isHdmiCecFeatureSupported(testDevice));

        this.init();
    };

    @Override
    protected void after() {
        this.killCecProcess();
    };

    /**
     * Checks if the HDMI CEC feature is running on the device. Call this function before running
     * any HDMI CEC tests.
     * This could throw a DeviceNotAvailableException.
     */
    private static boolean isHdmiCecFeatureSupported(ITestDevice device) throws Exception {
        return device.hasFeature(HDMI_CEC_FEATURE);
    }

    /** Initialise the client */
    private void init() throws Exception {
        boolean gotExpectedOut = false;
        List<String> commands = new ArrayList();
        int seconds = 0;

        commands.add("cec-client");
        commands.add("-p");
        commands.add("2");
        mCecClient = RunUtil.getDefault().runCmdInBackground(commands);
        mInputConsole = new BufferedReader(new InputStreamReader(mCecClient.getInputStream()));

        /* Wait for the client to become ready */
        mCecClientInitialised = true;
        if (checkConsoleOutput(CecClientMessage.CLIENT_CONSOLE_READY + "", MILLISECONDS_TO_READY)) {
            mOutputConsole = new BufferedWriter(
                                new OutputStreamWriter(mCecClient.getOutputStream()));
            return;
        }

        mCecClientInitialised = false;

        throw (new Exception("Could not initialise cec-client process"));
    }

    private void checkCecClient() throws Exception {
        if (!mCecClientInitialised) {
            throw new Exception("cec-client not initialised!");
        }
        if (!mCecClient.isAlive()) {
            throw new Exception("cec-client not running!");
        }
    }

    /**
     * Sends a CEC message with source marked as broadcast to the device passed in the constructor
     * through the output console of the cec-communication channel.
     */
    public void sendCecMessage(CecMessage message) throws Exception {
        sendCecMessage(CecDevice.BROADCAST, targetDevice, message, "");
    }

    /**
     * Sends a CEC message from source device to the device passed in the constructor through the
     * output console of the cec-communication channel.
     */
    public void sendCecMessage(CecDevice source, CecMessage message) throws Exception {
        sendCecMessage(source, targetDevice, message, "");
    }

    /**
     * Sends a CEC message from source device to a destination device through the output console of
     * the cec-communication channel.
     */
    public void sendCecMessage(CecDevice source, CecDevice destination,
        CecMessage message) throws Exception {
        sendCecMessage(source, destination, message, "");
    }

    /**
     * Sends a CEC message from source device to a destination device through the output console of
     * the cec-communication channel with the appended params.
     */
    public void sendCecMessage(CecDevice source, CecDevice destination,
        CecMessage message, String params) throws Exception {
        checkCecClient();
        mOutputConsole.write("tx " + source + destination + ":" + message + params);
        mOutputConsole.flush();
    }

    /** Sends a message to the output console of the cec-client */
    public void sendConsoleMessage(String message) throws Exception {
        checkCecClient();
        CLog.v("Sending message:: " + message);
        mOutputConsole.write(message);
        mOutputConsole.flush();
    }

    /** Check for any string on the input console of the cec-client, uses default timeout */
    public boolean checkConsoleOutput(String expectedMessage) throws Exception {
        return checkConsoleOutput(expectedMessage, DEFAULT_TIMEOUT);
    }

    /** Check for any string on the input console of the cec-client */
    public boolean checkConsoleOutput(String expectedMessage,
                                       long timeoutMillis) throws Exception {
        checkCecClient();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;

        while ((endTime - startTime <= timeoutMillis)) {
            if (mInputConsole.ready()) {
                String line = mInputConsole.readLine();
                if (line.contains(expectedMessage)) {
                    CLog.v("Found " + expectedMessage + " in " + line);
                    return true;
                }
            }
            endTime = System.currentTimeMillis();
        }
        return false;
    }

    /**
     * Looks for the CEC expectedMessage broadcast on the cec-client communication channel and
     * returns the first line that contains that message within default timeout. If the CEC message
     * is not found within the timeout, an exception is thrown.
     */
    public String checkExpectedOutput(CecMessage expectedMessage) throws Exception {
        return checkExpectedOutput(CecDevice.BROADCAST, expectedMessage, DEFAULT_TIMEOUT);
    }

    /**
     * Looks for the CEC expectedMessage sent to CEC device toDevice on the cec-client
     * communication channel and returns the first line that contains that message within
     * default timeout. If the CEC message is not found within the timeout, an exception is thrown.
     */
    public String checkExpectedOutput(CecDevice toDevice,
                                      CecMessage expectedMessage) throws Exception {
        return checkExpectedOutput(toDevice, expectedMessage, DEFAULT_TIMEOUT);
    }

    /**
     * Looks for the CEC expectedMessage broadcast on the cec-client communication channel and
     * returns the first line that contains that message within timeoutMillis. If the CEC message
     * is not found within the timeout, an exception is thrown.
     */
    public String checkExpectedOutput(CecMessage expectedMessage,
                                      long timeoutMillis) throws Exception {
        return checkExpectedOutput(CecDevice.BROADCAST, expectedMessage, timeoutMillis);
    }

    /**
     * Looks for the CEC expectedMessage sent to CEC device toDevice on the cec-client
     * communication channel and returns the first line that contains that message within
     * timeoutMillis. If the CEC message is not found within the timeout, an exception is thrown.
     */
    public String checkExpectedOutput(CecDevice toDevice, CecMessage expectedMessage,
                                       long timeoutMillis) throws Exception {
        checkCecClient();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;
        Pattern pattern = Pattern.compile("(.*>>)(.*?)" +
                                          "(" + targetDevice + toDevice + "):" +
                                          "(" + expectedMessage + ")(.*)",
                                          Pattern.CASE_INSENSITIVE);

        while ((endTime - startTime <= timeoutMillis)) {
            if (mInputConsole.ready()) {
                String line = mInputConsole.readLine();
                if (pattern.matcher(line).matches()) {
                    CLog.v("Found " + expectedMessage.name() + " in " + line);
                    return line;
                }
            }
            endTime = System.currentTimeMillis();
        }
        throw new Exception("Could not find message " + expectedMessage.name());
    }

    /** Gets the hexadecimal ASCII character values of a string. */
    public String getHexAsciiString(String string) {
        String asciiString = "";
        byte[] ascii = string.trim().getBytes();

        for (byte b : ascii) {
            asciiString.concat(Integer.toHexString(b));
        }

        return asciiString;
    }

    /** Prepares a CEC message. */
    public String prepareMessage(CecDevice source, CecDevice destination, CecMessage message,
                                 int params) {
        String cecMessage = "" + source + destination + ":" + message;

        String paramsString = Integer.toHexString(params);
        int position = 0;
        int endPosition = 2;

        do {
            cecMessage.concat(":" + paramsString.substring(position, endPosition));
            position = endPosition;
            endPosition += 2;
        } while (endPosition <= paramsString.length());

        return cecMessage;
    }

    /**
     * Gets the params from a CEC message.
     */
    public int getParamsFromMessage(String message) {
        return Integer.parseInt(getNibbles(message).substring(4), HEXADECIMAL_RADIX);
    }

    /**
     * Gets the first 'numNibbles' number of param nibbles from a CEC message.
     */
    public int getParamsFromMessage(String message, int numNibbles) {
        int paramStart = 4;
        int end = numNibbles + paramStart;
        return Integer.parseInt(getNibbles(message).substring(paramStart, end), HEXADECIMAL_RADIX);
    }

    /**
     * From the params of a CEC message, gets the nibbles from position start to position end.
     * The start and end are relative to the beginning of the params. For example, in the following
     * message - 4F:82:10:00:04, getParamsFromMessage(message, 0, 4) will return 0x1000 and
     * getParamsFromMessage(message, 4, 6) will return 0x04.
     */
    public int getParamsFromMessage(String message, int start, int end) {
        return Integer.parseInt(getNibbles(message).substring(4).substring(start, end), HEXADECIMAL_RADIX);
    }

    /**
     * Gets the source logical address from a CEC message.
     */
    public CecDevice getSourceFromMessage(String message) {
        String param = getNibbles(message).substring(0, 1);
        return CecDevice.getDevice(Integer.parseInt(param, HEXADECIMAL_RADIX));
    }


    /**
     * Gets the destination logical address from a CEC message.
     */
    public CecDevice getDestinationFromMessage(String message) {
        String param = getNibbles(message).substring(1, 2);
        return CecDevice.getDevice(Integer.parseInt(param, HEXADECIMAL_RADIX));
    }

    private String getNibbles(String message) {
        final String tag1 = "group1";
        final String tag2 = "group2";
        String paramsPattern = "(?:.*[>>|<<].*?)" +
                               "(?<" + tag1 + ">[\\p{XDigit}{2}:]+)" +
                               "(?<" + tag2 + ">\\p{XDigit}{2})" +
                               "(?:.*?)";
        String nibbles = "";

        Pattern p = Pattern.compile(paramsPattern);
        Matcher m = p.matcher(message);
        if (m.matches()) {
            nibbles = m.group(tag1).replace(":", "") + m.group(tag2);
        }
        return nibbles;
    }

    /**
     * Kills the cec-client process that was created in init().
     */
    private void killCecProcess() {
        try {
            checkCecClient();
            sendConsoleMessage(CecClientMessage.QUIT_CLIENT.toString());
            mOutputConsole.close();
            mInputConsole.close();
            mCecClientInitialised = false;
        } catch (Exception e) {
            /* If cec-client is not running, do not throw an exception, just return. */
            return;
        }
    }
}
