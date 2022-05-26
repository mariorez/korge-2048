import com.soywiz.klock.seconds
import com.soywiz.korev.Key
import com.soywiz.korge.animate.Animator
import com.soywiz.korge.animate.animateSequence
import com.soywiz.korge.input.keys
import com.soywiz.korge.input.onClick
import com.soywiz.korge.input.onOut
import com.soywiz.korge.input.onOver
import com.soywiz.korge.tween.get
import com.soywiz.korge.view.Container
import com.soywiz.korge.view.Stage
import com.soywiz.korge.view.centerBetween
import com.soywiz.korge.view.container
import com.soywiz.korge.view.position
import com.soywiz.korge.view.roundRect
import com.soywiz.korge.view.text
import com.soywiz.korim.color.Colors
import com.soywiz.korim.color.RGBA
import com.soywiz.korio.async.launchImmediately
import com.soywiz.korma.interpolation.Easing
import kotlin.random.Random

fun columnX(number: Int) = leftIndent + 10 + (cellSize + 10) * number

fun rowY(number: Int) = topIndent + 10 + (cellSize + 10) * number

fun numberFor(blockId: Int) = blocks[blockId]!!.number

fun Container.createNewBlockWithId(id: Int, number: Number, position: Position) {
    blocks[id] = block(number).position(columnX(position.x), rowY(position.y))
}

fun Container.createNewBlock(number: Number, position: Position): Int {
    val id = freeId++
    createNewBlockWithId(id, number, position)
    return id
}

fun Container.generateBlock() {
    val position = map.getRandomFreePosition() ?: return
    val number = if (Random.nextDouble() < 0.9) Number.ZERO else Number.ONE
    val newId = createNewBlock(number, position)
    map[position.x, position.y] = newId
}

fun deleteBlock(blockId: Int) = blocks.remove(blockId)!!.removeFromParent()

fun Stage.moveBlocksTo(direction: Direction) {
    if (isAnimationRunning) return
    if (!map.hasAvailableMoves()) {
        if (!isGameOver) {
            isGameOver = true
            showGameOver {
                isGameOver = false
                restart()
            }
        }
        return
    }

    val moves = mutableListOf<Pair<Int, Position>>()
    val merges = mutableListOf<Triple<Int, Int, Position>>()

    val newMap = calculateNewMap(map.copy(), direction, moves, merges)

    if (map != newMap) {
        isAnimationRunning = true
        showAnimation(moves, merges) {
            // when animation ends
            map = newMap
            generateBlock()
            isAnimationRunning = false
        }
    }
}

fun calculateNewMap(
    map: PositionMap,
    direction: Direction,
    moves: MutableList<Pair<Int, Position>>,
    merges: MutableList<Triple<Int, Int, Position>>
): PositionMap {
    val newMap = PositionMap()
    val startIndex = when (direction) {
        Direction.LEFT, Direction.TOP -> 0
        Direction.RIGHT, Direction.BOTTOM -> 3
    }
    var columnRow = startIndex

    fun newPosition(line: Int) = when (direction) {
        Direction.LEFT -> Position(columnRow++, line)
        Direction.RIGHT -> Position(columnRow--, line)
        Direction.TOP -> Position(line, columnRow++)
        Direction.BOTTOM -> Position(line, columnRow--)
    }

    for (line in 0..3) {
        var curPos = map.getNotEmptyPositionFrom(direction, line)
        columnRow = startIndex
        while (curPos != null) {
            val newPos = newPosition(line)
            val curId = map[curPos.x, curPos.y]
            map[curPos.x, curPos.y] = -1

            val nextPos = map.getNotEmptyPositionFrom(direction, line)
            val nextId = nextPos?.let { map[it.x, it.y] }
            //two blocks are equal
            if (nextId != null && numberFor(curId) == numberFor(nextId)) {
                //merge these blocks
                map[nextPos.x, nextPos.y] = -1
                newMap[newPos.x, newPos.y] = curId
                merges += Triple(curId, nextId, newPos)
            } else {
                //add old block
                newMap[newPos.x, newPos.y] = curId
                moves += Pair(curId, newPos)
            }
            curPos = map.getNotEmptyPositionFrom(direction, line)
        }
    }
    return newMap
}

fun Stage.showAnimation(
    moves: List<Pair<Int, Position>>,
    merges: List<Triple<Int, Int, Position>>,
    onEnd: () -> Unit
) = launchImmediately {
    animateSequence {
        parallel {
            moves.forEach { (id, pos) ->
                blocks[id]!!.moveTo(columnX(pos.x), rowY(pos.y), 0.15.seconds, Easing.LINEAR)
            }
            merges.forEach { (id1, id2, pos) ->
                sequence {
                    parallel {
                        blocks[id1]!!.moveTo(columnX(pos.x), rowY(pos.y), 0.15.seconds, Easing.LINEAR)
                        blocks[id2]!!.moveTo(columnX(pos.x), rowY(pos.y), 0.15.seconds, Easing.LINEAR)
                    }
                    block {
                        val nextNumber = numberFor(id1).next()
                        deleteBlock(id1)
                        deleteBlock(id2)
                        createNewBlockWithId(id1, nextNumber, pos)
                    }
                    sequenceLazy {
                        animateScale(blocks[id1]!!)
                    }
                }
            }
        }
        block {
            onEnd()
        }
    }
}

fun Animator.animateScale(block: Block) {
    val x = block.x
    val y = block.y
    val scale = block.scale
    tween(
        block::x[x - 4],
        block::y[y - 4],
        block::scale[scale + 0.1],
        time = 0.1.seconds,
        easing = Easing.LINEAR
    )
    tween(
        block::x[x],
        block::y[y],
        block::scale[scale],
        time = 0.1.seconds,
        easing = Easing.LINEAR
    )
}

fun Container.showGameOver(onRestart: () -> Unit) = container {

    fun restart() {
        this@container.removeFromParent()
        onRestart()
    }

    position(leftIndent, topIndent)

    roundRect(fieldSize, fieldSize, 5.0, fill = Colors["#FFFFFF33"])

    text("Game Over", 60.0, Colors.BLACK, font) {
        centerBetween(0.0, 0.0, fieldSize, fieldSize)
        y -= 40
    }

    text("Try again", 40.0, Colors.BLACK, font) {
        centerBetween(0.0, 0.0, fieldSize, fieldSize)
        onOver { color = RGBA(90, 90, 90) }
        onOut { color = RGBA(120, 120, 120) }
        onClick { restart() }
    }

    keys {
        down {
            when (it.key) {
                Key.ENTER, Key.SPACE -> restart()
                else -> Unit
            }
        }
    }
}

fun Container.restart() {
    map = PositionMap()
    blocks.values.forEach { it.removeFromParent() }
    blocks.clear()
    generateBlock()
}