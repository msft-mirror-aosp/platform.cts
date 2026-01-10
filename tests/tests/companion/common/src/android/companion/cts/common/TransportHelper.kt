/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.companion.cts.common

import android.companion.CompanionDeviceManager.MESSAGE_REQUEST_METADATA_UPDATE
import android.os.PersistableBundle
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A CDM message wrapper.
 */
data class CdmMessage(val type: Int, val sequence: Int, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CdmMessage

        if (type != other.type) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + payload.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "CdmMessage(type=0x${Integer.toHexString(
            type
        )}, payload=${Base64.encodeToString(payload, Base64.DEFAULT)})"
    }

    fun success(): CdmMessage {
        return CdmMessage(MESSAGE_RESPONSE_SUCCESS, sequence, byteArrayOf())
    }

    fun failure(): CdmMessage {
        return CdmMessage(MESSAGE_RESPONSE_FAILURE, sequence, byteArrayOf())
    }

    companion object {
        const val MESSAGE_RESPONSE_SUCCESS = 0x33838567
        const val MESSAGE_RESPONSE_FAILURE = 0x33706573

        fun forMetadataUpdate(metadata: PersistableBundle): CdmMessage {
            val bytes = ByteArrayOutputStream()
            metadata.writeToStream(bytes)
            return CdmMessage(MESSAGE_REQUEST_METADATA_UPDATE, -1, bytes.toByteArray())
        }
    }
}

/**
 * A fake input stream that feeds CDM formatted messages from a queue.
 */
class MessageFeeder : InputStream() {

    private val messageQueue = LinkedBlockingQueue<ByteArray>()
    private var currentMessage: ByteBuffer? = null

    fun feedMessage(message: CdmMessage) {
        val message = ByteBuffer.allocate(12 + message.payload.size)
            .putInt(message.type)
            .putInt(message.sequence)
            .putInt(message.payload.size)
            .put(message.payload)
            .array()

        messageQueue.offer(message)
    }

    fun feedMessage(messageType: Int, payload: ByteArray) {
        feedMessage(CdmMessage(messageType, -1, payload))
    }

    fun feedMessages(payloads: Collection<CdmMessage>) {
        payloads.forEach { feedMessage(it) }
    }

    fun signalEndOfStream() {
        messageQueue.offer(byteArrayOf())
    }

    private fun loadMessage(): Boolean {
        if (currentMessage != null && currentMessage!!.hasRemaining()) {
            return true
        }

        val nextMessageBytes = messageQueue.poll(100, TimeUnit.MILLISECONDS)
        if (nextMessageBytes == null || nextMessageBytes.isEmpty()) {
            currentMessage = null
            return false
        } else {
            currentMessage = ByteBuffer.wrap(nextMessageBytes)
            return true
        }
    }

    @Synchronized
    override fun read(): Int {
        if (!loadMessage()) {
            return -1
        }

        return currentMessage!!.get().toInt() and 0xFF
    }

    @Synchronized
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0

        if (!loadMessage()) {
            return -1
        }

        val bytesToRead = minOf(len, currentMessage!!.remaining())
        currentMessage!!.get(b, off, bytesToRead)
        return bytesToRead
    }

    override fun available(): Int {
        return currentMessage?.remaining() ?: 0
    }
}

/**
 * A fake output stream that demuxes CDM formatted messages into a queue.
 */
class MessageDemuxer(
    private val messageCallback: (CdmMessage) -> Unit
) : OutputStream() {

    private val outputBuffer = ByteBuffer.allocate(4096)
    private val outputMessages = LinkedBlockingQueue<CdmMessage>()

    @Synchronized
    override fun write(b: Int) {
        outputBuffer.put(b.toByte())
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (outputBuffer.remaining() < len) {
            val currentPosition = outputBuffer.position()
            outputBuffer.flip()
            val temp = ByteArray(outputBuffer.remaining())
            outputBuffer.get(temp)
            outputBuffer.clear()

            val newBuffer = ByteBuffer.allocate(
                    maxOf(outputBuffer.capacity() * 2, currentPosition + len)
            )
            newBuffer.order(outputBuffer.order())
            newBuffer.put(temp)
            newBuffer.put(b, off, len)
            outputBuffer.put(b, off, len)
        } else {
            outputBuffer.put(b, off, len)
        }
    }

    // Extract complete messages from the buffer
    @Synchronized
    override fun flush() {
        outputBuffer.flip() // Switch to read mode

        while (outputBuffer.remaining() >= 12) { // Wait for at least 12 bytes for the header
            outputBuffer.mark()

            val headerBuffer = ByteArray(12)
            outputBuffer.get(headerBuffer)
            val headerReader = ByteBuffer.wrap(headerBuffer)
            val messageType = headerReader.getInt()
            val sequence = headerReader.getInt()
            val payloadLength = headerReader.getInt()

            if (outputBuffer.remaining() >= payloadLength) {
                val payload = ByteArray(payloadLength)
                val message = CdmMessage(messageType, sequence, payload)
                outputBuffer.get(payload)
                messageCallback(message)
                outputMessages.offer(message)
            } else {
                // Not enough bytes for the full payload yet,
                // reset position and wait for more data
                outputBuffer.reset()
                break // Exit loop, wait for more data
            }
        }

        // Compact the buffer: move any unread data to the beginning
        outputBuffer.compact() // Switch back to write mode, discarding read data
    }

    /**
     * Get the next received message.
     */
    fun getNextMessage(timeoutMs: Long = DEFAULT_TIMEOUT_MS): CdmMessage? {
        return outputMessages.poll(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /**
     * Get the next received message with the given type. Any non-matching
     * messages are discarded from the queue.
     */
    fun getNextMessage(type: Int, timeoutMs: Long = DEFAULT_TIMEOUT_MS): CdmMessage? {
        val startTime = System.currentTimeMillis()
        var elapsedTime = 0L
        while (elapsedTime < timeoutMs) {
            val message = getNextMessage(timeoutMs - elapsedTime)
            if (message == null) {
                return null
            }
            if (message.type == type) {
                return message
            }
            elapsedTime = System.currentTimeMillis() - startTime
        }
        return null
    }

    fun getAllMessages(): List<CdmMessage> {
        val list = mutableListOf<CdmMessage>()
        outputMessages.drainTo(list)
        return list
    }

    fun clearMessages() {
        outputMessages.clear()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 2_000L
    }
}
