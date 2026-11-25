package com.okulservisi.backend.contract.api;

/** docs/API.md "11. Sözleşme" — {@code ContractController} mapping sabitleri. */
public final class ContractApiPaths {

    private ContractApiPaths() {
    }

    public static final String BASE = "/api/contracts";
    public static final String BY_ID = "/{id}";
    public static final String CANCEL = "/{id}/cancel";
    public static final String APPROVE = "/{id}/approve";
    public static final String REJECT = "/{id}/reject";
}
