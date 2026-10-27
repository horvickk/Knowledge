package app.erudit

import app.erudit.trainers.Countries
import app.erudit.trainers.Country
import app.erudit.trainers.GeoScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CountriesTest {
    private fun others(c: Country) = Countries.all.filter { it != c }

    private fun country(name: String) = Countries.all.first { it.name == name }

    private fun flagOk(input: String, name: String): Boolean {
        val c = country(name)
        return Countries.matches(input, c.names, others(c).flatMap { it.names })
    }

    private fun capitalOk(input: String, name: String): Boolean {
        val c = country(name)
        return Countries.matches(input, c.acceptedCapitals, others(c).flatMap { it.acceptedCapitals })
    }

    @Test
    fun listIsComplete() {
        assertEquals("193 члена ООН и Ватикан", 194, Countries.all.size)
        assertEquals(194, Countries.all.map { it.code }.toSet().size)
        val missing = Countries.all.filter { !File("src/main/assets/${it.flagAsset}").exists() }.map { it.code }
        assertTrue("нет флагов: $missing", missing.isEmpty())
    }

    @Test
    fun everyNameIsAcceptedOnlyForItsCountry() {
        val problems = mutableListOf<String>()
        for (c in Countries.all) {
            for (n in c.names) if (!flagOk(n, c.name)) problems += "страна ${c.code}: «$n»"
            for (n in c.acceptedCapitals) if (!capitalOk(n, c.name)) problems += "столица ${c.code}: «$n»"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun typosAndConfusions() {
        assertTrue(flagOk("австралия", "Австралия"))
        assertTrue(flagOk("Австралья", "Австралия"))
        assertFalse(flagOk("Австрия", "Австралия"))
        assertFalse(flagOk("Австралия", "Австрия"))
        assertTrue(flagOk("белоруссия", "Беларусь"))
        assertTrue(flagOk("папуа новая гвинея", "Папуа — Новая Гвинея"))
        assertFalse(flagOk("Гвинея", "Гвинея-Бисау"))
        assertFalse(flagOk("Доминика", "Доминиканская Республика"))
        assertFalse(flagOk("Конго", "ДР Конго"))
        assertTrue(flagOk("кот д ивуар", "Кот-д’Ивуар"))
        assertFalse(flagOk("Иран", "Ирак"))
        assertTrue(capitalOk("канбера", "Австралия"))
        assertFalse(capitalOk("Сидней", "Австралия"))
        assertTrue(capitalOk("Ла-Пас", "Боливия"))
        assertTrue(capitalOk("Сукре", "Боливия"))
        assertFalse(capitalOk("Кингстаун", "Ямайка"))
        assertTrue(capitalOk("кишинев", "Молдова"))
    }

    @Test
    fun scoreOrdersByCorrectThenTime() {
        val fast = GeoScore.encode(18, 60)
        val slow = GeoScore.encode(18, 95)
        val more = GeoScore.encode(19, 300)
        assertTrue(fast > slow)
        assertTrue(more > fast)
        assertEquals(18, GeoScore.correct(slow))
        assertEquals(95, GeoScore.seconds(slow))
    }
}
