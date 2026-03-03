import time
import sys

class PrintTools:
    """
    🌿 生态卫士后端 专属日志/打印工具类
    提供带颜色、带时间戳的格式化输出。
    """

    # ANSI 转义码
    HEADER = '\033[95m'
    BLUE = '\033[94m'
    CYAN = '\033[96m'
    GREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'

    @staticmethod
    def _get_timestamp():
        return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())

    @staticmethod
    def info(msg):
        """记录业务流/普通信息"""
        print(f"{PrintTools.BOLD}[{PrintTools._get_timestamp()}]{PrintTools.ENDC} {PrintTools.BLUE}[INFO]{PrintTools.ENDC} {msg}")

    @staticmethod
    def debug(msg):
        """仅在开发环境下使用的具体调试信息"""
        print(f"{PrintTools.BOLD}[{PrintTools._get_timestamp()}]{PrintTools.ENDC} {PrintTools.CYAN}[DEBUG]{PrintTools.ENDC} {msg}")

    @staticmethod
    def warning(msg):
        """记录可能需要关注但非致命的警告信息"""
        print(f"{PrintTools.BOLD}[{PrintTools._get_timestamp()}]{PrintTools.ENDC} {PrintTools.WARNING}[WARNING]{PrintTools.ENDC} {msg}")

    @staticmethod
    def error(msg):
        """记录系统异常或致命错误"""
        print(f"{PrintTools.BOLD}[{PrintTools._get_timestamp()}]{PrintTools.ENDC} {PrintTools.FAIL}[ERROR]{PrintTools.ENDC} {msg}")

    @staticmethod
    def success(msg):
        """记录成功的关键节点"""
        print(f"{PrintTools.BOLD}[{PrintTools._get_timestamp()}]{PrintTools.ENDC} {PrintTools.GREEN}[SUCCESS]{PrintTools.ENDC} {msg}")

# --- 测试用例 ---
if __name__ == "__main__":
    PrintTools.info("后端服务正在启动...")
    PrintTools.debug("数据库连接池初始化中...")
    PrintTools.warning("检测到非安全连接，建议生产环境使用 HTTPS")
    PrintTools.error("模型权重文件加载失败：路径未找到")
    PrintTools.success("API 接口网关已就绪！")
