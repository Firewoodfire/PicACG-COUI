package com.picacg.coui.data

/**
 * 屏蔽规则引擎。对应哔咔「设置 → 屏蔽设定」那一套。
 *
 * 支持四类规则，各占一行：
 *
 * | 写法 | 含义 | 判定范围 |
 * |---|---|---|
 * | `女装`      | 普通关键词（模糊包含，不区分大小写） | 标题、简介、作者、标签、分类 |
 * | `作者:某画师` | 指定维度，`:` 后是关键词 | author / tag / title / text 四选一 |
 * | `(?i)^\d{5}$` | 以 `re:` 开头的正则 | 同上，命中任意一处即屏蔽 |
 * | `#123456`   | 以 `#` 开头的条目 id | 只看 id，精确匹配 |
 *
 * 模糊匹配按「子串」判断，不做分词——中文分词在这里反而容易漏。
 * 规则为空时 [enabled] 为 false，所有过滤直接跳过，不影响性能。
 */
data class BlockRules(
    /** 总开关。关掉后所有规则都不生效，但内容保留。 */
    val enabled: Boolean = false,
    /** 原始规则文本，每行一条。保留原文是为了让设置页能原样回显。 */
    val raw: String = "",
) {

    /** 解析后的规则，按行拆好、去掉空行与 `#` 注释行。 */
    val lines: List<String> get() = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("//") }
        .toList()

    /** 是否真的需要过滤。 */
    val active: Boolean get() = enabled && lines.isNotEmpty()

    /** 规则条数，设置页用来显示「已启用 N 条」。 */
    val count: Int get() = lines.size

    /**
     * 判断一个条目是否该被屏蔽。
     *
     * @param fields 该条目所有可读文本，键是维度名（title/subtitle/tags/author…）
     */
    fun blocks(id: String, fields: Map<String, String>): Boolean {
        if (!active) return false
        for (line in lines) {
            if (hits(line, id, fields)) return true
        }
        return false
    }

    /** 单条规则判定。命中返回 true。 */
    private fun hits(line: String, id: String, fields: Map<String, String>): Boolean {
        // 1. 条目 id：精确匹配
        if (line.startsWith("#")) {
            val want = line.drop(1).trim()
            return want.isNotEmpty() && id.equals(want, ignoreCase = true)
        }

        // 2. 正则：re: 前缀，或整行明显是正则（以 ^ 开头 / 以 $ 结尾 / 含 (?i)）
        val regexBody = when {
            line.startsWith("re:", ignoreCase = true) -> line.substring(3)
            line.startsWith("^") || line.endsWith("$") || line.contains("(?i)") -> line
            else -> null
        }
        if (regexBody != null) {
            val re = runCatching { Regex(regexBody) }.getOrNull() ?: return false
            return fields.values.any { re.containsMatchIn(it) }
        }

        // 3. 指定维度：author:xxx / tag:xxx / title:xxx / text:xxx
        val colon = line.indexOfFirst { it == ':' || it == '：' }
        if (colon in 1..12) {
            val key = line.substring(0, colon).trim().lowercase()
            val want = line.substring(colon + 1).trim()
            if (want.isEmpty()) return false
            val dims = when (key) {
                "作者", "author", "artist", "画师" -> listOf("author")
                "标签", "tag", "tags" -> listOf("tags")
                "标题", "title", "name" -> listOf("title")
                "简介", "desc", "description", "摘要" -> listOf("subtitle")
                "全文", "text", "all", "任意" -> fields.keys.toList()
                else -> emptyList() // 认不出的维度名，当普通关键词整行匹配
            }
            if (dims.isNotEmpty()) {
                return dims.any { d -> fields[d]?.contains(want, ignoreCase = true) == true }
            }
        }

        // 4. 普通关键词：任意维度包含即屏蔽
        return fields.values.any { it.contains(line, ignoreCase = true) }
    }

    companion object {
        val EMPTY = BlockRules()

        /** 首次启动给的一份示例，让用户知道格式，默认不启用。 */
        const val SAMPLE = "# 每行一条规则，空行会被忽略\n" +
            "# 普通关键词，命中标题 / 简介 / 作者 / 标签任意一处即屏蔽\n" +
            "女装\n" +
            "# 只看某个维度：author / tag / title / desc\n" +
            "author:某某画师\n" +
            "# 正则，以 re: 开头\n" +
            "re:(?i)test|测试\n" +
            "# 按条目 id 精确屏蔽\n" +
            "#123456"
    }
}
