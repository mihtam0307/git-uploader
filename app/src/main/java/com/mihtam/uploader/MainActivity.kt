package com.mihtam.uploader

import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class MainActivity : Activity() {

    companion object {
        // 1) Client ID của OAuth App (đã bật Device Flow)
        const val CLIENT_ID = "Ov23liOEdNT6lu1DNGUT"
        // 2) Repo chứa code app này (nơi có Releases)
        const val REPO = "mihtam0307/git-uploader"
        const val PICK = 1
    }

    // ----- màu -----
    private val BG = Color.parseColor("#0D1117")
    private val CARD = Color.parseColor("#161B22")
    private val CARD2 = Color.parseColor("#21262D")
    private val STROKE = Color.parseColor("#30363D")
    private val TEXT = Color.parseColor("#E6EDF3")
    private val DIM = Color.parseColor("#8B949E")
    private val PURPLE = Color.parseColor("#7C3AED")
    private val BLUE = Color.parseColor("#2563EB")
    private val GREEN = Color.parseColor("#3FB950")
    private val RED = Color.parseColor("#F85149")

    private lateinit var prefs: SharedPreferences
    private var token: String? = null
    private var repos: List<String> = emptyList()
    private var fileUri: Uri? = null

    private lateinit var content: LinearLayout
    private lateinit var logo: FrameLayout
    private lateinit var banner: TextView
    private lateinit var status: TextView
    private lateinit var loginCard: LinearLayout
    private lateinit var loginBtn: TextView
    private lateinit var codeView: TextView
    private lateinit var mainCard: LinearLayout
    private lateinit var spinner: Spinner
    private lateinit var pickBtn: TextView
    private lateinit var pathEdit: EditText
    private lateinit var zipSwitch: Switch
    private var fileName: String = ""
    private lateinit var uploadBtn: TextView
    private lateinit var progress: ProgressBar
    private var floatAnim: ObjectAnimator? = null

    // ===================== UI helpers =====================
    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun rounded(color: Int, r: Int, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun gradient(r: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(PURPLE, BLUE)).apply {
            cornerRadius = dp(r).toFloat()
        }

    private fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }

    private fun label(t: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun smooth(): LayoutTransition = LayoutTransition().apply {
        disableTransitionType(LayoutTransition.APPEARING)
        disableTransitionType(LayoutTransition.DISAPPEARING)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, 20, STROKE)
        setPadding(dp(18), dp(18), dp(18), dp(18))
        layoutTransition = smooth()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun button(t: String, primary: Boolean, onClick: () -> Unit): TextView = TextView(this).apply {
        text = t
        textSize = 16f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) Color.WHITE else TEXT)
        background = if (primary) gradient(14) else rounded(CARD2, 14, STROKE)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(OvershootInterpolator(3f)).start()
            }
            false
        }
        setOnClickListener { onClick() }
    }

    private fun setEnabledAnim(v: View, on: Boolean) {
        v.isEnabled = on
        v.animate().alpha(if (on) 1f else 0.5f).setDuration(200).start()
    }

    // ===================== animation helpers =====================
    private fun enter(views: List<View>) {
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = dp(28).toFloat()
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(110L * i).setDuration(500)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun swap(out: View, inn: View) {
        out.animate().setStartDelay(0).alpha(0f).translationY(-dp(12).toFloat()).setDuration(220).withEndAction {
            out.visibility = View.GONE
            out.alpha = 1f
            out.translationY = 0f
            inn.alpha = 0f
            inn.translationY = dp(24).toFloat()
            inn.visibility = View.VISIBLE
            inn.animate().setStartDelay(0).alpha(1f).translationY(0f).setDuration(400)
                .setInterpolator(DecelerateInterpolator()).start()
        }.start()
    }

    private fun pop(v: View, to: Float = 1.15f) {
        v.animate().scaleX(to).scaleY(to).setDuration(130).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(380)
                .setInterpolator(OvershootInterpolator(4f)).start()
        }.start()
    }

    private fun shake(v: View) {
        ObjectAnimator.ofFloat(v, "translationX", 0f, -dp(10).toFloat(), dp(10).toFloat(), -dp(6).toFloat(), dp(6).toFloat(), 0f)
            .setDuration(380).start()
    }

    // kind: 0 = bình thường, 1 = thành công, 2 = lỗi
    private fun setStatus(s: String, kind: Int = 0) {
        status.animate().setStartDelay(0).alpha(0f).translationY(-dp(6).toFloat()).setDuration(110).withEndAction {
            status.text = s
            status.setTextColor(
                when (kind) {
                    1 -> GREEN
                    2 -> RED
                    else -> DIM
                }
            )
            status.translationY = dp(6).toFloat()
            status.animate().alpha(1f).translationY(0f).setDuration(220).start()
        }.start()
    }

    private fun showBanner(t: String) {
        banner.text = t
        if (banner.visibility != View.VISIBLE) {
            banner.alpha = 0f
            banner.translationY = -dp(20).toFloat()
            banner.visibility = View.VISIBLE
            banner.animate().setStartDelay(0).alpha(1f).translationY(0f).setDuration(400)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun showCode() {
        codeView.visibility = View.VISIBLE
        codeView.scaleX = 0.6f
        codeView.scaleY = 0.6f
        codeView.alpha = 0f
        codeView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(450)
            .setInterpolator(OvershootInterpolator(2f)).start()
    }

    private fun copyText(s: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("code", s))
    }

    // ===================== onCreate =====================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("app", Context.MODE_PRIVATE)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(32))
            layoutTransition = smooth()
        }

        // ----- header -----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        logo = FrameLayout(this).apply { background = gradient(18) }
        val logoImg = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleX = 1.5f
            scaleY = 1.5f
        }
        logo.addView(logoImg, FrameLayout.LayoutParams(dp(60), dp(60)))
        header.addView(logo, LinearLayout.LayoutParams(dp(60), dp(60)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
        }
        titles.addView(label("Uploader", 26f, TEXT, true))
        titles.addView(label("Phiên bản 1.0.${BuildConfig.VERSION_CODE}", 13f, DIM))
        header.addView(titles)
        content.addView(header)

        floatAnim = ObjectAnimator.ofFloat(logo, "translationY", 0f, -dp(5).toFloat()).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // ----- banner cập nhật -----
        banner = label("", 14f, Color.WHITE, true).apply {
            background = gradient(14)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            visibility = View.GONE
        }
        content.addView(banner, lp(top = 20))

        // ----- trạng thái -----
        val statusCard = card()
        status = label("Sẵn sàng", 15f, DIM)
        statusCard.addView(status)
        content.addView(statusCard, lp(top = 20))

        // ----- thẻ đăng nhập -----
        loginCard = card()
        loginCard.addView(label("Bắt đầu", 20f, TEXT, true))
        loginCard.addView(
            label("Đăng nhập GitHub để chọn dự án và upload file.", 14f, DIM)
                .apply { setPadding(0, dp(6), 0, dp(16)) }
        )
        loginBtn = button("Đăng nhập GitHub", true) { login() }
        loginCard.addView(loginBtn, lp())
        codeView = label("", 30f, TEXT, true).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.15f
            visibility = View.GONE
            setPadding(0, dp(18), 0, dp(4))
            setOnClickListener { copyText(text.toString()) }
        }
        loginCard.addView(codeView, lp())
        content.addView(loginCard, lp(top = 16))

        // ----- thẻ chính -----
        mainCard = card().apply { visibility = View.GONE }
        mainCard.addView(label("Dự án", 13f, DIM))
        val spinWrap = FrameLayout(this).apply { background = rounded(CARD2, 12, STROKE) }
        spinner = Spinner(this).apply { background = null }
        spinWrap.addView(spinner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        val arrow = label("▾", 18f, DIM)
        spinWrap.addView(
            arrow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply { marginEnd = dp(14) }
        )
        mainCard.addView(spinWrap, lp(top = 8))

        mainCard.addView(label("File", 13f, DIM), lp(top = 16))
        pickBtn = button("📎  Chọn file", false) { pickFile() }
        mainCard.addView(pickBtn, lp(top = 8))

        zipSwitch = Switch(this).apply {
            text = "Giải nén rồi upload"
            setTextColor(TEXT)
            textSize = 15f
            isChecked = true
            visibility = View.GONE
            setOnCheckedChangeListener { _, on -> applyZipMode(on) }
        }
        mainCard.addView(zipSwitch, lp(top = 12))

        pathEdit = EditText(this).apply {
            hint = "Đường dẫn trong repo (vd: abc/file.txt)"
            setHintTextColor(DIM)
            setTextColor(TEXT)
            textSize = 15f
            isSingleLine = true
            background = rounded(CARD2, 12, STROKE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        mainCard.addView(pathEdit, lp(top = 10))

        uploadBtn = button("Upload", true) { upload() }
        mainCard.addView(uploadBtn, lp(top = 16))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
            indeterminateTintList = ColorStateList.valueOf(PURPLE)
        }
        mainCard.addView(progress, lp(top = 12, h = dp(8)))

        val outBtn = label("Đăng xuất", 14f, DIM).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            setOnClickListener { logout() }
        }
        mainCard.addView(outBtn, lp())
        content.addView(mainCard, lp(top = 16))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            addView(content)
        })

        token = prefs.getString("token", null)
        if (token != null) {
            loginCard.visibility = View.GONE
            mainCard.visibility = View.VISIBLE
            enter(listOf(header, statusCard, mainCard))
            setStatus("Đang tải danh sách dự án...")
            fetchRepos()
        } else {
            enter(listOf(header, statusCard, loginCard))
        }
        checkUpdate()
    }

    override fun onDestroy() {
        floatAnim?.cancel()
        super.onDestroy()
    }

    // ===================== network =====================
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

    // ===================== đăng nhập (Device Flow) =====================
    private fun login() {
        setEnabledAnim(loginBtn, false)
        loginBtn.text = "Đang kết nối..."
        Thread {
            try {
                val (_, t) = http("POST", "https://github.com/login/device/code", "client_id=$CLIENT_ID&scope=repo%20workflow", form = true)
                val j = JSONObject(t)
                val dev = j.getString("device_code")
                val user = j.getString("user_code")
                val uri = j.getString("verification_uri")
                var interval = j.optInt("interval", 5)
                val deadline = System.currentTimeMillis() + j.optInt("expires_in", 900) * 1000L
                runOnUiThread {
                    copyText(user)
                    codeView.text = user
                    showCode()
                    loginBtn.text = "Đang chờ bạn xác nhận..."
                    setStatus("Nhập mã trên trang GitHub (đã copy sẵn mã)")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                }
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(interval * 1000L)
                    val r = try {
                        http(
                            "POST", "https://github.com/login/oauth/access_token",
                            "client_id=$CLIENT_ID&device_code=$dev&grant_type=urn:ietf:params:oauth:grant-type:device_code",
                            form = true
                        ).second
                    } catch (e: java.io.IOException) {
                        continue // mạng rớt tạm thời → thử lại ở lần poll sau
                    }
                    val rj = JSONObject(r)
                    if (rj.has("access_token")) {
                        token = rj.getString("access_token")
                        prefs.edit().putString("token", token).apply()
                        runOnUiThread {
                            pop(logo, 1.25f)
                            swap(loginCard, mainCard)
                            setStatus("Đăng nhập thành công, đang tải dự án...", 1)
                        }
                        fetchRepos()
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
                runOnUiThread {
                    setStatus("Lỗi đăng nhập: ${e.message}", 2)
                    shake(loginCard)
                    loginBtn.text = "Đăng nhập GitHub"
                    setEnabledAnim(loginBtn, true)
                }
            }
        }.start()
    }

    private fun logout() {
        prefs.edit().remove("token").apply()
        token = null
        codeView.visibility = View.GONE
        loginBtn.text = "Đăng nhập GitHub"
        setEnabledAnim(loginBtn, true)
        swap(mainCard, loginCard)
        setStatus("Đã đăng xuất")
    }

    private fun fetchRepos() {
        Thread {
            try {
                val (c, t) = http("GET", "https://api.github.com/user/repos?per_page=100&sort=updated", token = token)
                if (c == 401) {
                    runOnUiThread { logout() }
                    return@Thread
                }
                if (c !in 200..299) throw Exception("HTTP $c")
                val arr = JSONArray(t)
                repos = (0 until arr.length()).map { arr.getJSONObject(it).getString("full_name") }
                runOnUiThread {
                    spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, repos)
                    setStatus("Có ${repos.size} dự án. Chọn file để upload.", 1)
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Lỗi tải dự án: ${e.message}", 2) }
            }
        }.start()
    }

    // ===================== chọn file + upload =====================
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
            var name = ""
            contentResolver.query(uri, null, null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cur.getString(idx)
                }
            }
            fileName = name
            if (name.endsWith(".zip", true)) {
                zipSwitch.visibility = View.VISIBLE
                applyZipMode(zipSwitch.isChecked)
            } else {
                zipSwitch.visibility = View.GONE
                applyZipMode(false)
            }
            pickBtn.text = "📎  " + (if (name.isNotEmpty()) name else "Đã chọn file")
            pop(pickBtn, 1.04f)
            setStatus("Đã chọn file. Bấm Upload.")
        }
    }

    private fun setBusy(b: Boolean) {
        progress.visibility = if (b) View.VISIBLE else View.GONE
        uploadBtn.text = if (b) "Đang upload..." else "Upload"
        setEnabledAnim(uploadBtn, !b)
    }

    private fun upload() {
        val uri = fileUri
        val repo = repos.getOrNull(spinner.selectedItemPosition)
        val path = pathEdit.text.toString().trim().trimStart('/')
        val zipMode = fileName.endsWith(".zip", true) && zipSwitch.isChecked
        if (uri == null || repo == null || (!zipMode && path.isEmpty())) {
            setStatus("Cần chọn dự án và file", 2)
            shake(mainCard)
            return
        }
        if (zipMode) {
            uploadZip(uri, repo, path.trim('/'))
            return
        }
        setBusy(true)
        setStatus("Đang upload $path ...")
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
                runOnUiThread {
                    setBusy(false)
                    setStatus("Đã upload $path lên $repo ✅", 1)
                    pop(logo, 1.25f)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false)
                    setStatus("Lỗi upload: ${e.message}", 2)
                    shake(mainCard)
                }
            }
        }.start()
    }

    private fun applyZipMode(on: Boolean) {
        if (on) {
            pathEdit.hint = "Thư mục đích (để trống = gốc repo)"
            pathEdit.setText("")
        } else {
            pathEdit.hint = "Đường dẫn trong repo (vd: abc/file.txt)"
            pathEdit.setText(fileName)
        }
    }

    private fun ghErr(c: Int, t: String): String {
        val m = try { JSONObject(t).optString("message") } catch (e: Exception) { t.take(100) }
        val hint = if (m.contains("workflow", true)) " (hãy Đăng xuất rồi đăng nhập lại để cấp quyền workflow)" else ""
        return "HTTP $c: $m$hint"
    }

    // Giải nén zip trong máy rồi đẩy tất cả lên repo thành 1 commit duy nhất
    private fun uploadZip(uri: Uri, repo: String, prefix: String) {
        setBusy(true)
        setStatus("Đang đọc file zip...")
        val zipName = fileName
        Thread {
            try {
                // 1) đọc zip
                val raw = ArrayList<Pair<String, ByteArray>>()
                var total = 0L
                ZipInputStream(contentResolver.openInputStream(uri)!!).use { zin ->
                    var e = zin.nextEntry
                    while (e != null) {
                        if (!e.isDirectory) {
                            val data = zin.readBytes()
                            total += data.size
                            if (total > 100L * 1024 * 1024) throw Exception("Nội dung zip lớn hơn 100MB")
                            raw.add(Pair(e.name.replace('\\', '/').trimStart('/'), data))
                        }
                        zin.closeEntry()
                        e = zin.nextEntry
                    }
                }
                // bỏ file rác
                val kept = raw.filter {
                    val n = it.first
                    n.isNotEmpty() && !n.startsWith("__MACOSX/") && !n.endsWith(".DS_Store") &&
                        !n.startsWith(".git/") && !n.contains("../")
                }
                if (kept.isEmpty()) throw Exception("File zip rỗng")
                // zip chỉ có 1 thư mục gốc thì bỏ thư mục đó (giống cd vào thư mục rồi push)
                val roots = kept.map { it.first.substringBefore('/') }.toSet()
                val strip = roots.size == 1 && kept.all { it.first.contains('/') }
                val files = kept.map {
                    val rel = if (strip) it.first.substringAfter('/') else it.first
                    Pair(if (prefix.isEmpty()) rel else "$prefix/$rel", it.second)
                }

                // 2) lấy commit hiện tại của nhánh mặc định
                val base = "https://api.github.com/repos/$repo"
                val (rc, rt) = http("GET", base, token = token)
                if (rc !in 200..299) throw Exception(ghErr(rc, rt))
                val branch = JSONObject(rt).getString("default_branch")
                val (fc, ft) = http("GET", "$base/git/ref/heads/$branch", token = token)
                if (fc !in 200..299) throw Exception("Repo chưa có commit nào, hãy tạo 1 file trước. " + ghErr(fc, ft))
                val parent = JSONObject(ft).getJSONObject("object").getString("sha")
                val (cc, ct) = http("GET", "$base/git/commits/$parent", token = token)
                if (cc !in 200..299) throw Exception(ghErr(cc, ct))
                val baseTree = JSONObject(ct).getJSONObject("tree").getString("sha")

                // 3) gửi từng file (blob)
                val tree = JSONArray()
                files.forEachIndexed { i, f ->
                    runOnUiThread { status.text = "Đang gửi file ${i + 1}/${files.size}..." }
                    val b = JSONObject()
                        .put("content", Base64.encodeToString(f.second, Base64.NO_WRAP))
                        .put("encoding", "base64")
                    val (bc, bt) = http("POST", "$base/git/blobs", b.toString(), token)
                    if (bc !in 200..299) throw Exception("${f.first}: " + ghErr(bc, bt))
                    tree.put(
                        JSONObject().put("path", f.first).put("mode", "100644")
                            .put("type", "blob").put("sha", JSONObject(bt).getString("sha"))
                    )
                }

                // 4) tạo tree, commit, rồi cập nhật nhánh
                runOnUiThread { status.text = "Đang tạo commit..." }
                val (tc, tt) = http("POST", "$base/git/trees",
                    JSONObject().put("base_tree", baseTree).put("tree", tree).toString(), token)
                if (tc !in 200..299) throw Exception(ghErr(tc, tt))
                val treeSha = JSONObject(tt).getString("sha")
                val (mc, mt) = http("POST", "$base/git/commits",
                    JSONObject().put("message", "Upload $zipName").put("tree", treeSha)
                        .put("parents", JSONArray().put(parent)).toString(), token)
                if (mc !in 200..299) throw Exception(ghErr(mc, mt))
                val commitSha = JSONObject(mt).getString("sha")
                val (uc, ut) = http("PATCH", "$base/git/refs/heads/$branch",
                    JSONObject().put("sha", commitSha).toString(), token)
                if (uc !in 200..299) throw Exception(ghErr(uc, ut))

                runOnUiThread {
                    setBusy(false)
                    setStatus("Đã giải nén và upload ${files.size} file lên $repo ✅", 1)
                    pop(logo, 1.25f)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false)
                    setStatus("Lỗi giải nén/upload: ${e.message}", 2)
                    shake(mainCard)
                }
            }
        }.start()
    }

    // ===================== tự cập nhật từ GitHub Releases =====================
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
                runOnUiThread { showBanner("⬇  Có bản mới 1.0.$latest, đang tải...") }
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
            showBanner("Hãy bật 'Cho phép cài ứng dụng' cho Uploader, rồi mở lại app")
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        showBanner("✅  Đã tải xong, bấm Cài đặt để cập nhật")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }
}
