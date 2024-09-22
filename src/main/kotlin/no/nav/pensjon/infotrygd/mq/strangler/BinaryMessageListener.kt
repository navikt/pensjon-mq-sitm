package no.nav.pensjon.infotrygd.mq.strangler

import com.ibm.msg.client.jakarta.wmq.WMQConstants.JMS_IBM_CHARACTER_SET
import jakarta.jms.BytesMessage
import jakarta.jms.Destination
import jakarta.jms.Message
import jakarta.jms.Session
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.jms.annotation.JmsListener
import org.springframework.jms.core.JmsTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture.supplyAsync

@Component
class BinaryMessageListener(
    private val jmsTemplate: JmsTemplate,
    @Value("\${queue.bus}") private val queueBus: String,
    @Value("\${queue.app}") private val queueApp: String,
) {
    private val logger = getLogger(javaClass)

    @JmsListener(destination = "\${queue.input}")
    fun receiveMessage(
        message: BytesMessage,
    ) {
        val messageData = message.asByteArray()
        val charset = message.getStringProperty(JMS_IBM_CHARACTER_SET)
        val correlationId = message.jmsCorrelationID

        supplyAsync { sendMessageAndReceiveResponse(queueBus, messageData, charset, correlationId) }
            .thenCombine(
                supplyAsync { sendMessageAndReceiveResponse(queueApp, messageData, charset, correlationId) }
            ) { responseBus, responseApp ->
                val bytesBus = (responseBus as? BytesMessage)?.asByteArray()
                val bytesApp = (responseApp as? BytesMessage)?.asByteArray()

                compareResponses(bytesBus, bytesApp)

                bytesBus
            }
            .thenAccept {
                try {
                    if (it != null) {
                        sendReply(message.jmsReplyTo, it, charset, correlationId)
                    }
                } catch (e: Exception) {
                    logger.error("Feil ved sending av svar", e)
                }
            }

        Thread.sleep(100_000)
        throw RuntimeException("foo")
    }

    private fun sendMessageAndReceiveResponse(
        destinationQueue: String,
        messageData: ByteArray,
        charset: String?,
        correlationId: String
    ): Message? {
        return jmsTemplate.sendAndReceive(destinationQueue) { session: Session ->
            session.createBytesMessage().apply {
                jmsCorrelationID = correlationId
                writeBytes(messageData)
                charset?.let { setStringProperty(JMS_IBM_CHARACTER_SET, it) }
            }
        }
    }

    private fun compareResponses(bytesBus: ByteArray?, bytesApp: ByteArray?) {
        if (bytesBus == null && bytesApp == null) {
            logger.info("Svar fra bus og app var null")
        } else if (bytesBus == null) {
            logger.info("Svar fra bus var null")
        } else if (bytesApp == null) {
            logger.info("Svar fra app var null")
        } else if (bytesBus.size != bytesApp.size) {
            logger.info("Ulik lenge på meldinger bus=${bytesBus.size}, app=${bytesApp.size}")
        } else if (!bytesBus.contentEquals(bytesApp)) {
            logger.info("Innholdet er forskjellig fra bus og app")
        } else {
            logger.info("Innholdet er likt mellom bus og app")
        }
    }

    private fun BytesMessage.asByteArray() =
        ByteArray(bodyLength.toInt()).also {
            readBytes(it)
        }

    private fun sendReply(replyQueue: Destination, response: ByteArray, charset: String, correlationId: String) =
        jmsTemplate.send(replyQueue) {
            it.createBytesMessage().apply {
                jmsCorrelationID = correlationId
                writeBytes(response)
                setStringProperty(JMS_IBM_CHARACTER_SET, charset)
            }
        }
}
