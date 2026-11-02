package com.example.data.proxy

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalDohProxyServer(private val dohResolver: DohResolver) {

    private val tag = "LocalDohProxyServer"
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newFixedThreadPool(24)
    private val isRunning = AtomicBoolean(false)
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    var port: Int = 0
        private set

    @Synchronized
    fun start(): Int {
        if (isRunning.get()) return port

        try {
            val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = server
            port = server.localPort
            isRunning.set(true)

            executor.execute {
                acceptConnections(server)
            }
            Log.i(tag, "Local DoH Proxy started on 127.0.0.1:$port")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start local proxy server: ${e.message}")
        }
        return port
    }

    private fun acceptConnections(server: ServerSocket) {
        while (isRunning.get() && !server.isClosed) {
            try {
                val clientSocket = server.accept()
                clientSocket.soTimeout = 60_000 // 60s idle timeout
                activeSockets.add(clientSocket)

                executor.execute {
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.w(tag, "Error accepting client socket: ${e.message}")
                }
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val requestLine = readLine(clientIn)
            if (requestLine.isNullOrBlank()) {
                closeSocket(clientSocket)
                return
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                closeSocket(clientSocket)
                return
            }

            val method = parts[0].uppercase()
            val target = parts[1]

            if (method == "CONNECT") {
                // HTTPS Tunneling: CONNECT host:port HTTP/1.1
                handleConnect(target, clientSocket, clientIn, clientOut)
            } else {
                // Plain HTTP Forwarding: GET http://host/path HTTP/1.1
                handlePlainHttp(method, target, parts.getOrNull(2) ?: "HTTP/1.1", clientSocket, clientIn, clientOut)
            }
        } catch (e: Exception) {
            send502AndClose(clientSocket)
        }
    }

    private fun handleConnect(target: String, clientSocket: Socket, clientIn: InputStream, clientOut: OutputStream) {
        // Drain remaining HTTP headers from CONNECT request
        while (true) {
            val line = readLine(clientIn)
            if (line.isNullOrEmpty()) break
        }

        val hostPort = target.split(":")
        val host = hostPort[0]
        val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 443

        val resolvedIps = dohResolver.lookup(host)
        if (resolvedIps.isEmpty()) {
            send502AndClose(clientSocket)
            return
        }

        var remoteSocket: Socket? = null
        for (ip in resolvedIps) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, port), 10_000) // 10s connect timeout
                s.soTimeout = 60_000
                remoteSocket = s
                break
            } catch (e: Exception) {
                // Try next resolved IP
            }
        }

        if (remoteSocket == null) {
            send502AndClose(clientSocket)
            return
        }

        activeSockets.add(remoteSocket)

        try {
            // Respond 200 Connection established
            val response = "HTTP/1.1 200 Connection established\r\n\r\n"
            clientOut.write(response.toByteArray(Charsets.ISO_8859_1))
            clientOut.flush()

            val remoteIn = remoteSocket.getInputStream()
            val remoteOut = remoteSocket.getOutputStream()

            // Bi-directional TCP byte piping
            val t1 = Thread { pipeStream(clientIn, remoteOut) }
            val t2 = Thread { pipeStream(remoteIn, clientOut) }

            t1.start()
            t2.start()

            t1.join()
            t2.join()
        } catch (e: Exception) {
            Log.w(tag, "Tunnel error for $host: ${e.message}")
        } finally {
            closeSocket(remoteSocket)
            closeSocket(clientSocket)
        }
    }

    private fun handlePlainHttp(
        method: String,
        target: String,
        httpVersion: String,
        clientSocket: Socket,
        clientIn: InputStream,
        clientOut: OutputStream
    ) {
        var host: String? = null
        var port = 80
        val headers = mutableListOf<String>()

        if (target.startsWith("http://", ignoreCase = true)) {
            val uri = java.net.URI(target)
            host = uri.host
            if (uri.port != -1) port = uri.port
        }

        while (true) {
            val line = readLine(clientIn) ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Host:", ignoreCase = true) && host == null) {
                val h = line.substring(5).trim()
                val parts = h.split(":")
                host = parts[0]
                if (parts.size > 1) port = parts[1].toIntOrNull() ?: 80
            }
            headers.add(line)
        }

        if (host.isNullOrBlank()) {
            send502AndClose(clientSocket)
            return
        }

        val resolvedIps = dohResolver.lookup(host)
        if (resolvedIps.isEmpty()) {
            send502AndClose(clientSocket)
            return
        }

        var remoteSocket: Socket? = null
        for (ip in resolvedIps) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, port), 10_000)
                s.soTimeout = 60_000
                remoteSocket = s
                break
            } catch (e: Exception) {
                // Try next
            }
        }

        if (remoteSocket == null) {
            send502AndClose(clientSocket)
            return
        }

        activeSockets.add(remoteSocket)

        try {
            val remoteOut = remoteSocket.getOutputStream()
            val remoteIn = remoteSocket.getInputStream()

            // Forward HTTP Request
            val reqPath = if (target.startsWith("http://", ignoreCase = true)) {
                java.net.URI(target).rawPath.ifBlank { "/" }
            } else target

            val firstLine = "$method $reqPath $httpVersion\r\n"
            remoteOut.write(firstLine.toByteArray(Charsets.ISO_8859_1))
            for (h in headers) {
                remoteOut.write("$h\r\n".toByteArray(Charsets.ISO_8859_1))
            }
            remoteOut.write("\r\n".toByteArray(Charsets.ISO_8859_1))
            remoteOut.flush()

            // Pipe response back
            pipeStream(remoteIn, clientOut)
        } catch (e: Exception) {
            Log.w(tag, "HTTP forward error for $host: ${e.message}")
        } finally {
            closeSocket(remoteSocket)
            closeSocket(clientSocket)
        }
    }

    private fun pipeStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (e: Exception) {
            // Stream closed or socket error
        }
    }

    private fun send502AndClose(socket: Socket) {
        try {
            val out = socket.getOutputStream()
            val response = "HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n502 Bad Gateway: DNS Resolution Failed via DoH\n"
            out.write(response.toByteArray(Charsets.ISO_8859_1))
            out.flush()
        } catch (e: Exception) {
            // Ignore write errors
        } finally {
            closeSocket(socket)
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            if (b == '\n'.code) {
                return sb.toString().trimEnd('\r')
            }
            sb.append(b.toChar())
        }
    }

    private fun closeSocket(socket: Socket?) {
        if (socket != null) {
            activeSockets.remove(socket)
            runCatching { socket.close() }
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        Log.i(tag, "Stopping local DoH proxy server...")
        runCatching { serverSocket?.close() }
        serverSocket = null

        activeSockets.forEach { closeSocket(it) }
        activeSockets.clear()

        executor.shutdownNow()
    }
}
