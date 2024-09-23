package no.nav.pensjon.infotrygd.mq.strangler.infotrygd

fun readDecimal(str: String, start: Int, length: Int): Int = str.substring(start, start + length).toInt()

fun readString(str: String, start: Int, length: Int): String? = str.substring(start, start + length).trim().takeUnless { it.isEmpty() }
