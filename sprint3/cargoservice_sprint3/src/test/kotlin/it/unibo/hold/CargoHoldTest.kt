package it.unibo.hold

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifica della business logic della stiva.
 *
 * Gli scenari riproducono il Test Plan formalizzato nella chronicle dello
 * Sprint 1 (sezione "Test Plans"), che era stato progettato ma mai eseguito
 * in modo automatico.
 */
class CargoHoldTest {

    // --- Scenario "Carico Standard" ------------------------------------

    @Test
    fun `una richiesta valida su stiva vuota riserva il primo slot libero`() {
        val hold = CargoHold()

        val esito = hold.request(peso = 100, pid = 17)

        val reserved = assertIs<HoldResponse.Reserved>(esito)
        assertEquals(1, reserved.slot.id)
        assertEquals(17, reserved.slot.pid)
        assertEquals(SlotState.RESERVED, reserved.slot.state)
        assertEquals(100, hold.currentLoad)
    }

    @Test
    fun `richieste successive occupano slot distinti`() {
        val hold = CargoHold()

        val assegnati = (1..4).map {
            assertIs<HoldResponse.Reserved>(hold.request(peso = 10, pid = it)).slot.id
        }

        assertEquals(listOf(1, 2, 3, 4), assegnati)
        assertEquals(4, assegnati.distinct().size)
    }

    // --- Scenario "Rifiuto per Sovrappeso" -----------------------------

    @Test
    fun `una richiesta oltre MaxLoad viene rifiutata senza riservare slot`() {
        val hold = CargoHold(maxLoad = 1000)

        val esito = hold.request(peso = 1500, pid = 3)

        assertIs<HoldResponse.Overweight>(esito)
        assertEquals(0, hold.currentLoad)
        assertTrue(hold.slots.all { it.state == SlotState.FREE })
    }

    @Test
    fun `il limite di carico vale sulla somma dei pesi, non sul singolo`() {
        val hold = CargoHold(maxLoad = 1000)
        hold.request(peso = 600, pid = 1)

        // Da solo starebbe nel limite; sommato al carico corrente no.
        val esito = hold.request(peso = 600, pid = 2)

        assertIs<HoldResponse.Overweight>(esito)
        assertEquals(600, hold.currentLoad)
    }

    // --- Scenario "Rifiuto per Stiva Piena" ----------------------------

    @Test
    fun `con i quattro slot occupati la richiesta viene rifiutata`() {
        val hold = CargoHold()
        repeat(CargoHold.NUM_SLOTS) { i -> hold.request(peso = 10, pid = i + 1) }

        val esito = hold.request(peso = 10, pid = 99)

        assertIs<HoldResponse.Full>(esito)
    }

    // --- Payload non valido --------------------------------------------

    @Test
    fun `peso o pid non positivi sono payload invalidi`() {
        val hold = CargoHold()

        assertIs<HoldResponse.Invalid>(hold.request(peso = 0, pid = 5))
        assertIs<HoldResponse.Invalid>(hold.request(peso = -1, pid = 5))
        assertIs<HoldResponse.Invalid>(hold.request(peso = 10, pid = 0))
        assertEquals(0, hold.currentLoad)
    }

    @Test
    fun `il payload invalido viene valutato prima del sovrappeso`() {
        val hold = CargoHold(maxLoad = 100)

        // Peso oltre il limite ma pid non valido: vince Invalid, come nel
        // modello qak dove la guardia `invalid` precede `overweight`.
        assertIs<HoldResponse.Invalid>(hold.request(peso = 5000, pid = 0))
    }

    // --- Rilascio slot: regressione sul carico residuo -------------------

    @Test
    fun `il rilascio di uno slot restituisce il peso alla capacita disponibile`() {
        val hold = CargoHold(maxLoad = 1000)
        val slot = assertIs<HoldResponse.Reserved>(hold.request(peso = 800, pid = 1)).slot

        hold.release(slot.id)

        assertEquals(0, hold.currentLoad)
        assertEquals(SlotState.FREE, hold.slots.first { it.id == slot.id }.state)
    }

    @Test
    fun `dopo prenotazione e rilascio ripetuti la stiva accetta ancora carichi`() {
        val hold = CargoHold(maxLoad = 1000)

        // Nel modello qak `release(idx)` azzerava lo slot prima di scalare il
        // peso da totPeso, che quindi non calava mai: dopo pochi cicli la
        // stiva rispondeva OVERWEIGHT anche da vuota.
        repeat(5) {
            val slot = assertIs<HoldResponse.Reserved>(hold.request(peso = 900, pid = 1)).slot
            hold.release(slot.id)
        }

        assertEquals(0, hold.currentLoad)
        assertIs<HoldResponse.Reserved>(hold.request(peso = 900, pid = 2))
    }

    @Test
    fun `rilasciare uno slot gia libero o inesistente non ha effetto`() {
        val hold = CargoHold()

        assertFalse(hold.release(1))
        assertFalse(hold.release(99))
    }

    // --- Ciclo di vita dello slot ---------------------------------------

    @Test
    fun `uno slot riservato passa a occupato alla consegna`() {
        val hold = CargoHold()
        val slot = assertIs<HoldResponse.Reserved>(hold.request(peso = 50, pid = 7)).slot

        assertTrue(hold.occupy(slot.id))

        assertEquals(SlotState.OCCUPIED, hold.slots.first { it.id == slot.id }.state)
        assertEquals(50, hold.currentLoad, "un container consegnato pesa ancora sulla nave")
    }

    @Test
    fun `non si puo occupare uno slot mai riservato`() {
        val hold = CargoHold()

        assertFalse(hold.occupy(1))
    }

    // --- Coordinate degli slot ------------------------------------------

    @Test
    fun `ogni slot espone le coordinate che il cargorobot deve raggiungere`() {
        val hold = CargoHold()

        val coords = hold.slots.map { it.id to (it.targetX to it.targetY) }.toMap()

        assertEquals(1 to 1, coords[1])
        assertEquals(4 to 1, coords[2])
        assertEquals(1 to 3, coords[3])
        assertEquals(4 to 3, coords[4])
        // Nota: qui non si usa assertNotEquals perche' 2p301.jar (tuProlog,
        // dipendenza flatDir) include una copia arcaica di org.junit.Assert
        // priva di quel metodo, che oscura la JUnit 4.13.2 risolta da Gradle.
        assertTrue(coords[1] != coords[4], "slot diversi devono avere coordinate diverse")
    }
}
