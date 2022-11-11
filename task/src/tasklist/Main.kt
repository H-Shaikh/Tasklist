package tasklist

import kotlinx.datetime.*
import java.lang.RuntimeException
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

const val TASK_FIELD_WIDTH = 44

class Task(priority: Char, var date: String, var time: String) {
    var lineList = mutableListOf<String>()

    // dueTag specifies if the task can still be completed
    private val currentDate = Clock.System.now().toLocalDateTime(TimeZone.of("UTC+0")).date
    private val daysUntil = currentDate.daysUntil(date.toLocalDate())
    private val dueTag = if (daysUntil == 0) "T"
    else if (daysUntil > 0) "I"
    else "O"

    val dueTagColor = when (dueTag) {
        "I" -> "\u001B[102m \u001B[0m"
        "T" -> "\u001B[103m \u001B[0m"
        "O" -> "\u001B[101m \u001B[0m"
        else -> ""
    }

    var priorityColor = when (priority) {
        'C' -> "\u001B[101m \u001B[0m"
        'H' -> "\u001B[103m \u001B[0m"
        'N' -> "\u001B[102m \u001B[0m"
        'L' -> "\u001B[104m \u001B[0m"
        else -> ""
    }

    var priority: Char = priority
        set(value) {
            field = value
            priorityColor = when (value) {
                'C' -> "\u001B[101m \u001B[0m"
                'H' -> "\u001B[103m \u001B[0m"
                'N' -> "\u001B[102m \u001B[0m"
                'L' -> "\u001B[104m \u001B[0m"
                else -> ""
            }
        }

    fun add(line: String) {
        lineList.add(line)
    }
}

fun main() {
    val adapter = getTaskAdapter()
    val taskList = mutableListOf<Task>()
    readFromJson(taskList, adapter)
    if (!performBasicAction(taskList, adapter)) {
        return
    }
}

fun getTaskAdapter(): JsonAdapter<List<Task>> {
    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val type = Types.newParameterizedType(List::class.java, Task::class.java)
    return moshi.adapter(type)
}

fun performBasicAction(taskList: MutableList<Task>, adapter: JsonAdapter<List<Task>>): Boolean {
    var line = readBasicAction()
    while (true) {
        when (line.lowercase()) {
            "add" -> {
                processAddingTask(taskList)
                line = readBasicAction()
            }
            "print" -> {
                printTasks(taskList)
                line = readBasicAction()
            }
            "edit" -> {
                editOrDeleteTask(taskList, false)
                line = readBasicAction()
            }
            "delete" -> {
                editOrDeleteTask(taskList, true)
                line = readBasicAction()
            }
            "end" -> {
                println("Tasklist exiting!")
                saveToJson(taskList, adapter)
                return false
            }
            else -> {
                println("The input action is invalid")
                line = readBasicAction()
            }
        }
    }
}

fun readFromJson(taskList: MutableList<Task>, adapter: JsonAdapter<List<Task>>) {
    val jsonFile = File("tasklist.json")
    if (!jsonFile.exists()) return

    val prevTasks = adapter.fromJson(jsonFile.readText())
    if (prevTasks.isNullOrEmpty()) return
    for (i in prevTasks.indices) {
        taskList.add(prevTasks[i])
    }
}

fun saveToJson(taskList: MutableList<Task>, adapter: JsonAdapter<List<Task>>) {
    val jsonFile = File("tasklist.json")
    if (!jsonFile.exists()) jsonFile.createNewFile()
    jsonFile.writeText(adapter.toJson(taskList))
}

fun editOrDeleteTask(taskList: MutableList<Task>, toDelete: Boolean) {
    if (noTasksCheck(taskList)) return
    printTasks(taskList)

    var flag = true
    while (flag) {
        println("Input the task number (1-${taskList.size}):")
        val inp = readln()
        if (!isNumeric(inp) || inp.toInt() !in 1..taskList.size) {
            println("Invalid task number")
        } else {
            if (toDelete) {
                taskList.removeAt(inp.toInt() - 1)
                println("The task is deleted")
            } else {
                handleTaskEdit(taskList, inp.toInt() - 1)
            }
            flag = false
        }
    }
}

fun handleTaskEdit(taskList: MutableList<Task>, index: Int) {
    val fieldsToEdit: List<String> = listOf("priority", "date", "time", "task")
    var flag = true
    while (flag) {
        println("Input a field to edit (priority, date, time, task):")
        val inp = readln()
        if (inp !in fieldsToEdit) {
            println("Invalid field")
        } else {
            when (inp) {
                "priority" -> {
                    taskList[index].priority = processPriority()
                }
                "date" -> {
                    taskList[index].date = processDate()
                }
                "time" -> {
                    taskList[index].time = processTime()
                }
                "task" -> {
                    taskList[index].lineList.clear()
                    processTaskBody(taskList, taskList[index], false, index)
                }
            }
            println("The task is changed")
            flag = false
        }
    }
}

fun isNumeric(str: String) = str.all { it in '0'..'9' }

fun readBasicAction(): String {
    println("Input an action (add, print, edit, delete, end):")
    return readln()
}

fun noTasksCheck(taskList: MutableList<Task>): Boolean {
    if (taskList.isEmpty()) {
        println("No tasks have been input")
        return true
    }
    return false
}

fun processAddingTask(taskList: MutableList<Task>) {
    val priority = processPriority()
    val date = processDate()
    val time = processTime()
    val task = Task(priority, date, time)
    processTaskBody(taskList, task, true, null)
}

fun processTaskBody(taskList: MutableList<Task>, task: Task, newTask: Boolean, index: Int?) {
    println("Input a new task (enter a blank line to end):")
    var line = readln().trim()
    while (line.isNotBlank()) {
        task.add(line)
        line = readln().trim()
    }

    if (task.lineList.isEmpty()) {
        println("The task is blank")
    } else {
        if (newTask) taskList.add(task)
        else taskList[index!!] = task
    }
}

fun processPriority(): Char {
    println("Input the task priority (C, H, N, L):")
    val priorities = listOf('C', 'H', 'N', 'L')
    var inp: String = readln().uppercase()
    while (inp.length != 1 || inp[0] !in priorities) {
        println("Input the task priority (C, H, N, L):")
        inp = readln().uppercase()
    }
    return inp[0]
}

fun processDate(): String {
    val type = "date"
    val userInpRequest = userInpRequest(type)
    println(userInpRequest)
    var inpDate: LocalDate
    while (true) {
        try {
            val inp = readln().split('-')
            if (inp.size != 3) {
                handleInvalidInput(type, userInpRequest)
                continue
            }
            val res = inp[0].padStart(4, '0') + '-' +
                    inp[1].padStart(2, '0') + '-' +
                    inp[2].padStart(2, '0')
            inpDate = LocalDate.parse(res)
            break
        } catch (e: RuntimeException) {
            handleInvalidInput(type, userInpRequest)
        }
    }
    return inpDate.toString()
}

fun processTime(): String {
    val type = "time"
    val userInpRequest = userInpRequest(type)
    var inpTime: LocalDateTime
    println(userInpRequest)
    while (true) {
        try {
            val inp = readln().split(':')
            val res = inp[0].padStart(2, '0') + ':' + inp[1].padStart(2, '0')
            val inpTimeStr = "1000-01-01T$res"
            inpTime = LocalDateTime.parse(inpTimeStr)
            break
        } catch(e: RuntimeException) {
            handleInvalidInput(type, userInpRequest)
        }
    }
    return inpTime.hour.toString().padStart(2, '0') + ":" +
            inpTime.minute.toString().padStart(2, '0')
}

fun userInpRequest(type: String) = "Input the $type " + if (type == "date") "(yyyy-mm-dd):" else "(hh:mm):"

fun handleInvalidInput(type: String, userInpRequest: String) {
    println("The input $type is invalid")
    println(userInpRequest)
}

fun printTasks(taskList: MutableList<Task>) {
    val asciiRow = "+----+------------+-------+---+---+--------------------------------------------+"
    val header = "| N  |    Date    | Time  | P | D |                   Task                     |"
    val asciiRowBeforeTask = "|    |            |       |   |   |"

    if (noTasksCheck(taskList)) return

    println("$asciiRow\n$header\n$asciiRow")
    taskList.forEachIndexed { index, task ->
        print(
            "| ${
                (index + 1).toString().padEnd(3)
            }| ${task.date} | ${task.time} | ${task.priorityColor} | ${task.dueTagColor} |"
        )
        var counter = 0
        for (i in 0 until task.lineList.size) {
            if (i != 0) print(asciiRowBeforeTask)
            if (task.lineList[i].length > TASK_FIELD_WIDTH) {
                for (letter in task.lineList[i]) {
                    if (counter == TASK_FIELD_WIDTH) {
                        print("|\n$asciiRowBeforeTask")
                        counter = 0
                    }
                    print(letter)
                    counter++
                }
                println("".padEnd(TASK_FIELD_WIDTH - counter) + "|")
                counter = 0
            } else {
                println(task.lineList[i].padEnd(TASK_FIELD_WIDTH) + "|")
            }
        }
        println(asciiRow)
    }
}

//my spahetti , obviously won’t remember it