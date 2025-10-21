<script lang="ts">
import {TARGET_HOSTNAME} from "./common/globals";
import type {Query} from "./common/models";

export let tables: string[]
export let queries: Query[]
export let selectedQuery: Query | null

export let onTableClicked: (table: string) => Promise<void>
export let onQueryClicked: (query: Query) => Promise<void>
</script>

<div>
  <div class="flex items-center gap-2 px-4 pt-4 text-xl mb-2">
    <i class="ph ph-database text-2xl"></i>
    <span class="overflow-hidden text-ellipsis font-mono text-sm whitespace-nowrap">{TARGET_HOSTNAME}</span>
  </div>
  <ul class="menu flex flex-col w-full text-xs" data-test-id="menu-items">
    {#each tables as table (table)}
      <li class="w-full">
        <span
          class="flex items-center gap-2 font-mono w-full"
          onclick={() => {onTableClicked(table)}}
          data-test-id="menu-item-table"
          data-test-value={table}
        >
          <i class="ph ph-table"></i>
          <span class="overflow-hidden text-ellipsis whitespace-nowrap">{table}</span>
        </span>
      </li>
    {/each}
    {#each queries as query (query.name)}
      <li class="w-full">
        <span
          class="flex items-center gap-2 font-mono w-full cursor-pointer"
          onclick={() => {onQueryClicked(query)}}
          data-test-id="menu-item-query"
          data-test-value={query.name}
        >
          <i class="ph ph-file-sql"></i>
          <span
            class="overflow-hidden text-ellipsis whitespace-nowrap"
            class:underline={selectedQuery === query}
          >{query.name}</span>
        </span>
      </li>
    {/each}
  </ul>
</div>

<style lang="scss">
</style>
