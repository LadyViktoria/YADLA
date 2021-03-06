package com.kludgenics.cgmlogger.app.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.ArrayMap
import android.util.Log
import com.google.android.gms.gcm.*
import com.kludgenics.alrightypump.cloud.nightscout.Nightscout
import com.kludgenics.cgmlogger.app.R
import com.squareup.okhttp.HttpUrl
import com.squareup.okhttp.OkHttpClient
import org.jetbrains.anko.*

/**
 * Created by matthiasgranberry on 5/31/15.
 */

public class TaskService : GcmTaskService(), AnkoLogger {
    companion object {
        public val ACTION_SYNC_NOW: String = "com.kludgenics.cgmlogger.action.SYNC"
        public val ACTION_SYNC_FULL: String = "com.kludgenics.cgmlogger.action.SYNC_FULL"
        public val TASK_SYNC_TREATMENTS: String = "sync_treatments"
        public val TASK_SYNC_ENTRIES_PERIODIC: String = "sync_entries_periodic"
        public val TASK_SYNC_ENTRIES_FULL: String = "sync_entries_full"
        public val TASK_LOOKUP_LOCATIONS: String = "lookup_locations"

        private val TREATMENT_SYNC_PERIOD: Long = 60 * 30
        private val TREATMENT_SYNC_FLEX: Long = 60 * 10
        private val ENTRY_SYNC_PERIOD: Long = 60 * 30
        private val ENTRY_SYNC_FLEX: Long = 60 * 10

        public fun syncNow(context: Context) {
            val intent = Intent()
            with(intent) {
                setAction(ACTION_SYNC_NOW)
                setClass(context, TaskService::class.java)
            }
            context.startService(intent)
        }

        public fun fullSyncNow(context: Context) {
            val intent = Intent()
            with(intent) {
                setAction(ACTION_SYNC_FULL)
                setClass(context, TaskService::class.java)
            }
            context.startService(intent)
        }

        public fun cancelNightscoutTasks(context: Context) {
            Log.i ("TaskService", "Nightscout task cancellation requested by $context")
            val networkManager = GcmNetworkManager.getInstance(context)
            networkManager.cancelTask(TASK_SYNC_ENTRIES_FULL, TaskService::class.java)
            networkManager.cancelTask(TASK_SYNC_ENTRIES_PERIODIC, TaskService::class.java)
            networkManager.cancelTask(TASK_SYNC_TREATMENTS, TaskService::class.java)
        }

        public fun scheduleNightscoutPeriodicTasks(context: Context) {
            Log.i ("TaskService", "Periodic Nightscout sync requested by $context")
            val networkManager = GcmNetworkManager.getInstance(context)
            networkManager.schedule(createPeriodicTreatmentTask())
            networkManager.schedule(createPeriodicEntryTask())
        }

        public fun scheduleNightscoutEntriesFullSync(context: Context) {
            Log.i ("TaskService", "Full sync requested by $context")
            val networkManager = GcmNetworkManager.getInstance(context)
            return networkManager.schedule(OneoffTask.Builder()
                    .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                    .setExecutionWindow(0, 30)
                    .setPersisted(true)
                    .setService(TaskService::class.java)
                    .setUpdateCurrent(true)
                    .setTag(TASK_SYNC_ENTRIES_FULL)
                    .build())
        }

        public fun createPeriodicEntryTask(): PeriodicTask {
            return PeriodicTask.Builder().setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                    .setPeriod(ENTRY_SYNC_PERIOD)
                    .setFlex(ENTRY_SYNC_FLEX)
                    .setPersisted(true)
                    .setService(TaskService::class.java)
                    .setUpdateCurrent(true)
                    .setTag(TASK_SYNC_ENTRIES_PERIODIC)
                    .build()
        }

        public fun createPeriodicTreatmentTask(): PeriodicTask {
            return PeriodicTask.Builder().setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                    .setPeriod(TREATMENT_SYNC_PERIOD)
                    .setFlex(TREATMENT_SYNC_FLEX)
                    .setPersisted(true)
                    .setService(TaskService::class.java)
                    .setUpdateCurrent(true)
                    .setTag(TASK_SYNC_TREATMENTS)
                    .build()
        }
    }

    val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        val p = defaultSharedPreferences
        p.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
        p
    }

    val sharedPreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener {
        sharedPreferences, s ->
        val resources = resources
        when (s) {
            resources.getString(R.string.nightscout_uri),
            resources.getString(R.string.nightscout_enable) -> {
                createNightscoutTasks()
            }
        }
    }

    val tasks: MutableMap<String, NightscoutTask> = ArrayMap()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_SYNC_NOW -> {
                async() {
                    onRunTask(TaskParams(TASK_SYNC_ENTRIES_PERIODIC))
                    onRunTask(TaskParams(TASK_SYNC_TREATMENTS))
                }
                2
            }
            ACTION_SYNC_FULL -> {
                async() {
                    onRunTask(TaskParams(TASK_SYNC_ENTRIES_FULL))
                    onRunTask(TaskParams(TASK_SYNC_TREATMENTS))
                }
                2
            }
            else -> super.onStartCommand(intent, flags, startId)
        }
    }

    override fun onInitializeTasks() {
        super.onInitializeTasks()
        info("initializing tasks")
        val resources = resources
        if (prefs.getBoolean(resources.getString(R.string.nightscout_enable), false)  &&
                !(prefs.getString(resources.getString(R.string.nightscout_uri), "").isBlank())) {
            scheduleNightscoutPeriodicTasks(this)
        } else
            cancelNightscoutTasks(this)
    }

    override fun onRunTask(taskParams: TaskParams): Int {
        if (tasks.isEmpty())
            createNightscoutTasks()

        return when (taskParams.tag) {
            TASK_LOOKUP_LOCATIONS -> {
                error("Location lookup currently unimplemented")
                GcmNetworkManager.RESULT_FAILURE
            }
            in tasks.keys -> {
                // if the tasks were reset mid-stream, don't crash
                info ("Starting ${taskParams.tag}")
                val r = tasks.get(taskParams.tag)?.call() ?: GcmNetworkManager.RESULT_RESCHEDULE
                info ("Finished ${taskParams.tag}")
                r
            }
            else -> {
                error ("Unrecognized tag ${taskParams.tag} in onRunTask")
                GcmNetworkManager.RESULT_FAILURE
            }  // unrecognized tag, should never happen
        }
    }

    private fun createNightscoutTasks(): Unit {
        val uri = prefs.getString(resources.getString(R.string.nightscout_uri), "")
        val enabled = prefs.getBoolean(resources.getString(R.string.nightscout_enable), false)

        info ("closing tasks in createNightscoutTasks")
        synchronized(tasks) {
            tasks.clear()
            if (enabled && uri != "") {
                val nightscout = Nightscout(HttpUrl.parse(uri), OkHttpClient())
                tasks.put(TASK_SYNC_ENTRIES_FULL, NightscoutTask(nightscout, full = true))
                tasks.put(TASK_SYNC_ENTRIES_PERIODIC, NightscoutTask(nightscout, full = true))
            } else
                cancelNightscoutTasks(ctx)
        }
    }

    override fun onDestroy() {
        synchronized(tasks) {
            info ("onDestroy()")
            tasks.clear()
            super.onDestroy()
        }
    }

}

