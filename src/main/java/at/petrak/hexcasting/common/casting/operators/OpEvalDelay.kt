package at.petrak.hexcasting.common.casting.operators

import at.petrak.hexcasting.api.OperationResult
import at.petrak.hexcasting.api.Operator
import at.petrak.hexcasting.api.Operator.Companion.getChecked
import at.petrak.hexcasting.api.RenderedSpell
import at.petrak.hexcasting.api.SpellDatum
import at.petrak.hexcasting.common.casting.CastingContext
import at.petrak.hexcasting.common.casting.CastingHarness
import at.petrak.hexcasting.server.TickScheduler
import kotlin.math.max
import kotlin.math.roundToInt

object OpEvalDelay : Operator {
    override fun modifyStack(stack: MutableList<SpellDatum<*>>, ctx: CastingContext): OperationResult {
        val instrs: List<SpellDatum<*>> = stack.getChecked(stack.lastIndex - 1)
        val delay: Double = stack.getChecked(stack.lastIndex)
        stack.removeLastOrNull()
        stack.removeLastOrNull()

        val ticks = max((delay * 20.0).roundToInt(), 1)

        val harness = CastingHarness(ctx)
        harness.stack.addAll(stack)
        stack.clear()

        TickScheduler.schedule(ticks) {
            val spellsToCast = mutableListOf<RenderedSpell>()
            for (pat in instrs) {
                val res = harness.update(pat.tryGet(), ctx.world)
                when (res) {
                    is CastingHarness.CastResult.Error -> throw res.exn
                    is CastingHarness.CastResult.Cast -> spellsToCast.addAll(res.spells)
                    else -> {}
                }
                if (res.quitStatus() == CastingHarness.QuitStatus.QUIT) break
            }
            // god i hate how this would just work too
            // stack.addAll(harness.stack)
            // it is so weird to think about garbage collection
            for (spell in spellsToCast) {
                spell.cast(ctx)
            }
        }

        return OperationResult(80_000, emptyList())
    }
}