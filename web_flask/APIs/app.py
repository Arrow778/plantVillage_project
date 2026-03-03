import os
import sys

# 将 web_flask 加入路径，以便使用 from shared.models 引用
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from flask import Flask, redirect, send_from_directory
from flask_jwt_extended import JWTManager
from shared.models.model import db, TokenBlocklist
from routers.auth import auth_bp
from routers.predict import predict_bp
from routers.feedback import feedback_bp
from routers.expert import expert_bp
from shared.tools.printTools import PrintTools as pt
# 基础库引用已移至上方 sys.path 处理后

app = Flask(__name__)

from dotenv import load_dotenv

load_dotenv()

# 配置
app.config["SQLALCHEMY_DATABASE_URI"] = os.getenv(
    "SQLALCHEMY_DATABASE_URI"
)  # "sqlite:///plant_system.db"
app.config["JWT_SECRET_KEY"] = os.getenv(
    "JWT_SECRET_KEY"
)  # "super-secret-key-123"  # 建议换成复杂的
app.secret_key = os.getenv("FLASK_SECRET_KEY", "admin-dashboard-secret-key-456")

# 初始化插件
db.init_app(app)
jwt = JWTManager(app)

# 注册蓝图 (访问路径会自动带上 /api/v1/auth)
app.register_blueprint(auth_bp, url_prefix="/api/v1/auth")
app.register_blueprint(predict_bp, url_prefix="/api/v1/predict")
app.register_blueprint(feedback_bp, url_prefix="/api/v1/feedback")
app.register_blueprint(expert_bp, url_prefix="/api/v1/expert")


# 这个装饰器会在每个受保护的请求（@jwt_required）之前自动运行
@jwt.token_in_blocklist_loader
def check_if_token_revoked(jwt_header, jwt_payload):
    jti = jwt_payload["jti"]
    token = TokenBlocklist.query.filter_by(jti=jti).scalar()
    return token is not None  # 如果返回 True，则代表 Token 已失效


@app.route("/")
def index():
    return "PlantVillage API is running", 200

@app.route("/uploads/<category>/<filename>")
def uploaded_file(category, filename):
    if category not in ["contributions", "recognition"]:
        return "Not found", 404
    dir_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "uploads", category)
    return send_from_directory(dir_path, filename)

# 自动创建数据库文件
with app.app_context():
    db.create_all()

if __name__ == "__main__":
    pt.success("API 正在启动 (Port 5000)...")
    app.run(host="0.0.0.0", port=5000, debug=True)
