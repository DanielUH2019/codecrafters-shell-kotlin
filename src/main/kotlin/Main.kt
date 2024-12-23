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
        val splitUserInput = userInput.split(" ")
        val command = splitUserInput[0]
        val args = if (splitUserInput.size > 1) splitUserInput.subList(1, splitUserInput.size) else emptyList()

        val builtin = BuiltinCommand.fromCommand(command)
        if (builtin != null) {
            handleBuiltinCommand(builtin, args, shellState)
        } else {
            executeExternalCommand(command, args, shellState)
        }
    } while (true)
}

fun handleBuiltinCommand(builtin: BuiltinCommand, args: List<String>, shellState: ShellState) {
    when (builtin) {
        BuiltinCommand.EXIT -> {
            val exitCode = args.getOrNull(0)?.toIntOrNull() ?: 0 // Parse exit code or default to 0
            exitProcess(exitCode)
        }
        BuiltinCommand.ECHO -> println(args.joinToString(" "))
        BuiltinCommand.PWD -> println(shellState.currentDirectory.absolutePath)
        BuiltinCommand.CD -> changeDirectory(shellState, args.firstOrNull() ?: shellState.environmentVariables[EnvVar.HOME]!!)
        BuiltinCommand.TYPE -> runTypeBuiltin(args, shellState)
    }
}

fun runTypeBuiltin(args: List<String>, shellState: ShellState) {
    val command = args.firstOrNull() ?: ""
    if (command.isEmpty()) {
        println()
        return
    }

    val isBuiltin = BuiltinCommand.fromCommand(command) != null
    if (isBuiltin) {
        println("$command is a shell builtin")
        return
    }

    val pathDirectories = shellState.environmentVariables[EnvVar.PATH]?.split(":") ?: emptyList()
    pathDirectories.forEach{ path ->
        val commandFile = Path.of(path, command)
        if (commandFile.isExecutable()) {
            println("$command is $path/$command")
            return
        }
    }
    println("$command: not found")
}

fun findSourceDirectoryFromRelativePath(currentDirectory: File, path: String): Pair<File, String> {
    val currentDirectory = currentDirectory.parentFile
    val newPath = path.drop(3)
    if (currentDirectory.startsWith("../")){
        return findSourceDirectoryFromRelativePath(currentDirectory, newPath)
    }
    return Pair(currentDirectory, newPath)

}


fun changeDirectory(shellState: ShellState, newPath: String) {
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
        println("cd: $newPath: No such file or directory")
    }
}

fun executeExternalCommand(command: String, args: List<String>, shellState: ShellState) {
    val pathDirectories = shellState.environmentVariables[EnvVar.PATH]?.split(":") ?: emptyList()
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