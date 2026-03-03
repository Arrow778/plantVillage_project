from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime

db = SQLAlchemy()


class User(db.Model):
    __tablename__ = "users"
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(80), unique=True, nullable=False)
    email = db.Column(db.String(120), unique=True, nullable=True)
    password_hash = db.Column(db.String(255), nullable=False)
    is_expert = db.Column(db.Boolean, default=False)  # 专家标志
    expert_code = db.Column(db.String(20), nullable=True)  # 专家验证码
    is_admin = db.Column(db.Boolean, default=False)  # 系统管理员标志
    is_active = db.Column(db.Boolean, default=True)  # 用户状态（封禁标志）

    def set_password(self, password):
        self.password_hash = generate_password_hash(password)

    def check_password(self, password):
        return check_password_hash(self.password_hash, password)


# 我们需要一张表来存被拉黑的 Token：
class TokenBlocklist(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    jti = db.Column(db.String(36), nullable=False, index=True)  # JWT 的唯一标识
    created_at = db.Column(db.DateTime, server_default=db.func.now())


class RecognitionHistory(db.Model):
    __tablename__ = "recognition_history"
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False)
    image_path = db.Column(db.String(200))
    prediction = db.Column(db.String(100))
    confidence = db.Column(db.Float)
    engine = db.Column(db.String(20), default="tflite")
    created_at = db.Column(db.DateTime, default=datetime.now)

    # 关联评价（一对一）
    feedback = db.relationship("Feedback", backref="history", uselist=False)


class Feedback(db.Model):
    __tablename__ = "feedback"
    id = db.Column(db.Integer, primary_key=True)
    history_id = db.Column(
        db.Integer, db.ForeignKey("recognition_history.id"), nullable=False
    )
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False)
    score = db.Column(db.Integer)  # 1 为点赞，-1 为点踩
    reason = db.Column(db.String(200))  # 错误原因
    suggestion = db.Column(db.Text)  # 修改建议
    audit_status = db.Column(db.String(20), nullable=True)  # null=待审计, 'adopt'=采纳, 'ignore'=误报忽略


class Contribution(db.Model):
    __tablename__ = "contributions"
    id = db.Column(db.Integer, primary_key=True)
    expert_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False)
    image_url = db.Column(db.String(200))  # 专家上传的参考图
    disease_name = db.Column(db.String(100))
    treatment_plan = db.Column(db.Text)
    status = db.Column(db.String(20), default="待审核")  # 待审核/已采纳/已驳回
    reject_reason = db.Column(db.Text, nullable=True)  # 驳回原因
    created_at = db.Column(db.DateTime, default=datetime.now)

class ExpertInviteCode(db.Model):
    __tablename__ = "expert_invite_codes"
    id = db.Column(db.Integer, primary_key=True)
    code = db.Column(db.String(8), unique=True, nullable=False)  # 8位十六进制随机码
    target_username = db.Column(db.String(80), nullable=True)    # 绑定的 username
    is_used = db.Column(db.Boolean, default=False)               # 是否已使用
    is_revoked = db.Column(db.Boolean, default=False)            # 是否被管理员撤销
    created_at = db.Column(db.DateTime, default=datetime.now)
    expires_at = db.Column(db.DateTime, nullable=False)          # 24小时过期限制
