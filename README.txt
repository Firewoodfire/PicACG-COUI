PicACG COUI — ColorOS 17 风格应用骨架（支持 App 内配置接口）
================================================================

版本：v17 / 1.5.0（versionCode 17，2026-11-25）
应用名：PicACG COUI
包名：com.picacg.coui
系统要求：Android 12（API 31）及以上
APK 大小：27,954,607 字节（26.7 MB）
SHA256：9bb71cc885aaf39f52b59063313ab774728b0f20f2fc84baebdf68d3e5ea64cf
源码仓库：https://github.com/Firewoodfire/PicACG-COUI
发布页面：https://github.com/Firewoodfire/PicACG-COUI/releases


一、这一版改了什么
------------------
【v1.5.0 / v17】
1. 新增标签屏蔽：分类、搜索、排行的每个条目显示前三枚标签，点一下确认即可屏蔽。
2. 屏蔽后当前列表立即摘掉命中项，顶部提示条可一键撤销；详情页的标签也能直接屏蔽。
3. 设置 → 屏蔽新增「已屏蔽的标签」，胶囊形式逐个点掉，不用再手敲 tag: 规则。
4. tag: 规则改为整枚标签相等匹配，屏蔽「百合」不会连带「百合向」。

更早版本只在 CHANGELOG.md 里记一行要点，不在 README 里堆细节。


二、接口设置（v15 起默认内置哔咔）
------------------
从 v15 开始不用再手填地址和路径了。App 默认使用内置的哔咔接口：

  根地址：https://picaapi.picacomic.com
  登录：  POST /auth/sign-in        {"email": 账号, "password": 密码}
  首页：  GET  /comics?page=1
  分类：  GET  /categories
  排行：  GET  /comics/leaderboard?tt=H24   （day / week / month 自动映射 H24 / D7 / D30）
  搜索：  GET  /comics/search?keyword=xxx
  详情：  GET  /comics/{id}
  章节：  GET  /comics/{id}/eps
  公告：  GET  /announcements

请求签名（内置模式下自动加，不需要你管）：
  signature = HMAC-SHA256(路径 + 时间戳 + 随机串 + 方法 + api-key) 的小写十六进制
  同时发 api-key、nonce、timestamp、signature、app-channel、app-version、
  app-build-version、app-uuid、app-platform、app-mode、app-source 这些头。
  算法和 api-key 都来自 picacg 开源客户端的公开实现，不是你的账号凭据。

你只需要做一件事：登录。
  1. 右下角悬浮按钮 → 设置
  2. 「账号登录」里填哔咔账号（邮箱）和密码
  3. 点「登录」
  4. 成功后令牌自动保存，主页 / 分类 / 排行 / 搜索 / 详情立刻能用
  密码只在登录那一刻用一次，不写进配置、不落盘、不打印。

设置页现在长这样：
  · 接口来源：使用内置哔咔接口（默认开）/ 启用自定义接口 / 兼容模式
  · 账号登录：账号、密码、登录按钮、退出登录
  · 服务地址：内置模式下只显示令牌输入框；关掉内置后才显示根地址
  · 接口路径：只在自定义模式显示
  · 屏蔽设定：规则开关与文本

想接自己的后端：
  1. 关掉「使用内置哔咔接口」
  2. 打开「启用自定义接口」
  3. 填根地址、令牌、6 个路径
  路径占位符：{page} {id} {keyword} {kind} {category}
  「测试连接」会发一次真实请求，显示完整 URL、解析条数和原始返回前 1500 字。

生效方式：改完停 1 秒自动生效，也可以点「保存并生效」立即生效。


三、屏蔽规则（v9 新增）
--------------------
位置：右下角悬浮按钮 → 设置 → 「屏蔽设定」
开关关掉时任何规则都不生效，内容原样显示。

规则一行一条，写法：

  女装                 普通关键词。标题、简介、作者、标签任意一处包含就屏蔽
  author:某某画师       只看作者维度。维度名可用 author / tag / title / desc / text
  re:(?i)test|测试      正则。以 re: 开头，也可以写成 ^开头 或 结尾$ 的形式
  #123456              按条目 id 精确屏蔽
  // 注释              以 // 开头是注释，空行忽略

匹配规则：
  · 关键词是「子串包含」，不区分大小写，不做分词（中文分词反而容易漏）
  · 正则用 Kotlin Regex，语法写错的那一条会被跳过，不会崩
  · 列表里没有独立作者字段时，作者维度会去比对副标题，因为多数接口把作者放在副标题

生效位置：
  主页信息流、分类列表、排行列表、搜索结果、详情页
  列表页过滤后会在顶部显示「已按屏蔽规则隐藏 N 部」；
  详情页如果命中规则，会显示「该内容已被你的屏蔽规则拦截」。
  这样从排行、搜索直接点进来也绕不过去。

规则存在本地 SharedPreferences，不联网、不上传。

四、字段是怎么对应的
--------------------
解析是全自动的，会依次尝试常见字段名，所以大部分 REST 接口填上地址就能用。

标题：title / name / comicName / comic_name / bookName / nickname
封面：cover / coverUrl / coverImage / image / imageUrl / img / pic / picture /
      thumb / thumbnail / thumbUrl / poster / posterUrl / banner / avatar
      （v12 起会真的下载显示；嵌套写法 {"fileServer":"...","path":"..."} 也能拼）
      也支持对象形式：{"url": "..."} 或 {"fileServer": "...", "path": "..."}
简介：subtitle / description / desc / summary / intro / author / category / type / tag
标签：tags / tag / categories / category / labels / genres
      （支持数组、逗号分隔字符串、对象 {"name": "..."}）
列表：list / docs / items / comics / data / result / results / records / rows / content / books / posts / articles
      （找不到这些 key 时会递归往下找，最多 4 层，所以深层嵌套也能命中）
id：  id / _id / comicId / comic_id / uuid / key / sid / pathWord

详情页额外读：
  作者 author / artist / authorName / creator
  章节 chapters / eps / episodes / chapterList（元素取 id、title、pages）
  点赞 likes / likeCount，浏览 views / viewCount / readCount

公告页读：title / content / message / body / date / createdAt

如果字段名完全对不上，界面会显示「未命名」和占位色块，不会崩。
碰到这种情况，在设置页点「测试连接」，照着原始返回改一下你的接口字段，或者告诉我字段名，我加进映射表。


五、几处体验上的处理
------------------
1. 接口出错不再显示空白
   请求失败会显示具体原因（HTTP 状态码 + 返回内容前 180 字），并带「重试」按钮。
2. 地址格式检查
   没写 http:// 或 https:// 会直接提示，不会抛一个看不懂的异常。
3. 输入不会丢
   每敲一个字都会立刻存到本地，误退出也不会丢。
4. 边打字不会狂发请求
   输入停下 1 秒才发一次，中间会取消上一次。


六、装法
--------
1. 把 PicACG-COUI.apk 传到手机
2. 允许「未知来源应用」安装
3. 打开后点右下角悬浮的齿轮按钮进「设置」，填你的接口地址
   （设置页右上角「关于」可以看作者与 QQ 交流群）

Android 14 及以上能拿到全部 COUI 效果（轮廓投影、分段选择器等）。
Android 12 / 13 会自动降级成系统投影，界面照常可用。

注意：包名改成 com.picacg.coui 了，和之前的 com.coui.demo 是两个不同的应用，
可以同时安装，互不影响。旧版可以自行卸载。


七、附：直连 picaapi.picacg.com 的实测结果（2026-11-23）
--------------------------------------------------------
结论先说：现在连不通，拿不到任何数据。下面是探测过程。

网络层是通的：
  · picaapi.picacg.com 解析到 172.241.213.97，与阿里 DoH 公共解析一致，不是 DNS 污染
  · 443 端口可连
  · TLS 证书为 Let's Encrypt 签发的 *.picacg.com，有效期至 2027-01，证书合法

但应用层拿不到数据：
  · GET /             → 302 到 http://ww547.picacg.com/?tkn=19vnMT3S（一个第三方广告落地页，
                        解析到 34.160.121.137，与 API 服务器不是同一台）
  · GET /init         → 返回一个 484 字节的 JS 挑战页（Cowboy 服务器 + Joken JWT），
                        走完它的两步跳转后，仍然 302 到上面那个广告页
  · GET /books、/comics/1、/users/profile → 直接 302 到广告页
  · GET /categories、/rank → 同样返回 JS 挑战页，走完还是 302 到广告页
  · api.picacg.com、www.picacg.com、picacg.com 表现完全一致
  · 换 UA（okhttp / Chrome / 空）、换 HTTP/HTTPS、带完整 App 请求头名
    （api-key、nonce、time、signature、authorization）—— 全部一样
  · 全程 0 个 JSON 响应

判断：这个域名当前挂在一个「按鉴权有效性分流」的网关后面。
      没有有效的 api-key + 动态 signature 时，它不给 401 JSON 错误，
      而是统一跳转到广告页。所以「填个地址就通」在这里不成立。
      那套 signature 是客户端私有鉴权（HMAC-SHA256 + 时间戳 + 随机串），
      属于访问控制机制，这个 App 不去伪造它，也不内置任何提取出来的密钥。

可以用的方式：把你在用的、能正常返回 JSON 的接口地址填进设置页。
              只要返回结构是常见的 JSON，列表页和详情页都能直接解析。


八、已知限制（如实说明）
----------------------
1. 没有真机 / 模拟器验证
   构建环境没有硬件虚拟化（/dev/kvm 不存在），Android 模拟器跑不起来。
   已完成的是：编译通过、签名校验、权限写入、应用名与图标写入、类打包、
   JSON 解析规则验证、以及用本地 HTTP 服务做的完整接口链路验证（6 个接口全部通）。
   真机上的运行时问题无法提前发现，装了有问题请告诉我机型和系统版本。

2. 图片加载的几个边界
   · 只解静态图，没有做动图（GIF / 动态 WebP）解码
   · 磁盘缓存超过 120MB 直接整体清空，没有逐个淘汰——封面可重建，不影响正确性
   · 图源要是要求登录才给图，需要在设置页填好令牌，加载器会把令牌一起发过去
   · 示例数据用的是 picsum.photos 公开占位图，离线时自动退回渐变块

3. 分页依赖接口返回的 hasMore 语义
   当前判据是「这一页返回了非空条目就算还有下一页」。如果接口在最后一页仍然
   返回满页数据（而不是空数组），会多请求一次空页才停下。
   接口若返回明确的 total / has_more 字段，可以再收紧，需要就说。

4. 背景模糊需要 Android 12 及以上
   RenderEffect 是 API 31 才有的。本 App 的 minSdk 就是 31，实际不会降级；
   代码里仍保留低版本分支，方便以后下调 minSdk。

5. 哔咔原有界面没有改
   这一点没变：哔咔的界面逻辑在 R8 混淆代码里，只有 APK 没有源码时客观上改不了。
   这个 App 是独立的新应用，名字和图标与 PicACG 相关，但不是哔咔的改造版。



九、这一版的界面结构
--------------------
顶栏   主页显示 PicACG，其余栏目显示栏目名；滚动时 40sp 收成 18sp（与 COUI HomeTitle / TopBar 一致）
底栏   主页 / 分类 / 排行 / 公告 四项
悬浮   左下角深色浅色切换，右下角设置（悬浮在底栏上方 84dp 处）

主页   大标题 → 搜索栏 → 分类胶囊行（ChipTabRow，可横滑）→ 双列封面网格
       滚到底自动加载下一页；命中屏蔽时分类下面多一行「已隐藏 N 部」
搜索   继承搜索栏 → 「搜索结果」标题 + 命中条数 → 结果列表；清空关键字回到信息流
分类   左侧分类栏 92dp（点击会重新拉数据）+ 右侧列表，顶部显示分类名与条目数
排行   分段选择器（日榜/周榜/月榜，滑动指示器）+ 名次列表，前三名名次用强调色加粗
公告   纵向卡片：标题 → 日期徽章 → 正文
详情   玻璃卡片里的封面 + 标题/作者/标签/统计，下面简介与章节目录；
       返回键是箭头图标
关于   返回箭头 → 关于此应用 → 渐变主视觉 → 作者栏（头像 + 菜菜）
       → 信息卡（应用名/版本/样式参考）→ QQ 交流群（可点击跳转）

材质   卡片与悬浮按钮均为磨砂玻璃（半透明 + 背景模糊 + 淡描边），
       背后是三团氛围光斑，滚动时会从玻璃后面透出来
滚动   所有列表都接上了 COUI 的边界回弹（阻尼感），不是系统的蓝色拉伸条

四种状态都有对应界面，不会出现空白页：
  加载中 → 转圈 + 文字
  失败   → 红色卡片写明原因 + 重试按钮
  空数据 → 「还没有内容」提示 + 说明
  正常   → 内容


十、源码
--------
PicACG-COUI-Source.zip，解压后用 Android Studio 打开 coui/ 目录。
包路径：app/src/main/java/com/picacg/coui/

改接口不需要碰源码，全部在设置页完成。
如果要在代码里写死默认实现，改这几个文件：
  data/ApiConfig.kt        配置模型 + 持久化（默认地址与路径）
  data/HttpRepository.kt   通用 HTTP 数据源 + 自适应 JSON 解析（想加字段名改这里）
  data/ContentRepository.kt AppContainer 里按配置选数据源 + 统一套屏蔽过滤
  data/BlockRules.kt       屏蔽规则引擎（关键词 / 维度 / 正则 / id）
  data/CoverLoader.kt      封面图加载器（内存 LRU + 磁盘缓存 + 降采样解码）
  CoverImage.kt            CoverBlock 组合函数与渐变占位
  AboutScreen.kt           关于页 + QQ 群跳转
  res/drawable-nodpi/avatar_cai.png  作者头像（可替换）
  res/mipmap-*            图标资源

编译环境（如果你自己重新构建）：
  AGP 9.2.0 / Gradle 9.4.1 / compileSdk 37 / JDK 20


十一、GitHub 上传状态（v1.5.0，2026-11-25）
-------------------------------------------
1. 源码仓库：https://github.com/Firewoodfire/PicACG-COUI
   main 分支为 v1.5.0 完整源码。
2. Release：https://github.com/Firewoodfire/PicACG-COUI/releases/tag/v1.5.0
   资产 PicACG-COUI-v1.5.0.apk，27,954,607 字节。
3. 资产 SHA256：
   9bb71cc885aaf39f52b59063313ab774728b0f20f2fc84baebdf68d3e5ea64cf，与本地 APK 一致。
4. 应用内「设置 → 关于 → 软件版本」读取该仓库最新 Release 的标签与说明。
5. 发布约定：Release 说明只写简介 + 3~4 条本版要点，详细记录放 CHANGELOG.md。
