/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.graphics.gpuprofiling.cts

import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.`is` as Is
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.hamcrest.Matchers.not
import org.junit.rules.ErrorCollector

fun checkRenderStagesNotEmpty(
    errorCollector: ErrorCollector,
    renderStages: List<RenderStageEvent>
) {
    errorCollector.checkThat(
        "Trace does not contain GPU render stages.",
        renderStages,
        not(empty())
    )
}

fun checkRenderStagesValidity(
    errorCollector: ErrorCollector,
    renderStages: List<RenderStageEvent>
) {
    val renderStagesSummary = StringBuilder()

    for (renderStageEvent in renderStages.stream()
        .map(RenderStageEvent::value)
        .toList()) {
        if (!renderStageEvent.hasEventId()) {
            renderStagesSummary
                .append(" render stage event without event_id: ")
                .append(renderStageEvent.toString())
                .append(";")
            continue
        }

        if (@Suppress("DEPRECATION") renderStageEvent.hasHwQueueId()) {
            renderStagesSummary
                .append(" event_id ")
                .append(renderStageEvent.eventId)
                .append(" uses deprecated `hw_queue_id`;")
            continue
        }

        if (@Suppress("DEPRECATION") renderStageEvent.hasStageId()) {
            renderStagesSummary
                .append(" event_id ")
                .append(renderStageEvent.eventId)
                .append(" uses deprecated `stage_id`;")
            continue
        }

        if (!renderStageEvent.hasHwQueueIid()) {
            renderStagesSummary
                .append(" event_id ")
                .append(renderStageEvent.eventId)
                .append(" is missing `hw_queue_iid`;")
        }

        if (!renderStageEvent.hasStageIid()) {
            renderStagesSummary
                .append(" event_id ")
                .append(renderStageEvent.eventId)
                .append(" is missing `stage_iid`;")
        }

        if (!renderStageEvent.hasContext()) {
            renderStagesSummary
                .append(" event_id ")
                .append(renderStageEvent.eventId)
                .append(" is missing `context`;")
        }
    }
    errorCollector.checkThat(
        "Some of the reported GPU render stages are not valid.",
        renderStagesSummary.toString(),
        Is("")
    )
}

fun checkQueueSubmitsNotEmpty(
    errorCollector: ErrorCollector,
    queueSubmits: List<QueueSubmitEvent>
) {
    errorCollector.checkThat(
        "QueueSubmit events are missing.",
        queueSubmits,
        not(empty())
    )
}

fun checkQueueSubmitsStrictlyMonotonicallyIncreasing(
    errorCollector: ErrorCollector,
    queueSubmits: List<QueueSubmitEvent>
) {
    val queueSubmissionIds: MutableList<Int?> =
        queueSubmits.stream().map(QueueSubmitEvent::submissionId).toList()
    errorCollector.checkThat(
        "The queue submit IDs are not strictly monotonically increasing.",
        queueSubmissionIds.stream().sorted().distinct().toList(),
        Is(queueSubmissionIds)
    )
}

fun checkQueueSubmitsMatchAppLogs(
    errorCollector: ErrorCollector,
    queueSubmits: List<QueueSubmitEvent>,
    appQueueSubmits: List<AppQueueSubmitEvent>
) {
    var syncedQueueSubmits = queueSubmits
    var syncedAppQueueSubmits = appQueueSubmits

    // First and last event might not have a match, drop them if needed.
    if (syncedQueueSubmits.first().timestamp < appQueueSubmits.first().timestampBefore) {
        syncedQueueSubmits = syncedQueueSubmits.drop(1)
    } else if (syncedAppQueueSubmits.first().timestampAfter < queueSubmits.first().timestamp) {
        syncedAppQueueSubmits = syncedAppQueueSubmits.drop(1)
    }
    if (syncedQueueSubmits.last().timestamp > appQueueSubmits.last().timestampAfter) {
        syncedQueueSubmits = syncedQueueSubmits.dropLast(1)
    } else if (syncedAppQueueSubmits.last().timestampBefore > queueSubmits.last().timestamp) {
        syncedAppQueueSubmits = syncedAppQueueSubmits.dropLast(1)
    }

    errorCollector.checkThat(
        "The number of reported VkQueueSubmits does not match the app's Atrace logs.",
        syncedQueueSubmits.size,
        Is(syncedAppQueueSubmits.size)
    )

    val matchedPairs = syncedQueueSubmits.zip(syncedAppQueueSubmits)

    val misalignedSubmissionEventIds = matchedPairs
        .filter { (queueSubmit, appQueueSubmit) ->
            queueSubmit.timestamp < appQueueSubmit.timestampBefore ||
                    queueSubmit.durationNs >
                    appQueueSubmit.timestampAfter - appQueueSubmit.timestampBefore
        }

    errorCollector.checkThat(
        "Reported VkQueueSubmit event timing does not match app's Atrace logs.",
        misalignedSubmissionEventIds,
        Is(empty())
    )
}

fun checkRenderStagesMatchQueueSubmits(
    errorCollector: ErrorCollector,
    renderStagesData: RenderStagesData
) {
    data class SubmissionData(
        val queueSubmitTimestamp: Long?,
        val firstRenderStageTimestamp: Long?
    )

    val submissionIdToData = mutableMapOf<Int, SubmissionData>()

    for (queueSubmit in renderStagesData.queueSubmits) {
        submissionIdToData[queueSubmit.submissionId] = SubmissionData(
            queueSubmitTimestamp = queueSubmit.timestamp,
            firstRenderStageTimestamp = null
        )
    }

    for (renderStage in renderStagesData.renderStages) {
        submissionIdToData.compute(renderStage.value.submissionId) { _, existingData ->
            val timestamp = renderStage.timestamp
            if (existingData == null) {
                SubmissionData(
                    queueSubmitTimestamp = null,
                    firstRenderStageTimestamp = timestamp
                )
            } else {
                val currentTimestamp = existingData.firstRenderStageTimestamp
                existingData.copy(
                    firstRenderStageTimestamp = if (currentTimestamp != null) {
                        minOf(currentTimestamp, timestamp)
                    } else {
                        timestamp
                    }
                )
            }
        }
    }

    val submissionEvents = submissionIdToData.toSortedMap()
        .entries
        .drop(1)
        .dropLast(1)
        .dropWhile { it.value.queueSubmitTimestamp == null }
        .dropLastWhile { it.value.firstRenderStageTimestamp == null }
        .associate { it.key to it.value }

    errorCollector.checkThat(
        "Too many invalid queue submit/render stage events were detected at the trace boundaries.",
        submissionIdToData.size - submissionEvents.size,
        lessThanOrEqualTo(10)
    )

    errorCollector.checkThat(
        "No matching queue submits and render stages found.",
        submissionEvents.entries.stream().toList(),
        not(empty())
    )

    data class MatchingSubmissionData(
        val queueSubmitTimestamp: Long,
        val firstRenderStageTimestamp: Long
    )

    val matchingSubmissionEvents = submissionEvents.mapNotNull { (id, data) ->
        if (data.queueSubmitTimestamp != null && data.firstRenderStageTimestamp != null) {
            id to MatchingSubmissionData(
                data.queueSubmitTimestamp,
                data.firstRenderStageTimestamp
            )
        } else {
            null
        }
    }.toMap().toSortedMap()

    errorCollector.checkThat(
        "Found mismatched queue submits and/or render stages.",
        matchingSubmissionEvents.size,
        Is(submissionEvents.size)
    )

    val mistimedSubmissionEventIds = matchingSubmissionEvents.filter { (_, data) ->
        data.firstRenderStageTimestamp < data.queueSubmitTimestamp
    }.keys.toList()

    errorCollector.checkThat(
        "Render stages reported before their VkQueueSubmit events for submission_ids",
        mistimedSubmissionEventIds,
        Is(empty())
    )
}
