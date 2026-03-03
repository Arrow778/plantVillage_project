from flask import Blueprint, request, jsonify
from flask_jwt_extended import create_access_token, jwt_required, get_jwt, get_jwt_identity
from shared.models.model import db, User, TokenBlocklist, ExpertInviteCode
from datetime import datetime
from shared.tools.printTools import PrintTools as pt

auth_bp = Blueprint("auth", __name__)


# --- 注册 API ---
@auth_bp.route("/register", methods=["POST"])
def register():
    data = request.get_json()
    if User.query.filter_by(username=data["username"]).first():
        pt.warning(f"注册失败: 用户名 {data['username']} 已存在")
        return jsonify({"msg": "用户名已存在"}), 400

    email = data.get("email")
    if email == "":
        email = None
        
    if email and User.query.filter_by(email=email).first():
        return jsonify({"msg": "邮箱已被注册"}), 400

    new_user = User(username=data["username"], email=email)
    new_user.set_password(data["password"])
    db.session.add(new_user)
    db.session.commit()
    pt.success(f"新用户注册成功: {data['username']}")
    return jsonify({"msg": "注册成功"}), 201


# --- 登录 API ---
@auth_bp.route("/login", methods=["POST"])
def login():
    data = request.get_json()
    if not data or "username" not in data or "password" not in data:
        return jsonify({"msg": "参数不完整，必须包含用户名和密码"}), 400

    user = User.query.filter_by(username=data["username"]).first()

    if user and user.check_password(data["password"]):
        if not getattr(user, 'is_active', True):
            pt.error(f"登录拦截: 用户 {data['username']} 已被封禁")
            return jsonify({"msg": "账号已被封禁"}), 403

        # 在 Token 中加入专家身份信息
        access_token = create_access_token(
            identity=f"{user.id}",
            additional_claims={"is_expert": user.is_expert},
        )
        pt.info(f"用户 {data['username']} 登录成功")
        return (
            jsonify(
                {
                    "access_token": access_token,
                    "username": user.username,
                    "is_expert": user.is_expert,
                }
            ),
            200,
        )

    pt.warning(f"登录失败: 尝试用户名 {data.get('username')}")
    return jsonify({"msg": "用户名或密码错误"}), 401


# --- 专家认证 API ---
@auth_bp.route("/verify_expert", methods=["POST"])
def verify_expert():
    data = request.get_json()
    username = data.get("username")
    code_str = data.get("expert_code")
    
    if not username or not code_str:
        return jsonify({"msg": "缺少用户名或邀请码"}), 400
        
    user = User.query.filter_by(username=username).first()
    if not user:
        pt.warning(f"专家认证失败: 用户 {username} 不存在")
        return jsonify({"msg": "用户专家不存在"}), 404
        
    if user.is_expert:
        return jsonify({"msg": "该用户已经是专家"}), 400

    invite = ExpertInviteCode.query.filter_by(code=code_str).first()
    if not invite:
        pt.warning(f"专家认证失败: 邀请码 {code_str} 无效")
        return jsonify({"msg": "邀请码不存在"}), 400
        
    if invite.is_used:
        return jsonify({"msg": "邀请码已被使用"}), 400
        
    if invite.is_revoked:
        return jsonify({"msg": "该邀请码已被管理员撤销"}), 400
        
    if invite.expires_at < datetime.now():
        return jsonify({"msg": "邀请码已过期"}), 400
        
    if invite.target_username and invite.target_username != username:
        return jsonify({"msg": f"该邀请码已绑定其他用户 ({invite.target_username})"}), 403

    # 认证成功，更新相关表
    user.is_expert = True
    user.expert_code = code_str
    
    invite.is_used = True
    
    db.session.commit()
    pt.success(f"专家认证通过: 用户 {username}, CODE: {code_str}")
    return jsonify({"msg": "专家身份认证成功"}), 200


@auth_bp.route("/logout", methods=["DELETE"])  # 建议用 DELETE 或 POST
@jwt_required()
def logout():
    # 获取当前 Token 的唯一标识 jti
    jti = get_jwt()["jti"]
    # 将 jti 存入黑名单
    db.session.add(TokenBlocklist(jti=jti))
    db.session.commit()

    pt.info(f"用户已退出登录并回收 Token (JTI: {jti})")
    return jsonify({"msg": "成功退出登录，Token 已失效"}), 200

@auth_bp.route("/me", methods=["GET"])
@jwt_required()
def me():
    # 获取当前用户的 ID
    current_user_id = get_jwt_identity()
    user = User.query.get(int(current_user_id))
    if not user:
        return jsonify({"msg": "用户未找到"}), 404

    from shared.models.model import RecognitionHistory
    # 统计识别总次数
    total_recognitions = RecognitionHistory.query.filter_by(user_id=user.id).count()

    return jsonify({
        "username": user.username,
        "is_expert": user.is_expert,
        "is_admin": user.is_admin,
        "total_recognitions": total_recognitions
    }), 200
