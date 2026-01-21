/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os.cts.companiondevicetestapp

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.CALL_PHONE
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.companion.AssociationRequest
import android.companion.AssociationRequest.DEVICE_PROFILE_FITNESS_TRACKER
import android.companion.AssociationRequest.DEVICE_PROFILE_GLASSES
import android.companion.AssociationRequest.DEVICE_PROFILE_MEDICAL
import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.companion.AssociationRequest.PERMISSION_GROUP_NEARBY
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.os.Process
import android.text.format.DateFormat
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.LinearLayout.VERTICAL
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.regex.Pattern

class CompanionDeviceTestAppActivity : Activity() {

    val associationStatus by lazy { TextView(this) }
    val permissionStatus by lazy { TextView(this) }
    val notificationsStatus by lazy { TextView(this) }
    val bypassStatus by lazy { TextView(this) }
    val associateNumber by lazy { TextView(this) }

    val nameFilter by lazy { EditText(this).apply {
        hint = "Name Filter"
        contentDescription = "name filter" // Do not change: used in the tests.
    } }
    val singleCheckbox by lazy { CheckBox(this).apply { text = "Single Device" } }
    val watchCheckbox by lazy { CheckBox(this).apply { text = "Watch" } }
    val fitnessCheckbox by lazy { CheckBox(this).apply { text = "Fitness Tracker" } }
    val glassesCheckbox by lazy { CheckBox(this).apply { text = "Glasses" } }
    val medicalCheckbox by lazy { CheckBox(this).apply { text = "Medical" } }
    val nearbyCheckbox by lazy { CheckBox(this).apply { text = "Nearby Devices" } }
    val aiAgentCheckbox by lazy { CheckBox(this).apply { text = "Remote AI Agent Support" } }

    val associationListLabel by lazy { TextView(this).apply { text = "Association List:" } }
    val associationListRadioGroup by lazy { RadioGroup(this).apply {
        orientation = VERTICAL
    }}
    val associationListScrollView by lazy { ScrollView(this).apply {
        addView(associationListRadioGroup)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            300
        )
    }}

    val cdm: CompanionDeviceManager by lazy { val java = CompanionDeviceManager::class.java
        getSystemService(java)!! }
    val bt: BluetoothAdapter by lazy { val java = BluetoothManager::class.java
        getSystemService(java)!!.adapter }

    var device: BluetoothDevice? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // This map will store the timestamp locally when an association happens.
    private val associationTimeMap = mutableMapOf<String, Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(LinearLayout(this).apply {
            orientation = VERTICAL

            addView(associationStatus)
            addView(permissionStatus)
            addView(notificationsStatus)
            addView(bypassStatus)
            addView(associateNumber)

            addView(Button(ctx).apply {
                text = "^^^ Refresh"
                setOnClickListener { refresh() }
            })

            addView(nameFilter)
            addView(singleCheckbox)
            addView(watchCheckbox)
            addView(glassesCheckbox)
            addView(fitnessCheckbox)
            addView(medicalCheckbox)
            addView(nearbyCheckbox)
            addView(aiAgentCheckbox)
            addView(associationListLabel)
            addView(associationListScrollView)

            addView(cdmButton("Associate") {
                if (singleCheckbox.isChecked) {
                    setSingleDevice(true)
                }
                if (watchCheckbox.isChecked) {
                    setDeviceProfile(DEVICE_PROFILE_WATCH)
                }
                if (fitnessCheckbox.isChecked) {
                    setDeviceProfile(DEVICE_PROFILE_FITNESS_TRACKER)
                }
                if (glassesCheckbox.isChecked) {
                    setDeviceProfile(DEVICE_PROFILE_GLASSES)
                }
                if (medicalCheckbox.isChecked) {
                    setDeviceProfile(DEVICE_PROFILE_MEDICAL)
                }
                if (nearbyCheckbox.isChecked) {
                    setExtraPermissions(setOf(PERMISSION_GROUP_NEARBY))
                }
                if (aiAgentCheckbox.isChecked) {
                    setRemoteAiAgentSupported(true)
                }
                addDeviceFilter(BluetoothDeviceFilter.Builder().apply {
                    if (!nameFilter.text.isEmpty()) {
                        setNamePattern(Pattern.compile(".*${nameFilter.text}.*"))
                    }
                }.build())
            })

            addView(Button(ctx).apply {
                text = "Request notifications"
                setOnClickListener {
                    cdm.requestNotificationAccess(
                            ComponentName(ctx, NotificationListener::class.java)
                    )
                }
            })
            addView(Button(ctx).apply {
                text = "Disassociate"
                setOnClickListener {
                    val selectedId = associationListRadioGroup.checkedRadioButtonId
                    if (selectedId == -1) {
                        // No device selected
                        toast("need to select a device")
                    } else {
                        val selectedRadioButton =
                            associationListRadioGroup.findViewById<RadioButton>(selectedId)
                        // Get the MAC address stored in the tag
                        val macAddressToDisassociate = selectedRadioButton.tag as String

                        if (macAddressToDisassociate != "Unknown Address") {
                            toast("Disassociating $macAddressToDisassociate")
                            cdm.disassociate(macAddressToDisassociate)
                            val key = macAddressToDisassociate.uppercase(Locale.getDefault())
                            associationTimeMap.remove(key)
                            // Refresh the list after a short delay to reflect the change
                            mainHandler.postDelayed({ refresh() }, 500)
                        } else {
                            toast("Cannot disassociate device with unknown address")
                        }
                    }
                }
            })

            addView(Button(ctx).apply {
                text = "Register PresenceListener"
                setOnClickListener {
                    cdm.associations.forEach { address ->
                        toast("startObservingDevicePresence $address")
                        cdm.startObservingDevicePresence(address)
                    }
                }
            })

            addView(Button(ctx).apply {
                text = "Request permission transfer"
                setOnClickListener {
                    cdm.myAssociations.firstNotNullOf { associationInfo ->
                        val associationId = associationInfo.id
                        toast("requestSystemDataTransfer $associationId")
                        val intentSender = cdm.buildPermissionTransferUserConsentIntent(
                                associationId
                        )
                        if (intentSender != null) {
                            startIntentSender(intentSender, null, 0, 0, 0)
                        }
                    }
                }
            })

            addView(Button(ctx).apply {
                text = "Check location permission"
                setOnClickListener {
                    val locationAccess = ctx.checkSelfPermission(ACCESS_FINE_LOCATION)
                    toast("location access: $locationAccess")
                }
            })

            addView(Button(ctx).apply {
                text = "Request location permission"
                setOnClickListener {
                    requestPermissions(arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION), 10)
                }
            })
        })
    }

    private fun cdmButton(label: String, initReq: AssociationRequest.Builder.() -> Unit): Button {
        return Button(ctx).apply {
            text = label

            setOnClickListener {
                cdm.associate(
                    AssociationRequest.Builder()
                        .apply { initReq() }
                        .build(),
                        object : CompanionDeviceManager.Callback() {
                            override fun onFailure(error: CharSequence?) {
                                toast("error: $error")
                            }

                            override fun onDeviceFound(chooserLauncher: IntentSender) {
                                toast("launching $chooserLauncher")
                                chooserLauncher?.let {
                                    startIntentSenderForResult(it, REQUEST_CODE_CDM, null, 0, 0, 0)
                                }
                            }
                        },
                        mainHandler
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_CDM) {
            device = getDevice(data)
            toast("result code: $resultCode, device: $device")

            if (resultCode == Activity.RESULT_OK) {
                device?.address?.let { macAddress ->
                    val key = macAddress.uppercase(Locale.getDefault())
                    associationTimeMap[key] = System.currentTimeMillis()
                }
                // Post-delay to give the system time to update the associations list
                mainHandler.postDelayed({ refresh() }, 500)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun getDevice(data: Intent?): BluetoothDevice? {
        val rawDevice = data?.getParcelableExtra<Parcelable?>(CompanionDeviceManager.EXTRA_DEVICE)
        return when (rawDevice) {
            is BluetoothDevice -> rawDevice
            is ScanResult -> rawDevice.device
            else -> null
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        associationStatus.text = "Have associations: ${cdm.associations.isNotEmpty()}"

        permissionStatus.text = "Phone granted: ${
            checkPermission(CALL_PHONE, Process.myPid(), Process.myUid()) ==
                    PackageManager.PERMISSION_GRANTED}"

        notificationsStatus.postDelayed({
            notificationsStatus.text = "Notifications granted: ${
                try {
                    cdm.hasNotificationAccess(
                            ComponentName.createRelative(
                                    this, NotificationListener::class.java.name))
                } catch (e: Exception) {
                    toast("" + e.message)
                    false
                }
            }"
        }, 1000)

        associateNumber.text = "Association Number: ${
            cdm.associations.size
        }"

        // Store the currently checked MAC address to re-check it if it still exists
        val currentCheckedId = associationListRadioGroup.checkedRadioButtonId
        var currentCheckedMac: String? = null
        if (currentCheckedId != -1) {
            currentCheckedMac = associationListRadioGroup
                .findViewById<RadioButton>(currentCheckedId)?.tag as? String
        }

        associationListRadioGroup.removeAllViews()
        var newCheckedId = -1

        cdm.myAssociations.forEachIndexed { index, associationInfo ->
            val macAddress = associationInfo.deviceMacAddress?.toString() ?: "Unknown Address"
            val displayName = associationInfo.displayName ?: "Unknown Name"

            val profileString = when (associationInfo.deviceProfile) {
                DEVICE_PROFILE_WATCH -> "Watch"
                DEVICE_PROFILE_GLASSES -> "Glasses"
                DEVICE_PROFILE_MEDICAL -> "Medical"
                null -> "Non-Profile Device"
                else -> associationInfo.deviceProfile
            }

            val key = macAddress.uppercase(Locale.getDefault())
            val timestampMillis = associationTimeMap[key]

            val timeString = if (timestampMillis != null && timestampMillis > 0) {
                DateFormat.format("yyyy-MM-dd HH:mm:ss", timestampMillis).toString()
            } else {
                "Unknown Time (Not in this app session)"
            }

            val radioButton = RadioButton(this).apply {
                text = "$displayName ($macAddress) - [$profileString] - [$timeString]"
                tag = macAddress
                id = index
            }
            associationListRadioGroup.addView(radioButton)

            if (macAddress == currentCheckedMac) {
                newCheckedId = radioButton.id
            }
        }

        if (newCheckedId != -1) {
            associationListRadioGroup.check(newCheckedId)
        }
    }

    companion object {
        const val REQUEST_CODE_CDM = 1
    }
}

fun Context.toast(msg: String) {
    Log.i("CompanionDeviceManagerTest", "toast: $msg")
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

val Context.ctx get() = this
