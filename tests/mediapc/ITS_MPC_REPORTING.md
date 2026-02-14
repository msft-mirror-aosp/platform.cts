# Camera ITS Media Performance Class (MPC) Reporting Flow

This document describes how Camera ITS metrics are collected and reported for
the Media Performance Class (MPC) grading pipeline.

## Overview

The collection of camera MPC metrics (e.g., JPEG Capture Latency **7.5/H-1-5**)
involves a host-side execution environment and a device-side aggregation and
submission process.

## Process Flow

```sequence-diagram
participant Host (run_all_tests.py)
participant Python Test Script
participant Device (ItsTestActivity)
participant PerformanceClassEvaluator

Host (run_all_tests.py)->Python Test Script: executes
Python Test Script->Host (run_all_tests.py): print metric to stdout
Note over Host (run_all_tests.py): regex extraction
Host (run_all_tests.py)->Device (ItsTestActivity): broadcast ACTION_ITS_RESULT
Device (ItsTestActivity)->PerformanceClassEvaluator: update metrics
Device (ItsTestActivity)->PerformanceClassEvaluator: getRequirements()
Note over Device (ItsTestActivity): For each requirement: isReady?
Device (ItsTestActivity)->PerformanceClassEvaluator: submitAndVerify(ready)
Device (ItsTestActivity)->CtsCameraItsTestCases.reportlog.json: log results
```

1.  **Execution (Host)**:

    *   The ITS host script `run_all_tests.py` executes Mobly-based Python
        tests.
    *   Specific tests (e.g., `test_jpeg_capture_perf_class.py`) measure latency
        and print a formatted metric string to `stdout` (e.g.,
        `1080p_jpeg_capture_time_ms:750.5`).
    *   **Crucial Step**: Metrics MUST be printed **before** any assertions. If
        a script raises an `AssertionError` before printing, the data is lost.

2.  **Aggregation (Host)**:

    *   `run_all_tests.py` captures the `stdout` of each test.
    *   It extracts metric values using predefined regex patterns.
    *   Once a camera's tests for a scene are complete, the host broadcasts an
        `ACTION_ITS_RESULT` intent to the device. This intent contains a JSON
        object mapping scenes to results and MPC metrics.

3.  **Parsing (Device - ItsTestActivity)**:

    *   `ItsTestActivity` (in `CtsVerifier`) receives the `ACTION_ITS_RESULT`
        broadcast.
    *   It updates a `PerformanceClassEvaluator` (PCE) object with the received
        metrics.
    *   Each requirement (e.g., `CameraCaptureLatencyRequirement`) tracks
        measurements for both the primary rear and primary front cameras.

4.  **Submission (Device)**:

    *   `ItsTestActivity` checks for requirement readiness after every
        broadcast.
    *   A `Requirement` is considered `isReady()` when measurements for all its
        required components (e.g., both front and rear camera latencies) have
        been set.
    *   Ready requirements are submitted via
        `mPce.submitAndVerify(readyToSubmit)`.
    *   Submission writes the data to a report log (typically
        `CtsCameraItsTestCases.reportlog.json`), which is then ingested by the
        grading pipeline.

## Key Resilience Features

*   **Granular Submission**: Requirements are submitted independently as soon as
    they are ready. A single skipped or failing test (e.g., Ultra HDR not
    supported on older devices) will not block the reporting of other successful
    metrics like JPEG latency.
*   **Missing Camera Handling**: If a device lacks a primary front or rear
    camera, `ItsTestActivity` pre-sets those specific measurements to failing
    default values (e.g., `Float.MAX_VALUE` for latency, `false` for Ultra HDR)
    during initialization. This ensures the requirement can reach the
    `isReady()` state and be reported, while correctly reflecting that the
    device does not meet the MPC specification for both primary cameras.
*   **Rerun Support**: If a test is rerun within the same session, new metrics
    will update the existing requirements and trigger a fresh submission of the
    updated data.
