import java.io.IOException
import kotlin.io.path.*
import kotlin.system.exitProcess
import java.nio.file.Path


fun main() {
    var pathDirectories = System.getenv("PATH")?.split(":") ?: emptyList()
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
            "type" -> runTypeBuiltin(args?.getOrNull(0) ?: "", pathDirectories)
            "pwd" -> println(Path.of("").toAbsolutePath().toString())
            else -> runCommandIfIsInPath(command, args ?: listOf<String>(), pathDirectories)
        }
    } while (true)
}

fun runTypeBuiltin(command: String, pathDirectories: List<String>) {
    if (command.isEmpty()) {
        println()
        return
    }

    val isBuiltin = when (command) {
        "exit" -> true
        "echo" -> true
        "type" -> true
        "pwd" -> true
        else -> false
    }
    if (isBuiltin) {
        println("$command is a shell builtin")
        return
    }

    pathDirectories.forEach{ path ->
        val commandFile = Path.of(path, command)
        if (commandFile.isExecutable()) {
            println("$command is $path/$command")
            return
        }
    }
    println("$command: not found")
}

fun runCommandIfIsInPath(command: String, args: List<String>, pathDirectories: List<String>) {
    pathDirectories.forEach { path ->
        val commandFile = Path.of(path, command)
        if (commandFile.exists() && commandFile.isExecutable()) {
            try {
                val process = ProcessBuilder(listOf(commandFile.toString()) + args)
                    .inheritIO() // Ensures the process uses the same I/O as the parent process
                    .start()
                process.waitFor() // Wait for the process to complete
                return
            } catch (e: IOException) {
                println("Error executing command: ${e.message}")
                return
            }
        }
        else {
            println("$command: command not found")
            return
        }
    }
}