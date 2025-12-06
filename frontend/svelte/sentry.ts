import * as Sentry from "@sentry/browser";

Sentry.init({
  // @ts-expect-error defined globally
  dsn: window.SENTRY_DSN,
  // @ts-expect-error defined globally
  environment: window.MODE,
  // @ts-expect-error defined globally
  enabled: window.MODE !== 'Test',
  sendDefaultPii: true,
  // @ts-expect-error defined globally
  release: window.SENTRY_RELEASE,
  beforeSend(event, hint) {
    if (event.user) {
      event.user.ip_address = null
    }
    return event;
  },
});
