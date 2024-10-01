package io.github.yanok

import org.kotlincrypto.hash.sha3.Keccak224
import kotlin.math.max

// A class representing "Source of Truth" for some data.
// Given data and number of blocks it computes all the hashes of the Merkle tree.
// `getBlockWithProof` returns a block/proof pair for a valid block number.
// I decided to keep `verifyAndAddBlock` unimplemented, since we start with all the blocks,
// and thus it doesn't make much sense.
// After a second thought, I think it might be actually useful, since data and hashes are
// probably stored separately and some/all data may be deleted or become corrupted.
// Then it will be useful to accept blocks back. I didn't implement this here.
// This class is essentially immutable (underlying ArrayTree is mutable but is not mutated by any
// method), so trivially thread safe.
class MerkleTreeSOT(private val blocks: Array<MerkleBlock>) : MerkleTree {
    companion object {
        fun withNumberOfBlocks(bytes: ByteArray, numberOfBlocks: Int): MerkleTreeSOT {
            require(bytes.isNotEmpty()) { "bytes can't be empty" }
            require(numberOfBlocks > 0) { "number of blocks must be positive" }
            require(isPowOf2(numberOfBlocks)) { "number of blocks must be a power of two" }
            val bsize = (bytes.size + numberOfBlocks - 1).div(numberOfBlocks)
            val fullBlocks = numberOfBlocks - (bsize *  numberOfBlocks - bytes.size)
            val blocks = Array<MerkleBlock>(numberOfBlocks) { idx ->
                val offset = idx * bsize - max(idx - fullBlocks, 0)
                val size = if (idx < fullBlocks) bsize else bsize - 1
                MerkleBlock(idx, bsize, bytes.sliceArray(offset ..< offset + size))
            }
            return MerkleTreeSOT(blocks)
        }
    }
    override val numberOfBlocks: Int
        get() = blocks.size
    private val tree: ArrayTree<ByteArray>
    private fun buildTree() {
        var c = tree.leaf(0)
        blocks.forEach { block ->
            tree[c] = block.hash
            c = c.right()
        }
        c = tree.leaf(0).parent()
        val digest = Keccak224()
        while (true) {
            var c2 = c
            while (true) {
                digest.reset()
                digest.update(tree[c2.leftChild()])
                digest.update(tree[c2.rightChild()])
                tree[c2] = digest.digest()
                if (!c2.hasRight()) break
                c2 = c2.right()
            }
            if (c.isRoot()) break;
            c = c.parent()
        }
    }
    init {
        require(blocks.isNotEmpty()) { "Blocks can't be empty" }
        require(isPowOf2(blocks.size)) { "Number of blocks must be a power of two" }
        // We could also check the block size is the same for all blocks, but it's not strictly necessary.
        tree = ArrayTree(numberOfBlocks, ByteArray(0))
        buildTree()
    }

    override val rootHash: ByteArray
        get() = tree[tree.root]
    override fun verifyAndAddBlock(block: MerkleBlock, proof: MerkleProof): Result<Unit> =
        Result.failure(NotImplementedError("can't add block to the SOT tree."))

    override fun getBlockWithProof(blockNumber: Int): Result<Pair<MerkleBlock, MerkleProof>> {
        if (blockNumber >= numberOfBlocks) return Result.failure(IndexOutOfBoundsException())
        var c = tree.leaf(blockNumber)
        val proof = mutableListOf<ByteArray>()
        while (!c.isRoot()) {
            proof.add(tree[c.sibling()])
            c = c.parent()
        }
        return Result.success(blocks[blockNumber] to MerkleProof(proof))
    }
}