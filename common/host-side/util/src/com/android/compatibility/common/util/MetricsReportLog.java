/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.compatibility.common.util;

import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A {@link ReportLog} that can be used with the in memory metrics store used for host side metrics.
 */
public final class MetricsReportLog extends ReportLog {
    private final String mDeviceSerial;
    private final String mAbi;
    private final String mClassMethodName;

    // TODO(mishragaurav): Remove default names and constructor after fixing b/27950009.
    private static final String DEFAULT_REPORT_LOG_NAME = "DefaultHostTestMetrics";
    private static final String DEFAULT_STREAM_NAME = "DefaultStream";
    // Temporary folder must match the temp-dir value configured in ReportLogCollector target
    // preparer in cts/tools/cts-tradefed/res/config/cts-oreconditions.xml
    private static final String TEMPORARY_REPORT_FOLDER = "temp-report-logs/";
    private ReportLogHostInfoStore store;

    /**
     * @param deviceSerial serial number of the device
     * @param abi abi the test was run on
     * @param classMethodName class name and method name of the test in class#method format.
     *        Note that ReportLog.getClassMethodNames() provide this.
     */
    public MetricsReportLog(String deviceSerial, String abi, String classMethodName) {
        this(deviceSerial, abi, classMethodName, DEFAULT_REPORT_LOG_NAME, DEFAULT_STREAM_NAME);
    }

    /**
     * @param deviceSerial serial number of the device
     * @param abi abi the test was run on
     * @param classMethodName class name and method name of the test in class#method format.
     *        Note that ReportLog.getClassMethodNames() provide this.
     * @param reportLogName the name of the report log file. Metrics will be written out to this.
     */
    public MetricsReportLog(String deviceSerial, String abi, String classMethodName,
            String reportLogName) {
        this(deviceSerial, abi, classMethodName, reportLogName, DEFAULT_STREAM_NAME);
    }

    /**
     * @param deviceSerial serial number of the device
     * @param abi abi the test was run on
     * @param classMethodName class name and method name of the test in class#method format.
     *        Note that ReportLog.getClassMethodNames() provide this.
     * @param reportLogName the name of the report log file. Metrics will be written out to this.
     * @param streamName the key for the JSON object of the set of metrics to be logged.
     */
    public MetricsReportLog(String deviceSerial, String abi, String classMethodName,
            String reportLogName, String streamName) {
        super(reportLogName, streamName);
        mDeviceSerial = deviceSerial;
        mAbi = abi;
        mClassMethodName = classMethodName;
        try {
            final File dir = FileUtil.createNamedTempDir(TEMPORARY_REPORT_FOLDER);
            File jsonFile = new File(dir, mReportLogName + ".reportlog.json");
            store = new ReportLogHostInfoStore(jsonFile, mStreamName);
            store.open();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a double metric to the report.
     */
    @Override
    public void addValue(String source, String message, double value, ResultType type,
            ResultUnit unit) {
        super.addValue(source, message, value, type, unit);
        try {
            store.addResult(message, value);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a double metric to the report.
     */
    @Override
    public void addValue(String message, double value, ResultType type, ResultUnit unit) {
        super.addValue(message, value, type, unit);
        try {
            store.addResult(message, value);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Adds a double array of metrics to the report.
     */
    @Override
    public void addValues(String source, String message, double[] values, ResultType type,
                          ResultUnit unit) {
        super.addValues(source, message, values, type, unit);
        try {
            store.addArrayResult(message, values);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a double array of metrics to the report.
     */
    @Override
    public void addValues(String message, double[] values, ResultType type, ResultUnit unit) {
        super.addValues(message, values, type, unit);
        try {
            store.addArrayResult(message, values);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds an int metric to the report.
     */
    @Override
    public void addValue(String message, int value, ResultType type, ResultUnit unit) {
        try {
            store.addResult(message, value);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a long metric to the report.
     */
    @Override
    public void addValue(String message, long value, ResultType type, ResultUnit unit) {
        try {
            store.addResult(message, value);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a float metric to the report.
     */
    @Override
    public void addValue(String message, float value, ResultType type, ResultUnit unit) {
        try {
            store.addResult(message, value);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a boolean metric to the report.
     */
    @Override
    public void addValue(String message, boolean value, ResultType type, ResultUnit unit) {
        try {
            store.addResult(message, value);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a String metric to the report.
     */
    @Override
    public void addValue(String message, String value, ResultType type, ResultUnit unit) {
        try {
            store.addResult(message, value);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds an int array of metrics to the report.
     */
    @Override
    public void addValues(String message, int[] values, ResultType type, ResultUnit unit) {
        try {
            store.addArrayResult(message, values);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a long array of metrics to the report.
     */
    @Override
    public void addValues(String message, long[] values, ResultType type, ResultUnit unit) {
        try {
            store.addArrayResult(message, values);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a float array of metrics to the report.
     */
    @Override
    public void addValues(String message, float[] values, ResultType type, ResultUnit unit) {
        try {
            store.addArrayResult(message, values);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a boolean array of metrics to the report.
     */
    @Override
    public void addValues(String message, boolean[] values, ResultType type, ResultUnit unit) {
        try {
            store.addArrayResult(message, values);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a String List of metrics to the report.
     */
    @Override
    public void addValues(String message, List<String> values, ResultType type, ResultUnit unit) {
        try {
            store.addListResult(message, values);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the summary double metric of the report.
     *
     * NOTE: messages over {@value Metric#MAX_MESSAGE_LENGTH} chars will be trimmed.
     */
    @Override
    public void setSummary(String message, double value, ResultType type, ResultUnit unit) {
        super.setSummary(message, value, type, unit);
        try {
            store.addResult(message, value);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes report file and submits report.
     */
    public void submit() {
        try {
            store.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        MetricsStore.storeResult(mDeviceSerial, mAbi, mClassMethodName, this);
    }
}
