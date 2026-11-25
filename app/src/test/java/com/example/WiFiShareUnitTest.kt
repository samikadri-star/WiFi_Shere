package com.example

import com.example.data.local.entity.TransferEntity
import com.example.domain.model.TransferStatus
import com.example.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class WiFiShareUnitTest {

    @Test
    fun `format bytes uses western numerals and crisp units`() {
        assertEquals("0 B", FormatUtils.formatBytes(0L))
        assertEquals("512 B", FormatUtils.formatBytes(512L))
        assertEquals("2 KB", FormatUtils.formatBytes(2048L))
        assertEquals("1.5 MB", FormatUtils.formatBytes(1572864L))
        assertEquals("2.00 GB", FormatUtils.formatBytes(2L * 1024L * 1024L * 1024L))
    }

    @Test
    fun `format speed and duration`() {
        assertEquals("64.0 MB/s", FormatUtils.formatSpeedMbps(64L * 1024L * 1024L))
        assertEquals("01:14", FormatUtils.formatDurationMmSs(74L))
    }

    @Test
    fun `transfer entity computes progress percentage accurately`() {
        val entity = TransferEntity(
            id = "t1",
            sessionId = "s1",
            peerDeviceId = "p1",
            peerDeviceName = "هاتف سارة",
            fileName = "video.mp4",
            mimeType = "video/mp4",
            category = "VIDEO",
            totalBytes = 1000L,
            transferredBytes = 680L,
            status = TransferStatus.PAUSED.name,
        )
        assertEquals(68, entity.progressPercent)
        assertEquals(0.68f, entity.progressFraction, 0.001f)
    }
}
