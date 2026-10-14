package com.github.unstoppalezzz.reden.undo

import com.github.unstoppalezzz.reden.access.PlayerData
import com.github.unstoppalezzz.reden.network.Undo
import com.github.unstoppalezzz.reden.utils.multiver.Text
import com.github.unstoppalezzz.reden.utils.server
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.entity.EntityType
//? if >=26.2 {
import net.minecraft.world.entity.EntityTypes
//?} else {
/*import net.minecraft.world.entity.EntityType as EntityTypes
*///?}
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.LecternBlockEntity
//? if >=1.20.5
import net.minecraft.world.RandomizableContainer
//? if >=1.21.11
import net.minecraft.server.permissions.Permissions
import java.lang.reflect.Method
import java.util.*
import kotlin.math.min

object SurvivalUndo {
    const val PERMISSION = "reden.undo.survival"

    class Info(val level: ServerLevel, val inventoryBefore: List<ItemStack>) {
        var inventoryAfter: List<ItemStack>? = null
        val droppedItems: MutableMap<UUID, ItemStack> = mutableMapOf()
        val spawnedTypes: MutableMap<UUID, EntityType<*>> = mutableMapOf()
        val containersAfter: MutableMap<Long, List<ItemStack>> = mutableMapOf()
    }

    class ItemDelta {
        class Entry(val prototype: ItemStack, var amount: Int)

        val entries = mutableListOf<Entry>()

        fun add(stack: ItemStack, amount: Int) {
            if (stack.isEmpty || amount == 0) return
            val entry = entries.firstOrNull { sameItem(it.prototype, stack) }
            if (entry != null) entry.amount += amount
            else entries += Entry(stack.copyWithCount(1), amount)
        }

        fun addAll(other: ItemDelta, sign: Int = 1) {
            other.entries.forEach { add(it.prototype, it.amount * sign) }
        }
    }

    sealed interface Plan {
        class Ok(val inventoryPart: ItemDelta, val total: ItemDelta) : Plan
        class Refused(val status: Int) : Plan
        class Missing(val items: List<Pair<ItemStack, Int>>) : Plan
    }

    private val permissionValueMethod: Method? by lazy {
        if (!FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0")) return@lazy null
        runCatching {
            Class.forName("me.lucko.fabric.api.permissions.v0.Permissions").methods.firstOrNull {
                it.name == "getPermissionValue" && it.parameterCount == 2 &&
                    it.parameterTypes[0].isAssignableFrom(ServerPlayer::class.java) &&
                    it.parameterTypes[1] == String::class.java
            }
        }.getOrNull()
    }

    fun hasPermission(player: ServerPlayer): Boolean {
        permissionValueMethod?.let { method ->
            when (runCatching { method.invoke(null, player, PERMISSION) }.getOrNull()?.toString()) {
                "TRUE" -> return true
                "FALSE" -> return false
            }
        }
        //? if >=1.21.9 {
        if (server.isSingleplayerOwner(player.nameAndId())) return true
        //?} else {
        /*if (server.isSingleplayerOwner(player.gameProfile)) return true
        *///?}
        //? if >=1.21.11 {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
        //?} else {
        /*return player.hasPermissions(2)
        *///?}
    }

    fun startRecord(player: ServerPlayer, record: PlayerData.UndoRecord) {
        if (player.isCreative) return
        record.survival = Info(player.serverWorld(), snapshotInventory(player))
    }

    fun stopRecord(player: ServerPlayer, record: PlayerData.UndoRecord) {
        val info = record.survival ?: return
        info.inventoryAfter = snapshotInventory(player)
        info.spawnedTypes.keys.removeIf { info.level.getEntity(it) == null }
        snapshotContainers(record, info)
    }

    fun snapshotContainers(record: PlayerData.UndoRecord, info: Info) {
        record.data.keys.forEach { posLong ->
            val items = info.level.getBlockEntity(BlockPos.of(posLong))?.let(::containedItems) ?: return@forEach
            info.containersAfter[posLong] = items
        }
    }

    fun planUndo(world: ServerLevel, player: ServerPlayer, record: PlayerData.UndoRecord): Plan {
        val info = record.survival ?: return Plan.Refused(Undo.STATUS_CREATIVE_RECORD)
        for ((uuid, type) in info.spawnedTypes) {
            if (isLooseDrop(type) || type == EntityTypes.TNT) continue
            if (world.getEntity(uuid) == null) return Plan.Refused(Undo.STATUS_ENTITY_GONE)
        }

        val inventoryPart = ItemDelta()
        info.inventoryAfter?.let { after ->
            info.inventoryBefore.forEach { inventoryPart.add(it, it.count) }
            after.forEach { inventoryPart.add(it, -it.count) }
        }
        info.droppedItems.forEach { (uuid, dropped) ->
            val onGround = (world.getEntity(uuid) as? ItemEntity)?.item
                ?.takeIf { sameItem(it, dropped) }?.count ?: 0
            val pickedUp = dropped.count - onGround
            if (pickedUp > 0) inventoryPart.add(dropped, -pickedUp)
        }

        val total = ItemDelta()
        total.addAll(inventoryPart)
        containerDelta(world, record, info.containersAfter, isUndo = true, total)
        return check(player, inventoryPart, total)
    }

    fun planRedo(world: ServerLevel, player: ServerPlayer, record: PlayerData.RedoRecord): Plan {
        val undone = record.survivalLedger ?: return Plan.Refused(Undo.STATUS_CREATIVE_RECORD)
        val inventoryPart = ItemDelta().apply { addAll(undone, -1) }
        val total = ItemDelta()
        total.addAll(inventoryPart)
        containerDelta(world, record, record.containersBaseline, isUndo = false, total)
        return check(player, inventoryPart, total)
    }

    fun afterUndoApplied(world: ServerLevel, redo: PlayerData.RedoRecord, inventoryPart: ItemDelta) {
        redo.survivalLedger = inventoryPart
        redo.data.keys.forEach { posLong ->
            val items = world.getBlockEntity(BlockPos.of(posLong))?.let(::containedItems) ?: return@forEach
            redo.containersBaseline[posLong] = items
        }
    }

    private fun containerDelta(
        world: ServerLevel,
        record: PlayerData.UndoRedoRecord,
        baseline: Map<Long, List<ItemStack>>,
        isUndo: Boolean,
        delta: ItemDelta
    ) {
        record.data.forEach { (posLong, entry) ->
            val pos = BlockPos.of(posLong)
            if (!Undo.willRestore(world, pos, entry, isUndo)) return@forEach
            if (!world.getBlockState(pos).`is`(entry.state.block)) return@forEach
            val current = world.getBlockEntity(pos)?.let(::containedItems) ?: return@forEach
            val before = baseline[posLong]
                ?: entry.beType?.let { Undo.loadBlockEntity(world, pos, entry) }?.let(::containedItems)
                ?: emptyList()
            current.forEach { delta.add(it, it.count) }
            before.forEach { delta.add(it, -it.count) }
        }
    }

    private fun check(player: ServerPlayer, inventoryPart: ItemDelta, total: ItemDelta): Plan {
        val missing = total.entries.mapNotNull { entry ->
            if (entry.amount >= 0) return@mapNotNull null
            val lacking = -entry.amount - countInInventory(player, entry.prototype)
            if (lacking > 0) entry.prototype to lacking else null
        }
        if (missing.isNotEmpty()) return Plan.Missing(missing)
        return Plan.Ok(inventoryPart, total)
    }

    fun missingMessage(plan: Plan.Missing): Component {
        val list = Text.empty()
        plan.items.forEachIndexed { i, (stack, count) ->
            if (i > 0) list.append(", ")
            list.append("${count}x ").append(stack.hoverName)
        }
        return Text.translatable(
            "reden.message.undo.base",
            Text.translatable("reden.message.undo.missing_items", list)
        )
    }

    fun apply(player: ServerPlayer, total: ItemDelta) {
        total.entries.filter { it.amount < 0 }.forEach { take(player, it.prototype, -it.amount) }
        total.entries.filter { it.amount > 0 }.forEach { give(player, it.prototype, it.amount) }
        player.inventory.setChanged()
    }

    fun droppedStack(record: PlayerData.UndoRedoRecord, uuid: UUID): ItemStack? =
        (record as? PlayerData.UndoRecord)?.survival?.droppedItems?.get(uuid)

    private fun take(player: ServerPlayer, prototype: ItemStack, amount: Int) {
        val inventory = player.inventory
        var left = amount
        for (slot in 0 until inventory.containerSize) {
            if (left <= 0) break
            val stack = inventory.getItem(slot)
            if (!sameItem(stack, prototype)) continue
            val n = min(left, stack.count)
            inventory.removeItem(slot, n)
            left -= n
        }
    }

    private fun give(player: ServerPlayer, prototype: ItemStack, amount: Int) {
        var left = amount
        while (left > 0) {
            val stack = prototype.copyWithCount(min(left, prototype.maxStackSize))
            left -= stack.count
            player.inventory.add(stack)
            if (!stack.isEmpty) {
                //? if >=26.3 {
                player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY)
                //?} else {
                /*player.drop(stack, false)
                *///?}
            }
        }
    }

    fun isLooseDrop(type: EntityType<*>?) = type == EntityTypes.ITEM || type == EntityTypes.EXPERIENCE_ORB

    fun sameItem(a: ItemStack, b: ItemStack): Boolean =
        //? if >=1.20.5 {
        ItemStack.isSameItemSameComponents(a, b)
        //?} else {
        /*ItemStack.isSameItemSameTags(a, b)
        *///?}

    private fun snapshotInventory(player: ServerPlayer): List<ItemStack> {
        val inventory = player.inventory
        val items = (0 until inventory.containerSize).map { inventory.getItem(it) }
            .filterNot { it.isEmpty }
            .mapTo(mutableListOf()) { it.copy() }
        player.containerMenu.carried.takeUnless { it.isEmpty }?.let { items += it.copy() }
        return items
    }

    private fun countInInventory(player: ServerPlayer, prototype: ItemStack): Int {
        val inventory = player.inventory
        return (0 until inventory.containerSize).sumOf { slot ->
            inventory.getItem(slot).let { if (sameItem(it, prototype)) it.count else 0 }
        }
    }

    fun containedItems(be: BlockEntity): List<ItemStack>? {
        //? if >=1.20.5 {
        if (be is RandomizableContainer && be.lootTable != null) return null
        //?}
        return when (be) {
            is Container -> (0 until be.containerSize).map { be.getItem(it) }
                .filterNot { it.isEmpty }.map { it.copy() }
            is LecternBlockEntity -> listOfNotNull(be.book.takeUnless { it.isEmpty }?.copy())
            else -> null
        }
    }

    private fun ServerPlayer.serverWorld(): ServerLevel =
        //? if >=1.21.6 {
        level()
        //?} else {
        /*serverLevel()
        *///?}
}
