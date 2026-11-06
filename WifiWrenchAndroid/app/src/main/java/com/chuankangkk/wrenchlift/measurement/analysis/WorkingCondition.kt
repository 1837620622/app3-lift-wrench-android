package com.chuankangkk.wrenchlift.measurement.analysis

import com.chuankangkk.wrenchlift.measurement.TorqueAngleWindow
import kotlin.math.max
import kotlin.math.min

/**
 * 单个运行段的**工况划分**：扳手在转还是停、受没受力、扭矩在升还是在降。只用 0x12 过程点（转角、扭矩、接收时间），
 * 不改协议、不做标定，阈值均为开发默认值（待真机标定）。
 *
 * 每个点先按「前后各 [WINDOW] 点」的转角 / 扭矩变化归类（0x12 转角是整数度，单点差分常为 0，所以看窗口）：
 *
 * | 受力（扭矩 ≥ 受力门槛） | 转角在增 | 转角不动 | 转角在减 |
 * |---|---|---|---|
 * | 否，且本段还没受过力 | 空程 | 静止 | 回退 |
 * | 否，本段已受过力     | 脱开空转 | 静止 | 回退 |
 * | 是                   | 加载（扭矩降则「转动降扭」） | 停转保压（扭矩降则「卸载」） | 回退 |
 *
 * 整段峰值低于 [NO_LOAD_PEAK_NM]：在转的点记「空转」（没上螺栓 / 空载试转），不在转的点记「静止」。
 * 短于 [MIN_PHASE_POINTS] 点的阶段并入相邻阶段（抗抖）。另记三类瞬时事件：疑似打滑（相邻点扭矩骤降）、
 * 数据间断（段内接收间隔 > [GAP_MILLIS]）、转角回退（相邻点转角下降）。
 *
 * 受力门槛 = max(段峰值 × [ENGAGE_FRACTION], [ENGAGE_FLOOR_NM])，与 [LoadingAnalysis] 有效加载起点同一口径：
 * 「空程」结束处就是有效加载的起点。
 */
object WorkingCondition {
    const val ENGAGE_FRACTION = LoadingAnalysis.START_FRACTION
    const val ENGAGE_FLOOR_NM = 1.0
    /** 整段峰值扭矩低于此值：全程未受力。 */
    const val NO_LOAD_PEAK_NM = 5.0
    const val WINDOW = 3
    /** 窗口内转角净变化 ≥ 此值算在转，≤ −此值算回退。 */
    const val MOVE_DEG = 1.0
    /** 窗口内扭矩下降 ≥ 段峰值 × 此比例算「扭矩在降」。 */
    const val TREND_FRACTION = 0.03
    /** 相邻两点扭矩下降 ≥ 前一点的此比例（前一点 ≥ 2 × 受力门槛、转角未回退）记一次疑似打滑。 */
    const val SLIP_DROP_FRACTION = 0.3
    /** 相邻两点转角下降至少这么多记一次回退。 */
    const val BACKTRACK_EVENT_DEG = 1.0
    const val MIN_PHASE_POINTS = 3
    const val GAP_MILLIS = TorqueAngleWindow.DASH_GAP_MILLIS

    enum class Phase(val label: String, val abnormal: Boolean = false) {
        IDLE("静止"),
        FREE_TRAVEL("空程"),
        LOADING("加载"),
        STALL("停转保压"),
        UNLOADING("卸载"),
        TORQUE_DROP("转动降扭", abnormal = true),
        FREE_SPIN("脱开空转", abnormal = true),
        BACKOFF("回退"),
        NO_LOAD("空转"),
    }

    enum class EventKind(val label: String, val unit: String) {
        SLIP("疑似打滑", "扭矩降 %"),
        GAP("数据间断", "间隔 s"),
        BACKTRACK("转角回退", "回退 °"),
    }

    /** 一个阶段：[start]..[end] 为段内下标（含两端）。 */
    data class Segment(
        val phase: Phase,
        val start: Int,
        val end: Int,
        val startAngle: Double,
        val endAngle: Double,
        val startTorque: Double,
        val endTorque: Double,
        val maxTorque: Double,
        val startMillis: Long,
        val endMillis: Long,
    ) {
        val points: Int get() = end - start + 1
        val seconds: Double get() = (endMillis - startMillis) / 1000.0
        val angleChange: Double get() = endAngle - startAngle
    }

    /** 瞬时事件：[index] 为段内下标；[value] 单位见 [EventKind.unit]。 */
    data class Event(val kind: EventKind, val index: Int, val value: Double)

    data class Result(
        val segments: List<Segment>,
        val events: List<Event>,
        val peakTorque: Double,
        val engageTorque: Double,
        val noLoad: Boolean,
    ) {
        val pointCount: Int get() = segments.lastOrNull()?.end?.plus(1) ?: 0

        /** 末点所处阶段（实时界面用）。 */
        val current: Phase? get() = segments.lastOrNull()?.phase

        fun count(kind: EventKind): Int = events.count { it.kind == kind }

        fun angleIn(phase: Phase): Double = segments.filter { it.phase == phase }.sumOf { it.angleChange }

        fun secondsIn(phase: Phase): Double = segments.filter { it.phase == phase }.sumOf { it.seconds }

        /** 阶段次序（相邻相同的合并），最多 [limit] 项，例如「空程 → 加载 → 停转保压 → 卸载」。 */
        fun sequence(limit: Int = 8): String {
            val names = segments.map { it.phase.label }
            val shown = names.take(limit).joinToString(" → ")
            return if (names.size > limit) "$shown → …（共 ${names.size} 段）" else shown
        }

        /** 一句判读：只列客观现象，不下故障结论。 */
        val verdict: String
            get() {
                if (segments.isEmpty()) return "无数据"
                if (noLoad) return "全程未受力（空转 / 未上螺栓）"
                val notes = buildList {
                    count(EventKind.SLIP).takeIf { it > 0 }?.let { add("疑似打滑 $it 次") }
                    if (segments.any { it.phase == Phase.TORQUE_DROP }) add("转动中扭矩下降")
                    if (segments.any { it.phase == Phase.FREE_SPIN }) add("受力后脱开空转")
                    count(EventKind.BACKTRACK).takeIf { it > 0 }?.let { add("转角回退 $it 次") }
                    count(EventKind.GAP).takeIf { it > 0 }?.let { add("数据间断 $it 次") }
                }
                return if (notes.isEmpty()) "未见异常" else notes.joinToString("；")
            }

        /** 段内下标 → 阶段（阶段按下标升序、首尾相接）。 */
        fun phaseAt(index: Int): Phase? {
            var lo = 0
            var hi = segments.lastIndex
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val s = segments[mid]
                when {
                    index < s.start -> hi = mid - 1
                    index > s.end -> lo = mid + 1
                    else -> return s.phase
                }
            }
            return null
        }

        companion object {
            val EMPTY = Result(emptyList(), emptyList(), 0.0, ENGAGE_FLOOR_NM, noLoad = false)
        }
    }

    fun classify(angle: DoubleArray, torque: DoubleArray, millis: LongArray, count: Int = min(angle.size, min(torque.size, millis.size))): Result {
        val n = count
        if (n <= 0) return Result.EMPTY
        var peak = Double.NEGATIVE_INFINITY
        for (i in 0 until n) if (torque[i].isFinite()) peak = max(peak, torque[i])
        if (!peak.isFinite()) peak = 0.0
        val engage = max(peak * ENGAGE_FRACTION, ENGAGE_FLOOR_NM)
        val noLoad = peak < NO_LOAD_PEAK_NM
        val trend = max(peak * TREND_FRACTION, ENGAGE_FLOOR_NM)

        val labels = arrayOfNulls<Phase>(n)
        var engaged = false
        for (i in 0 until n) {
            val a = max(0, i - WINDOW)
            val b = min(n - 1, i + WINDOW)
            val dA = angle[b] - angle[a]
            val dT = torque[b] - torque[a]
            val loaded = !noLoad && torque[i] >= engage
            val moving = dA >= MOVE_DEG
            val back = dA <= -MOVE_DEG
            labels[i] = when {
                back -> Phase.BACKOFF
                noLoad -> if (moving) Phase.NO_LOAD else Phase.IDLE
                !loaded && moving -> if (engaged) Phase.FREE_SPIN else Phase.FREE_TRAVEL
                !loaded -> Phase.IDLE
                moving -> if (dT <= -trend) Phase.TORQUE_DROP else Phase.LOADING
                dT <= -trend -> Phase.UNLOADING
                else -> Phase.STALL
            }
            if (loaded) engaged = true
        }
        smooth(labels, n)

        val segments = ArrayList<Segment>()
        var start = 0
        for (i in 1..n) {
            if (i == n || labels[i] != labels[start]) {
                var maxT = torque[start]
                for (k in start..i - 1) maxT = max(maxT, torque[k])
                segments += Segment(
                    phase = labels[start]!!, start = start, end = i - 1,
                    startAngle = angle[start], endAngle = angle[i - 1],
                    startTorque = torque[start], endTorque = torque[i - 1], maxTorque = maxT,
                    startMillis = millis[start], endMillis = millis[i - 1],
                )
                start = i
            }
        }

        val events = ArrayList<Event>()
        var slipping = false
        var backing = false
        for (i in 1 until n) {
            val gap = millis[i] - millis[i - 1]
            if (gap > GAP_MILLIS) events += Event(EventKind.GAP, i, gap / 1000.0)
            val prevT = torque[i - 1]
            val drop = if (prevT > 0.0) (prevT - torque[i]) / prevT else 0.0
            // 疑似打滑：扭矩骤降且螺栓仍在转（转角严格增加）。卸载时扭矩降但转角停住，不算打滑。
            val slip = !noLoad && prevT >= 2.0 * engage && drop >= SLIP_DROP_FRACTION && angle[i] > angle[i - 1]
            if (slip && !slipping) events += Event(EventKind.SLIP, i, drop * 100.0)
            slipping = slip
            val backStep = angle[i - 1] - angle[i]
            val backNow = backStep >= BACKTRACK_EVENT_DEG
            if (backNow && !backing) events += Event(EventKind.BACKTRACK, i, backStep)
            backing = backNow
        }
        return Result(segments, events, peak, engage, noLoad)
    }

    /** 短阶段并入前一阶段（首段并入后一阶段），重复到没有短阶段或只剩一段。 */
    private fun smooth(labels: Array<Phase?>, n: Int) {
        repeat(4) {
            var changed = false
            var start = 0
            var previous: Phase? = null
            var i = 1
            while (i <= n) {
                if (i == n || labels[i] != labels[start]) {
                    val length = i - start
                    if (length < MIN_PHASE_POINTS && !(start == 0 && i == n)) {
                        val replacement = previous ?: labels.getOrNull(i)?.takeIf { i < n }
                        if (replacement != null && replacement != labels[start]) {
                            for (k in start until i) labels[k] = replacement
                            changed = true
                        }
                    }
                    previous = labels[start]
                    start = i
                }
                i += 1
            }
            if (!changed) return
        }
    }
}
