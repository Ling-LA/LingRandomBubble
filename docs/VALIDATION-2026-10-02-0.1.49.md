# 0.1.49 验证报告

日期：2026-10-02。版本：0.1.49 / code 50。上游提交：512dbdafe1dcc0e6a7223db689ba1ab925d144e9。目标：Android16 / QQ9.3.50 / LSPatch1.2集成模式。

本次针对48后续真实故障：同Fragment/root的两个包装保存不同联系人元组，旧扫描判multiple而跳过；回复取消后图标已清除，但aio.reply.a movement残留仍误拒绝普通文字，见[故障记录](collect-failure-0.1.48.txt)。49由Fragment的当前ChatPieManager→当前ChatPie→当前AIOContext/会话参数读取当前Contact，同时核对getter/字段、Fragment当前弱指针及实际包含发送按钮的ChatPie根视图。历史包装只定位真实Fragment，历史tuple读取仅用于诊断，不参与当前授权。

等待恢复时每次重新解析当前权威链，弱链接缺失或任一当前身份/元组不匹配即拒绝。QQ当前弱指针切换、onNewIntent(Bundle)、onHiddenChanged(true)、onPause及onDestroyView观察永久取消等待；返回原会话也不能恢复旧点击。必须全部必要观察hook注册成功才启用该适配，部分失败保留QQ原发送。回复取消后核对gja资源存在、tag空、全部复合图标空以及QQ只读GetReplyData返回确切空回复；查询未知拒绝，不主动修改QQ回复状态。正常商城协议、权益核验、账号隔离和控制来源未改变。

本轮96组核心、92项Android、构建/Lint、覆盖安装、内嵌签名/载荷、16KiB对齐及模块字节一致检查均完成。A～E以及真实冷启动后原219候选池的H，六条均经服务器及缓存确认后只恢复一次、实际各一条。C收录/库分页、D取消引用残留movement、E旧联系人冲突/双击均有现场证据；F修改还原、G离开聊天再返回取消且实际0条/精确草稿保留。冷启动前7/5/2，冷启动后H为1/1/0，两进程合计8/6/2，不是单一进程计数。原219候选/60秒/模式和勾选已精确恢复，临时应用与USB状态已核对。接收端本轮暂不能配合，样式及重进未验证；后台HOME、锁屏、全部生命周期及新的定时周期也未本轮复测。48历史报告保持原样，不代替49。

测试前最新备份为219个候选、秒60、automatic=false、perMessage=true、collect=false，库2621项/2449项勾选。已按本次备份精确恢复，不使用历史209/213候选覆盖用户后续编辑，收录新增未勾选库项保留。

| 检查 | 本轮结果及证据 |
|---|---|
| 核心Java回归 / 当前会话权威链 | 96组通过，新增12组覆盖当前owner链、旧/缺失/不同身份拒绝、等待身份变化和一次消费，[记录](core-tests-0.1.49.txt)。只证明fixture逻辑，不代替真实QQ链或hook现场验证 |
| 模块 / Android测试APK构建 | BUILD SUCCESSFUL，assembleDebug/assembleDebugAndroidTest/lintDebug；本轮版本49/code50 |
| Android Lint | 0 errors/27 warnings；较48新增getIdentifier的DiscouragedApi警告，lintDebug任务成功 |
| Android配置 / 安全检查 | 本轮完整92项实机通过，无FAIL、INSTRUMENTATION_CODE:-1，[记录](device-safety-0.1.49.txt)。测试隔离偏好在finally恢复，临时独立module/test已移除；QQ未卸载/清数据。配置fixture不代表QQ消息交付 |
| 内嵌原签名 / 其他ZIP载荷 / 覆盖安装 | 原签名匹配，其他40318项载荷不变，[记录](embedded-update-0.1.49.txt)；覆盖安装49成功，实际诊断运行49 |
| QQ 16KiB对齐 / 模块签名 | zipalign -c -P 16 -v 4显式PASS，[对齐](qq-alignment-0.1.49.txt)；模块v2签名验证通过，[签名](apk-signature-0.1.49.txt) |
| 模块与内嵌字节一致 | 153237 bytes，SHA-256 bc6a37d25191ba13c1f28ab9b77c14689fd27dbf43d4bcd02909e5d50b34d213，[记录](embedded-exact-module-0.1.49.txt)；不以此判定消息交付通过 |
| 无独立应用入口 / 原生面板 | 独立module/test包均不存在，[环境](device-environment-0.1.49.txt)；QQ设置实际仅一条Ling入口，原生面板可打开且最终留给用户，[入口证明](embedded-entry-0.1.49.txt)。库分页返回、收录保存及冷启动后模式/库读回已核对，[配置证明](config-restored-0.1.49.txt)。其他模块仅40318项载荷保持，不泛称全部插件运行UI已复核 |
| 当前权威链 / 旧联系人包装 | 现场目标分支已复现：E在04:44:08出现contexts=4、matchingFrames=3、historicalWrappers=2、staleContacts=1、unreadHistorical=0，source=currentChatPie/chainSame=true/activeRootContainsButton=true。历史旧Contact未阻断当前授权，服务器2116350确认后恢复一次、UI可见一条，[诊断](current-stale-0.1.49.txt)与[UI](message-ui-checks-0.1.49.txt)。必要观察hook已就绪；未知链/缺失hook故障仍未实机注入 |
| 普通文字 / 收录与面板往返 | A跳过未确认权益2137229后确认2158928，B确认2176246；C收录开启、库分页返回后确认282，D确认2171791。A/B/C/D各恢复一次、可见各一条，截至D计数4/4/0，[诊断](current-reply-0.1.49.txt)与[UI记录](message-ui-checks-0.1.49.txt)。未因最早异步前UI断言而记失败 |
| 不同聊天往返后普通文字 | 经搜索/群资料进入授权好友聊天且未发送，再返回测试群，B正常单次确认；contexts=4、matchingFrames=1、historicalWrappers=0，[记录](current-crosspeer-0.1.49.txt)。发生在点击前，不是等待期间切换保护，也未复现旧联系人包装 |
| 回复取消后普通文字 | D实际引用本人C后重写输入取消引用，残留replyMovement；逻辑核对replyTag=false/drawables=false/replyData=false，04:42:35确认2171791并恢复一次，实际可见一条。C也在同类残留被正确放行，[诊断](current-reply-0.1.49.txt)。有效回复发送和未知查询故障尚未实机注入 |
| 快速双击 / 修改还原 | E两次物理点击只增加1次延后/恢复，实际一条；F点击后删除最后7再输入7，修订已变化仍取消，精确草稿保留/实际0条。[E](current-stale-0.1.49.txt)、[保护](current-guards-0.1.49.txt)与[UI](message-ui-checks-0.1.49.txt)，累计7/5/2 |
| 等待期间会话离开再返回 | G点击后BACK离开Main AIO到最近会话，再回同测试群，实际0条/精确草稿保留；日志环境已变化，取消尚未提交切换，未自动发送或重试，[保护](current-guards-0.1.49.txt)与[UI](message-ui-checks-0.1.49.txt)。不是HOME后台实测，未覆盖全部setter/onNewIntent/hidden/pause/destroy分支 |
| 接收端样式 / 退出重进 | 用户本轮回复接收端暂无法测试，[记录](receiver-current-0.1.49.txt)。A～E/H接收端样式及重进均未验证；48A/B确认属历史结果，不代替49 |
| 真实冷启动 / 原池配置与发送 | LaunchState:COLD，[启动](qq-cold-0.1.49.txt)；当前49QQ本机来源，[冷启动诊断](current-cold-0.1.49.txt)。精确219IDs/60秒/原模式及库2644/2449读回；H在04:49:43确认2178074并恢复一次、实际一条，新进程1/1/0，心跳113/sync0，[最终诊断](current-final-0.1.49.txt)与[UI](message-ui-checks-0.1.49.txt) |
| 跨进程发送计数 | 冷启动前7/5/2，冷启动后H为1/1/0，合计延后/恢复/取消8/6/2；A～E/H共六条实际各一条，F/G共两条取消保留精确草稿 |
| 定时 / 大库 / 低频完整周期 | 本轮没有定时周期或大库阻塞压力测试；1800秒完整周期仍未验证 |
| 配置、库和临时安装 / USB恢复 | 真实冷启动后精确219候选与备份一致，SHA-256 9d84c036a334f49e544d00333d5ef23d60c23bdfc6ab35c82c87427a6678b426；秒60/automatic=false/perMessage=true/collect=false持久化，库2644项/原2449勾选不变，23新增未勾选保留，[证明](config-restored-0.1.49.txt)。独立module/test已移除、USB常亮仍原值0、QQ9.3.50未卸载/清数据，[环境](device-environment-0.1.49.txt)。当前装扮由原已开启逐消息管理，不宣称固定恢复某款 |
| 复杂接口 / 生命周期故障 / 账号变化 / 同步故障 | 未注入未知链/部分hook注册失败、未知回复查询、接口超时/重复回调、账号/代次变化或Binder失联；HOME后台、锁屏及全部setter/onNewIntent/hidden/pause/destroy分支未逐项实测。核心/历史结果不能替代这些现场分支 |

历史：[48报告](VALIDATION-2026-10-02-0.1.48.md)、[47报告](VALIDATION-2026-10-02.md)、[46报告](VALIDATION-2026-10-01.md)、[45报告](VALIDATION-2026-09-30.md)。源码包保留公开脱敏46～48记录，包含47/48后续故障；新证据使用-0.1.49.txt后缀。私人QQ APK、正文、会话标识、认证及UI图片不进入源码包。

定时和逐消息默认关闭，均影响整个账号并增加正常商城请求。短时测试不能证明长期稳定或低风控风险。接收端、真实QQ、Android配置和纯Java检查不能相互替代。

GitHub 发布准备：公开版本统一 UTF-8 文本 LF 行尾，源码清单和 ZIP 已重新生成；实测 APK 字节保持不变。仅脱敏历史记录中的本机路径和接收端昵称，历史测试结果、计数及限制未修改。
