package si.ogrodje.oge

import cats.effect.IO
import org.http4s.Response
import org.http4s.dsl.io.*

import scala.io.Source

object FSResource:
  def readServe(path: String): IO[Response[IO]] =
    val source           = Source.fromResource(path)
    val upcoming: String = source.getLines().mkString("\n")
    source.close()
    Ok(upcoming)
