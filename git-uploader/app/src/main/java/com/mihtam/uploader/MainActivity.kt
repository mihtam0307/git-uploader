package com.mihtam.uploader

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val BG = 0xFF0E1120.toInt()
private val SURF = 0xFF171B30.toInt()
private val SURF2 = 0xFF242A52.toInt()
private val TEXT = 0xFFEDEFFF.toInt()
private val MUTED = 0xFF8E95BA.toInt()
private val ACCENT = 0xFF7C8CFF.toInt()
private val ACCENT_DK = 0xFF5F6FE6.toInt()
private val ON_ACCENT = 0xFF0E1120.toInt()
private val DANGER = 0xFFFF7A7A.toInt()
private val GOOD = 0xFF6FE0B0.toInt()

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
    private var loggingIn = false

    private lateinit var status: TextView
    private lateinit var statusBox: LinearLayout
    private lateinit var statusDot: View
    private lateinit var accountLine: TextView
    private lateinit var tabUpload: TextView
    private lateinit var tabVault: TextView
    private lateinit var uploadPanel: LinearLayout
    private lateinit var loginBox: LinearLayout
    private lateinit var mainBox: LinearLayout
    private lateinit var vaultBox: LinearLayout
    private lateinit var vaultList: LinearLayout
    private lateinit var spinner: Spinner
    private lateinit var pathEdit: EditText
    private lateinit var fileName: TextView
    private lateinit var fileInfo: TextView
    private lateinit var progress: ProgressBar
    private lateinit var upBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("app", Context.MODE_PRIVATE)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        buildUi()
        migrateOldToken()
        val active = prefs.getString("active", null)
        token = loadVault().firstOrNull { it.first == active }?.second
        renderVault()
        showTab(0)
        if (token != null) onLoggedIn() else { applyAuthState(); setStatus("Đăng nhập hoặc chọn token trong kho để bắt đầu") }
        checkUpdate()
    }

    // ================= GIAO DIỆN =================
    private fun buildUi() {
        val title = label("Uploader", 30f, TEXT, true)
        accountLine = label("", 14f, MUTED).apply { setPadding(0, dp(2), 0, 0) }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(accountLine)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))
            addView(label("v1.0.${BuildConfig.VERSION_CODE}", 12f, MUTED))
        }

        statusDot = View(this)
        status = label("", 14f, TEXT)
        statusBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rect(SURF, 16)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            addView(statusDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(12) })
            addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        }

        tabUpload = label("Tải lên", 15f, TEXT, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12)); setOnClickListener { showTab(0) }
        }
        tabVault = label("Kho token", 15f, TEXT, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12)); setOnClickListener { showTab(1) }
        }
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rect(SURF, 18)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(tabUpload, LinearLayout.LayoutParams(0, -2, 1f))
            addView(tabVault, LinearLayout.LayoutParams(0, -2, 1f))
        }

        // ---- Tab Tải lên: chưa đăng nhập ----
        val loginBtn = Button(this).apply { text = "Đăng nhập GitHub"; setOnClickListener { login() } }
        styleBtn(loginBtn, 0)
        val toVault = Button(this).apply { text = "Dùng token trong kho"; setOnClickListener { showTab(1) } }
        styleBtn(toVault, 1)
        loginBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(SURF, 22)
            setPadding(dp(22), dp(22), dp(22), dp(22))
            addView(label("Chưa có tài khoản đang dùng", 18f, TEXT, true))
            addView(label("Đăng nhập để nhận mã (đã copy sẵn, chỉ cần dán vào trang GitHub), hoặc chọn một token đã lưu.", 14f, MUTED).apply { setPadding(0, dp(8), 0, dp(20)) })
            addView(loginBtn)
            addView(toVault, lp(top = 10))
        }

        // ---- Tab Tải lên: đã đăng nhập ----
        spinner = Spinner(this).apply { background = null; setPopupBackgroundDrawable(rect(SURF2, 14)) }
        val spinnerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rect(SURF, 18)
            setPadding(dp(10), dp(4), dp(18), dp(4))
            addView(spinner, LinearLayout.LayoutParams(0, -2, 1f))
            addView(label("▾", 16f, MUTED))
            setOnClickListener { spinner.performClick() }
        }

        val plus = label("+", 26f, ACCENT, true).apply {
            gravity = Gravity.CENTER
            background = rect(SURF2, 16)
        }
        fileName = label("Chọn file", 17f, TEXT, true).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.MIDDLE }
        fileInfo = label("Chạm để chọn file từ máy", 13f, MUTED).apply { setPadding(0, dp(2), 0, 0) }
        val fileTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(fileName)
            addView(fileInfo)
        }
        val pickTile = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rect(SURF, 22)
            setPadding(dp(16), dp(16), dp(18), dp(16))
            addView(plus, LinearLayout.LayoutParams(dp(52), dp(52)).apply { rightMargin = dp(16) })
            addView(fileTexts, LinearLayout.LayoutParams(0, -2, 1f))
            setOnClickListener { pickFile() }
        }

        pathEdit = field("vd: abc/file.txt")

        upBtn = Button(this).apply { text = "Upload"; setOnClickListener { upload() } }
        styleBtn(upBtn, 0)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(ACCENT)
            visibility = View.GONE
        }
        val outBtn = Button(this).apply { text = "Đăng xuất (token vẫn được giữ trong kho)"; setOnClickListener { logout() } }
        styleBtn(outBtn, 3)

        mainBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(section("Dự án"))
            addView(spinnerRow)
            addView(section("File"))
            addView(pickTile)
            addView(section("Vị trí trong repo"))
            addView(pathEdit)
            addView(upBtn, lp(top = 22))
            addView(progress, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(12) })
            addView(outBtn, lp(top = 12))
        }

        uploadPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(loginBox)
            addView(mainBox)
        }

        // ---- Tab Kho token ----
        vaultList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val addBtn = Button(this).apply { text = "Thêm token"; setOnClickListener { addTokenDialog() } }
        styleBtn(addBtn, 0)
        val ghLogin = Button(this).apply { text = "Đăng nhập GitHub để lấy token"; setOnClickListener { login() } }
        styleBtn(ghLogin, 1)
        vaultBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Chạm vào một token để dùng. Token được mã hóa bằng Android Keystore và chỉ nằm trên máy này.", 14f, MUTED).apply { setPadding(dp(4), dp(8), dp(4), dp(8)) })
            addView(vaultList)
            addView(addBtn, lp(top = 20))
            addView(ghLogin, lp(top = 10))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
            addView(header)
            addView(statusBox, lp(top = 20))
            addView(tabs, lp(top = 12))
            addView(uploadPanel, lp(top = 8))
            addView(vaultBox, lp(top = 8))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true; addView(content) })
    }

    private fun showTab(i: Int) {
        val up = i == 0
        uploadPanel.visibility = if (up) View.VISIBLE else View.GONE
        vaultBox.visibility = if (up) View.GONE else View.VISIBLE
        tabUpload.background = if (up) rect(ACCENT, 14) else null
        tabVault.background = if (up) null else rect(ACCENT, 14)
        tabUpload.setTextColor(if (up) ON_ACCENT else MUTED)
        tabVault.setTextColor(if (up) MUTED else ON_ACCENT)
    }

    private fun applyAuthState() {
        val on = token != null
        loginBox.visibility = if (on) View.GONE else View.VISIBLE
        mainBox.visibility = if (on) View.VISIBLE else View.GONE
        accountLine.text = if (on) "Đang dùng: ${prefs.getString("active", "")}" else "Chưa đăng nhập"
        accountLine.setTextColor(if (on) ACCENT else MUTED)
    }

    private fun setStatus(s: String) {
        val err = s.startsWith("Lỗi") || s.startsWith("Cần")
        val ok = s.startsWith("Đã upload")
        status.text = s
        status.setTextColor(if (err) DANGER else TEXT)
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (err) DANGER else if (ok) GOOD else MUTED)
        }
        progress.visibility = if (s.startsWith("Đang")) View.VISIBLE else View.GONE
        upBtn.isEnabled = !s.startsWith("Đang upload")
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun lp(w: Int = -1, h: Int = -2, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }

    private fun rect(fill: Int, r: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(r).toFloat()
        if (stroke != null) setStroke(dp(2), stroke)
    }

    private fun label(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun section(t: String) = label(t, 13f, MUTED, true).apply { setPadding(dp(4), dp(20), 0, dp(8)) }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        setTextColor(TEXT)
        setHintTextColor(0xFF5E6590.toInt())
        setSingleLine()
        background = rect(SURF, 18)
        setPadding(dp(18), dp(16), dp(18), dp(16))
    }

    // kind: 0 chính, 1 phụ, 2 nguy hiểm, 3 chữ
    private fun styleBtn(b: Button, kind: Int) {
        b.isAllCaps = false
        b.textSize = 15f
        b.typeface = Typeface.DEFAULT_BOLD
        b.stateListAnimator = null
        b.minHeight = dp(54)
        b.minimumHeight = dp(54)
        val normal = when (kind) { 0 -> rect(ACCENT, 18); 1 -> rect(SURF2, 18); 2 -> rect(DANGER, 18); else -> rect(Color.TRANSPARENT, 18) }
        val pressed = when (kind) { 0 -> rect(ACCENT_DK, 18); 2 -> rect(0xFFE05F5F.toInt(), 18); else -> rect(0xFF2E3566.toInt(), 18) }
        b.background = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), rect(0xFF2A2F55.toInt(), 18))
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
        b.setTextColor(when (kind) { 0, 2 -> ON_ACCENT; 1 -> TEXT; else -> MUTED })
    }

    private fun sheet(vararg views: View): Dialog {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rect(SURF, 26)
            setPadding(dp(22), dp(22), dp(22), dp(18))
            views.forEach { addView(it) }
        }
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), -2)
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return d
    }

    private fun btnRow(a: Button, b: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        addView(b, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun fmtSize(n: Long): String = when {
        n >= 1024 * 1024 -> String.format("%.1f MB", n / 1048576.0)
        n >= 1024 -> String.format("%.0f KB", n / 1024.0)
        else -> "$n B"
    }

    // ================= KHO TOKEN =================
    private fun vaultKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("uploader_vault", null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder("uploader_vault", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return g.generateKey()
    }

    private fun enc(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, vaultKey())
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun dec(s: String): String {
        val b = Base64.decode(s, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, vaultKey(), GCMParameterSpec(128, b, 0, 12))
        return String(c.doFinal(b, 12, b.size - 12))
    }

    private fun loadVault(): MutableList<Pair<String, String>> {
        return try {
            val raw = prefs.getString("vault", null) ?: return mutableListOf()
            val arr = JSONArray(dec(raw))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Pair(o.getString("name"), o.getString("token"))
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun saveVault(list: List<Pair<String, String>>): Boolean {
        return try {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("name", it.first).put("token", it.second)) }
            prefs.edit().putString("vault", enc(arr.toString())).apply()
            true
        } catch (e: Exception) {
            setStatus("Lỗi lưu kho token: ${e.message}")
            false
        }
    }

    private fun addToVault(name: String, tok: String): Boolean {
        val list = loadVault()
        list.removeAll { it.first == name }
        list.add(0, Pair(name, tok))
        return saveVault(list)
    }

    private fun migrateOldToken() {
        val old = prefs.getString("token", null) ?: return
        if (addToVault("Token cũ", old)) {
            prefs.edit().putString("active", "Token cũ").remove("token").apply()
        }
    }

    private fun mask(t: String) = if (t.length > 8) t.take(4) + "••••••" + t.takeLast(4) else "••••••"

    private fun renderVault() {
        vaultList.removeAllViews()
        val list = loadVault()
        val active = prefs.getString("active", null)
        if (list.isEmpty()) {
            vaultList.addView(label("Kho đang trống. Thêm token cá nhân (ghp_...) hoặc đăng nhập GitHub, token sẽ tự lưu vào đây.", 14f, TEXT).apply {
                background = rect(SURF, 18)
                setPadding(dp(18), dp(18), dp(18), dp(18))
            }, lp(top = 10))
            return
        }
        list.forEach { (name, tok) ->
            val on = name == active && token != null
            val dot = View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (on) ACCENT else SURF2) }
            }
            val nameTv = label(name, 16f, TEXT, true)
            val tokTv = label(mask(tok), 13f, MUTED).apply { typeface = Typeface.MONOSPACE; setPadding(0, dp(3), 0, 0) }
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(nameTv)
                addView(tokTv)
            }
            val del = label("Xóa", 14f, DANGER, true).apply {
                setPadding(dp(14), dp(12), dp(6), dp(12))
                setOnClickListener { confirmDelete(name) }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rect(if (on) SURF2 else SURF, 20, if (on) ACCENT else null)
                setPadding(dp(18), dp(14), dp(10), dp(14))
                addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(14) })
                addView(info, LinearLayout.LayoutParams(0, -2, 1f))
                addView(del)
                setOnClickListener { activate(name) }
            }
            vaultList.addView(row, lp(top = 10))
        }
    }

    private fun activate(name: String) {
        val t = loadVault().firstOrNull { it.first == name }?.second ?: return
        token = t
        prefs.edit().putString("active", name).apply()
        renderVault()
        showTab(0)
        onLoggedIn()
    }

    private fun saveAndActivate(name: String, tok: String) {
        if (!addToVault(name, tok)) return
        activate(name)
    }

    private fun confirmDelete(name: String) {
        val ok = Button(this).apply { text = "Xóa" }
        styleBtn(ok, 2)
        val no = Button(this).apply { text = "Giữ lại" }
        styleBtn(no, 1)
        val d = sheet(
            label("Xóa token \"$name\"?", 18f, TEXT, true),
            label("Token sẽ bị xóa khỏi kho trên máy này. Token trên GitHub không bị thu hồi.", 14f, MUTED).apply { setPadding(0, dp(8), 0, dp(20)) },
            btnRow(no, ok)
        )
        ok.setOnClickListener { d.dismiss(); deleteToken(name) }
        no.setOnClickListener { d.dismiss() }
        d.show()
    }

    private fun deleteToken(name: String) {
        val list = loadVault()
        list.removeAll { it.first == name }
        if (!saveVault(list)) return
        if (prefs.getString("active", null) == name) logout("Đã xóa token đang dùng") else {
            renderVault()
            setStatus("Đã xóa token $name")
        }
    }

    private fun addTokenDialog() {
        val nameEt = field("Tên gợi nhớ (để trống = tên tài khoản)")
        val tokEt = field("Token, vd: ghp_xxxx").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val msg = label("", 13f, DANGER).apply { setPadding(dp(4), dp(10), 0, 0); visibility = View.GONE }
        val paste = Button(this).apply { text = "Dán từ clipboard" }
        styleBtn(paste, 3)
        val save = Button(this).apply { text = "Kiểm tra và lưu" }
        styleBtn(save, 0)
        val cancel = Button(this).apply { text = "Hủy" }
        styleBtn(cancel, 1)
        val d = sheet(
            label("Thêm token", 18f, TEXT, true).apply { setPadding(0, 0, 0, dp(14)) },
            nameEt,
            tokEt.also { },
            paste,
            msg,
            btnRow(cancel, save).apply { setPadding(0, dp(12), 0, 0) }
        )
        (tokEt.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(10)
        paste.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (!t.isNullOrEmpty()) tokEt.setText(t)
        }
        cancel.setOnClickListener { d.dismiss() }
        save.setOnClickListener {
            val tok = tokEt.text.toString().trim()
            if (tok.isEmpty()) {
                msg.text = "Hãy nhập token"; msg.visibility = View.VISIBLE
                return@setOnClickListener
            }
            save.isEnabled = false
            msg.setTextColor(MUTED); msg.text = "Đang kiểm tra token..."; msg.visibility = View.VISIBLE
            Thread {
                try {
                    val (c, t) = http("GET", "https://api.github.com/user", token = tok)
                    if (c != 200) throw Exception("Token không hợp lệ (HTTP $c)")
                    val login = JSONObject(t).getString("login")
                    val name = nameEt.text.toString().trim().ifEmpty { login }
                    runOnUiThread { d.dismiss(); saveAndActivate(name, tok) }
                } catch (e: Exception) {
                    runOnUiThread {
                        msg.setTextColor(DANGER); msg.text = "Lỗi: ${e.message}"; save.isEnabled = true
                    }
                }
            }.start()
        }
        d.show()
    }

    // ================= HTTP =================
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

    // ================= ĐĂNG NHẬP (Device Flow) =================
    private fun login() {
        if (loggingIn) return
        loggingIn = true
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
                        val tk = rj.getString("access_token")
                        var nm = "GitHub"
                        try {
                            val (c, u) = http("GET", "https://api.github.com/user", token = tk)
                            if (c == 200) nm = JSONObject(u).getString("login")
                        } catch (e: Exception) { }
                        runOnUiThread { loggingIn = false; saveAndActivate(nm, tk) }
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
                runOnUiThread { loggingIn = false; setStatus("Lỗi đăng nhập: ${e.message}") }
            }
        }.start()
    }

    private fun logout(msg: String = "Đã đăng xuất") {
        prefs.edit().remove("active").apply()
        token = null
        applyAuthState()
        renderVault()
        setStatus(msg)
    }

    private fun onLoggedIn() {
        applyAuthState()
        renderVault()
        setStatus("Đang tải danh sách dự án...")
        val tk = token
        Thread {
            try {
                val (c, t) = http("GET", "https://api.github.com/user/repos?per_page=100&sort=updated", token = tk)
                if (c == 401) { runOnUiThread { logout("Lỗi: token hết hạn hoặc đã bị thu hồi") }; return@Thread }
                if (c !in 200..299) throw Exception("HTTP $c")
                val arr = JSONArray(t)
                repos = (0 until arr.length()).map { arr.getJSONObject(it).getString("full_name") }
                runOnUiThread {
                    spinner.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, repos) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                            (super.getView(position, convertView, parent) as TextView).apply {
                                setTextColor(TEXT); textSize = 15f; setPadding(dp(8), dp(12), dp(8), dp(12))
                            }
                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                            (super.getDropDownView(position, convertView, parent) as TextView).apply {
                                setTextColor(TEXT); textSize = 15f; setPadding(dp(18), dp(14), dp(18), dp(14))
                            }
                    }
                    setStatus("Có ${repos.size} dự án. Chọn file để upload.")
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Lỗi tải dự án: ${e.message}") }
            }
        }.start()
    }

    // ================= CHỌN FILE + UPLOAD =================
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
            var n = "file"
            var sz = ""
            contentResolver.query(uri, null, null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) n = cur.getString(idx)
                    val si = cur.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !cur.isNull(si)) sz = fmtSize(cur.getLong(si))
                }
            }
            pathEdit.setText(n)
            fileName.text = n
            fileInfo.text = if (sz.isEmpty()) "Chạm để đổi file" else "$sz, chạm để đổi file"
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

    // ================= TỰ CẬP NHẬT TỪ GITHUB RELEASES =================
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
