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
 * The three-state model matters: reading the kernel flag can fail because the
 * kernel predates the feature, because this build is not the recognised manager,
 * or because the ioctl was rejected. Collapsing "could not read" into "off"
 * would show root-only UI on a device the user believes is disguised, so
 * anything unreadable is treated as "possibly on".
 */
object Stealth {

    /**
     * UAPI version that introduced the stealth ioctls.
     *
     * The command numbers are appended after the existing table, so an older
     * kernel simply answers -ENOTTY. The version is used only to explain *why* a
     * read or write failed, never to gate behaviour.
     */
    private const val UAPI_WITH_STEALTH = 5

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

    /**
     * Whether root-only surface should be hidden.
     *
     * True unless the kernel explicitly said stealth is off, so an unreadable
     * state hides rather than reveals.
     */
    fun looksStealthy(): Boolean = state() != State.OFF

    /** True only when the kernel explicitly confirmed stealth is on. */
    fun isEnabled(): Boolean = kernelState() == 1

    fun setEnabled(enabled: Boolean): Boolean =
        runCatching { Natives.stealthSet(enabled) }.getOrDefault(false)

    /*
     * Failure explanation.
     *
     * When the write fails, the useful question is whether the kernel even has
     * the feature. If it does, the failure is an identity problem (this build is
     * not the recognised manager yet); if it does not, the running root is some
     * other implementation or the kernel is too old for this fork.
     */
    fun kernelHasStealthFeature(): Boolean =
        runCatching { Natives.kernelUAPIVersion >= UAPI_WITH_STEALTH }.getOrDefault(false)

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
            kernelHasStealthFeature() ->
                "${action}隐身失败：本应用当前未被内核认作管理器。请重启设备，或卸载其它管理器后重试"
            kernelLooksOurs() -> "内核版本过旧，不支持隐身模式"
            else -> "当前内核不是本项目的：请重启设备，或卸载其它 root 管理器后重试"
        }
    }
}
