# Difference with Magisk

Although 🐾星棱狐🐾 and Magisk modules have many similarities, there are inevitably some differences due to their completely different implementation mechanisms. If you want your module to work on both Magisk and 🐾星棱狐🐾, it's essential to understand these differences.

## Similarities

- Module file format: Both use the ZIP format to organize modules, and the module format is practically the same.
- Module installation directory: Both are located at `/data/adb/modules`.
- Systemless: Both support modifying `/system` in a systemless way through modules.
- post-fs-data.sh: Execution time and semantics are exactly the same.
- service.sh: Execution time and semantics are exactly the same.
- system.prop: Completely the same.
- sepolicy.rule: Completely the same.
- BusyBox: Scripts are run in BusyBox with "Standalone Mode" enabled in both cases.

## Differences

Before understanding the differences, it's important to know how to identify whether your module is running in 🐾星棱狐🐾 or Magisk. You can use the environment variable `KSU` to differentiate it in all places where you can run module scripts (`customize.sh`, `post-fs-data.sh`, `service.sh`). In 🐾星棱狐🐾, this environment variable will be set to `true`.

Here are some differences:

- 🐾星棱狐🐾 modules cannot be installed in Recovery mode.
- 🐾星棱狐🐾 modules don't have built-in support for Zygisk, but you can use Zygisk modules through [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext).
- **Module mounting architecture**: 🐾星棱狐🐾 uses a [metamodule system](metamodule.md) where mounting is delegated to pluggable metamodules (e.g., `meta-overlayfs`), while Magisk has mounting built into its core. 🐾星棱狐🐾 requires installing a metamodule to enable module mounting.
- The method for replacing or deleting files in 🐾星棱狐🐾 modules is completely different from Magisk. 🐾星棱狐🐾 doesn't support the `.replace` method. Instead, you need to create a same-named file with `mknod filename c 0 0` to delete the corresponding file.
- The directories for BusyBox are different. The built-in BusyBox in 🐾星棱狐🐾 is located at `/data/adb/ksu/bin/busybox`, while in Magisk it is at `/data/adb/magisk/busybox`. **Note that this is an internal behavior of 🐾星棱狐🐾 and may change in the future!**
- 🐾星棱狐🐾 doesn't support `.replace` files, but it supports the `REMOVE` and `REPLACE` variables to remove or replace files and folders.
- 🐾星棱狐🐾 adds the `boot-completed` stage to run scripts after the boot process is finished.
- 🐾星棱狐🐾 adds the `post-mount` stage to run scripts after module mounting is complete.
