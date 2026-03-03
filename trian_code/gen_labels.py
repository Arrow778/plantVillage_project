import os

# ================= 配置区 =================
DATASET_PATH = r"dataset_plant/data1"  # 填入你训练集文件夹的路径
OUTPUT_FILE = "labels/labels_01.txt"  # 输出路径
# =========================================


def generate_labels():
    if not os.path.exists(DATASET_PATH):
        print(f"❌ 错误：找不到路径 {DATASET_PATH}")
        return

    # 1. 获取所有子文件夹名称
    # 2. 过滤掉隐藏文件（如 .DS_Store）
    # 3. 严格执行字母顺序排序（非常重要！必须与 PyTorch 训练时的索引一致）
    classes = [
        d
        for d in os.listdir(DATASET_PATH)
        if os.path.isdir(os.path.join(DATASET_PATH, d))
    ]
    classes.sort()

    print(f"📊 扫描完成，共发现 {len(classes)} 个类别。")

    # 确保输出目录存在
    os.makedirs(os.path.dirname(OUTPUT_FILE), exist_ok=True)

    # 4. 写入文件
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        for i, class_name in enumerate(classes):
            f.write(f"{class_name}\n")
            # print(f"Index {i}: {class_name}") # 调试用

    print(f"✅ 标签文件已生成至: {OUTPUT_FILE}")
    print("🚀 请核对：第一行应该是你文件夹排序最靠前的类名。")


if __name__ == "__main__":
    generate_labels()
