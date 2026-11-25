package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.QrCodeUtils
import com.example.domain.model.QrPairingPayload
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("واي فاي شير", appName)
    }

    @Test
    fun `generate qr bitmap from pairing payload`() {
        val payload = QrPairingPayload(
            deviceId = "dev123",
            deviceName = "هاتف فهد",
            host = "192.168.43.1",
            port = 54321,
            ssid = "WiFiShare_5G",
            token = "tok_abc",
        )
        val raw = Json.encodeToString(payload)
        val bitmap = QrCodeUtils.generateQrBitmap(raw, 256)
        assertNotNull(bitmap)
        assertEquals(256, bitmap?.width)
        assertEquals(256, bitmap?.height)
    }
}
