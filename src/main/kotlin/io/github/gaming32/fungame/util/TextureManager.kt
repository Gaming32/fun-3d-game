package io.github.gaming32.fungame.util

import org.imgscalr.Scalr
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL
import org.lwjgl.system.MemoryUtil
import java.io.InputStream
import java.nio.ByteOrder
import javax.imageio.ImageIO

object TextureManager {
    val getResource: (String) -> InputStream? = object {}::class.java::getResourceAsStream
    var maxMipmap = 4
    var filter = GL_LINEAR
    var mipmapFilter = GL_LINEAR_MIPMAP_LINEAR
    var wrap = GL_REPEAT

    private val textures = mutableMapOf<String, Int>()

    fun getTexture(name: String) = textures.computeIfAbsent(name) { key ->
        val tex = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, tex)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, if (maxMipmap == -1) filter else mipmapFilter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap)
        if (maxMipmap != -1) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, maxMipmap)
        }
        val image = ImageIO.read(getResource(key))
        var width = image.width
        var height = image.height
        val rgba = MemoryUtil.memAlloc(width * height * 4).order(ByteOrder.BIG_ENDIAN)
        for (pixel in image.getRGB(0, 0, width, height, null, 0, width)) {
            rgba.putInt((pixel shl 8) or (pixel ushr 24))
        }
        rgba.flip()
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba)
        if (maxMipmap != -1) {
            for (i in 1..maxMipmap) {
                width /= 2
                height /= 2
                for (pixel in Scalr.resize(image, width, height).getRGB(0, 0, width, height, null, 0, width)) {
                    rgba.putInt((pixel shl 8) or (pixel ushr 24))
                }
                rgba.flip()
                glTexImage2D(GL_TEXTURE_2D, i, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba)
            }
        }
        MemoryUtil.memFree(rgba)
        tex
    }

    fun unload() {
        textures.values.forEach { glDeleteTextures(it) }
        textures.clear()
    }
}
