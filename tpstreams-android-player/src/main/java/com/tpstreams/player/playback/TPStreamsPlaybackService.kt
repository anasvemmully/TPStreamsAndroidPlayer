package com.tpstreams.player.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata as AndroidMediaMetadata
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import coil.ImageLoader
import coil.request.ImageRequest
import com.tpstreams.player.R
import com.tpstreams.player.TPStreamsPlayer

/**
 * Foreground service that manages background playback with media notifications
 */
@OptIn(UnstableApi::class)
class TPStreamsPlaybackService : MediaSessionService() {

    private var media3Session: MediaSession? = null
    private var mediaSessionCompat: MediaSessionCompat? = null
    private var activePlayer: TPStreamsPlayer? = null
    private var notificationManager: NotificationManager? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isUpdatingNotification = false
    private var currentArtwork: Bitmap? = null

    private val binder = PlaybackServiceBinder()
    private val tag = "TPStreamsPlaybackService"

    private val notificationId = 1001
    private val channelId = "tpstreams_playback_channel"

    companion object {
        const val ACTION_PLAY = "com.tpstreams.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.tpstreams.player.ACTION_PAUSE"
        const val ACTION_SEEK = "com.tpstreams.player.ACTION_SEEK"
        const val EXTRA_SEEK_POSITION = "seek_position"
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY -> {
                    Log.d(tag, "Play action received")
                    activePlayer?.play()
                    updateNotification()
                }
                ACTION_PAUSE -> {
                    Log.d(tag, "Pause action received")
                    activePlayer?.pause()
                    updateNotification()
                }
                ACTION_SEEK -> {
                    val position = intent.getLongExtra(EXTRA_SEEK_POSITION, 0)
                    Log.d(tag, "Seek action received: $position")
                    activePlayer?.seekTo(position)
                    updateNotification()
                }
            }
        }
    }

    inner class PlaybackServiceBinder : Binder() {
        fun getService(): TPStreamsPlaybackService = this@TPStreamsPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // Register control receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, IntentFilter().apply {
                addAction(ACTION_PLAY)
                addAction(ACTION_PAUSE)
                addAction(ACTION_SEEK)
            }, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(controlReceiver, IntentFilter().apply {
                addAction(ACTION_PLAY)
                addAction(ACTION_PAUSE)
                addAction(ACTION_SEEK)
            })
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(tag, "Service bound")
        super.onBind(intent)
        return binder
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return media3Session
    }

    fun registerPlayer(player: TPStreamsPlayer) {
        if (!player.enableBackgroundPlayback) {
            Log.d(tag, "Player does not have background playback enabled, hiding notification")
            hideNotification()
            return
        }

        Log.d(tag, "Registering player for background playback")
        activePlayer = player

        // Initialize media sessions
        initializeMediaSessions(player)

        // Create and show notification
        createAndShowNotification(player)

        // Listen to player state changes
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(tag, "Player is playing changed: $isPlaying")
                updateNotification()
                updateMediaSession()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(tag, "Player state changed: $playbackState")
                updateNotification()
                updateMediaSession()
            }
        })
    }

    fun unregisterPlayer(player: TPStreamsPlayer) {
        if (activePlayer == player) {
            Log.d(tag, "Unregistering active player")
            stopUpdatingNotification()
            hideNotification()
            activePlayer = null
        }
    }

    fun updateMetadata(player: TPStreamsPlayer, title: String, artist: String) {
        if (activePlayer == player) {
            updateMediaSession()
            updateNotification()
        }
    }

    private fun initializeMediaSessions(player: TPStreamsPlayer) {
        // Create Media3 MediaSession for routing
        if (media3Session == null) {
            media3Session = MediaSession.Builder(this, player)
                .build()
        } else {
            media3Session?.player = player
        }

        // Create MediaSessionCompat for notification controls
        if (mediaSessionCompat == null) {
            mediaSessionCompat = MediaSessionCompat(this, "TPStreamsPlayback").apply {
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )

                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        Log.d(tag, "MediaSession onPlay")
                        activePlayer?.play()
                    }

                    override fun onPause() {
                        Log.d(tag, "MediaSession onPause")
                        activePlayer?.pause()
                    }

                    override fun onSeekTo(pos: Long) {
                        Log.d(tag, "MediaSession onSeekTo: $pos")
                        activePlayer?.seekTo(pos)
                    }

                    override fun onStop() {
                        Log.d(tag, "MediaSession onStop")
                        activePlayer?.pause()
                    }
                })

                isActive = true
            }
        }

        updateMediaSession()
    }

    private fun updateMediaSession() {
        val player = activePlayer ?: return
        val sessionCompat = mediaSessionCompat ?: return

        // Update metadata
        val metadata = player.mediaMetadata
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(AndroidMediaMetadata.METADATA_KEY_TITLE, metadata.title?.toString() ?: "")
            .putString(AndroidMediaMetadata.METADATA_KEY_ARTIST, metadata.artist?.toString() ?: "TPStreams")
            .putLong(AndroidMediaMetadata.METADATA_KEY_DURATION, player.duration)

        currentArtwork?.let {
            metadataBuilder.putBitmap(AndroidMediaMetadata.METADATA_KEY_ALBUM_ART, it)
        }

        sessionCompat.setMetadata(metadataBuilder.build())

        // Update playback state
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(
                if (player.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                player.currentPosition,
                player.playbackParameters.speed
            )

        sessionCompat.setPlaybackState(stateBuilder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for media playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createAndShowNotification(player: TPStreamsPlayer) {
        val notification = buildNotification(player)
        startForeground(notificationId, notification)
        startUpdatingNotification()

        // Load artwork asynchronously
        val artworkUri = player.mediaMetadata.artworkUri
        if (artworkUri != null) {
            loadArtwork(artworkUri.toString())
        }
    }

    private fun buildNotification(player: TPStreamsPlayer): Notification {
        val metadata = player.mediaMetadata
        val title = metadata.title?.toString() ?: "Unknown"
        val artist = metadata.artist?.toString() ?: "TPStreams"

        // Content intent to open the app
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Create custom notification layout
        val notificationLayout = RemoteViews(packageName, R.layout.notification_media_control)
        val notificationLayoutExpanded = RemoteViews(packageName, R.layout.notification_media_control_expanded)

        // Set common content
        updateRemoteViews(notificationLayout, player, title, artist, false)
        updateRemoteViews(notificationLayoutExpanded, player, title, artist, true)

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSessionCompat?.sessionToken)
            .setShowActionsInCompactView(0)

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setCustomContentView(notificationLayout)
            .setCustomBigContentView(notificationLayoutExpanded)
            .setStyle(style)
            .setOnlyAlertOnce(true)
            .setOngoing(player.isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun updateRemoteViews(
        remoteViews: RemoteViews,
        player: TPStreamsPlayer,
        title: String,
        artist: String,
        isExpanded: Boolean
    ) {
        remoteViews.setTextViewText(R.id.notification_title, title)
        remoteViews.setTextViewText(R.id.notification_subtitle, artist)

        // Set artwork
        if (currentArtwork != null) {
            remoteViews.setImageViewBitmap(R.id.notification_artwork, currentArtwork)
        } else {
            remoteViews.setImageViewResource(R.id.notification_artwork, R.drawable.ic_default_artwork)
        }

        // Play/Pause button
        val isPlaying = player.isPlaying
        remoteViews.setImageViewResource(
            R.id.notification_play_pause,
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        val playPauseIntent = Intent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
        val playPausePendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.notification_play_pause, playPausePendingIntent)

        // Progress bar (only in expanded view)
        if (isExpanded) {
            val duration = player.duration.toInt()
            val position = player.currentPosition.toInt()

            remoteViews.setProgressBar(R.id.notification_progress, duration, position, false)
            remoteViews.setTextViewText(R.id.notification_time_current, formatTime(position.toLong()))
            remoteViews.setTextViewText(R.id.notification_time_total, formatTime(duration.toLong()))
        }
    }

    private fun updateNotification() {
        val player = activePlayer ?: return
        if (!isUpdatingNotification) return

        val notification = buildNotification(player)
        notificationManager?.notify(notificationId, notification)
    }

    private fun startUpdatingNotification() {
        isUpdatingNotification = true
        handler.post(notificationUpdateRunnable)
    }

    private fun stopUpdatingNotification() {
        isUpdatingNotification = false
        handler.removeCallbacks(notificationUpdateRunnable)
    }

    private val notificationUpdateRunnable = object : Runnable {
        override fun run() {
            if (isUpdatingNotification && activePlayer?.isPlaying == true) {
                updateNotification()
                handler.postDelayed(this, 1000) // Update every second
            } else if (isUpdatingNotification) {
                handler.postDelayed(this, 1000) // Keep checking even when paused
            }
        }
    }

    private fun hideNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager?.cancel(notificationId)
    }

    private fun loadArtwork(url: String) {
        val imageLoader = ImageLoader(this)
        val request = ImageRequest.Builder(this)
            .data(url)
            .target { drawable ->
                currentArtwork = (drawable as? BitmapDrawable)?.bitmap
                updateNotification()
                updateMediaSession()
            }
            .build()
        imageLoader.enqueue(request)
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(tag, "Task removed")
        activePlayer?.pause()
        stopUpdatingNotification()
        hideNotification()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Service destroyed")

        try {
            unregisterReceiver(controlReceiver)
        } catch (e: Exception) {
            Log.e(tag, "Error unregistering receiver: ${e.message}")
        }

        stopUpdatingNotification()

        mediaSessionCompat?.apply {
            isActive = false
            release()
        }
        mediaSessionCompat = null

        media3Session?.apply {
            player.release()
            release()
        }
        media3Session = null

        activePlayer = null
        currentArtwork = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return Service.START_STICKY
    }
}
