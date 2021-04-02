/*
 * Copyright 2019 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1813480588
        private val REQUIRED_PERMISSIONS = listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        start_bidir_activity.setOnClickListener {
            this.startChildActivity(BidirActivity::class.java)
        }

        start_recv_activity.setOnClickListener {
            this.startChildActivity(RecvActivity::class.java)
        }

        start_file_sender_activity.setOnClickListener {
            this.startChildActivity(FileSenderActivity::class.java)
        }

        start_uvc_camera_activity.setOnClickListener {
            this.startChildActivity(UvcCameraActivity::class.java)
        }

        // Load configurations.
        Config.load(this.applicationContext)

        // Save button
        save_config.setOnClickListener {
            Config.setRoomId(this@MainActivity.applicationContext, room_id.text.toString())
            layout_guide.visibility = View.INVISIBLE
        }

        // Room ID text box
        room_id.setText(Config.getRoomId())
        room_id.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString() != Config.getRoomId()) layout_guide.visibility = View.VISIBLE
                else layout_guide.visibility = View.INVISIBLE
            }
        })

        // Log level spinner
        log_level.setSelection(Config.getSelectedLoggingSeverity())
        log_level.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Config.setLoggingSeverity(this@MainActivity.applicationContext, log_level.selectedItem.toString())
            }
        }

        // Room Type
        room_type_radio.check(Config.getSelectedRoomTypeID())
        room_type_radio.setOnCheckedChangeListener { _, checkedId ->
            Config.setRoomType(applicationContext, checkedId)
        }
    }

    override fun onResume() {
        super.onResume()

        val notGrantedPermissions = REQUIRED_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (notGrantedPermissions.isNotEmpty()) {
            requestPermissions(notGrantedPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = REQUIRED_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (deniedPermissions.isNotEmpty()) {
                Toast.makeText(applicationContext, "Please grant all permission.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startChildActivity(cls: Class<*>) {
        val currentRoomId = room_id.text.toString()
        when {
            // Room ID has been changed.
            currentRoomId != Config.getRoomId() -> {
                AlertDialog.Builder(this)
                        .setMessage(R.string.room_id_has_been_changed)
                        .setPositiveButton(android.R.string.yes) { _, _ ->
                            startActivity(Intent(applicationContext, cls))
                            finish()
                        }
                        .setNegativeButton(android.R.string.no) { dialog, _ ->
                            dialog.dismiss()
                        }.show()
            }
            // Saved Room ID is empty.
            Config.getRoomId().isEmpty() -> {
                AlertDialog.Builder(this)
                        .setMessage(R.string.room_id_is_empty)
                        .setPositiveButton(android.R.string.yes) { dialog, _ ->
                            dialog.dismiss()
                        }.show()
            }
            // Call child activity right away.
            else -> {
                startActivity(Intent(applicationContext, cls))
                finish()
            }
        }
    }

}
