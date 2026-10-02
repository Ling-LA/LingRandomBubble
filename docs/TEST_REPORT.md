# 当前测试报告入口

当前版本为 **0.1.52 / code 53**。最终 R2 [96组核心](core-tests-0.1.52.txt)/[214项Android](device-safety-0.1.52.txt)通过，[构建/Lint](android-build-0.1.52.txt)0 errors/28 warnings，已更新至实际LSPatch集成QQ9.3.50。冷启动普通文字、引用及图片后普通文字通过，群/私聊图片与合成收藏表情均直接发送；选图＋文字交QQ原生发送，等待中选图再清空永久取消。四条气泡正向与一次取消，发送进程[5/4/1、心跳788/sync0](media-final-run-0.1.52.txt)。[接收端截图实际读取](receiver-media-0.1.52.txt)和[用户重进“全部保留”确认](receiver-retention-0.1.52.txt)分开记录，范围见[52报告](VALIDATION-2026-10-03-0.1.52.md)。

本轮旧版媒体异常在更新前重启51后已恢复，[原始对照](media-failure-0.1.51.txt)不证明根因或永久修复。52加固当前选图/非文字按钮与旧等待隔离；首候选冷启动适配失败已在R2纠正，首候选UI记录不算最终气泡通过。[实际COLD后的最终恢复](config-restored-0.1.52.txt)精确保留最新224候选/60秒/perMessage=true、automatic/collect=false、库及勾选均2644；[手机内嵌模块](embedded-exact-module-0.1.52.txt)与发布APK字节一致。[最后检查进程](current-final-0.1.52.txt)0/0/0仅恢复检查，独立应用/test已移除、USB原值0保持、仅清理本轮合成媒体。短时验证不代表长期稳定或风控安全。

## 0.1.51 历史摘要

以下保留51当时的引用验证结果和限制，不代替52验证。

0.1.51 / code 52：[96组核心](core-tests-0.1.51.txt)、[196项Android](device-safety-0.1.51.txt)通过；[构建和Lint](android-build-0.1.51.txt)为0 errors/28 warnings，[QQ覆盖安装](embedded-update-0.1.51.txt)成功。真实QQ普通/引用正向六条各一条，关闭引用的一次等待永久取消、未提交SET且0条/精确草稿保留。单发送进程最终**7/6/1、心跳3374/sync0**，[诊断](reply-final-run-0.1.51.txt)及[UI记录](message-ui-checks-0.1.51.txt)。接收端两款不同、引用卡片保留及重进保持来自用户回复“是”，没有读取接收端截图。范围和限制见[51报告](VALIDATION-2026-10-02-0.1.51.md)。

51原生引用同时核对tag/顶部图标/GetReplyData逻辑数据及等待快照，见[QQ结构](qq-reply-schema-0.1.51.txt)。A引用自己的source，B引用A双击只一条，C引用其他成员指定源并经QQ原生成员选择加入@，引用与@保留；该QQ设置没有自动@，不写自动添加。D/F分别为引用@和关闭引用之后普通文字。第一次[实际COLD](qq-cold-0.1.51.txt)后[初始配置](config-initial-0.1.51.txt)精确读回原219候选/60秒/perMessage=true；[入口0/0/0](current-entry-0.1.51.txt)为发送前快照。引用回切、h.m重绘、小表情加引用、普通无引用@原路径、后台/锁屏和新定时周期等未本轮实测；50或更早结果不补足51范围。

临时测试后的[第二次实际COLD](qq-cold-final-0.1.51.txt)完成[最终精确恢复](config-restored-0.1.51.txt)：原219 IDs/60秒/automatic=false/perMessage=true/collect=false，库2644/勾选2449保持。[最后检查进程](current-final-0.1.51.txt)0/0/0、心跳347/sync0仅配置与原生面板检查，没有新增发送，和之前发送进程7/6/1区分。[设备](device-final-0.1.51.txt)确认独立模块/test应用不存在、USB原值0、QQ未卸载/清数据，最终留下原生面板；当前装扮2173887由原开启逐消息模式管理。90条fatal均为09-30历史，本轮10-02 20:06:49以来无新fatal，不称整个buffer无崩溃或长期稳定。

## 0.1.50 历史摘要

以下保留50当时的结果和限制，不代替51验证。

0.1.50 / code 51：[96组核心](core-tests-0.1.50.txt)、[122项Android](device-safety-0.1.50.txt)通过，含30项生产文字分类与快照回归；[构建和Lint](android-build-0.1.50.txt)为0 errors/30 warnings，手机覆盖安装50成功。完整范围见[50报告](VALIDATION-2026-10-02-0.1.50.md)。

[四条顺序发送](current-sequence-0.1.50.txt)涵盖QQ小表情、之后的纯文字和双击；[编辑还原与HOME取消](current-guards-0.1.50.txt)均未自动发送、保留精确草稿。冷启动后的[两款接收端测试](current-receiver-sends-0.1.50.txt)各发送一次；两个发送进程合计延后/恢复/取消 **8/6/2**，六条正向各可见一条、两次取消各0条。用户文字确认[“两款不同，重进后仍保留”](receiver-current-0.1.50.txt)；没有读取接收端截图，不能称为截图核验。

[内嵌更新](embedded-update-0.1.50.txt)保留原QQ签名及其他40318项ZIP载荷；[16KiB对齐](qq-alignment-0.1.50.txt)、[模块签名](apk-signature-0.1.50.txt)及[内嵌模块字节一致](embedded-exact-module-0.1.50.txt)已核对。第二次[实际COLD启动](qq-final-cold-0.1.50.txt)后，[最终恢复读回](config-restored-0.1.50.txt)确认原219款/60秒、automatic=false/perMessage=true/collect=false，库2644项及2449勾选保持。最终新进程[0/0/0、心跳138/sync0](current-final-0.1.50.txt)仅用于配置与入口检查，未再次发送测试消息；[环境核对](device-environment-0.1.50.txt)确认独立模块和测试应用不存在、USB常亮为原值0，QQ原生面板留在前台。

50支持精确QQ小表情类的输入并保护span快照；本轮短时测试不能证明所有随机失效、长期稳定或风控安全。锁屏、全部生命周期分支、新60秒及完整1800秒定时周期等未在50全面复测；权益排除是既有当前QQ进程行为，重启或保存配置后重新核对，不删库或更改勾选。

## 0.1.49 历史摘要

以下保留49当时的结果和限制，不代替51验证。

0.1.49 / code 50：[96组核心](core-tests-0.1.49.txt)、[92项Android](device-safety-0.1.49.txt)通过，构建和Lint 0 errors/27 warnings通过；覆盖安装49成功。A～E及真实冷启动后原219池的H，六条各发送一次；F修改还原/G聊天离开再返回取消保留精确草稿。原最新配置与环境已恢复，本轮接收端暂无法配合，样式及重进未验证。完整范围见[49报告](VALIDATION-2026-10-02-0.1.49.md)。

[内嵌更新](embedded-update-0.1.49.txt)保留原QQ签名、其他40318项ZIP载荷；[16KiB对齐](qq-alignment-0.1.49.txt)、[模块签名](apk-signature-0.1.49.txt)及[模块与内嵌字节一致](embedded-exact-module-0.1.49.txt)已核对。这些检查不证明消息交付，本轮未完成项不记PASS。

本次修复依据是[48后续故障](collect-failure-0.1.48.txt)：心跳3321、同步异常0，普通文字仍因两个上下文同Fragment/root但contactSame=false被跳过；回复取消后drawables已经为空，aio.reply.a movement残留也导致跳过。49通过QQ当前ChatPie链读取当前Contact，历史包装仅定位Fragment、旧tuple仅作诊断计数；回复tag、复合图标和QQ只读GetReplyData均确认为空才适配。必要hook或任何当前链/查询无法确认时拒绝延后恢复。

A/B分别确认2158928/2176246；C收录开启、库分页返回后确认282，D实际引用本人C后重写输入取消引用，replyMovement残留但tag/图标/逻辑回复为空，确认2171791后恢复一次。C也正确放行同类残留；截至D计数4/4/0，实际可见四条各一条，[诊断](current-reply-0.1.49.txt)与[UI记录](message-ui-checks-0.1.49.txt)。点击前不同聊天往返后的B成功，但matchingFrames=1/historicalWrappers=0，该动作未复现旧联系人包装分支。用户[本轮接收端暂无法测试](receiver-current-0.1.49.txt)，49样式及重进保持未验证，历史48确认不替代。

随后E经主最近会话列表进入好友聊天未发送、再返回测试群，现场contexts4/matchingFrames3/historicalWrappers2/staleContacts1/unread0，当前链与实际按钮根一致；04:44:09确认2116350后恢复一次。两次物理点击日志只增加一次延后/恢复，E可见一条，累计5/5/0，[目标分支证据](current-stale-0.1.49.txt)。旧联系人包装冲突已现场复现通过；未知链/缺失hook故障仍未注入。

F点击后删除最后7并输入7恢复原文字，G点击后BACK离开Main AIO到最近会话再回同测试，均取消尚未提交切换、不自动发送或重试；实际消息0条/精确草稿保留，累计7/5/2，[诊断](current-guards-0.1.49.txt)与[UI](message-ui-checks-0.1.49.txt)。G不是HOME后台实测，全部setter/onNewIntent/hidden/pause/destroy分支仍未单独覆盖。

真实[LaunchState:COLD](qq-cold-0.1.49.txt)后精确219候选/60秒/automatic=false/perMessage=true/collect=false读回，H在04:49:43确认2178074并恢复一次、实际一条；新进程1/1/0，心跳113/sync0，[最终诊断](current-final-0.1.49.txt)。冷启动前7/5/2与冷启动后1/1/0合计8/6/2，不是单一进程计数。

[配置恢复](config-restored-0.1.49.txt)与最新219候选备份SHA-256一致；库2644项、原2449勾选不变，23新增未勾选保留。独立module/test包不存在、USB常亮仍原值0、QQ9.3.50未卸载或清数据，[环境](device-environment-0.1.49.txt)。完全无独立应用的[QQ设置仅一条Ling入口](embedded-entry-0.1.49.txt)，原生面板实际可用并最终留给用户。其他插件仅确认ZIP载荷不变，不泛称其运行UI全部复核；当前装扮由用户原已开启逐消息管理，未宣称固定恢复某款。

[48历史报告](VALIDATION-2026-10-02-0.1.48.md)保留当时84核心、92Android、短时发送及接收端结果，另保留其后旧Contact冲突失效证据。49未知链/回复查询、缺失hook、接口超时、账号/代次变化或同步故障未注入；HOME后台、锁屏、全部生命周期分支、新60秒定时周期与完整1800秒低频周期未本轮复测。长期稳定或风控安全不能从短时测试推断。

[47历史报告](VALIDATION-2026-10-02.md)、[46](VALIDATION-2026-10-01.md)、[45](VALIDATION-2026-09-30.md)及[45双端记录](RECEIVER-RESULT-2026-09-30.md)保留各自范围；旧版本的通过不能代替本轮验证。纯Java身份fixture、Android配置、真实QQ发送及接收端观察分别记录，不能相互替代。旧消息属性替换策略仍在生产入口关闭。
