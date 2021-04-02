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
import com.ricoh.livestreaming.webrtc.CompressedFileVideoCapturer
import kotlinx.android.synthetic.main.activity_file_sender.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_sender)

        mEgl = EglBase.create()
        val eglContext = mEgl!!.eglBaseContext as EglBase14.Context

        local_view.init(eglContext, null)

        pick_button.setOnClickListener {
            pick_button.isEnabled = false
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
            }, READ_REQUEST_CODE)
        }

        connect_button.setOnClickListener {
            connect_button.isEnabled = false
            if (mClient == null) {
                connect_button.text = getString(R.string.connecting)
                connect()
            } else {
                connect_button.text = getString(R.string.disconnecting)
                disconnect()
            }
        }

        roomId.setText(Config.getRoomId())
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
                video_file_path.text = uri.toString()
                connect_button.isEnabled = true
            }
        }
    }

    private fun connect() = executor.safeSubmit {
        var roomId = roomId.text.toString()

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
        override fun onConnecting() {
            LOGGER.debug("Client#onConnecting")

            runOnUiThread {
                pick_button.isEnabled = false
                connect_button.isEnabled = false
                connect_button.text = getString(R.string.connecting)
            }
        }

        override fun onOpen() {
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
                connect_button.text = getString(R.string.disconnect)
                connect_button.isEnabled = true
            }
        }

        override fun onClosing() {
            LOGGER.debug("Client#onClosing")

            runOnUiThread {
                connect_button.isEnabled = false
                connect_button.text = getString(R.string.disconnecting)
            }
        }

        override fun onClosed() {
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
                pick_button.isEnabled = true
                connect_button.text = getString(R.string.connect)
                connect_button.isEnabled = true
            }
        }

        override fun onAddRemoteConnection(connectionId: String, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onAddRemoteConnection(connectionId = ${connectionId})")

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onRemoveRemoteConnection(connectionId: String, metadata: Map<String, Any>, mediaStreamTracks: List<MediaStreamTrack>) {
            LOGGER.debug("Client#onRemoveRemoteConnection(connectionId = ${connectionId})")

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            for (mediaStreamTrack in mediaStreamTracks) {
                LOGGER.debug("mediaStreamTrack={}", mediaStreamTrack)
            }
        }

        override fun onAddLocalTrack(track: MediaStreamTrack, stream: MediaStream) {
            LOGGER.debug("Client#onAddLocalTrack({})", track.id())
            if (track is VideoTrack) {
                track.addSink(local_view)
            }
        }

        override fun onAddRemoteTrack(connectionId: String, stream: MediaStream, track: MediaStreamTrack, metadata: Map<String, Any>, muteType: MuteType) {
            LOGGER.debug("Client#onAddRemoteTrack({} {}, {}, {})", connectionId, stream.id, track.id(), muteType)

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateRemoteConnection(connectionId: String, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onUpdateRemoteConnection(connectionId = ${connectionId})")

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateRemoteTrack(connectionId: String, stream: MediaStream, track: MediaStreamTrack, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onUpdateRemoteTrack({} {}, {})", connectionId, stream.id, track.id())

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateMute(connectionId: String, stream: MediaStream, track: MediaStreamTrack, muteType: MuteType) {
            LOGGER.debug("Client#onUpdateMute({} {}, {}, {})", connectionId, stream.id, track.id(), muteType)
        }

        override fun onChangeStability(connectionId: String, stability: Stability) {
            LOGGER.debug("Client#onChangeStability({}, {})", connectionId, stability)
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
