package com.lmqr.ha9_comp_service.command_runners

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class UnixDomainSocketClient(private val socketName: String) {
    private var socket: LocalSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    fun connect() {
        try {
            socket = LocalSocket().apply {
                connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
            }
            outputStream = socket?.outputStream
            inputStream = socket?.inputStream
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun sendCommand(command: String) {
        if (socket?.isConnected != true) {
            disconnect()
            connect()
        }
        try {
            outputStream?.write((command + "\n").toByteArray())
            outputStream?.flush()
        } catch (e: IOException) {
            e.printStackTrace()
            disconnect()
            connect()
            try {
                outputStream?.write((command + "\n").toByteArray())
                outputStream?.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /** Only "ver" is answered; a daemon too old to know it stays silent until the timeout. */
    fun query(command: String, timeoutMs: Int): String? {
        sendCommand(command)
        return try {
            socket?.soTimeout = timeoutMs
            readLine()
        } catch (e: IOException) {
            null
        } finally {
            try {
                socket?.soTimeout = 0
            } catch (_: IOException) {
            }
        }
    }

    private fun readLine(): String? {
        val input = inputStream ?: return null
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte == -1) return null
            if (byte == '\n'.code) return line.toString()
            line.append(byte.toChar())
        }
    }

    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
            inputStream = null
            outputStream = null
            socket = null
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

class UnixSocketCommandRunner : CommandRunner {
    private val client by lazy { UnixDomainSocketClient("a9_eink_socket").apply { connect() } }

    override fun runCommands(cmds: Array<String>) {
        for (cmd in cmds)
            client.sendCommand(cmd)
    }

    override fun query(command: String): String? = client.query(command, QUERY_TIMEOUT_MS)

    override fun onDestroy() {
        client.disconnect()
    }
}

private const val QUERY_TIMEOUT_MS = 500
