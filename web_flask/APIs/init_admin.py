from app import app, db
from models.model import User

with app.app_context():
    # 因为直接修改了 model.py 里 users 和 contributions 表结构，
    # 如果已经存在旧的 sqlite 数据库，缺少新字段会导致报错。
    # 这里通过捕获异常尝试建表。建议开发阶段如果表不重要可以直接删除 instance 文件夹下的 plant_system.db
    db.create_all()

    # 创建默认超级管理员账号
    admin = User.query.filter_by(username='admin').first()
    if not admin:
        admin = User(username='admin', is_expert=True, is_admin=True, is_active=True)
        admin.set_password('admin123')
        db.session.add(admin)
        db.session.commit()
        print("Default admin user created: admin / admin123")
    else:
        # 如果老账号存在，强制更新为管理员
        admin.is_admin = True
        admin.is_active = True
        db.session.commit()
        print("Admin user already exists. Set as admin.")
