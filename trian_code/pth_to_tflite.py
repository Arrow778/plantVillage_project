import sys
import os

# 该环境使用tf_gpu环境运行

# ================= 兼容性补丁 (必须在所有 import 之前) =================
try:
    import tensorflow as tf

    try:
        import tf_keras
    except ImportError:
        # 强制将 tensorflow.keras 映射为 tf_keras，欺骗 onnx2tf
        import tensorflow.keras as keras

        sys.modules["tf_keras"] = keras
    print("✅ 已完成 tf_keras 兼容性映射")
except Exception as e:
    print(f"⚠️ 环境预检查警告: {e}")

import torch
import torch.nn as nn
from torchvision import models

# ================= 配置区 (请根据你的实际路径修改) =================
PTH_FILE = "models/plant_v2_0301_1754.pth"  # 你的权重文件
NUM_CLASSES = 39  # 你的植物类别数
ONNX_PATH = "models/resnet50_0301.onnx"  # 中转文件名
TFLITE_DIR = "models/tflite_output_resnet50"  # TFLite 输出文件夹
# =================================================================


def run_conversion():
    # 1. 检查并创建输出目录
    if not os.path.exists("models"):
        os.makedirs("models")

    # 2. 重建 PyTorch 模型结构 (现在是 ResNet50)
    print("\n[Step 1/3] 正在从 PTH 加载 PyTorch 模型...")
    model = models.resnet50(weights=None)
    num_ftrs = model.fc.in_features
    model.fc = nn.Sequential(
        nn.Dropout(0.5), 
        nn.Linear(num_ftrs, NUM_CLASSES)
    )

    # 加利加载权重
    if not os.path.exists(PTH_FILE):
        print(f"❌ 错误：在路径 {PTH_FILE} 找不到模型文件！")
        return

    model.load_state_dict(torch.load(PTH_FILE, map_location="cpu"))
    model.eval()

    # 3. 导出 ONNX
    print("\n[Step 2/3] 正在导出 ONNX (中转格式)...")
    dummy_input = torch.randn(1, 3, 224, 224)
    torch.onnx.export(
        model,
        dummy_input,
        ONNX_PATH,
        export_params=True,
        opset_version=12,  # TFLite 对 12 的支持最稳
        do_constant_folding=True,
        input_names=["input"],
        output_names=["output"],
    )
    print(f"✅ ONNX 导出成功: {ONNX_PATH}")

    # 4. 调用 onnx2tf 转换为 TFLite
    print("\n[Step 3/3] 正在启动 onnx2tf 核心转换引擎...")
    # --osd 为转换时的图优化，--non_verbose 减少刷屏
    # 如果运行失败，可以尝试去掉 --osd
    cmd = f"onnx2tf -i {ONNX_PATH} -o {TFLITE_DIR}"

    exit_code = os.system(cmd)

    if exit_code == 0:
        print("\n" + "=" * 50)
        print("🎉 转换圆满完成！")
        print(f"📁 最终模型路径: {os.path.abspath(TFLITE_DIR)}/model_float32.tflite")
        print("💡 提示: 将此文件重命名后放入 Android 的 assets 目录即可。")
        print("=" * 50)
    else:
        print("\n❌ 转换过程中断，请检查上方报错信息。")


if __name__ == "__main__":
    run_conversion()
