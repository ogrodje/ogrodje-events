package si.ogrodje.oge.subs

import io.github.felipebonezi.cipherizy.algorithm.CipherFactory

import java.util.Base64
import scala.util.Try

object SecretString:
  private val cipherFactory                  = CipherFactory.getInstance()
  private val cipher                         = cipherFactory.get(CipherFactory.Algorithm.AES)
  private val salt                           = "tasu.kd:iscXe1p32ecPuscatXisSaltzas".take(16).getBytes
  private val (base64Encoder, base64Decoder) = Base64.getEncoder -> Base64.getDecoder
  extension (raw: String)

    def encrypt(
      key: String,
      salt: Array[Byte] = salt
    ): Try[String] =
      Try(base64Encoder.encodeToString(cipher.encrypt(key.getBytes, salt, raw.getBytes)))

    def decrypt(
      key: String,
      salt: Array[Byte] = salt
    ): Try[String] =
      Try(cipher.decryptToString(key.getBytes, salt, base64Decoder.decode(raw)))
