<script lang="ts">
import type {Database, Query, Table} from "./common/models";
import AdditionalLoginModal from "./_additional_login_modal.svelte";

export let database: Database
export let queries: Query[]
export let selectedQuery: Query | null

export let onTableClicked: (table: Table) => Promise<void>
export let onQueryClicked: (query: Query) => Promise<void>
export let onLoggedIn: () => Promise<void>

let expanded = true
let additionalLoginModal: AdditionalLoginModal |  null
</script>

<div>
  {#if database.requireLogin}
    <div
      class="flex items-center gap-2 p-2 cursor-pointer"
      onclick={() => {additionalLoginModal!.open(database)}}
      data-test-id="database-lock-item"
      data-test-value={database.name}
    >
      <i class="ph ph-lock text-sm"></i>
      <span class="overflow-hidden text-ellipsis font-mono text-xs whitespace-nowrap underline">{database.name}</span>
    </div>
    <AdditionalLoginModal bind:this={additionalLoginModal} onLoggedIn={onLoggedIn} />
  {:else}
    <div class="flex items-center gap-2 p-2 cursor-pointer" onclick={() => {expanded = !expanded}}>
      <i class="ph {expanded ? 'ph-caret-down' : 'ph-caret-right' } text-sm"></i>
      <span class="overflow-hidden text-ellipsis font-mono text-xs whitespace-nowrap underline">{database.name}</span>
    </div>
    <ul
      class="menu flex flex-col w-full text-xs p-0"
      data-test-id="menu-items"
      data-test-value={database.name}
      class:hidden={!expanded}
    >
      {#each database.tables as table (table)}
        <li class="w-full">
        <span
          class="flex items-center gap-2 font-mono w-full"
          onclick={() => {onTableClicked({database: database.name, name: table})}}
          data-test-id="menu-item-table"
          data-test-value={table}
        >
          <i class="ph ph-table"></i>
          <span class="overflow-hidden text-ellipsis whitespace-nowrap">{table}</span>
        </span>
        </li>
      {/each}
      {#each queries.filter(q => q.database === database.name) as query (query.name)}
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
  {/if}
</div>

<style lang="scss">
</style>
