package com.xinglinghu.manager

import android.system.Os

/**
 * @author weishu
 * @date 2022/12/10.
 */

data class KernelVersion(val major: Int, val patchLevel: Int, val subLevel: Int) {
    override fun toString(): String {
        return "$major.$patchLevel.$subLevel"
    }

    fun isGKI(): Boolean {

        // kernel 6.x
        if (major > 5) {
            return true
        }

        // kernel 5.10.x
        if (major == 5) {
            return patchLevel >= 10
        }

        return false
    }

    /**
     * XingLingHu can be patched onto GKI 2.0 as well as onto the older non-GKI
     * kernels it was backported to, down to 4.14. Non-GKI devices have no
     * bundled LKM, so a kernelsu.ko built for that exact kernel is required
     * when patching their boot image.
     */
    fun isSupported(): Boolean {
        if (major < 0) {
            return false
        }
        return major > 4 || (major == 4 && patchLevel >= 14)
    }

    /** A supported kernel that is not GKI, e.g. 4.14 / 4.19 / 5.4. */
    fun isNonGKI(): Boolean = isSupported() && !isGKI()
}

fun parseKernelVersion(version: String): KernelVersion {
    val find = "(\\d+)\\.(\\d+)\\.(\\d+)".toRegex().find(version)
    return if (find != null) {
        KernelVersion(find.groupValues[1].toInt(), find.groupValues[2].toInt(), find.groupValues[3].toInt())
    } else {
        KernelVersion(-1, -1, -1)
    }
}

fun getKernelVersion(): KernelVersion {
    Os.uname().release.let {
        return parseKernelVersion(it)
    }
}