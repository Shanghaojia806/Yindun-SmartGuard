"""Worker 启动入口。

示例（在仓库根目录）：
    python -m ml.training.worker.main --once
    python -m ml.training.worker.main --loop --interval 2

环境变量：
    P11R_API_BASE_URL   业务后端地址，默认 http://127.0.0.1:8000/api/v1
    P11R_WORKER_TOKEN   Worker Token（必填，不可写入前端）
    P11R_WORKER_ID      执行器 ID，默认 worker-cpu-01
    P11R_WORKER_LEASE_SECONDS  租约秒数，默认 120
"""

from __future__ import annotations

import argparse
import os
import sys
import time

from .client import WorkerClient, WorkerApiError
from .runner import run_task


def _env_int(name: str, fallback: int) -> int:
    try:
        return int(os.environ.get(name, fallback))
    except ValueError:
        return fallback


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="P11-R CPU Smoke Test 训练执行器")
    parser.add_argument("--base-url", default=os.environ.get("P11R_API_BASE_URL", ""))
    parser.add_argument("--worker-id", default=os.environ.get("P11R_WORKER_ID", "worker-cpu-01"))
    parser.add_argument("--lease-seconds", type=int, default=_env_int("P11R_WORKER_LEASE_SECONDS", 120))
    parser.add_argument("--interval", type=float, default=2.0, help="空闲轮询间隔（秒）")
    parser.add_argument("--once", action="store_true", help="只尝试领取一次任务")
    parser.add_argument("--loop", action="store_true", help="持续轮询（默认行为，保留该参数便于脚本表达意图）")
    parser.add_argument("--max-tasks", type=int, default=0, help="最多执行多少个任务后退出（0 表示不限）")
    args = parser.parse_args(argv)

    client = WorkerClient(base_url=args.base_url or None, worker_id=args.worker_id)
    print(f"[worker] 启动：workerId={args.worker_id} baseUrl={client.base_url}")
    executed = 0
    try:
        while True:
            task = client.claim(args.lease_seconds)
            if task is None:
                if args.once or (args.max_tasks and executed >= args.max_tasks):
                    break
                time.sleep(max(0.5, args.interval))
                continue

            task_id = task["taskId"]
            print(f"[worker] 领取任务 {task_id}（attempt={task.get('attempt')}）")
            # 领取后重新获取上下文，确保资格状态是最新的
            fresh = client.fetch_context(task_id)
            status = run_task(client, fresh, lease_seconds=args.lease_seconds)
            print(f"[worker] 任务 {task_id} 结束：{status}")
            executed += 1
            if args.once or (args.max_tasks and executed >= args.max_tasks):
                break
    except WorkerApiError as exc:
        print(f"[worker] 业务后端返回错误：{exc}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("[worker] 收到中断，退出")
    finally:
        client.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
