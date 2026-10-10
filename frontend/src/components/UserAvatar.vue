<script setup lang="ts">
import { computed } from 'vue'

/**
 * 用户头像。
 *
 * 没设置头像时显示一个**默认的空白头像**（灰底 + 昵称首字），而不是裂图。
 */
const props = withDefaults(
  defineProps<{
    /** 头像地址，为空表示还没设置 */
    src?: string | null
    /** 昵称，用来取首字 */
    name?: string
    /** 直径（像素） */
    size?: number
  }>(),
  {
    src: null,
    name: '',
    size: 36
  }
)

const initial = computed(() => {
  const n = (props.name || '').trim()
  return n ? n.charAt(0).toUpperCase() : ''
})

const style = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${Math.max(12, Math.round(props.size * 0.42))}px`
}))
</script>

<template>
  <div class="user-avatar" :style="style">
    <img v-if="src" :src="src" :alt="name" />
    <span v-else>{{ initial }}</span>
  </div>
</template>

<style scoped>
.user-avatar {
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.13);
  color: var(--at-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
  font-weight: bold;
  user-select: none;
  border: 1px solid rgba(255, 255, 255, 0.13);
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
</style>
