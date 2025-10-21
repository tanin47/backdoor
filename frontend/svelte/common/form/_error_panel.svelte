<script lang="ts">
export let errors: string[] = []
</script>

{#if errors.length > 0}
  <div class="text-error whitespace-pre-wrap text-xs font-mono p-1 rounded bg-base-300" data-test-id="error-panel">
    {#each errors as error, index (index)}
      <p>{error}</p>
    {/each}
  </div>
{/if}

<style lang="scss">
</style>
