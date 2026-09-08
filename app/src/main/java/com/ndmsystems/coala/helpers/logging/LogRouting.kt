package com.ndmsystems.coala.helpers.logging

/**
 * Where a record goes, as opposed to what it says.
 *
 * Deliberately not in [LogKeys]. Everything in that object is a field name offered to call sites,
 * and a key that silently discards the record it is attached to is not a field - a caller who
 * picked it for its plain meaning would lose the message, the level and every other field with no
 * error anywhere. The names here are prefixed so that collision has to be deliberate.
 */
object LogRouting {

    /**
     * Marks a record as belonging in logcat and nowhere else. A sink that ships records honours it
     * by dropping the record; the local ones ignore it and print as usual. Either way the key is
     * never rendered and never reaches a server.
     *
     * The level is about how bad something is. Where a record should end up is a different
     * question, and answering it by lowering a level only works by accident - a full wire trace is
     * not less important than a lifecycle line, it is just useless to a server.
     */
    const val LOCAL_ONLY = "__local_only"

    /**
     * [fields] without any routing key, ready to be rendered or shipped.
     *
     * Allocates only when there is something to remove, which is the overwhelmingly common case:
     * almost no record carries a marker at all.
     */
    fun strip(fields: Map<String, Any?>): Map<String, Any?> =
        if (LOCAL_ONLY in fields) fields - LOCAL_ONLY else fields
}
