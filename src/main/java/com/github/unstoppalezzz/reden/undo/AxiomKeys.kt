package com.github.unstoppalezzz.reden.undo

import com.github.unstoppalezzz.reden.Reden
import java.util.function.Predicate

object AxiomKeys {
    private const val ACTION_RESULT = "com.moulberry.axiom.UserAction\$ActionResult"

    @JvmField
    var supersede: Predicate<Boolean>? = null

    private val notHandled: Any? by lazy {
        try {
            Class.forName(ACTION_RESULT).enumConstants.firstOrNull { (it as Enum<*>).name == "NOT_HANDLED" }
        } catch (e: Throwable) {
            Reden.LOGGER.warn("Couldn't find Axiom's action result, Axiom's undo keys won't be overridden", e)
            null
        }
    }

    @JvmStatic
    fun supersededResult(action: Any?): Any? {
        val isUndo = when ((action as? Enum<*>)?.name) {
            "UNDO" -> true
            "REDO" -> false
            else -> return null
        }
        if (supersede?.test(isUndo) != true) return null
        return notHandled
    }
}
