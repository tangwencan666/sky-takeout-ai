<script setup>
import { computed, ref, watch } from 'vue'
import { imageUrl } from './format'
const props = defineProps({ src: String, name: String, meal: Boolean })
const failed = ref(false)
watch(() => props.src, () => { failed.value = false })
const fallback = computed(() => props.meal ? '/images/meal-placeholder.svg' : '/images/dish-placeholder.svg')
const url = computed(() => failed.value || !props.src ? fallback.value : imageUrl(props.src))
const illustrated = computed(() => url.value.includes('placeholder.svg'))
</script>
<template>
  <div class="food-image">
    <img :src="url" :alt="`${name || '餐品'}${illustrated ? '示意图' : ''}`" loading="lazy" @error="failed = true">
    <span v-if="illustrated" class="image-note">餐品示意图</span>
  </div>
</template>
