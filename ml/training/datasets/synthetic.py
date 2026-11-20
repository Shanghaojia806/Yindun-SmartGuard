"""合成二分类数据生成。

重要：这里生成的是可复现的随机特征，仅用于验证训练链路（Smoke Test），
不包含任何真实诈骗样本，也不代表真实反诈任务分布。
"""

from __future__ import annotations

from dataclasses import dataclass

import torch


@dataclass
class TensorSplit:
    features: torch.Tensor
    labels: torch.Tensor

    def __len__(self) -> int:
        return int(self.labels.shape[0])


def _make_split(
    count: int, feature_dim: int, generator: torch.Generator, weight: torch.Tensor
) -> TensorSplit:
    count = max(1, int(count))
    features = torch.randn(count, feature_dim, generator=generator)
    noise = torch.randn(count, generator=generator)
    logits = features @ weight + 0.6 * noise
    labels = (logits > 0).long()
    # 保证二分类都有样本，避免 precision/recall 分母为 0
    if labels.min() == labels.max():
        labels[0] = 1 - int(labels[0])
    return TensorSplit(features=features, labels=labels)


def make_synthetic_datasets(
    train_count: int,
    validation_count: int,
    seed: int,
    feature_dim: int = 16,
) -> tuple[TensorSplit, TensorSplit, TensorSplit]:
    """固定种子生成训练 / 验证 / 测试三份合成数据，可完全复现。"""
    generator = torch.Generator().manual_seed(int(seed))
    weight = torch.randn(feature_dim, generator=generator)
    train = _make_split(train_count, feature_dim, generator, weight)
    validation = _make_split(validation_count, feature_dim, generator, weight)
    test = _make_split(validation_count, feature_dim, generator, weight)
    return train, validation, test
