package si.ogrodje.oge.view

import cats.effect.IO
import org.http4s.Response
import scalatags.Text.TypedTag
import scalatags.Text.all.Modifier
import si.ogrodje.oge.model.db.{Event, Meetup}
import si.ogrodje.oge.repository.{EventsRepository, MeetupsRepository}
import si.ogrodje.oge.view.Layout.renderHtml
import org.http4s.*
import scalatags.Text.all.*
import si.ogrodje.oge.model.EventForm

object CreateEvent:
  def renderEventForm(
    meetupsRepository: MeetupsRepository[IO, Meetup, String],
    eventsRepository: EventsRepository[IO, Event, String],
    eventForm: EventForm = EventForm.empty,
    maybeError: Option[Throwable] = None,
    maybeEvent: Option[Event] = None,
    layout: Seq[Modifier] => TypedTag[String] = Layout.defaultLayout
  ): IO[Response[IO]] = for
    meetups <- meetupsRepository.all
    out     <- renderHtml(
      layout(
        div(
          cls := "create-event",
          h1("Dodaj dogodek 📅"),
          maybeError.fold(span(""))(th => div(cls := "error", p(th.getMessage))),
          maybeEvent.fold(span("")) { event =>
            div(
              cls := "event",
              h3(s"Podrobnosti: ${event.name}"),
              p("Dogodek je shranjen. Hvala!"),
              p(s"Event ID: ${event.id}")
            )
          },
          div(
            cls := "event-form",
            form(
              method := "POST",
              action := "/create-event",
              div(
                cls := "input-wrap",
                label("Ime dogodka", `for` := "name"),
                input(
                  `type`                   := "text",
                  id                       := "name",
                  name                     := "name",
                  placeholder              := "Ime dogodka",
                  required                 := "required",
                  value                    := eventForm.name
                )
              ),
              div(
                cls := "input-wrap",
                label("Organizacija / Meetup", `for` := "meetup_id"),
                select(
                  name                               := "meetup_id",
                  id                                 := "meetup_id",
                  for { meetup <- meetups.sortBy(_.name) } yield option(value := meetup.id, meetup.name)
                )
              ),
              div(
                cls := "input-wrap",
                label("URL / povezava", `for` := "url"),
                input(
                  `type`                      := "url",
                  id                          := "url",
                  name                        := "url",
                  placeholder                 := "URL / povezava",
                  required                    := "required",
                  value                       := eventForm.url
                )
              ),
              div(
                cls := "input-wrap",
                label("Lokacija", `for` := "location"),
                input(
                  `type`                := "text",
                  id                    := "location",
                  name                  := "location",
                  placeholder           := "Lokacija",
                  value                 := eventForm.location
                )
              ),
              div(
                cls := "input-wrap",
                label("Datum in ura pričetka", `for` := "datetime_start_at"),
                input(
                  `type`                             := "datetime-local",
                  id                                 := "datetime_start_at",
                  name                               := "datetime_start_at",
                  required                           := "required",
                  value                              := eventForm.dateTimeStartAt
                )
              ),
              div(
                cls := "input-wrap",
                label("Datum in ura zaključka", `for` := "datetime_end_at"),
                input(
                  `type`                              := "datetime-local",
                  id                                  := "datetime_end_at",
                  name                                := "datetime_end_at",
                  required                            := "required",
                  value                               := eventForm.dateTimeEndAt
                )
              ),
              div(
                cls := "input-wrap",
                label("Kontaktni email", `for` := "email"),
                input(
                  `type`                       := "email",
                  id                           := "email",
                  name                         := "email",
                  placeholder                  := "Kontaktni email",
                  required                     := "required",
                  value                        := eventForm.email
                )
              ),
              div(
                cls := "input-wrap",
                input(
                  `type` := "submit",
                  value  := "Shrani"
                )
              )
            ),
            div(
              br,
              h3("Navodila in napotki"),
              ul(
                li(p("Vsi ročno dodani dogodki morajo biti potrjeni s strani ekipe Ogrodja.")),
                li(
                  p(
                    "Naša platforma podpira avtomatično zbiranje dogodkov. " +
                      "V kolikor želite, da dogodke samodejno pobiramo se prosimo obrnite na našo ekipo."
                  )
                ),
                li(p("Ekipa Ogrodja si pridržuje pravico do ne-objave dogodkov.")),
                li(
                  p(
                    "Izključno promocijski/sales dogodki niso dovoljeni. " +
                      "V kolikor niste prepričani, v katero kategorijo spada vaš dogodek se obrnite na ekipo."
                  )
                ),
                li(
                  p(
                    "V kolikor vaše organizacije / meetupa še ni na seznamu nas prosim kontaktirajte!"
                  )
                ),
                li(
                  p(
                    "Kontakt via ",
                    a(href := "https://bit.ly/discord-ogrodje", "Discord"),
                    " ali ",
                    a(href := "https://www.linkedin.com/company/ogrodje", "Ogrodje @ LinkedIn"),
                    "."
                  )
                )
              )
            )
          )
        ) :: Nil
      )
    )
  yield out
