import * as Aptabase from '@aptabase/web';
import {APP_VERSION, GLOBAL_SETTINGS, MODE, PARADIGM} from "./globals";

// @ts-expect-error defined globally
if (APTABASE_ID_WEBPACK_REPLACEMENT) {
  // @ts-expect-error defined globally
  Aptabase.init(APTABASE_ID_WEBPACK_REPLACEMENT, {
    isDebug: MODE !== 'Prod',
    appVersion: `${PARADIGM}-${APP_VERSION}${GLOBAL_SETTINGS.analyticsName ? `-${GLOBAL_SETTINGS.analyticsName}` : ''}`
  });
}

export function trackEvent(eventName: string, props?: Record<string, string | number | boolean>): void {
  // @ts-expect-error defined globally
  if (APTABASE_ID_WEBPACK_REPLACEMENT) {
    Aptabase.trackEvent(eventName, props)
  }
}
