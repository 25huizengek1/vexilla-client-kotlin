package me.huizengek.vexilla.client

public interface Logger {
    public fun info(msg: String)
    public fun warn(msg: String)
    public fun err(msg: String)
    public fun debug(msg: String)
}

internal fun simpleLogger(name: String, enabled: Boolean = true) = object : Logger {
    private fun log(level: String, msg: String) {
        if (enabled) println("[$level $name: $msg")
    }

    override fun info(msg: String) = log("INFO", msg)
    override fun warn(msg: String) = log("WARN", msg)
    override fun err(msg: String) = log("ERROR", msg)
    override fun debug(msg: String) = log("DEBUG", msg)
}
