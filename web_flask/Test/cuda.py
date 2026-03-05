import os
import sys

# 1. 这里的路径必须指向你刚才安装 nvidia 包的地方
# 你的路径大概率是这个，请仔细核对一下 bin 文件夹是否存在
nvidia_base_path = r"W:\Development_software\miniconda\envs\cuda311\Lib\site-packages\nvidia"

# 定义需要加载的几个核心路径
cuda_bins = [
    os.path.join(nvidia_base_path, "cuda_runtime", "bin"),
    os.path.join(nvidia_base_path, "cudnn", "bin"),
    os.path.join(nvidia_base_path, "cublas", "bin"),
    os.path.join(nvidia_base_path, "cusolver", "bin"),
    os.path.join(nvidia_base_path, "cusparse", "bin")
]

# 2. 核心黑魔法：手动把这些路径塞进 Windows 的 DLL 搜索列表
for path in cuda_bins:
    if os.path.exists(path):
        print(f"✅ 成功挂载 DLL 路径: {path}")
        os.add_dll_directory(path)
    else:
        print(f"❌ 路径不存在，请检查: {path}")

# 3. 正常导入 TF
import tensorflow as tf

print("-" * 30)
print("TensorFlow 版本:", tf.__version__)
gpus = tf.config.list_physical_devices('GPU')
if gpus:
    print("🚀 成功识别 GPU:", gpus)
else:
    print("😭 依然没有识别到 GPU，请检查显卡驱动是否支持 CUDA 12.4+")

