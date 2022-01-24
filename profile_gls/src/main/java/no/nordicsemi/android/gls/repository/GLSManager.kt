/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package no.nordicsemi.android.gls.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import no.nordicsemi.android.ble.common.callback.RecordAccessControlPointDataCallback
import no.nordicsemi.android.ble.common.callback.glucose.GlucoseMeasurementContextDataCallback
import no.nordicsemi.android.ble.common.callback.glucose.GlucoseMeasurementDataCallback
import no.nordicsemi.android.ble.common.data.RecordAccessControlPointData
import no.nordicsemi.android.ble.common.profile.RecordAccessControlPointCallback.RACPErrorCode
import no.nordicsemi.android.ble.common.profile.RecordAccessControlPointCallback.RACPOpCode
import no.nordicsemi.android.ble.common.profile.glucose.GlucoseMeasurementCallback.GlucoseStatus
import no.nordicsemi.android.ble.common.profile.glucose.GlucoseMeasurementContextCallback.Carbohydrate
import no.nordicsemi.android.ble.common.profile.glucose.GlucoseMeasurementContextCallback.Health
import no.nordicsemi.android.ble.common.profile.glucose.GlucoseMeasurementContextCallback.Meal
import no.nordicsemi.android.ble.common.profile.glucose.GlucoseMeasurementContextCallback.Medication
import no.nordicsemi.android.ble.common.profile.glucose.GlucoseMeasurementContextCallback.Tester
import no.nordicsemi.android.gls.data.ConcentrationUnit
import no.nordicsemi.android.gls.data.GLSRecord
import no.nordicsemi.android.gls.data.GLSRepository
import no.nordicsemi.android.gls.data.MeasurementContext
import no.nordicsemi.android.gls.data.MedicationUnit
import no.nordicsemi.android.gls.data.RecordType
import no.nordicsemi.android.gls.data.RequestStatus
import no.nordicsemi.android.service.BatteryManager
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/** Glucose service UUID  */
val GLS_SERVICE_UUID: UUID = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")

/** Glucose Measurement characteristic UUID  */
private val GM_CHARACTERISTIC = UUID.fromString("00002A18-0000-1000-8000-00805f9b34fb")

/** Glucose Measurement Context characteristic UUID  */
private val GM_CONTEXT_CHARACTERISTIC =
    UUID.fromString("00002A34-0000-1000-8000-00805f9b34fb")

/** Glucose Feature characteristic UUID  */
private val GF_CHARACTERISTIC = UUID.fromString("00002A51-0000-1000-8000-00805f9b34fb")

/** Record Access Control Point characteristic UUID  */
private val RACP_CHARACTERISTIC = UUID.fromString("00002A52-0000-1000-8000-00805f9b34fb")

@Singleton
internal class GLSManager @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: GLSRepository
) : BatteryManager(context) {

    private var glucoseMeasurementCharacteristic: BluetoothGattCharacteristic? = null
    private var glucoseMeasurementContextCharacteristic: BluetoothGattCharacteristic? = null
    private var recordAccessControlPointCharacteristic: BluetoothGattCharacteristic? = null

    override fun onBatteryLevelChanged(batteryLevel: Int) {
        repository.setNewBatteryLevel(batteryLevel)
    }

    override fun getGattCallback(): BatteryManagerGattCallback {
        return GlucoseManagerGattCallback()
    }

    /**
     * BluetoothGatt callbacks for connection/disconnection, service discovery,
     * receiving notification, etc.
     */
    private inner class GlucoseManagerGattCallback : BatteryManagerGattCallback() {
        override fun initialize() {
            super.initialize()

            // The gatt.setCharacteristicNotification(...) method is called in BleManager during
            // enabling notifications or indications
            // (see BleManager#internalEnableNotifications/Indications).
            // However, on Samsung S3 with Android 4.3 it looks like the 2 gatt calls
            // (gatt.setCharacteristicNotification(...) and gatt.writeDescriptor(...)) are called
            // too quickly, or from a wrong thread, and in result the notification listener is not
            // set, causing onCharacteristicChanged(...) callback never being called when a
            // notification comes. Enabling them here, like below, solves the problem.
            // However... the original approach works for the Battery Level CCCD, which makes it
            // even weirder.
            /*
			gatt.setCharacteristicNotification(glucoseMeasurementCharacteristic, true);
			if (glucoseMeasurementContextCharacteristic != null) {
				device.setCharacteristicNotification(glucoseMeasurementContextCharacteristic, true);
			}
			device.setCharacteristicNotification(recordAccessControlPointCharacteristic, true);
			*/
            setNotificationCallback(glucoseMeasurementCharacteristic)
                .with(object : GlucoseMeasurementDataCallback() {

                    override fun onGlucoseMeasurementReceived(
                        device: BluetoothDevice,
                        sequenceNumber: Int,
                        time: Calendar,
                        glucoseConcentration: Float?,
                        unit: Int?,
                        type: Int?,
                        sampleLocation: Int?,
                        status: GlucoseStatus?,
                        contextInformationFollows: Boolean
                    ) {
                        val record = GLSRecord(
                            sequenceNumber = sequenceNumber,
                            time = time,
                            glucoseConcentration = glucoseConcentration ?: 0f,
                            unit = unit?.let { ConcentrationUnit.create(it) }
                                ?: ConcentrationUnit.UNIT_KGPL,
                            type = RecordType.createOrNull(type),
                            sampleLocation = sampleLocation ?: 0,
                            status = status
                        )

                        repository.addNewRecord(record)
                    }
                })
            setNotificationCallback(glucoseMeasurementContextCharacteristic)
                .with(object : GlucoseMeasurementContextDataCallback() {

                    override fun onGlucoseMeasurementContextReceived(
                        device: BluetoothDevice,
                        sequenceNumber: Int,
                        carbohydrate: Carbohydrate?,
                        carbohydrateAmount: Float?,
                        meal: Meal?,
                        tester: Tester?,
                        health: Health?,
                        exerciseDuration: Int?,
                        exerciseIntensity: Int?,
                        medication: Medication?,
                        medicationAmount: Float?,
                        medicationUnit: Int?,
                        HbA1c: Float?
                    ) {
                        val context = MeasurementContext(
                            sequenceNumber = sequenceNumber,
                            carbohydrate = carbohydrate,
                            carbohydrateUnits = carbohydrateAmount ?: 0f,
                            meal = meal,
                            tester = tester,
                            health = health,
                            exerciseDuration = exerciseDuration ?: 0,
                            exerciseIntensity = exerciseIntensity ?: 0,
                            medication = medication,
                            medicationQuantity = medicationAmount ?: 0f,
                            medicationUnit = medicationUnit?.let { MedicationUnit.create(it) }
                                ?: MedicationUnit.UNIT_KG,
                            HbA1c = HbA1c ?: 0f
                        )

                        repository.addNewContext(context)
                    }
                })
            setIndicationCallback(recordAccessControlPointCharacteristic)
                .with(object : RecordAccessControlPointDataCallback() {

                    @SuppressLint("SwitchIntDef")
                    override fun onRecordAccessOperationCompleted(
                        device: BluetoothDevice,
                        @RACPOpCode requestCode: Int
                    ) {
                        val status = when (requestCode) {
                            RACP_OP_CODE_ABORT_OPERATION -> RequestStatus.ABORTED
                            else -> RequestStatus.SUCCESS
                        }
                        repository.setRequestStatus(status)
                    }

                    override fun onRecordAccessOperationCompletedWithNoRecordsFound(
                        device: BluetoothDevice,
                        @RACPOpCode requestCode: Int
                    ) {
                        repository.setRequestStatus(RequestStatus.SUCCESS)
                    }

                    override fun onNumberOfRecordsReceived(
                        device: BluetoothDevice,
                        numberOfRecords: Int
                    ) {
                        if (numberOfRecords > 0) {
                            if (repository.records().isNotEmpty()) {
                                val sequenceNumber = repository.records().last().sequenceNumber + 1 //TODO check if correct
                                writeCharacteristic(
                                    recordAccessControlPointCharacteristic,
                                    RecordAccessControlPointData.reportStoredRecordsGreaterThenOrEqualTo(
                                        sequenceNumber
                                    )
                                )
                                    .enqueue()
                            } else {
                                writeCharacteristic(
                                    recordAccessControlPointCharacteristic,
                                    RecordAccessControlPointData.reportAllStoredRecords()
                                )
                                    .enqueue()
                            }
                        }
                        repository.setRequestStatus(RequestStatus.SUCCESS)
                    }

                    override fun onRecordAccessOperationError(
                        device: BluetoothDevice,
                        @RACPOpCode requestCode: Int,
                        @RACPErrorCode errorCode: Int
                    ) {
                        log(Log.WARN, "Record Access operation failed (error $errorCode)")
                        if (errorCode == RACP_ERROR_OP_CODE_NOT_SUPPORTED) {
                            repository.setRequestStatus(RequestStatus.NOT_SUPPORTED)
                        } else {
                            repository.setRequestStatus(RequestStatus.FAILED)
                        }
                    }
                })
            enableNotifications(glucoseMeasurementCharacteristic).enqueue()
            enableNotifications(glucoseMeasurementContextCharacteristic).enqueue()
            enableIndications(recordAccessControlPointCharacteristic)
                .fail { device: BluetoothDevice?, status: Int ->
                    log(
                        Log.WARN,
                        "Failed to enabled Record Access Control Point indications (error $status)"
                    )
                }
                .enqueue()
        }

        public override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val service = gatt.getService(GLS_SERVICE_UUID)
            if (service != null) {
                glucoseMeasurementCharacteristic = service.getCharacteristic(GM_CHARACTERISTIC)
                glucoseMeasurementContextCharacteristic = service.getCharacteristic(
                    GM_CONTEXT_CHARACTERISTIC
                )
                recordAccessControlPointCharacteristic = service.getCharacteristic(
                    RACP_CHARACTERISTIC
                )
            }
            return glucoseMeasurementCharacteristic != null && recordAccessControlPointCharacteristic != null
        }

        override fun onServicesInvalidated() { }

        override fun isOptionalServiceSupported(gatt: BluetoothGatt): Boolean {
            super.isOptionalServiceSupported(gatt)
            return glucoseMeasurementContextCharacteristic != null
        }

        override fun onDeviceDisconnected() {
            glucoseMeasurementCharacteristic = null
            glucoseMeasurementContextCharacteristic = null
            recordAccessControlPointCharacteristic = null
        }
    }

    /**
     * Clears the records list locally.
     */
    private fun clear() {
        repository.clearRecords()
        val target = bluetoothDevice
        if (target != null) {
            repository.setRequestStatus(RequestStatus.SUCCESS)
        }
    }

    /**
     * Sends the request to obtain the last (most recent) record from glucose device. The data will
     * be returned to Glucose Measurement characteristic as a notification followed by Record Access
     * Control Point indication with status code Success or other in case of error.
     */
    fun requestLastRecord() {
        if (recordAccessControlPointCharacteristic == null) return
        val target = bluetoothDevice ?: return
        clear()
        repository.setRequestStatus(RequestStatus.PENDING)
        writeCharacteristic(
            recordAccessControlPointCharacteristic,
            RecordAccessControlPointData.reportLastStoredRecord()
        ).enqueue()
    }

    /**
     * Sends the request to obtain the first (oldest) record from glucose device. The data will be
     * returned to Glucose Measurement characteristic as a notification followed by Record Access
     * Control Point indication with status code Success or other in case of error.
     */
    fun requestFirstRecord() {
        if (recordAccessControlPointCharacteristic == null) return
        val target = bluetoothDevice ?: return
        clear()
        repository.setRequestStatus(RequestStatus.PENDING)
        writeCharacteristic(
            recordAccessControlPointCharacteristic,
            RecordAccessControlPointData.reportFirstStoredRecord()
        ).enqueue()
    }

    /**
     * Sends the request to obtain all records from glucose device. Initially we want to notify user
     * about the number of the records so the 'Report Number of Stored Records' is send. The data
     * will be returned to Glucose Measurement characteristic as a notification followed by
     * Record Access Control Point indication with status code Success or other in case of error.
     */
    fun requestAllRecords() {
        if (recordAccessControlPointCharacteristic == null) return
        val target = bluetoothDevice ?: return
        clear()
        repository.setRequestStatus(RequestStatus.PENDING)
        writeCharacteristic(
            recordAccessControlPointCharacteristic,
            RecordAccessControlPointData.reportNumberOfAllStoredRecords()
        ).enqueue()
    }

    /**
     * Sends the request to obtain from the glucose device all records newer than the newest one
     * from local storage. The data will be returned to Glucose Measurement characteristic as
     * a notification followed by Record Access Control Point indication with status code Success
     * or other in case of error.
     *
     *
     * Refresh button will not download records older than the oldest in the local memory.
     * E.g. if you have pressed Last and then Refresh, than it will try to get only newer records.
     * However if there are no records, it will download all existing (using [.getAllRecords]).
     */
    fun refreshRecords() {
        if (recordAccessControlPointCharacteristic == null) return
        val target = bluetoothDevice ?: return
        if (repository.records().isEmpty()) {
            requestAllRecords()
        } else {
            repository.setRequestStatus(RequestStatus.PENDING)

            // obtain the last sequence number
            val sequenceNumber = repository.records().last().sequenceNumber + 1 //TODO check if correct
            writeCharacteristic(
                recordAccessControlPointCharacteristic,
                RecordAccessControlPointData.reportStoredRecordsGreaterThenOrEqualTo(sequenceNumber)
            ).enqueue()
            // Info:
            // Operators OPERATOR_LESS_THEN_OR_EQUAL and OPERATOR_RANGE are not supported by Nordic Semiconductor Glucose Service in SDK 4.4.2.
        }
    }

    /**
     * Sends abort operation signal to the device.
     */
    fun abort() {
        if (recordAccessControlPointCharacteristic == null) return
        val target = bluetoothDevice ?: return
        writeCharacteristic(
            recordAccessControlPointCharacteristic,
            RecordAccessControlPointData.abortOperation()
        ).enqueue()
    }

    /**
     * Sends the request to delete all data from the device. A Record Access Control Point
     * indication with status code Success (or other in case of error) will be send.
     */
    fun deleteAllRecords() {
        if (recordAccessControlPointCharacteristic == null) return
        val target = bluetoothDevice ?: return
        clear()
        repository.setRequestStatus(RequestStatus.PENDING)
        writeCharacteristic(
            recordAccessControlPointCharacteristic,
            RecordAccessControlPointData.deleteAllStoredRecords()
        ).enqueue()

        val elements = listOf(1, 2, 3)
        val result = elements.all { it > 3 }
    }
}
