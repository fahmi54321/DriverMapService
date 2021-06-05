package com.android.drivermapservice

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.drivermapservice.EventBus.DriverRequestReceived
import com.android.drivermapservice.Model.MapLocation
import com.android.drivermapservice.Remote.IGoogleAPI
import com.android.drivermapservice.Remote.RetrofitClient
import com.android.drivermapservice.Service.MyMapService
import com.android.drivermapservice.Utils.Common
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_maps.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import java.io.IOException
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*
import java.util.concurrent.TimeUnit

class MapsActivity : AppCompatActivity(), OnMapReadyCallback,
    SharedPreferences.OnSharedPreferenceChangeListener {


    //  todo 3 design request layout
    private val compositeDisposable = CompositeDisposable()
    private var iGoogleApi: IGoogleAPI? = null
    private var blackPolyLine: Polyline? = null
    private var greyPolyLine: Polyline? = null
    private var polyLineOptions: PolylineOptions? = null
    private var blackPolyLineOptions: PolylineOptions? = null
    private var polyLineList: ArrayList<LatLng?>? = null
    private lateinit var chip_decline: Chip
    private lateinit var layout_accept: CardView
    private lateinit var circularProgressBar: CircularProgressBar
    private lateinit var txt_estimate_time: TextView
    private lateinit var txt_estimate_distance: TextView

    //todo 1 map
    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment

    //todo 1 map service current location
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null

    companion object {
        val MY_PERMISSIONS_REQUEST_LOCATION = 99
    }

    private var mService: MyMapService? = null
    private var mBound = false
    private var mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val binder = p1 as MyMapService.LocalBinder
            mService = binder.service
            mBound = true
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            mService = null
            mBound = false
        }

    }

    //todo 1 map service current location and firebase
    private lateinit var onlineRef: DatabaseReference
    private var currentUserRef: DatabaseReference? = null
    private lateinit var driverLocationRef: DatabaseReference
    private lateinit var geoFire: GeoFire
    private var onlineValueEventListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.exists() && currentUserRef != null) {
                currentUserRef?.onDisconnect()?.removeValue()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Snackbar.make(mapFragment.requireView(), error.message, Snackbar.LENGTH_LONG).show()
        }

    }

    //todo 2 map service current location
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onBackgroundLocationRetrive(event: MapLocation) {

        if (event.location != null) {
            Log.e("map back", "lat : ${event.location.latitude} long : ${event.location.longitude}")
            val newPos = LatLng(
                event.location.latitude,
                event.location.longitude
            )
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))

            //todo 2 map service current location and firebase
            val geoCoder = Geocoder(this, Locale.getDefault())
            val addressList: List<Address>?
            try {
                addressList = geoCoder.getFromLocation(
                    event.location.latitude,
                    event.location.longitude,
                    1
                )
                val cityName = addressList[0].locality
                driverLocationRef =
                    FirebaseDatabase.getInstance()
                        .getReference(Common.DRIVER_LOCATION_REFERENCE)
                        .child(cityName)
                currentUserRef = driverLocationRef.child(
                    FirebaseAuth.getInstance().currentUser!!.uid
                )
                geoFire = GeoFire(driverLocationRef)

                //update location
                geoFire.setLocation(
                    FirebaseAuth.getInstance().currentUser!!.uid,
                    GeoLocation(
                        event.location.latitude,
                        event.location.longitude
                    )
                ) { key: String, error: DatabaseError? ->
                    if (error != null) {
                        Snackbar.make(
                            mapFragment.requireView(),
                            error.message,
                            Snackbar.LENGTH_LONG
                        ).show()
                    } else {
                        Snackbar.make(
                            mapFragment.requireView(),
                            "You are online",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }

                registerOnlineSystem()

                //modifikasi rule
//                    "MotorLocations" : {
//                            "$uid" : {
//                            ".read" : "auth !=null",
//                            ".write" : "$auth != null"
//                        }
//                    },

            } catch (e: IOException) {
                Snackbar.make(mapFragment.requireView(), e.message ?: "", Snackbar.LENGTH_SHORT)
                    .show()
            }
        }

    }

    //todo 8 design request layout
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDriverRequestReceived(eventBus: DriverRequestReceived) {
        if (ActivityCompat.checkSelfPermission(
                this@MapsActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this@MapsActivity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //Location Permission already granted
            Toast.makeText(
                this@MapsActivity,
                "Permission granted",
                Toast.LENGTH_SHORT
            )
                .show()
            return
        } else {
            //Request Location Permission
            checkLocationPermission()
        }
        fusedLocationProviderClient?.lastLocation
            ?.addOnFailureListener {
                Snackbar.make(mapFragment.requireView(), it.message ?: "", Snackbar.LENGTH_LONG)
                    .show()
            }
            ?.addOnSuccessListener { location ->
                compositeDisposable.add(
                    iGoogleApi?.getDirections(
                        "driving",
                        "less_driving",
                        StringBuilder()
                            .append(location.latitude)
                            .append(",")
                            .append(location.longitude)
                            .toString(),
                        eventBus.pickupLocation,
                        getString(R.string.google_maps_key)
                    )
                    !!.subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            Log.d("API_RETURN", it)
                            try {
                                val jsonObject = JSONObject(it)
                                val jsonArray = jsonObject.getJSONArray("routes")
                                for (i in 0 until jsonArray.length()) {
                                    val route = jsonArray.getJSONObject(i)
                                    val poly = route.getJSONObject("overview_polyline")
                                    val polyLine = poly.getString("points")
                                    polyLineList = Common.decodePoly(polyLine)
                                }

                                polyLineOptions = PolylineOptions()
                                polyLineOptions?.color(Color.GRAY)
                                polyLineOptions?.width(12f)
                                polyLineOptions?.startCap(SquareCap())
                                polyLineOptions?.jointType(JointType.ROUND)
                                polyLineOptions?.addAll(polyLineList)
                                greyPolyLine = mMap.addPolyline(polyLineOptions)

                                blackPolyLineOptions = PolylineOptions()
                                blackPolyLineOptions?.color(Color.BLACK)
                                blackPolyLineOptions?.width(5f)
                                blackPolyLineOptions?.startCap(SquareCap())
                                blackPolyLineOptions?.jointType(JointType.ROUND)
                                blackPolyLineOptions?.addAll(polyLineList)
                                blackPolyLine = mMap.addPolyline(blackPolyLineOptions)

                                //Animator
                                val valueAnimator = ValueAnimator.ofInt(0, 100)
                                valueAnimator.duration = 1100
                                valueAnimator.repeatCount = ValueAnimator.INFINITE
                                valueAnimator.interpolator = LinearInterpolator()
                                valueAnimator.addUpdateListener {
                                    val points = greyPolyLine?.points
                                    val percentValue = it.animatedValue.toString().toInt()
                                    val size = points?.size
                                    val newPoints = (size?.times((percentValue / 100.0f)))?.toInt()
                                    val p = points?.subList(0, newPoints ?: 0)
                                    blackPolyLine?.points = (p)
                                }
                                valueAnimator.start()

                                val origin = LatLng(location.latitude, location.longitude)
                                val destination = LatLng(
                                    eventBus.pickupLocation!!.split(",")[0].toDouble(),
                                    eventBus.pickupLocation!!.split(",")[1].toDouble()
                                )

                                val latLngBound =
                                    LatLngBounds.Builder().include(origin)
                                        .include(destination)
                                        .build()
                                //Add car icon for origin
                                val objects = jsonArray.getJSONObject(0)
                                val legs = objects.getJSONArray("legs")
                                val legsObject = legs.getJSONObject(0)
                                val time = legsObject.getJSONObject("duration")
                                val duration = time.getString("text")
                                val distanceEstimate = legsObject.getJSONObject("distance")
                                val distance = distanceEstimate.getString("text")

                                txt_estimate_time.setText(duration)
                                txt_estimate_distance.setText(distance)
                                mMap.addMarker(
                                    MarkerOptions().position(destination)
                                        .icon(BitmapDescriptorFactory.defaultMarker())
                                        .title("Pickup Location")
                                )

                                mMap.moveCamera(
                                    CameraUpdateFactory.newLatLngBounds(
                                        latLngBound,
                                        160
                                    )
                                )
                                mMap.moveCamera(
                                    CameraUpdateFactory.zoomTo(
                                        mMap.cameraPosition?.zoom?.minus(
                                            1
                                        ) ?: 0f
                                    )
                                )

                                chip_decline.visibility = View.VISIBLE
                                layout_accept.visibility = View.VISIBLE

                                //Countdown
                                Observable.interval(100, TimeUnit.MILLISECONDS)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .doOnNext {
                                        circularProgressBar?.progress =
                                            circularProgressBar?.progress?.plus(1f)
                                                ?: 0f
                                    }
                                    .takeUntil { aLong -> aLong == "100".toLong() } // 10 sec
                                    .doOnComplete {
                                        Toast.makeText(this, "Fake accept action", Toast.LENGTH_SHORT).show()
                                    }.subscribe()


                            } catch (e: Exception) {
                                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                            }
                        }, {

                        })
                )
            }
    }

    private fun registerOnlineSystem() {
        onlineRef.addValueEventListener(onlineValueEventListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        //todo 2 map
        mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //todo 5 design request layout
        initViews()

        //todo 3 map service current location and firebase(next my map service)
        init()

    }

    private fun initViews() {
        //todo 6 design request layout
        chip_decline = findViewById(R.id.chip_decline) as Chip
        layout_accept = findViewById(R.id.layout_accept) as CardView
        circularProgressBar = findViewById(R.id.circularProgressBar) as CircularProgressBar
        txt_estimate_time = findViewById(R.id.txt_estimate_time) as TextView
        txt_estimate_distance = findViewById(R.id.txt_estimate_distance) as TextView
    }

    private fun init() {
        iGoogleApi =
            RetrofitClient.intance?.create(IGoogleAPI::class.java) //todo 4 design request layout
        onlineRef = FirebaseDatabase.getInstance().getReference().child(".info/connected")
    }

    //todo 3 map service current location
    override fun onStart() {
        super.onStart()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
        EventBus.getDefault().register(this)
    }

    //todo 4 map service current location
    override fun onStop() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)

        //todo 7 design request layout
        compositeDisposable.clear()
        if (EventBus.getDefault().hasSubscriberForEvent(MapsActivity::class.java)) {
            EventBus.getDefault().removeStickyEvent(MapsActivity::class.java)
        }

        EventBus.getDefault().unregister(this)

        super.onStop()
    }

    //todo 5 map service current location
    override fun onSharedPreferenceChanged(p0: SharedPreferences?, p1: String?) {
        if (p1.equals(Common.KEY_REQUEST_LOCATION_UPDATE)) {
            setButtonState(p0!!.getBoolean(Common.KEY_REQUEST_LOCATION_UPDATE, false))
        }
    }

    //todo 6 map service current location
    private fun setButtonState(boolean: Boolean) {
        if (boolean) {
            remove_update_location.isEnabled = true
            request_update_location.isEnabled = false
        } else {
            remove_update_location.isEnabled = false
            request_update_location.isEnabled = true
        }
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        mMap = googleMap!!

        //todo 7 map service current location (finish)
        Dexter.withActivity(this)
            .withPermissions(
                Arrays.asList(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    android.Manifest.permission.FOREGROUND_SERVICE
                )
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(p0: MultiplePermissionsReport?) {
                    request_update_location.setOnClickListener {
                        mService?.requestLocationUpdates()

                        if (ActivityCompat.checkSelfPermission(
                                this@MapsActivity,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                                this@MapsActivity,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            //Location Permission already granted
                            Toast.makeText(
                                this@MapsActivity,
                                "Permission granted",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                            return@setOnClickListener
                        } else {
                            //Request Location Permission
                            checkLocationPermission()
                        }
                        mMap.isMyLocationEnabled = true
                        mMap.uiSettings.isMyLocationButtonEnabled = true
                        mMap.setOnMyLocationClickListener {
                            fusedLocationProviderClient?.lastLocation
                                ?.addOnFailureListener {
                                    Toast.makeText(
                                        this@MapsActivity,
                                        it.message,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                ?.addOnSuccessListener {
                                    val userLatLng = LatLng(it.latitude, it.longitude)
                                    mMap.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                            userLatLng, 18f
                                        )
                                    )
                                }
                            true
                        }

                        val locationButton = (mapFragment.requireView()
                            .findViewById<View>("1".toInt())
                            .parent as View).findViewById<View>("2".toInt())
                        val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                        params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                        params.bottomMargin = 50

                    }
                    remove_update_location.setOnClickListener {
                        mService?.removeLocationUpdates()
                    }

                    setButtonState(Common.requestingLocationUpdates(this@MapsActivity))
                    bindService(
                        Intent(
                            this@MapsActivity,
                            MyMapService::class.java
                        ),
                        mServiceConnection,
                        Context.BIND_AUTO_CREATE
                    )
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: MutableList<PermissionRequest>?,
                    p1: PermissionToken?
                ) {
                    TODO("Not yet implemented")
                }

            })
            .check()
    }

    private fun checkLocationPermission() {


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            if (!checkSinglePermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                !checkSinglePermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                !checkSinglePermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ||
                !checkSinglePermission(Manifest.permission.FOREGROUND_SERVICE)
            ) {
                val permList = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.FOREGROUND_SERVICE
                )
                requestPermissions(permList, MY_PERMISSIONS_REQUEST_LOCATION)
            }

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSinglePermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                checkSinglePermission(Manifest.permission.ACCESS_COARSE_LOCATION) &&
                checkSinglePermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) &&
                checkSinglePermission(Manifest.permission.FOREGROUND_SERVICE)
            ) return
            val permList = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE
            )
            requestPermissions(permList, MY_PERMISSIONS_REQUEST_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (checkSinglePermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) return
            AlertDialog.Builder(this)
                .setTitle("Location Permission Needed")
                .setMessage("This app needs the Location permission, please accept to use location functionality")
                .setPositiveButton(
                    "OK"
                ) { _, _ ->
                    //Prompt the user once explanation has been shown
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        MapsActivity.MY_PERMISSIONS_REQUEST_LOCATION
                    )
                }
                .create()
                .show()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MapsActivity.MY_PERMISSIONS_REQUEST_LOCATION
            )
        }
    }

    private fun Context.checkSinglePermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}