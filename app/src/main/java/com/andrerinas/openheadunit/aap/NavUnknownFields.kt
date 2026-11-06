package com.andrerinas.openheadunit.aap

import com.google.protobuf.Message
import com.google.protobuf.UnknownFieldSet

/**
 * Fields of a navigation message that our proto doesn't name, to find out what else Maps sends (a
 * battery forecast at arrival?). Only numbers that could be a percentage are shown: anything else,
 * text or a coordinate, is reduced to its type, since log files end up on GitHub.
 */
object NavUnknownFields {

    /** "destination_distances[0].#4=varint:37 #5=bytes(12)", or empty when everything is known. */
    fun describe(message: Message): String = buildList { collect(message, "", this) }.joinToString(" ")

    private fun collect(message: Message, path: String, out: MutableList<String>) {
        unknown(message.unknownFields, path, out)
        message.allFields.forEach { (field, value) ->
            val name = if (path.isEmpty()) field.name else "$path.${field.name}"
            when (value) {
                is Message -> collect(value, name, out)
                is List<*> -> value.forEachIndexed { i, item -> if (item is Message) collect(item, "$name[$i]", out) }
            }
        }
    }

    private fun unknown(set: UnknownFieldSet, path: String, out: MutableList<String>) {
        val prefix = if (path.isEmpty()) "" else "$path."
        set.asMap().forEach { (number, field) ->
            field.varintList.forEach { out += "$prefix#$number=varint:${shown(it)}" }
            field.fixed32List.forEach { out += "$prefix#$number=fixed32:${shown(java.lang.Float.intBitsToFloat(it))}" }
            field.fixed64List.forEach { out += "$prefix#$number=fixed64:${shown(java.lang.Double.longBitsToDouble(it))}" }
            field.lengthDelimitedList.forEach { out += "$prefix#$number=bytes(${it.size()})" }
            field.groupList.forEach { unknown(it, "$prefix#$number", out) }
        }
    }

    private fun shown(value: Long): String = if (value in 0..100) "$value" else "other"

    private fun shown(value: Number): String {
        val d = value.toDouble()
        return if (d.isFinite() && d in 0.0..100.0) "%.1f".format(java.util.Locale.US, d) else "other"
    }
}
