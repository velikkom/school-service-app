export const paymentKeys = {
  all: ["payments"],
  list: () => ["payments", "list"],
};

/** Ödeme kaydı/iptali ekstre bakiyesini değiştirir. */
export const PAYMENT_INVALIDATE = [["payments"], ["students", "statement"]];
