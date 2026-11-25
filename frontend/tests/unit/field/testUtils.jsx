import { render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { PrimeReactProvider } from "primereact/api";
import { vi } from "vitest";

/** jsdom'da matchMedia yok; varsayılan masaüstü. */
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
  return render(
    <PrimeReactProvider>
      <QueryClientProvider client={client}>{ui}</QueryClientProvider>
    </PrimeReactProvider>
  );
}

export const page = (content) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });
