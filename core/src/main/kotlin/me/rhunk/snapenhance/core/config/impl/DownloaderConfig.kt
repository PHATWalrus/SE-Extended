package me.rhunk.snapenhance.core.config.impl

import me.rhunk.snapenhance.core.config.ConfigContainer

class DownloaderConfig : ConfigContainer() {
    val saveFolder = string("save_folder") { isFolder = true }
    val autoDownloadOptions = multiple("auto_download_options",
        "friend_snaps",
        "friend_stories",
        "public_stories",
        "spotlight"
    )
    val pathFormat = multiple("path_format",
        "create_user_folder",
        "append_hash",
        "append_date_time",
        "append_type",
        "append_username"
    )
    val allowDuplicate = boolean("allow_duplicate")
    val mergeOverlays = boolean("merge_overlays")
    val chatDownloadContextMenu = boolean("chat_download_context_menu")
    val logging = multiple("logging", "started", "success", "progress", "failure")
}