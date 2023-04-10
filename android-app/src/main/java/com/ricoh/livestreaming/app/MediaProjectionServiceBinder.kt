/*
 * Copyright 2021 Ricoh Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import org.slf4j.LoggerFactory

object MediaProjectionServiceBinder {
    private val LOGGER = LoggerFactory.getLogger(MediaProjectionServiceBinder::class.java)

    private var mService: MediaProjectionService? = null
    private lateinit var mCallback: ServiceConnection

    fun getService(): MediaProjectionService? {
        return mService
    }

    fun bindToService(context: Context, callback: ServiceConnection) {
        mCallback = callback
        val intent = Intent(context, MediaProjectionService::class.java)
        context.startService(intent)
        context.bindService(intent, serviceConnection, 0)
    }

    fun unbindFromService(context: Context) {
        context.unbindService(serviceConnection)
        context.stopService(Intent(context, MediaProjectionService::class.java))
        mService = null
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
            LOGGER.info("onServiceConnected()")
            mService = (binder as MediaProjectionService.MediaProjectionServiceBinder).getService()
            mCallback.onServiceConnected(componentName, binder)
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            LOGGER.info("onServiceDisconnected()")
            mCallback.onServiceDisconnected(componentName)
            mService = null
        }
    }

}
