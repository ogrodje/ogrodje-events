package si.ogrodje.oge

import cats.effect.IO
import org.http4s.Response
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import cats.effect.unsafe.implicits.global

import scala.concurrent.Future

trait AsyncParserSpec { self: AsyncFlatSpec =>
  import FSResource.readServe
  protected given Conversion[IO[Assertion], Future[Assertion]] = _.unsafeToFuture()

  def readAndServe(path: String): IO[Response[IO]]           = readServe(path)
  def readCompressedAndServe(path: String): IO[Response[IO]] =
    IO.println("Compressed reading is not yet supported") *>
      readAndServe(path)
}
