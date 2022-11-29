package at.petrak.hexcasting.common.casting.operators.spells.great

import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.api.spell.*
import at.petrak.hexcasting.api.spell.casting.CastingContext
import at.petrak.hexcasting.api.spell.iota.Iota
import at.petrak.hexcasting.api.spell.mishaps.MishapImmuneEntity
import at.petrak.hexcasting.api.spell.mishaps.MishapLocationTooFarAway
import at.petrak.hexcasting.common.lib.HexEntityTags
import at.petrak.hexcasting.common.network.MsgBlinkAck
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.TicketType
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3

// TODO while we're making breaking changes I *really* want to have the vector in the entity's local space
// WRT its look vector
object OpTeleport : SpellAction {
    override val argc = 2
    override val isGreat = true
    override fun execute(
        args: List<Iota>,
        ctx: CastingContext
    ): Triple<RenderedSpell, Int, List<ParticleSpray>> {
        val teleportee = args.getEntity(0, argc)
        val delta = args.getVec3(1, argc)
        ctx.assertEntityInRange(teleportee)

        if (!teleportee.canChangeDimensions() || teleportee.type.`is`(HexEntityTags.CANNOT_TELEPORT))
            throw MishapImmuneEntity(teleportee)

        val targetPos = teleportee.position().add(delta)
        ctx.assertVecInWorld(targetPos)
        if (!ctx.isVecInWorld(targetPos.subtract(0.0, 1.0, 0.0)))
            throw MishapLocationTooFarAway(targetPos, "too_close_to_out")

        val targetMiddlePos = teleportee.position().add(0.0, teleportee.eyeHeight / 2.0, 0.0)


        return Triple(
            Spell(teleportee, delta),
            10 * MediaConstants.CRYSTAL_UNIT,
            listOf(ParticleSpray.cloud(targetMiddlePos, 2.0), ParticleSpray.burst(targetMiddlePos.add(delta), 2.0))
        )
    }

    private data class Spell(val teleportee: Entity, val delta: Vec3) : RenderedSpell {
        override fun cast(ctx: CastingContext) {
            val distance = delta.length()

            // TODO make this not a magic number (config?)
            if (distance < 32768.0) {
                teleportRespectSticky(teleportee, delta, ctx.world)
            }

            if (teleportee is ServerPlayer && teleportee == ctx.caster) {
                // Drop items conditionally, based on distance teleported.
                // MOST IMPORTANT: Never drop main hand item, since if it's a trinket, it will get duplicated later.

                val baseDropChance = distance / 10000.0

                // Armor and hotbar items have a further reduced chance to be dropped since it's particularly annoying
                // having to rearrange those. Also it makes sense for LORE REASONS probably, since the caster is more
                // aware of items they use often.
                for (armorItem in teleportee.inventory.armor) {
                    if (EnchantmentHelper.hasBindingCurse(armorItem))
                        continue

                    if (Math.random() < baseDropChance * 0.25) {
                        teleportee.drop(armorItem.copy(), true, false)
                        armorItem.shrink(armorItem.count)
                    }
                }

                for ((pos, invItem) in teleportee.inventory.items.withIndex()) {
                    if (invItem == teleportee.mainHandItem) continue
                    val dropChance = if (pos < 9) baseDropChance * 0.5 else baseDropChance // hotbar
                    if (Math.random() < dropChance) {
                        teleportee.drop(invItem.copy(), true, false)
                        invItem.shrink(invItem.count)
                    }
                }

                // we also don't drop the offhand just to be nice
            }
        }
    }

    fun teleportRespectSticky(teleportee: Entity, delta: Vec3, world: ServerLevel) {
        val base = teleportee.rootVehicle
        val target = base.position().add(delta)

        val playersToUpdate = mutableListOf<ServerPlayer>()
        val indirect = base.indirectPassengers

        val sticky = indirect.any { it.type.`is`(HexEntityTags.STICKY_TELEPORTERS) }
        val cannotSticky = indirect.none { it.type.`is`(HexEntityTags.CANNOT_TELEPORT) }
        if (sticky && cannotSticky)
            return

        if (sticky) {
            // this handles teleporting the passengers
            base.teleportTo(target.x, target.y, target.z)
            indirect
                .filterIsInstance<ServerPlayer>()
                .forEach(playersToUpdate::add)
        } else {
            // Break it into two stacks
            teleportee.stopRiding()
            teleportee.passengers.forEach(Entity::stopRiding)
            if (teleportee is ServerPlayer) {
                playersToUpdate.add(teleportee)
            } else {
                teleportee.setPos(teleportee.position().add(delta))
            }
        }

        for (player in playersToUpdate) {
            // See TeleportCommand
            val chunkPos = ChunkPos(BlockPos(delta))
            // the `1` is apparently for "distance." i'm not sure what it does but this is what
            // /tp does
            world.chunkSource.addRegionTicket(TicketType.POST_TELEPORT, chunkPos, 1, player.id)
            player.connection.resetPosition()
            player.setPos(target)
            IXplatAbstractions.INSTANCE.sendPacketToPlayer(player, MsgBlinkAck(delta))
        }
    }
}
