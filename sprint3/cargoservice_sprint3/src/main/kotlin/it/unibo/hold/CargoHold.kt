package it.unibo.hold

/**
 * Logica di dominio della stiva, estratta dall'attore qak `hold` per renderla
 * verificabile in isolamento (si veda userDocs/cargoHold_refactoring.pdf).
 *
 * L'attore resta responsabile del protocollo (richieste, reply, aggiornamento
 * della risorsa CoAP osservabile); questa classe conosce solo le regole di
 * business: occupazione degli slot e capacita' di carico della nave.
 */

enum class SlotState { FREE, RESERVED, OCCUPIED }

/**
 * Uno slot della stiva. Le coordinate sono quelle che il cargorobot deve
 * raggiungere: qui sono un dato dello slot e non piu' un `when(slot)`
 * duplicato dentro l'attore cargorobot.
 */
data class Slot(
    val id: Int,
    val targetX: Int,
    val targetY: Int,
    var state: SlotState = SlotState.FREE,
    var pid: Int = 0,
    var peso: Int = 0
)

/** Esito di una richiesta di prenotazione. */
sealed class HoldResponse {
    data class Reserved(val slot: Slot) : HoldResponse()
    object Invalid : HoldResponse()
    object Overweight : HoldResponse()
    object Full : HoldResponse()
}

class CargoHold(val maxLoad: Int = DEFAULT_MAX_LOAD) {

    companion object {
        const val DEFAULT_MAX_LOAD = 1000
        const val NUM_SLOTS = 4
    }

    val slots = listOf(
        Slot(1, 1, 1),
        Slot(2, 4, 1),
        Slot(3, 1, 3),
        Slot(4, 4, 3)
    )

    /** Peso complessivo attualmente impegnato (RESERVED + OCCUPIED). */
    val currentLoad: Int
        get() = slots.filter { it.state != SlotState.FREE }.sumOf { it.peso }

    private fun findFree(): Slot? = slots.find { it.state == SlotState.FREE }

    private fun slotById(id: Int): Slot? = slots.find { it.id == id }

    /**
     * Valuta una richiesta di carico. L'ordine dei controlli riproduce quello
     * del modello qak: payload non valido, poi sovraccarico, poi stiva piena.
     */
    fun request(peso: Int, pid: Int): HoldResponse {
        if (peso <= 0 || pid <= 0) return HoldResponse.Invalid
        if (currentLoad + peso > maxLoad) return HoldResponse.Overweight

        val free = findFree() ?: return HoldResponse.Full
        free.state = SlotState.RESERVED
        free.pid = pid
        free.peso = peso
        return HoldResponse.Reserved(free)
    }

    /** Conferma che il container e' stato effettivamente depositato. */
    fun occupy(slotId: Int): Boolean {
        val slot = slotById(slotId) ?: return false
        if (slot.state != SlotState.RESERVED) return false
        slot.state = SlotState.OCCUPIED
        return true
    }

    /**
     * Libera uno slot restituendone il peso alla capacita' disponibile.
     *
     * Nota: nel modello qak lo scarico del peso avveniva dopo l'azzeramento
     * dello slot, quindi sottraeva sempre zero e il carico non calava mai.
     * Qui `currentLoad` e' derivato dagli slot, per cui il problema non si
     * puo' ripresentare per costruzione.
     */
    fun release(slotId: Int): Boolean {
        val slot = slotById(slotId) ?: return false
        if (slot.state == SlotState.FREE) return false
        slot.state = SlotState.FREE
        slot.pid = 0
        slot.peso = 0
        return true
    }

    override fun toString(): String =
        slots.joinToString(" ") { "S${it.id}=${it.state}(pid=${it.pid},w=${it.peso})" }
}
