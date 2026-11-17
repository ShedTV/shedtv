package com.matchday.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** Receives the result of an update install and reports it to the Updater. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Fire TV wants the user to confirm: show its install prompt.
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                        .onFailure { Updater.report("Couldn't show the install prompt (${it.message}).") }
                    Updater.report("Confirm the install on the prompt. ShedTV will close and reopen on the new build.")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.report("Updated. Reopen ShedTV if it didn't restart by itself.")
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.report("Install cancelled. Press Install to try again.")
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                Updater.report("Not enough free space on the Fire Stick to install the update. Clear some space and try again.")
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                Updater.report("The update clashes with the installed app ($detail). Uninstall ShedTV, then install it again with Downloader.")
            PackageInstaller.STATUS_FAILURE_BLOCKED ->
                Updater.report("Fire TV blocked the install ($detail). Check Install unknown apps is on for ShedTV.")
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> Updater.report("This update isn't compatible with this device ($detail).")
            else -> Updater.report("Install failed" + if (detail.isNotBlank()) " ($detail)." else ".")
        }
    }
}
