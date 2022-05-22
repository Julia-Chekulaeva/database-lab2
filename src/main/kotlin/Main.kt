import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.math.ceil

const val visitorsCount = 10000
const val addressesCount = 100
const val waitersCount = 1000
const val hallsCount = 1000
const val eventsCount = 1000
const val bookingsCount = 10000
const val eventOrdersCount = 10000

val namesMen = File("src\\main\\resources\\names_men.txt").readLines().map { it.split(" ") }
val namesWomen = File("src\\main\\resources\\names_women.txt").readLines().map { it.split(" ") }
val sqlCreation = File("src\\main\\resources\\task1.sql").readText()
val streets = File("src\\main\\resources\\streets.txt").readLines()
val outputFile = File("src\\main\\resources\\task2_generated.sql").bufferedWriter()

val bookings = mutableListOf<Booking>()
val tablesInHalls = mutableListOf<Int>()
val hallsOnAddresses = Array(addressesCount) { 0 }
val showTimesForEvents = mutableListOf<Int>()

const val startWorking = 8 * 60
const val endWorking = 23 * 60
const val minTimeForBooking = 30
const val maxPrice = 99999
const val minPrice = 1000
const val maxShowTime = 4 * 60
const val minShowTime = 15
const val minTablesInHall = 4
const val maxTablesInHall = 20

fun main(args: Array<String>) {
    val connection = connect()
    init(connection)
    insertData(connection)
    outputFile.close()
}

private fun connect(): Connection {
    try {
        Class.forName("org.postgresql.Driver")
        val connection = DriverManager.getConnection(
            "jdbc:postgresql://localhost/postgres",
            "postgres", "10102001"
        )
        println("Подключение успешно выполнено")
        return connection
    } catch (e: Exception) {
        println("Не удалось подключиться к базе данных")
        throw e
    }
}

private fun init(connection: Connection) {
    println("Creating tables")
    val query = connection.prepareStatement(sqlCreation)
    query.execute()
}

private fun insertData(connection: Connection) {
    println("Inserting data into visitors")
    for (i in 0 until visitorsCount) {
        val genderIndicator = if (generateBool(0.5f)) 1 else 0
        insertInto(
            "visitors", listOf("name", "surname", "patronymic", "phone"),
            listOf(
                "name" to listOf(genderIndicator), "surname" to listOf(genderIndicator),
                "patronymic" to listOf(genderIndicator), "phone" to listOf()
            ), connection
        )
    }
    println("Inserting data into addresses")
    for (i in 0 until addressesCount)
        insertInto(
            "addresses", listOf("street", "building"),
            listOf("street" to listOf(), "INTEGER" to listOf(1, 99)),
            connection
        )
    println("Inserting data into waiters")
    for (i in 0 until waitersCount) {
        val genderIndicator = if (generateBool(0.5f)) 1 else 0
        insertInto(
            "waiters", listOf("name", "surname", "patronymic", "address_id"),
            listOf(
                "name" to listOf(genderIndicator), "surname" to listOf(genderIndicator),
                "patronymic" to listOf(genderIndicator), "INTEGER" to listOf(1, addressesCount)
            ), connection
        )
    }
    println("Inserting data into halls")
    for (i in 0 until hallsCount)
        insertInto(
            "halls", listOf("hall_num", "address_id", "tables_count"),
            listOf(), connection
        )
    println("Inserting data into events")
    for (i in 0 until eventsCount)
        insertInto(
            "events", listOf("name", "price", "show_time"),
            listOf("VARCHAR" to listOf(20)), connection
        )
    println("Inserting data into bookings")
    for (i in 0 until bookingsCount)
        insertInto(
            "bookings", listOf(
                "booking_date", "time_start", "time_end",
                "visitor_id", "hall_id", "full_hall", "table_num"
            ), listOf(), connection
        )
    println("Inserting data into event_orders")
    for (i in 0 until eventOrdersCount)
        insertInto(
            "event_orders", listOf("event_id", "start_time", "booking_id"),
            listOf(), connection
        )
}

private fun generateInsertion(
    table: String, fields: List<String>, values: List<Any>
): String = "INSERT INTO $table (${fields.joinToString()}) VALUES (\n${
    values.joinToString(",\n") { "\t" + it }
}\n);\n"

private fun generateRandomData(params: List<Pair<String, List<Int>>>) = params.map {
    when (it.first) {
        "name" -> generateNameSurnamePatronymic(0, it.second[0] > 0)
        "surname" -> generateNameSurnamePatronymic(1, it.second[0] > 0)
        "patronymic" -> generateNameSurnamePatronymic(2, it.second[0] > 0)
        "street" -> generateStreet()
        "VARCHAR" -> generateStr(it.second[0], false)
        "INTEGER" -> generateIntegerIncludingStartAndEnd(it.second[0], it.second[1])
        "phone" -> generatePhone()
        else -> throw Exception("Undefined type")
    }
}

private fun generateRandomDataForBooking(): List<String> {
    val middle = generateIntegerIncludingStartAndEnd(startWorking, endWorking - minTimeForBooking)
    val hallId = ceil(Math.random() * hallsCount).toInt()
    val booking = Booking(
        generateDate(), generateIntegerIncludingStartAndEnd(startWorking, middle),
        generateIntegerIncludingStartAndEnd(middle + minTimeForBooking, endWorking),
        ceil(Math.random() * visitorsCount).toInt(), hallId,
        generateBool(0.5f), tablesInHalls[hallId - 1]
    )
    var hasIntersection = true
    while (hasIntersection) {
        hasIntersection = false
        for (b in bookings) {
            hasIntersection = b.hasIntersection(booking)
            if (hasIntersection) {
                booking.booking_date = generateDate()
                break
            }
        }
    }
    bookings.add(booking)
    return listOf(
        booking.booking_date, convertTimeToString(booking.time_start),
        convertTimeToString(booking.time_end), booking.visitor_id.toString(),
        booking.hall_id.toString(), booking.full_hall.toString(), booking.table_num.toString()
    )
}

private fun generateRandomDataForHall(): List<String> {
    val addressId = generateIntegerIncludingStartAndEnd(1, addressesCount)
    val hallNum = ++hallsOnAddresses[addressId - 1]
    val tablesCount = generateIntegerIncludingStartAndEnd(minTablesInHall, maxTablesInHall)
    tablesInHalls.add(tablesCount)
    return listOf(hallNum.toString(), addressId.toString(), tablesCount.toString())
}

private fun generateRandomDataForEventOrder(): List<String> {
    var eventId = generateIntegerIncludingStartAndEnd(1, eventsCount)
    val bookingId = generateIntegerIncludingStartAndEnd(1, bookingsCount)
    val booking = bookings[bookingId - 1]
    while (booking.time_start > booking.time_end - showTimesForEvents[eventId - 1]) {
        eventId = generateIntegerIncludingStartAndEnd(1, eventsCount)
    }
    val startTime = generateIntegerIncludingStartAndEnd(
        booking.time_start, booking.time_end - showTimesForEvents[eventId - 1]
    )
    return listOf(eventId.toString(), convertTimeToString(startTime), bookingId.toString())
}

private fun generateRandomDataForEvent(nameCapacity: Int): List<String> {
    val name = generateStr(nameCapacity, false)
    val price = generateIntegerIncludingStartAndEnd(minPrice, maxPrice)
    val showTime = generateIntegerIncludingStartAndEnd(minShowTime, maxShowTime)
    showTimesForEvents.add(showTime)
    return listOf(name.toString(), price.toString(), convertTimeToString(showTime))
}

private fun insertInto(
    table: String, fields: List<String>, params: List<Pair<String, List<Int>>>,
    connection: Connection
) {
    val values = when (table) {
        "halls" -> generateRandomDataForHall()
        "events" -> generateRandomDataForEvent(params[0].second[0])
        "bookings" -> generateRandomDataForBooking()
        "event_orders" -> generateRandomDataForEventOrder()
        else -> generateRandomData(params)
    }
    val sql = generateInsertion(table, fields, values)
    val query = connection.prepareStatement(sql)
    query.execute()
    outputFile.write(sql)
}

private fun generateStr(capacity: Int, numbers: Boolean): StringBuilder {
    val length = (Math.random() * (capacity - 1) + 1).toInt()
    val sb = StringBuilder(if (!numbers) length + 2 else length)
    if (!numbers)
        sb.append('\'')
    for (i in 0 until length) {
        if (numbers)
            sb.append('0' + (Math.random() * 10).toInt())
        else
            sb.append('А' + (Math.random() * 33).toInt())
    }
    if (!numbers)
        sb.append('\'')
    return sb
}

private fun generateDate(): String {
    val year = (Math.random() * 3).toInt() + 2020
    val month = (Math.random() * 12).toInt() + 1
    val even = month % 2 == 0
    val firstHalf = month <= 7
    val date = when {
        month == 2 -> {
            if (year % 4 == 0 && year % 100 != 0 || year % 400 == 0)
                (Math.random() * 29).toInt() + 1
            else
                (Math.random() * 28).toInt() + 1
        }
        !even && firstHalf || even && !firstHalf -> {
            (Math.random() * 31).toInt() + 1
        } else -> (Math.random() * 30).toInt() + 1
    }
    return String.format("'%04d-%02d-%02d'", year, month, date)
}

private fun convertTimeToString(time: Int) = String.format(
    "'%02d:%02d'",
    time / 60, time % 60
)

private fun generateIntegerIncludingStartAndEnd(start: Int, end: Int) = (Math.random() * (end + 1 - start)).toInt() + start

private fun generatePhone(): String {
    val sb = StringBuilder("89")
    for (i in 0 until 9) {
        sb.append('0' + (Math.random() * 10).toInt())
    }
    return sb.toString()
}

private fun generateBool(p: Float) = Math.random() >= p

private fun generateDecimal(capacity: Int, afterComma: Int): String {
    val num = generateStr(capacity, true)
    if (afterComma > 0) {
        num.append('.', capacity - afterComma)
    }
    return num.toString()
}

private fun generateNameSurnamePatronymic(num: Int, genderMan: Boolean) = if (genderMan)
        "'${namesMen[generateIntegerIncludingStartAndEnd(0, namesMen.lastIndex)][num]}'"
    else
        "'${namesWomen[generateIntegerIncludingStartAndEnd(0, namesWomen.lastIndex)][num]}'"

private fun generateStreet() =
    "'${streets[generateIntegerIncludingStartAndEnd(0, streets.lastIndex)]}'"
