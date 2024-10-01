package io.github.yanok

import org.kotlincrypto.hash.sha3.Keccak224
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

// A class representing a clone of a Merkle tree.
// We start with the root hash only (not even knowing the number of blocks) and
// accept incoming block with `verifyAndAddBlock`. As soon as the first block is verified,
// we know the size of the tree and start returning already collected blocks from
// `getBlockWithProof`.
// This class is thread safe. We have three atomic members: numBlocks, tree and blocks.
// Also, elements of tree and blocks are themselves atomic references.
// They are all written using the "update once" semantics, with the only possible update being
// changing from default value to non-default value.
// Why this is correct? For tree and blocks we only need to allocate them, we don't really care
// if another thread did it faster, we just need to use the value that ended up in atomic.
// For numBlocks and elements of tree and blocks: we only update them after we verified the proof,
// so we know that it must be one of:
// - there is no existing data in the tree, so we should put it there
// - there is existing data, but it's exactly the same, so it's safe to skip update
// - three is existing data, but it's not exactly the same, but it means that either someone broke
//   our hash function, or we have a bug in the algorithm.
class MerkleTreeCopy(override val rootHash: ByteArray) : MerkleTree {
    // Only updated once, when the first valid block is received
    private val numBlocks: AtomicInteger = AtomicInteger(0)
    // Allocated once, right before numBlocks is set
    private val tree: AtomicReference<ArrayTree<AtomicReference<ByteArray?>>?> = AtomicReference(null)
    private val blocks: AtomicReference<Array<AtomicReference<MerkleBlock?>>?> = AtomicReference(null)

    override val numberOfBlocks: Int?
        get() = when (val nr = numBlocks.get()) {
            0 -> null
            else -> nr
        }

    override fun getBlockWithProof(blockNumber: Int): Result<Pair<MerkleBlock, MerkleProof>> {
        val nb = numberOfBlocks ?: return Result.failure(IllegalStateException())
        if (blockNumber >= nb) return Result.failure(IndexOutOfBoundsException())
        val bs = blocks.get() ?: return Result.failure(InternalError()) // shouldn't happen
        val block = bs[blockNumber].get() ?: return Result.failure(NoSuchElementException())
        val t = tree.get() ?: return Result.failure(InternalError())

        // TODO: this part is the same as in MerkleTreeSOT, apart from having to deal with
        // AtomicReference... Generalize?
        var c = t.leaf(blockNumber)
        val proof = mutableListOf<ByteArray>()
        while (!c.isRoot()) {
            // If block was added to blocks, all the hashes for the proof are already where.
            proof.add(t[c.sibling()].get() ?: return Result.failure(InternalError()))
            c = c.parent()
        }
        return Result.success(block to MerkleProof(proof))
    }

    private fun verify(block: MerkleBlock, proof: MerkleProof):
            Result<Pair<Int, List<Pair<ArrayTree.Cursor, ByteArray>>>> {
        val leaves = 1.shl(proof.hashes.size)
        numberOfBlocks?.let {
            if (it != leaves) return@verify Result.failure(IllegalArgumentException(
                "Wrong number of blocks: proof suggests $leaves, while we expect $it"
            ))
        }
        val blockNr = block.blockNumber
        if (blockNr >= leaves) return Result.failure(IllegalArgumentException(
            "Block number $blockNr is too big, we expect only $leaves blocks"))
        var c = ArrayTree.Cursor.leaf(blockNr, outOf = leaves)
        var hash = block.hash
        val digest = Keccak224()
        val treeUpdate = mutableListOf(c to hash)
        proof.hashes.forEach { siblingHash ->
            treeUpdate.add(c.sibling() to siblingHash)
            digest.update(hash)
            digest.update(siblingHash)
            hash = digest.digest()
            digest.reset()
            c = c.parent()
            treeUpdate.add(c to hash)
        }
        if (!hash.contentEquals(rootHash)) return Result.failure(IllegalArgumentException(
                "Verification failed, block rejected"))
        return Result.success(leaves to treeUpdate)
    }
    override fun verifyAndAddBlock(block: MerkleBlock, proof: MerkleProof): Result<Unit> =
        verify(block, proof).map { (leaves, treeUpdate) ->
            // Here we verified the block, so we now know the tree size for sure. Let's create the tree,
            // if it doesn't exist yet.
            val tr = tree.get() ?: run {
                tree.compareAndSet(null, ArrayTree<AtomicReference<ByteArray?>>(leaves, AtomicReference()))
                // we don't care if we won the race, just use the winner tree
                // as an unfortunate side effect, we probably just allocated a big Array and threw it
                // away immediately.
                tree.get()!!
            }
            // we can update the tree in any order, we haven't yet added the block, so getBlockWithProof won't be
            // looking for new nodes.
            treeUpdate.forEach { (c, hash) ->
                // safe to skip if already non-null (if we trust our hash function, hashes that are parts of
                // verified proofs must be equal).
                tr[c].compareAndSet(null, hash)
                assert(hash.contentEquals(tr[c].get()))
            }
            // Create blocks array if it doesn't exist yet
            val blks = blocks.get() ?: run {
                blocks.compareAndSet(null, Array(leaves) { AtomicReference() })
                // same here, whoever was first wins
                blocks.get()!!
            }
            // add the block finally!
            blks[block.blockNumber].compareAndSet(null, block)
            assert(block == blks[block.blockNumber].get())
            // finally, set the number of blocks if not set yet
            numBlocks.compareAndSet(0, leaves)
            assert(leaves == numBlocks.get())
        }
}