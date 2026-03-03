import os
import sys

# 将 web_flask 加入路径，以便使用 from shared.models 引用
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from flask import Flask, redirect, send_from_directory
from shared.models.model import db
from routers.admin import admin_bp
from dotenv import load_dotenv
from shared.tools.printTools import PrintTools as pt

load_dotenv()

app = Flask(__name__)

# 配置
app.config["SQLALCHEMY_DATABASE_URI"] = os.getenv("SQLALCHEMY_DATABASE_URI")
app.secret_key = os.getenv("FLASK_SECRET_KEY", "admin-dashboard-secret-key-456")

# 初始化插件
db.init_app(app)

# 注册 admin 蓝图
app.register_blueprint(admin_bp, url_prefix="/admin")


@app.route("/")
def index():
    return redirect("/admin")


# 管理员界面的图片可能引用自 uploads/contributions 等，需要将路径重定向到 APIs 下对应的文件夹，保证共享同一个上传池
@app.route("/uploads/<category>/<filename>")
def uploaded_file(category, filename):
    if category not in ["contributions", "recognition"]:
        return "Not found", 404
    dir_path = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "APIs",
        "uploads",
        category,
    )
    return send_from_directory(dir_path, filename)


if __name__ == "__main__":
    pt.info("后台管理系统 5002 端口启动........")
    app.run(host="0.0.0.0", port=5002, debug=True)
