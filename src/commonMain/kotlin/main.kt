import Number.ONE
import Number.ZERO
import com.soywiz.korev.Key
import com.soywiz.korge.Korge
import com.soywiz.korge.input.SwipeDirection
import com.soywiz.korge.input.keys
import com.soywiz.korge.input.onSwipe
import com.soywiz.korge.view.Container
import com.soywiz.korge.view.Stage
import com.soywiz.korge.view.alignRightToLeftOf
import com.soywiz.korge.view.alignRightToRightOf
import com.soywiz.korge.view.alignTopToBottomOf
import com.soywiz.korge.view.alignTopToTopOf
import com.soywiz.korge.view.centerOn
import com.soywiz.korge.view.centerXOn
import com.soywiz.korge.view.container
import com.soywiz.korge.view.image
import com.soywiz.korge.view.position
import com.soywiz.korge.view.roundRect
import com.soywiz.korge.view.size
import com.soywiz.korge.view.text
import com.soywiz.korim.color.Colors
import com.soywiz.korim.color.RGBA
import com.soywiz.korim.font.BitmapFont
import com.soywiz.korim.font.readBitmapFont
import com.soywiz.korim.format.readBitmap
import com.soywiz.korim.text.TextAlignment
import com.soywiz.korio.file.std.resourcesVfs
import com.soywiz.korma.geom.Rectangle
import kotlin.properties.Delegates
import kotlin.random.Random

var cellSize: Double = 0.0
var fieldSize: Double = 0.0
var leftIndent: Double = 0.0
var topIndent: Double = 0.0
var font: BitmapFont by Delegates.notNull()

var map = PositionMap()
val blocks = mutableMapOf<Int, Block>()
var freeId = 0

suspend fun main() = Korge(width = 480, height = 640, title = "2048", bgcolor = RGBA(253, 247, 240)) {

    /* ASSETS ********************************************/
    val restartImg = resourcesVfs["restart.png"].readBitmap()
    val undoImg = resourcesVfs["undo.png"].readBitmap()
    font = resourcesVfs["clear_sans.fnt"].readBitmapFont()

    /* CONFIG ********************************************/
    cellSize = views.virtualWidth / 5.0
    fieldSize = 50 + 4 * cellSize
    leftIndent = (views.virtualWidth - fieldSize) / 2
    topIndent = 150.0

    val bgField = roundRect(fieldSize, fieldSize, 5.0, fill = Colors["#b9aea0"]) {
        position(leftIndent, topIndent)
    }

    for (i in 0..3) {
        for (j in 0..3) {
            roundRect(cellSize, cellSize, 5.0, fill = Colors["#cec0b2"]) {
                x = leftIndent + 10 + (cellSize + 10) * i
                y = topIndent + 10 + (cellSize + 10) * j
            }
        }
    }

    /* LOGO **********************************************/
    val bgLogo = roundRect(cellSize, cellSize, 5.0, fill = Colors["#edc403"]) {
        position(leftIndent, 30.0)
    }

    text("2048", cellSize * 0.5, Colors.WHITE, font).centerOn(bgLogo)

    /* BEST SCORE ****************************************/
    val bgBest = roundRect(cellSize * 1.5, cellSize * 0.8, 5.0, fill = Colors["#bbae9e"]) {
        alignRightToRightOf(bgField)
        alignTopToTopOf(bgLogo)
    }

    text("BEST", cellSize * 0.25, RGBA(239, 226, 210), font) {
        centerXOn(bgBest)
        alignTopToTopOf(bgBest, 5.0)
    }

    text("0", cellSize * 0.5, Colors.WHITE, font) {
        setTextBounds(Rectangle(0.0, 0.0, bgBest.width, cellSize - 24.0))
        alignment = TextAlignment.MIDDLE_CENTER
        alignTopToTopOf(bgBest, 12.0)
        centerXOn(bgBest)
    }

    /* CURRENT SCORE *************************************/
    val bgScore = roundRect(cellSize * 1.5, cellSize * 0.8, 5.0, fill = Colors["#bbae9e"]) {
        alignRightToLeftOf(bgBest, 24)
        alignTopToTopOf(bgBest)
    }

    text("SCORE", cellSize * 0.25, RGBA(239, 226, 210), font) {
        centerXOn(bgScore)
        alignTopToTopOf(bgScore, 5.0)
    }

    text("0", cellSize * 0.5, Colors.WHITE, font) {
        setTextBounds(Rectangle(0.0, 0.0, bgScore.width, cellSize - 24.0))
        alignment = TextAlignment.MIDDLE_CENTER
        centerXOn(bgScore)
        alignTopToTopOf(bgScore, 12.0)
    }

    /* BUTTONS *******************************************/
    val btnSize = cellSize * 0.3

    val restartBlock = container {
        val background = roundRect(btnSize, btnSize, 5.0, fill = RGBA(185, 174, 160))
        image(restartImg) {
            size(btnSize * 0.8, btnSize * 0.8)
            centerOn(background)
        }
        alignTopToBottomOf(bgBest, 5)
        alignRightToRightOf(bgField)
    }

    val undoBlock = container {
        val background = roundRect(btnSize, btnSize, 5.0, fill = RGBA(185, 174, 160))
        image(undoImg) {
            size(btnSize * 0.6, btnSize * 0.6)
            centerOn(background)
        }
        alignTopToTopOf(restartBlock)
        alignRightToLeftOf(restartBlock, 5.0)
    }

    /* GAME RULE *****************************************/
    generateBlock()

    keys {
        down {
            when (it.key) {
                Key.LEFT -> moveBlocksTo(Direction.LEFT)
                Key.RIGHT -> moveBlocksTo(Direction.RIGHT)
                Key.UP -> moveBlocksTo(Direction.TOP)
                Key.DOWN -> moveBlocksTo(Direction.BOTTOM)
                else -> Unit
            }
        }
    }

    onSwipe(20.0) {
        when (it.direction) {
            SwipeDirection.LEFT -> moveBlocksTo(Direction.LEFT)
            SwipeDirection.RIGHT -> moveBlocksTo(Direction.RIGHT)
            SwipeDirection.TOP -> moveBlocksTo(Direction.TOP)
            SwipeDirection.BOTTOM -> moveBlocksTo(Direction.BOTTOM)
        }
    }
}

fun columnX(number: Int) = leftIndent + 10 + (cellSize + 10) * number

fun rowY(number: Int) = topIndent + 10 + (cellSize + 10) * number

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
    val number = if (Random.nextDouble() < 0.9) ZERO else ONE
    val newId = createNewBlock(number, position)
    map[position.x, position.y] = newId
}

fun Stage.moveBlocksTo(direction: Direction) {
    println(direction)
}
