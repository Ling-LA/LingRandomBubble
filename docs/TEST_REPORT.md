# 当前测试报告入口

当前版本为 0.1.49 / code 50。[96组核心](core-tests-0.1.49.txt)、[92项Android](device-safety-0.1.49.txt)通过，构建和Lint 0 errors/27 warnings通过；覆盖安装49成功。A～E及真实冷启动后原219池的H，六条各发送一次；F修改还原/G聊天离开再返回取消保留精确草稿。原最新配置与环境已恢复，本轮接收端暂无法配合，样式及重进未验证。完整范围见[49报告](VALIDATION-2026-10-02-0.1.49.md)。

[内嵌更新](embedded-update-0.1.49.txt)保留原QQ签名、其他40318项ZIP载荷；[16KiB对齐](qq-alignment-0.1.49.txt)、[模块签名](apk-signature-0.1.49.txt)及[模块与内嵌字节一致](embedded-exact-module-0.1.49.txt)已核对。这些检查不证明消息交付，本轮未完成项不记PASS。

本次修复依据是[48后续故障](collect-failure-0.1.48.txt)：心跳3321、同步异常0，普通文字仍因两个上下文同Fragment/root但contactSame=false被跳过；回复取消后drawables已经为空，aio.reply.a movement残留也导致跳过。49通过QQ当前ChatPie链读取当前Contact，历史包装仅定位Fragment、旧tuple仅作诊断计数；回复tag、复合图标和QQ只读GetReplyData均确认为空才适配。必要hook或任何当前链/查询无法确认时拒绝延后恢复。

A/B分别确认2158928/2176246；C收录开启、库分页返回后确认282，D实际引用本人C后重写输入取消引用，replyMovement残留但tag/图标/逻辑回复为空，确认2171791后恢复一次。C也正确放行同类残留；截至D计数4/4/0，实际可见四条各一条，[诊断](current-reply-0.1.49.txt)与[UI记录](message-ui-checks-0.1.49.txt)。点击前不同聊天往返后的B成功，但matchingFrames=1/historicalWrappers=0，该动作未复现旧联系人包装分支。用户[本轮接收端暂无法测试](receiver-current-0.1.49.txt)，49样式及重进保持未验证，历史48确认不替代。

随后E经主最近会话列表进入好友聊天未发送、再返回测试群，现场contexts4/matchingFrames3/historicalWrappers2/staleContacts1/unread0，当前链与实际按钮根一致；04:44:09确认2116350后恢复一次。两次物理点击日志只增加一次延后/恢复，E可见一条，累计5/5/0，[目标分支证据](current-stale-0.1.49.txt)。旧联系人包装冲突已现场复现通过；未知链/缺失hook故障仍未注入。

F点击后删除最后7并输入7恢复原文字，G点击后BACK离开Main AIO到最近会话再回同测试，均取消尚未提交切换、不自动发送或重试；实际消息0条/精确草稿保留，累计7/5/2，[诊断](current-guards-0.1.49.txt)与[UI](message-ui-checks-0.1.49.txt)。G不是HOME后台实测，全部setter/onNewIntent/hidden/pause/destroy分支仍未单独覆盖。

真实[LaunchState:COLD](qq-cold-0.1.49.txt)后精确219候选/60秒/automatic=false/perMessage=true/collect=false读回，H在04:49:43确认2178074并恢复一次、实际一条；新进程1/1/0，心跳113/sync0，[最终诊断](current-final-0.1.49.txt)。冷启动前7/5/2与冷启动后1/1/0合计8/6/2，不是单一进程计数。

[配置恢复](config-restored-0.1.49.txt)与最新219候选备份SHA-256一致；库2644项、原2449勾选不变，23新增未勾选保留。独立module/test包不存在、USB常亮仍原值0、QQ9.3.50未卸载或清数据，[环境](device-environment-0.1.49.txt)。完全无独立应用的[QQ设置仅一条Ling入口](embedded-entry-0.1.49.txt)，原生面板实际可用并最终留给用户。其他插件仅确认ZIP载荷不变，不泛称其运行UI全部复核；当前装扮由用户原已开启逐消息管理，未宣称固定恢复某款。

[48历史报告](VALIDATION-2026-10-02-0.1.48.md)保留当时84核心、92Android、短时发送及接收端结果，另保留其后旧Contact冲突失效证据。49未知链/回复查询、缺失hook、接口超时、账号/代次变化或同步故障未注入；HOME后台、锁屏、全部生命周期分支、新60秒定时周期与完整1800秒低频周期未本轮复测。长期稳定或风控安全不能从短时测试推断。

[47历史报告](VALIDATION-2026-10-02.md)、[46](VALIDATION-2026-10-01.md)、[45](VALIDATION-2026-09-30.md)及[45双端记录](RECEIVER-RESULT-2026-09-30.md)保留各自范围；旧版本的通过不能代替本轮验证。纯Java身份fixture、Android配置、真实QQ发送及接收端观察分别记录，不能相互替代。旧消息属性替换策略仍在生产入口关闭。
