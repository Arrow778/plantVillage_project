# 🌿 PlantVillage 农作物病害识别系统

> 基于端云协同架构的农作物病害智能识别平台，面向本科毕业设计开发。

---

## 📖 项目简介

本系统集成了 **TFLite 端侧推理**、**PyTorch 云端专家引擎**、**Flask 后端 API** 与 **Jetpack Compose Android 客户端**，实现了从图像采集到病害诊断、百科查询、用户反馈的完整闭环。

系统可识别 **14 种农作物** 的 **38 种常见病害**，包括苹果、葡萄、玉米、番茄、土豆、草莓、辣椒、樱桃、桃子、南瓜等。

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────┐
│              Android 客户端                  │
│  Jetpack Compose + ViewModel + StateFlow    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ TFLite   │  │  Wiki    │  │ Offline  │  │
│  │ 本地推理  │  │  百科缓存 │  │ 离线存储  │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  │
└───────┼─────────────┼─────────────┼────────┘
        │  Retrofit   │  REST API   │
┌───────▼─────────────▼─────────────▼────────┐
│              Flask 后端 (APIs/)              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │  Auth    │  │ Predict  │  │ Feedback │  │
│  │ 登录注册  │  │ 云端推理  │  │ 反馈审计  │  │
│  └──────────┘  └──────────┘  └──────────┘  │
│              MySQL 数据库                    │
└─────────────────────────────────────────────┘
        │
┌───────▼──────────────────┐
│   PyTorch ResNet50 模型   │
│   trian_code/ 训练脚本    │
└──────────────────────────┘
```

---

## 📁 目录结构

```
plantVillage/
├── android/                  # Android 客户端（Kotlin + Jetpack Compose）
│   └── app/src/main/java/edu/geng/plantapp/
│       ├── data/
│       │   ├── local/        # DataStore、OfflineManager（离线缓存）
│       │   └── remote/       # Retrofit API 接口与数据模型
│       ├── ml/               # TFLiteHelper 本地推理封装
│       ├── repository/       # 数据仓库层（Auth/Feedback/Predict/Expert）
│       └── ui/
│           ├── screens/
│           │   ├── home/     # 主页：拍照、图库、wiki 展示、反馈
│           │   ├── result/   # 结果页：详细病害信息
│           │   ├── history/  # 历史档案页
│           │   ├── auth/     # 登录注册页
│           │   └── profile/  # 个人中心、专家认证
│           ├── navigation/   # 导航图
│           └── theme/        # 主题色、字体
│
├── web_flask/                # 后端服务（Python + Flask）
│   ├── APIs/                 # 主要 API 服务
│   │   ├── app.py            # Flask 入口
│   │   ├── routers/          # 路由模块（auth/predict/feedback/wiki/expert）
│   │   ├── modelsx/          # PyTorch 模型文件
│   │   └── .env              # 环境变量配置
│   ├── shared/               # 公共工具（DB 连接、JWT 等）
│   └── requirements.txt      # Python 依赖
│
└── trian_code/               # 模型训练脚本
    ├── pytoch_train.py       # ResNet50 训练主脚本
    ├── pth_to_tflite.py      # PyTorch → TFLite 转换
    ├── gen_labels.py         # 标签文件生成
    └── models/               # 训练输出的模型文件
```

---

## ✨ 核心功能

### 📱 Android 客户端

| 功能 | 说明 |
|------|------|
| **实时拍照识别** | 调用系统相机，TFLite 模型本地推理，0 网络延迟 |
| **图库上传识别** | 从相册选择图片，本地端侧推理 |
| **云端专家引擎** | 调用后端 PyTorch ResNet50 模型进行二次验证 |
| **百科文献查询** | 自动拉取病害症状、防治方案，结果缓存避免重复请求 |
| **网络错误重试** | wiki 请求失败时展示"重新检测"按钮，一键重试 |
| **离线本地缓存** | 无网络时自动将识别记录保存至本地 JSON，Toast 提示 |
| **历史档案** | 查看所有云端同步的识别历史记录 |
| **用户反馈** | 识别准确/纠错反馈，支持专家审计 |
| **专家认证** | 输入邀请码升级专家权限，参与云端数据贡献 |

### 🖥️ 后端 API

| 模块 | 路由前缀 | 功能 |
|------|----------|------|
| 认证模块 | `/auth` | 注册、登录、JWT 签发、专家验证 |
| 推理模块 | `/predict` | 接收图片，PyTorch 推理，返回结果 |
| 反馈模块 | `/feedback` | 上传识别记录、提交用户反馈、获取历史 |
| 百科模块 | `/wiki` | 按病害标签返回症状与防治方案 |
| 专家模块 | `/expert` | 专家数据贡献、审计 |

---

## 🚀 快速启动

### 后端服务

```bash
cd web_flask/Backend

conda create -n env1 python==3.12.12 -y
conda activate env1

# 创建并激活环境 安装依赖 这是后端使用的环境
pip install -r ../requirements.txt


# 配置环境变量（复制 .env 并填写数据库、JWT 密钥）
cp .env.example .env

# 初始化管理员账号
python init_admin.py

# 启动服务（默认 5000 端口）
python app.py
```

### API启动

```bash
cd web_flask/APIs
# 激活环境（如果未激活的话）
conda activate env1

python app.py
```

### Android 客户端

1. 用 **Android Studio** 打开 `android/` 目录
2. 在 `NetworkClient.kt` 中配置后端 `BASE_URL`
3. 确保 TFLite 模型文件已放置在 `assets/` 目录
4. 点击 **Run** 即可运行到真机或模拟器

### 模型训练

```bash
cd trian_code

# 创建并激活训练专用环境（Python 3.10.x）
conda create -n torch python==3.10.19 -y
conda activate torch

# 安装依赖（包含 torch==2.10.0+cu128，需要 CUDA 12.8）
pip install -r req.txt

# ⚠️  GPU 要求：本项目在 NVIDIA RTX 5060（Blackwell 架构）上训练
# 依赖 CUDA 12.8，请确认驱动版本 ≥ 570.x
# 如使用其他 GPU（如 RTX 40xx / 30xx 系列），请对应修改 req.txt 中的
# torch 版本，例如 torch==2.5.1+cu121

# 准备 PlantVillage 数据集到 dataset_plant/ 目录
# 生成标签文件
python gen_labels.py

# 开始训练
python pytoch_train.py

# 转换为 TFLite 格式（用于 Android 端侧推理）
python pth_to_tflite.py
```

---

## 🛠️ 技术栈

**Android 端**
- Kotlin + Jetpack Compose（声明式 UI）
- ViewModel + StateFlow（状态管理）
- Retrofit2（网络请求）
- TensorFlow Lite（端侧推理）
- DataStore（本地持久化）
- Coil（图片加载）

**后端**
- Python **3.12.12** + Flask（Conda 环境 `env1`）
- PyTorch + torchvision（ResNet50 推理）
- MySQL **8.0.x**（关系型数据库）
- JWT（身份认证）
- LangChain（知识图谱/百科模块）

**模型训练**
- Python **3.10.19**（Conda 环境 `torch`）
- PyTorch **2.10.0+cu128** + torchvision 0.25.0（ResNet50 迁移学习）
- CUDA **12.8**，训练硬件：NVIDIA **RTX 5060**（Blackwell 架构）
- PlantVillage 开源数据集

---

## 📝 注意事项

- 模型仅供参考，AI 识别结果不能代替专业农技人员的判断
- 离线识别记录保存在 App 私有目录，卸载后将丢失
- 专家认证需联系管理员获取邀请码
- **GPU / CUDA 兼容性**：训练脚本依赖 `torch==2.10.0+cu128`，需要 CUDA 12.8 及以上版本；若使用其他显卡（如 RTX 40xx/30xx 系列），请自行替换 `req.txt` 中的 torch 版本以匹配对应的 CUDA 工具链
- **MySQL 版本**：后端仅在 MySQL **8.0.x** 下测试，不保证兼容更低版本

---

## 📄 License

本项目使用 [MIT License](./LICENSE) 开源协议。
