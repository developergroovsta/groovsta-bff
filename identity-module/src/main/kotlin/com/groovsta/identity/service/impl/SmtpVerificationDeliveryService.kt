package com.groovsta.identity.service.impl

import com.groovsta.identity.config.EmailProperties
import com.groovsta.identity.exception.VerificationDeliveryException
import com.groovsta.identity.model.VerificationChannel
import com.groovsta.identity.model.VerificationPurpose
import com.groovsta.identity.service.VerificationDeliveryService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ClassPathResource
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Year

@Service
@ConditionalOnProperty(name = ["groovsta.email.enabled"], havingValue = "true")
class SmtpVerificationDeliveryService(
    private val mailSender: JavaMailSender,
    private val emailProperties: EmailProperties
) : VerificationDeliveryService {

    override fun send(
        channel: VerificationChannel,
        destination: String,
        code: String,
        purpose: VerificationPurpose
    ) {
        if (channel != VerificationChannel.EMAIL) {
            throw UnsupportedOperationException("Email delivery cannot send phone verification codes")
        }

        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, StandardCharsets.UTF_8.name())
        helper.setFrom(emailProperties.from)
        helper.setTo(destination)
        helper.setReplyTo(emailProperties.replyTo)
        helper.setSubject(subject(purpose))
        helper.setText(render(purpose, code), true)
        try {
            prepareMailSender()
            mailSender.send(message)
        } catch (exception: MailException) {
            throw VerificationDeliveryException("Email delivery failed", exception)
        }
    }

    private fun subject(purpose: VerificationPurpose): String = when (purpose) {
        VerificationPurpose.ACCOUNT_VERIFICATION -> "Verify your Groovsta email"
        VerificationPurpose.PASSWORD_RESET -> "Reset your Groovsta password"
    }

    private fun render(purpose: VerificationPurpose, code: String): String {
        val template = when (purpose) {
            VerificationPurpose.ACCOUNT_VERIFICATION -> "templates/email-verification.html"
            VerificationPurpose.PASSWORD_RESET -> "templates/password-reset-email.html"
        }
        val html = ClassPathResource(template).inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return html
            .replace("{{code}}", escapeHtml(code))
            .replace("{{expiresInMinutes}}", "15")
            .replace("{{currentYear}}", Year.now().value.toString())
    }

    private fun prepareMailSender() {
        val javaMailSender = mailSender as? JavaMailSenderImpl ?: return
        val host = javaMailSender.host.orEmpty()
        val password = javaMailSender.password.orEmpty()
        val normalizedPassword = password
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .filterNot(Char::isWhitespace)

        if (normalizedPassword != password) {
            javaMailSender.password = normalizedPassword
        }

        if (host.equals(GMAIL_SMTP_HOST, ignoreCase = true) && normalizedPassword.length != GMAIL_APP_PASSWORD_LENGTH) {
            throw VerificationDeliveryException(
                "Gmail SMTP app password must be $GMAIL_APP_PASSWORD_LENGTH characters; configured length is " +
                    normalizedPassword.length
            )
        }
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private companion object {
        private const val GMAIL_SMTP_HOST = "smtp.gmail.com"
        private const val GMAIL_APP_PASSWORD_LENGTH = 16
    }
}
