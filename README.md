# 🐾星棱狐🐾 LKM Builder

这个仓库有两个 workflow：

| Workflow | 作用 | 用哪个 |
| --- | --- | --- |
| **一体构建管理器《U》** | 编 `kernelsu.ko` → 内嵌进 ksud → 打包成**装完即用的管理器 APK** | ⭐ 推荐 |
| **Build 星棱狐 LKM** | 只编 `kernelsu.ko`，给已有的管理器用 | 想单独换模块时 |

两个都需要先配签名密钥（见下面「一次性配置」）。

---

## 为什么必须自己编译

内核判定"谁是管理器"要求**两个条件同时成立**（`kernel/manager/throne_tracker.c`）：

```c
strncmp(np->package, package, KSU_MAX_PACKAGE_NAME) == 0   // 包名匹配
check_v2_signature(path, EXPECTED_SIZE, EXPECTED_HASH)     // 证书匹配
```

官方发布页那 16 个现成的 `lkm-*.ko` 里写的是上游的 `me.weishu.kernelsu`
和上游证书哈希。它们能加载、能开机，但**永远不会把 root 交给你的管理器**。

这个仓库的 `kernel/Kbuild` 已经改成你的身份：

```make
KSU_PACKAGE_NAME  ?= com.xinglinghu.manager
KSU_EXPECTED_SIZE ?= 0x033b
KSU_EXPECTED_HASH ?= ff7d77b3f5ee160dfcac5ff8a42d7a31eb825b2d88f83017b8323dd51c6c3b8d
```

---

## 一次性配置：签名密钥

**为什么必须配**：APK 的签名证书哈希被烧进了内核（`KSU_EXPECTED_HASH`）。
如果 CI 用别的密钥签名，内核就不认这个管理器 —— 和用官方 LKM 一样白搭。

到 **Settings → Secrets and variables → Actions → New repository secret** 添加 4 个：

| Secret 名称 | 值 |
| --- | --- |
| `KEYSTORE` | `.jks` 文件的 base64 |
| `KEYSTORE_PASSWORD` | 密钥库口令 |
| `KEY_ALIAS` | 别名（`xinglinghu-key`） |
| `KEY_PASSWORD` | 私钥口令 |

生成 base64（在你自己的机器上跑，**不要**把它贴到任何公开地方）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\ksu-build\xinglinghu-release.jks')) | Set-Clipboard
```

> ⚠️ **密钥口令曾经泄露过一次。** 早期提交里 `manager/gradle.properties`
> 带着明文口令被推到了公开仓库（现已彻底清除并重建仓库）。
> **请务必改掉口令** —— 改口令**不会改变证书**，所以 `KSU_EXPECTED_HASH` 依然有效，
> 不需要重新编译任何东西。把下面的 `旧口令` 换成你手里的原口令：
>
> ```powershell
> keytool -storepasswd -keystore C:\ksu-build\xinglinghu-release.jks `
>   -storepass 旧口令 -new 你的新口令
> keytool -keypasswd -alias xinglinghu-key -keystore C:\ksu-build\xinglinghu-release.jks `
>   -storepass 你的新口令 -keypass 旧口令 -new 你的新口令
> ```
>
> 改完之后，本机 `manager/gradle.properties` 与 GitHub 的 `KEYSTORE_PASSWORD`、
> `KEY_PASSWORD` 两个 Secret 都要同步成新口令。

---

## 用法一：一体构建管理器《U》（推荐）

**Actions** → 左侧 **一体构建管理器《U》** → **Run workflow**。

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `kmi` | `all` | 要内嵌的 KMI。默认全都要，一个 APK 适配所有 5.10 以上机型 |
| `package_name` | `com.xinglinghu.manager` | |
| `expected_hash` | `ff7d77b3…3b8d` | |
| `expected_size` | `0x033b` | |
| `version_code` | `30008` | 要和你现在装的 APK 一致，否则覆盖安装会失败 |
| `version_name` | `v3.4.1-xinglinghu` | |
| `sign` | `true` | 关掉会产出未签名 APK（装不上，仅供调试） |

跑完在 **Artifacts** 下载：

- **`一体构建-管理器APK`** → 装这个就行，模块已经内嵌在里面
- **`一体构建-LKM模块`** → 单独的 ko，备用

装好后进「安装」选 boot 镜像，管理器会直接用内嵌的模块修补，**不需要**再选本地 LKM 文件。

### 关于 `kmi`

默认 `all`，也就是把 8 个 GKI 模块全部内嵌进去：

```
android12-5.10  android13-5.10  android13-5.15  android14-5.15
android14-6.1   android15-6.6   android16-6.12  android17-6.18
```

这样一个 APK 拿到任何 5.10 以上的设备上都能用，不用先查 KMI。
每个模块约 100–300 KB，全部内嵌会让 APK 大几 MB —— 用体积换通用性。

想省体积就自己挑，逗号分隔：

```
android14-6.1
android14-6.1,android14-5.15
```

查自己设备是哪个：

```bash
adb shell uname -r
# 6.1.75-android14-11-g4b1a2c3d
#       ^^^^^^^^^^^^ → android14-6.1
```

> 5.4 / 4.19 / 4.14 这些非 GKI 内核**不在支持范围内**，它们没有通用 LKM，
> 必须拿设备自己的内核源码在树内编译。

---

## 用法二：只编 LKM

**Actions** → **Build 星棱狐 LKM** → **Run workflow** → `kmi` 填 `all` 或单个。

产物 `xinglinghu-lkm-all` 是 8 个 ko。然后在管理器里：
**安装** → 选 `init_boot.img` → 展开 **使用本地 LKM 文件** → 选中对应的 ko。

`boot_patch.rs:806-810` 的优先级保证本地 LKM 会覆盖内置的：

```rust
} else if let Some(kmod_path) = kmod {      // ← 你选了本地文件就走这里
    Box::new(map_file(&kmod_path)?)
} else {
    assets::get_asset(&name)...             // ← 没指定才用内置的
};
```

---

## 两个 workflow 都做的安全检查

编译后会自动扫二进制，确认里面**真的**是给你的管理器编的：

```bash
grep -aq "com.xinglinghu.manager" kernelsu.ko   # 必须命中
grep -aq "ff7d77b3…"              kernelsu.ko   # 必须命中
grep -aq "c371061b…"              kernelsu.ko   # 命中就报错
```

一体构建还会额外检查 APK 里每个 ABI 的 `libksud.so`
确实内嵌了 `*_kernelsu.ko` —— 因为 ksud 自己的 `build.rs:237`
会把包名默认成同一个字符串，光 grep 包名是**测不出来**有没有内嵌模块的。

任何一项不过就直接失败，不会给你一个装上去没 root 的包。

---

## 本地编译（可选，需要 Docker）

```powershell
docker run --rm -it --privileged `
  -v "$PWD:/build" -w /build/kernel `
  ghcr.io/ylarod/ddk-min:android14-6.1-20260828 `
  bash -c "CONFIG_KSU=m CC=clang make KSU_VERSION_OVERRIDE=30008 && ls -l kernelsu.ko"
```

三个不能少：`--privileged`、工作目录 `kernel/`、`CONFIG_KSU=m CC=clang make`。
拉不动 `ghcr.io` 就换 `docker.cnb.cool/ylarod/ddk-min:...`。

---

## 注意

- **不要**打 `v*` 标签，会触发上游的 `release.yml` 用上游证书编一整套东西。
- 编出来的模块**只能给你自己的管理器用**，换包名或换签名就失效。
- 非 GKI 内核（4.14 / 4.19 / 5.4）**不适用**，那些要用设备自己的内核源码在树内编译。
- 改 `version_code` 时要和实际安装的 APK 对齐，否则系统不让覆盖安装
  （签名相同但 versionCode 更低会被拒）。
