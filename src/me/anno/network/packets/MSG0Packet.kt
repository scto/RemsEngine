package me.anno.network.packets

import me.anno.network.Packet
import me.anno.network.Server
import me.anno.network.TCPClient
import java.io.DataInputStream
import java.io.DataOutputStream

abstract class MSG0Packet(magic: String = "MSG0") : Packet(magic) {

    var sender = 0L
    var senderName = ""

    var receiver = 0L
    var receiverName = ""

    var message = ""

    override val constantSize: Boolean = false

    override fun sendData(server: Server?, client: TCPClient, dos: DataOutputStream) {
        dos.writeLong(sender)
        dos.writeUTF(senderName)
        dos.writeLong(receiver)
        dos.writeUTF(receiverName)
        dos.writeUTF(message)
    }

    override fun receiveData(server: Server?, client: TCPClient, dis: DataInputStream, size: Int) {
        sender = dis.readLong()
        senderName = dis.readUTF()
        receiver = dis.readLong()
        receiverName = dis.readUTF()
        message = dis.readUTF()
        onReceive(server, client)
    }

    abstract fun onReceive(server: Server?, client: TCPClient)

}