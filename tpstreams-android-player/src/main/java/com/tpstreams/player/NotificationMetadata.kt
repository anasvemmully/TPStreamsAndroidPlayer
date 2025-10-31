package com.tpstreams.player

import android.net.Uri

/**
 * Metadata configuration for media playback notifications.
 *
 * @property title The title to display in the notification (overrides API-fetched title if provided)
 * @property artist The artist/creator name to display in the notification
 * @property artworkUri Custom artwork URI for the notification (overrides API-fetched thumbnail if provided)
 * @property showProgress Whether to show playback progress in the notification (default: true)
 */
data class NotificationMetadata(
    val title: String? = null,
    val artist: String? = null,
    val artworkUri: Uri? = null,
    val showProgress: Boolean = true
)
