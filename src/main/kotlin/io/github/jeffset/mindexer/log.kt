package io.github.jeffset.mindexer

interface Logger {
    fun verbose(message: String)
    fun error(message: String)
}

object SilentLogger : Logger {
    override fun verbose(message: String) = Unit
    override fun error(message: String) {
        System.err.println("[error] $message")
    }
}

object VerboseLogger : Logger by SilentLogger {
    override fun verbose(message: String) {
        println("[VERBOSE] $message")
    }
}
