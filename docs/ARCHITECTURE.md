# 0.1.50 架构

生产入口仅对 QQ 9.3.50 主进程启用账号装扮路径。SettingInject 适配该版本的 CharSequence 条目和分组构造器，在 QQ 设置的“模块”分组插入“Ling 随机气泡”，直接打开进程内 SettingsPanel。可复用或自建分组，不保证与 QFun 同组，最终保留一条入口。QQ 原生 Dialog/View 构成设置、分页气泡库和账号装扮配置；内嵌加载无需外部 Activity 或独立应用。

AccountLibrary 在 QQ 私有目录按账号保存气泡库；HostSettings 保存账号装扮配置、控制来源及待同步标记。AccountRef 优先读取实际 MobileQQ runtime；运行时存在但账号未知时不借用历史账号。界面捕获打开时的账号，提交前再次核对，阻止旧界面写入新账号。定时和逐消息均默认关闭，仅用户明确保存才启用；默认配置、采集和选择不会启用。

库收录通过 addAll 批量查重，单文档处理整批，最多保存一次；没有新增或内容相同则不写盘。AtomicFile 的 startWrite/finishWrite/failWrite 和 openRead 恢复保留可回读的旧库，避免先删除旧文件的保存方式。后台批量收录和元数据同步由 LingBubble-Sync 执行，safeSync 捕获异常，下一周期仍运行。

HostBridge 的 LingBubble-Timer 独立每秒读取本机装扮控制，不持有气泡库锁、不等待 Binder 同步。控制发布使用独立锁；AccountDecoration 接收、发起设置前和发送恢复前核对实时账号及配置 generation，旧快照不能恢复旧配置。运行时使用 elapsedRealtime 单调时钟；冷启动读取按账号保存的墙钟时间，回拨时立即重新计算，避免永久等待。持久化冷却在请求前保存，避免重启绕过冷却。

定时轮换只在 QQ 前台、屏幕点亮且解锁时发起。配置改变、后台或锁屏取消尚未提交的请求，回到前台后可继续，不把暂停写成永久失败；旧版因后台误记的阻断状态有迁移处理。查询暂错使用退避冷却，连续三次才记录当前代次暂停并要求重新保存；有效预检重置暂错计数。响应有效且款式匹配、但未确认权益时，把目标加入当前 generation 的 unavailable 集合并跳过，不调用 SET；新 generation 清空该集合。定时跳过仍占一次尝试，下一个目标等待配置间隔，实际变更间隔可能更长。逐消息一次等待最多核对三款，仍共享原 30 秒发送期限；候选耗尽时取消恢复，提示调整配置。业务拒绝、响应结构异常或提交结果不能确认仍会暂停当前代次，由用户重新保存发起。

AccountDecoration 以主线程状态机持有单个 Operation，强持有当前 handler 至结束，弱引用观察值失效时重新向当前 runtime 获取 SVIPHandler。手动命令一次消费、30 秒有效；同步镜像不重新发送本机手动命令，同代次镜像保留尚未消费且未过期的命令。重复 API 回调通过一次性交付保护，不能导致重复 SET；已提交请求结果不确定时仅作只读详情核对，不自动重新提交。

UiTapGate 的逐消息模式与定时开关独立，默认关闭。仅拦截原生普通文字（含已适配的 QQ 小表情）的物理发送按钮，保留一次用户点击，先切换账号装扮，服务器当前款式及 QQ 缓存均确认后再恢复一次原按钮点击。输入内容/修订、会话、账号、配置代次、当前前台与解锁状态都须仍匹配。消息恢复最多等待 30 秒；过期或环境变化取消恢复并保留输入框，已提交的装扮 Operation 可继续至 65 秒看门狗限时核对，但晚到回调不能重新发送。附件、回复、@、Enter 键与脚本发送沿用 QQ 原流程，不宣称这些路径的逐消息效果。逐消息不等待定时模式的配置间隔；单请求串行和最小尝试间隔仍生效，查询暂错同样退避，因此会比定时模式增加商城请求。0.1.47曾完成D/E正向及F/G/H保护的短时实测，之后实际运行出现matches=multiple拒绝，历史结果不覆盖本次修复；旧日志未记录两包装身份，不能确认当时两者等价。

0.1.48曾按Fragment/View根身份及会话元组合并包装，但后续现场证实同一真实Fragment/root会保留旧Contact：两个包装contactSame=false仍令普通文字被multiple判定跳过。49改用QQ权威活动链，历史AIO包装仅定位唯一的实际Fragment及包含按钮的根视图，不读取历史tuple作为授权依据。由Fragment.chatPieManager读取当前ChatPie，要求manager.e()==manager.b；ChatPie.k()==ChatPie.f==Fragment.currentContext.get()；该context.c()仍是当前Fragment，context.g()==ChatPie.l()==ChatPie.h。ActiveConversationChain负责非null和对象身份比较；实际ChatPie.d根视图必须位于Fragment根内、包含按钮、已附着且同窗口。当前Contact只从这条链的当前param读取，未知类型、字段、会话或不一致指针全部拒绝。诊断模式可统计historicalWrappers/staleContacts/unreadHistorical，不参与当前判定，不公开会话标识。

Attempt弱保存当前Fragment、manager、pie、context、param和真实ChatPie根；每次valid都重新解析QQ当前链并对比所有身份及type/peer/guild，仍保留窗口、按钮、监听器、Editable、文本修订、账号、配置代次和前台保护。ChatFragment.access$setCurrentContext$p在改为不同或空上下文时永久取消pending；onNewIntent(Bundle)、onPause、onDestroyView以及onHiddenChanged(true)也永久取消。恢复原会话或指针不能撤销cancelled。只在全部必要hook注册成功后发布ready，部分注册失败保持原QQ发送，本进程不重试注册；历史包装构造时间不作为会话切换证据，不因旁路新建包装取消pending。QQ实际根和当前链负责授权，不以最新包装或临时LifecycleRegistry状态替代。

QQ取消引用回复会清除gja tag和compound drawables，但可留下aio.reply.a movement。49起只把movement用作诊断；plainEditor要求gja资源存在、tag为空、绝对/相对全部复合图标为空，50通过ComposerTextGate分类普通文字及QQ小表情span。通过当前binding.context.e().k(InputReplyMsgIntent$GetReplyData.d)只读查询，响应必须为确切aio.input.reply.a$a且a()==null。active回复或查询未知在defer时保留原QQ发送，在等待valid时取消恢复；发送恢复前再次查询，不清除tag、图标、movement、逻辑回复或输入。实际附件、@和其他未适配路径仍沿用QQ原流程。

ComposerTextGate仅接受精确com.tencent.mobileqq.text.style.EmoticonSpan，排除其子类、COMPOSING、无效范围和未知绘制span；确切类的合法零长度残留可忽略，不移除原span。等待快照同时记录span对象身份、范围、flags和index/emojiType/size三个int字段，恢复前再次核对，防止未触发SpanWatcher的原地字段变化误恢复。QQ小表情适配不扩展至图片、@、引用或其他ReplacementSpan。

OfficialBubbleApi 从商城成功正常操作学习当前账号的有限参数：气泡设置模板、详情查询 filter/qua、SDK 封装元数据，保存在 QQ 私有目录并按账号分隔。不读取 TicketManager 登录密钥，不保存网络认证封装或用户消息。商城协议与 0.1.45/0.1.46 相同：QQKuiklyPlatformApi.sendPbRequest 调用 0x942d_0 设置、0x9716_0 详情，WebSsoBody 由 QQ 类序列化，登录认证由 QQ SDK 处理。

详情返回 appId/itemId 匹配、authret=0、payForbidden=false 后才提交；设置业务 ret=0 后再次查询同款 isSetup=true，再更新 SVIPHandler 正常缓存并核对字体。缺失或未知标志不视为成功，数字 0/1 与布尔标志严格解析。字体状态改变会停止本次缓存刷新。SET 不确定时最多追加只读核对，避免重复提交。

HostRuntime.ready 始终 false：旧 SendPolicy 和测试只保留历史回归，生产 send hook 不修改消息参数。逐消息恢复仅对应用户的一次物理点击；不调用脚本发送、撤回或复读 API，不主动生成新消息。QFun 与其他内嵌模块保持原载荷。

Repository/ConfigProvider 管理可选独立应用配置，Provider 校验调用 UID，只允许模块自身和 QQ；模块自身不能消费 QQ 手动命令。账号选择、编辑和响应作为同一事务处理。仅在 Provider 确认保存后清除待同步标记；账号或编辑代次变更后丢弃旧响应。普通采集不会改写开关或勾选。来自独立应用的控制要求 providerOwner 与当前控制账号匹配且最近成功同步不足 15 秒，不能借用其他账号的成功心跳；失联或同步挂起超过期限会暂停。用户明确保存为 QQ 本机控制后不依赖该应用。

为限制 Binder 事务大小，气泡库 JSON 超过 131,072 字符时使用 controlsOnly：不发送整库或 hostConfig，Provider 不导入或返回完整库，仅同步装扮及有限诊断。完整大库仍在 QQ 内管理，独立应用不会完整同步这一大库；未同步的库待确认标记不会被误清除。原生库使用回收列表、20 项分页、搜索与一次提交的批量选择；全选覆盖搜索外及所有页，主气泡 ID 去重后填入装扮配置。

49历史验证：96核心（新增12组当前链/等待身份检查）、92 Android、构建及Lint 0 errors/27 warnings通过，覆盖安装49成功。A～E及真实冷启动后原219池H，六条服务器/缓存确认后实际各一条；E有3匹配包装/2历史包装/1旧Contact仍由当前链授权，双击只一条。F修改还原、G聊天离开到最近会话再返回取消保留草稿；冷启动前7/5/2、后1/1/0，合计8/6/2。原219/60秒/定时关/逐消息开/收录关精确读回，库2644/原2449勾选、23新增未勾选保留；独立module/test不存在、USB仍0、QQ未清数据，原生设置单入口可用。49接收端不可用；HOME后台/锁屏、全部生命周期分支、未知链/查询/接口故障及新定时周期未复测，详[49历史报告](VALIDATION-2026-10-02-0.1.49.md)。45～48报告及公开脱敏46～48记录与后续故障保留，不能替代50；fixture不证明未实测QQ边界。公开日志不含正文、账号或认证；私人QQ APK、反编译资料和UI排除在源码包外。

50当前验证：96核心、122 Android（新增30项生产分类及快照检查）、构建和Lint 0 errors/30 warnings通过。小表情/随后纯文字/双击正向及两款接收端发送合计六条各一条，修改还原与HOME两次取消保留草稿；两个发送进程合计8/6/2。接收端仅有用户文字确认“两款不同，重进后仍保留”，未读取截图。最终第二次COLD后恢复原219候选/60秒/原开关，库2644/勾选2449保持；最终新进程0/0/0、心跳138/sync0，仅配置与入口检查，没有新增测试发送。独立module/test不存在、USB原值0已核对。详[50报告](VALIDATION-2026-10-02-0.1.50.md)；短时测试不证明所有随机失效、长期稳定或风控安全。
