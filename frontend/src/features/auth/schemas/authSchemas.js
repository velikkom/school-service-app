import { z } from "zod";

const email = z.string().trim().min(1, "E-posta zorunludur.").email("Geçerli bir e-posta adresi girin.");
// Backend kuralıyla aynı: en az 8 karakter.
const newPassword = z.string().min(8, "Şifre en az 8 karakter olmalıdır.").max(100, "Şifre en fazla 100 karakter olabilir.");

export const loginSchema = z.object({
  email,
  password: z.string().min(1, "Şifre zorunludur."),
  rememberMe: z.boolean().optional(),
});

export const forgotPasswordSchema = z.object({ email });

export const resetPasswordSchema = z
  .object({ newPassword, confirm: z.string().min(1, "Şifre tekrarı zorunludur.") })
  .refine((v) => v.newPassword === v.confirm, { path: ["confirm"], message: "Şifreler eşleşmiyor." });

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Mevcut şifre zorunludur."),
    newPassword,
    confirm: z.string().min(1, "Şifre tekrarı zorunludur."),
  })
  .refine((v) => v.newPassword === v.confirm, { path: ["confirm"], message: "Şifreler eşleşmiyor." })
  .refine((v) => v.newPassword !== v.currentPassword, { path: ["newPassword"], message: "Yeni şifre mevcut şifreyle aynı olamaz." });

export const registerRequestSchema = z
  .object({
    firstName: z.string().trim().min(1, "Ad zorunludur.").max(100),
    lastName: z.string().trim().min(1, "Soyad zorunludur.").max(100),
    email,
    password: newPassword,
    passwordConfirm: z.string().min(1, "Şifre tekrarı zorunludur."),
  })
  .refine((v) => v.password === v.passwordConfirm, { path: ["passwordConfirm"], message: "Şifreler eşleşmiyor." });
