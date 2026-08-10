# fork-build-security Specification

## Purpose

本能力用于确保复制后的独立仓库不再依赖上游遥测项目或公开签名身份，并让验证构建、正式发布和敏感材料管理具有可检查、失败即停止的安全边界。

## Requirements

### Requirement: 独立构建与上游遥测隔离
系统 MUST 在不提供上游 Firebase 客户端配置的情况下完成受支持的 Debug 构建，并且构建产物不得初始化上游 Firebase Analytics 或 Performance。

#### Scenario: 干净检出执行 Debug 构建
- **WHEN** 开发者从干净检出执行项目规定的 Debug 构建
- **THEN** 构建无需 `app/google-services.json` 即可完成
- **AND** 产物不包含本仓库继承的上游 Firebase 初始化配置

#### Scenario: 查看应用隐私说明
- **WHEN** 用户查看应用内隐私政策
- **THEN** 文档不得声称当前构建使用已经移除的 Firebase 统计或性能服务

### Requirement: 验证工作流不使用发布签名
Pull Request 和 `master` 分支的验证工作流 MUST 运行测试并构建 Debug 产物，且 MUST NOT 读取仓库内签名二进制、硬编码签名密码或正式发布 Secrets。

#### Scenario: Pull Request 触发验证
- **WHEN** Pull Request 的改动命中 Android 验证范围
- **THEN** 工作流执行单元测试和 Debug 构建
- **AND** 工作流不生成或分发使用正式应用标识和发布签名的 APK

#### Scenario: 验证命令失败
- **WHEN** 单元测试或 Gradle 构建命令返回非零状态
- **THEN** 验证工作流 MUST 返回失败
- **AND** 后续步骤不得把该次验证报告为成功

### Requirement: 正式发布仅接受受控签名
正式发布工作流 MUST 只使用 GitHub Actions Secrets 注入的本仓库签名材料，并且 MUST NOT 回退到仓库文件或公开签名。

#### Scenario: 发布 Secrets 完整
- **WHEN** 仓库所有者手动触发正式发布且所需签名 Secrets 全部存在
- **THEN** 工作流验证签名材料后构建受签名的正式变体

#### Scenario: 发布 Secrets 缺失
- **WHEN** 任一必需签名 Secret 缺失或签名材料无法验证
- **THEN** 正式发布工作流在构建前明确失败
- **AND** 不生成使用其他签名的替代发布包

### Requirement: 仓库不跟踪继承的敏感材料
当前跟踪树 MUST NOT 包含继承的 Google Services 配置、旧签名二进制或硬编码签名密码，并且忽略规则 MUST 覆盖常见本地签名文件和 Firebase 客户端配置。

#### Scenario: 检查版本控制跟踪树
- **WHEN** 维护者检查当前提交所跟踪的配置与签名文件
- **THEN** 不存在继承的 `app/google-services.json` 和旧 `legado.jks`
- **AND** 工作流中不存在对应的硬编码签名凭据

#### Scenario: 开发者生成本地签名材料
- **WHEN** 开发者在工作区生成常见格式的本地签名文件或 Firebase 配置
- **THEN** 这些文件默认不会被 Git 纳入新的提交

### Requirement: 安全加固保持应用数据兼容
本变更 MUST NOT 修改最低 SDK、Room 数据库、书源和订阅源规则、导入 URI、JSON 字段或备份格式。

#### Scenario: 现有用户升级安全加固构建
- **WHEN** 用户从兼容签名的既有版本升级到完成安全加固的版本
- **THEN** 应用持久化数据和兼容性敏感接口无需迁移即可继续使用
