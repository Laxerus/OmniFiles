package dev.laxerus.omnifiles.adb

import android.content.Context
import android.net.wifi.WifiManager
import com.flyfishxu.kadb.mdns.KadbMdnsAndroid
import com.flyfishxu.kadb.mdns.MdnsDiscoveryState
import kotlinx.coroutines.flow.StateFlow

class AdbDiscoveryManager(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mdns = KadbMdnsAndroid(appContext)
    private val multicastLock = runCatching {
        appContext.getSystemService(WifiManager::class.java)
            ?.createMulticastLock("OmniFiles-AdbDiscovery")
            ?.apply { setReferenceCounted(false) }
    }.getOrNull()

    val state: StateFlow<MdnsDiscoveryState> get() = mdns.state

    fun start() {
        runCatching {
            if (multicastLock != null && !multicastLock.isHeld) multicastLock.acquire()
        }
        mdns.start()
    }

    fun stop() {
        mdns.stop()
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock.release()
        }
    }

    override fun close() {
        mdns.close()
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock.release()
        }
    }
}
