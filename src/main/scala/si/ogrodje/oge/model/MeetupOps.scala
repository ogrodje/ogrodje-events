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

  extension (event: Event)
    def humanWhenWhere: String =
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
          start.format(withHourF) + " do " + end.format(hourF) + ", " + location

        case (start, true, Some(end), true, Some(location), true) =>
          start.format(noHourF) + " do " + end.format(noHourF) + ", " + location

        case (start, true, Some(end), true, Some(location), false) =>
          start.format(noHourF) + " do " + end.format(noHourF) + ", " + location

        case (start, false, Some(end), false, None, false) =>
          start.format(withHourF) + " do " + end.format(hourF) + ", "

        case (start, true, Some(end), true, None, false) =>
          start.format(noHourF) + " do " + end.format(noHourF)

        case (start, false, Some(end), false, None, true) =>
          start.format(withHourF) + " do " + end.format(hourF)

        case (start, false, Some(end), false, Some(location), false) =>
          start.format(noHourF) + " do " + end.format(noHourF) + ", " + location

        case (start, false, None, _, Some(location), _) =>
          start.format(withHourF) + ", " + location

        case (start, true, None, _, Some(location), _) =>
          start.format(noHourF) + ", " + location

        case (start, true, _, true, None, true) =>
          start.format(noHourF)

        case (start, false, _, _, _, _) =>
          start.format(withHourF)

        case (start, true, _, _, _, _) =>
          start.format(noHourF)
}
