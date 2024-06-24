package si.ogrodje.oge.model

import si.ogrodje.oge.model.db.{Event, Meetup}

import java.time.format.DateTimeFormatter
import java.util.Locale

object MeetupOps {
  private val siLocale: Locale             = Locale.of("sl")
  private val hourF: DateTimeFormatter     = DateTimeFormatter.ofPattern("HH:mm")
  private val noHourF: DateTimeFormatter   = DateTimeFormatter.ofPattern("EEEE, d. MMMM y").withLocale(siLocale)
  private val withHourF: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d. MMMM y, HH:mm").withLocale(siLocale)
  private val justDay: DateTimeFormatter   =
    DateTimeFormatter.ofPattern("d.").withLocale(siLocale)
  private val justDayM: DateTimeFormatter  =
    DateTimeFormatter.ofPattern("d. MMMM y").withLocale(siLocale)

  extension (raw: String)
    private infix def withDbg(rest: String)(using debug: Boolean): String = if debug then s"[$raw] $rest" else rest

  extension (event: Event)
    def humanWhenWhere(using debug: Boolean = false): String =
      (
        event.dateTime,
        event.noStartTime,
        event.dateTimeEnd,
        event.noEndTime,
        event.location,
        event.dateTimeEnd.fold(false)(end =>
          event.dateTime.getYear == end.getYear &&
            event.dateTime.getMonth == end.getMonth &&
            event.dateTime.getDayOfMonth == end.getDayOfMonth
        )
      ) match
        case (start, false, Some(end), false, Some(location), true) =>
          "1" withDbg (start.format(withHourF) + " do " + end.format(hourF) + ", " + location)

        case (start, true, Some(end), true, Some(location), true) =>
          "2" withDbg (start.format(noHourF) + " do " + end.format(noHourF) + ", " + location)

        case (start, true, Some(end), true, Some(location), false) =>
          "3" withDbg (start.format(noHourF) + " do " + end.format(noHourF) + ", " + location)

        case (start, false, Some(end), false, None, false) =>
          "4" withDbg (start.format(withHourF) + " do " + end.format(hourF) + ", ")

        case (start, true, Some(end), true, None, false) =>
          "5" withDbg (start.format(noHourF) + " do " + end.format(noHourF))

        case (start, false, Some(end), false, None, true) =>
          "6" withDbg (start.format(withHourF) + " do " + end.format(hourF))

        case (start, false, Some(end), false, Some(location), false) =>
          if start.getYear == end.getYear && start.getMonth == start.getMonth then
            "7A" withDbg (start.format(justDay) + " - " + end.format(justDayM) + ", " + location)
          else {
            "7B" withDbg (start.format(noHourF) + " do " + end.format(noHourF) + ", " + location)
          }

        case (start, false, None, _, Some(location), _) =>
          "8" withDbg (start.format(withHourF) + ", " + location)

        case (start, true, None, _, Some(location), _) =>
          "9" withDbg (start.format(noHourF) + ", " + location)

        case (start, true, _, true, None, true) =>
          "10" withDbg (start.format(noHourF))

        case (start, false, _, _, _, _) =>
          "11" withDbg (start.format(withHourF))

        case (start, true, _, _, _, _) =>
          "rest" withDbg (start.format(noHourF))
}
