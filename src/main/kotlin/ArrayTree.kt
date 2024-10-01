package io.github.yanok

class ArrayTree<T>(private val store: Array<T>) {
    val size = store.size
    companion object {
        inline operator fun <reified T> invoke(numberOfLeaves: Int, default: T) =
            ArrayTree(store = Array(2*numberOfLeaves) { default })
    }
    init {
        require(size > 1) { "ArrayTree can't be empty"}
        require(isPowOf2(size)) { "ArrayTree must be a full binary tree" }
    }
    @JvmInline
    value class Cursor(val position: Int) {
        fun parent() = Cursor(position/2)
        fun leftChild() = Cursor(position*2)
        fun rightChild() = Cursor(position*2 + 1)
        fun isRoot() = position == 1
        fun sibling() = when (position.and(1) == 0) {
            true -> Cursor(position+1)
            else -> Cursor(position-1)
        }
        fun hasLeft() = !isPowOf2(position)
        fun hasRight() = !isPowOf2(position+1)
        fun left() = Cursor(position-1)
        fun right() = Cursor(position+1)
        companion object {
            val root = Cursor(1)
            fun leaf(n: Int, outOf: Int) = Cursor(outOf + n)
        }
    }
    fun leaf(n: Int) = Cursor.leaf(n, size/2)
    val root = Cursor.root

    operator fun get(cursor: Cursor) = store[cursor.position]
    operator fun set(cursor: Cursor, value: T) {
        store[cursor.position] = value
    }
}