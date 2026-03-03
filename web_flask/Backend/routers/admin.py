from flask import Blueprint, request, jsonify, session, render_template, redirect, url_for
from shared.models.model import db, User, RecognitionHistory, Contribution, ExpertInviteCode, Feedback
from datetime import datetime, timedelta
import secrets
from functools import wraps
import os
import json

BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LABEL_PATH = os.path.join(BASE_DIR, "APIs", "modelsx", "labels_01.txt")
KNOWLEDGE_BASE_PATH = os.path.join(BASE_DIR, "shared", "assets", "DISEASE_KNOWLEDGE_BASE.json")

def load_labels():
    try:
        with open(LABEL_PATH, "r", encoding="utf-8") as f:
            return [line.strip() for line in f.readlines() if line.strip()]
    except:
        return []

def load_knowledge_base():
    try:
        with open(KNOWLEDGE_BASE_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    except:
        return {}

CLASS_NAMES = load_labels()
DISEASE_KNOWLEDGE_BASE = load_knowledge_base()

admin_bp = Blueprint("admin", __name__, template_folder="../templates", static_folder="../static")

def admin_required(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        if 'admin_id' not in session:
            if request.path.startswith('/admin/api'):
                return jsonify({"msg": "Admin privileges required"}), 403
            return redirect(url_for('admin.login_page'))
        # 验证是否真的是管理员
        user = User.query.get(session['admin_id'])
        if not user or not user.is_admin:
            session.pop('admin_id', None)
            if request.path.startswith('/admin/api'):
                return jsonify({"msg": "Not authorized"}), 403
            return redirect(url_for('admin.login_page'))
        return f(*args, **kwargs)
    return decorated_function

# ==================== 页面路由 ====================

@admin_bp.route('/login', methods=['GET'])
def login_page():
    if 'admin_id' in session:
        return redirect(url_for('admin.dashboard_page'))
    return render_template('admin_login.html')

@admin_bp.route('/', methods=['GET'])
@admin_bp.route('/dashboard', methods=['GET'])
@admin_required
def dashboard_page():
    return render_template('admin_panel.html')

# ==================== 后端 API ====================

@admin_bp.route('/api/login', methods=['POST'])
def api_login():
    data = request.json
    username = data.get("username", None)
    password = data.get("password", None)
    
    if not username or not password:
        return jsonify({"msg": "Missing username or password"}), 400
        
    user = User.query.filter_by(username=username).first()
    if not user or not user.check_password(password) or not user.is_admin:
        return jsonify({"msg": "Bad username or password or not admin"}), 401
    
    # 使用 Flask session 持有会话
    session['admin_id'] = user.id
    return jsonify({"msg": "Login successful"}), 200

@admin_bp.route('/api/logout', methods=['POST'])
def api_logout():
    session.pop('admin_id', None)
    return jsonify({"msg": "Logged out"}), 200

# 1. Dashboard 统计信息
@admin_bp.route('/api/stats', methods=['GET'])
@admin_required
def get_stats():
    total_users = User.query.count()
    expert_count = User.query.filter_by(is_expert=True).count()
    
    # 假设39类基础病害，加上新增采纳的贡献
    # 其实可以说总检测次数 / API调用量
    total_recognitions = RecognitionHistory.query.count()
    total_contributions = Contribution.query.filter_by(status='已采纳').count()
    disease_base = 39
    
    return jsonify({
        "total_users": total_users,
        "expert_count": expert_count,
        "total_api_calls": total_recognitions,
        "total_diseases_in_db": disease_base + total_contributions
    }), 200

# 2. 专家准入管理
@admin_bp.route('/api/invites', methods=['GET', 'POST'])
@admin_required
def manage_invites():
    if request.method == 'GET':
        codes = ExpertInviteCode.query.order_by(ExpertInviteCode.created_at.desc()).all()
        now = datetime.now()
        res = []
        for c in codes:
            status = "未使用"
            if c.is_used:
                status = "已使用"
            elif c.is_revoked:
                status = "已撤销"
            elif c.expires_at < now:
                status = "已过期"
                
            res.append({
                "id": c.id,
                "code": c.code,
                "target": c.target_username,
                "status": status,
                "created_at": c.created_at.strftime("%Y-%m-%d %H:%M:%S"),
                "expires_at": c.expires_at.strftime("%Y-%m-%d %H:%M:%S")
            })
        return jsonify(res), 200
        
    if request.method == 'POST':
        data = request.json
        target = data.get("username", None)
        
        # 补充：如果有 target username，向数据库中校验是否有这个用户
        if target:
            target_user = User.query.filter_by(username=target).first()
            if not target_user:
                return jsonify({"msg": f"用户 '{target}' 不存在"}), 400
            if target_user.is_expert:
                return jsonify({"msg": f"用户 '{target}' 已经是专家，无需验证码"}), 400
        
        # 生成 8 位十六进制 (4 bytes)
        new_code = secrets.token_hex(4)
        expires = datetime.now() + timedelta(hours=24)
        
        invite = ExpertInviteCode(code=new_code, target_username=target, expires_at=expires)
        db.session.add(invite)
        db.session.commit()
        return jsonify({"msg": "Invite code generated", "code": new_code}), 201

@admin_bp.route('/api/invites/<int:id>/revoke', methods=['PATCH'])
@admin_required
def revoke_invite(id):
    invite = ExpertInviteCode.query.get_or_404(id)
    if invite.is_used:
        return jsonify({"msg": "Cannot revoke used code"}), 400
    invite.is_revoked = True
    db.session.commit()
    return jsonify({"msg": "Invite code revoked"}), 200

# 3. 知识贡献审核
@admin_bp.route('/api/contributions', methods=['GET'])
@admin_required
def get_contributions():
    # Fetch pending first, then others
    status_filter = request.args.get('status', '待审核')
    if status_filter == 'all':
        contribs = Contribution.query.order_by(Contribution.created_at.desc()).all()
    else:
        contribs = Contribution.query.filter_by(status=status_filter).order_by(Contribution.created_at.desc()).all()
        
    res = []
    for c in contribs:
        expert = User.query.get(c.expert_id)
        res.append({
            "id": c.id,
            "expert_name": expert.username if expert else "Unknown",
            "image_url": c.image_url,
            "disease_name": c.disease_name,
            "treatment_plan": c.treatment_plan,
            "status": c.status,
            "reject_reason": c.reject_reason,
            "created_at": c.created_at.strftime("%Y-%m-%d %H:%M:%S")
        })
    return jsonify(res), 200

@admin_bp.route('/api/contributions/<int:id>/review', methods=['PATCH'])
@admin_required
def review_contribution(id):
    data = request.json
    action = data.get("action") # 'approve' or 'reject'
    reason = data.get("reason", "")
    
    contrib = Contribution.query.get_or_404(id)
    if action == 'approve':
        contrib.status = '已采纳'
    elif action == 'reject':
        contrib.status = '已驳回'
        contrib.reject_reason = reason
    else:
        return jsonify({"msg": "Invalid action"}), 400
        
    db.session.commit()
    return jsonify({"msg": f"Contribution {action}d"}), 200


# 4. 用户与审计
@admin_bp.route('/api/users', methods=['GET'])
@admin_required
def get_users():
    users = User.query.all()
    res = []
    for u in users:
        res.append({
            "id": u.id,
            "username": u.username,
            "is_expert": u.is_expert,
            "is_admin": u.is_admin,
            "is_active": u.is_active
        })
    return jsonify(res), 200

@admin_bp.route('/api/users/<int:id>/ban', methods=['PATCH'])
@admin_required
def ban_user(id):
    data = request.json
    is_active = data.get("is_active", False)
    user = User.query.get_or_404(id)
    if user.is_admin:
        return jsonify({"msg": "Cannot ban another admin"}), 400
        
    user.is_active = is_active
    db.session.commit()
    status_msg = "activated" if is_active else "banned"
    return jsonify({"msg": f"User {status_msg}"}), 200

# 5. 数据字典与百科映射
@admin_bp.route('/api/dictionary', methods=['GET'])
@admin_required
def get_dictionary():
    res = []
    # 组合已有的 CLASS_NAMES 和 DISEASE_KNOWLEDGE_BASE
    for index, label in enumerate(CLASS_NAMES):
        info = DISEASE_KNOWLEDGE_BASE.get(label, {})
        res.append({
            "index": index,
            "label": label,
            "common_name": info.get("common_name", "暂无"),
            "symptoms": info.get("symptoms", "暂无描述"),
            "standard_treatment": info.get("standard_treatment", "暂无建议")
        })
    return jsonify(res), 200


# 6. 用户反馈审计
@admin_bp.route('/api/feedbacks', methods=['GET'])
@admin_required
def get_feedbacks():
    """列出所有用户反馈，支持按 score 过滤"""
    score_filter = request.args.get('score', 'all')  # 'all' | '-1' | '1'
    audit_filter = request.args.get('audit', 'pending')  # 'pending' | 'all'

    query = db.session.query(Feedback, RecognitionHistory, User).join(
        RecognitionHistory, Feedback.history_id == RecognitionHistory.id
    ).join(
        User, Feedback.user_id == User.id
    )

    if score_filter == '-1':
        query = query.filter(Feedback.score == -1)
    elif score_filter == '1':
        query = query.filter(Feedback.score == 1)

    if audit_filter == 'pending':
        query = query.filter(Feedback.audit_status == None)

    rows = query.order_by(Feedback.id.desc()).all()

    res = []
    for fb, hist, user in rows:
        img_url = None
        if hist.image_path:
            img_url = f"/uploads/recognition/{hist.image_path}"
        res.append({
            "id": fb.id,
            "username": user.username,
            "score": fb.score,
            "reason": fb.reason or "",
            "suggestion": fb.suggestion or "",
            "audit_status": fb.audit_status,
            "prediction": hist.prediction,
            "common_name": DISEASE_KNOWLEDGE_BASE.get(hist.prediction, {}).get("common_name", hist.prediction),
            "confidence": f"{float(hist.confidence)*100:.2f}%",
            "image_url": img_url,
            "created_at": hist.created_at.strftime("%Y-%m-%d %H:%M")
        })
    return jsonify(res), 200


@admin_bp.route('/api/feedbacks/<int:id>/audit', methods=['PATCH'])
@admin_required
def audit_feedback(id):
    """审计一条反馈: action = 'adopt'(采纳) | 'ignore'(忽略/误报)"""
    data = request.json
    action = data.get('action')  # 'adopt' | 'ignore'
    if action not in ('adopt', 'ignore'):
        return jsonify({"msg": "Invalid action"}), 400

    fb = Feedback.query.get_or_404(id)
    fb.audit_status = action
    db.session.commit()
    return jsonify({"msg": f"Feedback {id} marked as {action}"}), 200
