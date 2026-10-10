<script setup lang="ts">
// 根组件：渲染路由对应的页面，并给页面切换加一点淡入。
// 过渡用 global 的 .fade-* 类（定义在 assets/main.css 里）。
</script>

<template>
  <router-view v-slot="{ Component }">
    <transition name="fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>
