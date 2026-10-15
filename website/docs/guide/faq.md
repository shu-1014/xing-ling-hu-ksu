# FAQ

## Does 🐾星棱狐🐾 support my device?

🐾星棱狐🐾 supports devices running Android with an unlocked bootloader. However, official support is only for GKI Linux Kernels 5.10+ (in practice, this means your device needs to have Android 12 out-of-the-box to be supported).

You can easily check the support for your device through the 🐾星棱狐🐾 manager, which is available [here](https://github.com/tiann/KernelSU/releases). 

If the app shows `Not installed`, it means your device is officially supported by 🐾星棱狐🐾.

If the app shows `Unsupported`, it means your device isn't officially supported at present. However, you can build kernel source code and integrate 🐾星棱狐🐾 to make it work, or use [Unofficially supported devices](unofficially-support-devices).

## Do I need to unlock the bootloader to use 🐾星棱狐🐾?

Yes. 🐾星棱狐🐾 requires an unlocked bootloader.

## Does 🐾星棱狐🐾 support modules?

Yes, most Magisk modules work on 🐾星棱狐🐾. However, if your module needs to modify `/system` files, you need to install a [metamodule](metamodule.md) (such as `meta-overlayfs`). Other module features work without a metamodule. Check [Module guide](module.md) for more info.

## Does 🐾星棱狐🐾 support Xposed?

Yes, you can use LSPosed (or other modern Xposed derivative) with [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext).

## Does 🐾星棱狐🐾 support Zygisk?

🐾星棱狐🐾 has no built-in Zygisk support, but you can use a module like [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext) to support it.

## Is 🐾星棱狐🐾 compatible with Magisk?

🐾星棱狐🐾's module system conflicts with Magisk's magic mount. If any module is enabled in 🐾星棱狐🐾, Magisk will stop working entirely.

However, if you only use the `su` of 🐾星棱狐🐾, it will work well with Magisk. 🐾星棱狐🐾 modifies the `kernel`, while Magisk modifies the `ramdisk`, allowing both to work together.

## Will 🐾星棱狐🐾 substitute Magisk?

No. Replacing Magisk isn't our goal. Magisk is already an excellent userspace root solution. 🐾星棱狐🐾 focuses on exposing kernel interfaces to users instead of supplanting Magisk.

## Can 🐾星棱狐🐾 support non-GKI devices?

It's possible. But you should download the kernel source, integrate 🐾星棱狐🐾 into the source tree, and compile the kernel yourself.

## Can 🐾星棱狐🐾 support devices below Android 12?

It's the device's kernel that affects 🐾星棱狐🐾's compatibility, and it has nothing to do with the Android version. The only restriction is that devices launched with Android 12 must have a kernel version of 5.10+ (GKI devices). So:

1. Devices launched with Android 12 must be supported.
2. Devices with an older kernel (some devices with Android 12 also have the older kernel) are compatible (you should build kernel yourself).

## Can 🐾星棱狐🐾 support old kernel?

It's possible. 🐾星棱狐🐾 is backported to kernel 4.14 now. For older kernels, you need to backport it manually, and PRs are always welcome!

## How to integrate 🐾星棱狐🐾 for an older kernel?

Please check the [Integrate for non-GKI devices](how-to-integrate-for-non-gki) guide.

## Why my Android version is 13, and the kernel shows "android12-5.10"?

The kernel version has nothing to do with the Android version. If you need to flash kernel, always use the kernel version; the Android version isn't as important.

## I'm GKI 1.0, can I use this?

GKI 1.0 is completely different from GKI 2.0, you must compile kernel by yourself.

## How can I make `/system` RW?

We don't recommend that you modify the system partition directly. Please check [Module guide](module.md) to modify it systemlessly. If you insist on doing this, check [magisk_overlayfs](https://github.com/HuskyDG/magic_overlayfs).

## Can 🐾星棱狐🐾 modify hosts? How can I use AdAway？

Of course. But 🐾星棱狐🐾 doesn't have built-in hosts support, you can install a module like [systemless-hosts](https://github.com/symbuzzer/systemless-hosts-🐾星棱狐🐾-module) to do it.

## Why aren't my modules working after fresh install?

If your modules need to modify `/system` files, you need to install a [metamodule](metamodule.md) to mount the `system` directory. Other module features (scripts, sepolicy, system.prop) work without a metamodule.

**Solution**: See the [Metamodule Guide](metamodule.md) for installation instructions.

## What is a metamodule and why do I need one?

A metamodule is a special module that provides infrastructure for mounting regular modules. See the [Metamodule Guide](metamodule.md) for a complete explanation.
