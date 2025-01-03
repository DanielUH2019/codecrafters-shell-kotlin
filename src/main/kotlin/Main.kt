import java.io.File
import java.io.IOException
import kotlin.io.path.*
import kotlin.system.exitProcess
import java.nio.file.Path

enum class EnvVar(val key: String) {
    PATH("PATH"),
    HOME("HOME");

    companion object {
        fun fromKey(key: String): EnvVar? = EnvVar.entries.find { it.key == key }
    }
}

enum class BuiltinCommand(val command: String) {
    EXIT("exit"),
    ECHO("echo"),
    PWD("pwd"),
    CD("cd"),
    TYPE("type");

    companion object {
        fun fromCommand(command: String): BuiltinCommand? = BuiltinCommand.entries.find { it.command == command }
    }
}

data class ShellState(
    var currentDirectory: File,
    val environmentVariables: MutableMap<EnvVar, String> = mutableMapOf()
)

data class RedirectionResult(
    val outputFile: File? = null, // File for output redirection (`>`, `>>`)
    val appendOutput: Boolean = false, // Whether to append to output file (`>>`)
    val redirectToStdeer: Boolean = false
)

fun main() {
    val shellState = ShellState(
        currentDirectory = File(System.getProperty("user.dir")).canonicalFile,
        environmentVariables = mutableMapOf(
            EnvVar.PATH to (System.getenv("PATH") ?: ""),
            EnvVar.HOME to (System.getenv("HOME") ?: "/")
        )
    )
    do {
        print("$ ")
        val userInput = readln()
        val splitUserInput = tokenizeInput(userInput)
        val command = splitUserInput[0]
        var args = if (splitUserInput.size > 1) splitUserInput.subList(1, splitUserInput.size) else emptyList()
        val (argsWithoutRedirections, redirectionResult) = parseRedirectionOperators(args)
        val builtin = BuiltinCommand.fromCommand(command)
        if (builtin != null) {
            handleBuiltinCommand(builtin, argsWithoutRedirections, redirectionResult, shellState)
        } else {
            executeExternalCommand(command, argsWithoutRedirections, redirectionResult, shellState)
        }
    } while (true)
}

fun parseRedirectionOperators(args: List<String>): Pair<List<String>, RedirectionResult> {
    var outputFile: File? = null
    var appendOutput = false
    val actualArgs = mutableListOf<String>()

    if( args.isEmpty()) {
        return Pair(args, RedirectionResult())
    }

    val redirectToStdeer = args[0].startsWith("2")
    var i = 0
    while (i < args.size) {
        when {
            args[i] == "1>" || args[i] == ">" || args[i] == "2>" -> {
                if (i + 1 < args.size) {
                    outputFile = File(args[++i])
                } else {
                    println("Syntax error: expected file after '>'")
                    throw IllegalArgumentException()
                }
            }
            args[i] == "1>>" || args[i] == "2>>" || args[i] == ">>" -> {
                if (i + 1 < args.size) {
                    outputFile = File(args[++i])
                    appendOutput = true
                } else {
                    println("Syntax error: expected file after '>>'")
                    throw IllegalArgumentException()
                }
            }
            else -> {
                actualArgs.add(args[i])
            }
        }
        i+=1
    }
    val redirectionResult = RedirectionResult(
        outputFile = outputFile,
        appendOutput = appendOutput,
        redirectToStdeer = redirectToStdeer
    )
    return Pair(actualArgs, redirectionResult)
}


fun tokenizeInput(input: String): List<String> {
    val tokens = mutableListOf<String>()
    val token = StringBuilder()
    var inSingleQuotes = false
    var inDoubleQuotes = false
    var escaping = false
    val allowedCharsForEscaping = arrayOf('\\', '$', '"')
    var i: Int = 0
    while (i < input.length) {
        val char = input[i]
        val nextChar = if (i + 1 < input.length) input[i + 1] else null // Peek at the next character
        when {
            escaping -> {
                token.append(char)
                escaping = false
            }
            char == '\\' -> {
                if (inDoubleQuotes && nextChar in allowedCharsForEscaping || (!inDoubleQuotes && !inSingleQuotes)) {
                    escaping=true
                } else {
                    token.append(char)
                }
            }
            char == '\'' -> {
                if (inDoubleQuotes) {
                    token.append(char)
                } else {
                    inSingleQuotes = !inSingleQuotes
                }
            }
            char == '"' -> {
                if (inSingleQuotes) {
                    token.append(char)
                } else {
                    inDoubleQuotes = !inDoubleQuotes
                }
            }
            char.isWhitespace() -> {
                if (inSingleQuotes || inDoubleQuotes) {
                    token.append(char)
                } else if (token.isNotEmpty()) {
                    tokens.add(token.toString())
                    token.clear()
                }
            }
            else -> {
                token.append(char)
            }
        }
        i++
    }

    if (token.isNotEmpty()) {
        tokens.add(token.toString())
    }

    return tokens
}


fun handleBuiltinCommand(builtin: BuiltinCommand, args: List<String>, redirection: RedirectionResult, shellState: ShellState) {
     val result = when (builtin) {
        BuiltinCommand.EXIT -> {
            val exitCode = args.getOrNull(0)?.toIntOrNull() ?: 0 // Parse exit code or default to 0
            exitProcess(exitCode)
        }
        BuiltinCommand.ECHO -> args.joinToString(" ")
        BuiltinCommand.PWD -> shellState.currentDirectory.absolutePath
        BuiltinCommand.CD -> changeDirectory(shellState, args.firstOrNull() ?: shellState.environmentVariables[EnvVar.HOME]!!)
        BuiltinCommand.TYPE -> runTypeBuiltin(args, shellState)
    }

    if (result == null || result.isEmpty()) {
        return
    }

    if (redirection.outputFile != null) {
        try {
            val outputMode = if (redirection.appendOutput) "APPEND" else "WRITE"
            val outputFile = redirection.outputFile
            val writer = if (redirection.appendOutput) {
                outputFile.appendText(result + "\n")
            } else {
                outputFile.writeText(result + "\n")
            }

        } catch (e: IOException) {
//            println("Error writing to file ${redirection.outputFile.absolutePath}: ${e.message}")
        }
    } else {
        // Default behavior: Print to standard output
        if (redirection.redirectToStdeer) {
            System.err.println(result)
        } else {
            println(result)
        }
    }


}

fun runTypeBuiltin(args: List<String>, shellState: ShellState) : String {
    val command = args.firstOrNull() ?: ""
    if (command.isEmpty()) {
        return ""
    }

    val isBuiltin = BuiltinCommand.fromCommand(command) != null
    if (isBuiltin) {
        return "$command is a shell builtin"
    }

    val pathDirectories = shellState.environmentVariables[EnvVar.PATH]?.split(":") ?: emptyList()
    pathDirectories.forEach{ path ->
        val commandFile = Path.of(path, command)
        if (commandFile.isExecutable()) {
            return "$command is $path/$command"
        }
    }
    return "$command: not found"
}

fun findSourceDirectoryFromRelativePath(currentDirectory: File, path: String): Pair<File, String> {
    val currentDirectory = currentDirectory.parentFile
    val newPath = path.drop(3)
    if (currentDirectory.startsWith("../")){
        return findSourceDirectoryFromRelativePath(currentDirectory, newPath)
    }
    return Pair(currentDirectory, newPath)
}


fun changeDirectory(shellState: ShellState, newPath: String) : String {
    val newPathWithHomeReplaced = if (newPath.startsWith("~/")) {
        newPath.replace("~/", shellState.environmentVariables[EnvVar.HOME]!!)
    } else if (newPath.startsWith("~")) {
        newPath.replace("~", shellState.environmentVariables[EnvVar.HOME]!!)
    } else {
        newPath
    }
    val (sourceDirectory, newPath) = if (newPathWithHomeReplaced.startsWith("../")) {
        findSourceDirectoryFromRelativePath(shellState.currentDirectory, newPathWithHomeReplaced)
    } else {
        Pair(shellState.currentDirectory, newPathWithHomeReplaced)
    }

    val targetDirectory = if (newPath.startsWith("/")) {
        File(newPath)
    } else {
        File(sourceDirectory, newPath)
    }

    if (targetDirectory.exists() && targetDirectory.isDirectory) {
        shellState.currentDirectory = targetDirectory.canonicalFile
    } else {
        return "cd: $newPath: No such file or directory"
    }
    return ""
}

fun executeExternalCommand(command: String, args: List<String>, redirection: RedirectionResult, shellState: ShellState) {
    val pathDirectories = shellState.environmentVariables[EnvVar.PATH]?.split(":") ?: emptyList()
    pathDirectories.forEach { path ->
        val commandFile = Path.of(path, command)
        if (commandFile.exists() && commandFile.isExecutable()) {
            try {
                val processBuilder = ProcessBuilder(listOf(commandFile.toString()) + args)

                // Redirect I/O streams if necessary
                if (redirection.outputFile != null) {
                    if (redirection.appendOutput) {
                        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(redirection.outputFile))
                    } else {
                        processBuilder.redirectOutput(redirection.outputFile).redirectError(ProcessBuilder.Redirect.INHERIT)
                    }
                }  else {
                    processBuilder.inheritIO() // Ensure normal I/O behavior if no redirection is set
                }

                val process = processBuilder.start()
                process.waitFor()
                return
            } catch (e: IOException) {
                println("Error executing command: ${e.message}")
                return
            }
        }
    }
    println("$command: command not found")
}