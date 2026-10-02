# 0.1.52 架构

生产入口仅对 QQ 9.3.50 主进程启用账号装扮路径。SettingInject 适配该版本的 CharSequence 条目和分组构造器，在 QQ 设置的“模块”分组插入“Ling 随机气泡”，直接打开进程内 SettingsPanel。可复用或自建分组，不保证与 QFun 同组，最终保留一条入口。QQ 原生 Dialog/View 构成设置、分页气泡库和账号装扮配置；内嵌加载无需外部 Activity 或独立应用。

AccountLibrary 在 QQ 私有目录按账号保存气泡库；HostSettings 保存账号装扮配置、控制来源及待同步标记。AccountRef 优先读取实际 MobileQQ runtime；运行时存在但账号未知时不借用历史账号。界面捕获打开时的账号，提交前再次核对，阻止旧界面写入新账号。定时和逐消息均默认关闭，仅用户明确保存才启用；默认配置、采集和选择不会启用。

库收录通过 addAll 批量查重，单文档处理整批，最多保存一次；没有新增或内容相同则不写盘。AtomicFile 的 startWrite/finishWrite/failWrite 和 openRead 恢复保留可回读的旧库，避免先删除旧文件的保存方式。后台批量收录和元数据同步由 LingBubble-Sync 执行，safeSync 捕获异常，下一周期仍运行。

HostBridge 的 LingBubble-Timer 独立每秒读取本机装扮控制，不持有气泡库锁、不等待 Binder 同步。控制发布使用独立锁；AccountDecoration 接收、发起设置前和发送恢复前核对实时账号及配置 generation，旧快照不能恢复旧配置。运行时使用 elapsedRealtime 单调时钟；冷启动读取按账号保存的墙钟时间，回拨时立即重新计算，避免永久等待。持久化冷却在请求前保存，避免重启绕过冷却。

定时轮换只在 QQ 前台、屏幕点亮且解锁时发起。配置改变、后台或锁屏取消尚未提交的请求，回到前台后可继续，不把暂停写成永久失败；旧版因后台误记的阻断状态有迁移处理。查询暂错使用退避冷却，连续三次才记录当前代次暂停并要求重新保存；有效预检重置暂错计数。响应有效且款式匹配、但未确认权益时，把目标加入当前 generation 的 unavailable 集合并跳过，不调用 SET；新 generation 清空该集合。定时跳过仍占一次尝试，下一个目标等待配置间隔，实际变更间隔可能更长。逐消息一次等待最多核对三款，仍共享原 30 秒发送期限；候选耗尽时取消恢复，提示调整配置。业务拒绝、响应结构异常或提交结果不能确认仍会暂停当前代次，由用户重新保存发起。

AccountDecoration 以主线程状态机持有单个 Operation，强持有当前 handler 至结束，弱引用观察值失效时重新向当前 runtime 获取 SVIPHandler。手动命令一次消费、30 秒有效；同步镜像不重新发送本机手动命令，同代次镜像保留尚未消费且未过期的命令。重复 API 回调通过一次性交付保护，不能导致重复 SET；已提交请求结果不确定时仅作只读详情核对，不自动重新提交。

UiTapGate 的逐消息模式与定时开关独立，默认关闭。51适配普通文字、QQ 自带小表情及原生引用回复文字的物理发送按钮，引用内精确QQ原生@随引用处理。保留一次用户点击，先切换账号装扮，服务器当前款式及 QQ 缓存均确认后再恢复一次原按钮点击。输入内容/修订、引用快照、会话、账号、配置代次、当前前台与解锁状态都须仍匹配。消息恢复最多等待 30 秒；过期或环境变化取消恢复并保留输入框，已提交的装扮 Operation 可继续至 65 秒看门狗限时核对，但晚到回调不能重新发送。图片消息、表情包图、附件、无引用@、Enter 键与脚本发送沿用 QQ 原流程，不宣称这些路径的逐消息效果。逐消息不等待定时模式的配置间隔；单请求串行和最小尝试间隔仍生效，查询暂错同样退避，因此会比定时模式增加商城请求。51引用文字/引用内原生@及一次逻辑清除已短时实测，未实测的引用回切、重绘、小表情加引用等不由架构描述补足。0.1.47曾完成D/E正向及F/G/H保护的短时实测，之后实际运行出现matches=multiple拒绝，历史结果不覆盖本次修复；旧日志未记录两包装身份，不能确认当时两者等价。

52加强原QQ媒体路径和旧文字等待的隔离。相同send_btn资源ID不等于同一种发送：非精确AIOInputSendBtn或非原生文字标签先取消旧pending恢复许可，再返回QQ原处理，不吞图片/相册/表情面板的点击。缺少本次物理许可但命中旧pending同按钮也取消旧等待并返回原QQ；只有同一按钮、同一编辑框、全部文本/引用/当前链/媒体状态仍有效的重复物理点击才被消费以避免重复文字发送。已提交的装扮请求仍可能完成，取消许可不会重试旧消息。

文字延后新增MediaQueueObserver门控。已有照片handler时，在当前真实AIOContext经只读GetSelectMediaInfo路由获得精确b$i结果；MediaSelectionGate仅核对其public final List b()返回List并调用isEmpty，不读取、遍历、渲染或记录媒体元素。该分支只有已知空结果才继续；非空、null、未知类/签名、调用失败均保留原QQ发送。原按钮监听器须非null，Attempt捕获其对象身份，恢复前仍须为同一个监听器；不替换监听器，也不要求其精确类为aio.input.sendmsg.b。

已有handler的空结果须属于当前PhotoPanelVM与AIOMediaRepository：通过当前查询期间精确PhotoPanelVM.S回调核对FrameworkVM.getMContext()/mContext与当前AIOContext同对象、VM.o为精确当前repo；一次查询须唯一匹配该handler及同返回对象。快照在QQ读取队列前注册，弱保存context/VM/repo身份；每次valid重新查询空状态、核对owner和snapshot，不能使用旧聊天缓存。AIOMediaRepository.i(Pair)变更通知使关联快照永久changed，已通知的选图变化即使清空也不复活旧点击。该hook发生在普通mutator改列表后、通知前；异常改列表但未通知或未来hotpatch路径未证明全部覆盖。

尚未注册照片handler时，NO_HANDLER分支从当前context.e()的精确aio.runtime.b代理读取a，要求其为精确VMMessenger，字段h与s()均指向当前context，字段d为精确ConcurrentHashMap。GetSelectMediaInfo的精确键在真实注册表中必须不存在；同singleton请求实际经过该messenger.l恰好一次，前后owner/route/map身份和键缺失均一致，路由与dispatch均返回null，PhotoPanelVM.S调用零次，才可捕获这一状态。已有未知handler、handler返回null、所有者不符或无法核对真实表均拒绝，不能将普通null响应当作空队列。该弱快照保存context/route/messenger/map身份；valid重新查询并比较相同分支及身份。相关j注册/a注销入口、实际排队registerR/unregisterR的零参void invoke事件，以及同context的PhotoPanelVM.onCreate，都会永久标记相应快照changed；处理器后来注销或队列再次为空也不复活旧点击。

两种捕获均最多保留两个活跃弱快照，供一个等待与一次短暂复查。hook未齐、未知所有者、重复/失配路由、已关闭或弱引用失效均拒绝延后。完成或失败及时关闭快照，不写QQ队列、注册表或调用发送方法，详[媒体结构](qq-media-schema-0.1.52.txt)。观察的是已核查的普通注册/通知路径，未证明异常写入或未来hotpatch全部覆盖。

此门控覆盖已核查的原生照片选图队列，收藏表情的PicEmotionSendEvent有独立发送路径；不能由空照片队列推断所有图片、表情包或附件队列已覆盖。R2已现场完成群/私聊照片和合成收藏表情的原QQ直接发送，以及选图再清空后旧文字取消；这不扩展为逐图片换气泡或所有媒体覆盖。注册/注销任务独立触发、异常无通知或热补丁未单独覆盖，可见取消也不证明某一repo.i hook独立触发了它。51媒体异常在开关模式下均出现，冷启动后关闭和恢复开启模式又能成功发送；这些对照不足以证明根因，详[51媒体记录](media-failure-0.1.51.txt)。52是防护收紧，根因修复结论不由本轮成功推导。

0.1.48曾按Fragment/View根身份及会话元组合并包装，但后续现场证实同一真实Fragment/root会保留旧Contact：两个包装contactSame=false仍令普通文字被multiple判定跳过。49改用QQ权威活动链，历史AIO包装仅定位唯一的实际Fragment及包含按钮的根视图，不读取历史tuple作为授权依据。由Fragment.chatPieManager读取当前ChatPie，要求manager.e()==manager.b；ChatPie.k()==ChatPie.f==Fragment.currentContext.get()；该context.c()仍是当前Fragment，context.g()==ChatPie.l()==ChatPie.h。ActiveConversationChain负责非null和对象身份比较；实际ChatPie.d根视图必须位于Fragment根内、包含按钮、已附着且同窗口。当前Contact只从这条链的当前param读取，未知类型、字段、会话或不一致指针全部拒绝。诊断模式可统计historicalWrappers/staleContacts/unreadHistorical，不参与当前判定，不公开会话标识。

Attempt弱保存当前Fragment、manager、pie、context、param和真实ChatPie根；每次valid都重新解析QQ当前链并对比所有身份及type/peer/guild，仍保留窗口、按钮、监听器、Editable、文本修订、账号、配置代次和前台保护。ChatFragment.access$setCurrentContext$p在改为不同或空上下文时永久取消pending；onNewIntent(Bundle)、onPause、onDestroyView以及onHiddenChanged(true)也永久取消。恢复原会话或指针不能撤销cancelled。只在全部必要hook注册成功后发布ready，部分注册失败保持原QQ发送，本进程不重试注册；历史包装构造时间不作为会话切换证据，不因旁路新建包装取消pending。QQ实际根和当前链负责授权，不以最新包装或临时LifecycleRegistry状态替代。

QQ取消引用回复会清除gja tag和compound drawables，但可留下aio.reply.a movement。51的ReplyStateGate同时核对当前binding.context.e().k(InputReplyMsgIntent$GetReplyData.d)只读响应、gja tag及绝对/相对复合图标。无引用要求已知逻辑回复为空、tag为空、全部图标为空；取消回复留下的movement不作为有引用的单独依据。有引用要求精确aio.input.l逻辑数据、aio.reply.d tag和aio.reply.a movement，QQ预览TextView、顶部Drawable及tag预览均符合已核查结构，其他方向图标为空。引用结构不一致或查询未知在defer时保留原QQ发送，在等待valid时取消恢复；模块不清除tag、图标、movement、逻辑回复或输入。

引用快照弱保存逻辑数据、tag、movement、顶部图标及预览对象身份，保存引用sequence/messageId精确值和昵称/引用正文/预览的UTF-16 SHA-256指纹，只在内存比对，不记录正文、会话标识或指纹到日志。发送恢复前重新读取并核对全部身份和值，结束后清理快照。UiTapGate对QQ逻辑设置/清除和预览设置/清除方法注册必要hook，仅取消对应当前输入/上下文的pending；切换引用再切回、取消后恢复引用也不能恢复旧点击，hook未齐则不启用延后。

ComposerTextGate的小表情适配仅接受精确com.tencent.mobileqq.text.style.EmoticonSpan，排除其子类、COMPOSING、无效范围和未知绘制span；确切类的合法零长度残留可忽略，不移除原span。等待快照同时记录span对象身份、范围、flags和index/emojiType/size三个int字段，恢复前再次核对，防止未触发SpanWatcher的原地字段变化误恢复。仅当ReplyStateGate已确认原生有效引用，才额外接受精确com.tencent.qqnt.aio.at.a；其已核查父类的d/e/r/s四个String字段只读形成内存指纹并参与快照，不调用绘制方法或输出值。无引用@、子类、未知字段、COMPOSING及其他ReplacementSpan继续拒绝适配。

OfficialBubbleApi 从商城成功正常操作学习当前账号的有限参数：气泡设置模板、详情查询 filter/qua、SDK 封装元数据，保存在 QQ 私有目录并按账号分隔。不读取 TicketManager 登录密钥，不保存网络认证封装或用户消息。商城协议与 0.1.45/0.1.46 相同：QQKuiklyPlatformApi.sendPbRequest 调用 0x942d_0 设置、0x9716_0 详情，WebSsoBody 由 QQ 类序列化，登录认证由 QQ SDK 处理。

详情返回 appId/itemId 匹配、authret=0、payForbidden=false 后才提交；设置业务 ret=0 后再次查询同款 isSetup=true，再更新 SVIPHandler 正常缓存并核对字体。缺失或未知标志不视为成功，数字 0/1 与布尔标志严格解析。字体状态改变会停止本次缓存刷新。SET 不确定时最多追加只读核对，避免重复提交。

HostRuntime.ready 始终 false：旧 SendPolicy 和测试只保留历史回归，生产 send hook 不修改消息参数。逐消息恢复仅对应用户的一次物理点击；不调用脚本发送、撤回或复读 API，不主动生成新消息。QFun 与其他内嵌模块保持原载荷。

Repository/ConfigProvider 管理可选独立应用配置，Provider 校验调用 UID，只允许模块自身和 QQ；模块自身不能消费 QQ 手动命令。账号选择、编辑和响应作为同一事务处理。仅在 Provider 确认保存后清除待同步标记；账号或编辑代次变更后丢弃旧响应。普通采集不会改写开关或勾选。来自独立应用的控制要求 providerOwner 与当前控制账号匹配且最近成功同步不足 15 秒，不能借用其他账号的成功心跳；失联或同步挂起超过期限会暂停。用户明确保存为 QQ 本机控制后不依赖该应用。

为限制 Binder 事务大小，气泡库 JSON 超过 131,072 字符时使用 controlsOnly：不发送整库或 hostConfig，Provider 不导入或返回完整库，仅同步装扮及有限诊断。完整大库仍在 QQ 内管理，独立应用不会完整同步这一大库；未同步的库待确认标记不会被误清除。原生库使用回收列表、20 项分页、搜索与一次提交的批量选择；全选覆盖搜索外及所有页，主气泡 ID 去重后填入装扮配置。

49历史验证：96核心（新增12组当前链/等待身份检查）、92 Android、构建及Lint 0 errors/27 warnings通过，覆盖安装49成功。A～E及真实冷启动后原219池H，六条服务器/缓存确认后实际各一条；E有3匹配包装/2历史包装/1旧Contact仍由当前链授权，双击只一条。F修改还原、G聊天离开到最近会话再返回取消保留草稿；冷启动前7/5/2、后1/1/0，合计8/6/2。原219/60秒/定时关/逐消息开/收录关精确读回，库2644/原2449勾选、23新增未勾选保留；独立module/test不存在、USB仍0、QQ未清数据，原生设置单入口可用。49接收端不可用；HOME后台/锁屏、全部生命周期分支、未知链/查询/接口故障及新定时周期未复测，详[49历史报告](VALIDATION-2026-10-02-0.1.49.md)。45～48报告及公开脱敏46～48记录与后续故障保留，不能替代50；fixture不证明未实测QQ边界。公开日志不含正文、账号或认证；私人QQ APK、反编译资料和UI排除在源码包外。

50历史验证：96核心、122 Android（新增30项生产分类及快照检查）、构建和Lint 0 errors/30 warnings通过。小表情/随后纯文字/双击正向及两款接收端发送合计六条各一条，修改还原与HOME两次取消保留草稿；两个发送进程合计8/6/2。接收端仅有用户文字确认“两款不同，重进后仍保留”，未读取截图。最终第二次COLD后恢复原219候选/60秒/原开关，库2644/勾选2449保持；最终新进程0/0/0、心跳138/sync0，仅配置与入口检查，没有新增测试发送。独立module/test不存在、USB原值0已核对。详[50报告](VALIDATION-2026-10-02-0.1.50.md)；短时测试不证明所有随机失效、长期稳定或风控安全。

51历史进展：96核心、196 Android生产分类/引用状态/快照及配置检查通过，构建和Lint为0 errors/28 warnings，QQ覆盖安装成功。第一次COLD后原219候选/60秒/perMessage=true精确读回；随后临时两款池source/A/B/C/D/F六条正向各一条，含自己的引用、双击引用及其他成员源引用内QQ原生成员选择@（没有自动@）。E7发送点击后关闭原生预览X触发逻辑引用变更永久取消，未提交SET、0条并保留精确草稿。单发送进程7/6/1、心跳3374/sync0；接收端用户文字“是”确认两款不同、引用卡片保留和重进保持，未读取接收端截图，A/B发送端截图仅私有保存。51最终第二次实际COLD后原219精确池/60秒/原开关、库2644/勾选2449已[恢复](config-restored-0.1.51.txt)，最后检查进程0/0/0、347/sync0未新增发送；模块/test不存在、USB原值0、QQ未卸载/清数据，留下原生面板，当时2173887由原开启逐消息模式管理。引用回切、h.m重绘具体触发、小表情加引用、普通无引用@原路径、后台/锁屏和新定时周期等未在51实测，详[51报告](VALIDATION-2026-10-02-0.1.51.md)与[QQ结构](qq-reply-schema-0.1.51.txt)。

52首轮构建进展（新增NO_HANDLER前）：96组核心、214项唯一Android检查（INSTRUMENTATION_CODE:-1）与构建/Lint 0 errors/28 warnings通过；Android测试不操作QQ聊天。新增18项媒体结果fixture验证精确类、空/非空/未知状态及不读取媒体内容，不等于真实路由owner/变更hook或媒体交付通过。首轮52已替换QQ内嵌模块，其他40318项载荷保持；实际冷启动未开相册时capture为null、计数0/0/0，开相册后已有handler的空队列捕获成功，但额外监听器精确类限制阻止适配。该限制已改回非null监听器对象身份核对。

52 R2构建及发送：含NO_HANDLER的APK已构建并重跑214项独立Android检查通过，Lint 0 errors/28 warnings；内嵌更新保留其他40318项载荷、QQ原v2签名及16KiB对齐。实际COLD2020/2026ms，未开相册首条文字服务器确认2153379，引用该首条确认2173887、实际截图引用卡片保留；相册后文字和最后双击文字也各一条，输入框空。最终发送进程5/4/1、心跳788/sync0：四次恢复正向，一次选图再清空取消；该取消未提交SET，两次跨页面返回均0条/精确草稿，随后新文字正常。群/私聊照片和合成收藏表情走原QQ直接发送一条，原生文字加选图组合一条，不增加Ling延后计数；收藏仅本次合成项已删除。接收端[实际截图](receiver-media-0.1.52.txt)支持群图片、相册后款式、混合消息及私聊照片/收藏表情；重进后图片/收藏、cold/quote/final气泡及引用卡片保持由用户[文字“全部保留”](receiver-retention-0.1.52.txt)确认，重进后未读取截图。独立初始化/注册task事件因果、异常无通知或热补丁未证明，详[52报告](VALIDATION-2026-10-03-0.1.52.md)。

52最后[实际COLD](qq-cold-final-0.1.52.txt)1916/1927ms后[精确恢复](config-restored-0.1.52.txt)原224候选/60秒/逐消息开、定时关、收录关及2644库/勾选；新[检查进程](current-final-0.1.52.txt)0/0/0、106/sync0仅面板核对，未新增发送。[最终手机内嵌字节](embedded-exact-module-0.1.52.txt)等同280055-byte R2候选、SHA-256 af96b1c1826eb2d7d209c4ea2389039d61593ebc33bc02c4c0fb847521739a5d。[环境收尾](device-final-0.1.52.txt)确认独立模块/test不存在、USB原0、只清理五个明确合成文件和新收藏、QQ未卸载/清数据；所查缓冲未见10-03 03:19:42 R2起新QQ fatal，保留短时/所查buffer范围，不称全部路径或长期无故障。
