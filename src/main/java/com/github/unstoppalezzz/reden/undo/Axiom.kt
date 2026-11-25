package com.github.unstoppalezzz.reden.undo

import com.github.unstoppalezzz.reden.Reden
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.chunk.PalettedContainer
import java.lang.reflect.Method

object Axiom {
    private const val MOD_ID = "axiom"
    private const val BLOCK_BUFFER = "com.moulberry.axiom.world_modification.BlockBuffer"

    val loaded: Boolean by lazy { FabricLoader.getInstance().isModLoaded(MOD_ID) }

    private class BufferAccess(val blockBufferClass: Class<*>, val entrySet: Method, val emptyState: Any)

    private val access: BufferAccess? by lazy {
        if (!loaded) return@lazy null
        try {
            val blockBuffer = Class.forName(BLOCK_BUFFER)
            BufferAccess(
                blockBufferClass = blockBuffer,
                entrySet = blockBuffer.getMethod("entrySet"),
                emptyState = blockBuffer.getField("EMPTY_STATE").get(null),
            )
        } catch (e: Throwable) {
            Reden.LOGGER.warn("Axiom is loaded but its block buffer could not be read, Axiom buffer edits won't be undoable", e)
            null
        }
    }

    fun bufferPositions(buffer: Any, level: ServerLevel, limit: Int): LongArrayList? {
        val access = access ?: return null
        return try {
            if (!access.blockBufferClass.isInstance(buffer)) return null
            val empty = access.emptyState
            val out = LongArrayList()
            for (raw in access.entrySet.invoke(buffer) as Iterable<*>) {
                val entry = raw as Long2ObjectMap.Entry<*>
                val sectionKey = entry.longKey
                val sx = BlockPos.getX(sectionKey)
                val sy = BlockPos.getY(sectionKey)
                val sz = BlockPos.getZ(sectionKey)
                val sectionIndex = level.getSectionIndexFromSectionY(sy)
                if (sectionIndex < 0 || sectionIndex >= level.sectionsCount) continue
                val container = entry.value as PalettedContainer<*>
                for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16) {
                    if (container.get(x, y, z) === empty) continue
                    out.add(BlockPos.asLong((sx shl 4) + x, (sy shl 4) + y, (sz shl 4) + z))
                    if (out.size > limit) return out
                }
            }
            out
        } catch (e: Throwable) {
            Reden.LOGGER.warn("Failed to read Axiom block buffer, this edit won't be undoable", e)
            null
        }
    }
}
