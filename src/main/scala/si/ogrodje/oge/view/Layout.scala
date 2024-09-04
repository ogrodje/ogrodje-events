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
  def defaultLayout(contentM: Modifier*): TypedTag[String] =
    html(
      lang := "sl",
      head(
        titleTag("Ogrodje / Dogodki"),
        meta(charset := "utf-8"),
        meta(
          name       := "viewport",
          content    := "width=device-width, initial-scale=1, maximum-scale=2, shrink-to-fit=no, viewport-fit=cover"
        ),
        styleTag(
          """html,body,td,th,input,select,option { font-family:sans-serif; font-size:12pt; line-height:18pt }
            |body { background-color: #151314; color: white; }
            |a { color: #EB3F6C; text-decoration: none;  }
            |a:hover, a:visited { color: darken(#EB3F6C, 40%); }
            |.wrapper { margin: 0 auto; display:block; max-width:800px; padding-top: 50px; }
            |.header { position: relative; display: block; margin-bottom:20px; }
            |.header .tools { position: absolute; top:0; right:0; display:block; margin-bottom: 10px; }
            |.header .logo a { font-weight: 700; color: white; }
            |.week { margin-bottom: 50px; display:block; }
            |.week:last-child { margin: none; }
            |.events { padding-left:5px; padding-right:5px; }
            |.event { border-top: 1px solid #2A2828; display:block; margin-bottom: 10px; padding: 15px; }
            |.event:first-child { border: none; }
            |.event .event-name { font-weight: 700; font-size: 14pt; line-height: 20pt; }
            |.event .meetup-name {  }
            |.wrapper .footer { text-align: center; font-size: small; }
            |.info-observe { padding: 5px; text-align: center; font-size: smaller; }
            |.other-week { display: none; }
            |.create-event form .input-wrap { margin-bottom:10px; }
            |.create-event form .input-wrap label { margin-right:10px; padding-right: 10px; display:inline-block; width:200px }
            |.create-event form .input-wrap input[type=text],
            |.create-event form .input-wrap input[type=url],
            |.create-event form .input-wrap input[type=number],
            |.create-event form .input-wrap input[type=email],
            |.create-event form .input-wrap select
            | { border-radius: 3px; margin-right:10px; display:inline-block; width: 60% }""".stripMargin
        )
      ),
      body(
        div(
          cls := "wrapper",
          div(
            cls   := "header",
            span(cls := "logo", a(href := "/", "Dogodki @ Ogrodje")),
            div(cls  := "tools", a(href := "/create-event", "Dodaj dogodek ⭐️"))
          ),
          div(cls := "content", contentM),
          div(
            cls   := "footer",
            p(cls := "logo", span("Powered by ", a(href := "https://ogrodje.si", "Ogrodje"))),
            p(
              cls := "info",
              a(href := "/api/events/upcoming", "API"),
              span(" / "),
              a(href := "https://github.com/ogrodje/ogrodje-events", "Source Code @ GitHub")
            )
          )
        )
      )
    )

  def renderHtml[Out <: String](typedTag: TypedTag[Out]): IO[Response[IO]] =
    IO.pure("<!DOCTYPE html>" + typedTag)
      .flatMap(html => Ok(html, `Content-Type`(MediaType.text.html)))
