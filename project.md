# E-File PyCharm Plugin 项目文档

## 📋 项目概述

| 项目信息 | 详情 |
|----------|------|
| **项目名称** | E-File PyCharm Plugin |
| **包名** | `com.github.antigravity.efile` |
| **版本** | 1.0-SNAPSHOT |
| **目标平台** | PyCharm / IntelliJ IDEA (2023.1 - 2025.3.*) |
| **JDK 版本** | Java 17 |
| **Kotlin 版本** | 1.9.22 |
| **IntelliJ Platform Plugin 版本** | 2.2.1 |

### 📝 项目描述

这是一个为 PyCharm / IntelliJ IDEA 开发的插件，用于支持 **E语言（电力系统数据标记语言）** 文件的编辑和查看。E语言是中国电力系统中广泛使用的数据交换格式。

### 🎯 支持的文件类型

| 扩展名 | 说明 |
|--------|------|
| `.e` | E语言主文件 |
| `.dt` | 数据文件 |
| `.cime` | CIM-E 文件 |

---

## 🏗️ 项目架构

### 📁 目录结构

```
src/
├── main/
│   ├── kotlin/com/github/antigravity/efile/
│   │   ├── ELanguage.kt              # 语言定义
│   │   ├── EFileType.kt              # 文件类型定义
│   │   ├── actions/
│   │   │   └── EFormatAction.kt      # 格式化动作
│   │   ├── fold/
│   │   │   └── EFoldingBuilder.kt    # 代码折叠
│   │   ├── format/
│   │   │   └── EFormattingModelBuilder.kt  # 格式化模型
│   │   ├── highlight/
│   │   │   ├── EHighlighter.kt       # 语法高亮
│   │   │   └── ERainbowAnnotator.kt  # 彩虹列着色
│   │   ├── io/
│   │   │   └── EEncodingProvider.kt  # 编码提供器
│   │   ├── lang/
│   │   │   ├── ELexer.kt             # 词法分析器
│   │   │   ├── EParserDefinition.kt  # 解析器定义
│   │   │   └── ETokenTypes.kt        # Token 类型定义
│   │   ├── toolwindow/
│   │   │   ├── EToolWindow.kt        # 工具窗口
│   │   │   └── EToolWindowFactory.kt # 工具窗口工厂
│   │   └── ui/
│   │       └── EFileEditorListener.kt # 编辑器监听器
│   └── resources/
│       └── META-INF/
│           └── plugin.xml            # 插件配置
└── test/
    └── kotlin/com/github/antigravity/efile/
        └── EFormatterTest.kt         # 格式化测试
```

---

## 🔧 核心组件

### 1️⃣ 语言定义层 (`lang/`)

#### ELexer.kt - 词法分析器

**功能**: 将源代码文本转换为 Token 序列

**支持的 Token 类型**:

| Token | 符号 | 说明 |
|-------|------|------|
| `COMMENT` | `//...` | 单行注释 |
| `TAG_START` | `<` | 标签开始 |
| `TAG_END` | `>` | 标签结束 |
| `TAG_CLOSE_START` | `</` | 闭合标签开始 |
| `TAG_INFO_START` | `<!` | 信息标签开始 |
| `TAG_INFO_END` | `!>` | 信息标签结束 |
| `MARKER_AT` | `@` | 表头标记 |
| `MARKER_AT_AT` | `@@` | 双@标记 |
| `MARKER_HASH` | `#` | 数据行标记 |
| `MARKER_DOLLAR` | `$` | 特殊标记 |
| `MARKER_PERCENT` | `%` | 百分比标记 |
| `STRING` | `'...'` | 字符串字面量 |
| `IDENTIFIER` | - | 标识符 |
| `DOUBLE_COLON` | `::` | 双冒号 |

#### EParserDefinition.kt - 解析器定义

**功能**: 定义语法解析规则，构建 PSI 树

**解析逻辑**:
- 使用栈结构处理嵌套模块
- `<...>` 开始新模块
- `</...>` 结束模块
- 自动构建 `MODULE` 节点

---

### 2️⃣ 语法高亮层 (`highlight/`)

#### EHighlighter.kt - 语法高亮器

**颜色映射**:

| Token 类型 | 颜色风格 |
|------------|----------|
| COMMENT | 灰色 (LINE_COMMENT) |
| TAG_* | 金色 (FUNCTION_DECLARATION) |
| MARKER_* | 黄色 (METADATA) |
| STRING | 绿色 (STRING) |
| NUMBER | 蓝色 (NUMBER) |
| IDENTIFIER | 默认标识符色 |

#### ERainbowAnnotator.kt - 彩虹列着色

**功能**: 对数据行的不同列自动着色，方便区分数据列

**着色逻辑**:
- 检测以 `#` 或 `@` 开头的行
- 按列索引循环使用 7 种颜色
- 通过 PSI 树遍历计算列位置

---

### 3️⃣ 格式化层 (`format/`)

#### EFormattingModelBuilder.kt - 格式化模型

**核心功能**: 列对齐格式化

**特性**:
- 识别 `@` 表头行和 `#` 数据行
- 自动计算每列最大宽度
- 支持中文字符宽度（按 2 字符计算）
- 大文件保护（>1MB 跳过格式化）

**格式化规则**:
- 标签行：不进行列对齐
- 数据块：按列对齐，列间距 2 空格
- 保留原有换行

---

### 4️⃣ 代码折叠 (`fold/`)

#### EFoldingBuilder.kt

**功能**: 模块折叠支持

**折叠策略**:
- 折叠整个 `<Module>...</Module>` 块
- 折叠提示显示 `<Module...>`
- 默认不折叠

---

### 5️⃣ 编码处理 (`io/`)

#### EEncodingProvider.kt

**功能**: 强制使用 GBK 编码

**支持的扩展名**: `.e`, `.dt`, `.cime`

---

### 6️⃣ 工具窗口 (`toolwindow/`)

#### EToolWindow.kt - 结构视图

**功能**:
- 显示文件模块结构树
- 显示每个模块的数据行数
- 显示字段列表
- 点击导航到对应位置

**显示格式**: `ModuleName [DataCount]`

---

### 7️⃣ 编辑器增强 (`ui/`)

#### EFileEditorListener.kt

**功能**:
- 打开 E 文件时显示通知
- 自动移除只读属性
- 提示编码为 GBK

---

### 8️⃣ 动作 (`actions/`)

#### EFormatAction.kt - 格式化动作

**触发方式**: `Ctrl+Alt+Shift+E` 或 Code 菜单

**功能**:
- 后台执行格式化
- 显示进度指示器
- 支持取消操作

---

## 📦 插件配置 (plugin.xml)

### 扩展点注册

| 扩展点 | 实现类 | 功能 |
|--------|--------|------|
| `fileType` | `EFileType` | 文件类型识别 |
| `lang.parserDefinition` | `EParserDefinition` | 语法解析 |
| `lang.syntaxHighlighterFactory` | `EHighlighterFactory` | 语法高亮 |
| `annotator` | `ERainbowAnnotator` | 彩虹着色 |
| `lang.foldingBuilder` | `EFoldingBuilder` | 代码折叠 |
| `lang.formatter` | `EFormattingModelBuilder` | 格式化 |
| `fileEncodingProvider` | `EEncodingProvider` | 编码处理 |
| `toolWindow` | `EToolWindowFactory` | 结构窗口 |

---

## 🔨 构建与使用

### 构建命令

```bash
# 手动构建
./gradlew copyJarToRoot

# 自动监视构建
./watch_build.bat
```

### 输出产物

- `e_file_for_pycharm.jar` (约 55KB)

### 安装方式

1. PyCharm → Settings → Plugins → ⚙️ → Install Plugin from Disk
2. 选择 `e_file_for_pycharm.jar`
3. 重启 IDE

---

## 📊 E语言语法示例

```e
<basevalue::MeasurePoint>
@    name       value        unit
#    p_base     100.000000   MW
#    u_scale    1.000000     pu
</basevalue::MeasurePoint>

<bus::Bus>
@@  name    voltage    angle
#   Bus1    230.0      0.0
#   Bus2    230.0      -1.5
</bus::Bus>
```

**语法说明**:
- `<ModuleName::Type>` - 模块开始标签
- `</ModuleName::Type>` - 模块结束标签
- `@` - 表头行
- `@@` - 双@表头（特殊含义）
- `#` - 数据行
- `//` - 注释

---

## 🧪 测试

### 测试类: EFormatterTest

| 测试方法 | 功能 |
|----------|------|
| `testFormatRealFile()` | 格式化实际文件测试 |
| `testAlignment()` | 列对齐验证测试 |

### 运行测试

```bash
./gradlew test
```

---

## 📋 功能特性总结

| ✅ 功能 | 状态 |
|--------|------|
| 语法高亮 | 完成 |
| 彩虹列着色 | 完成 |
| 模块折叠 | 完成 |
| 列对齐格式化 | 完成 |
| GBK 编码强制 | 完成 |
| 结构视图窗口 | 完成 |
| 吸顶展示 (Sticky Lines) | 🌟完成 |
| 自动打包版号与启动弹窗 | 🌟完成 |
| 快捷键格式化 | 完成 |
| 只读属性自动移除 | 完成 |

---

## ⚠️ 已知问题

1. 大文件（>1MB）格式化会被跳过
2. 简单词法分析器，不支持复杂嵌套语法
3. 中文宽度计算基于简单规则，可能不准确

---

## 📅 更新历史

| 日期 | 版本 | 说明 |
|------|------|------|
| 2025-02-05 | V1 | 初始版本发布 |
| 2026-03-22 | V1.2.x | 🌟**里程碑:** 完美实现结构化吸顶 (Sticky Lines)。改造解析树并在 `@` 行加入嵌套结构，配合 `EBreadcrumbsProvider` 面包屑引擎提供给原生 IDE 支持原生双层吸顶。 |
| 2026-03-22 | V1.2.x | 自动动态构建时间戳版号，并在 PyCharm 重启加载后弹出明确的版本更新确认窗口。 |

---

## ❓ 用户关键提问记录

1. **核心需求**: 用该插件浏览E文件时，向下滚动或向上滚动的受始终保持当前表的 表名（<>行）和字段行(@开头的行)位于编辑器顶部。
2. **部署诉求**: 自动部署新插件，如果需要重启pycharm，自动关闭并重启。
3. **工程化规则**: 插件每次修改都更新版本号。pycharm启动时不在弹出插件 actived 的提示，而是弹出插件当前的版本信息。
4. **定位与排错**: 这个版本依然没有实现要求，努力想办法找到失败的原因，找到确定的原因后再次修改。
5. **归档要求**: 记录当前版本为一个里程碑，方便以后回退。
6. **标签着色要求**: <>内的文本颜色和 <> 的颜色保持一致。
7. **工具窗口高亮**: 给右侧 E-file structure 中 表名 也着色。
8. **结构树光标联动**: 光标位于 # 开头的行时，自动识别列索引，并在工具窗口展开对应的表且将同名列名高亮渲染为红色。
9. **超大文件支持**: 针对 8MB 以上文件（突破 IDE PSI 限制），引入高速文本回退扫描引擎，确保结构树索引永不为空。

---

## 👤 作者

**Antigravity** - [GitHub](https://github.com/antigravity/efile)