package com.mihtam.uploader

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    companion object {
        // 1) Tạo OAuth App trên GitHub (bật Device Flow) rồi dán Client ID vào đây
        const val CLIENT_ID = "Ov23liOEdNT6lu1DNGUT"
        // 2) Repo chứa code app này (nơi có Releases)
        const val REPO = "mihtam0307/git-uploader"
        const val PICK = 1
    }

    private lateinit var prefs: SharedPreferences
    private var token: String? = null
    private var repos: List<String> = emptyList()
    private var fileUri: Uri? = null

    private lateinit var status: TextView
    private lateinit var loginBtn: Button
    private lateinit var mainBox: LinearLayout
    private lateinit var spinner: Spinner
    private lateinit var pathEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("app", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        status = TextView(this).apply { textSize = 16f; text = "Phiên bản 1.0.${BuildConfig.VERSION_CODE}" }
        loginBtn = Button(this).apply { text = "Đăng nhập GitHub"; setOnClickListener { login() } }

        mainBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        spinner = Spinner(this)
        val pickBtn = Button(this).apply { text = "Chọn file"; setOnClickListener { pickFile() } }
        pathEdit = EditText(this).apply { hint = "Đường dẫn trong repo (vd: abc/file.txt)" }
        val upBtn = Button(this).apply { text = "Upload"; setOnClickListener { upload() } }
        val outBtn = Button(this).apply { text = "Đăng xuất"; setOnClickListener { logout() } }
        mainBox.addView(TextView(this).apply { text = "Chọn dự án:" })
        mainBox.addView(spinner)
        mainBox.addView(pickBtn)
        mainBox.addView(pathEdit)
        mainBox.addView(upBtn)
        mainBox.addView(outBtn)

        root.addView(status)
        root.addView(loginBtn)
        root.addView(mainBox)
        setContentView(ScrollView(this).apply { addView(root) })

        token = prefs.getString("token", null)
        if (token != null) onLoggedIn()
        checkUpdate()
    }

    private fun setStatus(s: String) { status.text = s }

    private fun http(method: String, url: String, body: String? = null, token: String? = null, form: Boolean = false): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "uploader-app")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", if (form) "application/x-www-form-urlencoded" else "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return Pair(code, text)
    }

    // ---------- Đăng nhập (Device Flow, không cần server) ----------
    private fun login() {
        loginBtn.isEnabled = false
        Thread {
            try {
                val (_, t) = http("POST", "https://github.com/login/device/code", "client_id=$CLIENT_ID&scope=repo", form = true)
                val j = JSONObject(t)
                val dev = j.getString("device_code")
                val user = j.getString("user_code")
                val uri = j.getString("verification_uri")
                var interval = j.optInt("interval", 5)
                val deadline = System.currentTimeMillis() + j.optInt("expires_in", 900) * 1000L
                runOnUiThread {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("code", user))
                    setStatus("Nhập mã $user tại GitHub (đã copy sẵn)")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                }
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(interval * 1000L)
                    val (_, r) = http(
                        "POST", "https://github.com/login/oauth/access_token",
                        "client_id=$CLIENT_ID&device_code=$dev&grant_type=urn:ietf:params:oauth:grant-type:device_code",
                        form = true
                    )
                    val rj = JSONObject(r)
                    if (rj.has("access_token")) {
                        token = rj.getString("access_token")
                        prefs.edit().putString("token", token).apply()
                        runOnUiThread { onLoggedIn() }
                        return@Thread
                    }
                    when (rj.optString("error")) {
                        "slow_down" -> interval += 5
                        "authorization_pending" -> {}
                        else -> throw Exception(rj.optString("error"))
                    }
                }
                throw Exception("Mã đã hết hạn")
            } catch (e: Exception) {
                runOnUiThread { setStatus("Lỗi đăng nhập: ${e.message}"); loginBtn.isEnabled = true }
            }
        }.start()
    }

    private fun logout() {
        prefs.edit().remove("token").apply()
        token = null
        mainBox.visibility = View.GONE
        loginBtn.visibility = View.VISIBLE
        loginBtn.isEnabled = true
        setStatus("Đã đăng xuất")
    }

    private fun onLoggedIn() {
        loginBtn.visibility = View.GONE
        mainBox.visibility = View.VISIBLE
        setStatus("Đang tải danh sách dự án...")
        Thread {
            try {
                val (c, t) = http("GET", "https://api.github.com/user/repos?per_page=100&sort=updated", token = token)
                if (c == 401) { runOnUiThread { logout() }; return@Thread }
                if (c !in 200..299) throw Exception("HTTP $c")
                val arr = JSONArray(t)
                repos = (0 until arr.length()).map { arr.getJSONObject(it).getString("full_name") }
                runOnUiThread {
                    spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, repos)
                    setStatus("Có ${repos.size} dự án. Chọn file để upload.")
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Lỗi tải dự án: ${e.message}") }
            }
        }.start()
    }

    // ---------- Chọn file + Upload ----------
    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            fileUri = uri
            contentResolver.query(uri, null, null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) pathEdit.setText(cur.getString(idx))
                }
            }
            setStatus("Đã chọn file. Bấm Upload.")
        }
    }

    private fun upload() {
        val uri = fileUri
        val repo = repos.getOrNull(spinner.selectedItemPosition)
        val path = pathEdit.text.toString().trim().trimStart('/')
        if (uri == null || repo == null || path.isEmpty()) {
            setStatus("Cần chọn dự án, file và đường dẫn")
            return
        }
        setStatus("Đang upload...")
        Thread {
            try {
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                if (bytes.size > 50 * 1024 * 1024) throw Exception("File lớn hơn 50MB")
                val api = "https://api.github.com/repos/$repo/contents/" +
                    path.split('/').joinToString("/") { Uri.encode(it) }
                val (gc, gt) = http("GET", api, token = token)
                val body = JSONObject()
                    .put("message", "Upload $path")
                    .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                if (gc == 200) body.put("sha", JSONObject(gt).getString("sha"))
                val (pc, pt) = http("PUT", api, body.toString(), token)
                if (pc !in 200..299) throw Exception("HTTP $pc: " + JSONObject(pt).optString("message"))
                runOnUiThread { setStatus("Đã upload $path lên $repo") }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Lỗi upload: ${e.message}") }
            }
        }.start()
    }

    // ---------- Tự cập nhật từ GitHub Releases ----------
    private fun checkUpdate() {
        Thread {
            try {
                val (c, t) = http("GET", "https://api.github.com/repos/$REPO/releases/latest")
                if (c != 200) return@Thread
                val j = JSONObject(t)
                val latest = j.getString("tag_name").substringAfterLast('.').toIntOrNull() ?: return@Thread
                if (latest <= BuildConfig.VERSION_CODE) return@Thread
                val assets = j.getJSONArray("assets")
                var found: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) found = a.getString("browser_download_url")
                }
                val url = found ?: return@Thread
                runOnUiThread { setStatus("Có bản mới 1.0.$latest, đang tải...") }
                val f = File(File(cacheDir, "update").apply { mkdirs() }, "update.apk")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "uploader-app")
                conn.instanceFollowRedirects = true
                conn.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                runOnUiThread { install(f) }
            } catch (e: Exception) {
                // bỏ qua lỗi kiểm tra cập nhật (mất mạng...)
            }
        }.start()
    }

    private fun install(f: File) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            setStatus("Hãy bật 'Cho phép cài ứng dụng' cho Uploader, rồi mở lại app")
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }
}
