import kotlin.io.path.*
import kotlin.system.exitProcess
import java.nio.file.Path




fun main(args: Array<String>) {
    val programArgs: Map<ProgramArg, String> = buildProgramArgMap(args)
    var pathDirectories = listOf<Path>()

    programArgs.forEach{ argName, argValue ->
        when (argName) {
            ProgramArg.PATH -> {
                pathDirectories = buildPathFromArgValue(argValue)
            }
        }
    }

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
            else -> println("$command: command not found")
        }

    } while (true)
}

enum class ProgramArg() {
    PATH
}

fun buildProgramArgMap(args: Array<String>): Map<ProgramArg, String> {
    val programArgMap = mutableMapOf<ProgramArg, String>()
//    println("total args $args")
    args.forEach { arg ->
        // Split argument into key=value (e.g., "PATH=/usr/bin")
        val parts = arg.split("=")
        if (parts.size == 2) {
            val key = parts[0]
            val value = parts[1]

            // Match the key to the corresponding ProgramArg
            try {
                // Attempt to convert the key to a ProgramArg
                val programArg = ProgramArg.valueOf(key)
                programArgMap[programArg] = value
//                println("added $programArg with $value")
            } catch (e: IllegalArgumentException) {
                // Ignore if the key is not a valid ProgramArg
                println("Warning: Unsupported key '$key' encountered.")
                throw e
            }
        }
    }

    return programArgMap
}

fun buildPathFromArgValue(argValue: String) : List<Path> {
    val directories = argValue.split(":")
    val paths = mutableListOf<Path>()
    directories.forEach({
        val p = Path(it)
        if (p.notExists() || !p.isDirectory()) {
            throw IllegalArgumentException("Path $p not exists")
        }
        paths.add(p)
    })
    return paths
}

fun runTypeBuiltin(command: String, pathDirectories: List<Path>) {
    if (command.isEmpty()) {
        println()
        return
    }

    val isBuiltin = when (command) {
        "exit" -> true
        "echo" -> true
        "type" -> true
        else -> false
    }

    if (isBuiltin) {
        println("$command is a shell builtin")
        return
    }

    pathDirectories.forEach{ path ->
        val commandFile = Path.of(path.toString(), command)
//        println("commandFIle $commandFile")
        if (commandFile.exists() && commandFile.isExecutable()) {
            println("$command is $path/$command")
            return
        }
    }
    println("$command not found")
}