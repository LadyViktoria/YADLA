package com.kludgenics.alrightypump.tandem

import com.kludgenics.alrightypump.*
import okio.BufferedSink
import okio.BufferedSource
import org.joda.time.Chronology
import org.joda.time.chrono.ISOChronology

/**
 * Created by matthias on 11/19/15.
 */
class TandemPump(private val source: BufferedSource, private val sink: BufferedSink) : InsulinPump, Glucometer {
    override val chronology: Chronology
        get() = ISOChronology.getInstance()
    override val supportedFeatures: Set<DeviceFeature>
        get() = setOf()
    override val firmwareVersions: List<String>
        get() = throw UnsupportedOperationException()
    override val serialNumbers: List<String>
        get() = throw UnsupportedOperationException()
    override val outOfRangeLow: Double
        get() = 19.0
    override val outOfRangeHigh: Double
        get() = 601.0
    override val dateTimeChangeRecords: Sequence<DateTimeChangeRecord>
        get() = throw UnsupportedOperationException()
    override val smbgRecords: Sequence<SmbgRecord>
        get() = throw UnsupportedOperationException()

    public fun commandResponse(payload: TandemPayload): TandemResponse {
        val packet = TandemRequest(payload).frame
        sink.write(packet, packet.size())
        val response = TandemResponse(source)
        return response
    }
}