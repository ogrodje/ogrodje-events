package si.ogrodje.oge.view
import scalatags.Text.all.*
import cats.effect.IO
import org.http4s.Response
import org.http4s.headers.`Content-Type`
import scalatags.Text
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import scalatags.Text.TypedTag
import scalatags.Text.tags2.title as titleTag
import scalatags.Text.tags2.style as styleTag
import scalatags.Text.Modifier

object Layout:
  def defaultLayout(content: Modifier*): TypedTag[String] =
    html(
      head(
        titleTag("Ogrodje / Dogodki"),
        styleTag(
          """html,body,td,th { font-family:sans-serif; font-size:12pt; line-height:18pt }
            |.wrapper { margin: 0 auto; display:block; max-width:800px; padding-top: 50px; }
            |.week { margin-bottom: 40px; display:block; }
            |.week:last-child { margin: none; }
            |.event { border-top: 1px solid #EEE; display:block; margin-bottom: 10px; padding: 10px; }
            |.event:first-child { border: none; }
            |.event .event-name { font-weight: 700; font-size: 14pt; line-height: 20pt; }
            |.event .meetup-name { font-size: smaller; }
            |.wrapper .footer { text-align: center; font-size: small; }""".stripMargin
        )
      ),
      body(
        div(
          cls := "wrapper",
          div(cls := "content", content),
          div(
            cls   := "footer",
            div(cls := "logo", span("Powered by ", a(href := "https://ogrodje.si", "Ogrodje"))),
            div(
              cls   := "info",
              a(href := "/api/events/upcoming", "API"),
              span(" / "),
              a(href := "https://github.com/ogrodje/ogrodje-events", "Source Code")
            )
          )
        )
      )
    )

  def renderHtml[Out <: String](typedTag: TypedTag[Out]): IO[Response[IO]] =
    IO.pure("<!DOCTYPE html>" + typedTag)
      .flatMap(html => Ok(html, `Content-Type`(MediaType.text.html)))
