/*
 * Copyright 2020 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.app

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import com.ricoh.livestreaming.*
import com.ricoh.livestreaming.app.databinding.ActivityUvcCameraBinding
import com.ricoh.livestreaming.theta.ThetaVideoEncoderFactory
import com.ricoh.livestreaming.uvc.*
import com.ricoh.livestreaming.webrtc.CodecUtils
import org.slf4j.LoggerFactory
import org.webrtc.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class UvcCameraActivity : AppCompatActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(UvcCameraActivity::class.java)
        private val LOCK = Object()

        private enum class UvcControlDialogInfo(
            val controlId: UvcControlId,
            val autoControlId: UvcControlId?,
            val layoutResourceId: Int,
            val labelResourceId: Int?,
            val valueResourceId: Int?,
            val seekBarResourceId: Int?,
            val autoResourceId: Int?
        ) {
            BACKLIGHT_COMPENSATION(
                ProcessingUnitUvcControlId.BACKLIGHT_COMPENSATION,
                null,
                R.id.backlight_compensation_layout,
                R.id.textView_label_backlight_compensation,
                R.id.textView_value_backlight_compensation,
                R.id.seekBar_backlight_compensation,
                null
            ),
            BRIGHTNESS(
                ProcessingUnitUvcControlId.BRIGHTNESS,
                null,
                R.id.brightness_layout,
                R.id.textView_label_brightness,
                R.id.textView_value_brightness,
                R.id.seekBar_brightness,
                null
            ),
            CONTRAST(
                ProcessingUnitUvcControlId.CONTRAST,
                null,
                R.id.contrast_layout,
                R.id.textView_label_contrast,
                R.id.textView_value_contrast,
                R.id.seekBar_contrast,
                null
            ),
            SATURATION(
                ProcessingUnitUvcControlId.SATURATION,
                null,
                R.id.saturation_layout,
                R.id.textView_label_saturation,
                R.id.textView_value_saturation,
                R.id.seekBar_saturation,
                null
            ),
            SHARPNESS(
                ProcessingUnitUvcControlId.SHARPNESS,
                null,
                R.id.sharpness_layout,
                R.id.textView_label_sharpness,
                R.id.textView_value_sharpness,
                R.id.seekBar_sharpness,
                null
            ),
            GAMMA(
                ProcessingUnitUvcControlId.GAMMA,
                null,
                R.id.gamma_layout,
                R.id.textView_label_gamma,
                R.id.textView_value_gamma,
                R.id.seekBar_gamma,
                null
            ),
            GAIN(
                ProcessingUnitUvcControlId.GAIN,
                null,
                R.id.gain_layout,
                R.id.textView_label_gain,
                R.id.textView_value_gain,
                R.id.seekBar_gain,
                null
            ),
            HUE(
                ProcessingUnitUvcControlId.HUE,
                ProcessingUnitUvcControlId.HUE_AUTO,
                R.id.hue_layout,
                R.id.textView_label_hue,
                R.id.textView_value_hue,
                R.id.seekBar_hue,
                R.id.checkBox_hue_auto
            ),
            WHITE_BALANCE_TEMPERATURE(
                ProcessingUnitUvcControlId.WHITE_BALANCE_TEMPERATURE,
                ProcessingUnitUvcControlId.WHITE_BALANCE_TEMPERATURE_AUTO,
                R.id.whitebalancetemparature_layout,
                R.id.textView_label_whitebalancetemparature,
                R.id.textView_value_whitebalancetemparature,
                R.id.seekBar_whitebalancetemparature,
                R.id.checkBox_whitebalancetemparature_auto
            ),
            ZOOM_ABSOLUTE(
                CameraTerminalUvcControlId.ZOOM_ABSOLUTE,
                null,
                R.id.zoom_layout,
                R.id.textView_label_zoom,
                R.id.textView_value_zoom,
                R.id.seekBar_zoom,
                null
            ),
            FOCUS_ABSOLUTE(
                CameraTerminalUvcControlId.FOCUS_ABSOLUTE,
                CameraTerminalUvcControlId.FOCUS_AUTO,
                R.id.focus_layout,
                R.id.textView_label_focus,
                R.id.textView_value_focus,
                R.id.seekBar_focus,
                R.id.checkBox_focus_auto
            ),
            POWER_LINE_FREQUENCY(
                ProcessingUnitUvcControlId.POWER_LINE_FREQUENCY,
                null,
                R.id.powerlinefrequency_layout,
                R.id.textView_label_powerlinefrequency,
                null,
                null,
                null
            )
        }
    }

    private val executor = Executors.newSingleThreadExecutor()

    private var mEgl: EglBase? = null

    private var mClient: Client? = null

    private var mRtcStatsLogger: RTCStatsLogger? = null

    private var mVideoCapturer: UvcVideoCapturer? = null

    private var mStatsTimer: Timer? = null

    private var mAdapter: UvcFormatListAdapter? = null

    private val mCameraFormats = arrayListOf<UvcVideoCapturer.Format>()

    private var mHandler = Handler()

    private var mViewLayoutManager: ViewLayoutManager? = null

    /** View Binding */
    private lateinit var mActivityUvcCameraBinding: ActivityUvcCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mActivityUvcCameraBinding = ActivityUvcCameraBinding.inflate(layoutInflater)
        setContentView(mActivityUvcCameraBinding.root)

        supportActionBar!!.hide()
        mEgl = EglBase.create()
        mViewLayoutManager = ViewLayoutManager(
                applicationContext,
                window,
                mEgl,
                mActivityUvcCameraBinding.viewLayout,
                this.isKindOfSfu(Config.getRoomType()),
                object : ViewLayoutManager.Listener {
                    override fun onVideoReceiveCheckedChanged(connectionId: String, isChecked: Boolean) {
                        try {
                            val videoRequirement = if (isChecked) {
                                VideoRequirement.REQUIRED
                            } else {
                                VideoRequirement.UNREQUIRED
                            }
                            mClient?.changeMediaRequirements(connectionId, videoRequirement)
                        } catch (e: SDKError) {
                            LOGGER.error(e.toReportString())
                        }
                    }
                }
        )

        mActivityUvcCameraBinding.connectButton.setOnClickListener {
            if (mClient == null) {
                mActivityUvcCameraBinding.connectButton.text = getString(R.string.connecting)
                connect()
            } else {
                mActivityUvcCameraBinding.connectButton.text = getString(R.string.disconnecting)
                disconnect()
            }
        }

        mActivityUvcCameraBinding.roomId.setText(Config.getRoomId())
        mActivityUvcCameraBinding.connectButton.isEnabled = false
        mVideoCapturer = UvcVideoCapturer(applicationContext)
                .apply {
                    setEventListener(UvcVideoCapturerListener())
                }

        // Camera list Spinner
        mAdapter = UvcFormatListAdapter(this)
        mActivityUvcCameraBinding.capSpinner.adapter = mAdapter
        mActivityUvcCameraBinding.capSpinner.isEnabled = true

        mActivityUvcCameraBinding.cameraSettingButton.setOnClickListener {
            showCameraControlDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        mEgl?.release()
        mEgl = null
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (mClient?.state == Client.State.OPEN) {
            mActivityUvcCameraBinding.controlsLayout.visibility = View.VISIBLE

            mHandler.removeCallbacksAndMessages(null)
            mHandler.postDelayed({
                mActivityUvcCameraBinding.controlsLayout.visibility = View.GONE
            }, 3000)
        }

        return super.dispatchTouchEvent(ev)
    }


    private fun connect() = executor.safeSubmit {
        val roomId = mActivityUvcCameraBinding.roomId.text.toString()

        val videoBitrate = BuildConfig.VIDEO_BITRATE

        val format = mCameraFormats[mActivityUvcCameraBinding.capSpinner.selectedItemPosition]
        LOGGER.info("Try to connect. RoomType={} CameraFormat={}", Config.getRoomType(), format)
        mVideoCapturer?.setConfig(format)

        val roomSpec = RoomSpec(Config.getRoomType())
        val accessToken = JwtAccessToken.createAccessToken(BuildConfig.CLIENT_SECRET, roomId, roomSpec)

        val eglContext = mEgl!!.eglBaseContext as EglBase14.Context
        mClient = Client(
                applicationContext,
                eglContext,
                ThetaVideoEncoderFactory(
                        eglContext,
                        CodecUtils.getSupportedEncoderCodecInfo(applicationContext)
                )
        ).apply {
            setEventListener(ClientListener())
        }

        val constraints = MediaStreamConstraints.Builder()
                .videoCapturer(mVideoCapturer!!)
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
        for (track in stream.videoTracks) {
            val trackOption = LSTrackOption.Builder()
                    .meta(mapOf("track_metadata" to "video"))
                    .build()
            lsTracks.add(LSTrack(track, stream, trackOption))
        }

        val option = Option.Builder()
                .loggingSeverity(Config.getLoggingSeverity())
                .localLSTracks(lsTracks)
                .meta(mapOf("connect_meta" to "android"))
                .sending(SendingOption(
                        SendingVideoOption.Builder()
                                .videoCodecType(SendingVideoOption.VideoCodecType.H264)
                                .sendingPriority(SendingVideoOption.SendingPriority.HIGH)
                                .maxBitrateKbps(videoBitrate)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        mViewLayoutManager!!.onConfigurationChanged()
    }

    private fun showCameraControlDialog() {
        mVideoCapturer?.let { capturer ->
            val dialog = Dialog(this).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setContentView(R.layout.camera_control_dialog)
                window?.let {
                    it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    it.attributes.gravity = Gravity.TOP or Gravity.START
                }
                findViewById<Button>(R.id.button_close)?.setOnClickListener {
                    dismiss()
                }
                findViewById<Button>(R.id.button_default)?.setOnClickListener {
                    mVideoCapturer?.let { capturer ->
                        try {
                            resetUvcControls()
                            updateUvcControls(this, capturer)
                        } catch (e: Exception) {
                            LOGGER.error("Failed to reset controls", e)
                        }
                    }
                }

                updateUvcControls(this, capturer)
            }
            dialog.show()
        }
    }

    private fun updateUvcControls(dialog: Dialog, capturer: UvcVideoCapturer) {
        for (info in UvcControlDialogInfo.values()) {
            try {
                applyUvcControl(dialog, info, capturer)
            } catch (e: Exception) {
                LOGGER.error("Failed to applyUvcControl()", e)
            }
        }
    }

    private fun applyUvcControl(dialog: Dialog, info: UvcControlDialogInfo, capturer: UvcVideoCapturer) {
        LOGGER.info("applyUvcControl(controlId={})", info.controlId)
        val layout = dialog.findViewById<LinearLayout>(info.layoutResourceId)
        val labelText = if (info.labelResourceId != null) {
            dialog.findViewById<TextView>(info.labelResourceId)
        } else {
            null
        }

        val control = capturer.getControl(info.controlId)
        if (control == null) {
            LOGGER.info("{} is not supported.", info.controlId)
            layout.visibility = View.GONE
        } else {
            layout.visibility = View.VISIBLE

            if (info == UvcControlDialogInfo.POWER_LINE_FREQUENCY) {
                val radioGroupFreq = dialog.findViewById<RadioGroup>(R.id.radioGroup_powerlinefrequency)
                if (control.isSetCurrentValueSupported &&
                    control.isGetCurrentValueSupported &&
                    !control.isAutoUpdate) {
                    control.receiveCurrentValue()

                    when (control.currentValue.toInt()) {
                        0 -> radioGroupFreq.check(R.id.radioButton_freq_disabled)
                        1 -> radioGroupFreq.check(R.id.radioButton_freq_50hz)
                        2 -> radioGroupFreq.check(R.id.radioButton_freq_60hz)
                    }
                    radioGroupFreq
                        .setOnCheckedChangeListener { _, checkedId ->
                            var c = 0
                            when (checkedId) {
                                R.id.radioButton_freq_disabled -> c = 0
                                R.id.radioButton_freq_50hz -> c = 1
                                R.id.radioButton_freq_60hz -> c = 2
                            }
                            try {
                                control.sendCurrentValue(c.toLong())
                            } catch (e: Exception) {
                                LOGGER.error("Failed to sendCurrentValue()", e)
                            }
                        }

                } else {
                    labelText?.isEnabled = false
                    radioGroupFreq.isEnabled = false
                }
            } else {
                val valueText = if (info.valueResourceId != null) {
                    dialog.findViewById<TextView>(info.valueResourceId)
                } else {
                    null
                }
                val autoCheckBox = if (info.autoResourceId != null) {
                    dialog.findViewById<CheckBox>(info.autoResourceId)
                } else {
                    null
                }
                val seekBar = if (info.seekBarResourceId != null) {
                    dialog.findViewById<SeekBar>(info.seekBarResourceId)
                } else {
                    null
                }

                val autoControl = if (info.autoControlId != null) {
                    capturer.getControl(info.autoControlId)
                } else {
                    null
                }
                autoControl?.receiveCurrentValue()

                if (seekBar != null) {
                    seekBar.max = (control.maxValue - control.minValue).toInt()
                    if ((autoControl == null && control.isAutoUpdate) ||
                        (autoControl != null && autoControl.currentValue != 0L)
                    ) {
                        seekBar.isEnabled = false
                        seekBar.progress = 0
                        if (autoControl == null) {
                            labelText?.isEnabled = false
                        }
                        valueText?.setText(R.string.value_auto)
                    } else if (!control.isSetCurrentValueSupported) {
                        seekBar.isEnabled = false
                        if (control.isGetCurrentValueSupported) {
                            seekBar.progress = ctrlToSeek(control)
                            // Read Only
                            labelText?.isEnabled = false
                            valueText?.setText(R.string.value_readonly)
                        } else {
                            seekBar.progress = 0
                            // Invisible
                            labelText?.isEnabled = false
                            valueText?.setText(R.string.value_invisible)
                        }
                    } else if (!control.isGetCurrentValueSupported) {
                        seekBar.progress = 0
                        // Invisible
                        labelText?.isEnabled = false
                        valueText?.setText(R.string.value_invisible)
                    } else {
                        seekBar.isEnabled = true
                        control.receiveCurrentValue()
                        seekBar.progress = ctrlToSeek(control)
                        valueText?.also { textView ->
                            textView.text = getString(R.string.control_value, control.currentValue)
                            seekBar.setOnSeekBarChangeListener(
                                UvcSeekBarChangeListener(
                                    control,
                                    valueText
                                )
                            )
                        }
                    }
                }

                autoCheckBox?.let { checkBox ->
                    if (autoControl != null &&
                        autoControl.isSetCurrentValueSupported &&
                        autoControl.isGetCurrentValueSupported
                    ) {
                        checkBox.isEnabled = true
                        checkBox.isChecked = autoControl.currentValue != 0L
                        checkBox.setOnClickListener {
                            try {
                                autoControl.sendCurrentValue(if (checkBox.isChecked) 1 else 0.toLong())
                                applyUvcControl(dialog, info, capturer)
                            } catch (e: Exception) {
                                LOGGER.error("Failed to sendCurrentValue()", e)
                            }
                        }
                    } else {
                        checkBox.isEnabled = false
                        if (autoControl != null) {
                            checkBox.isChecked = autoControl.currentValue != 0L
                        }
                    }
                }
            }
        }
    }

    private fun seekToCtrl(ctrl: UvcControl, v: Long): Long {
        return v / ctrl.resolution * ctrl.resolution + ctrl.minValue
    }

    private fun ctrlToSeek(ctrl: UvcControl): Int {
        return (ctrl.currentValue - ctrl.minValue).toInt()
    }

    private inner class UvcSeekBarChangeListener constructor(
        ctrl: UvcControl,
        tv: TextView
    ) :
        OnSeekBarChangeListener {
        private val ctrl: UvcControl
        private val tv: TextView

        init {
            this.ctrl = ctrl
            this.tv = tv
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {}
        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        override fun onProgressChanged(
            seekBar: SeekBar, progress: Int,
            fromUser: Boolean
        ) {
            if (!fromUser) {
                return
            }
            val c: Long = seekToCtrl(ctrl, progress.toLong())
            if (c != ctrl.currentValue) {
                try {
                    ctrl.sendCurrentValue(c)
                } catch (e: Exception) {
                    LOGGER.error("Failed to sendCurrentValue.", e)
                    return
                }
                seekBar.progress = ctrlToSeek(ctrl)
                tv.text = getString(R.string.control_value, ctrl.currentValue)
            }
        }
    }

    private fun resetUvcControls() {
        mVideoCapturer?.let { capturer ->
            for (info in UvcControlDialogInfo.values()) {
                val control = capturer.getControl(info.controlId)
                val autoControl = if (info.autoControlId != null) {
                    capturer.getControl(info.autoControlId)
                } else {
                    null
                }

                if (control != null) {
                    autoControl?.receiveCurrentValue()

                    if (control.isSetCurrentValueSupported &&
                        !control.isDisabledDueToAutomaticMode ||
                        (autoControl != null && autoControl.currentValue == 0L)) {
                        control.sendCurrentValue(control.defaultValue)
                    }
                }

                if (autoControl != null && autoControl.isSetCurrentValueSupported) {
                    autoControl.sendCurrentValue(autoControl.defaultValue)
                }
            }
        }
    }

    inner class ClientListener : Client.Listener {

        override fun onConnecting(event: LSConnectingEvent) {
            LOGGER.debug("Client#onConnecting")

            runOnUiThread {
                mActivityUvcCameraBinding.connectButton.isEnabled = false
                mActivityUvcCameraBinding.connectButton.text = getString(R.string.connecting)
                mActivityUvcCameraBinding.capSpinner.isEnabled = false
            }
        }

        override fun onOpen(event: LSOpenEvent) {
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
            mVideoCapturer?.start()

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
                mActivityUvcCameraBinding.connectButton.text = getString(R.string.disconnect)
                mActivityUvcCameraBinding.connectButton.isEnabled = true
                mActivityUvcCameraBinding.cameraSettingButton.visibility = View.VISIBLE
                mHandler.postDelayed({
                    mActivityUvcCameraBinding.controlsLayout.visibility = View.GONE
                }, 3000)
            }
        }

        override fun onClosing(event: LSClosingEvent) {
            LOGGER.debug("Client#onClosing")

            runOnUiThread {
                mActivityUvcCameraBinding.connectButton.isEnabled = false
                mActivityUvcCameraBinding.connectButton.text = getString(R.string.disconnecting)
            }
        }

        override fun onClosed(event: LSClosedEvent) {
            LOGGER.debug("Client#onClosed")

            // == For WebRTC Internal Tracing
            //
            // PeerConnectionFactory.stopInternalTracingCapture()

            mStatsTimer?.cancel()
            mStatsTimer = null

            synchronized(LOCK) {
                mRtcStatsLogger?.close()
                mRtcStatsLogger = null

                mVideoCapturer?.stop()
                mVideoCapturer?.release()

                mClient?.setEventListener(null)
                mClient = null

                mVideoCapturer = UvcVideoCapturer(applicationContext)
                        .apply {
                            setEventListener(UvcVideoCapturerListener())
                        }
            }

            runOnUiThread {
                mHandler.removeCallbacksAndMessages(null)
                mActivityUvcCameraBinding.controlsLayout.visibility = View.VISIBLE

                mActivityUvcCameraBinding.connectButton.text = getString(R.string.connect)
                mActivityUvcCameraBinding.connectButton.isEnabled = true
                mActivityUvcCameraBinding.capSpinner.isEnabled = true
                mActivityUvcCameraBinding.cameraSettingButton.visibility = View.GONE
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

    inner class UvcVideoCapturerListener : UvcVideoCapturer.Listener {
        override fun onDeviceOpened(formats: List<UvcVideoCapturer.Format>) {
            LOGGER.debug("UvcVideoCapturerListener#onDeviceOpened()")

            for (format in formats) {
                LOGGER.debug("device format={}", format)
            }

            runOnUiThread {
                mCameraFormats.clear()
                mCameraFormats.addAll(formats)
                mAdapter!!.clear()
                mAdapter!!.addAll(formats)

                mActivityUvcCameraBinding.connectButton.isEnabled = true
            }
        }

        override fun onDeviceDetached() {
            LOGGER.debug("UvcVideoCapturerListener#onDeviceDetached()")
            runOnUiThread {
                mCameraFormats.clear()
                mAdapter!!.clear()

                mActivityUvcCameraBinding.connectButton.isEnabled = false
            }
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

    private fun isKindOfSfu(roomType: RoomSpec.RoomType): Boolean {
        return roomType == RoomSpec.RoomType.SFU || roomType == RoomSpec.RoomType.SFU_LARGE
    }
}
