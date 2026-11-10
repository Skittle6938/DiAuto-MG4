package com.andrerinas.openheadunit.aap.protocol.messages

import com.andrerinas.openheadunit.aap.protocol.proto.Sensors
import com.andrerinas.openheadunit.vehicle.CarDataSensors
import com.google.protobuf.Message

/** Battery charge as Android Auto's FUEL_LEVEL sensor (see CarDataSensors). */
class FuelLevelEvent(fuel: CarDataSensors.Fuel)
    : SensorEvent(Sensors.SensorType.FUEL_LEVEL_VALUE, makeProto(fuel)) {

    companion object {
        private fun makeProto(fuel: CarDataSensors.Fuel): Message =
            Sensors.SensorBatch.newBuilder().also {
                it.addFuelData(
                    Sensors.SensorBatch.FuelData.newBuilder()
                        .setFuellevel(fuel.levelPercent)
                        .setRange(fuel.rangeKm)
                        .setLowfuel(fuel.low)
                )
            }.build()
    }
}

/** Odometer as Android Auto's ODOMETER sensor, in tenths of a km. */
class OdometerEvent(tenthsKm: Int)
    : SensorEvent(Sensors.SensorType.ODOMETER_VALUE, makeProto(tenthsKm)) {

    companion object {
        private fun makeProto(tenthsKm: Int): Message =
            Sensors.SensorBatch.newBuilder().also {
                it.addOdometerData(Sensors.SensorBatch.OdometerData.newBuilder().setKmsEl(tenthsKm))
            }.build()
    }
}
