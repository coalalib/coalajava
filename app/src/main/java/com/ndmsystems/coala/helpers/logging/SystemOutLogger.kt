package com.ndmsystems.coala.helpers.logging

class SystemOutLogger(private val tagPrefix: String) : ILogger {
    override fun v(message: String) {
        println(getTag("V") + " " + message)
    }

    override fun d(message: String) {
        println(getTag("D") + " " + message)
    }

    override fun i(message: String) {
        println(getTag("I") + " " + message)
    }

    override fun w(message: String) {
        println(getTag("W") + " " + message)
    }

    override fun e(message: String) {
        println(getTag("E") + " " + message)
    }

    private fun getTag(severity: String): String {
        val fullClassName = Thread.currentThread().stackTrace[4].className
        val className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1)
        val methodName = Thread.currentThread().stackTrace[4].methodName
        val lineNumber = Thread.currentThread().stackTrace[4].lineNumber
        return "$severity: $tagPrefix$className$methodName():$lineNumber"
    }
}