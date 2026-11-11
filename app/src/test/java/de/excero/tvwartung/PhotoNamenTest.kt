package de.excero.tvwartung

import de.excero.tvwartung.files.PhotoNamen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoNamenTest {

    @Test
    fun `neuer Name hat laufende Nummer und keine Uhrzeit`() {
        assertEquals(
            "B4_09_20261111_nah_01_k3f9.jpg",
            PhotoNamen.neuerName("B4_09", "20261111", "nah", emptyList(), "k3f9")
        )
        val vorhandene = listOf(
            "B4_09_20261111_nah_01_k3f9.jpg", "B4_09_20261111_nah_02_zz00.jpg",
            "B4_09_20261111_nah1_07_abcd.jpg", "B4_09_20261111_nah_124901.jpg"
        )
        assertEquals(
            "B4_09_20261111_nah_03_q1w2.jpg",
            PhotoNamen.neuerName("B4_09", "20261111", "nah", vorhandene, "q1w2")
        )
    }

    @Test
    fun `altes und neues Schema werden unterschieden und das Label gefunden`() {
        assertTrue(PhotoNamen.istAltesSchema("A4_01a_20260714_fern_101530.jpg"))
        assertTrue(PhotoNamen.istAltesSchema("A4_01a_20260714_fern_101530_1.jpg"))
        assertFalse(PhotoNamen.istAltesSchema("A4_01a_20260714_fern_01_k3f9.jpg"))
        assertEquals("fern", PhotoNamen.label("A4_01a_20260714_fern_101530.jpg"))
        assertEquals("fernbedienung", PhotoNamen.label("B4_09_20261111_fernbedienung_01_k3f9.jpg"))
        assertEquals(null, PhotoNamen.label("irgendwas.jpg"))
    }

    @Test
    fun `neues Foto gilt als neuer als eines mit Uhrzeit vom selben Tag`() {
        val namen = listOf(
            "B4_09_20261111_nah_02_aaaa.jpg", "B4_09_20261111_nah_124901.jpg",
            "B4_09_20261111_nah_01_zzzz.jpg"
        )
        assertEquals("B4_09_20261111_nah_02_aaaa.jpg", namen.maxWithOrNull(PhotoNamen.nachAlter))
    }
}
