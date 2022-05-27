import com.soywiz.korev.Key
import com.soywiz.korge.Korge
import com.soywiz.korge.input.SwipeDirection
import com.soywiz.korge.input.keys
import com.soywiz.korge.input.onClick
import com.soywiz.korge.input.onSwipe
import com.soywiz.korge.service.storage.storage
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
import com.soywiz.korio.async.ObservableProperty
import com.soywiz.korio.file.std.resourcesVfs
import com.soywiz.korma.geom.Rectangle
import kotlin.properties.Delegates

var cellSize: Double = 0.0
var fieldSize: Double = 0.0
var leftIndent: Double = 0.0
var topIndent: Double = 0.0
var font: BitmapFont by Delegates.notNull()

var map = PositionMap()
val blocks = mutableMapOf<Int, Block>()
var freeId = 0
var history: History by Delegates.notNull()

var isAnimationRunning = false
var isGameOver = false

val score = ObservableProperty(0)
val best = ObservableProperty(0)

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

    /* SCORE *********************************************/
    val storage = views.storage
    best.update(storage.getOrNull("best")?.toInt() ?: 0)
    history = History(storage.getOrNull("history")) {
        storage["history"] = it.toString()
    }

    score.observe {
        if (it > best.value) best.update(it)
    }

    best.observe {
        storage["best"] = it.toString()
    }

    /* LAYOUT ********************************************/
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

    text(best.value.toString(), cellSize * 0.5, Colors.WHITE, font) {
        setTextBounds(Rectangle(0.0, 0.0, bgBest.width, cellSize - 24.0))
        alignment = TextAlignment.MIDDLE_CENTER
        alignTopToTopOf(bgBest, 12.0)
        centerXOn(bgBest)
        best.observe {
            text = it.toString()
        }
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

    text(score.value.toString(), cellSize * 0.5, Colors.WHITE, font) {
        setTextBounds(Rectangle(0.0, 0.0, bgScore.width, cellSize - 24.0))
        alignment = TextAlignment.MIDDLE_CENTER
        centerXOn(bgScore)
        alignTopToTopOf(bgScore, 12.0)
        score.observe {
            text = it.toString()
        }
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
        onClick {
            this@Korge.restart()
        }
    }

    val undoBlock = container {
        val background = roundRect(btnSize, btnSize, 5.0, fill = RGBA(185, 174, 160))
        image(undoImg) {
            size(btnSize * 0.6, btnSize * 0.6)
            centerOn(background)
        }
        alignTopToTopOf(restartBlock)
        alignRightToLeftOf(restartBlock, 5.0)
        onClick {
            this@Korge.restoreField(history.undo())
        }
    }

    /* GAME CONTROL *****************************************/
    if (history.isEmpty()) {
        generateBlockAndSave()
    } else {
        restoreField(history.currentElement)
    }

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
