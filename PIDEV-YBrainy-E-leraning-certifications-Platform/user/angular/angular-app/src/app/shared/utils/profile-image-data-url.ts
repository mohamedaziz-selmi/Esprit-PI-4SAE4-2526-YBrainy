/** Extrait mime + base64 brut depuis un data URL (pour l API Gemini). */
export function extractBase64FromDataUrl(dataUrl: string): { mime: string; base64: string } | null {
  const m = dataUrl.match(/^data:([^;,]+);base64,(.+)$/s);
  if (!m) return null;
  return { mime: m[1].trim(), base64: m[2].replace(/\s/g, '') };
}
