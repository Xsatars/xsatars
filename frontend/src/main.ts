import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 官方的深色变量：只要在 html 上挂 class="dark" 就会生效。
// 用它比我们自己去覆盖每个组件的颜色靠谱得多（官方跟着版本维护）。
import 'element-plus/theme-chalk/dark/css-vars.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import './assets/main.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// Element Plus 组件库，使用中文语言包
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
