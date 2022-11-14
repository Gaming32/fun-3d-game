package io.github.gaming32.fungame

import io.github.gaming32.fungame.model.Model
import io.github.gaming32.fungame.obj.ObjLoader
import io.github.gaming32.fungame.util.*
import org.joml.Math.clamp
import org.joml.Math.toRadians
import org.joml.Vector2d
import org.joml.Vector2f
import org.joml.Vector2i
import org.joml.Vector3d
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NanoVGGL2.NVG_ANTIALIAS
import org.lwjgl.nanovg.NanoVGGL2.nvgCreate
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_MULTISAMPLE
import org.lwjgl.opengl.GLUtil
import java.text.DecimalFormat
import kotlin.math.roundToLong

class Application {
    companion object {
        private const val MOUSE_SPEED = 0.5
        private const val MOVE_SPEED = 7.0 // m/s
        private const val JUMP_SPEED = 6.0 // m/s
        private const val DRAG = 25.0
        private const val PHYSICS_SPEED = 0.02
        private const val GRAVITY = -11.0 // m/s/s
        private val DEC_FORMAT = DecimalFormat("0.0")
    }

    private val windowSize = Vector2i()
    private val position = Vector3d(0.0, 0.5, -5.0)
    private val motion = Vector3d()
    private val movementInput = Vector3d()
    private val rotation = Vector2f()
    private var wireframe = false
    private var window = 0L
    private var nanovg = 0L
    private var clearParams = GL_DEPTH_BUFFER_BIT
    private lateinit var objLoader: ObjLoader

    fun main() {
        init()
        registerEvents()
        val skybox = withValue(-1, TextureManager::maxMipmap, { TextureManager.maxMipmap = it }) {
            withValue(GL_NEAREST, TextureManager::filter, { TextureManager.filter = it }) {
                objLoader.loadObj("/skybox.obj").toDisplayList()
            }
        }
        val level = objLoader.loadObj("/example.obj")
        val levelList = level.toDisplayList()
        var lastTime = glfwGetTime()
        var lastPhysicsTime = lastTime
        val collisions = mutableListOf<Model.Tri>()
        var fpsAverage = 0.0
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents()
            glClear(clearParams)

            val time = glfwGetTime()
            val deltaTime = time - lastTime
            val fps = 1 / deltaTime
            fpsAverage = 0.95 * fpsAverage + 0.05 * fps
            lastTime = time

            if (time - lastPhysicsTime > 1.0) {
                lastPhysicsTime = time - 1.0
            }
            while (time - lastPhysicsTime > PHYSICS_SPEED) {
                val adjustedMovementInput = Vector3d(movementInput).rotateY(
                    toRadians(rotation.y.toDouble())
                )
                motion.x += adjustedMovementInput.x
                motion.z += adjustedMovementInput.z
                if (movementInput.y != 0.0) {
                    motion.y = movementInput.y
                    movementInput.y = 0.0
                }
                motion.x *= DRAG * PHYSICS_SPEED
                motion.z *= DRAG * PHYSICS_SPEED
                motion.y += GRAVITY * PHYSICS_SPEED
                position.x += motion.x * PHYSICS_SPEED
                position.y += motion.y * PHYSICS_SPEED
                position.z += motion.z * PHYSICS_SPEED
//                if (position.y <= 0) {
//                    position.y = 0.0
//                    motion.y = 0.0
//                }
                collisions.clear()
                collide(position, motion, level, collisions)
//                println("X: ${position.x} Y: ${position.y} Z: ${position.z} ROT: ${rotation.y}")
                lastPhysicsTime += PHYSICS_SPEED
            }

            if (position.y < -100) {
                position.set(0.0, 0.5, -5.0)
            }

            // 3D Mode
            glEnable(GL_CULL_FACE)
            glMatrixMode(GL_PROJECTION)
            glLoadIdentity()
            gluPerspective(80f, windowSize.x.toFloat() / windowSize.y, 0.01f, 1000f)
            glMatrixMode(GL_MODELVIEW)
            glLoadIdentity()
            glRotatef(rotation.x, 1f, 0f, 0f)
            glRotatef(180 - rotation.y, 0f, 1f, 0f)

            // Skybox
            skybox.draw()

            // Level
            glEnable(GL_DEPTH_TEST)
            glEnable(GL_LIGHTING)
            glTranslatef(-position.x.toFloat(), -position.y.toFloat() - 1.8f, -position.z.toFloat())
            glLightfv(
                GL_LIGHT0, GL_POSITION, floatArrayOf(
                    position.x.toFloat() - 12.9f,
                    position.y.toFloat() + 30f,
                    position.z.toFloat() + 17.1f,
                    0f
                )
            )

            levelList.draw(/* ModelBuilder(), collisions */)

            // HUD
            glMatrixMode(GL_PROJECTION)
            glLoadIdentity()
            glOrtho(0.0, windowSize.x.toDouble(), windowSize.y.toDouble(), 0.0, 1.0, 3.0)
            glMatrixMode(GL_MODELVIEW)
            glLoadIdentity()
            glTranslatef(0f, 0f, -2f)
            glDisable(GL_DEPTH_TEST)
            glDisable(GL_LIGHTING)

            val widthArray = IntArray(1)
            glfwGetFramebufferSize(window, widthArray, null)
            nvgBeginFrame(
                nanovg,
                windowSize.x.toFloat(), windowSize.y.toFloat(),
                widthArray[0].toFloat() / windowSize.x
            )
            nvgFontFace(nanovg, "minecraftia")
            nvgText(nanovg, 10f, 35f, "FPS: ${fpsAverage.roundToLong()}")
            nvgText(
                nanovg, 10f, 55f,
                "X/Y/Z: " +
                    "${DEC_FORMAT.format(position.x)}/" +
                    "${DEC_FORMAT.format(position.y)}/" +
                    DEC_FORMAT.format(position.z)
            )
            nvgEndFrame(nanovg)

            glfwSwapBuffers(window)
        }
        skybox.close()
        levelList.close()
        quit()
    }

    private fun init() {
        GLFWErrorCallback.createPrint().set()
        if (!glfwInit()) {
            throw Exception("Failed to initialize GLFW")
        }

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_SAMPLES, 4)
//        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE)

//        val monitor = glfwGetPrimaryMonitor()
//        val videoMode = glfwGetVideoMode(monitor) ?: throw Exception("Could not determine video mode")

        windowSize.set(1280, 720)
        window = glfwCreateWindow(windowSize.x, windowSize.y, "Fun 3D Game", 0, 0)
        glfwMakeContextCurrent(window)
        GL.createCapabilities()
        GLUtil.setupDebugMessageCallback()

        nanovg = nvgCreate(NVG_ANTIALIAS)
        loadFont(nanovg, "minecraftia")

        glfwSwapInterval(1)

        glfwSetWindowSizeCallback(window) { _, width, height ->
            windowSize.set(width, height)
            glViewport(0, 0, windowSize.x, windowSize.y)
        }
        glViewport(0, 0, windowSize.x, windowSize.y)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_MULTISAMPLE)

        glClearColor(0f, 0f, 0f, 1f)
        glShadeModel(GL_SMOOTH)
        glEnable(GL_LIGHT0)
        glMaterialfv(GL_FRONT, GL_AMBIENT, floatArrayOf(1f, 1f, 1f, 1f))
        glLineWidth(10f)

        objLoader = ObjLoader(TextureManager.getResource)
    }

    private fun registerEvents() {
        val lastMousePos = Vector2d(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
        glfwSetCursorPosCallback(window) { _, x, y ->
            if (
                lastMousePos.x != Double.POSITIVE_INFINITY &&
                glfwGetInputMode(window, GLFW_CURSOR) == GLFW_CURSOR_DISABLED
            ) {
                rotation.y -= ((x - lastMousePos.x) * MOUSE_SPEED).toFloat()
                rotation.x = clamp(-90f, 90f, rotation.x + ((y - lastMousePos.y) * MOUSE_SPEED).toFloat())
            }
            lastMousePos.set(x, y)
        }

        glfwSetMouseButtonCallback(window) { _, _, action, _ ->
            if (action == GLFW_PRESS) {
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
            }
        }

        glfwSetKeyCallback(window) { _, key, _, action, _ ->
            if (action == GLFW_REPEAT) return@glfwSetKeyCallback
            val press = action == GLFW_PRESS
            if (glfwGetInputMode(window, GLFW_CURSOR) != GLFW_CURSOR_DISABLED) {
                return@glfwSetKeyCallback
            }
            when (key) {
                GLFW_KEY_ESCAPE -> if (press) {
                    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL)
                    movementInput.x = 0.0
                    movementInput.z = 0.0
                }
                GLFW_KEY_W -> if (press) {
                    movementInput.z += MOVE_SPEED
                } else {
                    movementInput.z -= MOVE_SPEED
                }
                GLFW_KEY_S -> if (press) {
                    movementInput.z -= MOVE_SPEED
                } else {
                    movementInput.z += MOVE_SPEED
                }
                GLFW_KEY_A -> if (press) {
                    movementInput.x += MOVE_SPEED
                } else {
                    movementInput.x -= MOVE_SPEED
                }
                GLFW_KEY_D -> if (press) {
                    movementInput.x -= MOVE_SPEED
                } else {
                    movementInput.x += MOVE_SPEED
                }
                GLFW_KEY_SPACE -> if (press /* && position.y <= 0 */) {
                    movementInput.y = JUMP_SPEED
                }
                GLFW_KEY_F3 -> if (press) {
                    wireframe = !wireframe
                    @Suppress("LiftReturnOrAssignment")
                    if (wireframe) {
                        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)
                        glLineWidth(2f)
                        clearParams = GL_DEPTH_BUFFER_BIT or GL_COLOR_BUFFER_BIT
                    } else {
                        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL)
                        glLineWidth(10f)
                        clearParams = GL_DEPTH_BUFFER_BIT
                    }
                }
            }
        }
    }

    private fun quit() {
        TextureManager.unload()
        glfwFreeCallbacks(window)
        glfwDestroyWindow(window)
        glfwTerminate()
        glfwSetErrorCallback(null)?.free()
    }
}

fun main() = Application().main()
