"""Smoke Test 模型：两层 MLP，仅用于验证 PyTorch 训练链路。"""

from __future__ import annotations

import torch
from torch import nn


class SmokeClassifier(nn.Module):
    """与真实反诈模型无关的最小二分类网络。"""

    def __init__(self, feature_dim: int = 16, hidden_dim: int = 32, num_classes: int = 2) -> None:
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(feature_dim, hidden_dim),
            nn.ReLU(),
            nn.Linear(hidden_dim, num_classes),
        )

    def forward(self, features: torch.Tensor) -> torch.Tensor:
        return self.net(features)


def build_optimizer(name: str, model: nn.Module, learning_rate: float) -> torch.optim.Optimizer:
    name = (name or "adamw").lower()
    if name == "adam":
        return torch.optim.Adam(model.parameters(), lr=learning_rate)
    if name == "sgd":
        return torch.optim.SGD(model.parameters(), lr=learning_rate, momentum=0.9)
    return torch.optim.AdamW(model.parameters(), lr=learning_rate)
