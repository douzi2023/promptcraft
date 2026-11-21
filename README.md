# 🧠 PromptCraft (MC 1.21.1 Fabric)

> 游戏内大语言模型智能体 Mod：支持现场建造、生成枪械与魔法武器、编排波次防守小游戏及联动其他 Mod。

---

## ✨ 核心能力实现对照

| 设定能力 | 实现机制 | 效果 |
| :--- | :--- | :--- |
| **1. 原版指令与建造** | `execute_commands` 工具 + 异步调度 | 用户说“造个火柴盒/刷怪塔”，AI 计算坐标直接调用 `/fill`、`/setblock` |
| **2. 现场生成“枪械 Mod”** | `create_dynamic_weapon` + 通用元物品（1.21 数据组件） | 用户说“给我做一把火球加特林”，背包立即到手，右键连发火球、带击退音效 |
| **3. 现场制作小游戏** | `create_minigame` + `MinigameManager` + 原生 BossBar | 用户说“玩僵尸攻城”，屏幕顶部出现 Boss 血条与倒计时，定时刷新波次怪物 |
| **4. 联动其他 Mod** | `inspect_mods` + 注册表反射查询 | 自动扫描整合包中其他 Mod 的物品与方块 ID（如机械动力、热力等），供 AI 检索调用 |

---

## 🚀 游戏内指令速查

### 1. 配置大模型
进入游戏后，只需配置一次 API：
```mcfunction
# 设置 API Key (支持 DeepSeek, OpenAI, Kimi, 或本地 Ollama)
/ai config key sk-xxxxxxxxxxxxxxxxxxxxxxxx

# (可选) 设置 API 端点 (默认为 DeepSeek)
/ai config url https://api.deepseek.com/chat/completions
# 若使用本地 Ollama:
# /ai config url http://localhost:11434/v1/chat/completions

# (可选) 设置模型名 (默认为 deepseek-chat)
/ai config model deepseek-chat

# 查看当前配置
/ai config
```
*配置文件保存在：`config/promptcraft.json`，也支持直接读取系统环境变量 `PROMPTCRAFT_API_KEY`。*

---

### 2. 对话与即时生成

#### 示例 1：现场生成枪械与特制法杖
```
/ai 给我做一把火球加特林，射速极快，带爆炸效果
/ai 做一把能发射闪电的雷神之杖，冷却时间短一点
/ai 给我一把冰冻狙击枪，伤害50，打中目标会减速冰冻
```
> **即时效果**：物品瞬间出现在玩家主手或背包中，带有定制名称、彩色 Lore、精确的数据属性（射速、弹药类型、伤害、爆炸威力），右键即可直接开火！

#### 示例 2：现场发起小游戏
```
/ai 玩僵尸攻城游戏，一共3波，每波怪越来越强
/ai 来一场骷髅弓箭手狙击防守战，坚持 3 分钟算赢
/ai 停止当前小游戏
```
> **即时效果**：屏幕上方立即挂出彩色 `BossBar` 进度条与阶段标题，波次号角吹响，外围怪物按编排定时生成。全部清空后发放钻石与经验奖励，通关结算！如果想提前结束，输入 `/ai stopgame`。

#### 示例 3：建筑与原版魔法指令
```
/ai 在我站的位置建一个 7x7 的玻璃温室，留一扇橡木门
/ai 把天气改成晴天，时间调成正午
/ai 看看周围都有什么生物和环境
```

#### 示例 4：联动其他 Mod
```
/ai 查一下当前游戏里有没有带 gun 或者 sword 的物品
/ai 用机械动力里的物品给我配一套装备
```

---

## 📦 如何安装与运行

### 方式 A：直接放入游戏
打包生成的成品 Mod 文件位于：
`build/libs/promptcraft-1.0.0.jar`
* 将该文件直接复制到任意 **Minecraft 1.21.1 Fabric** 客户端的 `.minecraft/mods` 文件夹即可。
* 需要安装前置：**Fabric API 1.21.1**。

### 方式 B：本地源码调试启动
在终端中执行：
```powershell
# 启动客户端测试环境
.\gradlew.bat runClient

# 启动服务端测试环境
.\gradlew.bat runServer
```
