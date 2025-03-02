package top.rootu.lampa.receivers

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.os.Build
import top.rootu.lampa.helpers.Helpers.isAndroidTV

@TargetApi(Build.VERSION_CODES.O)
class HomeWatch : BroadcastReceiver() {
    private val TAG = "HomeWatch"

    override fun onReceive(context: Context, intent: Intent) {

        val action = intent.action

        if (action == null || !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isAndroidTV))
            return

        val watchNextId = intent.getLongExtra(TvContract.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L)
        val previewId = intent.getLongExtra(TvContract.EXTRA_PREVIEW_PROGRAM_ID, -1L)

        when (action) {


        }
    }
}