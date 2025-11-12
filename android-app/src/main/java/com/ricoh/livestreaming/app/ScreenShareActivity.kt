/*
 * Copyright 2019 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.view.View.VISIBLE
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ricoh.livestreaming.*
import com.ricoh.livestreaming.app.databinding.ActivityScreenShareBinding
import org.slf4j.LoggerFactory
import org.webrtc.*
import java.lang.IllegalStateException

class ScreenShareActivity : AppCompatActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ScreenShareActivity::class.java)
        private const val CAPTURE_PERMISSION_REQUEST_CODE = 1
    }

    private var mViewLayoutManager: ViewLayoutManager? = null
    
    /** View Binding */
    private lateinit var mActivityScreenShareBinding: ActivityScreenShareBinding

    private val clientListener = ClientListener()
    private var mediaProjectionIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mActivityScreenShareBinding = ActivityScreenShareBinding.inflate(layoutInflater)
        setContentView(mActivityScreenShareBinding.root)

        supportActionBar!!.hide()

        mActivityScreenShareBinding.connectButton.setOnClickListener {
            if (MediaProjectionServiceBinder.getService() == null ||
                MediaProjectionServiceBinder.getService()!!.getClientState() == Client.State.CLOSED) {
                val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                screenCaptureStartForResult.launch(mediaProjectionManager.createScreenCaptureIntent())
            } else {
                mActivityScreenShareBinding.connectButton.text = getString(R.string.disconnecting)
                MediaProjectionServiceBinder.getService()?.disconnect()
            }
        }

        mActivityScreenShareBinding.roomId.setText(Config.getRoomId())
        MediaProjectionServiceBinder.getService()?.let {
            // Serviceが既に起動している
            mViewLayoutManager = createViewLayoutManager(it)
        } ?:run {
            // Serviceが起動していない場合はService起動後にViewLayoutManagerを生成する
        }
    }

    private val screenCaptureStartForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            LOGGER.info("Screen capture permission request result. code={}, data={}.",
                result.resultCode, result.data)
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    mActivityScreenShareBinding.connectButton.text = getString(R.string.connecting)
                    MediaProjectionServiceBinder.getService()?.connect(
                        mActivityScreenShareBinding.roomId.text.toString(),
                        intent
                    ) ?:run {
                        // Serviceが起動していない場合はService起動後に接続する
                        mediaProjectionIntent = intent
                        // Serviceを起動する
                        MediaProjectionServiceBinder.bindToService(applicationContext, serviceConnection)
                    }
                }
            }
        }

    override fun onDestroy() {
        super.onDestroy()

        if (isFinishing) {
            MediaProjectionServiceBinder.unbindFromService(applicationContext)
        }
    }

    override fun onStart() {
        super.onStart()
        MediaProjectionServiceBinder.getService()?.let { service ->
            service.setClientListener(clientListener)

            // バックグランド中にClientのStateが変化することがあるのでconnectButtonに現在の状態を反映させる
            when (service.getClientState()) {
                Client.State.CONNECTING -> {
                    mActivityScreenShareBinding.connectButton.isEnabled = false
                    mActivityScreenShareBinding.connectButton.text = getString(R.string.connecting)
                }
                Client.State.OPEN -> {
                    mActivityScreenShareBinding.connectButton.isEnabled = true
                    mActivityScreenShareBinding.connectButton.text = getString(R.string.disconnect)
                }
                Client.State.CLOSING -> {
                    mActivityScreenShareBinding.connectButton.isEnabled = false
                    mActivityScreenShareBinding.connectButton.text = getString(R.string.disconnecting)
                }
                Client.State.IDLE, Client.State.CLOSED -> {
                    mActivityScreenShareBinding.connectButton.isEnabled = true
                    mActivityScreenShareBinding.connectButton.text = getString(R.string.connect)
                }
            }

            // バックグランド中にルームに参加、退出することがあるので最新の情報でLayoutを作り直す
            mViewLayoutManager?.let { manager ->
                service.getLocalVideoTrack()?.let {
                    manager.addLocalTrack(it)
                }
                for ((k, v) in service.getRemoteVideoTracks()) {
                    manager.addRemoteTrack(k, v.first, v.second == VideoRequirement.REQUIRED)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        LOGGER.debug("onStop()")
        MediaProjectionServiceBinder.getService()?.setClientListener(null)

        // バックラウンド中は描画を止める
        mViewLayoutManager?.let {
            MediaProjectionServiceBinder.getService()?.getLocalVideoTrack()?.removeSink(it.getLocalView())
            it.clear()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mViewLayoutManager?.onConfigurationChanged()
    }

    private fun createViewLayoutManager(service: MediaProjectionService): ViewLayoutManager {
        return ViewLayoutManager(
                applicationContext,
                window,
                service.getEglBase(),
                mActivityScreenShareBinding.viewLayout,
                this.isKindOfSfu(Config.getRoomType()),
                object : ViewLayoutManager.Listener {
                    override fun onVideoReceiveCheckedChanged(connectionId: String, isChecked: Boolean) {
                        try {
                            val videoRequirement = if (isChecked) {
                                VideoRequirement.REQUIRED
                            } else {
                                VideoRequirement.UNREQUIRED
                            }
                            service.changeMediaRequirements(connectionId, videoRequirement)
                        } catch (e: SDKError) {
                            LOGGER.error(e.toReportString())
                        }
                    }
                })
    }

    inner class ClientListener : Client.Listener {

        override fun onConnecting(event: LSConnectingEvent) {
            LOGGER.debug("Client#onConnecting")

            runOnUiThread {
                mActivityScreenShareBinding.connectButton.isEnabled = false
                mActivityScreenShareBinding.connectButton.text = getString(R.string.connecting)
            }
        }

        override fun onOpen(event: LSOpenEvent) {
            LOGGER.debug("Client#onOpen")

            runOnUiThread {
                mActivityScreenShareBinding.connectButton.text = getString(R.string.disconnect)
                mActivityScreenShareBinding.connectButton.isEnabled = true
            }
        }

        override fun onClosing(event: LSClosingEvent) {
            LOGGER.debug("Client#onClosing")

            runOnUiThread {
                mActivityScreenShareBinding.connectButton.isEnabled = false
                mActivityScreenShareBinding.connectButton.text = getString(R.string.disconnecting)
            }
        }

        override fun onClosed(event: LSClosedEvent) {
            LOGGER.debug("Client#onClosed")

            runOnUiThread {
                mActivityScreenShareBinding.connectButton.text = getString(R.string.connect)
                mActivityScreenShareBinding.connectButton.isEnabled = true
                mViewLayoutManager!!.clear()
            }
        }

        override fun onAddLocalTrack(event: LSAddLocalTrackEvent) {
            val track = event.mediaStreamTrack
            LOGGER.debug("Client#onAddLocalTrack({})", track.id())

            if (track is VideoTrack) {
                runOnUiThread {
                    mViewLayoutManager!!.addLocalTrack(track)
                }
            }
        }

        override fun onAddRemoteConnection(event: LSAddRemoteConnectionEvent) {
            LOGGER.debug("Client#onAddRemoteConnection(connectionId = ${event.connectionId})")

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onRemoveRemoteConnection(event: LSRemoveRemoteConnectionEvent) {
            LOGGER.debug("Client#onRemoveRemoteConnection(connectionId = ${event.connectionId})")

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            for (mediaStreamTrack in event.mediaStreamTracks) {
                LOGGER.debug("mediaStreamTrack={}", mediaStreamTrack)
            }

            runOnUiThread {
                mViewLayoutManager!!.removeRemoteTrack(event.connectionId)
            }
        }

        override fun onAddRemoteTrack(event: LSAddRemoteTrackEvent) {
            val track = event.mediaStreamTrack
            LOGGER.debug("Client#onAddRemoteTrack({} {}, {}, {})", event.connectionId, event.stream.id, track.id(), event.mute)

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            if (track is VideoTrack) {
                runOnUiThread {
                    mViewLayoutManager!!.addRemoteTrack(event.connectionId, track)
                }
            }
        }

        override fun onUpdateRemoteConnection(event: LSUpdateRemoteConnectionEvent) {
            LOGGER.debug("Client#onUpdateRemoteConnection(connectionId = ${event.connectionId})")

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateRemoteTrack(event: LSUpdateRemoteTrackEvent) {
            LOGGER.debug("Client#onUpdateRemoteTrack({} {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id())

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateConnectionsStatus(event: LSUpdateConnectionsStatusEvent) {
            LOGGER.debug("Client#onUpdateConnectionsStatus receiver_existence = ${event.connectionsStatus.video.receiverExistence}")
        }

        override fun onUpdateMute(event: LSUpdateMuteEvent) {
            LOGGER.debug("Client#onUpdateMute({} {}, {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id(), event.mute)
        }

        override fun onChangeStability(event: LSChangeStabilityEvent) {
            LOGGER.debug("Client#onChangeStability({}, {})", event.connectionId, event.stability)
        }

        override fun onError(error: SDKErrorEvent) {
            LOGGER.error("Client#onError({}:{}:{}:{})", error.detail.type, error.detail.code, error.detail.error, error.toReportString())
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
            LOGGER.debug("onServiceConnected()")
            MediaProjectionServiceBinder.getService()?.let {
                mViewLayoutManager = createViewLayoutManager(it)
                it.setClientListener(clientListener)
                it.connect(
                    mActivityScreenShareBinding.roomId.text.toString(),
                    mediaProjectionIntent!!
                )
            }
        }

        override fun onServiceDisconnected(p0: ComponentName) {
            LOGGER.debug("onServiceDisconnected()")
        }
    }

    private fun isKindOfSfu(roomType: RoomSpec.RoomType): Boolean {
        return roomType == RoomSpec.RoomType.SFU || roomType == RoomSpec.RoomType.SFU_LARGE
    }
}
