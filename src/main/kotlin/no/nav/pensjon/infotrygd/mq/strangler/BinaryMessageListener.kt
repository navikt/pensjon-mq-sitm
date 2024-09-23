package no.nav.pensjon.infotrygd.mq.strangler

import com.ibm.msg.client.jakarta.wmq.WMQConstants.JMS_IBM_CHARACTER_SET
import com.ibm.msg.client.jakarta.wmq.WMQConstants.JMS_IBM_ENCODING
import jakarta.jms.BytesMessage
import jakarta.jms.Destination
import jakarta.jms.Message
import jakarta.jms.Session
import no.nav.pensjon.infotrygd.mq.strangler.infotrygd.InfotrygdMessage
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.jms.annotation.JmsListener
import org.springframework.jms.core.JmsTemplate
import org.springframework.stereotype.Component
import java.nio.charset.Charset
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

                compareResponses(responseBus, bytesBus, responseApp, bytesApp)

                responseBus to bytesBus
            }
            .thenAccept { (responseMessage, responseData) ->
                try {
                    if (responseMessage != null && responseData != null) {
                        sendReply(message.jmsReplyTo, responseMessage, responseData)
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

    private fun compareResponses(responseBus: Message?, bytesBus: ByteArray?, responseApp: Message?, bytesApp: ByteArray?) {
        try {
            if (bytesBus == null && bytesApp == null) {
                logger.info("Svar fra bus og app var null")
            } else if (bytesBus == null) {
                logger.info("Svar fra bus var null")
            } else if (bytesApp == null) {
                logger.info("Svar fra app var null")
            } else if (bytesBus.size != bytesApp.size || !bytesBus.contentEquals(bytesApp)) {
                logger.info("Innholdet er forskjellig, bus=${bytesBus.size}, app=${bytesApp.size}")

                val messageBus = InfotrygdMessage.deserialize(bytesBus, Charset.forName(responseBus!!.getStringProperty(JMS_IBM_CHARACTER_SET) ?: "ibm277"))
                val messageApp = InfotrygdMessage.deserialize(bytesApp, Charset.forName(responseApp!!.getStringProperty(JMS_IBM_CHARACTER_SET) ?: "ibm277"))

                logger.info("Message bus {}", messageBus)
                logger.info("Message app {}", messageApp)
            } else {
                logger.info("Innholdet er likt mellom bus og app")
            }
        } catch (e: Exception) {
            logger.error("Feil ved sammenligning av svar", e)
        }
    }

    private fun BytesMessage.asByteArray() =
        ByteArray(bodyLength.toInt()).also {
            readBytes(it)
        }

    private fun sendReply(replyQueue: Destination, responseMessage: Message, response: ByteArray) =
        jmsTemplate.send(replyQueue) { session ->
            session.createBytesMessage().apply {
                jmsCorrelationID = responseMessage.jmsCorrelationID
                writeBytes(response)
                responseMessage.getStringProperty(JMS_IBM_CHARACTER_SET)?.let { charset -> setStringProperty(JMS_IBM_CHARACTER_SET, charset) }
                (responseMessage.getObjectProperty(JMS_IBM_ENCODING) as? Int)?.let { setIntProperty(JMS_IBM_ENCODING, it) }
            }
        }
}
