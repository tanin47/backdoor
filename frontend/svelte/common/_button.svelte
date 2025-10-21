<script lang="ts">
export let isLoading = false
export let disabled = false
export let onClick: (() => Promise<void>) | null = null
export let dataTestId: string | null = null

let klass = "btn btn-neutral"
export {klass as class}
</script>

<button
  class="{klass} flex flex-row items-center gap-1"
  class:animated-gradient={isLoading}
  data-test-id={dataTestId}
  disabled={isLoading || disabled}
  onclick={() => { if (onClick) {onClick()} }}
>
  <slot></slot>
</button>

<style lang="scss">
.animated-gradient {
  background: linear-gradient(to right, #000, #333);
  background-size: 300% 100%; /* Make the gradient wider than the element */
  animation: gradient-shift 0.6s linear infinite alternate; /* Animate the position */
}

@keyframes gradient-shift {
  0% {
    background-position: 0% 50%;
  }
  100% {
    background-position: 100% 50%;
  }
}
</style>
