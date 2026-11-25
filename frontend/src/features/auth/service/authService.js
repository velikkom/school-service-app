import { http } from "@/lib/http/client";

/** BFF auth uçları (docs/API.md §1). Token'lar HttpOnly çerezde kalır; yanıtta yalnız `user` döner. */
export const authService = {
  me: () => http.get("/auth/me"),
  login: (payload) => http.post("/auth/login", payload),
  logout: () => http.post("/auth/logout"),
  changePassword: (payload) => http.post("/auth/change-password", payload),
  forgotPassword: (payload) => http.post("/auth/forgot-password", payload),
  resetPassword: (payload) => http.post("/auth/reset-password", payload),
  registerRequest: (payload) => http.post("/auth/register-requests", payload),
  verifyEmail: (payload) => http.post("/auth/register-requests/verify-email", payload),
  socialProviders: () => http.get("/auth/social/providers"),
  socialLink: () => http.post("/auth/social-link"),
};
