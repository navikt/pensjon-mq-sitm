package no.nav.pensjon.infotrygd.mq.strangler

import org.slf4j.MDC

fun <R> mdc(vararg pairs: Pair<String, Any?>, block: () -> R): R {
    try {
        pairs.forEach { (k, v) ->
            MDC.put(k, v?.toString() ?: "-")
        }

        return block()
    } finally {
        pairs.forEach { (k, _) ->
            MDC.remove(k)
        }
    }
}

