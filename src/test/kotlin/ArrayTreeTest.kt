import io.github.yanok.ArrayTree
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.forAll

val leafNrAndSizeGen = Arb.int(1..8).flatMap { logSz ->
    val sz = 1.shl(logSz)
    Arb.int(0 ..< sz).map { n -> n to sz }
}

val nonRootAndSizeGen = Arb.int(1 .. 8).flatMap { logSz ->
    val sz = 1.shl(logSz)
    Arb.int(2 ..< sz).map { n -> n to sz }
}

fun height(cursor: ArrayTree.Cursor): Int {
    var h = 0
    var c = cursor
    while (!c.isRoot()) {
        c = c.parent()
        h++
    }
    return h
}

class CursorTest : StringSpec({
    "root is root" {
        ArrayTree.Cursor.root.isRoot()
    }
    "leaf of 1 element tree is root" {
        ArrayTree.Cursor.leaf(0, 1).isRoot()
    }
    "leaves of many element trees are never roots" {
        forAll(leafNrAndSizeGen){ (n, sz) -> !ArrayTree.Cursor.leaf(n, sz).isRoot() }
    }
    "sibling of a sibling is the original" {
        forAll(Arb.int(2 ..< 1024)) { n ->
            val c = ArrayTree.Cursor(n)
            c.sibling().sibling() == c
        }
    }
    "siblings share their parent" {
        forAll(Arb.int(2 ..< 1024)) { n ->
            val c = ArrayTree.Cursor(n)
            c.parent() == c.sibling().parent()
        }
    }
    "parents children are siblings" {
        forAll(Arb.int(1 ..< 1024)) { n ->
            val c = ArrayTree.Cursor(n)
            c.leftChild().sibling() == c.rightChild()
        }
    }
    "root has no left" {
        !ArrayTree.Cursor.root.hasLeft()
    }
    "root has no right" {
        !ArrayTree.Cursor.root.hasRight()
    }
    "left edge has no left" {
        forAll(Arb.int(1 .. 10)) { n ->
            var c = ArrayTree.Cursor.root
            (1..n).all {
                c = c.leftChild()
                !c.hasLeft()
            }
        }
    }
    "right edge has no right" {
        forAll(Arb.int(1 .. 10)) { n ->
            var c = ArrayTree.Cursor.root
            (1..n).all {
                c = c.rightChild()
                !c.hasRight()
            }
        }
    }
    "left preserves height if hasLeft" {
        forAll(Arb.int(2 ..< 1024)) { n ->
            val c = ArrayTree.Cursor(n)
            (!c.hasLeft()).or(height(c) == height(c.left()))
        }
    }
    "right preserves height if hasRight" {
        forAll(Arb.int(2 ..< 1024)) { n ->
            val c = ArrayTree.Cursor(n)
            (!c.hasRight()).or(height(c) == height(c.right()))
        }
    }
})

fun walkLayersBR(tree: ArrayTree<Int>): List<Int> {
    var layer = tree.root
    val lastLeaf = tree.leaf(tree.size / 2 - 1)
    val out = mutableListOf<Int>()
    do {
        var c = layer
        while (true) {
            out.add(tree[c])
            if (!c.hasRight()) break
            c = c.right()
        }
        layer = layer.leftChild()
    } while (c != lastLeaf)
    return out
}

fun walkLayersTL(tree: ArrayTree<Int>): List<Int> {
    var layer = tree.leaf(tree.size / 2 - 1)
    val out = mutableListOf<Int>()
    do {
        var c = layer
        while (true) {
            out.add(tree[c])
            if (!c.hasLeft()) break
            c = c.left()
        }
        layer = layer.parent()
    } while (c != tree.root)
    return out
}

class ArrayTreeTest : StringSpec({
    "root is root" {
        forAll(Arb.int(1..8)) { n ->
            val tree = ArrayTree(1.shl(n), 0)
            tree.root.isRoot()
        }
    }
    "walk by layer top-to-bottom left-to-right is correct" {
        forAll(Arb.int(1 .. 10)) { n ->
            val sz = 1.shl(n)
            val arr = Array(sz) { it }
            val out = walkLayersBR(ArrayTree(arr))
            out.withIndex().all { it.index + 1 == it.value }
        }
    }
    "walk by layer bottom-to-top right-to-left is correct" {
        forAll(Arb.int(1 .. 10)) { n ->
            val sz = 1.shl(n)
            val arr = Array(sz) { it }
            val out = walkLayersTL(ArrayTree(arr))
            out.reversed().withIndex().all { it.index + 1 == it.value }
        }
    }
})