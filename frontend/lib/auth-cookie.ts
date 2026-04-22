const ACCESS_COOKIE = "access_token";

export function setAccessTokenCookie(token: string, maxAgeSeconds: number) {
  if (typeof document === "undefined") return;
  document.cookie = `${ACCESS_COOKIE}=${encodeURIComponent(token)}; path=/; max-age=${maxAgeSeconds}; SameSite=Lax`;
}

export function clearAccessTokenCookie() {
  if (typeof document === "undefined") return;
  document.cookie = `${ACCESS_COOKIE}=; path=/; max-age=0; SameSite=Lax`;
}

export { ACCESS_COOKIE };
