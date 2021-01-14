/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telephony.ims.cts;

import android.telephony.ims.ImsException;
import android.telephony.ims.cts.TestImsService.DeviceCapPublishListener;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;
import android.util.Log;

import java.util.concurrent.Executor;

/**
 * A implementation class of RcsCapabilityExchangeImplBase for the TestRcsFeature.
 */
public class TestRcsCapabilityExchangeImpl extends RcsCapabilityExchangeImplBase {

    private static final String LOG_TAG = "TestRcsCapExchangeImpl";

    @FunctionalInterface
    public interface PublishOperation {
        void execute(DeviceCapPublishListener listener, String pidfXml, PublishResponseCallback cb)
                throws ImsException;
    }

    private DeviceCapPublishListener mPublishListener;

    // The operation of publishing capabilities
    private PublishOperation mPublishOperation;

    /**
     * Create a new RcsCapabilityExchangeImplBase instance.
     * @param executor The executor that remote calls from the framework will be called on.
     */
    public TestRcsCapabilityExchangeImpl(Executor executor, DeviceCapPublishListener listener) {
        super(executor);
        mPublishListener = listener;
    }

    public void setPublishOperator(PublishOperation operation) {
        mPublishOperation = operation;
    }

    @Override
    public void publishCapabilities(String pidfXml, PublishResponseCallback cb) {
        try {
            mPublishOperation.execute(mPublishListener, pidfXml, cb);
        } catch (ImsException e) {
            Log.w(LOG_TAG, "publishCapabilities exception: " + e);
        }
    }
}
