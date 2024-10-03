package io.github.yanok

import org.kotlincrypto.hash.sha3.Keccak224

data class MerkleBlock(val blockNumber: Int, val blockSize: Int, private val bytes: ByteArray) {
    private val headerString = "[#$blockNumber/$blockSize/${bytes.size}]"
    val hash: ByteArray
    val data = bytes.asList() // to prevent modification
    init {
        require(bytes.size <= blockSize) { "actual size must be smaller or equal than the block size" }
        // Creating a local Digest here for simplicity, in production, if blocks are created often,
        // we might want to have a per-thread Digest or a pool of Digest instances.
        val digest = Keccak224()
        digest.update(headerString.toByteArray())
        digest.update(bytes)
        hash = digest.digest();
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MerkleBlock

        if (blockNumber != other.blockNumber) return false
        if (blockSize != other.blockSize) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blockNumber
        result = 31 * result + blockSize
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}