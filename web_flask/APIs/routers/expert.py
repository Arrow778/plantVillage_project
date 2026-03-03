import os
import uuid
from flask import Blueprint, request, jsonify
from flask_jwt_extended import jwt_required, get_jwt
from shared.models.model import db, Contribution, User
from shared.tools.printTools import PrintTools as pt

expert_bp = Blueprint("expert", __name__)

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UPLOAD_FOLDER = os.path.join(BASE_DIR, "uploads", "contributions")

if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)
    pt.info(f"专家贡献上传文件夹已创建: {UPLOAD_FOLDER}")


@expert_bp.route("/contribute", methods=["POST"])
@jwt_required()
def contribute_knowledge():
    # 权限校验：必须是专家
    claims = get_jwt()
    if not claims.get("is_expert"):
        pt.warning(f"非法尝试: 非专家用户 (ID: {claims.get('sub')}) 尝试提交专家建议")
        return jsonify({"msg": "权限不足，请先完成专家认证"}), 403

    disease_name = request.form.get("disease_name")
    treatment_plan = request.form.get("treatment_plan")
    
    if not disease_name or not treatment_plan:
        return jsonify({"msg": "病理名称和防治方法为必填项"}), 400

    saved_images = []
    # 接收客户端的多图上传（字段名为 images）
    files = request.files.getlist("images")
    for file in files[:3]:  # 限制最多3张
        if file and file.filename != "":
            file_ext = os.path.splitext(file.filename)[1].lower()
            unique_filename = f"{uuid.uuid4()}{file_ext}"
            save_path = os.path.join(UPLOAD_FOLDER, unique_filename)
            file.save(save_path)
            saved_images.append(unique_filename)
            
    # 如果没有上传，默认为空，或 default.jpg
    image_url_str = ",".join(saved_images) if saved_images else "default.jpg"

    new_contri = Contribution(
        expert_id=claims.get("sub"),  # identity 通常存的是 ID 或用户名
        disease_name=disease_name,
        treatment_plan=treatment_plan,
        image_url=image_url_str,
    )
    db.session.add(new_contri)
    db.session.commit()
    pt.success(f"专家 (ID: {claims.get('sub')}) 提交了新的病理贡献: {disease_name}")
    return jsonify({"status": "success", "msg": "感谢您的知识贡献，审核中"}), 201

@expert_bp.route("/contribute/<int:contri_id>", methods=["PUT", "POST"])
@jwt_required()
def resubmit_knowledge(contri_id):
    claims = get_jwt()
    if not claims.get("is_expert"):
        pt.warning(f"非法尝试: 非专家用户 (ID: {claims.get('sub')}) 尝试修改专家建议")
        return jsonify({"msg": "权限不足，请先完成专家认证"}), 403

    contri = Contribution.query.get(contri_id)
    if not contri:
        return jsonify({"msg": "贡献记录未找到"}), 404
        
    if str(contri.expert_id) != str(claims.get("sub")):
        return jsonify({"msg": "只能修改自己的贡献记录"}), 403
        
    disease_name = request.form.get("disease_name")
    treatment_plan = request.form.get("treatment_plan")
    
    if not disease_name or not treatment_plan:
        return jsonify({"msg": "病理名称和防治方法为必填项"}), 400

    saved_images = []
    files = request.files.getlist("images")
    for file in files[:3]:
        if file and file.filename != "":
            file_ext = os.path.splitext(file.filename)[1].lower()
            unique_filename = f"{uuid.uuid4()}{file_ext}"
            save_path = os.path.join(UPLOAD_FOLDER, unique_filename)
            file.save(save_path)
            saved_images.append(unique_filename)
            
    if saved_images:
        # 如果新上传了图片，则覆盖旧图片。也可以选择保留，这里简单覆盖。
        contri.image_url = ",".join(saved_images)
        
    contri.disease_name = disease_name
    contri.treatment_plan = treatment_plan
    contri.status = "待审核"
    contri.reject_reason = None
    
    db.session.commit()
    pt.info(f"专家 (ID: {claims.get('sub')}) 重新提交了建议 (记录 ID: {contri_id})")
    return jsonify({"status": "success", "msg": "重新提交成功，审核中"}), 200


@expert_bp.route("/stats", methods=["GET"])
@jwt_required()
def get_expert_stats():
    # 获取该专家的贡献统计
    claims = get_jwt()
    contributions = Contribution.query.filter_by(expert_id=claims.get("sub")).all()

    return jsonify(
        {
            "total": len(contributions),
            "pending": len([c for c in contributions if c.status == "待审核"]),
            "accepted": len([c for c in contributions if c.status == "已采纳"]),
        }
    )

@expert_bp.route("/list", methods=["GET"])
@jwt_required()
def get_expert_contributions():
    # 获取该专家的贡献分页列表
    claims = get_jwt()
    if not claims.get("is_expert"):
        return jsonify({"msg": "权限不足，请先完成专家认证"}), 403

    page = request.args.get("page", 1, type=int)
    size = request.args.get("size", 3, type=int)
    
    pagination = Contribution.query.filter_by(expert_id=claims.get("sub")).order_by(Contribution.created_at.desc()).paginate(page=page, per_page=size, error_out=False)
    
    items = []
    for c in pagination.items:
        items.append({
            "id": c.id,
            "disease_name": c.disease_name,
            "status": c.status,
            "reject_reason": c.reject_reason,
            "treatment_plan": c.treatment_plan,
            "image_url": c.image_url,
            "created_at": c.created_at.strftime("%Y-%m-%d %H:%M:%S") if c.created_at else None
        })
        
    return jsonify({
        "items": items,
        "total": pagination.total,
        "pages": pagination.pages,
        "current_page": page
    })
