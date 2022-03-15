package com.query.cache.definitions.impl

import com.query.Application
import com.query.Application.logger
import com.query.Constants.library
import com.query.cache.Loader
import com.query.cache.Serializable
import com.query.cache.definitions.Definition
import com.query.dump.DefinitionsTypes
import com.query.utils.ConfigType
import com.query.utils.IndexType
import com.query.utils.index
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch


data class OverlayDefinition(
    override val id: Int = 0,
    var rgbColor: Int = 0,
    var secondaryRgbColor: Int = -1,
    var textureId: Int = -1,
    var hideUnderlay: Boolean = true,
    var hue: Int = 0,
    var saturation: Int = 0,
    var lightness: Int = 0,
    var otherHue: Int = 0,
    var otherSaturation: Int = 0,
    var otherLightness: Int = 0
): Definition {

    fun calculateHsl() {
        if (secondaryRgbColor != -1) {
            calculateHsl(secondaryRgbColor)
            otherHue = hue
            otherSaturation = saturation
            otherLightness = lightness
        }
        calculateHsl(rgbColor)
    }



    private fun calculateHsl(var1: Int) {
        val var2 = (var1 shr 16 and 255).toDouble() / 256.0
        val var4 = (var1 shr 8 and 255).toDouble() / 256.0
        val var6 = (var1 and 255).toDouble() / 256.0
        var var8 = var2
        if (var4 < var2) {
            var8 = var4
        }
        if (var6 < var8) {
            var8 = var6
        }
        var var10 = var2
        if (var4 > var2) {
            var10 = var4
        }
        if (var6 > var10) {
            var10 = var6
        }
        var var12 = 0.0
        var var14 = 0.0
        val var16 = (var8 + var10) / 2.0
        if (var10 != var8) {
            if (var16 < 0.5) {
                var14 = (var10 - var8) / (var10 + var8)
            }
            if (var16 >= 0.5) {
                var14 = (var10 - var8) / (2.0 - var10 - var8)
            }
            if (var2 == var10) {
                var12 = (var4 - var6) / (var10 - var8)
            } else if (var4 == var10) {
                var12 = 2.0 + (var6 - var2) / (var10 - var8)
            } else if (var10 == var6) {
                var12 = 4.0 + (var2 - var4) / (var10 - var8)
            }
        }
        var12 /= 6.0
        hue = (256.0 * var12).toInt()
        saturation = (var14 * 256.0).toInt()
        lightness = (var16 * 256.0).toInt()
        if (saturation < 0) {
            saturation = 0
        } else if (saturation > 255) {
            saturation = 255
        }
        if (lightness < 0) {
            lightness = 0
        } else if (lightness > 255) {
            lightness = 255
        }
    }

}

class OverlayProvider(val latch: CountDownLatch?, val writeTypes : Boolean = true) : Loader, Runnable {

    override val revisionMin = 1

    override fun run() {
        val start: Long = System.currentTimeMillis()
        Application.store(OverlayDefinition::class.java, load().definition)
        Application.prompt(this::class.java, start)
        latch?.countDown()
    }

    override fun load(): Serializable {
        val archive = library.index(IndexType.CONFIGS).archive(ConfigType.OVERLAY.id)!!
        val definitions = archive.fileIds().map {
           decode(ByteBuffer.wrap(archive.file(it)?.data), OverlayDefinition(it))
        }
        return Serializable(DefinitionsTypes.OVERLAYS,this, definitions,writeTypes)
    }

    fun decode(buffer: ByteBuffer, definition: OverlayDefinition): Definition {
        do when (val opcode: Int = buffer.get().toInt() and 0xff) {
            1 -> definition.rgbColor = (((buffer.get().toInt() and 0xff) shl 16) + ((buffer.get().toInt() and 0xff) shl 8) + (buffer.get().toInt() and 0xff))
            2 -> definition.textureId = buffer.get().toInt() and 0xff
            5 -> definition.hideUnderlay = false
            7 -> definition.secondaryRgbColor = (((buffer.get().toInt() and 0xff) shl 16) + ((buffer.get().toInt() and 0xff) shl 8) + (buffer.get().toInt() and 0xff))
            0 -> break
            else -> logger.warn { "Unhandled overlay definition opcode with id: ${opcode}." }
        } while (true)
        definition.calculateHsl()
        return definition
    }


}