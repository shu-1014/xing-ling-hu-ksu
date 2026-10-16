package com.xinglinghu.manager.ui.screen.assistant

import android.content.Context
import android.os.Build
import com.xinglinghu.manager.Natives
import com.xinglinghu.manager.R
import com.xinglinghu.manager.ui.stealth.Stealth
import com.xinglinghu.manager.ui.stealth.StealthController
import com.xinglinghu.manager.ui.util.getModuleCount
import com.xinglinghu.manager.ui.util.getSuperuserCount
import com.xinglinghu.manager.ui.util.listModules
import com.xinglinghu.manager.ui.util.rootAvailable
import org.json.JSONArray

/**
 * Fully offline intent engine behind the assistant screen.
 *
 * There is no network call anywhere in this file and no remote model: every
 * answer is either computed from a local kernel query or picked from a bundled
 * list. That is a deliberate design choice, not a shortcut. The manager runs as
 * root on someone else's phone, so shipping a chat box that silently uploads
 * what the user typed would be indefensible. Everything the assistant "knows"
 * is either read from the kernel through the JNA bridge or written into
 * `strings.xml`.
 *
 * The two halves stay separate:
 *
 *  - [reply] is pure: it may read local state, but it never mutates anything and
 *    never touches root. Running it is always safe.
 *  - [ACTION_PREFIX] commands are the only path that can change device state,
 *    and they go through ksud exactly like the rest of the app. They are
 *    recognized inside [reply] and returned as [Reply.Action] so the caller can
 *    ask for confirmation before executing.
 */
object LocalAssistant {

    /** Marker for the currently released joke list; shown on the joke card. */
    const val JOKE_PACK_VERSION = "本地段子 · 离线"

    /**
     * A single assistant turn.
     *
     * @param text rendered in the chat bubble, already localized.
     * @param action when non-null the bubble carries a device-changing command
     *   that must be confirmed before it runs.
     */
    data class Reply(
        val text: String,
        val action: Action? = null,
        val failed: Boolean = false,
    )

    data class Action(
        val label: String,
        val command: String,
    )

    /**
     * Phrases the engine understands. Kept as a matrix of keyword lists so a new
     * alias is one word, not one branch. Matching is substring-based and
     * case-insensitive, which is enough for Chinese input where there is no
     * whitespace to tokenize on.
     */
    private val KEYWORDS_STATUS = listOf(
        "状态", "情况", "信息", "status", "info", "怎么样", "检查", "自检",
    )
    private val KEYWORDS_HELP = listOf(
        "帮助", "help", "你会", "能做什么", "怎么用", "功能", "命令",
    )
    private val KEYWORDS_MODULE = listOf("模块", "module", "mod")
    private val KEYWORDS_SUPERUSER = listOf("超级用户", "授权", "root 权限", "root权限", "root")
    private val KEYWORDS_STEALTH_ON = listOf("开启隐身", "打开隐身", "启动隐身", "隐身模式开")
    private val KEYWORDS_STEALTH_OFF = listOf("关闭隐身", "关掉隐身", "退出隐身", "隐身模式关")
    private val KEYWORDS_STEALTH = listOf("隐身", "stealth", "隐藏")
    private val KEYWORDS_WEBUI = listOf("网页", "web", "面板", "webui", "管理器面板")
    private val KEYWORDS_JOKE = listOf("段子", "豆话", "笑话", "来一句", "梗", "逗", "讲个")
    private val KEYWORDS_VERSION = listOf("版本", "version", "内核版本", "kmi")
    private val KEYWORDS_DEVICE = listOf("设备", "机型", "手机", "device", "系统")

    /**
     * Answer [input] using only local state.
     *
     * Pure with respect to the device: reads nothing that needs root and writes
     * nothing at all. Call it off the main thread anyway, because the kernel
     * probes behind [statusText] are ioctls.
     */
    fun reply(context: Context, input: String): Reply {
        val text = input.trim()
        if (text.isEmpty()) return Reply(greeting(context))

        // Jokes first: "再来一句豆话" contains no management keyword, and a joke
        // request that also mentions root should still be a joke.
        if (matches(text, KEYWORDS_JOKE)) return Reply(randomJoke(context))

        if (matches(text, KEYWORDS_STEALTH_ON)) {
            return confirmAction(
                context,
                context.getString(R.string.assistant_action_stealth_on_label),
                "stealth start",
            )
        }
        if (matches(text, KEYWORDS_STEALTH_OFF)) {
            return confirmAction(
                context,
                context.getString(R.string.assistant_action_stealth_off_label),
                "stealth stop",
            )
        }
        if (matches(text, KEYWORDS_WEBUI)) {
            return confirmAction(
                context,
                context.getString(R.string.assistant_action_webui_label),
                "webui start",
            )
        }
        if (matches(text, KEYWORDS_STEALTH)) return Reply(stealthText(context))
        if (matches(text, KEYWORDS_VERSION)) return Reply(versionText())
        if (matches(text, KEYWORDS_DEVICE)) return Reply(deviceText())
        if (matches(text, KEYWORDS_MODULE)) return Reply(moduleText())
        if (matches(text, KEYWORDS_SUPERUSER)) return Reply(superuserText())
        if (matches(text, KEYWORDS_STATUS)) return Reply(statusText())
        if (matches(text, KEYWORDS_HELP)) return Reply(helpText(context))
        return Reply(unknownText(context))
    }

    /** The line the assistant opens with, mirroring the reference layout. */
    fun greeting(context: Context): String = context.getString(R.string.assistant_greeting)

    /** Title of the daily joke card. */
    fun jokeCardTitle(context: Context): String = context.getString(R.string.assistant_joke_title)

    /** Single-line disclaimer shown under the composer. */
    fun disclaimer(context: Context): String = context.getString(R.string.assistant_disclaimer)

    /**
     * Wrap [index] into the joke list.
     *
     * Wrapping rather than clamping means a persisted counter that somehow went
     * out of range still lands on a valid line instead of crashing the screen.
     */
    fun jokeAt(index: Int): Int {
        val size = JOKES.size
        if (size == 0) return 0
        return ((index % size) + size) % size
    }

    fun jokeCount(): Int = JOKES.size

    fun joke(index: Int): String = JOKES[jokeAt(index)]

    /**
     * Bundled offline jokes. They are about root, kernels and modules, because
     * that is what this app is, and a generic joke list would feel pasted on.
     */
    private val JOKES = listOf(
        "今天不写周报，今天管 Root。",
        "别的 App 在后台偷偷跑，我在后台悄悄 mount。",
        "内核说它不认识我，我说没关系，我认识你就行。",
        "老板问我为什么手机这么烫，我说它在编译内核，其实它在跑分。",
        "模块列表里那个 unknown，是真的 unknown，不是我不想说。",
        "有人问我 root 之后能干嘛，我说能睡觉，因为终于不用刷机了。",
        "SELinux 问我是谁，我掏出 sepolicy：自己人。",
        "日志里最诚实的一行永远是 permission denied。",
        "我不产生 bug，我只是内核 bug 的搬运工。",
        "凌晨三点的内核日志，比任何悬疑小说都精彩。",
        "刷机前先备份，这句话的价值等于一万个 sorry。",
        "fastboot 的本质是：让手机短暂地变成一块砖的预演。",
        "有人说 root 不安全，我说不安全的是你不知道自己装了什么。",
        "我的手机没有广告，因为广告商找不到我的 uid。",
        "内核版本号越看越像电话号码，可惜打不通。",
    )

    /**
     * Random-but-stable joke index, advancing a persisted counter so the same
     * joke does not repeat on consecutive taps.
     */
    fun nextJoke(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = jokeAt(prefs.getInt(KEY_JOKE_INDEX, -1) + 1)
        prefs.edit().putInt(KEY_JOKE_INDEX, next).apply()
        return next
    }

    /** Random joke string, used when a joke is requested from the composer. */
    private fun randomJoke(context: Context): String = joke(nextJoke(context))

    private fun confirmAction(context: Context, label: String, command: String) = Reply(
        text = context.getString(R.string.assistant_confirm_action, label),
        action = Action(label, command),
    )

    /** One-line overview of everything the kernel is willing to say. */
    fun statusText(): String = buildString {
        appendLine("内核：${kernelLine()}")
        appendLine("Root：${if (rootAvailable()) "已授权" else "未授权"}")
        appendLine("模块：${moduleCountSafe()} 个")
        appendLine("超级用户：${superuserCountSafe()} 个")
        append("隐身：${stealthLine()}")
    }

    private fun versionText(): String = buildString {
        appendLine("管理器 UAPI：${Natives.managerUAPIVersion}")
        append("内核 UAPI：${runCatching { Natives.kernelUAPIVersion }.getOrDefault(0)}")
        if (Natives.managerUAPIVersion != runCatching { Natives.kernelUAPIVersion }.getOrDefault(0)) {
            appendLine()
            append("两边不一致，说明内核不是这一版源码编的。")
        }
    }

    private fun deviceText(): String = buildString {
        appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
        append("ABI：${Build.SUPPORTED_ABIS.firstOrNull() ?: "未知"}")
    }

    private fun moduleText(): String {
        val modules = runCatching { JSONArray(listModules()) }.getOrNull()
            ?: return "模块列表读不出来。检查 Root 权限，或者确认 ksud 已安装。"
        if (modules.length() == 0) return "一个模块都没装，干净得像刚出厂。"
        val lines = ArrayList<String>(modules.length())
        for (i in 0 until modules.length()) {
            val module = modules.optJSONObject(i) ?: continue
            val name = module.optString("name").ifBlank { module.optString("id") }
            val version = module.optString("version").ifBlank { "未知版本" }
            // `enabled` is the field ksud reports; a missing field means on.
            val enabled = module.optBoolean("enabled", true)
            lines += "· $name（$version）${if (enabled) "" else " · 已停用"}"
        }
        return "共 ${modules.length()} 个模块：\n" + lines.joinToString("\n")
    }

    private fun superuserText(): String {
        val count = superuserCountSafe()
        return when (count) {
            0 -> "没有任何应用拿到授权。"
            1 -> "有 1 个应用拿到授权，去超级用户页面能看到是谁。"
            else -> "有 $count 个应用拿到授权，去超级用户页面能看到具体列表。"
        }
    }

    private fun stealthText(context: Context): String {
        val hidden = StealthController.isHidden(context)
        return buildString {
            appendLine("内核隐身：${stealthLine()}")
            append("桌面图标：${if (hidden) "已隐藏" else "正常显示"}")
        }
    }

    private fun stealthLine(): String = when (Stealth.state()) {
        Stealth.State.ON -> "已开启"
        Stealth.State.OFF -> "未开启"
        Stealth.State.UNKNOWN -> "读不到（内核可能不支持）"
    }

    private fun helpText(context: Context): String = context.getString(R.string.assistant_help_body)

    private fun unknownText(context: Context): String = context.getString(R.string.assistant_unknown)

    private fun kernelLine(): String = runCatching { Natives.version.toString() }.getOrDefault("未知")

    private fun moduleCountSafe(): Int = runCatching { getModuleCount() }.getOrDefault(0)

    private fun superuserCountSafe(): Int = runCatching { getSuperuserCount() }.getOrDefault(0)

    /** Case-insensitive substring match against any keyword in [group]. */
    private fun matches(input: String, group: List<String>): Boolean =
        group.any { input.contains(it, ignoreCase = true) }

    private const val PREFS = "ikun_assistant"
    private const val KEY_JOKE_INDEX = "joke_index"
}
