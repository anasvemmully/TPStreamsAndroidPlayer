package com.tpstreams.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * MediaSessionService that manages playback in the background with media notifications.
 * This service enables Spotify-like persistent playback when the app is backgrounded.
 */
@OptIn(UnstableApi::class)
class TPSPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        private const val TAG = "TPSPlaybackService"

        // Notification ID for the media notification
        const val NOTIFICATION_ID = 1002

        // Global reference to the current player (set by TPStreamsPlayer)
        @Volatile
        var currentPlayer: Player? = null

        // Session activity for notification tap action (set by TPStreamsPlayer)
        @Volatile
        var sessionActivityIntent: PendingIntent? = null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PlaybackService created")
        initializeSessionAndNotification()
    }

    private fun initializeSessionAndNotification() {
        val player = currentPlayer
        if (player == null) {
            Log.w(TAG, "No player available, service will wait for player binding")
            return
        }

        try {
            // Create the MediaSession
            mediaSession = MediaSession.Builder(this, player)
                .apply {
                    // Set session activity for when user taps the notification
                    sessionActivityIntent?.let { pendingIntent ->
                        setSessionActivity(pendingIntent)
                    }
                }
                .build()

            Log.d(TAG, "MediaSession created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating MediaSession", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // When task is removed from recents, check if we should stop the service
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady) {
            // If not playing, stop the service
            stopSelf()
        }
        // If playing, continue in background (Spotify-like behavior)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "PlaybackService destroyed")
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /**
     * Called when a player is set or updated. Should be invoked by TPStreamsPlayer
     * when notification is enabled.
     */
    fun updatePlayer(player: Player) {
        Log.d(TAG, "Updating player in service")
        currentPlayer = player

        if (mediaSession == null) {
            initializeSessionAndNotification()
        } else {
            // Update existing session with new player
            mediaSession?.player = player
        }
    }
}
