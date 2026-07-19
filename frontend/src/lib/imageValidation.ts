const ALLOWED_EXT = new Set(["png", "jpg", "jpeg", "webp", "gif"]);
const ALLOWED_MIME = new Set(["image/png", "image/jpeg", "image/webp", "image/gif"]);
const MAX = 2 * 1024 * 1024;

/** Frontend UX validation — backend is the security authority. */
export function validateImageFile(file: File): string | null {
  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (!ALLOWED_EXT.has(ext)) {
    return "Only PNG, JPEG, WebP, or GIF files are allowed.";
  }
  if (file.type && !ALLOWED_MIME.has(file.type)) {
    return "Only PNG, JPEG, WebP, or GIF files are allowed.";
  }
  if (file.size <= 0 || file.size > MAX) {
    return "File must be between 1 byte and 2 MB.";
  }
  return null;
}

export const ACCEPT_IMAGES = "image/png,image/jpeg,image/jpg,image/webp,image/gif,.png,.jpg,.jpeg,.webp,.gif";
