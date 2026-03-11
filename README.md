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
- `RiskPopupCoordinator`：全局弹窗协调器，确保同一时刻只有一个风险弹窗，支持高风险替换低风险
- `BackgroundDetectionService`：后台检测前台服务，持续监测传感器并触发风险弹窗
- `LowRiskPopupActivity`：低风险提示弹窗，支持升级到中风险或自动被高风险替换
- `MediumRiskActivity`：中风险干预与升级，60s 倒计时 + 语音识别"需要急救"
- `EmergencyModeActivity`：高风险急救流程，提供拨打 120、定位分享、AR 指导入口
- `ArGuideActivity`：**AR CPR 指导页**（CameraX + MediaPipe Pose + 胸口定位 + 110 BPM 节拍器）
  - `PoseDetectorHelper`：MediaPipe Pose Landmarker 封装，检测人体关键点并计算胸口位置
  - `CprOverlayView`：自定义 View，在相机预览上叠加胸口按压圆圈与节奏动画
- `CollaborationActivity`：协同救援与任务分配（二维码占位）
- `RecoveryActivity`：急救结束后恢复与记录
- `ProfileActivity`：个人信息与紧急联系人
- `KnowledgeActivity`：急救知识学习

## 目录建议
- `app/src/main/java/com/example/firstaid/logic`：算法与检测逻辑
- `app/src/main/java/com/example/firstaid/ui`：页面 Activity 与自定义 View
- `app/src/main/java/com/example/firstaid/service`：后台服务
- `app/src/main/res/layout`：页面布局 XML
- `app/src/main/res/drawable`：按钮/卡片/警示背景等样式资源
- `app/src/main/assets`：**MediaPipe 模型文件**（需手动创建并放入 `pose_landmarker_lite.task`）

## 协作规范（简版）
- 新功能按“**逻辑层 + 页面层 + 资源层**”拆分提交，避免大而杂的改动。
- 新增页面需同步更新 `AndroidManifest.xml` 注册与导航入口。
- 命名统一：`XxxActivity`、`activity_xxx.xml`、`bg_xxx.xml`。
- 提交前至少自测：冷启动、权限流程、首页跳转、核心按钮可用性。

## AR CPR 指导功能说明

**技术栈**：CameraX + MediaPipe Pose Landmarker + 自定义 Overlay View

**核心功能**：
1. **人体胸口定位**：通过 MediaPipe Pose 检测左右肩关键点（landmark 11/12），计算胸口中心坐标
2. **AR 视觉叠加**：在相机预览上绘制红色按压圆圈，跟随胸口位置实时移动
3. **按压节奏提示**：110 BPM 节拍器，圆圈脉冲动画 + 节奏文本反馈（Press Faster/Slower/Good Rhythm）

**模型文件**：
- 需要手动下载并放入：`app/src/main/assets/pose_landmarker_lite.task`
- 下载地址：[MediaPipe Pose Landmarker Lite](https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task)

**相关文件**：
- `ArGuideActivity.java`：AR 指导主页面，集成 CameraX + Pose 检测 + 节拍器
- `PoseDetectorHelper.java`：MediaPipe Pose 封装，处理图像帧并返回胸口坐标
- `CprOverlayView.java`：自定义 View，绘制按压圆圈与节奏动画

---

## 后续优先项
- 接入地图定位与紧急联系人通知
- 用 Room 落地急救事件与模型优化数据
- AR 指导增加按压频率实时检测（通过手腕位移估算用户实际 BPM）

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
- [x] **全局弹窗协调器**：同一时刻只允许一个风险弹窗，高风险可替换低风险，阻止同级重复弹出
- [x] **AR CPR 指导 MVP**：CameraX + MediaPipe Pose 胸口定位 + 110 BPM 节拍器 + 按压圆圈叠加

### 待完成
- [ ] 首页增加后台服务状态灯（运行中/重连中/异常）与调试日志面板
- [ ] 增加后台检测总开关（手动启停服务）与开机自启开关
- [ ] AED 导航接入真实地图 SDK 与最近设备查询接口
- [ ] 协同救援二维码改为真实动态生成与扫码协同链路
- [ ] 急救事件记录持久化（Room）与历史回放页面
- [ ] 语音识别服务缺失场景优化（引导安装/启用识别引擎）
- [ ] 增加核心流程自动化测试（风险切换、跳转、服务存活）
