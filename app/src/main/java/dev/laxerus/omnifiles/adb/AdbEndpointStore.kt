package dev.laxerus.omnifiles.adb

import android.content.Context

data class AdbEndpoint(val host: String, val port: Int)

class AdbEndpointStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("adb_endpoint", Context.MODE_PRIVATE)

    fun save(endpoint: AdbEndpoint) {
        prefs.edit()
            .putString("host", endpoint.host)
            .putInt("port", endpoint.port)
            .apply()
    }

    fun load(): AdbEndpoint? {
        val host = prefs.getString("host", null)?.trim().orEmpty()
        val port = prefs.getInt("port", -1)
        return if (host.isNotEmpty() && port in 1..65535) AdbEndpoint(host, port) else null
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
