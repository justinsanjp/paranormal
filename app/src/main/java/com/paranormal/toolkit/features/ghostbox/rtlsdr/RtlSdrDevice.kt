package com.paranormal.toolkit.features.ghostbox.rtlsdr

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

/**
 * USB driver for RTL2832U-based dongles ("RTL-SDR"), following the public
 * protocol documented by the open-source librtlsdr project
 * (osmocom/rtl-sdr, BSD licensed). This talks to real, physically attached
 * hardware over USB bulk/control transfers - there is no simulated signal
 * path anywhere in this class. Without a real dongle this class is simply
 * never constructed.
 *
 * Caveat: this has not been exercised against physical hardware in this
 * session (none was available). The control-transfer plumbing (register
 * blocks, I2C-through-demod addressing) and the R820T2 PLL tuning formula
 * follow the published protocol with reasonable confidence; the R820T2
 * bring-up register table uses the commonly published minimal/default
 * values rather than a full gain/IF-filter calibration table, so real
 * reception quality will likely need tuning once actual hardware is
 * plugged in.
 */
class RtlSdrDevice(
    private val connection: UsbDeviceConnection,
    usbInterface: UsbInterface,
) {
    private var bulkInEndpoint: UsbEndpoint? = null

    init {
        connection.claimInterface(usbInterface, true)
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.direction == UsbConstants.USB_DIR_IN &&
                endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK
            ) {
                bulkInEndpoint = endpoint
            }
        }
        requireNotNull(bulkInEndpoint) { "Kein Bulk-IN-Endpoint gefunden - kein RTL2832U-kompatibles Gerät?" }
        initDemod()
        tunerInit()
    }

    // ---- low-level USB control transfers ------------------------------------------

    private fun demodWriteReg(page: Int, addr: Int, value: Int, length: Int) {
        val data = if (length == 1) {
            byteArrayOf(value.toByte())
        } else {
            byteArrayOf((value shr 8).toByte(), value.toByte())
        }
        val realAddr = (addr shl 8) or 0x20
        connection.controlTransfer(USB_TYPE_OUT, 0, realAddr, page, data, data.size, TIMEOUT_MS)
    }

    private fun i2cWriteReg(i2cAddr: Int, reg: Int, value: Int) {
        val data = byteArrayOf(reg.toByte(), value.toByte())
        connection.controlTransfer(USB_TYPE_OUT, 0, i2cAddr, BLOCK_I2C shl 8, data, data.size, TIMEOUT_MS)
    }

    // ---- init sequence ---------------------------------------------------------------

    private fun initDemod() {
        demodWriteReg(1, 0x01, 0x14, 1) // soft reset
        demodWriteReg(1, 0x01, 0x10, 1) // release reset
        demodWriteReg(1, 0x15, 0x00, 1) // disable DAGC while configuring
        demodWriteReg(1, 0x01, 0x18, 1) // enable I2C repeater so we can reach the tuner
        setSampleRate(SAMPLE_RATE_HZ)
    }

    private fun tunerInit() {
        for ((reg, value) in R820T2_INIT_REGISTERS) {
            i2cWriteReg(R820T2_I2C_ADDR, reg, value)
        }
    }

    fun setSampleRate(hz: Int) {
        val ratio = (CRYSTAL_FREQ_HZ.toLong() shl 22) / hz
        demodWriteReg(1, 0x9F, ((ratio shr 16) and 0xFFFF).toInt(), 2)
        demodWriteReg(1, 0xA1, (ratio and 0xFFFF).toInt(), 2)
    }

    /** Tunes the R820T2 PLL to [freqHz] using its published integer/fractional-N formula. */
    fun tune(freqHz: Long) {
        val vcoMin = 1_770_000_000L
        var divNum = 0
        var vcoFreq = freqHz
        while (vcoFreq < vcoMin && divNum < 5) {
            divNum++
            vcoFreq = freqHz shl divNum
        }
        val refFreq = CRYSTAL_FREQ_HZ.toLong()
        val nInt = (vcoFreq / (2 * refFreq)).toInt()
        val remainder = vcoFreq - nInt.toLong() * 2 * refFreq
        val nFrac = ((remainder shl 15) / refFreq).toInt()

        i2cWriteReg(R820T2_I2C_ADDR, 0x10, divNum shl 5)
        i2cWriteReg(R820T2_I2C_ADDR, 0x14, nInt and 0xFF)
        i2cWriteReg(R820T2_I2C_ADDR, 0x15, (nFrac shr 8) and 0xFF)
        i2cWriteReg(R820T2_I2C_ADDR, 0x16, nFrac and 0xFF)
    }

    /** Reads raw interleaved unsigned 8-bit I/Q samples straight off the bulk endpoint. */
    fun readSamples(buffer: ByteArray): Int {
        val endpoint = bulkInEndpoint ?: return -1
        return connection.bulkTransfer(endpoint, buffer, buffer.size, TIMEOUT_MS)
    }

    fun close() {
        runCatching { connection.close() }
    }

    companion object {
        private const val TIMEOUT_MS = 1000
        private const val USB_TYPE_OUT = UsbConstants.USB_TYPE_VENDOR or UsbConstants.USB_DIR_OUT
        private const val BLOCK_I2C = 6
        private const val R820T2_I2C_ADDR = 0x34

        const val CRYSTAL_FREQ_HZ = 28_800_000
        const val SAMPLE_RATE_HZ = 1_024_000

        // Minimal documented R820T2 bring-up registers - conservative chip
        // defaults, not a full gain/IF-filter calibration table. See the
        // class doc's hardware-validation caveat.
        private val R820T2_INIT_REGISTERS = listOf(
            0x05 to 0x90, 0x06 to 0x80, 0x07 to 0x60, 0x08 to 0x80,
            0x09 to 0x40, 0x0A to 0x80, 0x0B to 0x6B, 0x0C to 0x00,
            0x0D to 0x63, 0x0E to 0x75, 0x0F to 0x68, 0x10 to 0x6C,
            0x11 to 0x00, 0x12 to 0x37, 0x13 to 0x28, 0x14 to 0x00,
            0x15 to 0x00, 0x16 to 0x40, 0x17 to 0x30, 0x18 to 0x48,
            0x19 to 0x20, 0x1A to 0x30, 0x1B to 0x48, 0x1C to 0x20,
            0x1D to 0x0F, 0x1E to 0x40, 0x1F to 0x00,
        )
    }
}
