package com.android.drivermapservice.Utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.drivermapservice.Model.DriverInfoModel
import com.android.drivermapservice.R
import java.text.DateFormat
import java.util.*


object Common {

    val TOKEN_REFERENCE: String = "Token"
    val DRIVER_INFO_REFERENCE: String = "DriverInfo"
    val DRIVER_LOCATION_REFERENCE: String = "DriverLocations"
    var currentUser: DriverInfoModel? = null
    val KEY_REQUEST_LOCATION_UPDATE = "requesting_location_update"
    val NOTIF_BODY: String = "body"
    val NOTIF_TITLE: String= "title"

    fun getLocationText(mLocation: Location?): String {
        return if (mLocation == null) {
            "Uknown Location"
        } else {
            "" + mLocation.latitude + "/" + mLocation.longitude
        }

    }

    fun getLocationTitle(context: Context): String {

        return String.format("Location Updated : ${DateFormat.getDateInstance().format(Date())}")

    }

    fun setRequestingLocationUpdates(context: Context, value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(KEY_REQUEST_LOCATION_UPDATE, value)
            .apply()
    }

    fun requestingLocationUpdates(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(KEY_REQUEST_LOCATION_UPDATE, false)
    }

    fun showNotification(
        context: Context,
        id: Int,
        title: String?,
        body: String?,
        intent: Intent?
    ) {
        var pendingIntent: PendingIntent? = null
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (intent != null) {
            pendingIntent = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_ONE_SHOT)
            val NOTIFICATION_CHANNEL_ID = "uber"
            val notoficationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notoficationChannel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Uber Saya",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notoficationChannel.description = "description"
                notoficationChannel.enableLights(true)
                notoficationChannel.lightColor = Color.RED
                notoficationChannel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                notoficationChannel.enableVibration(true)
                notoficationManager.createNotificationChannel(notoficationChannel)
            }

            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_SOUND)
                .setSmallIcon(R.drawable.ic_baseline_directions_car_24)
                .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_baseline_directions_car_24))

            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent)
                val notafication = builder.build()
                notoficationManager.notify(id, notafication)
            }
        }else{
            Log.e("intent","ada")
        }
    }
}