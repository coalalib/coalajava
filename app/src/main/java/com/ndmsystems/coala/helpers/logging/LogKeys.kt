package com.ndmsystems.coala.helpers.logging

/**
 * Names for the context a log call carries alongside its message.
 *
 * A suggestion, not a gate. The field map is `Map<String, Any?>`, so a call site that needs a name
 * this list does not have just writes the string - `mapOf(LogKeys.ERROR to e.message, "segment" to
 * id)` mixes the two without ceremony. What the list is for is the handful of names that would
 * otherwise be spelled six ways: an error reached the collector as `err`, `error`, `errorMessage`,
 * `cause` and `reason` in the same codebase, and filtering across that is worse than grep.
 *
 * These are `const val`, not an enum, for exactly that reason: an enum would make the free-form
 * case awkward, and mixing the two in one map impossible without a second overload.
 *
 * It lives beside [ILogger] rather than in the app so that `:api` and coala itself can name a
 * field the same way the app does. A few entries are app-shaped (`screen`, `deeplink`) and will
 * never be used from here; one vocabulary that covers everyone beats two that disagree.
 *
 * Two rules the names here already follow, and a new one should too:
 *
 * - **snake_case**, because that is what the collector writes for its own fields (`src_ip`, and
 *   `http_status` in its documented example). One convention per row means a query does not have
 *   to remember which side a given field came from.
 * - **never a name the log backend writes for itself** - `cid`, `app`, `os`, `version`, `build`,
 *   `region`, `bundle`, `source`, `message`, `id` and friends. A collector keeps its own value
 *   and drops the record's, in silence, so such a field is not a conflict to resolve but data
 *   that vanishes. The app's LogKeysTest checks this list against the rules that apply there.
 *
 * Deliberately absent: anything that names a person. `email` appears in a couple of existing log
 * lines and is not offered here - diagnostics should carry the identifier the backend correlates
 * by, not the user's mailbox.
 */
object LogKeys {

    // ---- Failure -----------------------------------------------------------------------------
    // Roughly half of everything logged at info and above is "something failed", so these are the
    // names worth getting right first.

    /** Human-readable failure text, usually `Throwable.message`. */
    const val ERROR = "error"

    /** The exception's simple class name, when the type is the useful part. */
    const val ERROR_TYPE = "error_type"

    /** A code the backend or the router returned, numeric or string. */
    const val ERROR_CODE = "error_code"

    /**
     * Why it failed, one level down: the message of `throwable.cause`, or - where there is no
     * exception at all - a short machine-ish reason such as `timeout`, `no_network`, `cancelled`.
     */
    const val CAUSE = "cause"

    /** A rendered stack trace, where the frames matter and not just the message. */
    const val STACK = "stack_trace"

    // ---- Network and transport ---------------------------------------------------------------

    /** Hostname on its own. */
    const val HOST = "host"

    /** Host and port together, as the transport prints it. */
    const val ADDRESS = "address"

    /** Full request URL. Redaction still applies - a secret in the query string is stripped. */
    const val URL = "url"

    /** HTTP status code. The name the collector's own documentation uses. */
    const val HTTP_STATUS = "http_status"

    /** Which try this was, 1-based. */
    const val ATTEMPT = "attempt"

    /** How many tries the caller will make in total. */
    const val MAX_ATTEMPTS = "max_attempts"

    // ---- Router and account domain -----------------------------------------------------------

    /** Network identifier. Not `cid`, which the envelope already carries for the device. */
    const val NETWORK_UID = "network_uid"

    /** The identifier of the *other* end of a session, where one exists. */
    const val PEER_CID = "peer_cid"

    /** MAC address of a device on the network. */
    const val DEVICE_MAC = "device_mac"

    /** Router model name. */
    const val MODEL = "model"

    /** Router interface name, e.g. `PPPoE0`, `Bridge0`. */
    const val INTERFACE = "interface"

    /** Which service was being talked to: `account`, `gum`, and so on. */
    const val SERVICE = "service"

    // ---- Shape and size ----------------------------------------------------------------------

    /** How many of something - entries skipped, devices found, bytes queued. */
    const val COUNT = "count"

    /** A size in bytes, where the number is the point. */
    const val BYTES = "bytes"

    /** Elapsed time in milliseconds. */
    const val DURATION_MS = "duration_ms"

    // ---- Payloads and identity ----------------------------------------------------------------

    /**
     * The body that was sent or received, verbatim.
     *
     * Router answers and CoAP payloads are logged whole all over the app, and a JSON body is
     * exactly the shape `LogSanitizer` knows how to strip credentials out of - so it stays a
     * string here rather than being parsed into fields.
     */
    const val PAYLOAD = "payload"

    /** The answer to a request, where [PAYLOAD] would be the request's own body. */
    const val RESPONSE = "response"

    /** Request path, without the host: `/get`, `/rci/`, `/user/auth`. */
    const val PATH = "path"

    /** Name of the thing acted on, when nothing more specific fits. */
    const val NAME = "name"

    /** Internet connection profile - the name, or the whole parsed profile. */
    const val PROFILE = "profile"

    /** The value under discussion, when the message already says what it is. */
    const val VALUE = "value"

    /** Current state or status, as the code names it. */
    const val STATE = "state"

    /** Why this happened, in the caller's own words - free text, unlike [CAUSE]. */
    const val REASON = "reason"

    /** File name or path. */
    const val FILE = "file"

    // ---- CoAP ---------------------------------------------------------------------------------

    /**
     * The CoAP message token, hex-encoded.
     *
     * Deliberately not `token`: this is a correlation id, and `LogSanitizer` blanks a field named
     * `token` outright. Redacting it would make every message trace unreadable for nothing.
     */
    const val COAP_TOKEN = "coap_token"

    /** The CoAP message id, which unlike [COAP_TOKEN] is per-transmission. */
    const val COAP_MESSAGE_ID = "coap_message_id"


    // ---- App surface -------------------------------------------------------------------------

    /** Screen or presenter the call came from, when `caller` is not specific enough. */
    const val SCREEN = "screen"

    /** Deep link that brought the user here. */
    const val DEEPLINK = "deeplink"

    /** Launcher shortcut identifier. */
    const val SHORTCUT_ID = "shortcut_id"
}
