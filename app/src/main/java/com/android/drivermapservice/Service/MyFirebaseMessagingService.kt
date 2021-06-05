package com.android.drivermapservice.Service

import android.content.Intent
import android.util.EventLog
import android.util.Log
import com.android.drivermapservice.EventBus.DriverRequestReceived
import com.android.drivermapservice.MapsActivity
import com.android.drivermapservice.Utils.Common
import com.android.drivermapservice.Utils.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.greenrobot.eventbus.EventBus
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    //todo 2 notifications (next splashscreen)
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (FirebaseAuth.getInstance().currentUser != null) {
            UserUtils.updateToken(this, token)
        }
    }

    //todo 4 notifications (next manifest)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.e("message", "received")
        val data = remoteMessage.data
        val intent = Intent(this, MapsActivity::class.java)
        if (data != null) {
//          todo 3 design request layout (next map activity)
            if (data[Common.NOTIF_TITLE].equals(Common.REQUEST_DRIVER_TITLE)) {
                EventBus.getDefault().postSticky(
                    DriverRequestReceived(
                        data[Common.RIDER_KEY],
                        data[Common.PICKUP_LOCATION]
                    )
                )

                Common.showNotification(
                    this, Random.nextInt(),
                    data[Common.NOTIF_TITLE],
                    data[Common.NOTIF_BODY],
                    intent
                )
            } else {
                Common.showNotification(
                    this, Random.nextInt(),
                    data[Common.NOTIF_TITLE],
                    data[Common.NOTIF_BODY],
                    intent
                )
            }
        }
    }


}