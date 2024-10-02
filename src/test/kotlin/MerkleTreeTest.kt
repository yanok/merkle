import io.github.yanok.MerkleTreeCopy
import io.github.yanok.MerkleTreeSOT
import io.kotest.common.flatMap
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.forAll

class MerkleTreeTest : StringSpec({
    "create a tree and send a block to copy" {
        val data = ByteArray(2048) { it.mod(256).toByte() }
        val sot = MerkleTreeSOT.withNumberOfBlocks(data, 16)
        val copy = MerkleTreeCopy(sot.rootHash)
        checkAll(Arb.int(0 ..< 16)) { blockNr ->
            sot.getBlockWithProof(blockNr).flatMap { (block, proof) ->
                copy.verifyAndAddBlock(block, proof)
            } shouldBeSuccess Unit
            copy.numberOfBlocks shouldBe 16
        }
    }
})