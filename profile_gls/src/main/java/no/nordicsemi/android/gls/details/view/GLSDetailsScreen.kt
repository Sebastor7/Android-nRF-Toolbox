package no.nordicsemi.android.gls.details.view

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import no.nordicsemi.android.gls.R
import no.nordicsemi.android.gls.data.GLSRecord
import no.nordicsemi.android.gls.view.toDisplayString

@Composable
internal fun GLSDetailsScreen(record: GLSRecord) {
    Column {

        Field(stringResource(id = R.string.gls_details_sequence_number), record.sequenceNumber.toString())

        record.time?.let {
            Field(stringResource(id = R.string.gls_details_date_and_time), stringResource(R.string.gls_timestamp, it))
            Spacer(modifier = Modifier.size(4.dp))
        }
        record.type?.let {
            Field(stringResource(id = R.string.gls_details_type), it.toDisplayString())
            Spacer(modifier = Modifier.size(4.dp))
        }
        record.sampleLocation?.let {
            Field(stringResource(id = R.string.gls_details_location), it.toDisplayString())
            Spacer(modifier = Modifier.size(4.dp))
        }

        Field(stringResource(id = R.string.gls_details_glucose_condensation_title), stringResource(id = R.string.gls_details_glucose_condensation_field, record.glucoseConcentration, record.unit.toDisplayString()))

        record.status?.let {
            Field(stringResource(id = R.string.gls_details_battery_low), it.deviceBatteryLow.toGLSStatus())
            Spacer(modifier = Modifier.size(4.dp))
            Field(stringResource(id = R.string.gls_details_sensor_malfunction), it.sensorMalfunction.toGLSStatus())
            Spacer(modifier = Modifier.size(4.dp))
            Field(stringResource(id = R.string.gls_details_insufficient_sample), it.sampleSizeInsufficient.toGLSStatus())
            Spacer(modifier = Modifier.size(4.dp))
            Field(stringResource(id = R.string.gls_details_strip_insertion_error), it.stripInsertionError.toGLSStatus())
            Spacer(modifier = Modifier.size(4.dp))
            Field(stringResource(id = R.string.gls_details_strip_type_incorrect), it.stripTypeIncorrect.toGLSStatus())
            Spacer(modifier = Modifier.size(4.dp))
            Field(stringResource(id = R.string.gls_details_sensor_result_too_high), it.sensorResultHigherThenDeviceCanProcess.toGLSStatus())
            Spacer(modifier = Modifier.size(4.dp))
            Field(stringResource(id = R.string.gls_details_sensor_result_too_low), it.sensorResultLowerThenDeviceCanProcess.toGLSStatus())
            Spacer(modifier = Modifier.size(4.dp))
            Field(stringResource(id = R.string.gls_details_temperature_too_high), it.sensorTemperatureTooHigh.toGLSStatus())
            Spacer(modifier = Modifier.size(4.dp))
            Field(stringResource(id = R.string.gls_details_temperature_too_low), it.sensorTemperatureTooLow.toGLSStatus())
            Spacer(modifier = Modifier.size(4.dp))
            Field(stringResource(id = R.string.gls_details_strip_pulled_too_soon), it.sensorReadInterrupted.toGLSStatus())
            Spacer(modifier = Modifier.size(4.dp))
            Field(stringResource(id = R.string.gls_details_general_device_fault), it.generalDeviceFault.toGLSStatus())
            Spacer(modifier = Modifier.size(4.dp))
            Field(stringResource(id = R.string.gls_details_time_fault), it.timeFault.toGLSStatus())
            Spacer(modifier = Modifier.size(4.dp))
        }

        record.context?.let {
            Field(stringResource(id = R.string.gls_context_title), stringResource(id = R.string.gls_available))
            Spacer(modifier = Modifier.size(4.dp))
            it.carbohydrate?.let {
                Field(stringResource(id = R.string.gls_context_carbohydrate), it.toDisplayString())
                Spacer(modifier = Modifier.size(4.dp))
            }
            it.meal?.let {
                Field(stringResource(id = R.string.gls_context_meal), it.toDisplayString())
                Spacer(modifier = Modifier.size(4.dp))
            }
            it.tester?.let {
                Field(stringResource(id = R.string.gls_context_tester), it.toDisplayString())
                Spacer(modifier = Modifier.size(4.dp))
            }
            it.health?.let {
                Field(stringResource(id = R.string.gls_context_health), it.toDisplayString())
                Spacer(modifier = Modifier.size(4.dp))
            }
            Field(stringResource(id = R.string.gls_context_exercise_title), stringResource(id = R.string.gls_context_exercise_field, it.exerciseDuration, it.exerciseIntensity))
            Spacer(modifier = Modifier.size(4.dp))
            Field(stringResource(id = R.string.gls_context_medication_title), stringResource(id = R.string.gls_context_medication_field, it.medicationQuantity, it.medicationUnit.toDisplayString(), it.medication?.toDisplayString()))
            Spacer(modifier = Modifier.size(4.dp))
            Field(stringResource(id = R.string.gls_context_hba1c_title), stringResource(id = R.string.gls_context_hba1c_field, it.HbA1c))
            Spacer(modifier = Modifier.size(4.dp))
        } ?: Field(stringResource(id = R.string.gls_context_title), stringResource(id = R.string.gls_unavailable))
    }
}

@Composable
internal fun Field(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
