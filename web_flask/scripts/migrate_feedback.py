"""
运行此脚本为 feedback 表添加 audit_status 列。
用法: python scripts/migrate_feedback.py (from web_flask 目录)
"""
import pymysql

conn = pymysql.connect(
    host="127.0.0.1",
    port=3306,
    user="root",
    password="root",
    database="plant_db",
    charset="utf8mb4"
)
cursor = conn.cursor()

# 检查列是否已存在
cursor.execute("""
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA='plant_db' AND TABLE_NAME='feedback' AND COLUMN_NAME='audit_status'
""")
exists = cursor.fetchone()[0]

if exists:
    print("✅ audit_status 列已存在，无需迁移。")
else:
    cursor.execute("""
        ALTER TABLE feedback
        ADD COLUMN audit_status VARCHAR(20) NULL DEFAULT NULL
        COMMENT 'null=待审计, adopt=采纳, ignore=误报忽略'
    """)
    conn.commit()
    print("✅ 迁移完成：feedback.audit_status 已成功添加。")

cursor.close()
conn.close()
