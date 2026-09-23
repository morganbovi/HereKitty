package io.sweatshop.herekitty.relaybridge

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.sweatshop.herekitty.adb.AdbHostClient
import io.sweatshop.herekitty.domain.base.Log
import io.sweatshop.herekitty.domain.features.auth.AuthRepository
import io.sweatshop.herekitty.domain.features.relay.RelayBridgeRepository
import io.sweatshop.herekitty.domain.features.relay.RelayBridgeStatus
import io.sweatshop.herekitty.domain.features.relay.RelayTransport
import io.sweatshop.herekitty.net.DesktopConfig
import io.sweatshop.herekitty.net.HttpFetch
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.jmdns.JmDNS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Single

private const val CLAIM_RELAY_SESSION_URL =
    "https://us-central1-herekitty-mobile.cloudfunctions.net/claimRelaySession"
private const val RELEASE_RELAY_SESSION_URL =
    "https://us-central1-herekitty-mobile.cloudfunctions.net/releaseRelaySession"
private const val ACCEPT_TIMEOUT_MILLIS = 15_000L
private const val RECONNECT_DELAY_MILLIS = 4_000L
private const val MAX_RECONNECT_ATTEMPTS = 3

@Single(binds = [RelayBridgeRepository::class])
class RelayBridgeRepositoryImpl(
    private val authRepository: AuthRepository,
    private val adbHostClient: AdbHostClient,
) : RelayBridgeRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val wsClient = HttpClient(CIO) { install(WebSockets) }
    private val sessions = mutableMapOf<String, BridgeSession>()

    private val _statuses = MutableStateFlow<Map<String, RelayBridgeStatus>>(emptyMap())
    override val statuses: StateFlow<Map<String, RelayBridgeStatus>> = _statuses.asStateFlow()

    private val _connectedEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val connectedEvents: SharedFlow<String> = _connectedEvents.asSharedFlow()

    override suspend fun connect(deviceId: String) = start(deviceId, retryAttempt = 0)

    private suspend fun start(deviceId: String, retryAttempt: Int) {
        if (sessions.containsKey(deviceId)) return
        setStatus(deviceId, RelayBridgeStatus.Claiming)

        val serverSocket = ServerSocket()
        val session = BridgeSession(serverSocket)
        session.job = scope.launch {
            var hostPort: String? = null
            var claim: RelaySessionClaim? = null
            try {
                withContext(Dispatchers.IO) {
                    serverSocket.reuseAddress = true
                    serverSocket.bind(InetSocketAddress("127.0.0.1", 0))
                }
                val boundHostPort = "127.0.0.1:${serverSocket.localPort}"
                hostPort = boundHostPort

                val claimedSession = claimSession(deviceId)
                claim = claimedSession

                setStatus(deviceId, RelayBridgeStatus.Bridging)

                val connectDeferred = async { adbHostClient.connect(boundHostPort) }

                val accepted = withTimeoutOrNull(ACCEPT_TIMEOUT_MILLIS) {
                    withContext(Dispatchers.IO) { serverSocket.accept() }
                } ?: run {
                    connectDeferred.cancel()
                    error("Timed out waiting for adb to dial the bridged port")
                }
                session.acceptedSocket = accepted

                var transport = RelayTransport.Relay
                val bridgeJob = launch {
                    val localBridge = discoverLocalBridge(claimedSession.relaySessionId)
                    if (localBridge != null) {
                        transport = RelayTransport.LocalNetwork
                        setStatus(deviceId, RelayBridgeStatus.Bridging)
                        try {
                            bridgeDirect(accepted, localBridge, claimedSession.relayToken)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            transport = RelayTransport.Relay
                            bridgeViaRelay(accepted, claimedSession)
                        }
                    } else {
                        bridgeViaRelay(accepted, claimedSession)
                    }
                }

                val connectOutcome = withTimeoutOrNull(ACCEPT_TIMEOUT_MILLIS) { connectDeferred.await() }
                if (connectOutcome == null || connectOutcome.isFailure) {
                    bridgeJob.cancel()
                    error(
                        connectOutcome?.exceptionOrNull()?.message
                            ?: "Timed out waiting for adb's handshake with the bridged device",
                    )
                }

                setStatus(deviceId, RelayBridgeStatus.Connected(boundHostPort, transport))
                _connectedEvents.emit(boundHostPort)
                bridgeJob.join()
                error("Phone connection ended")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(e) { "Relay bridge for $deviceId failed" }
                if (retryAttempt < MAX_RECONNECT_ATTEMPTS) {
                    val nextAttempt = retryAttempt + 1
                    setStatus(deviceId, RelayBridgeStatus.Reconnecting(nextAttempt))
                    scope.launch {
                        delay(RECONNECT_DELAY_MILLIS)
                        start(deviceId, retryAttempt = nextAttempt)
                    }
                } else {
                    setStatus(deviceId, RelayBridgeStatus.Failed("Couldn't reconnect. ${e.message ?: "Try again."}"))
                }
            } finally {
                claim?.let { releaseSession(deviceId, it) }
                hostPort?.let { runCatching { adbHostClient.disconnect(it) } }
                runCatching { session.acceptedSocket?.close() }
                runCatching { serverSocket.close() }
                sessions.remove(deviceId, session)
                _statuses.update { current ->
                    if (current[deviceId] is RelayBridgeStatus.Failed || current[deviceId] is RelayBridgeStatus.Reconnecting) current
                    else current - deviceId
                }
            }
        }
        sessions[deviceId] = session
    }

    override suspend fun disconnect(deviceId: String) {
        val session = sessions.remove(deviceId) ?: return
        session.job?.cancel()
        runCatching { session.acceptedSocket?.close() }
        runCatching { session.serverSocket.close() }
        session.job?.join()
        _statuses.update { it - deviceId }
    }

    private suspend fun bridge(socket: Socket, relayUrl: String, relaySessionId: String, relayToken: String) {
        wsClient.webSocket(
            request = {
                url("${relayUrl.removeSuffix("/")}/bridge/$relaySessionId")
                parameter("token", relayToken)
            },
        ) {
            val toRelay = launch {
                val buffer = ByteArray(8192)
                while (true) {
                    val n = withContext(Dispatchers.IO) { socket.getInputStream().read(buffer) }
                    if (n < 0) break
                    send(Frame.Binary(true, buffer.copyOf(n)))
                }
            }
            val fromRelay = launch {
                for (frame in incoming) {
                    if (frame is Frame.Binary) {
                        withContext(Dispatchers.IO) {
                            socket.getOutputStream().write(frame.readBytes())
                            socket.getOutputStream().flush()
                        }
                    }
                }
            }
            toRelay.invokeOnCompletion { fromRelay.cancel() }
            fromRelay.invokeOnCompletion { toRelay.cancel() }
            joinAll(toRelay, fromRelay)
        }
    }

    private suspend fun bridgeViaRelay(socket: Socket, claim: RelaySessionClaim) {
        val relayUrl = DesktopConfig.relayUrl
            ?: error("No local bridge found and relayHost is not configured")
        bridge(socket, relayUrl, claim.relaySessionId, claim.relayToken)
    }

    private suspend fun discoverLocalBridge(relaySessionId: String): InetSocketAddress? = withContext(Dispatchers.IO) {
        runCatching {
            JmDNS.create().use { mdns ->
                val service = mdns.list("_herekitty._tcp.local.", 2_500)
                    .firstOrNull { it.name == "herekitty-$relaySessionId" }
                    ?: return@use null
                val address = service.inet4Addresses.firstOrNull() ?: return@use null
                InetSocketAddress(address, service.port)
            }
        }.getOrNull()
    }

    private suspend fun bridgeDirect(socket: Socket, endpoint: InetSocketAddress, relayToken: String) {
        withContext(Dispatchers.IO) {
            Socket().use { phone ->
                phone.connect(endpoint, 3_000)
                phone.getOutputStream().write("HEREKITTY $relayToken\n".encodeToByteArray())
                phone.getOutputStream().flush()
                val toPhone = scope.launch {
                    socket.getInputStream().copyTo(phone.getOutputStream())
                }
                val fromPhone = scope.launch {
                    phone.getInputStream().copyTo(socket.getOutputStream())
                }
                toPhone.invokeOnCompletion { fromPhone.cancel() }
                fromPhone.invokeOnCompletion { toPhone.cancel() }
                joinAll(toPhone, fromPhone)
            }
        }
    }

    private suspend fun claimSession(deviceId: String): RelaySessionClaim {
        val token = authRepository.idToken() ?: error("Not signed in")
        val response = HttpFetch.postJson(
            CLAIM_RELAY_SESSION_URL,
            """{"data":{"deviceId":"$deviceId"}}""",
            headers = mapOf("Authorization" to "Bearer $token"),
        )
        val result = json.parseToJsonElement(response).jsonObject["result"]?.jsonObject
            ?: error("Unexpected response from claimRelaySession")
        return RelaySessionClaim(
            relaySessionId = result.getValue("relaySessionId").jsonPrimitive.content,
            relayToken = result.getValue("relayToken").jsonPrimitive.content,
        )
    }

    private suspend fun releaseSession(deviceId: String, claim: RelaySessionClaim) {
        runCatching {
            val token = authRepository.idToken() ?: return
            HttpFetch.postJson(
                RELEASE_RELAY_SESSION_URL,
                """{"data":{"deviceId":"$deviceId","relaySessionId":"${claim.relaySessionId}"}}""",
                headers = mapOf("Authorization" to "Bearer $token"),
            )
        }.onFailure { error -> Log.w(error) { "Unable to release relay session for $deviceId" } }
    }

    private fun setStatus(deviceId: String, status: RelayBridgeStatus) {
        _statuses.update { it + (deviceId to status) }
    }

    private class BridgeSession(val serverSocket: ServerSocket) {
        var job: Job? = null
        @Volatile var acceptedSocket: Socket? = null
    }

    private data class RelaySessionClaim(val relaySessionId: String, val relayToken: String)
}
