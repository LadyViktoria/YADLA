package com.kludgenics.alrightypump.device.dexcom.g4

import com.kludgenics.alrightypump.therapy.CgmRecord
import org.joda.time.Instant

/**
 * Created by matthias on 11/19/15.
 */
data class DexcomCgmRecord(public override val time: Instant,
                           public override val value: DexcomG4GlucoseValue,
                           public override val source: String = DexcomG4.SOURCE) : CgmRecord {
    constructor(egvRecord: EgvRecord, sgvRecord: SgvRecord?,
                calSetRecord: CalSetRecord?): this(egvRecord.displayTime, DexcomG4GlucoseValue(egvRecord, sgvRecord, calSetRecord))
}