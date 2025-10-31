# Background Playback & Notification Guide

This guide explains how to use the background playback and Spotify-like notification features in TPStreams Android Player.

## Features

- **Background Playback**: Continue playing video/audio when the app is minimized or removed from recent apps
- **Media Notifications**: Display a persistent notification with playback controls (Play/Pause, Close)
- **Progress Bar**: Show current playback position in the notification
- **Custom Metadata**: Provide custom title, artist, and artwork for notifications

## Prerequisites

The library automatically includes all required dependencies and permissions. No additional setup is needed.

## Basic Usage

### 1. Simple Background Playback (No Notification)

Enable background playback without showing notifications:

```kotlin
val player = TPStreamsPlayer.create(
    context = applicationContext,
    assetId = "your-asset-id",
    accessToken = "your-access-token",
    playInBackground = true  // Enable background playback
)
```

**Behavior**: Video continues playing when the app is backgrounded, but no notification is shown.

### 2. Background Playback with Notification

Enable both background playback and media notifications:

```kotlin
val player = TPStreamsPlayer.create(
    context = applicationContext,
    assetId = "your-asset-id",
    accessToken = "your-access-token",
    playInBackground = true,      // Enable background playback
    enableNotification = true     // Show media notification
)
```

**Behavior**: Video continues playing in background with a persistent notification showing:
- Video title and thumbnail (from API)
- Play/Pause button
- Close button
- Playback progress bar

### 3. Custom Notification Metadata

Provide custom metadata for the notification:

```kotlin
val notificationMetadata = NotificationMetadata(
    title = "Custom Video Title",
    artist = "Creator Name",
    artworkUri = Uri.parse("https://example.com/custom-artwork.jpg"),
    showProgress = true  // Show progress bar (default: true)
)

val player = TPStreamsPlayer.create(
    context = applicationContext,
    assetId = "your-asset-id",
    accessToken = "your-access-token",
    playInBackground = true,
    enableNotification = true,
    notificationMetadata = notificationMetadata
)
```

**Note**: Custom metadata overrides API-fetched data:
- If `title` is provided, it overrides the API title
- If `artworkUri` is provided, it overrides the API thumbnail
- If `artist` is provided, it's added to the notification

## Complete Example

```kotlin
class PlayerActivity : AppCompatActivity() {
    private lateinit var player: TPStreamsPlayer
    private lateinit var playerView: TPStreamsPlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)

        // Initialize SDK
        TPStreamsSDK.init("your-org-id")

        // Create player with background playback and notifications
        player = TPStreamsPlayer.create(
            context = applicationContext,
            assetId = "8rEx9apZHFF",
            accessToken = "your-access-token",
            shouldAutoPlay = true,
            playInBackground = true,      // Enable background playback
            enableNotification = true,    // Show notification
            notificationMetadata = NotificationMetadata(
                artist = "Your Channel Name",
                showProgress = true
            )
        )

        // Set player to view
        playerView.player = player

        // Optional: Set up token refresh for downloads
        player.listener = object : TPStreamsPlayer.Listener {
            override fun onAccessTokenExpired(videoId: String, callback: (String) -> Unit) {
                // Fetch new token and call callback
                val newToken = fetchNewToken(videoId)
                callback(newToken)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()  // Stops service and releases resources
    }

    private fun fetchNewToken(videoId: String): String {
        // Your token refresh logic
        return "new-access-token"
    }
}
```

## Notification Controls

The notification includes the following controls:

1. **Play/Pause Button**: Toggles playback state
2. **Close Button**: Stops playback and dismisses the notification
3. **Tap on Notification**: Returns to your app
4. **Progress Bar**: Shows current playback position (if `showProgress = true`)

## Behavior Details

### Background Playback (`playInBackground = true`)

- Video continues playing when app is minimized
- Video continues playing when app is removed from recent apps
- Playback stops only when:
  - User explicitly pauses via notification or in-app controls
  - `player.release()` is called
  - App is force-stopped

### Notification (`enableNotification = true`)

- Notification appears when playback starts
- Notification updates in real-time with playback state
- Notification shows Play button when paused, Pause button when playing
- Notification automatically dismisses when:
  - User taps the Close button
  - `player.release()` is called
  - App is force-stopped

### Default Behavior (Both Disabled)

When both parameters are `false` (default):
- Video pauses when app is minimized
- Video pauses when app is removed from recent apps
- No notification is shown
- Traditional foreground-only playback

## NotificationMetadata Properties

```kotlin
data class NotificationMetadata(
    val title: String? = null,              // Custom title (overrides API)
    val artist: String? = null,             // Artist/creator name
    val artworkUri: Uri? = null,            // Custom artwork (overrides API)
    val showProgress: Boolean = true        // Show progress bar
)
```

## Best Practices

1. **Request Runtime Permissions**: For Android 13+ (API 33), request `POST_NOTIFICATIONS` permission:
   ```kotlin
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
       if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
           != PackageManager.PERMISSION_GRANTED) {
           requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
       }
   }
   ```

2. **Always Call release()**: Ensure you call `player.release()` in `onDestroy()` to properly clean up resources and stop the service.

3. **Background Playback for Audio**: Background playback is ideal for audio-only content or podcasts. For video, consider if background playback makes sense for your use case.

4. **Test App Removal**: Test your app's behavior when removed from recent apps to ensure it behaves as expected.

## Troubleshooting

### Notification Not Showing
- Ensure `enableNotification = true`
- Check notification permissions (Android 13+)
- Verify the service is declared in AndroidManifest (already included in library)

### Playback Stops in Background
- Ensure `playInBackground = true`
- Check if battery optimization is affecting your app
- Verify the app isn't being killed by the system

### Notification Doesn't Update
- Ensure MediaMetadata is set correctly
- Check logcat for any MediaSession errors

## Migration from Previous Versions

If you're upgrading from a version without these features:

**Before:**
```kotlin
val player = TPStreamsPlayer.create(
    context = applicationContext,
    assetId = "your-asset-id",
    accessToken = "your-access-token"
)
```

**After (with background playback):**
```kotlin
val player = TPStreamsPlayer.create(
    context = applicationContext,
    assetId = "your-asset-id",
    accessToken = "your-access-token",
    playInBackground = true,
    enableNotification = true
)
```

All existing code remains compatible. New parameters are optional with default values that maintain the original behavior.

## API Reference

### TPStreamsPlayer.create() Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `playInBackground` | Boolean | `false` | Enable background playback |
| `enableNotification` | Boolean | `false` | Show media notification |
| `notificationMetadata` | NotificationMetadata? | `null` | Custom notification metadata |

### NotificationMetadata

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `title` | String? | `null` | Custom title (overrides API) |
| `artist` | String? | `null` | Artist/creator name |
| `artworkUri` | Uri? | `null` | Custom artwork URI |
| `showProgress` | Boolean | `true` | Show progress bar |

## License

Same as TPStreams Android Player library.
