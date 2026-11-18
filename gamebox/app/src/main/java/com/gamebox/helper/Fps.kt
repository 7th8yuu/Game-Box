package com.gamebox.helper

// Đo FPS của game từ SurfaceFlinger bằng quyền shell (Shizuku/root). Không dùng lớp Android nào nên chạy được cả bằng
// app_process trong CI (xem debug/FpsCli.kt) để thử đúng mã này trên emulator.
//
// Cách 1 "latency": dumpsys SurfaceFlinger --latency <layer> trả về mốc hiển thị của ~128 khung gần nhất.
// Cách 2 "timestats": dumpsys SurfaceFlinger --timestats đếm tổng số khung của từng layer; FPS = chênh lệch / thời gian.
// Cách 2 là dự phòng khi cách 1 không trả về khung nào (tùy bản Android/ROM). Trả -1 khi không có dữ liệu gì,
// để giao diện hiện "–" thay vì một số 0 sai.
class Layer(val name: String, val frames: Int, val fps: Int)

object FpsParse {
    private val ws = Regex("\\s+")

    // Android 15 in "--list" mỗi layer dạng "RequestedLayerState{<tên> parentId=.. z=..}"; --latency cần đúng <tên> đó.
    // Bản cũ in thẳng tên. Trả về tên dùng được cho --latency.
    fun norm(line: String): String {
        val l = line.trim()
        return if (l.startsWith("RequestedLayerState{")) l.removePrefix("RequestedLayerState{").substringBeforeLast(" parentId=").trim() else l
    }

    // Các layer của game từ kết quả --list: bỏ layer nền/viền, ưu tiên layer vẽ khung (SurfaceView/BLAST), tối đa n
    fun layerNames(list: List<String>, pkg: String, n: Int = 10): List<String> =
        list.filter { it.contains(pkg) }.map { norm(it) }
            .filter { it.isNotEmpty() && !it.startsWith("Background for") && !it.startsWith("Bounds for") }
            .distinct()
            .sortedByDescending { if (it.contains("SurfaceView") || it.contains("BLAST")) 1 else 0 }
            .take(n)

    // Đầu vào: các khối "@@<tên layer>", sau đó là dòng chu kỳ làm mới (1 số) rồi các dòng "desired actual ready" (ns).
    // Cột 2 = lúc hiển thị thật; khung chưa hiển thị có giá trị rất lớn (Long.MAX) nên bị bỏ.
    // Layer không có khung mới suốt hơn 2 giây (game tạm dừng) tính là 0. seen nhớ khung cuối của từng layer.
    fun latency(o: List<String>, now: Long, seen: MutableMap<String, Pair<Long, Long>>): List<Layer> {
        val res = ArrayList<Layer>()
        var name = ""
        var ts = ArrayList<Long>()
        fun flush() {
            if (name.isNotEmpty()) {
                var f = 0
                if (ts.size >= 2) {
                    val last = ts.max()
                    val prev = seen[name]
                    if (prev == null || prev.first != last) { seen[name] = last to now; f = rate(ts, last) }
                    else if (now - prev.second <= 2000L) f = rate(ts, last)
                }
                res.add(Layer(name, ts.size, f))
            }
            ts = ArrayList()
        }
        for (raw in o) {
            val l = raw.trim()
            if (l.startsWith("@@")) { flush(); name = l.substring(2); continue }
            val c = l.split(ws)
            if (c.size >= 3) {
                val t = c[1].toLongOrNull() ?: continue
                if (t > 0L && t < Long.MAX_VALUE / 2) ts.add(t)
            }
        }
        flush()
        return res
    }

    private fun rate(ts: List<Long>, last: Long): Int {
        val win = ts.filter { it > last - 1_000_000_000L }.sorted()
        val span = win.last() - win.first()
        val f = if (win.size >= 3 && span > 200_000_000L) ((win.size - 1) * 1e9 / span).toInt() else win.size
        return f.coerceIn(0, 240)
    }

    // Đầu vào là các dòng "layerName = X" và "totalFrames = N" của --timestats -dump. Một layer có thể xuất hiện nhiều lần
    // (theo từng mức tần số quét/render) nên cộng dồn. Dòng totalFrames chung (trước mọi layerName) bị bỏ.
    fun timeStats(o: List<String>): Map<String, Long> {
        val m = LinkedHashMap<String, Long>()
        var cur = ""
        for (raw in o) {
            val l = raw.trim()
            if (l.startsWith("layerName")) { cur = l.substringAfter("=").trim(); continue }
            if (l.startsWith("totalFrames") && cur.isNotEmpty()) {
                m[cur] = (m[cur] ?: 0L) + (l.substringAfter("=").trim().toLongOrNull() ?: 0L)
                cur = ""
            }
        }
        return m
    }
}

class FpsMeter(private val run: (String) -> List<String>) {
    private val seen = HashMap<String, Pair<Long, Long>>()
    private var layer = ""          // layer đang cho FPS tốt nhất, các lần sau chỉ hỏi layer này cho nhẹ
    private var scanAt = 0L
    private var zero = 0
    private var tsOn = false        // đã thấy số liệu timestats của game
    private var tsPrev: Map<String, Long> = emptyMap()
    private var tsAt = 0L

    @Volatile var diag = "Chưa đo"
    @Volatile var method = ""
    // Lần đo gần nhất có thấy layer của game (diag bị ghi đè khi game không còn trên màn hình) và các giá trị FPS gần đây
    @Volatile var diagSeen = "Chưa thấy layer nào của game"
    private val recentF = ArrayDeque<Int>()
    @Synchronized fun recent(): String = if (recentF.isEmpty()) "(trống)" else recentF.joinToString(" ")

    @Synchronized
    fun reset() {
        seen.clear(); layer = ""; scanAt = 0L; zero = 0; tsOn = false; tsPrev = emptyMap(); tsAt = 0L
        diag = "Chưa đo"; method = ""; diagSeen = "Chưa thấy layer nào của game"; recentF.clear()
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    // Trả về FPS (>= 0) hoặc -1 nếu không đo được. now tính bằng ms, tăng dần.
    @Synchronized
    fun measure(pkg: String, now: Long): Int {
        if (pkg.isBlank()) return -1
        // Quét danh sách layer lúc đầu, định kỳ, hoặc khi layer cũ ngừng có khung (game đổi cảnh/layer)
        // (Chưa thấy layer nào mà cũng chưa có số liệu timestats thì quét mỗi lần, chờ game hiện lên)
        val scan = now >= scanAt || zero >= 3 || (layer.isEmpty() && !tsOn)
        val names: List<String> = when {
            scan -> FpsParse.layerNames(run("dumpsys SurfaceFlinger --list | grep -F ${q(pkg)} | head -n 60"), pkg)
            layer.isNotEmpty() -> listOf(layer)
            else -> emptyList()
        }
        val s = StringBuilder()
        for (n in names) s.append("echo ${q("@@$n")}; dumpsys SurfaceFlinger --latency ${q(n)}\n")
        if (scan || tsOn) {
            s.append("echo '##TS'\n")
            s.append("dumpsys SurfaceFlinger --timestats -enable >/dev/null 2>&1\n")
            s.append("dumpsys SurfaceFlinger --timestats -dump 2>/dev/null | grep -F -e layerName -e totalFrames\n")
        }
        val out = if (s.isEmpty()) emptyList() else run(s.toString())
        val i = out.indexOfFirst { it.trim() == "##TS" }
        val lat = if (i >= 0) out.subList(0, i) else out
        val tsl = if (i >= 0) out.subList(i + 1, out.size) else emptyList()

        val layers = FpsParse.latency(lat, now, seen)
        val bestLat = layers.maxByOrNull { it.fps }
        val latF = bestLat?.fps ?: 0
        if (scan) {
            val pick = if (latF > 0) bestLat else layers.maxByOrNull { it.frames }
            layer = pick?.name ?: ""
            scanAt = now + 15_000L
            zero = 0
        }

        // Cách 2: tốc độ tăng của totalFrames từng layer thuộc game (lấy layer cao nhất, tránh cộng đôi layer cửa sổ + SurfaceView)
        val ts = FpsParse.timeStats(tsl).filter { (k, _) -> k.contains(pkg) && !k.startsWith("Background for") && !k.startsWith("Bounds for") }
        var tsF = -1
        if (ts.isNotEmpty()) {
            tsOn = true
            if (tsAt > 0L && now - tsAt >= 400L) {
                var b = 0.0
                for ((k, v) in ts) {
                    val p = tsPrev[k]
                    if (p != null && v >= p) b = maxOf(b, (v - p) * 1000.0 / (now - tsAt))
                }
                tsF = (b + .5).toInt().coerceIn(0, 240)
            }
            tsPrev = ts; tsAt = now
        }

        val f: Int
        when {
            latF > 0 -> { f = latF; method = "latency" }
            tsF > 0 -> { f = tsF; method = "timestats" }
            layers.isNotEmpty() || ts.isNotEmpty() -> { f = 0; method = if (layers.isNotEmpty()) "latency" else "timestats" }
            else -> { f = -1; method = "không thấy layer" }
        }
        if (f > 0) zero = 0 else zero++

        diag = buildString {
            append("FPS=").append(f).append(" · cách: ").append(method).append(if (scan) " · vừa quét" else "").append('\n')
            append("layer đang theo dõi: ").append(layer.ifEmpty { "(chưa có)" }).append('\n')
            append("latency: ").append(if (layers.isEmpty()) "không có layer khớp" else layers.take(6).joinToString("; ") { "${it.name.take(64)} [${it.frames} mốc, ${it.fps} fps]" }).append('\n')
            append("timestats: ").append(if (ts.isEmpty()) "không có số liệu" else ts.entries.take(4).joinToString("; ") { "${it.key.take(48)}=${it.value}" }).append(" -> ").append(tsF)
        }
        if (layers.isNotEmpty() || ts.isNotEmpty()) diagSeen = diag
        recentF.addLast(f); while (recentF.size > 40) recentF.removeFirst()
        return f
    }
}
