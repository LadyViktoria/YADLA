package com.kludgenics.alrightypump

import com.kludgenics.alrightypump.therapy.BasalRecord
import com.kludgenics.alrightypump.therapy.BolusRecord
import com.kludgenics.alrightypump.therapy.ConsumableRecord
import com.kludgenics.alrightypump.therapy.Record
import org.joda.time.Chronology


/**
 * A baseline interface to query an insulin pump.  This interface only provides the ability to
 * identify and verify a connection to a pump along with metadata about the pump.  An actual
 * implementation will implement other interfaces to query the device logs and
 */
interface InsulinPump {
    /**
     * A Joda Time [Chronolgy] constructed from pump time change records, allowing for
     * the translation of raw pump events to standard time.
     */
    val chronology: Chronology

    /**
     * Opaque list of version identifiers
     */
    val firmwareVersions: List<String>

    /**
     * Opaque list of serial numbers
     */
    val serialNumbers: List<String>

    val bolusRecords: Sequence<BolusRecord>

    val basalRecords: Sequence<BasalRecord>

    val consumableRecords: Sequence<ConsumableRecord>
}
