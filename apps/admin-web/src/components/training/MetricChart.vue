<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { echarts } from '@/plugins/echarts'

/**
 * 训练指标折线图（P11）。
 *
 * 约束：
 * - 横轴固定为 Epoch，训练集与验证集必须以不同系列区分；
 * - 无数据时展示空状态，不生成占位曲线；
 * - 演示指标必须在标题旁明确标识，避免被误读为真实模型效果。
 */
interface ChartSeries {
  name: string
  data: number[]
  color: string
  dashed?: boolean
}

interface Props {
  title: string
  subtitle?: string
  epochs: number[]
  series: ChartSeries[]
  /** loss 使用小数，percent 以百分比展示 */
  valueType: 'loss' | 'percent'
  /** 数据来源标识，例如「演示指标」 */
  sourceLabel?: string
  emptyText?: string
}

const props = withDefaults(defineProps<Props>(), {
  subtitle: '',
  sourceLabel: '',
  emptyText: '暂无训练指标',
})

type ChartInstance = ReturnType<typeof echarts.init>
type ChartOption = Parameters<ChartInstance['setOption']>[0]

const container = ref<HTMLDivElement | null>(null)
let chart: ChartInstance | null = null
let observer: ResizeObserver | null = null

const hasData = computed(
  () => props.epochs.length > 0 && props.series.some((item) => item.data.length > 0),
)

function formatValue(value: number): string {
  if (!Number.isFinite(value)) return '—'
  return props.valueType === 'percent' ? `${(value * 100).toFixed(1)}%` : value.toFixed(3)
}

function buildOption(): ChartOption {
  return {
    animation: false,
    grid: { left: 12, right: 20, top: 36, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      confine: true,
    },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { color: '#5b6b7a', fontSize: 12 },
    },
    xAxis: {
      type: 'category',
      name: 'Epoch',
      nameTextStyle: { color: '#8494a3', fontSize: 11 },
      boundaryGap: false,
      data: props.epochs.map((item) => String(item)),
      axisLine: { lineStyle: { color: '#dfe6ec' } },
      axisLabel: { color: '#8494a3', fontSize: 11 },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: props.valueType === 'percent' ? 1 : undefined,
      splitLine: { lineStyle: { color: '#eef2f6' } },
      axisLabel: {
        color: '#8494a3',
        fontSize: 11,
        formatter: (value: number) =>
          props.valueType === 'percent' ? `${Math.round(value * 100)}%` : value.toFixed(2),
      },
    },
    series: props.series.map((item) => ({
      name: item.name,
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: {
        width: 2,
        color: item.color,
        type: item.dashed ? 'dashed' : 'solid',
      },
      itemStyle: { color: item.color },
      data: item.data,
      valueFormatter: (value: unknown) => formatValue(Number(value)),
    })),
  }
}

function render(): void {
  if (!chart || !hasData.value) return
  chart.setOption(buildOption(), true)
  chart.resize()
}

onMounted(() => {
  if (!container.value) return
  chart = echarts.init(container.value)
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(container.value)
  render()
})

watch(
  () => [props.epochs, props.series],
  () => render(),
  { deep: true },
)

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  chart?.dispose()
  chart = null
})
</script>

<template>
  <section class="ss-metric-chart">
    <header class="ss-metric-chart__header">
      <div>
        <h3 class="ss-metric-chart__title">{{ title }}</h3>
        <p v-if="subtitle" class="ss-metric-chart__subtitle">{{ subtitle }}</p>
      </div>
      <el-tag v-if="sourceLabel" size="small" type="warning" effect="plain">
        {{ sourceLabel }}
      </el-tag>
    </header>

    <div v-if="!hasData" class="ss-metric-chart__empty">
      <el-empty :description="emptyText" :image-size="56" />
    </div>
    <div v-show="hasData" ref="container" class="ss-metric-chart__canvas" />
  </section>
</template>

<style scoped>
.ss-metric-chart {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-2);
  padding: var(--ss-space-4);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.ss-metric-chart__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ss-space-3);
}

.ss-metric-chart__title {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body);
  font-weight: var(--ss-font-weight-medium);
}

.ss-metric-chart__subtitle {
  margin-top: 2px;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.ss-metric-chart__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
}

.ss-metric-chart__canvas {
  width: 100%;
  height: 260px;
}
</style>
