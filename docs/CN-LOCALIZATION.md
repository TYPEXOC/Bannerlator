# 简体中文本地化（CN Localization）维护指南

> 本 fork 在官方代码之上增加了一层简体中文本地化，以及一个应用内语言切换器。
> 本文说明**它怎么工作、上游更新后怎么维护、如何继续补译、如何加更多语言**。

---

## 1. 功能概述

| 功能 | 位置 |
|---|---|
| 语言切换 | 应用内：**设置 → 外观 → 语言**（跟随系统 / English / 简体中文） |
| 生效方式 | 即时生效，无需重启；选择会持久保存 |
| 系统集成 | Android 13+ 会同步到系统「应用语言」设置（manifest `localeConfig`） |

## 2. 架构：为什么这样设计

官方代码里 **90% 的界面文字是硬编码在 Compose 代码中的英文字面量**，不走 string 资源——
所以传统的 `values-zh-rCN` 汉化**碰不到**它们。本 fork 用「三层解耦」解决：

| 层 | 文件 | 作用 |
|---|---|---|
| 运行时词表 | `app/src/main/java/com/winlator/star/i18n/CnTable.kt` | 英文原文 → 中文映射（查不到就原样显示英文） |
| 框架 | `i18n/I18n.kt`、`i18n/AppLanguage.kt`、`i18n/I18nText.kt` | 词表调度、语言持久化、`Text` 包装（所有界面文字都经过它） |
| 资源 | `app/src/main/res/values-zh-rCN/strings.xml` | 给 Java/XML 老界面与 `stringResource` 用的标准汉化 |

外加：约 160 个界面文件的 import 从 `androidx.compose.material3.Text` 换成了
`com.winlator.star.i18n.Text`（仅一行替换，调用点全部不动），使所有 `Text(...)`
文字自动经过词表。

**关键行为：词表/资源里没有的字符串自动回落英文**——不报错、不崩溃、不阻塞功能。
这让翻译可以「一屏一屏」增量推进，也让上游更新永远不会破坏运行。

## 3. 汉化引擎的全部文件清单

```
app/src/main/java/com/winlator/star/i18n/       ← 汉化引擎（上游没有这个目录）
    I18n.kt         多语言调度（TABLES 注册表：加语言 = 加一行）
    AppLanguage.kt  语言状态与持久化（options 决定外观页的选项列表）
    I18nText.kt     Text 包装组件（镜像 material3 签名，按值翻译）
    CnTable.kt      英文 → 中文词表（核心资产，逐条增删）
app/src/main/res/values-zh-rCN/strings.xml      ← 标准资源汉化
app/src/main/res/xml/locales_config.xml         ← Android 13+ 语言列表
```

被修改的既有文件（上游更新时需留意，见 §4）：
`AndroidManifest.xml`（localeConfig）、`app/build.gradle`（appcompat 1.6.1）、
`BannerlatorApp.kt`（启动时应用语言）、`ui/screens/AppearanceScreen.kt`（语言选择器）、
约 160 个界面文件的 import 行。

## 4. 上游更新维护流程

当上游（The412Banner/Bannerlator）推送了新代码：

```
① GitHub：fork 的 main 分支页 → “Sync fork” → “Update branch”      （一键）
② 本地：git fetch origin && git checkout dev && git merge origin/main
        → 出现冲突就解决（大概率没有；若有，见下）
③ 缺口扫描：运行 §5.3 的脚本，列出上游新增、尚未翻译的字符串
④ 补译：把新词条加入 CnTable.kt（或 values-zh-rCN）→ 提交推送
⑤ 构建：GitHub Actions → “CI Build (artifacts only)” → Run workflow → 分支 dev
```

**会冲突吗？** 翻译是解耦层，绝大部分文件上游根本不会动。理论上只有 4 处可能出现
几行的标准冲突，均易解：

1. 约 160 个文件的 import 替换行（上游若同时改了 import 区）；
2. `AppearanceScreen.kt`（我们插入了语言区块）；
3. `app/build.gradle`（appcompat 版本行）；
4. `AndroidManifest.xml` / `BannerlatorApp.kt`（各插入了一行/一块）。

**解决原则**：两边都保留——保留我们的 import / 语言相关行，接受上游的其余改动。

**上游新增了界面文件怎么办？** 新文件的文字默认走原始 material3 `Text`，不会翻译
（也无害）。运行 §5.2 的「接入脚本」即可把新文件接进翻译层（幂等，可反复跑）。

## 5. 继续补译的方法

### 5.1 往词表加词条（Compose 界面）

在 `CnTable.kt` 的 map 中新增一行即可，**无需改任何界面代码**：

```kotlin
    "Settings" to "设置",
    "Some New Button" to "某个新按钮",
```

- 键（英文原文）必须与运行时**完全一致**（含标点、大小写）；
- 带 `${...}` 插值的动态串无法精确匹配，跳过或用其他方式处理；
- 多行拼接的字符串（`"a " + "b"`）需以**拼接后的完整值**为键；
- 重复键不会报错但会覆盖，加完建议跑 §5.4 的校验脚本。

### 5.2 接入新文件（import 替换，幂等）

```python
# 在仓库根目录运行：把仍用 material3 Text 的界面文件接进翻译层
import re, pathlib
root = pathlib.Path('app/src/main/java')
pat = re.compile(r'(?m)^import androidx\.compose\.material3\.Text(\r?)$')
for f in root.rglob('*.kt'):
    t = f.read_text(encoding='utf-8')
    new, n = pat.subn(lambda m: 'import com.winlator.star.i18n.Text' + m.group(1), t)
    if n:
        f.write_text(new, encoding='utf-8')
        print('已接入:', f)
```

### 5.3 缺口扫描（列出未译字符串）

```python
# 在仓库根目录运行：列出所有尚未进入词表/资源的界面英文字符串
import re, pathlib, xml.etree.ElementTree as ET
root = pathlib.Path('app/src/main/java')
cn = (root / 'com/winlator/star/i18n/CnTable.kt').read_text(encoding='utf-8')
done = set(k.replace('\\"', '"') for k in re.findall(r'(?m)^\s*"((?:[^"\\]|\\.)+)"\s+to\s', cn))
zh = ET.parse('app/src/main/res/values-zh-rCN/strings.xml').getroot()
names = {el.get('name') for el in zh if el.tag == 'string'}
en = ET.parse('app/src/main/res/values/strings.xml').getroot()
en_by = {el.get('name'): (el.text or '') for el in en if el.tag == 'string'}
done |= {en_by[n] for n in names if n in en_by}
lit = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
todo = set()
for f in root.rglob('*.kt'):
    if 'i18n' in f.parts: continue
    for m in lit.finditer(f.read_text(encoding='utf-8', errors='ignore')):
        s = m.group(1)
        if len(s) < 2 or '${' in s or '\\n' in s or s.isupper(): continue
        if not re.search(r'[A-Za-z]', s) or (' ' not in s and s[0].islower()): continue
        if s not in done: todo.add(s)
print('\n'.join(sorted(todo)))
print(f'# 共 {len(todo)} 条候选（含日志等非界面串，人工筛除后翻译）')
```

### 5.4 提交前校验（三查）

```python
# 词表：引号配对 / 重复键 / 结构完整
import re, collections, pathlib
t = pathlib.Path('app/src/main/java/com/winlator/star/i18n/CnTable.kt').read_text(encoding='utf-8')
bad = [l for l in t.split('\n') if l.replace('\\"','').count('"') % 2]
keys = re.findall(r'(?m)^\s*"((?:[^"\\]|\\.)+)"\s+to\s', t)
dups = [k for k, c in collections.Counter(keys).items() if c > 1]
print('问题行:', bad, '| 条目:', len(keys), '| 重复:', dups, '| 结尾:', repr(t[-8:]))
```

资源文件：XML 合法性、字符串名必须存在于默认 `strings.xml`、占位符（`%1$s`、`%1$d`、`%%`）两边一致。

### 5.5 有意不翻译的内容

- **内部日志字符串**（`blsteam:`、`syncBack(...) failed` 等，只在 logcat 出现，不进界面）；
- 品牌/专有名词（`app_name`、`Unity`、`Denuvo`、`Steam`、`Turnip` 等）；
- 格式串（如 `R%1$dC%2$d`）与正则；中文括注用全角引号“”，避免破坏 XML/Kotlin。

## 6. 扩展到更多语言（繁体 / 日语 / 韩语）

框架已按多语言设计，**加一门语言不需要动任何界面代码**：

1. 新建词表文件（如 `CnTableTw.kt`，同结构 `internal val CN_TABLE_TW = mapOf(...)`）；
2. `I18n.kt` 的 `TABLES` 注册一行：`AppLanguage.CHINESE_TW to CN_TABLE_TW`；
3. `AppLanguage.kt` 加常量 + `normalize()` 分支 + `options` 加一项；
4. 新建 `values-zh-rTW/strings.xml`（繁体可由简体脚本转换 + 人工校对）；
5. `locales_config.xml` 加一行。

> 优先建议先做**繁体中文**：现有 4900 条简体翻译可用简繁转换工具（如 OpenCC 词组表）
> 自动生成初版，再校对少量术语（内存→記憶體、视频→影片 等），成本很低。

## 7. 构建与验收

- 本 fork 只通过 **GitHub Actions** 构建（本机无需 Android 环境）：
  `Actions → “CI Build (artifacts only)” → Run workflow → 分支选 dev`；
- 产物：4 个 flavor 的已签名 APK（Artifacts 区下载，保留 7 天）；
- 验收路径：装机后 **设置 → 外观 → 语言 → 简体中文**，逐屏核对；
- 若 CI 编译失败：从失败 run 页面右上角 `...` 菜单下载 **log archive**，
  在其中 `27_Build with Gradle.txt` 搜 `e: file` 即可定位全部编译错误。

## 8. 当前状态（截至 2026-10-16）

- 词表 **4287 条** + 资源 **614 条**（≈ 4900 条翻译），界面文案已完整覆盖；
- 资源侧被引用的 617 条中 614 条已译，其余 6 条为有意保留（品牌名/格式串/空源）；
- 未译部分仅剩**内部日志字符串**（有意为之）。
