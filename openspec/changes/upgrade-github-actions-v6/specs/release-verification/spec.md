## ADDED Requirements

### Requirement: 发布工作流内部 artifact 传递必须保持唯一且完整
发布工作流 MUST 将唯一普通正式 APK 以唯一名称上传，并在后续作业按该名称下载到确定路径；
下载结果 MUST 恰好包含预期文件名的一个 APK。artifact 服务报告的摘要或下载内容摘要不一致
时 MUST 失败，不得创建 tag、草稿 Release 或其他发布对象。Action 升级不得改变 APK 名称、
路径、保留期、包名、签名身份或 Release 唯一资产约束。

#### Scenario: 唯一 APK 完整传递
- **WHEN** 构建作业产生唯一预期普通正式 APK 并完成 artifact 上传
- **THEN** 后续作业按唯一 artifact 名称下载到预期目录
- **AND** 下载后只存在文件名、大小和摘要均符合预期的一个 APK

#### Scenario: artifact 摘要不匹配
- **WHEN** 下载内容摘要与 artifact 服务提供的预期摘要不一致
- **THEN** 发布工作流立即失败
- **AND** 不创建 tag、草稿 Release 或替代资产

#### Scenario: artifact 数量或路径漂移
- **WHEN** 下载目录没有 APK、包含多个 APK 或唯一 APK 不在预期路径
- **THEN** 发布工作流立即失败
- **AND** 不通过搜索其他目录或重命名未知文件继续发布

### Requirement: 发布 Action 升级验证不得产生发布副作用
系统 MUST 在不读取正式签名 Secrets、不运行正式构建、不推送 tag、不创建或修改 Release 的
前提下验证发布工作流的触发、权限、目标 Action 版本、唯一 APK 传递和失败即停止合同。只有
替代变更合并后另行取得明确发布授权，才可手动触发正式发布工作流。

#### Scenario: Pull Request 验证发布工作流
- **WHEN** Pull Request 修改发布工作流或其合同
- **THEN** 系统通过静态语法检查和仓库合同验证发布边界
- **AND** 不读取签名 Secrets、不构建正式 APK 且不创建任何发布对象

#### Scenario: 验证需要真实 artifact 闭环
- **WHEN** 静态合同不足以证明升级后的上传下载兼容性
- **THEN** 系统使用无签名、无发布权限的测试夹具执行上传下载闭环
- **AND** 夹具不得被当作 APK、Release 资产或正式发布证据

#### Scenario: 工作流升级已经合并
- **WHEN** 替代变更已合并且维护者尚未明确授权新一轮正式发布
- **THEN** 系统保持发布工作流未触发
- **AND** 不以 CI 绿色推断签名、设备或公开 Release 门禁已经通过
