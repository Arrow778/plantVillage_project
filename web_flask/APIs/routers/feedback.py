from flask import Blueprint, request, jsonify
from flask_jwt_extended import jwt_required, get_jwt_identity
from shared.models.model import db, RecognitionHistory, Feedback, User
from shared.tools.printTools import PrintTools as pt

feedback_bp = Blueprint("feedback", __name__)


# --- 1. 获取历史记录 API ---
@feedback_bp.route("/history/list", methods=["GET"])
@jwt_required()
def get_history():
    current_user_id = get_jwt_identity()
    user = User.query.get(int(current_user_id))
    if not user:
        return jsonify({"msg": "用户未找到"}), 404

    # 查询该用户的所有识别记录，按时间倒序排列
    histories = (
        RecognitionHistory.query.filter_by(user_id=user.id)
        .order_by(RecognitionHistory.created_at.desc())
        .all()
    )

    output = []
    for h in histories:
        output.append(
            {
                "id": h.id,
                "prediction": h.prediction,
                "confidence": f"{0.9998 * 100:.2f}%" if float(h.confidence) >= 0.9999 else f"{float(h.confidence) * 100:.2f}%",
                "image_url": h.image_path,
                "time": h.created_at.strftime("%Y-%m-%d %H:%M:%S"),
                "has_feedback": h.feedback is not None,
                "feedback_score": h.feedback.score if h.feedback is not None else None,
                "engine": h.engine,
            }
        )

    pt.info(f"用户 {user.username} 拉取了其历史识别档案 (共 {len(output)} 条)")
    return jsonify({"status": "success", "data": output}), 200


# --- 2. 提交反馈 API ---
@feedback_bp.route("/submit", methods=["POST"])
@jwt_required()
def submit_feedback():
    data = request.get_json()
    current_user_id = get_jwt_identity()
    user = User.query.get(int(current_user_id))
    if not user:
        return jsonify({"msg": "用户未找到"}), 404

    # 校验这条历史记录是否存在且属于该用户
    history = RecognitionHistory.query.filter_by(
        id=data.get("history_id"), user_id=user.id
    ).first()
    if not history:
        return jsonify({"msg": "未找到对应的识别记录"}), 404

    new_feedback = Feedback(
        history_id=history.id,
        user_id=user.id,
        score=data.get("score"),
        reason=data.get("reason"),
        suggestion=data.get("suggestion"),
    )

    db.session.add(new_feedback)
    db.session.commit()

    pt.success(f"用户 {user.username} 提交了反馈 (针对记录 ID: {history.id})")
    return jsonify({"status": "success", "msg": "感谢您的反馈！"}), 201
