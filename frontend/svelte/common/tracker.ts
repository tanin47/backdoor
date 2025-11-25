import * as Aptabase from '@aptabase/web';
import {APP_VERSION, IS_LOCAL_DEV, PARADIGM} from "./globals";

// @ts-expect-error defined globally
if (APTABASE_ID_WEBPACK_REPLACEMENT) {
  // @ts-expect-error defined globally
  Aptabase.init(APTABASE_ID_WEBPACK_REPLACEMENT, {isDebug: IS_LOCAL_DEV, appVersion: `${PARADIGM}-${APP_VERSION}`});
}

export function trackEvent(eventName: string, props?: Record<string, string | number | boolean>): void {
  // @ts-expect-error defined globally
  if (APTABASE_ID_WEBPACK_REPLACEMENT) {
    Aptabase.trackEvent(eventName, props)
  }
}
