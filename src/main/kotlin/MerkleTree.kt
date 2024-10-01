package io.github.yanok

interface MerkleTree {
    val rootHash: ByteArray
    val numberOfBlocks: Int?
    fun getBlockWithProof(blockNumber: Int): Result<Pair<MerkleBlock, MerkleProof>>
    fun verifyAndAddBlock(block: MerkleBlock, proof: MerkleProof): Result<Unit>
}

