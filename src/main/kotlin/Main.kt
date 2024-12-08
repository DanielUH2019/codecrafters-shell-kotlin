import kotlin.system.exitProcess



fun main() {
    do {
        print("$ ")
        val userInput = readln()
        val splitUserInput = userInput.split(" ")
        val command = splitUserInput[0]
        val args = if (splitUserInput.size > 1) splitUserInput.subList(1, splitUserInput.size) else null

        when (command) {
            "exit" -> {
                val exitCode = args?.getOrNull(0)?.toIntOrNull() ?: 0 // Parse exit code or default to 0
                exitProcess(exitCode)
            }
            "echo" -> println(args?.joinToString(" ") ?: "")
            "type" -> runTypeBuiltin(args?.getOrNull(0) ?: "")
            else -> println("$command: command not found")
        }

    } while (true)
}

fun runTypeBuiltin(command: String) {
    if (command.isEmpty()) {
        println()
        return
    }
    val isSupported = when (command) {
        "exit" -> true
        "echo" -> true
        "type" -> true
        else -> false
    }
    if (isSupported) println("$command is a shell builtin") else println("$command: not found")
}