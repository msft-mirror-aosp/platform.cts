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

package android.logcat.cts;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import com.android.logcat.proto.LogcatEntryProto;
import com.android.tradefed.device.CollectingByteOutputReceiver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;

import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Test adb logcat command. */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class LogcatHostTest implements IDeviceTest {
    private ITestDevice mDevice;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Test
    public void testProtoFormat() throws Exception {
        ITestDevice device = getDevice();
        CollectingByteOutputReceiver receiver = new CollectingByteOutputReceiver();

        // Ensure there is at least one log message.
        device.executeShellCommand("log -p e -t LogcatHostTest TestLogcat");
        device.executeShellCommand("logcat --proto -d", receiver);

        byte[] bytes = receiver.getOutput();
        try {
            List<LogcatEntryProto> entries = parseLogcatProtoStream(bytes);
            Assert.assertFalse(entries.isEmpty());
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable e) {
            fail("Failed to parse proto entries from logcat --proto command.", e);
        }
    }

    private static List<LogcatEntryProto> parseLogcatProtoStream(byte[] bytes)
            throws InvalidProtocolBufferException {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        ArrayList<LogcatEntryProto> entries = new ArrayList<>();
        buf.order(LITTLE_ENDIAN);
        while (buf.remaining() > 0) {
            long length = buf.getLong();
            byte[] entryBytes = new byte[(int) length];
            buf.get(entryBytes);
            LogcatEntryProto entry = LogcatEntryProto.parseFrom(entryBytes);
            entries.add(entry);
        }
        return entries;
    }

    @SuppressWarnings("SameParameterValue")
    private static void fail(String message, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();
        Assert.fail(message + "\n" + e.getClass().getName() + "\n" + stackTrace);
    }
}
