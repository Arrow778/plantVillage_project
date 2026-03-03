import torch
import torch.nn as nn
import torch.optim as optim
from torchvision import datasets, transforms, models
from torch.utils.data import DataLoader
from tqdm import tqdm
from PIL import Image
import os
from datetime import datetime
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.metrics import confusion_matrix
import numpy as np

# 该文件使用troch环境运行
# ======================
# 1. 配置常量
# ======================
CONFIG = {
    "IMG_SIZE": 224,
    "BATCH_SIZE": 64,
    "EPOCHS": 15,  # 增强数据后，模型需要更多轮次来学习
    "LEARNING_RATE": 0.0001,  # ResNet50 稍降一点学习率
    "VAL_RATE": 0.28,
    "DATASET_PATH": r"dataset_plant\data1",
    "MODEL_SAVE_PATH": "models",
}


def rgb_loader(path):
    with open(path, "rb") as f:
        img = Image.open(f)
        return img.convert("RGB")


def main():
    # 自动检测并优先使用 GPU 计算
    if torch.cuda.is_available():
        device = torch.device("cuda")
        gpu_name = torch.cuda.get_device_name(0)
        print(f"✅ 运行环境: {device} | 显卡型号: {gpu_name} (CUDA {torch.version.cuda})")
    else:
        device = torch.device("cpu")
        print(f"⚠️ 未检测到有效 CUDA，回退至 CPU 运行环境: {device}")

    # ======================
    # 2. 数据增强与加载 (强力抗干扰版)
    # ======================
    # 针对端侧/手机拍摄产生的各种光影失真、尺度扭曲、背景干扰进行增强
    train_transform = transforms.Compose(
        [
            # 1. 尺度防失真：随机裁剪并缩放，模拟用户远近不同的拍摄距离
            transforms.RandomResizedCrop(CONFIG["IMG_SIZE"], scale=(0.5, 1.0), ratio=(0.8, 1.2)),
            # 2. 角度不变性：自然界叶片朝向各异，增加旋转角度
            transforms.RandomRotation(45),
            # 3. 翻转
            transforms.RandomHorizontalFlip(p=0.5),
            transforms.RandomVerticalFlip(p=0.5),
            # 4. 光照防失真：剧烈的光照、对比度抖动，模拟闪光灯/背光/阴天
            transforms.ColorJitter(brightness=0.3, contrast=0.3, saturation=0.3, hue=0.1),
            # 5. 可选：引入少量高斯模糊，模拟手机相机对焦不准（要求 torchvision 版本较新）
            transforms.GaussianBlur(kernel_size=(5, 9), sigma=(0.1, 5)),
            # 格式转换与归一化
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
        ]
    )

    val_transform = transforms.Compose(
        [
            transforms.Resize((CONFIG["IMG_SIZE"], CONFIG["IMG_SIZE"])),
            transforms.ToTensor(),
            transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
        ]
    )

    if not os.path.exists(CONFIG["DATASET_PATH"]):
        print(f"❌ 错误：找不到数据集目录 {CONFIG['DATASET_PATH']}")
        return

    # 注意：这里有一个深度学习里常见的新手坑。
    # 用 random_split 时，如果直接修改 dataset.transform，会导致训练集和验证集应用相同的 transform。
    # 正确的做法是实例化两次 ImageFolder。
    train_dataset = datasets.ImageFolder(root=CONFIG["DATASET_PATH"], transform=train_transform, loader=rgb_loader)
    val_dataset = datasets.ImageFolder(root=CONFIG["DATASET_PATH"], transform=val_transform, loader=rgb_loader)

    # 保证划分的一致性，使用同样的随机数种子
    dataset_size = len(train_dataset)
    train_size = int((1 - CONFIG["VAL_RATE"]) * dataset_size)
    val_size = dataset_size - train_size

    generator = torch.Generator().manual_seed(42)
    
    # 获取切分后的索引
    from torch.utils.data import Subset
    indices = torch.randperm(dataset_size, generator=generator).tolist()
    
    train_data = Subset(train_dataset, indices[:train_size])
    val_data = Subset(val_dataset, indices[train_size:])

    train_loader = DataLoader(
        train_data, batch_size=CONFIG["BATCH_SIZE"], shuffle=True, num_workers=0
    )
    val_loader = DataLoader(
        val_data, batch_size=CONFIG["BATCH_SIZE"], shuffle=False, num_workers=0
    )

    num_classes = len(train_dataset.classes)
    print(f"📊 类别数: {num_classes} | 训练集: {train_size} | 验证集: {val_size}")

    # ======================
    # 3. 构建模型 (升级为重型专家网络 ResNet50)
    # ======================
    model = models.resnet50(weights=models.ResNet50_Weights.IMAGENET1K_V1)
    num_ftrs = model.fc.in_features
    # 替换最后的全连接层
    model.fc = nn.Sequential(
        nn.Dropout(0.5), 
        nn.Linear(num_ftrs, num_classes)
    )
    model = model.to(device)

    criterion = nn.CrossEntropyLoss()
    optimizer = optim.Adam(model.parameters(), lr=CONFIG["LEARNING_RATE"])

    # 用于保存绘图数据
    history = {'train_loss': [], 'val_loss': [], 'train_acc': [], 'val_acc': []}

    # ======================
    # 4. 训练核心循环
    # ======================
    all_final_preds = []
    all_final_labels = []
    for epoch in range(CONFIG["EPOCHS"]):
        model.train()
        train_loss, train_correct = 0.0, 0

        # 训练进度条
        train_pbar = tqdm(
            train_loader,
            desc=f"Epoch [{epoch+1}/{CONFIG['EPOCHS']}] (Train)",
            leave=False,
        )

        for images, labels in train_pbar:
            images, labels = images.to(device), labels.to(device)

            optimizer.zero_grad()
            outputs = model(images)
            loss = criterion(outputs, labels)
            loss.backward()
            optimizer.step()

            train_loss += loss.item() * images.size(0)
            _, preds = torch.max(outputs, 1)
            train_correct += torch.sum(preds == labels.data)

            # 进度条实时显示当前的 Loss 和 Acc
            current_acc = (torch.sum(preds == labels.data).double() / images.size(0)).item()
            train_pbar.set_postfix(loss=f"{loss.item():.4f}", acc=f"{current_acc:.4f}")

        avg_train_loss = train_loss / train_size
        avg_train_acc = (train_correct.double() / train_size).item()

        # 验证环节
        model.eval()
        val_loss, val_correct = 0.0, 0
        val_pbar = tqdm(
            val_loader, desc=f"Epoch [{epoch+1}/{CONFIG['EPOCHS']}] (Val)", leave=False
        )

        with torch.no_grad():
            for v_images, v_labels in val_pbar:
                v_images, v_labels = v_images.to(device), v_labels.to(device)
                v_outputs = model(v_images)
                v_batch_loss = criterion(v_outputs, v_labels)

                val_loss += v_batch_loss.item() * v_images.size(0)
                _, v_preds = torch.max(v_outputs, 1)
                val_correct += torch.sum(v_preds == v_labels.data)

                # 如果是最后一个 epoch，收集数据画混淆矩阵
                if epoch == CONFIG["EPOCHS"] - 1:
                    all_final_preds.extend(v_preds.cpu().numpy())
                    all_final_labels.extend(v_labels.data.cpu().numpy())

        avg_val_loss = val_loss / val_size
        avg_val_acc = (val_correct.double() / val_size).item()
        
        # 记录历史
        history['train_loss'].append(avg_train_loss)
        history['val_loss'].append(avg_val_loss)
        history['train_acc'].append(avg_train_acc)
        history['val_acc'].append(avg_val_acc)

        # 每一轮结束，统一打印完整指标
        print(
            f"✨ Epoch {epoch+1:02d} | Train Loss: {avg_train_loss:.4f} Acc: {avg_train_acc:.4f} | Val Loss: {avg_val_loss:.4f} Acc: {avg_val_acc:.4f}"
        )

    # ======================
    # 5. 可视化训练过程与混淆矩阵
    # ======================
    # 设置支持中文的字体（如果你的系统缺失这个字体，图表里的中文会变方块，但这不影响代码运行）
    plt.rcParams['font.sans-serif'] = ['SimHei']  
    plt.rcParams['axes.unicode_minus'] = False 

    # 1. 绘制历史折线图
    epochs_range = range(1, CONFIG["EPOCHS"] + 1)
    
    plt.figure(figsize=(12, 5))
    plt.subplot(1, 2, 1)
    plt.plot(epochs_range, history['train_loss'], label='Train Loss', marker='o')
    plt.plot(epochs_range, history['val_loss'], label='Val Loss', marker='o')
    plt.title('Training and Validation Loss')
    plt.xlabel('Epochs')
    plt.ylabel('Loss')
    plt.legend()

    plt.subplot(1, 2, 2)
    plt.plot(epochs_range, history['train_acc'], label='Train Accuracy', marker='o')
    plt.plot(epochs_range, history['val_acc'], label='Val Accuracy', marker='o')
    plt.title('Training and Validation Accuracy')
    plt.xlabel('Epochs')
    plt.ylabel('Accuracy')
    plt.legend()
    
    history_path = os.path.join(CONFIG["MODEL_SAVE_PATH"], "training_history.png")
    plt.tight_layout()
    plt.savefig(history_path)
    plt.close()
    print(f"📈 训练曲线图已保存至: {history_path}")

    # 2. 绘制混淆矩阵
    print("生成混淆矩阵中...")
    cm = confusion_matrix(all_final_labels, all_final_preds)
    plt.figure(figsize=(24, 20))
    sns.heatmap(cm, annot=False, fmt='d', cmap='Blues', 
                xticklabels=train_dataset.classes, 
                yticklabels=train_dataset.classes)
    plt.title('Validation Confusion Matrix (Final Epoch)', fontsize=20)
    plt.ylabel('True Label', fontsize=14)
    plt.xlabel('Predicted Label', fontsize=14)
    plt.xticks(rotation=90, fontsize=8)
    plt.yticks(rotation=0, fontsize=8)
    
    cm_path = os.path.join(CONFIG["MODEL_SAVE_PATH"], "confusion_matrix.png")
    plt.tight_layout()
    plt.savefig(cm_path)
    plt.close()
    print(f"🧩 混淆矩阵已保存至: {cm_path}")

    # ======================
    # 6. 保存结果
    # ======================
    if not os.path.exists(CONFIG["MODEL_SAVE_PATH"]):
        os.makedirs(CONFIG["MODEL_SAVE_PATH"])

    timestamp = datetime.now().strftime("%m%d_%H%M")
    save_path = os.path.join(CONFIG["MODEL_SAVE_PATH"], f"plant_v2_{timestamp}.pth")
    torch.save(model.state_dict(), save_path)
    print("-" * 50)
    print(f"🎉 训练任务圆满完成！\n💾 模型路径: {save_path}")


if __name__ == "__main__":
    main()
