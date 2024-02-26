/*
 * Copyright 2019 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ricoh.livestreaming.*
import com.ricoh.livestreaming.app.databinding.ActivityFileSenderBinding
import com.ricoh.livestreaming.webrtc.CompressedFileVideoCapturer
import org.slf4j.LoggerFactory
import org.webrtc.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class FileSenderActivity : AppCompatActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(FileSenderActivity::class.java)
        private val LOCK = Object()

        private const val READ_REQUEST_CODE: Int = 42
    }

    private val executor = Executors.newSingleThreadExecutor()

    private var mEgl: EglBase? = null

    private var mClient: Client? = null

    private var mRtcStatsLogger: RTCStatsLogger? = null

    private var mVideoFileUri: Uri? = null

    private var mCapturer: CompressedFileVideoCapturer? = null

    private var mStatsTimer: Timer? = null

    /** View Binding */
    private lateinit var mActivityFileSenderBinding: ActivityFileSenderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mActivityFileSenderBinding = ActivityFileSenderBinding.inflate(layoutInflater)
        setContentView(mActivityFileSenderBinding.root)

        mEgl = EglBase.create()
        val eglContext = mEgl!!.eglBaseContext as EglBase14.Context

        mActivityFileSenderBinding.localView.init(eglContext, null)

        mActivityFileSenderBinding.pickButton.setOnClickListener {
            mActivityFileSenderBinding.pickButton.isEnabled = false
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
            }, READ_REQUEST_CODE)
        }

        mActivityFileSenderBinding.connectButton.setOnClickListener {
            mActivityFileSenderBinding.connectButton.isEnabled = false
            if (mClient == null) {
                mActivityFileSenderBinding.connectButton.text = getString(R.string.connecting)
                connect()
            } else {
                mActivityFileSenderBinding.connectButton.text = getString(R.string.disconnecting)
                disconnect()
            }
        }

        mActivityFileSenderBinding.roomId.setText(Config.getRoomId())
    }

    override fun onDestroy() {
        super.onDestroy()

        mEgl?.release()
        mEgl = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data.also { uri ->
                mVideoFileUri = uri
                mActivityFileSenderBinding.videoFilePath.text = uri.toString()
                mActivityFileSenderBinding.connectButton.isEnabled = true
            }
        }
    }

    private fun connect() = executor.safeSubmit {
        val roomId = mActivityFileSenderBinding.roomId.text.toString()

        mCapturer = CompressedFileVideoCapturer(applicationContext, mVideoFileUri!!)
        val roomSpec = RoomSpec(Config.getRoomType())
        val accessToken = JwtAccessToken.createAccessToken(BuildConfig.CLIENT_SECRET, roomId, roomSpec)

        val eglContext = mEgl!!.eglBaseContext as EglBase14.Context
        mClient = Client(applicationContext, eglContext).apply {
            setEventListener(ClientListener())
        }
        val constraints = MediaStreamConstraints.Builder()
                .videoCapturer(mCapturer!!)
                .build()
        val stream = mClient!!.getUserMedia(constraints)
        val lsTracks = arrayListOf<LSTrack>()
        for (track in stream.videoTracks) {
            val trackOption = LSTrackOption.Builder()
                    .meta(mapOf("track_metadata" to "video"))
                    .build()
            lsTracks.add(LSTrack(track, stream, trackOption))
        }

        val option = Option.Builder()
                .loggingSeverity(Config.getLoggingSeverity())
                .localLSTracks(lsTracks)
                .meta(mapOf("connect_metadata" to "android"))
                .sending(SendingOption(
                        SendingVideoOption.Builder()
                                .videoCodecType(SendingVideoOption.VideoCodecType.H264)
                                .sendingPriority(SendingVideoOption.SendingPriority.NORMAL)
                                .maxBitrateKbps(BuildConfig.VIDEO_BITRATE)
                                .build()))
                .iceServersProtocol(Config.getIceServersProtocol())
                .build()

        mClient!!.connect(
                BuildConfig.CLIENT_ID,
                accessToken,
                option)
    }

    private fun disconnect() = executor.safeSubmit {
        mClient!!.disconnect()
    }

    override fun onPause() {
        super.onPause()

        executor.safeSubmit {
            mClient?.disconnect()
        }.get()
    }

    inner class ClientListener : Client.Listener {
        override fun onConnecting(event: LSConnectingEvent) {
            LOGGER.debug("Client#onConnecting")

            runOnUiThread {
                mActivityFileSenderBinding.pickButton.isEnabled = false
                mActivityFileSenderBinding.connectButton.isEnabled = false
                mActivityFileSenderBinding.connectButton.text = getString(R.string.connecting)
            }
        }

        override fun onOpen(event: LSOpenEvent) {
            LOGGER.debug("Client#onOpen")

            val file = createLogFile()
            LOGGER.info("create log file: ${file.path}")
            mRtcStatsLogger = RTCStatsLogger(file)
            mCapturer?.start()

            mStatsTimer = Timer(true)
            mStatsTimer?.schedule(object : TimerTask() {
                override fun run() {
                    synchronized(LOCK) {
                        if (mClient != null) {
                            val stats = mClient!!.stats
                            for ((key, value) in stats) {
                                mRtcStatsLogger?.log(key, value)
                            }
                        }
                    }
                }
            }, 0, 1000)

            runOnUiThread {
                mActivityFileSenderBinding.connectButton.text = getString(R.string.disconnect)
                mActivityFileSenderBinding.connectButton.isEnabled = true
            }
        }

        override fun onClosing(event: LSClosingEvent) {
            LOGGER.debug("Client#onClosing")

            runOnUiThread {
                mActivityFileSenderBinding.connectButton.isEnabled = false
                mActivityFileSenderBinding.connectButton.text = getString(R.string.disconnecting)
            }
        }

        override fun onClosed(event: LSClosedEvent) {
            LOGGER.debug("Client#onClosed")

            mStatsTimer?.cancel()
            mStatsTimer = null

            synchronized(LOCK) {
                mRtcStatsLogger?.close()
                mRtcStatsLogger = null

                mCapturer?.stop()
                mCapturer?.release()

                mClient?.setEventListener(null)
                mClient = null
            }

            runOnUiThread {
                mActivityFileSenderBinding.pickButton.isEnabled = true
                mActivityFileSenderBinding.connectButton.text = getString(R.string.connect)
                mActivityFileSenderBinding.connectButton.isEnabled = true
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
        }

        override fun onAddLocalTrack(event: LSAddLocalTrackEvent) {
            val track = event.mediaStreamTrack
            LOGGER.debug("Client#onAddLocalTrack({})", track.id())
            if (track is VideoTrack) {
                track.addSink(mActivityFileSenderBinding.localView)
            }
        }

        override fun onAddRemoteTrack(event: LSAddRemoteTrackEvent) {
            LOGGER.debug("Client#onAddRemoteTrack({} {}, {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id(), event.mute)

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
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
            LOGGER.error("Client#onError({})", error.toReportString())
        }
    }

    private fun ExecutorService.safeSubmit(action: () -> Unit): Future<*> {
        return submit {
            try {
                action()
            } catch (e: Exception) {
                LOGGER.error("Uncaught Exception in Executor", e)
            }
        }
    }
}
