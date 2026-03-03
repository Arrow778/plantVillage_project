import uuid
import os
import io
from flask import Blueprint, request, jsonify
from flask_jwt_extended import jwt_required, get_jwt_identity
from shared.models.model import User, RecognitionHistory, db
from shared.tools.printTools import PrintTools as pt
from PIL import Image
import numpy as np
import torch
import torch.nn as nn
from torchvision import models, transforms

import json


from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.messages import HumanMessage, SystemMessage

predict_bp = Blueprint("predict", __name__)

# --- 配置 ---

#  模型配置
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UPLOAD_FOLDER = os.path.join(BASE_DIR, "uploads", "recognition")
LABEL_PATH = os.path.join(BASE_DIR, "modelsx", "labels_01.txt")
KNOWLEDGE_BASE_PATH = os.path.join(os.path.dirname(BASE_DIR), "shared", "assets", "DISEASE_KNOWLEDGE_BASE.json")

# langchain配置
from dotenv import load_dotenv

load_dotenv()
# load_dotenv()  # 加载环境变量
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL")  # "https://api.deepseek.com"


if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)


# --- 1. 动态加载标签 ---
def load_labels(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return [line.strip() for line in f.readlines() if line.strip()]
    except Exception as e:
        pt.error(f"无法加载标签文件: {e}")
        # 备用方案：如果文件读取失败，至少保证程序不崩溃
        return ["Unknown"]


CLASS_NAMES = load_labels(LABEL_PATH)
pt.success(f"成功加载 {len(CLASS_NAMES)} 个类别标签")
# 打印前几个标签供用户核对顺序
pt.debug(f"标签前5名: {CLASS_NAMES[:5]}")
pt.debug(f"标签后5名: {CLASS_NAMES[-5:]}")


def softmax(x):
    e_x = np.exp(x - np.max(x))
    return e_x / e_x.sum()

# --- 2.5 初始化 PyTorch 模型 ---
try:
    PTH_MODEL_PATH = os.path.join(BASE_DIR, "modelsx", "plant_v2_0301_1754.pth")
    # 强制在 API 端使用 CPU 推理，避免 CUDA kernel image 不匹配的错误
    device = torch.device('cpu')
    num_classes = len(CLASS_NAMES)
    
    # 改用更强大的 ResNet50 作为云端专家模型
    pytorch_model = models.resnet50(weights=None)
    num_ftrs = pytorch_model.fc.in_features
    pytorch_model.fc = nn.Sequential(
        nn.Dropout(0.5), nn.Linear(num_ftrs, num_classes)
    )
    pytorch_model.load_state_dict(torch.load(PTH_MODEL_PATH, map_location=device))
    pytorch_model.to(device)
    pytorch_model.eval()
    pt.success(f"PyTorch 模型初始化成功 (运行在 {device})")
except Exception as e:
    pt.error(f"PyTorch 模型加载失败: {e}")
    pytorch_model = None

pytorch_transform = transforms.Compose([
    transforms.Resize(256),              # 先等比缩放，短边到256
    transforms.CenterCrop(224),          # 居中裁剪，不破坏叶片长宽比例
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
])




# --- 3. 核心 API 接口 ---
@predict_bp.route("/cloud", methods=["POST"])
@jwt_required()
def cloud_predict():
    try:
        if pytorch_model is None:
            return jsonify({"status": "error", "message": "PyTorch model is not available"}), 500

        # 1. 基础校验
        if "file" not in request.files:
            return jsonify({"status": "error", "message": "No file uploaded"}), 400

        file = request.files["file"]
        if file.filename == "":
            return jsonify({"status": "error", "message": "No selected file"}), 400

        # 2. 保存图片文件
        file_ext = os.path.splitext(file.filename)[1].lower()
        if not file_ext:
            file_ext = ".jpg"
        unique_filename = f"{uuid.uuid4()}{file_ext}"
        save_path = os.path.join(UPLOAD_FOLDER, unique_filename)

        img_bytes = file.read()
        with open(save_path, "wb") as f:
            f.write(img_bytes)

        # 3. 图像处理与模型推理 (引入 TTA: 测试时增强)
        img = Image.open(io.BytesIO(img_bytes)).convert("RGB")
        
        # 定义四种变换：原图、水平翻转、垂直翻转
        img_variants = [
            img,
            img.transpose(Image.FLIP_LEFT_RIGHT),
            img.transpose(Image.FLIP_TOP_BOTTOM)
        ]
        
        all_probs = []
        for variant in img_variants:
            input_tensor = pytorch_transform(variant).unsqueeze(0).to(device)
            with torch.no_grad():
                outputs = pytorch_model(input_tensor)
                p = torch.nn.functional.softmax(outputs[0], dim=0).cpu().numpy()
                all_probs.append(p)
        
        # 取均值，增加稳定性
        probs = np.mean(all_probs, axis=0)
        top_indices = probs.argsort()[-3:][::-1]

        # 打印 Top 3 到控制台，方便分析
        pt.info(f"==== Cloud Predict Debug (TTA Averaged) ====")
        for i, idx in enumerate(top_indices):
            pt.debug(f"Top {i+1}: {CLASS_NAMES[idx]} ({probs[idx]*100:.2f}%)")

        # 4. 构建响应
        top_prob = float(probs[top_indices[0]])
        if top_prob >= 0.9999:
            top_prob = 0.9998

        result = {
            "status": "success",
            "data": {
                "prediction": (
                    CLASS_NAMES[top_indices[0]]
                    if top_indices[0] < len(CLASS_NAMES)
                    else "Unknown"
                ),
                "confidence": top_prob,
                "image_id": unique_filename,
                "top3": [
                    {
                        "class": CLASS_NAMES[idx],
                        "prob": f"{0.9998 * 100:.2f}%" if float(probs[idx]) >= 0.9999 else f"{probs[idx] * 100:.2f}%"
                    }
                    for idx in top_indices if idx < len(CLASS_NAMES)
                ],
                "engine": "PyTorch",
            },
        }

        # 将结果存入到数据库中；
        current_user_id = get_jwt_identity()
        user = User.query.get(int(current_user_id))
        
        if not user:
            return jsonify({"status": "error", "message": "User not found"}), 404

        new_record = RecognitionHistory(
            user_id=user.id,
            image_path=unique_filename,
            prediction=CLASS_NAMES[top_indices[0]],
            confidence=top_prob,
            engine="pth",
        )
        db.session.add(new_record)
        db.session.commit()

        return jsonify(result), 200

    except Exception as e:
        pt.error(f"PyTorch 推理接口异常: {str(e)}")
        return (
            jsonify(
                {
                    "status": "error",
                    "message": "Internal server error during inference",
                    "error_detail": (
                        str(e) if os.environ.get("FLASK_DEBUG") == "1" else None
                    ),
                }
            ),
            500,
        )


@predict_bp.route("/edge_sync", methods=["POST"])
@jwt_required()
def edge_sync():
    try:
        if "file" not in request.files:
            return jsonify({"status": "error", "message": "No file part"}), 400

        file = request.files["file"]
        if file.filename == "":
            return jsonify({"status": "error", "message": "No selected file"}), 400

        label = request.form.get("label", "Unknown")
        confidence = float(request.form.get("confidence", 0.0))

        file_ext = os.path.splitext(file.filename)[1].lower()
        if not file_ext:
            file_ext = ".jpg"
        unique_filename = f"{uuid.uuid4()}{file_ext}"
        save_path = os.path.join(UPLOAD_FOLDER, unique_filename)

        img_bytes = file.read()
        with open(save_path, "wb") as f:
            f.write(img_bytes)

        current_user_id = get_jwt_identity()
        user = User.query.get(int(current_user_id))

        if not user:
            return jsonify({"status": "error", "message": "User not found"}), 404

        new_record = RecognitionHistory(
            user_id=user.id,
            image_path=unique_filename,
            prediction=label,
            confidence=confidence,
            engine="tflite",
        )
        db.session.add(new_record)
        db.session.commit()

        return jsonify({"status": "success", "history_id": new_record.id}), 200

    except Exception as e:
        pt.error(f"边缘同步接口异常: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500


# 初始化 LangChain 聊天对象
llm = ChatOpenAI(
    model="deepseek-chat",
    openai_api_key=DEEPSEEK_API_KEY,
    openai_api_base=DEEPSEEK_BASE_URL,
    max_tokens=1024,
)

# --- 模拟病害知识库（从 JSON 文件加载） ---
def load_knowledge_base():
    try:
        with open(KNOWLEDGE_BASE_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:
        pt.error(f"无法加载病害知识库文件 {KNOWLEDGE_BASE_PATH}: {e}")
        return {}

DISEASE_KNOWLEDGE_BASE = load_knowledge_base()


@predict_bp.route("/treatment", methods=["GET"])
@jwt_required()
def get_treatment():
    try:
        # 1. 获取病害名称（从上一个识别接口拿到结果后传参过来）
        disease_name = request.args.get("disease_name")
        if not disease_name:
            return jsonify({"status": "error", "message": "Missing disease_name"}), 400

        # 2. 检索本地死数据（基础约束）
        knowledge = DISEASE_KNOWLEDGE_BASE.get(
            disease_name,
            {
                "common_name": disease_name,
                "symptoms": "未知症状",
                "standard_treatment": "建议咨询专业农业技术人员。",
            },
        )

        # 3. 构建 LangChain 防幻觉 Prompt
        system_prompt = (
            "你是一位资深的植物病理学家。你的回答必须严格基于提供的【基础知识】进行扩充和格式整理。"
            "如果基础知识中没有提及某类特定的化学用药，严禁你自行编造或产生幻觉。"
            "输出必须是原始的纯 JSON 格式（不要加上 ```json 标签），必须包含以下四个字段："
            "disease_name(通俗病名), symptoms(症状描述的口语化整理), treatment(治疗/用药建议), prevention(预防措施)。"
        )

        user_prompt = f"模型识别标签：{disease_name}\n【基础知识】：\n{json.dumps(knowledge, ensure_ascii=False)}\n\n请根据【基础知识】，生成这份结构的专业建议："

        # 4. 调用 DeepSeek (此处逻辑已写好，你可以先注释掉改为返回死数据)
        response = llm.invoke(
            [SystemMessage(content=system_prompt), HumanMessage(content=user_prompt)]
        )
        ai_content = response.content
        clean_json = ai_content.replace("```json", "").replace("```", "").strip()

        try:
            # 将字符串转为真实的 Python 字典
            treatment_dict = json.loads(clean_json)

            return (
                jsonify(
                    {
                        "query_id":str(uuid.uuid4()),
                        "status": "success",
                        "data": treatment_dict,  # 这样返回的就是漂亮的嵌套 JSON 了
                    }
                ),
                200,
            )
        except Exception as e:
            # 如果解析失败，说明 AI 返回的格式不对，返回原始数据备份
            return (
                jsonify(
                    {
                        "status": "partial_success",
                        "data": {"raw_text": ai_content},
                        "msg": "AI 返回格式非标准 JSON",
                    }
                ),
                200,
            )

    except Exception as e:
        pt.error(f"LangChain 接口异常: {str(e)}")
        return (
            jsonify({"status": "error", "message": "Failed to generate treatment"}),
            500,
        )


@predict_bp.route("/wiki/detail", methods=["GET"])
@jwt_required()
def get_wiki_detail():
    disease_name = request.args.get("disease_name")
    if not disease_name:
        return jsonify({"msg": "参数缺失"}), 400

    # 直接从内置动态 JSON 库中查找关联百科记录
    knowledge = DISEASE_KNOWLEDGE_BASE.get(disease_name)

    if knowledge:
        # 为了兼容前端，我们将现有结构转换为类似百科的展示结构
        # 这里还可以利用症状描绘中的关键字做一个简单的基础危险等级预判
        danger = "常规"
        if "腐烂" in str(knowledge) or "枯死" in str(knowledge) or "毁灭性" in str(knowledge):
            danger = "高危"

        wiki_info = {
            "title": knowledge.get("common_name", disease_name),
            "symptoms": knowledge.get("symptoms", "暂无明显症状记录"),
            "standard_treatment": knowledge.get("standard_treatment", "建议联系农技人员"),
            "danger_level": danger,
        }
        return jsonify({"query_id":str(uuid.uuid4()),"status": "success", "data": wiki_info}), 200
    else:
        return jsonify({"query_id":str(uuid.uuid4()),"msg": "基础百科字典库中暂未收录该病害标签"}), 404
