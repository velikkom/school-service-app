import { render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { PrimeReactProvider } from "primereact/api";
import { vi } from "vitest";

/** jsdom'da matchMedia yok; masaüstü (>= 900 px) varsayılır. */
export function installMatchMedia(matches = false) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query) => ({
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

export function renderWithClient(ui) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const utils = render(
    <PrimeReactProvider>
      <QueryClientProvider client={client}>{ui}</QueryClientProvider>
    </PrimeReactProvider>
  );
  return { ...utils, client };
}

/** Spring Page zarfı. */
export function page(content, totalElements = content.length) {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / 20)) };
}

/** 409 vb. sunucu hatası (ApiError benzeri). */
export function apiError(status, message, extra = {}) {
  const err = new Error(message);
  err.name = "ApiError";
  err.status = status;
  err.error = status === 409 ? "CONFLICT" : status === 400 ? "VALIDATION_ERROR" : "ERROR";
  err.fieldErrors = [];
  Object.assign(err, extra);
  return err;
}
