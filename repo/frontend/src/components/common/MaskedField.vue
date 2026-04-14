<template>
  <span class="masked-field" style="display: inline-flex; align-items: center; gap: 6px;">
    <span class="masked-field__value">{{ maskedValue }}</span>
    <button
      v-if="revealable && value"
      type="button"
      class="masked-field__toggle"
      style="
        background: none;
        border: 1px solid #ccc;
        border-radius: 4px;
        padding: 2px 8px;
        cursor: pointer;
        font-size: 0.85em;
        line-height: 1.4;
        color: inherit;
      "
      @click="revealed = !revealed"
    >
      {{ revealed ? 'Hide' : 'Show' }}
    </button>
  </span>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  value: {
    type: String,
    default: ''
  },
  visibleChars: {
    type: Number,
    default: 4
  },
  maskChar: {
    type: String,
    default: '*'
  },
  revealable: {
    type: Boolean,
    default: true
  }
})

const revealed = ref(false)

const maskedValue = computed(() => {
  if (!props.value) {
    return '\u2014'
  }

  if (revealed.value) {
    return props.value
  }

  const len = props.value.length
  if (len <= props.visibleChars) {
    return props.maskChar.repeat(len)
  }

  const maskedLength = len - props.visibleChars
  const tail = props.value.slice(-props.visibleChars)
  return props.maskChar.repeat(maskedLength) + tail
})
</script>
