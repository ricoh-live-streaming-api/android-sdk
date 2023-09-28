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
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ricoh.livestreaming.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1813480588
        private val REQUIRED_PERMISSIONS = listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        )
    }
    
    /** View Binding */
    private lateinit var mActivityMainBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        mActivityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mActivityMainBinding.root)

        mActivityMainBinding.startBidirActivity.setOnClickListener {
            this.startChildActivity(BidirActivity::class.java)
        }

        mActivityMainBinding.startRecvActivity.setOnClickListener {
            this.startChildActivity(RecvActivity::class.java)
        }

        mActivityMainBinding.startFileSenderActivity.setOnClickListener {
            this.startChildActivity(FileSenderActivity::class.java)
        }

        mActivityMainBinding.startUvcCameraActivity.setOnClickListener {
            this.startChildActivity(UvcCameraActivity::class.java)
        }

        mActivityMainBinding.startScreenShareCameraActivity.setOnClickListener {
            this.startChildActivity(ScreenShareActivity::class.java)
        }

        // Load configurations.
        Config.load(this.applicationContext)

        // Save button
        mActivityMainBinding.saveConfig.setOnClickListener {
            Config.setRoomId(this@MainActivity.applicationContext, mActivityMainBinding.roomId.text.toString())
            mActivityMainBinding.layoutGuide.visibility = View.INVISIBLE
        }

        // Room ID text box
        mActivityMainBinding.roomId.setText(Config.getRoomId())
        mActivityMainBinding.roomId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString() != Config.getRoomId()) mActivityMainBinding.layoutGuide.visibility = View.VISIBLE
                else mActivityMainBinding.layoutGuide.visibility = View.INVISIBLE
            }
        })

        // Log level spinner
        mActivityMainBinding.logLevel.setSelection(Config.getSelectedLoggingSeverity())
        mActivityMainBinding.logLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Config.setLoggingSeverity(this@MainActivity.applicationContext, mActivityMainBinding.logLevel.selectedItem.toString())
            }
        }

        // Room Type
        mActivityMainBinding.roomType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, RoomSpec.RoomType.values())
        mActivityMainBinding.roomType.setSelection(Config.getSelectedRoomType())
        mActivityMainBinding.roomType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Config.setRoomType(this@MainActivity.applicationContext, position, mActivityMainBinding.roomType.selectedItem as RoomSpec.RoomType)
            }
        }

        // ICE Servers protocol
        mActivityMainBinding.iceServersProtocol.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, com.ricoh.livestreaming.IceServersProtocol.values())
        mActivityMainBinding.iceServersProtocol.setSelection(Config.getSelectedIceServersProtocol())
        mActivityMainBinding.iceServersProtocol.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Config.setIceServersProtocol(this@MainActivity.applicationContext, position, mActivityMainBinding.iceServersProtocol.selectedItem as com.ricoh.livestreaming.IceServersProtocol)
            }
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
        val currentRoomId = mActivityMainBinding.roomId.text.toString()
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
