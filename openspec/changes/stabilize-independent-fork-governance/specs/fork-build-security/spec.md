## MODIFIED Requirements

### Requirement: 验证工作流不使用发布签名
Pull Request 和 `master` 分支的验证工作流 MUST 运行适用的 Android 单元测试、lint、Debug 构建、网页端检查、OpenSpec 严格校验和安全分析，且 MUST NOT 读取仓库内签名二进制、硬编码签名密码或正式发布 Secrets。

#### Scenario: Pull Request 触发验证
- **WHEN** Pull Request 的改动命中 Android、网页端、OpenSpec 或仓库治理验证范围
- **THEN** 工作流执行对应的必需检查并在适用时构建 Debug 产物
- **AND** 工作流不生成或分发使用正式应用标识和发布签名的 APK

#### Scenario: 验证命令失败
- **WHEN** 任一必需测试、静态检查、构建或安全分析返回非零状态
- **THEN** 验证工作流 MUST 返回失败
- **AND** 后续步骤不得把该次验证报告为成功

### Requirement: 正式发布仅接受受控签名
正式发布工作流 MUST 只使用 GitHub Actions Secrets 注入的本仓库签名材料，并且 MUST NOT 回退到仓库文件或公开签名。预发布质量、安全门禁通过且适用模拟器预检已完成后，工作流 MUST 只允许仓库所有者通过 `workflow_dispatch` 手动触发，生成只含包名 `io.legado.app.release` 的一个受控签名草稿候选；不得生成未来 `releaseA` 产物。候选的完整性、身份和指定真机门禁全部通过后才可公开 Release。

#### Scenario: 发布 Secrets 完整
- **WHEN** 仓库所有者通过 `workflow_dispatch` 手动触发正式发布、预发布质量/安全门禁已满足、适用模拟器预检已完成，且所需签名 Secrets 全部存在
- **THEN** 工作流验证签名材料后构建唯一普通正式 APK并保持 Release 为草稿
- **AND** APK 使用 `io.legado.app.release` 和本仓库受控签名身份

#### Scenario: 非所有者或非手动触发发布
- **WHEN** tag push、schedule、repository dispatch、非仓库所有者或其他入口尝试触发正式发布
- **THEN** 工作流不得生成受签名候选或 Release
- **AND** 不读取正式发布 Secrets

#### Scenario: 草稿候选尚未通过真机门禁
- **WHEN** 受控签名候选已经构建但完整性、身份或指定真机验证尚未全部通过
- **THEN** Release 保持草稿或等价不可见状态
- **AND** 工作流不得将其公开或设为 Latest

#### Scenario: 发布 Secrets 缺失
- **WHEN** 任一必需签名 Secret 缺失或签名材料无法验证
- **THEN** 正式发布工作流在构建前明确失败
- **AND** 不生成使用其他签名的替代发布包

#### Scenario: 工作流尝试生成 releaseA
- **WHEN** 正式发布配置包含新的 `releaseA` 构建、改写或上传路径
- **THEN** 正式发布门禁失败
- **AND** 历史 `releaseA` Release 资产保持不变
