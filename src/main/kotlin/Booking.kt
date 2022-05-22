class Booking(
    var booking_date: String, val time_start: Int,
    val time_end: Int, val visitor_id: Int, val hall_id: Int,
    val full_hall: Boolean, val table_num: Int
    ) {

    fun hasIntersection(other: Booking) = hall_id == other.hall_id && booking_date == other.booking_date &&
                time_end > other.time_start && time_start < other.time_end &&
                (full_hall || other.full_hall || table_num == other.table_num)
}