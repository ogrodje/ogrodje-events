package si.ogrodje.oge.clients

import cats.effect.{IO, Resource}
import jakarta.mail.internet.{InternetAddress, MimeBodyPart, MimeMessage, MimeMultipart}
import jakarta.mail.{Authenticator, Message, PasswordAuthentication, Session}
import org.typelevel.log4cats.slf4j.Slf4jFactory
import si.ogrodje.oge.Config

import java.util.Properties

final class SMTPSender private (
  config: Config,
  properties: Properties,
  session: Session
) extends MailSender[Unit]:
  // TODO: Disabled for now.
  val (username, password) = "config.smtpUsername" -> "config.smtpPassword"
  private val logger       = Slf4jFactory.create[IO].getLogger

  override def send(to: String, subject: String, htmlBody: String): IO[Unit] = {
    val message = MimeMessage(session)
    message.setSender(InternetAddress.parse(username).head)
    message.setFrom(new InternetAddress(username))
    message.setRecipient(Message.RecipientType.TO, InternetAddress.parse(to).head)
    message.setSubject(subject)
    message.setContent(getMultipart(htmlBody))

    IO {
      val transport = session.getTransport("smtps")
      transport.connect(username, password)
      transport.sendMessage(message, message.getRecipients(Message.RecipientType.TO))
      transport.close()
    } <*
      logger.info(s"Sending via SMTP sender to $to")
  }

  private def getMultipart(msg: String) = {
    val mimeBodyPart               = MimeBodyPart()
    mimeBodyPart.setContent(msg, "text/html; charset=utf-8")
    val mimeBodyPartWithStyledText = new MimeBodyPart()
    mimeBodyPartWithStyledText.setContent(msg, "text/html; charset=utf-8")

    val multipart = new MimeMultipart()
    multipart.addBodyPart(mimeBodyPart)
    multipart.addBodyPart(mimeBodyPartWithStyledText)
    multipart
  }

object SMTPSender:
  private val logger = Slf4jFactory.create[IO].getLogger

  private def mkProperties(config: Config): IO[(Properties, String, String)] = IO {
    // TODO: Disabled for now
    val (username, password) = "config.smtpUsername" -> "config.smtpPassword"
    val (host, port)         = "config.smtpHost"     -> "config.smtpPort.toString"

    val properties = new Properties()
    properties.put("mail.debug", "true")

    properties.put("mail.transport.protocol", "smtp") // smtps

    properties.put("mail.smtp.auth", "true")
    properties.put("mail.smtp.host", host)
    properties.put("mail.smtp.password", password)
    properties.put("mail.smtp.port", port)
    properties.put("mail.smtp.starttls.enable", "true")
    properties.put("mail.smtp.user", username)

    properties.put("mail.smtps.auth", "true")
    properties.put("mail.smtps.host", host)
    properties.put("mail.smtps.password", password)
    properties.put("mail.smtps.port", port)
    properties.put("mail.smtps.starttls.enable", "true")
    properties.put("mail.smtps.user", username)

    properties.put("mail.smtps.quitwait", "false")

    properties.put("mail.smtp.socketFactory.port", "1025")
    properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
    properties.put("mail.smtp.socketFactory.fallback", "false")
    properties.put("mail.smtps.socketFactory.port", "1025")
    properties.put("mail.smtps.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
    properties.put("mail.smtps.socketFactory.fallback", "false")

    properties.put("mail.smtp.ssl.trust", "*")
    properties.put("mail.smtps.ssl.trust", "*")

    // TODO: Disabled.
    (properties, "config.smtpUsername", "config.smtpPassword")
  }

  private def mkSession(properties: Properties, username: String, password: String): IO[Session] = IO(
    Session.getDefaultInstance(
      properties,
      new Authenticator {
        override def getPasswordAuthentication: PasswordAuthentication =
          new PasswordAuthentication(username, password)
      }
    )
  )

  def resource(config: Config): Resource[IO, SMTPSender] = for
    (properties, username, password) <- Resource.eval(mkProperties(config)).evalTap { properties =>
      logger.info(s"Properties: $properties")
    }
    session                          <- Resource.eval(mkSession(properties, username, password))
    sender                           <- Resource.pure(new SMTPSender(config, properties, session))
  yield sender
