package io.sweatshop.herekitty.adb

data class AdbEndpoint(val host: String = DEFAULT_HOST, val port: Int = DEFAULT_PORT) {
    companion object {
        const val DEFAULT_HOST: String = "127.0.0.1"
        const val DEFAULT_PORT: Int = 5037

        fun fromEnvironment(): AdbEndpoint {
            val port = System.getenv("ANDROID_ADB_SERVER_PORT")?.toIntOrNull() ?: DEFAULT_PORT
            val host = System.getenv("ANDROID_ADB_SERVER_ADDRESS") ?: DEFAULT_HOST
            return AdbEndpoint(host, port)
        }
    }
}

class AdbProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)
