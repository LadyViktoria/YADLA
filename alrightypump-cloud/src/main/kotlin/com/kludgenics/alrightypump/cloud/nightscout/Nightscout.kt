package com.kludgenics.alrightypump.cloud.nightscout

import com.kludgenics.alrightypump.DateTimeChangeRecord
import com.kludgenics.alrightypump.device.ContinuousGlucoseMonitor
import com.kludgenics.alrightypump.device.Glucometer
import com.kludgenics.alrightypump.device.InsulinPump
import com.kludgenics.alrightypump.device.dexcom.g4.DexcomCgmRecord
import com.kludgenics.alrightypump.therapy.*
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.okhttp.*
import okio.ByteString
import org.joda.time.Chronology
import org.joda.time.Instant
import retrofit.MoshiConverterFactory
import retrofit.Retrofit
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.*
import javax.inject.Inject
import javax.inject.Named

/**
 * Created by matthias on 11/29/15.
 */

class Nightscout @Inject constructor (@Named("Nightscout") val url: HttpUrl,
                                      okHttpClient: OkHttpClient) : InsulinPump,
        ContinuousGlucoseMonitor,
        Glucometer
{
    override val profileRecords: Sequence<ProfileRecord>
        get() = throw UnsupportedOperationException()
    override val chronology: Chronology
        get() = throw UnsupportedOperationException()
    override val serialNumber: String
        get() = url.toString()
    override val bolusRecords: Sequence<BolusRecord>
        get() = throw UnsupportedOperationException()
        //treatments.filter { it.insulin != null }.map { null }
    override val basalRecords: Sequence<BasalRecord>
        get() = throw UnsupportedOperationException()
    override val consumableRecords: Sequence<ConsumableRecord>
        get() = throw UnsupportedOperationException()
    override val smbgRecords: Sequence<NightscoutApiMbgEntry>
        get() = entries.filterIsInstance<NightscoutApiMbgEntry>()
    override val dateTimeChangeRecords: Sequence<DateTimeChangeRecord>
        get() = throw UnsupportedOperationException()
    override val outOfRangeHigh: Double
        get() = throw UnsupportedOperationException()
    override val outOfRangeLow: Double
        get() = throw UnsupportedOperationException()
    override val cgmRecords: Sequence<NightscoutApiSgvEntry>
        get() = entries.filterIsInstance<NightscoutApiSgvEntry>()

    companion object {
        const final val FETCH_SIZE = 50000
    }

    private val retrofit: Retrofit
    private val nightscoutApi: NightscoutApi

    public val entries: Sequence<Record> get() = entries().mapNotNull {
        when (it.rawEntry) {
            is Record -> it
            else -> null
        }
    }

    public fun entries(range: ClosedRange<Instant> = RestCallSequence.InstantRange(Instant.parse("2008-01-01T01:00:00Z"),
            Instant.now())): Sequence<NightscoutEntryJson> {
        return (RestCallSequence(range) {
            start, end ->
            nightscoutApi
                    .getRecordsBetween(start.millis, end.millis, FETCH_SIZE)
                    .execute()
                    .body()
        })
    }

    public val treatments: Sequence<NightscoutTreatment> get() = treatments()
    public fun treatments(range: ClosedRange<Instant> = RestCallSequence.InstantRange(Instant.parse("2008-01-01T01:00:00Z"),
            Instant.now())): Sequence<NightscoutTreatment> {
        return RestCallSequence(range) {
            start, end ->
            nightscoutApi
                    .getTreatmentsBetween(start.toString(), end.toString(), FETCH_SIZE)
                    .execute()
                    .body()
        }
    }

    public fun postRecords(records: Sequence<Record>) {
        var (treatmentRecords, entryRecords) = records.flatMap {
            when (it) {
                is CalibrationRecord -> sequenceOf(NightscoutEntryJson(NightscoutCalJson(it)))
                is DexcomCgmRecord -> sequenceOf(NightscoutEntryJson(NightscoutSgvJson(it))) // TODO this is the only place where there is a device dependency :(
                is RawCgmRecord -> sequenceOf(NightscoutEntryJson(NightscoutSgvJson(it)))
                is CgmRecord -> sequenceOf(NightscoutEntryJson(NightscoutSgvJson(it)))
                is SmbgRecord -> sequenceOf(NightscoutEntryJson(NightscoutMbgJson(it)))
                is FoodRecord,
                is CgmInsertionRecord,
                is TemporaryBasalStartRecord,
                is TemporaryBasalEndRecord,
                is SuspendedBasalRecord,
                is CannulaChangedRecord,
                is CartridgeChangeRecord,
                is CannulaChangedRecord,
                    // is ScheduledBasalRecord,
                is BolusRecord -> {
                    val treatment = NightscoutTreatment(HashMap())
                    treatment.applyRecord(it)
                    sequenceOf(treatment)
                }
                else -> emptySequence<Record>()
            }
        }.partition { it is NightscoutApiTreatment }
        while (!entryRecords.isEmpty()) {
            val r = entryRecords.filterIsInstance<NightscoutEntryJson>().mapIndexed {
                i, nightscoutEntryJson ->
                i to nightscoutEntryJson
            }.partition {
                it.first < 1000
            }
            val batch = r.first.map { it.second }.toMutableList()
            entryRecords = r.second.map { it.second }
            try {
                if (!batch.isEmpty())
                    nightscoutApi.postRecords(batch).enqueue(object : retrofit.Callback<MutableList<NightscoutEntryJson>> {
                        override fun onResponse(response: retrofit.Response<MutableList<NightscoutEntryJson>>?, retrofit: Retrofit?) {
                        }

                        override fun onFailure(t: Throwable?) {
                        }

                    })
            } catch (e: JsonDataException) {

            }
        }

        val t = treatmentRecords.filterIsInstance<NightscoutTreatment>().fold(arrayListOf<NightscoutTreatment>()) {
            arrayList: ArrayList<NightscoutTreatment>, nightscoutTreatment: NightscoutTreatment ->
            if (arrayList.size >= 1000) {
                nightscoutApi.postTreatments(arrayList).enqueue(object : retrofit.Callback<ResponseBody> {
                    override fun onResponse(response: retrofit.Response<ResponseBody>?, retrofit: Retrofit?) {
                    }

                    override fun onFailure(t: Throwable?) {
                    }

                })
                arrayList.clear()
            }
            arrayList.add(nightscoutTreatment)
            arrayList
        }
        if (t.isNotEmpty()) {
            nightscoutApi.postTreatments(t).enqueue(object : retrofit.Callback<ResponseBody> {
                override fun onResponse(response: retrofit.Response<ResponseBody>?, retrofit: Retrofit?) {
                }

                override fun onFailure(t: Throwable?) {
                }

            })
        }
    }

    private fun calculateSecretHash(secret: String): String {
        val digestBytes = MessageDigest.getInstance("SHA-1")
                .digest(secret.toByteArray(Charset.forName("utf8")));
        return ByteString.of(*digestBytes).hex()
    }

    private class ApiSecretInterceptor(private val apiSecretHash: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            return chain.proceed(request.newBuilder().header("api-secret", apiSecretHash).build())
        }
    }

    init {
        val moshiBuilder = Moshi.Builder()
        NightscoutApi.registerTypeAdapters(moshiBuilder)
        val apiSecret = url.username()
        val baseUrl = url.newBuilder().username("").build()
        okHttpClient.networkInterceptors().add(ApiSecretInterceptor(calculateSecretHash(apiSecret)))
        retrofit = Retrofit.Builder().addConverterFactory(MoshiConverterFactory.create(moshiBuilder.build()))
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .build()
        nightscoutApi = retrofit.create(NightscoutApi::class.java)
    }
}

