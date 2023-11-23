package top.rootu.lampa

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import top.rootu.lampa.browser.Browser
import top.rootu.lampa.channels.LampaChannels
import top.rootu.lampa.channels.LampaChannels.updateBookChannel
import top.rootu.lampa.channels.LampaChannels.updateHistChannel
import top.rootu.lampa.channels.LampaChannels.updateLikeChannel
import top.rootu.lampa.channels.WatchNext
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.helpers.Helpers.isValidJson
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.favorite
import top.rootu.lampa.helpers.Prefs.recs
import top.rootu.lampa.helpers.Prefs.saveFavorite
import top.rootu.lampa.helpers.Prefs.saveRecs
import top.rootu.lampa.helpers.Prefs.setTmdbApiUrl
import top.rootu.lampa.helpers.Prefs.setTmdbImgUrl
import top.rootu.lampa.helpers.Prefs.tmdbApiUrl
import top.rootu.lampa.helpers.Prefs.tmdbImgUrl
import top.rootu.lampa.models.Favorite
import top.rootu.lampa.models.LampaRec
import top.rootu.lampa.models.getEntity
import top.rootu.lampa.net.Http
import kotlin.system.exitProcess

class AndroidJS(private val mainActivity: MainActivity, private val browser: Browser) {

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun StorageChange(str: String) {
        val eo: JSONObject = if (str == "\"\"") {
            JSONObject()
        } else {
            JSONObject(str)
        }
        if (!eo.has("name") || !eo.has("value")) return

        when (eo.optString("name")) {
            "player_timecode" -> {
                MainActivity.playerTimeCode = eo.optString("value", MainActivity.playerTimeCode)
            }

            "file_view" -> {
                MainActivity.playerFileView = eo.optJSONObject("value")
            }

            "playlist_next" -> {
                MainActivity.playerAutoNext = eo.optString("value", "true") == "true"
            }

            "torrserver_preload" -> {
                MainActivity.torrserverPreload = eo.optString("value", "false") == "true"
            }

            "internal_torrclient" -> {
                MainActivity.internalTorrserve = eo.optString("value", "false") == "true"
            }

            "language" -> {
                mainActivity.setLang(eo.optString("value", "ru"))
            }

            "proxy_tmdb", "protocol" -> {
                mainActivity.changeTmdbUrls()
            }

            "baseUrlApiTMDB" -> {
                mainActivity.setTmdbApiUrl(eo.optString("value", mainActivity.tmdbApiUrl))
                if (BuildConfig.DEBUG) Log.d(
                    "*****",
                    "baseUrlApiTMDB set to ${mainActivity.tmdbApiUrl}"
                )
            }

            "baseUrlImageTMDB" -> {
                mainActivity.setTmdbImgUrl(eo.optString("value", mainActivity.tmdbImgUrl))
                if (BuildConfig.DEBUG) Log.d(
                    "*****",
                    "baseUrlImageTMDB set to ${mainActivity.tmdbImgUrl}"
                )
            }

            "favorite" -> {
                if (BuildConfig.DEBUG) Log.d("*****", "favorite json changed")
                val json = eo.optString("value", "")
                if (isValidJson(json)) {
                    App.context.saveFavorite(json)
                    if (BuildConfig.DEBUG) Log.d("*****", "favorite json stored")
                }
            }

            "recomends_list" -> {
                if (BuildConfig.DEBUG) Log.d("*****", "recomends json changed")
                val json = eo.optString("value", "")
                if (isValidJson(json)) {
                    App.context.saveRecs(json)
                    if (BuildConfig.DEBUG) Log.d("*****", "recomends json stored")
                }
            }
        }
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun appVersion(): String {
        return BuildConfig.VERSION_NAME + "-" + BuildConfig.VERSION_CODE
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun exit() {
        try {
            mainActivity.runOnUiThread { mainActivity.appExit() }
        } catch (unused: Exception) {
            exitProcess(1)
        }
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    @Throws(JSONException::class)
    fun openTorrentLink(str: String, str2: String): Boolean {
        val jSONObject: JSONObject = if (str2 == "\"\"") {
            JSONObject()
        } else {
            JSONObject(str2)
        }
        val intent = Intent("android.intent.action.VIEW")
        val parse = Uri.parse(str)
        if (str.startsWith("magnet")) {
            intent.data = parse
        } else {
            intent.setDataAndType(parse, "application/x-bittorrent")
        }
        val title = jSONObject.optString("title")
        if (!TextUtils.isEmpty(title)) {
            intent.putExtra("title", title)
            intent.putExtra("displayName", title)
            intent.putExtra("forcename", title)
        }
        val poster = jSONObject.optString("poster")
        if (!TextUtils.isEmpty(poster)) {
            intent.putExtra("poster", poster)
        }
        if (jSONObject.optJSONObject("data") != null) {
            val optJSONObject = jSONObject.optJSONObject("data")
            if (optJSONObject != null) {
                intent.putExtra("data", optJSONObject.toString())
            }
        }
        mainActivity.runOnUiThread {
            try {
                mainActivity.startActivity(intent)
            } catch (e: Exception) {
                Log.d(TAG, e.message, e)
                App.toast(R.string.no_activity_found, false)
            }
        }
        // update Recs to filter viewed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(5000)
                LampaChannels.updateRecsChannel()
            }
        }
        return true
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun openYoutube(str: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/watch?v=$str")
        )
        mainActivity.runOnUiThread {
            try {
                mainActivity.startActivity(intent)
            } catch (e: Exception) {
                Log.d(TAG, e.message, e)
                App.toast(R.string.no_activity_found, false)
            }
        }
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun clearDefaultPlayer() {
        mainActivity.runOnUiThread {
            mainActivity.setPlayerPackage("", false)
            mainActivity.setPlayerPackage("", true)
            App.toast(R.string.select_player_reset)
        }
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun httpReq(str: String?, returnI: Int) {
        Log.d("JS", str!!)
        val jSONObject: JSONObject?
        try {
            jSONObject = JSONObject(str)
            val url = jSONObject.optString("url")
            val data = jSONObject.opt("post_data")
            var headers = jSONObject.optJSONObject("headers")
            var contentType = jSONObject.optString("contentType")
            val timeout = jSONObject.optInt("timeout", 15000)
            var requestContent = ""
            if (data != null) {
                if (data is String) {
                    requestContent = data.toString()
                    contentType = try {
                        JSONObject(requestContent)
                        "application/json"
                    } catch (e: JSONException) {
                        "application/x-www-form-urlencoded"
                    }
                } else if (data is JSONObject) {
                    contentType = "application/json"
                    requestContent = data.toString()
                }
            }
            if (requestContent.isNotEmpty()) {
                if (headers == null) {
                    headers = JSONObject()
                    headers.put("Content-Type", contentType)
                } else if (!headers.has("Content-Type")) {
                    if (headers.has("Content-type")) {
                        contentType = headers.optString("Content-type", contentType)
                        headers.remove("Content-type")
                    }
                    if (headers.has("content-type")) {
                        contentType = headers.optString("content-type", contentType)
                        headers.remove("content-type")
                    }
                    headers.put("Content-Type", contentType)
                }
            }
            val finalRequestContent = requestContent
            val finalHeaders = headers

            class LampaAsyncTask : AsyncTask<Void?, String?, String>("LampaAsyncTask") {
                override fun doInBackground(vararg params: Void?): String {
                    var s: String
                    var action = "complite"
                    val json: JSONObject?
                    val http = Http()
                    try {
                        s = if (TextUtils.isEmpty(finalRequestContent)) {
                            // GET
                            http.Get(url, finalHeaders, timeout)
                        } else {
                            // POST
                            http.Post(url, finalRequestContent, finalHeaders, timeout)
                        }
                    } catch (e: Exception) {
                        json = JSONObject()
                        try {
                            json.put("status", http.lastErrorCode)
                            json.put("message", "request error: " + e.message)
                        } catch (jsonException: JSONException) {
                            jsonException.printStackTrace()
                        }
                        s = json.toString()
                        action = "error"
                        e.printStackTrace()
                    }
                    reqResponse[returnI.toString()] = s
                    return action
                }

                override fun onPostExecute(result: String?) {
                    mainActivity.runOnUiThread {
                        val js = ("Lampa.Android.httpCall("
                                + returnI.toString() + ", '"
                                + result.toString()
                            .replace("\\", "\\\\")
                            .replace("'", "\\'")
                            .replace("\n", "\\\n")
                                + "')")
                        browser.evaluateJavascript(js) { value -> Log.i("JSRV", value) }
                    }
                }
            }

            LampaAsyncTask().execute()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun getResp(str: String): String? {
        var string: String? = ""
        if (reqResponse.containsKey(str)) {
            string = reqResponse[str]
            reqResponse.remove(str)
        }
        return string
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun openPlayer(link: String, jsonStr: String) {
        Log.d(TAG, "openPlayer: $link json:$jsonStr")
        val jsonObject: JSONObject = try {
            JSONObject(jsonStr.ifEmpty { "{}" })
        } catch (e: Exception) {
            JSONObject()
        }
        if (!jsonObject.has("url")) {
            try {
                jsonObject.put("url", link)
            } catch (ignored: JSONException) {
            }
        }
        mainActivity.runOnUiThread { mainActivity.runPlayer(jsonObject) }
        // update Recs to filter viewed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(5000)
                LampaChannels.updateRecsChannel()
            }
        }
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun setProxyPAC(link: String): Boolean {
        return Http.setProxyPAC(link)
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun getProxyPAC(): String {
        return Http.getProxyPAC()
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun voiceStart() {
        // Голосовой ввод с последующей передачей результата через JS
        mainActivity.runOnUiThread {
            try {
                mainActivity.displaySpeechRecognizer()
            } catch (e: Exception) {
                e.printStackTrace()
                // Очищаем поле ввода
                mainActivity.runVoidJsFunc("window.voiceResult", "''")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun updateChannel(where: String?) {
        // https://github.com/yumata/lampa-source/blob/e5505b0e9cf5f95f8ec49bddbbb04086fccf26c8/src/app.js#L203
        if (where != null) {
            Log.d(TAG, "***** updateChannel $where")
            when (where) {
                LampaProvider.Hist -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(5000)
                        updateHistChannel()
                    }
                }

                LampaProvider.Book -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(5000)
                        updateBookChannel()
                    }
                }

                LampaProvider.Like -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(5000)
                        updateLikeChannel()
                    }
                }

                "wath" -> {
                    // Handle add to Watch Later in Lampa
                    // TODO: find a better way to manage WatchNext
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(5000)
                        val wathCards =
                            App.context.FAV?.card?.filter { App.context.FAV?.wath?.contains(it.id) == true }
                        wathCards?.forEach {
                            val tmdbID = it.toTmdbID()
                            val ent = tmdbID.getEntity()
                            ent?.let {
                                withContext(Dispatchers.Main) {
                                    try {
                                        if (BuildConfig.DEBUG) Log.d("*****", "Add ${tmdbID.id} to WatchNext")
                                        WatchNext.add(it)
                                    } catch (e: Exception) {
                                        if (BuildConfig.DEBUG) Log.d("*****", "Error add ${tmdbID.id} to WatchNext: $e")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AndroidJS"
        var reqResponse: MutableMap<String, String> = HashMap()
    }
}