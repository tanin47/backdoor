<script lang="ts">

import Button from './common/_button.svelte';
import type {SqlHistoryEntry} from "./common/models";

export let query: SqlHistoryEntry;
let isCopied: boolean = false;
export let onClick: (() => Promise<void>);

function timeAgo(timestamp: number) {
  const rtf = new Intl.RelativeTimeFormat('en', {numeric: 'auto'});
  const now = new Date();
  const diffInSeconds = (now.getTime() - timestamp) / 1000;

  if (diffInSeconds < 60) {
    return rtf.format(-Math.round(diffInSeconds), 'second');
  } else if (diffInSeconds < 3600) {
    return rtf.format(-Math.round(diffInSeconds / 60), 'minute');
  } else if (diffInSeconds < 86400) {
    return rtf.format(-Math.round(diffInSeconds / 3600), 'hour');
  } else if (diffInSeconds < 2592000) { // Approx 30 days
    return rtf.format(-Math.round(diffInSeconds / 86400), 'day');
  } else if (diffInSeconds < 31536000) { // Approx 1 year
    return rtf.format(-Math.round(diffInSeconds / 2592000), 'month');
  } else {
    return rtf.format(-Math.round(diffInSeconds / 31536000), 'year');
  }
}

</script>


<div class="flex flex-col border border-neutral bg-info-content rounded overflow-hidden gap-0">
  <div
    class="font-mono text-xs p-2 whitespace-pre overflow-auto max-h-[150px]"
    data-test-id="sql-history-entry-sql"
  >{query.sql.trim()}</div>
  <div
    class="flex items-center justify-between gap-1 text-xs p-0.5 border-t border-t-neutral"
  >
    <div class="flex items-center gap-0">
      <Button
        class="btn btn-xs btn-ghost flex items-center gap-1 cursor-pointer"
        onClick={onClick}
      >
        <i class="ph ph-plus text-xs"></i>
        <span>Use this query</span>
      </Button>
      <Button
        class="btn btn-xs btn-ghost flex items-center gap-1 cursor-pointer"
        onClick={async () => {
          await navigator.clipboard.writeText(query.sql);
          isCopied = true;

          setTimeout(() => {
            isCopied = false;
          }, 2000);
        }}
      >
        <i class="ph text-xs {isCopied ? 'ph-check-square-offset' : 'ph-copy'}"></i>
        <span>{isCopied ? 'Copied' : 'Copy'}</span>
      </Button>
    </div>
    <div class="flex items-center gap-2 pe-1">
      <div class="text-neutral-content underline">{query.database}</div>
      <div
        class="text-gray-400"
        title={new Date(query.executedAt).toISOString()}
      >{timeAgo(query.executedAt)}</div>
    </div>
  </div>
</div>
