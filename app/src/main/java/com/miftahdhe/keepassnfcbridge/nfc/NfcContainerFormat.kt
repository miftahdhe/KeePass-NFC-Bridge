package com.miftahdhe.keepassnfcbridge.nfc

import java.nio.ByteBuffer
import java.security.MessageDigest

class ContainerException(message: String) : Exception(message)

data class ContainerData(
    val version: Byte,
    val keyLength: Int,
    val checksum: ByteArray,
    val keyData: ByteArray
) {
    fun isValidChecksum(): Boolean {
        val md = MessageDigest.getInstance("SHA-256")
        val fullHash = md.digest(keyData)
        val actualChecksum = fullHash.copyOf(4)
        return actualChecksum.contentEquals(checksum)
    }
}

object NfcContainerFormat {
    val HEADER = "KPNFC01".toByteArray(Charsets.US_ASCII)
    const val CURRENT_VERSION: Byte = 0x01
    const val METADATA_SIZE = 14 // 7 header + 1 version + 2 length + 4 checksum

    fun pack(keyFile: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(METADATA_SIZE + keyFile.size)
        buffer.put(HEADER)
        buffer.put(CURRENT_VERSION)
        buffer.putShort(keyFile.size.toShort())
        
        val md = MessageDigest.getInstance("SHA-256")
        val fullHash = md.digest(keyFile)
        buffer.put(fullHash, 0, 4) // First 4 bytes as checksum
        
        buffer.put(keyFile)
        return buffer.array()
    }
    
    fun unpack(data: ByteArray): ContainerData {
        if (data.size < METADATA_SIZE) {
            throw ContainerException("Data too small to be a container")
        }
        
        val buffer = ByteBuffer.wrap(data)
        val headerBytes = ByteArray(HEADER.size)
        buffer.get(headerBytes)
        
        if (!headerBytes.contentEquals(HEADER)) {
            throw ContainerException("Invalid header. Not a KPNFC01 container.")
        }
        
        val version = buffer.get()
        if (version > CURRENT_VERSION) {
            throw ContainerException("Unsupported version: $version")
        }
        
        val keyLen = buffer.short.toInt()
        if (keyLen <= 0 || METADATA_SIZE + keyLen > data.size) {
            throw ContainerException("Invalid container size or corrupted data.")
        }
        
        val checksum = ByteArray(4)
        buffer.get(checksum)
        
        val keyData = ByteArray(keyLen)
        buffer.get(keyData)
        
        val container = ContainerData(version, keyLen, checksum, keyData)
        if (!container.isValidChecksum()) {
            throw ContainerException("Container checksum mismatch! Data may be corrupted.")
        }
        
        return container
    }
}
