package com.ricoh.livestreaming.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ricoh.livestreaming.*
import kotlinx.android.synthetic.main.activity_recv.*
import org.slf4j.LoggerFactory
import org.webrtc.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class RecvActivity : AppCompatActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(RecvActivity::class.java)
        private val LOCK = Object()
    }

    private val executor = Executors.newSingleThreadExecutor()

    private var mEgl: EglBase? = null

    private var mClient: Client? = null

    private var mRtcStatsLogger: RTCStatsLogger? = null

    private var mStatsTimer: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recv)

        mEgl = EglBase.create()
        val eglContext = mEgl!!.eglBaseContext as EglBase14.Context

        remote_view.init(eglContext, null)

        connect_button.setOnClickListener {
            if (mClient == null) {
                connect()
            } else {
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

    private fun connect() = executor.safeSubmit {
        var roomId = roomId.text.toString()

        val roomSpec = RoomSpec(Config.getRoomType())
        val accessToken = JwtAccessToken.createAccessToken(BuildConfig.CLIENT_SECRET, roomId, roomSpec)

        val eglContext = mEgl!!.eglBaseContext as EglBase14.Context
        mClient = Client(applicationContext, eglContext).apply {
            setEventListener(ClientListener())
        }
        val constraints = MediaStreamConstraints.Builder()
                .audio(true)
                .build()

        val stream = mClient!!.getUserMedia(constraints)
        val lsTracks = arrayListOf<LSTrack>()
        for (track in stream.audioTracks) {
            val trackOption = LSTrackOption.Builder()
                    .meta(mapOf("track_metadata" to "audio"))
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
            mClient!!.disconnect()
        }.get()
    }

    inner class ClientListener : Client.Listener {
        override fun onConnecting() {
            LOGGER.debug("Client#onConnecting")

            runOnUiThread {
                connect_button.isEnabled = false
                connect_button.text = getString(R.string.connecting)
            }
        }

        override fun onOpen() {
            LOGGER.debug("Client#onOpen")

            // == For WebRTC Internal Tracing Capture.
            // == "/sdcard/{LOGS_DIR}/{date}T{time}.log.json" will be created.
            //
            // if (!PeerConnectionFactory.startInternalTracingCapture(createLogFile().absolutePath + ".json")) {
            //     LOGGER.error("failed to start internal tracing capture")
            // }

            val file = createLogFile()
            LOGGER.info("create log file: ${file.path}")
            mRtcStatsLogger = RTCStatsLogger(file)

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

            // == For WebRTC Internal Tracing
            //
            // PeerConnectionFactory.stopInternalTracingCapture()

            mStatsTimer?.cancel()
            mStatsTimer = null

            synchronized(LOCK) {
                mRtcStatsLogger?.close()
                mRtcStatsLogger = null

                mClient?.setEventListener(null)
                mClient = null
            }

            runOnUiThread {
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
        }

        override fun onAddRemoteTrack(connectionId: String, stream: MediaStream, track: MediaStreamTrack, metadata: Map<String, Any>, muteType: MuteType) {
            LOGGER.debug("Client#onAddRemoteTrack({} {}, {}, {})", connectionId, stream.id, track.id(), muteType)

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            if (track is VideoTrack) {
                track.addSink(remote_view)
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
