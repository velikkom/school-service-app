import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { stubMatchMedia } from "./helpers";
import RegistrationApproveDialog from "@/features/registration-requests/components/RegistrationApproveDialog";

const request = { id: 3, firstName: "Ali", lastName: "Kaya", email: "ali@example.test" };

describe("Kayıt talebi onay dialogu", () => {
  beforeEach(() => stubMatchMedia());

  it("DRIVER seçilince ehliyet no zorunlu; doluysa gövde licenseNo içerir", async () => {
    const onApprove = vi.fn().mockResolvedValue(undefined);
    render(<RegistrationApproveDialog request={request} submitting={false} onHide={() => {}} onApprove={onApprove} />);

    expect(screen.queryByLabelText(/Ehliyet no/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByText("Şoför"));
    const submit = document.querySelector("button[type=submit]");
    fireEvent.click(submit);
    expect(await screen.findByText("Şoför rolünde ehliyet no zorunludur.")).toBeInTheDocument();
    expect(onApprove).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText(/Ehliyet no/), { target: { value: "B-42" } });
    fireEvent.click(submit);
    await waitFor(() => expect(onApprove).toHaveBeenCalledWith({ role: "DRIVER", licenseNo: "B-42" }));
  });
});
