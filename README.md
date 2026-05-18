# Coala Java

Android/Kotlin implementation of Coala on top of CoAP messages. The library is
packaged as an Android module and exposes a Java-callable API for peer-to-peer
CoAP communication over UDP, with optional TCP proxy transport support.

Coala Java includes:

- UDP client/server API over CoAP datagram encoding.
- TCP proxy transport using Coala frame format.
- Resources with `GET`, `POST`, `PUT`, and `DELETE` handlers.
- RxJava response APIs and callback-based send APIs.
- Response callbacks, retransmit pool, and delivery statistics.
- Observable resources and Observe registration handling.
- Multicast discovery on `224.0.0.187:<port>/info`.
- Proxy options.
- Block1/Block2 and selective-repeat ARQ for large payloads.
- `coaps` handshake/encryption with Curve25519, HKDF-SHA256, and AES-GCM using
  Coala's 12-byte authentication tag format.

## Requirements

- Android Gradle Plugin `8.7.3`
- Kotlin `2.0.21`
- Java 11 source/target compatibility
- Android `minSdkVersion 21`, `targetSdkVersion 35`, `compileSdk 35`

```bash
./gradlew testDebugUnitTest
```

## Integration

Add this repository as a submodule and include the Android library module.

`settings.gradle`:

```gradle
include ':submodules:coala:app'
```

Application module `build.gradle`:

```gradle
dependencies {
    implementation project(':submodules:coala:app')
}
```

## Quick Start

```kotlin
import com.ndmsystems.coala.CoAPResource
import com.ndmsystems.coala.CoAPResourceInput
import com.ndmsystems.coala.CoAPResourceOutput
import com.ndmsystems.coala.Coala
import com.ndmsystems.coala.ICoalaStorage
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.message.CoAPRequestMethod

val storage = object : ICoalaStorage {
    private val map = hashMapOf<String, Any>()

    override fun put(key: String, obj: Any) {
        map[key] = obj
    }

    override fun <T> get(key: String, clz: Class<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return map[key] as? T
    }

    override fun remove(key: String) {
        map.remove(key)
    }
}

val server = Coala(5683, storage)
server.addResource("msg", CoAPRequestMethod.GET, object : CoAPResource.CoAPResourceHandler() {
    override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput {
        return CoAPResourceOutput(
            CoAPMessagePayload("Hello from Coala Java"),
            CoAPMessageCode.CoapCodeContent,
            CoAPMessage.MediaType.TextPlain,
        )
    }
})
server.start()

val client = Coala(0, storage)
client.start()

val request = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
request.setURI("coap://127.0.0.1:5683/msg")

client.sendRequest(request).subscribe(
    { response -> println(response.payload) },
    { error -> error.printStackTrace() },
)
```

Stop sockets and clear pending messages when the instance is no longer needed:

```kotlin
client.stop()
server.stop()
```

## Main API

### `Coala`

| API | Description |
| --- | --- |
| `Coala(port, storage, params, connectivityManager)` | Creates a Coala stack bound to `port`. `port = 0` uses an ephemeral UDP port. |
| `start()` | Starts receiver and sender threads. |
| `stop()` | Clears pending messages and closes sockets. |
| `restartConnection()` | Stops and starts the current connection. |
| `setTransportMode(mode, tcpProxyAddress)` | Switches between `UDP` and `TCP` transport modes. TCP requires a proxy address. |
| `send(message, handler)` | Sends a message and delivers the response through `CoAPHandler`. |
| `send(message, handler, isNeedAddTokenForced)` | Same as `send`, with explicit token auto-generation control. |
| `send(message)` | Returns `Observable<CoAPMessage>`. |
| `sendRequest(message)` | Returns `Observable<ResponseData>` with string/byte payload helpers. |
| `cancel(message)` | Removes a message from the retransmit pool and ack handler pool. |
| `addResource(path, method, handler)` | Registers a server-side resource. |
| `removeResource(path, method)` | Removes a registered resource. |
| `addObservableResource(path, handler)` | Registers an observable `GET` resource. |
| `getObservableResource(path)` | Returns the observable resource for a path, if present. |
| `registerObserver(uri)` | Sends an Observe registration and returns `Observable<String?>`. |
| `runResourceDiscovery()` | Sends multicast discovery and returns `Single<List<ResourceDiscoveryResult>>`. |
| `getMessageDeliveryInfo(message)` | Reads retry/proxy/ARQ delivery metrics for a message. |
| `getReceivedStateForToken(token)` | Reads ARQ receive state for a token. |
| `setOnPortIsBusyHandler(handler)` | Installs a callback for port binding failures. |

### Message Pool Parameters

`CoAPMessagePool.Companion.Params` controls retransmission and cleanup timing:

| Field | Default | Description |
| --- | --- | --- |
| `resendPeriod` | `2002` ms | Delay before retrying a normal request. |
| `resendLongPeriod` | `30002` ms | Delay before retrying long-running requests. |
| `expirationPeriod` | `60002` ms | Time before deleting unsent commands. |
| `garbagePeriod` | `25002` ms | Time before deleting sent commands without ACK/error. |
| `maxPickAttempts` | `6` | Maximum send attempts. |

## Transports

| Mode | Description |
| --- | --- |
| `Coala.TransportMode.UDP` | Default mode. Uses `MulticastSocket` and binds to Android's active network on API 23+. |
| `Coala.TransportMode.TCP` | Sends Coala TCP frames through a socket connected to `tcpProxyAddress`. |

TCP mode:

```kotlin
coala.setTransportMode(
    Coala.TransportMode.TCP,
    InetSocketAddress("192.168.1.1", 5683),
)
coala.start()
```

Coala TCP frame format:

| Field | Size |
| --- | --- |
| delimiter `M` | 1 byte |
| IPv4 address | 4 bytes |
| port | 2 bytes |
| payload size | 2 bytes |
| CoAP payload | payload size bytes |

## Resources

| Class | Description |
| --- | --- |
| `CoAPResource` | Regular resource with `method`, `path`, and handler. |
| `CoAPObservableResource` | `GET` resource with Observe registration support. |
| `CoAPResourceInput` | Incoming request data: `message` and source `address`. |
| `CoAPResourceOutput` | Response payload, response code, and media type. |

Example `POST` resource:

```kotlin
server.addResource("config", CoAPRequestMethod.POST, object : CoAPResource.CoAPResourceHandler() {
    override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput {
        val payload = inputData.message.payload?.toString().orEmpty()
        val mode = inputData.message.getURIQuery("mode")

        return CoAPResourceOutput(
            CoAPMessagePayload("changed: $mode $payload"),
            CoAPMessageCode.CoapCodeChanged,
            CoAPMessage.MediaType.TextPlain,
        )
    }
})
```

## Messages and Methods

### CoAP Methods

| Method | Purpose |
| --- | --- |
| `CoAPRequestMethod.GET` / `CoAPMessageCode.GET` | Read a resource representation or state. |
| `CoAPRequestMethod.POST` / `CoAPMessageCode.POST` | Send a command or create/update subordinate state. |
| `CoAPRequestMethod.PUT` / `CoAPMessageCode.PUT` | Replace or set resource state. |
| `CoAPRequestMethod.DELETE` / `CoAPMessageCode.DELETE` | Delete a resource or clear state. |

Exact semantics are defined by the server-side handler, as in CoAP.

### Reliability Types

| Type | Purpose |
| --- | --- |
| `CoAPMessageType.CON` | Requires ACK/RST. The message pool retransmits until timeout. |
| `CoAPMessageType.NON` | Sends without requiring ACK. Used by discovery. |
| `CoAPMessageType.ACK` | Acknowledges a CON message. |
| `CoAPMessageType.RST` | Rejects a message or Observe notification. |

### `CoAPMessage`

| API | Description |
| --- | --- |
| `CoAPMessage(type, code, id)` | Creates a message with explicit type, code, and optional ID. |
| `CoAPMessage(message)` | Copies an existing message. |
| `setURI(uri)` | Writes scheme, address, path, and query from a URI string. |
| `getURI()` | Rebuilds the URI from address and CoAP options. |
| `setURIPath(path)`, `getURIPathString()` | Writes/reads URI-Path options. |
| `addQueryParam`, `addQueryParams`, `getURIQuery` | Writes/reads URI-Query options. |
| `setURIScheme`, `getURIScheme` | Selects `coap` or `coaps`. |
| `payload`, `setStringPayload` | Binary or UTF-8 payload. |
| `token`, `hexToken` | Token bytes and hex representation. |
| `responseHandler` | Optional response handler for `sendRequest`. |
| `peerPublicKey` | Expected peer key for validation, or received peer key on responses. |
| `setProxy(proxyAddress)` | Sets proxy address and Proxy-Uri option. |
| `addOption`, `removeOption`, `getOption`, `getOptions()` | Mutates and reads options. |
| `ackTo(...)`, `resetTo(...)` | Builds ACK/RST messages for an incoming message. |

Responses can be consumed as `ResponseData`:

```kotlin
val message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
message.setURI("coap://192.168.1.10:5683/info")

coala.sendRequest(message).subscribe(
    { response ->
        println(response.payload)
        println(response.peerPublicKey?.joinToString(""))
    },
    { error -> error.printStackTrace() },
)
```

## Discovery

`runResourceDiscovery()` works only in UDP mode:

```kotlin
coala.runResourceDiscovery().subscribe { peers ->
    peers.forEach { peer ->
        println("${peer.host}: ${peer.payload}")
    }
}
```

Discovery behavior:

- request: `NON GET coap://224.0.0.187:<port>/info`
- multicast group: `224.0.0.187`
- timeout before results are emitted: `500 ms`
- the implementation sends the multicast request twice for stability
- `<port>` is the port passed to the `Coala` constructor

## Observe

Server-side observable resource:

```kotlin
server.addObservableResource("temperature", object : CoAPResource.CoAPResourceHandler() {
    override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput {
        return CoAPResourceOutput(
            CoAPMessagePayload("23.4"),
            CoAPMessageCode.CoapCodeContent,
            CoAPMessage.MediaType.TextPlain,
        )
    }
})
```

Client-side registration:

```kotlin
client.registerObserver("coap://192.168.1.10:5683/temperature").subscribe(
    { payload -> println(payload) },
    { error -> error.printStackTrace() },
)
```

The Observe layer tracks registrations by token, processes Observe sequence
numbers, sends ACK for confirmable notifications, sends RST for unexpected
notifications, and removes registrations when observation stops.

## Secure Coala (`coaps`)

Use a URI with the `coaps` scheme. The handshake starts automatically:

```kotlin
val request = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST)
request.setURI("coaps://192.168.1.10:5683/secure")
request.payload = CoAPMessagePayload("encrypted payload")

coala.sendRequest(request).subscribe(
    { response -> println(response.payload) },
    { error -> error.printStackTrace() },
)
```

To pin the peer key, set `peerPublicKey` before sending:

```kotlin
request.peerPublicKey = expectedPeerPublicKey
```

Internally:

- Curve25519 key agreement.
- HKDF-SHA256 derives two AES keys and two IVs.
- Payload and encrypted URI are carried through Coala custom options.
- AES-GCM tag is truncated to 12 bytes for Coala compatibility.
- `PeerPublicKeyMismatchException` is raised when the actual key differs from
  `peerPublicKey`.

## Proxy

Set a proxy address on a message before sending:

```kotlin
val request = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
request.setURI("coap://192.168.1.10:5683/remote/info")
request.setProxy(InetSocketAddress("10.0.0.1", 5683))

coala.send(request).subscribe(
    { response -> println(response.payload) },
    { error -> error.printStackTrace() },
)
```

Proxy support uses `Proxy-Uri` and Coala `proxySecurityId` when secure sessions
are proxied.

## Blockwise and Large Payloads

Large payloads are split into Block1/Block2 segments. For Coala peers, the stack
also uses selective-repeat ARQ with option `OptionSelectiveRepeatWindowSize`
(`3001`) to send a window of blocks and reassemble the payload on the receiver.

Delivery and ARQ state can be inspected after a request:

```kotlin
val info = coala.getMessageDeliveryInfo(request)
println(info?.retransmitCount)
println(info?.dataSize)
println(info?.timeDiff)
```

## Logging

Register one or more loggers through `LogHelper`:

```kotlin
import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.helpers.logging.SystemOutLogger

LogHelper.addLogger(SystemOutLogger(""))
LogHelper.setLogLevel(LogHelper.LogLevel.DEBUG)
```

Custom logger:

```kotlin
import com.ndmsystems.coala.helpers.logging.ILogger

class AppLogger : ILogger {
    override fun v(message: String) = println("V $message")
    override fun d(message: String) = println("D $message")
    override fun i(message: String) = println("I $message")
    override fun w(message: String) = println("W $message")
    override fun e(message: String) = println("E $message")
}
```

## Serializer API

| API | Description |
| --- | --- |
| `CoAPSerializer.toBytes(message)` | Encodes `CoAPMessage` into a CoAP datagram. |
| `CoAPSerializer.fromBytes(data, addressFrom)` | Decodes a datagram into `CoAPMessage`. |
| `CoAPSerializer.DeserializeException` | Error raised for malformed datagrams. |

## How Coala Differs from CoAP

CoAP is a standard application protocol: message format, methods, response
codes, options, UDP transport, reliability model, Observe, Blockwise, and
discovery conventions. Coala Java uses the CoAP message model and wire format
for basic UDP datagrams, but adds compatibility with the Coala ecosystem.

Key differences:

- Discovery uses the Coala convention: multicast `224.0.0.187`, path `info`,
  and the port selected for the `Coala` instance. This is not the generic
  `/.well-known/core` discovery endpoint.
- `coaps` here is not DTLS. Secure mode is implemented at the Coala layer with
  Curve25519 handshake, HKDF-SHA256, AES-GCM, and custom CoAP options.
- Coala defines custom options: `OptionURIScheme` (`2111`),
  `OptionSelectiveRepeatWindowSize` (`3001`), `OptionWindowChangeable`
  (`3002`), `OptionProxySecurityID` (`3004`), `OptionCookie` (`3036`),
  `OptionHandshakeType` (`3999`), `OptionSessionNotFound` (`4001`),
  `OptionSessionExpired` (`4003`), `OptionCoapsURI` (`4005`), and
  `OptionChecksum` (`4006`).
- Large messages can use Coala selective-repeat ARQ on top of Block1/Block2,
  not only basic CoAP blockwise exchange.
- TCP transport uses a Coala proxy frame format, not RFC 8323 CoAP-over-TCP
  framing.
- The API is organized around an Android client/server object model: `Coala`,
  resources, RxJava callbacks, observe registry, message pool, and delivery
  metrics.

In short: regular CoAP peers can understand simple UDP CoAP datagrams, but Coala
features such as secure mode, Coala TCP proxy framing, selective-repeat ARQ, and
the discovery payload require Coala extension support on the other side.
