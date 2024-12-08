import kotlin.system.exitProcess

fun main() {
    do {
        print("$ ")
        val userInput = readln()
        val splittedUserInput = userInput.split(" ")
        val command = splittedUserInput[0]
        val args = if (splittedUserInput.size > 1) splittedUserInput.subList(1, splittedUserInput.size) else null

        when (command) {
            "exit" -> {
                val exitCode = args?.getOrNull(0)?.toIntOrNull() ?: 0 // Parse exit code or default to 0
                exitProcess(exitCode)
            }
            "echo" -> println(args?.joinToString(" ") ?: "")
            else -> println("$command: command not found")
        }

    } while (true)
}