import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

fun createServerSocket(port: Int) = ServerSocket(port)

fun main() {
    val socket = createServerSocket(port = 4444)

    while (true) {
        val clientSocket = socket.accept()

        thread {
            val inputStream = clientSocket.inputStream

            val version: Int = inputStream.read()
//        println("protocol = $version")

            val cmd: Int = inputStream.read()
//        println("cmd = $cmd")

            val port = (inputStream.read() shl 8) or inputStream.read()
//        println("port = $port")

            val ip = inputStream.readNBytes(4).joinToString(".") { (it.toInt() and 0xFF).toString() }
//        println("ip = $ip")

            while (inputStream.read() != 0);

            clientSocket.soTimeout = 100

            val outputStream = clientSocket.getOutputStream()

            val bytesToConnect: ByteArray = byteArrayOf(0, 0x5a, 0, 0, 0, 0, 0, 0)
            outputStream.write(bytesToConnect)

            proxy(clientSocket, ip, port, inputStream, outputStream)
        }
    }
}

fun transfer(buffer: ByteArray, source: InputStream, destination: OutputStream): Boolean {
    try {
        val n = source.read(buffer)

        if (n == -1)
            return false

        destination.write(buffer, 0, n)
    } catch (e: SocketTimeoutException) {
        return true
    } catch (e: SocketException) {
        return false
    }

    return true
}

fun proxy(clientSocket: Socket, ip: String, port: Int, clientInputStream: InputStream, clientOutputStream: OutputStream) {
    val upstreamSocket = Socket(ip, port)
    upstreamSocket.soTimeout = 100

    val upstreamOutputStream = upstreamSocket.getOutputStream()
    val upstreamInputStream = upstreamSocket.getInputStream()

    val buffer = ByteArray(8192)

    while (true) {
        if (!clientSocket.isClosed) {
            if (!transfer(buffer, clientInputStream, upstreamOutputStream))
                clientSocket.close()
        }

        if (!upstreamSocket.isClosed) {
            if (!transfer(buffer, upstreamInputStream, clientOutputStream))
                upstreamSocket.close()
        }

        if (clientSocket.isClosed && upstreamSocket.isClosed)
            break
    }

    println("done")
}