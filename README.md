# 救在身边（AI First Aid Assistant）

面向院外突发急救场景的 Android AI 急救助手。  
技术栈：**Android Studio + Java + XML**。

## 项目目标
- 通过多传感器融合进行风险检测（低/中/高风险）
- 在异常情况下引导用户完成急救流程
- 提供 AR 指导、AED 导航、协同救援、急救记录与个人信息管理

## 快速开始
1. 使用 Android Studio 打开本项目根目录。
2. 确保 Gradle JDK 为 **11 或 17**（JDK 8 无法构建 AGP 8.x）。
3. 等待 Gradle Sync 完成后运行 `app` 模块。
4. 首次启动请授予相机、麦克风、定位、活动识别等权限。

## 主要模块（当前实现）
- `MainActivity`：首页急救台、风险状态展示、功能入口
- `SensorFusionManager`：加速度计/陀螺仪融合 + 风险评分
- `RiskTriggerActivity`：全屏倒计时确认
- `MediumRiskActivity`：中风险干预与升级
- `EmergencyModeActivity`：高风险急救流程
- `ArGuideActivity`：AR 指导页（当前为占位实现）
- `CollaborationActivity`：协同救援与任务分配（二维码占位）
- `RecoveryActivity`：急救结束后恢复与记录
- `ProfileActivity`：个人信息与紧急联系人
- `KnowledgeActivity`：急救知识学习

## 目录建议
- `app/src/main/java/com/example/firstaid/logic`：算法与检测逻辑
- `app/src/main/java/com/example/firstaid/ui`：页面 Activity
- `app/src/main/res/layout`：页面布局 XML
- `app/src/main/res/drawable`：按钮/卡片/警示背景等样式资源

## 协作规范（简版）
- 新功能按“**逻辑层 + 页面层 + 资源层**”拆分提交，避免大而杂的改动。
- 新增页面需同步更新 `AndroidManifest.xml` 注册与导航入口。
- 命名统一：`XxxActivity`、`activity_xxx.xml`、`bg_xxx.xml`。
- 提交前至少自测：冷启动、权限流程、首页跳转、核心按钮可用性。

## 后续优先项
- 接入 CameraX 与真实 AR 叠加引导
- 接入地图定位与紧急联系人通知
- 用 Room 落地急救事件与模型优化数据

## 任务看板（当前）

### 已完成
- [x] Java + XML 架构落地，核心页面与统一顶部返回样式完成
- [x] 三阶段跌倒检测算法（冲击 -> 姿态变化 -> 长时间静止）接入
- [x] 风险分级联动：低/中/高风险流程与页面跳转打通
- [x] 中风险语音询问与语音关键词识别（“需要急救”等）实现
- [x] 中风险 60s 倒计时与高风险 5s 进入急救模式逻辑完成
- [x] 后台检测前台服务（Foreground Service）与开机恢复检测（Boot Receiver）完成
- [x] 单一检测源改造：后台服务统一产出风险，首页/中风险页面订阅同一风险广播
- [x] “我没事”安全确认同步：前后台状态统一重置 + 冷却保护
- [x] 真机稳定性修复：前台服务启动兜底、通知权限兼容、服务重连看门狗

### 待完成
- [ ] 首页增加后台服务状态灯（运行中/重连中/异常）与调试日志面板
- [ ] 增加后台检测总开关（手动启停服务）与开机自启开关
- [ ] AR 页面接入 CameraX 实时画面与急救引导叠加（替换占位）
- [ ] AED 导航接入真实地图 SDK 与最近设备查询接口
- [ ] 协同救援二维码改为真实动态生成与扫码协同链路
- [ ] 急救事件记录持久化（Room）与历史回放页面
- [ ] 语音识别服务缺失场景优化（引导安装/启用识别引擎）
- [ ] 增加核心流程自动化测试（风险切换、跳转、服务存活）
