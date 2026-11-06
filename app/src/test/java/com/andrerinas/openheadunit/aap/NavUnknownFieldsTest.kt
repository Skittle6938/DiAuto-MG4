package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.proto.NavigationStatus
import com.google.protobuf.UnknownFieldSet
import org.junit.Assert.assertEquals
import org.junit.Test

class NavUnknownFieldsTest {

    private fun field(builder: UnknownFieldSet.Field.Builder) = builder.build()

    @Test
    fun `known fields alone give nothing`() {
        val position = NavigationStatus.NavigationCurrentPosition.newBuilder()
            .addDestinationDistances(NavigationStatus.NavigationDestinationDistance.newBuilder().setTimeToArrivalSeconds(600))
            .build()
        assertEquals("", NavUnknownFields.describe(position))
    }

    @Test
    fun `an unknown field nested in a destination is found, a percentage shown and anything else hidden`() {
        val unknown = UnknownFieldSet.newBuilder()
            .addField(4, field(UnknownFieldSet.Field.newBuilder().addVarint(37)))
            .addField(5, field(UnknownFieldSet.Field.newBuilder().addVarint(486_123_456)))
            .addField(6, field(UnknownFieldSet.Field.newBuilder().addLengthDelimited(com.google.protobuf.ByteString.copyFromUtf8("Home"))))
            .build()
        val position = NavigationStatus.NavigationCurrentPosition.newBuilder()
            .addDestinationDistances(
                NavigationStatus.NavigationDestinationDistance.newBuilder().setTimeToArrivalSeconds(600).setUnknownFields(unknown),
            )
            .build()
        assertEquals(
            "destination_distances[0].#4=varint:37 destination_distances[0].#5=varint:other destination_distances[0].#6=bytes(4)",
            NavUnknownFields.describe(position),
        )
    }
}
