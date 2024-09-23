package no.nav.pensjon.infotrygd.mq.strangler.infotrygd

import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger

data class InfotrygdMessage(
    // K278MHEA
    val kodeAksjon: String?,        // :TAG:-KODE-AKSJON         PIC X(08).
    val kilde: String?,             // :TAG:-KILDE               PIC X(04).
    val brukerId: String?,          // :TAG:-BRUKERID            PIC X(08).
    val lengde: Int?,               // :TAG:-LENGDE              PIC 9(07).
    val dato: String?,              // :TAG:-DATO                PIC X(08).
    val klokke: String?,            // :TAG:-TID                 PIC X(06).

    // K469MMEL
    val systemId: String?,          // :TAG:-SYSTEM-ID           PIC X(08).
    val kodeMelding: String?,       // :TAG:-KODE-MELDING        PIC X(08).
    val alvorlighetsgrad: Int?,     // :TAG:-ALVORLIGHETSGRAD    PIC 9(02).
    val beskMelding: String?,       // :TAG:-BESKR-MELDING       PIC X(75).
    val sqlKode: String?,           // :TAG:-SQL-KODE            PIC X(04).
    val sqlState: String?,          // :TAG:-SQL-STATE           PIC X(05).
    val sqlMelding: String?,        // :TAG:-SQL-MELDING         PIC X(80).
    val mqCompletionCode: String?,  // :TAG:-MQ-COMPLETION-CODE  PIC X(04).
    val mqReasonCode: String?,      // :TAG:-MQ-REASON-CODE      PIC X(04).
    val progId: String?,            // :TAG:-PROGRAM-ID          PIC X(08).
    val sectionNavn: String?,       // :TAG:-SECTION-NAVN        PIC X(30).

    // K278M8ID
    val copyId: String?,            // :TAG:-COPY-ID             PIC X(08).
    val antall: Int,                // :TAG:-ANTALL              PIC 9(05).
) {
    companion object {
        fun deserialize(bytes: ByteArray, charset: Charset): InfotrygdMessage {
            val string = String(bytes, charset)
            val cursor = AtomicInteger()
            val antallRecords = AtomicInteger()

            fun readDecimal(length: Int): Int =
                readDecimal(string, cursor.getAndAdd(length), length)
            fun readString(length: Int): String? =
                readString(string, cursor.getAndAdd(length), length)

            return InfotrygdMessage(
                // K278MHEA
                kodeAksjon = readString(8),
                kilde = readString(4),
                brukerId = readString(8),
                lengde = readDecimal(7),
                dato = readString(8),
                klokke = readString(6),

                // K469MMEL
                systemId = readString(8),
                kodeMelding = readString(8),
                alvorlighetsgrad = readDecimal(2),
                beskMelding = readString(75),
                sqlKode = readString(4),
                sqlState = readString(5),
                sqlMelding = readString(80),
                mqCompletionCode = readString(4),
                mqReasonCode = readString(4),
                progId = readString(8),
                sectionNavn = readString(30),

                // K278M8ID
                copyId = readString(8),
                antall = readDecimal(5).also { antallRecords.set(it) },
            )
        }
    }
}

