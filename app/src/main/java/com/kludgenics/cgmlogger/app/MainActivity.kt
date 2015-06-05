package com.kludgenics.cgmlogger.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBar
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import com.google.android.gms.location.DetectedActivity
import com.kludgenics.cgmlogger.app.service.BaseGmsPlaceService
import com.kludgenics.cgmlogger.app.service.LocationIntentService
import com.kludgenics.cgmlogger.extension.*
import com.kludgenics.cgmlogger.model.activity.PlayServicesActivity
import com.kludgenics.cgmlogger.model.glucose.BloodGlucoseRecord
import com.kludgenics.cgmlogger.model.location.GeoApi
import com.kludgenics.cgmlogger.model.location.data.GeocodedLocation
import com.kludgenics.cgmlogger.model.location.data.Position
import com.kludgenics.cgmlogger.model.location.places.GooglePlacesLocation
import com.kludgenics.cgmlogger.model.nightscout.NightscoutApiTreatment
import com.kludgenics.cgmlogger.model.treatment.Treatment
import io.realm.Realm
import org.jetbrains.anko.*
import org.joda.time.DateTime
import rx.lang.kotlin.subscriber
import rx.lang.kotlin.toObservable
import java.util.*
import kotlin.properties.Delegates

public class MainActivity : BaseActivity() {
    override protected val navigationId = R.id.nav_home

    private val coordinator: CoordinatorLayout by Delegates.lazy { find<CoordinatorLayout>(R.id.main_content) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super<BaseActivity>.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupNavigationBar()
        setupActionBar()

        val fab = find<FloatingActionButton>(R.id.fab)
        fab.onClick {
         }
        startService(intentFor<LocationIntentService>().setAction(LocationIntentService.ACTION_START_LOCATION_UPDATES))
        // Set up the drawer.
        val realm = Realm.getInstance(ctx)
        realm.use {
            val before = System.currentTimeMillis()
            val res = realm.where(javaClass<BloodGlucoseRecord>())
                    .greaterThan("date", DateTime().minusDays(1).toDate())
                    .findAllSorted("date", false)
            if (res != null)
                info("${res.first().getValue()} ${res.first().getDate()} ${res.first().getType()}")
            val after = System.currentTimeMillis()

            info("BG query took ${after - before} ms")
            val acts = realm.allObjects(javaClass<PlayServicesActivity>())
            acts.map {
                it.getTime() to
                when(it.getActivityId()) {
                    DetectedActivity.IN_VEHICLE -> "in_vehicle"
                    DetectedActivity.ON_BICYCLE -> "on_bicycle"
                    DetectedActivity.ON_FOOT -> "on_foot"
                    DetectedActivity.RUNNING -> "running"
                    DetectedActivity.STILL -> "still"
                    DetectedActivity.TILTING -> "tilting"
                    DetectedActivity.WALKING -> "walking"
                    DetectedActivity.UNKNOWN -> "unknown"
                    else -> "other"
                }
                //info("Activity: ${activity}, ${it.getConfidence()}, ${it.getTime()}")
            }
        }
    }

    override fun onStart() {
        super<BaseActivity>.onStart()
        coordinator.snackbar("Hello, World")
    }

    override fun onStop() {
        super<BaseActivity>.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        /*if (!mNavigationDrawerFragment!!.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu)
            restoreActionBar()
            return true
        }*/
        return super<BaseActivity>.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item!!.getItemId()

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true
        }

        return super<BaseActivity>.onOptionsItemSelected(item)
    }

    companion object {
        val agpPathString = "M24,378.87426419718315L30.291666666666664,376.97117072676065C36.58333333333333,375.0680772563381,49.166666666666664,371.261890315493,61.75,369.41767841971836C74.33333333333333,367.5734665239437,86.91666666666666,367.6912296732395,99.5,367.5679041267606C112.08333333333333,367.4445785802817,124.66666666666666,367.08016433802817,137.25,367.0949823521127C149.83333333333331,367.1098003661972,162.41666666666666,367.50385063661975,175,367.80758824788734C187.58333333333331,368.11132585915493,200.16666666666663,368.3247508112676,212.74999999999997,366.3791379014085C225.33333333333331,364.4335249915493,237.91666666666666,360.3288742197183,250.49999999999997,358.25056461690144C263.0833333333333,356.1722550140845,275.66666666666663,356.1202865802817,288.24999999999994,357.4123954619718C300.83333333333326,358.70450434366194,313.41666666666663,361.34069054084506,326,363.78160152394366C338.5833333333333,366.22251250704227,351.16666666666663,368.46814827605635,363.75,370.594276771831C376.3333333333333,372.72040526760566,388.91666666666663,374.7270264901408,401.49999999999994,376.0852874422535C414.08333333333326,377.4435483943662,426.66666666666663,378.1534490760563,439.25,378.6158982394366C451.8333333333333,379.0783474028169,464.41666666666663,379.2933450478873,477,379.38751200563377C489.5833333333333,379.48167896338026,502.16666666666663,379.4550152338028,514.75,379.1635328676056C527.3333333333333,378.87205050140847,539.9166666666665,378.3157494985915,552.4999999999999,377.00122539436614C565.0833333333333,375.68670129014083,577.6666666666666,373.613954084507,590.25,372.65630581408453C602.8333333333333,371.69865754366197,615.4166666666666,371.85610820845073,628,372.0155634563381C640.5833333333333,372.1750187042254,653.1666666666665,372.3364785352113,665.7499999999999,372.4666360619718C678.3333333333333,372.5967935887324,690.9166666666666,372.6956488112676,703.4999999999999,372.12226723661973C716.0833333333333,371.5488856619718,728.6666666666666,370.30326729014087,741.25,369.8275674225352C753.8333333333333,369.3518675549296,766.4166666666665,369.64608619154933,778.9999999999999,369.81031749014085C791.5833333333333,369.9745487887324,804.1666666666666,370.00879274929576,816.7499999999999,369.10541853521124C829.3333333333333,368.2020443211268,841.9166666666666,366.3610519323944,854.5,367.83350372676057C867.0833333333333,369.30595552112675,879.6666666666665,374.0918514985915,892.2499999999999,376.48421894084504C904.8333333333333,378.8765863830986,917.4166666666666,378.87542529014087,923.7083333333333,378.874844743662C930,378.87426419718315,930,378.87426419718315,930,347.60867021971836C930,316.3430762422536,930,253.81188828732394,930,222.54629430985915C930,191.28070033239433,930,191.28070033239433,923.7083333333333,198.15859513239434C917.4166666666666,205.03648993239435,904.8333333333333,218.79227953239436,892.2499999999999,224.05856943943664C879.6666666666665,229.3248593464789,867.0833333333333,226.10164956056337,854.5,225.88716827887325C841.9166666666666,225.6726869971831,829.3333333333333,228.46693421971833,816.75,230.31741121408453C804.1666666666666,232.1678882084507,791.5833333333333,233.07459497464788,778.9999999999999,240.46435205633802C766.4166666666665,247.85410913802815,753.8333333333333,261.7269165352113,741.25,268.44608777464794C728.6666666666666,275.16525901408454,716.0833333333333,274.7307940957747,703.5,269.75640560845073C690.9166666666666,264.7820171211268,678.3333333333333,255.26770506478874,665.7499999999999,249.3563625943662C653.1666666666665,243.44502012394366,640.5833333333333,241.13664723943663,628,241.38045688450705C615.4166666666666,241.62426652957748,602.8333333333333,244.42025870422535,590.25,244.69788290422537C577.6666666666666,244.97550710422536,565.0833333333333,242.73476332957748,552.4999999999999,238.81183644225354C539.9166666666665,234.8889095549296,527.3333333333333,229.2837995549296,514.75,220.38544482816903C502.16666666666663,211.48709010140846,489.5833333333333,199.2954906478873,476.99999999999994,196.60809296338027C464.41666666666663,193.9206952788732,451.8333333333333,200.73749936338027,439.25,206.1093398253521C426.66666666666663,211.48118028732392,414.08333333333326,215.40805712676053,401.49999999999994,219.6527040732394C388.91666666666663,223.8973510197183,376.3333333333333,228.45976807323945,363.74999999999994,226.75327074647888C351.16666666666663,225.0467734197183,338.5833333333333,217.07136171267604,326,216.12654260845068C313.41666666666663,215.18172350422532,300.83333333333326,221.26749700281684,288.24999999999994,224.70028323943654C275.66666666666663,228.13306947605628,263.0833333333333,228.9128684507042,250.5,226.0299574535211C237.91666666666666,223.147046456338,225.33333333333331,216.60142548732392,212.74999999999997,213.42993278591547C200.16666666666663,210.25844008450701,187.58333333333331,210.46107565070417,175,215.36038978873233C162.41666666666666,220.25970392676052,149.83333333333331,229.85569663661968,137.25,234.99300294647884C124.66666666666666,240.130309256338,112.08333333333333,240.80892916619715,99.5,238.95970270704223C86.91666666666666,237.11047624788728,74.33333333333333,232.73340341971826,61.75,224.3655952957746C49.166666666666664,215.99778717183094,36.58333333333333,203.63924375211263,30.291666666666664,197.45997204225347L24,191.28070033239433"
    }

}