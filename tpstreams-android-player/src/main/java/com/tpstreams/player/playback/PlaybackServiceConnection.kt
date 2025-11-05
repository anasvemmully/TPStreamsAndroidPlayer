package com.tpstreams.player.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.tpstreams.player.TPStreamsPlayer

/**
 * Manages connection to the background playback service
 */
class PlaybackServiceConnection(private val context: Context) {

    private var service: TPStreamsPlaybackService? = null
    private var isBound = false
    private val pendingPlayers = mutableListOf<TPStreamsPlayer>()
    private val tag = "PlaybackServiceConnection"

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(tag, "Service connected")
            val serviceBinder = binder as? TPStreamsPlaybackService.PlaybackServiceBinder
            service = serviceBinder?.getService()
            isBound = true

            // Register any pending players
            pendingPlayers.forEach { player ->
                service?.registerPlayer(player)
            }
            pendingPlayers.clear()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(tag, "Service disconnected")
            service = null
            isBound = false
        }
    }

    init {
        bindToService()
    }

    private fun bindToService() {
        val intent = Intent(context, TPStreamsPlaybackService::class.java)
        context.startService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun registerPlayer(player: TPStreamsPlayer) {
        if (isBound && service != null) {
            service?.registerPlayer(player)
        } else {
            // Queue for later when service is bound
            pendingPlayers.add(player)
        }
    }

    fun unregisterPlayer(player: TPStreamsPlayer) {
        pendingPlayers.remove(player)
        service?.unregisterPlayer(player)

        // Unbind from service when no players remain
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(tag, "Error unbinding service: ${e.message}")
            }
            isBound = false
        }
    }

    fun updateMetadata(player: TPStreamsPlayer, title: String, artist: String) {
        service?.updateMetadata(player, title, artist)
    }
}
