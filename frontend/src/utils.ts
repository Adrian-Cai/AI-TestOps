/**
 * Normalize any JSON string value into a formatted non-empty JSON array string.
 * - valid array -> pretty-printed as-is
 * - valid JSON string -> wrapped in array
 * - everything else -> wrapped as single-element array
 */
export function normalizeArray(value?: string | null): string {
  if (!value) return "[]";
  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed)) return JSON.stringify(parsed, null, 2);
    if (typeof parsed === "string") return JSON.stringify([parsed], null, 2);
    return JSON.stringify([JSON.stringify(parsed)], null, 2);
  } catch {
    return JSON.stringify([value.trim()], null, 2);
  }
}

/**
 * Map risk level to default case type.
 * P0 (高) -> 异常场景
 * P1 (中) -> 正常场景
 * P2 (低) -> 正常场景
 */
export function getDefaultCaseTypeForRiskLevel(riskLevel?: string | null): string {
  if (riskLevel === "P0") {
    return "异常场景";
  }
  return "正常场景";
}

/**
 * Ant Design form rule: validates that a field contains a JSON array.
 * Also accepts a plain non-empty string (for user-entered text without JSON syntax).
 * Empty arrays [] are valid.
 */
export function jsonArrayRule(label: string, { allowEmpty = true } = {}) {
  return {
    validator: (_: unknown, value: string) => {
      // Trim whitespace before parsing to handle stray newlines/spaces
      const trimmed = value ? value.trim() : "";
      if (!trimmed) {
        return Promise.reject(new Error(`${label}必须是 JSON 数组`));
      }
      try {
        const parsed = JSON.parse(trimmed);
        if (typeof parsed === "string" && parsed.trim()) {
          return Promise.resolve();
        }
        if (Array.isArray(parsed)) {
          if (allowEmpty || parsed.length > 0) {
            return Promise.resolve();
          }
          return Promise.reject(new Error(`${label}必须是非空 JSON 数组`));
        }
        return Promise.reject(new Error(`${label}必须是 JSON 数组`));
      } catch {
        return Promise.reject(new Error(`${label}不是合法 JSON`));
      }
    }
  };
}
