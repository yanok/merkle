import io.github.yanok.MerkleBlock
import io.github.yanok.MerkleTreeCopy
import io.github.yanok.MerkleTreeSOT
import io.kotest.common.flatMap
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.triple
import io.kotest.property.checkAll
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.experimental.xor

class MerkleTreeTest : StringSpec({
    val size = 2048
    val nBlocks = 16
    val blockSize = size / nBlocks
    val data = ByteArray(size) { it.mod(256).toByte() }
    val sot = MerkleTreeSOT.withNumberOfBlocks(data, nBlocks)
    "create a tree and send a block to copy" {
        checkAll(Arb.int(0 ..< nBlocks)) { blockNr ->
            // new copy for each iteration to be able to test that other blocks are not there
            val copy = MerkleTreeCopy(sot.rootHash)
            sot.getBlockWithProof(blockNr).flatMap { (block, proof) ->
                copy.verifyAndAddBlock(block, proof)
            } shouldBeSuccess Unit
            copy.numberOfBlocks shouldBe nBlocks
            copy.getBlockWithProof(blockNr).flatMap { (block, proof) ->
                sot.getBlockWithProof(blockNr).map { (sotBlock, sotProof) ->
                    block shouldBe sotBlock
                    proof.hashes shouldBe sotProof.hashes
                }
            } shouldBeSuccess Unit
            (0 ..< 16).forEach { n ->
                if (n == blockNr) return@forEach
                copy.getBlockWithProof(n) shouldBeFailure { it.message shouldBe "We don't have block $n"}
            }
        }
    }
    "sending mutated block fails (flipping one bit)" {
        val copy = MerkleTreeCopy(sot.rootHash)
        checkAll(Arb.triple(Arb.int(0 ..< nBlocks), Arb.int(0 ..< blockSize), Arb.int(0 .. 7))) { (blockNr, pos, bitNr) ->
            val result1 = sot.getBlockWithProof(blockNr)
            result1 shouldBeSuccess { }
            result1.flatMap { (block, proof) ->
                val blockData = block.data.toByteArray()
                blockData[pos] = (blockData[pos].xor(1.shl(bitNr).toByte()))
                copy.verifyAndAddBlock(MerkleBlock(blockNr, blockSize, blockData), proof)
            } shouldBeFailure { it.message shouldBe "Verification failed, block rejected" }
        }
    }
    "sending mutated block fails (changing one byte)" {
        val copy = MerkleTreeCopy(sot.rootHash)
        checkAll(Arb.triple(Arb.int(0 ..< nBlocks), Arb.int(0 ..< blockSize), Arb.byte(min = 1))) { (blockNr, pos, diff) ->
            val result1 = sot.getBlockWithProof(blockNr)
            result1 shouldBeSuccess { }
            result1.flatMap { (block, proof) ->
                val blockData = block.data.toByteArray()
                blockData[pos] = (blockData[pos] + diff).toByte()
                copy.verifyAndAddBlock(MerkleBlock(blockNr, blockSize, blockData), proof)
            } shouldBeFailure { it.message shouldBe "Verification failed, block rejected" }
        }
    }
    "changing block number fails" {
        val copy = MerkleTreeCopy(sot.rootHash)
        checkAll(Arb.pair(Arb.int(0 ..< nBlocks), Arb.int(1 ..< nBlocks))) { (blockNr, diff) ->
            val result1 = sot.getBlockWithProof(blockNr)
            result1.map {  } shouldBeSuccess Unit
            result1.flatMap { (block, proof) ->
                val modifiedBlockNumber = (blockNr + diff).mod(nBlocks)
                copy.verifyAndAddBlock(MerkleBlock(modifiedBlockNumber, blockSize, block.data.toByteArray()), proof)
            } shouldBeFailure { it.message shouldBe "Verification failed, block rejected" }
        }
    }
    "send all the blocks concurrently" {
        val copy = MerkleTreeCopy(sot.rootHash)
        val blocksWithProofs = (0 ..< nBlocks).shuffled().map {
            val res = sot.getBlockWithProof(it)
            res shouldBeSuccess { }
            res.getOrThrow()
        }
        val failed = AtomicBoolean(false)
        val threads = blocksWithProofs.map { (block, proof) -> Thread {
            if (copy.verifyAndAddBlock(block, proof).isFailure) failed.set(true)
        }}
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        failed.get() shouldBe false
    }
})