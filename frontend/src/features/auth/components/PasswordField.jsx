"use client";

import { Controller } from "react-hook-form";
import { Password } from "primereact/password";
import Field from "@/components/ui/Field";

/** RHF'ye bağlı, göster/gizle düğmeli şifre alanı. */
export default function PasswordField({ control, name, label, error, autoComplete = "new-password", required = true, help }) {
  const inputId = `pw-${name}`;
  return (
    <Field label={label} error={error} required={required} help={help} htmlFor={inputId}>
      <Controller
        name={name}
        control={control}
        render={({ field }) => (
          <Password
            inputId={inputId}
            value={field.value ?? ""}
            onChange={(e) => field.onChange(e.target.value)}
            onBlur={field.onBlur}
            feedback={false}
            toggleMask
            autoComplete={autoComplete}
            invalid={Boolean(error)}
            pt={{
              input: { "aria-invalid": error ? true : undefined, "aria-describedby": error ? `${inputId}-err` : undefined },
              showIcon: { "aria-label": "Şifreyi göster" },
              hideIcon: { "aria-label": "Şifreyi gizle" },
            }}
          />
        )}
      />
    </Field>
  );
}
