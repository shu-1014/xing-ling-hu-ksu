# 🐾星棱狐🐾 LKM Builder

这个仓库是用来**编译 `kernelsu.ko`** 的，让改名后的 🐾星棱狐🐾 管理器真正拿到 root。

只需要跑一次 Actions，不需要在本地装任何东西。

---

## 为什么必须重新编译

内核判定"谁是管理器"要求**两个条件同时成立**（`kernel/manager/throne_tracker.c`）：

```c
strncmp(np->package, package, KSU_MAX_PACKAGE_NAME) == 0   // 包名匹配
check_v2_signature(path, EXPECTED_SIZE, EXPECTED_HASH)     // 证书匹配
```

官方发布页那 16 个现成的 `lkm-*.ko` 里面写的是上游的 `me.weishu.kernelsu`
和上游证书哈希。它们能加载、能开机，但**永远不会把 root 交给你的管理器**。

这个仓库的 `kernel/Kbuild` 已经改成了你的身份：

```make
KSU_PACKAGE_NAME  ?= com.xinglinghu.manager
KSU_EXPECTED_SIZE ?= 0x033b
KSU_EXPECTED_HASH ?= ff7d77b3f5ee160dfcac5ff8a42d7a31eb825b2d88f83017b8323dd51c6c3b8d
```

用这份源码编出来的模块，内核才认。

---

## 怎么用

### 1. 跑 workflow

**Actions** → 左侧 **Build 星棱狐 LKM** → **Run workflow** → 绿色按钮。

参数保持默认即可：

| 参数 | 默认值 |
| --- | --- |
| `kmi` | `all`（编全部 8 个 KMI；想快可以填单个，如 `android14-6.1`）|
| `package_name` | `com.xinglinghu.manager` |
| `expected_hash` | `ff7d77b3…3b8d` |
| `expected_size` | `0x033b` |
| `ksu_version` | `30008`（要等于管理器 APK 的 versionCode）|

约 10–20 分钟。workflow 会**自动校验**产物里确实带着你的包名和证书哈希，
如果编出来还是上游身份，它会直接报错失败，不会给你一个没用的模块。

### 2. 下载

Run 页面底部 **Artifacts** → 下载 **`xinglinghu-lkm-all`**，解压得到：

```
android12-5.10_kernelsu.ko    android14-6.1_kernelsu.ko   android16-6.12_kernelsu.ko
android13-5.10_kernelsu.ko    android15-6.6_kernelsu.ko   android17-6.18_kernelsu.ko
android13-5.15_kernelsu.ko    android14-5.15_kernelsu.ko
```

### 3. 挑对版本

```bash
adb shell uname -r
```

输出里的 `android14-6.1` 这种片段就是 KMI，选同名的那个 ko。
**多数 Android 14 设备是 `android14-6.1`。**

### 4. 刷入

管理器 → **安装** → 选 `init_boot.img` → 展开 **使用本地 LKM 文件** → 选中刚下载的 ko
→ 下一步 → 修补 → 刷入产物。

或命令行：

```bash
/data/adb/ksud boot-patch -b /sdcard/init_boot.img \
  -m /sdcard/android14-6.1_kernelsu.ko -o /sdcard/patched.img
fastboot flash init_boot patched.img
```

### 5. 验证

开机后：

```bash
adb shell dmesg | grep -iE "XingLingHu|Found manager|is_manager"
```

看到 **`is_manager: 1`** 就成功了。

---

## 本地编译（可选，需要 Docker）

```powershell
docker run --rm -it --privileged `
  -v "$PWD:/build" -w /build/kernel `
  ghcr.io/ylarod/ddk-min:android14-6.1-20260828 `
  bash -c "CONFIG_KSU=m CC=clang make KSU_VERSION_OVERRIDE=30008 && ls -l kernelsu.ko"
```

三个不能少的点：`--privileged`、工作目录是 `kernel/`、`CONFIG_KSU=m CC=clang make`。
拉不动 `ghcr.io` 就换成 `docker.cnb.cool/ylarod/ddk-min:...`。

---

## 注意

- **不要**打 `v*` 标签。那会触发上游的 `release.yml`，用上游证书编一整套东西。
- 这个仓库里的模块**只能给你自己的管理器用**：换包名或换签名就会失效。
- 非 GKI 内核（4.14 / 4.19 / 5.4）**不适用**，那些要用设备自己的内核源码在树内编译。
- 详细说明见仓库外的 `上传说明.md`。
