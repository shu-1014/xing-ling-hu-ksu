package com.xinglinghu.manager.ui.stealth

import com.xinglinghu.manager.Natives

/**
 * Kernel-backed stealth mode.
 *
 * ADAPTED from 7kimisu's `Stealth`. Two independent disguises exist in this fork
 * and they are deliberately kept separate:
 *
 *  - **Kernel stealth** (this object) makes the kernel stop reporting
 *    [Natives.isManager], so the whole Manager renders as "not installed" to
 *    anyone looking at the screen. Real privileges are unchanged, which is what
 *    keeps the switch itself reachable.
 *  - **Hidden launcher icon** ([StealthController]) is a pure PackageManager
 *    trick with no kernel involvement.
 *
 * The three-state model matters. [state] is read from the kernel, and a failed
 * read must not be reported to the user as "off": it can mean the kernel lacks
 * the ioctl, that this build is not the recognised manager, or that the call was
 * rejected. Only an explicit 1 is treated as on, and only an explicit 0 as off.
 *
 * The duplicate of this file's knowledge that used to live here — comparing
 * KERNEL_SU_UAPI_VERSION against a "version that introduced stealth" — was wrong
 * and has been removed; see [kernelHasStealthFeature] for why.
 */
object Stealth {

    /** Mirrors `KSU_STEALTH_PATH` in `kernel/manager/stealth.c`. */
    const val STEALTH_FLAG_PATH = "/data/adb/ksu/stealth"

    /** Raw kernel value: 1 = on, 0 = off, anything else = unreadable. */
    fun kernelState(): Int = runCatching { Natives.stealthState() }.getOrDefault(-1)

    enum class State {
        /** Kernel explicitly reported 1. */
        ON,

        /** Kernel explicitly reported 0. */
        OFF,

        /** Could not read: too old, not the manager, or the call failed. */
        UNKNOWN,
    }

    fun state(): State = when (kernelState()) {
        1 -> State.ON
        0 -> State.OFF
        else -> State.UNKNOWN
    }

    /** True only when the kernel explicitly confirmed stealth is on. */
    fun isEnabled(): Boolean = kernelState() == 1

    fun setEnabled(enabled: Boolean): Boolean =
        runCatching { Natives.stealthSet(enabled) }.getOrDefault(false)

    /**
     * Whether the kernel answered our stealth ioctl at all.
     *
     * This deliberately probes the ioctl instead of comparing
     * [Natives.kernelUAPIVersion] against [UAPI_WITH_STEALTH]. The kernel keeps
     * KERNEL_SU_UAPI_VERSION at 5 on purpose (raising it would make "new APK +
     * older kernel" fail ksud's version check and break module mounting), so a
     * version of 5 says nothing about whether these commands exist. A kernel
     * that lacks them answers -ENOTTY, which reads back as -1.
     */
    fun kernelHasStealthFeature(): Boolean = kernelState() >= 0

    fun kernelLooksOurs(): Boolean = runCatching { Natives.isManager }.getOrDefault(false)

    /**
     * Toggles stealth and reports failure in words.
     *
     * @return null on success, otherwise a user-facing reason.
     */
    fun setEnabledReporting(enabled: Boolean): String? {
        if (setEnabled(enabled)) return null
        val action = if (enabled) "开启" else "关闭"
        return when {
            // A readable state proves the ioctls exist, so the write failed for
            // some other reason; say so rather than blaming the kernel version.
            kernelHasStealthFeature() ->
                "${action}隐身失败：内核拒绝了本次写入，请重启设备后重试"
            // The ioctl is missing. If the kernel still calls us its manager then
            // it is simply an older build of this fork; otherwise it is not ours.
            kernelLooksOurs() ->
                "当前内核不支持隐身模式：请刷入本次构建的内核（已装 LKM 的需重新修补）"
            else ->
                "当前内核不是本项目的：请重启设备，或卸载其它 root 管理器后重试"
        }
    }
}
