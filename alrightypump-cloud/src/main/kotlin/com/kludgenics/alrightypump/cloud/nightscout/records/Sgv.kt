package com.kludgenics.alrightypump.cloud.nightscout.records

interface Sgv : NightscoutEntry {
    override val type: String get() = "sgv"
    val sgv: Int
    val direction: String
    val filtered: Double?
    val unfiltered: Double?
    val rssi: Int?
    val noise: Int?
}