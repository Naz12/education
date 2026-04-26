/**
 * Checklist item ordering / grouping. Matches first-seen groupKey order (same as mobile LinkedHashMap behavior).
 */

export const DEFAULT_CHECKLIST_SECTION_ID = "00000000-0000-4000-8000-000000000001";

export function newChecklistItemRowId() {
  return typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `row-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/** @param {{ _id: string, name: string }[]} sections @param {object[]} items */
export function orderItemsToMatchSectionOrder(sections, items) {
  const ids = new Set(sections.map((s) => s._id));
  const ordered = sections.flatMap((s) =>
    items
      .map((it, idx) => ({ it, idx }))
      .filter(({ it }) => it.sectionId === s._id)
      .sort((a, b) => a.idx - b.idx)
      .map(({ it }) => it)
  );
  const orphans = items
    .map((it, idx) => ({ it, idx }))
    .filter(({ it }) => !ids.has(it.sectionId))
    .sort((a, b) => a.idx - b.idx)
    .map(({ it }) => it);
  return [...ordered, ...orphans];
}

export function normalizeGroupKeyForDisplay(item) {
  const g = item?.groupKey;
  if (g == null || String(g).trim() === "") return "General";
  return String(g).trim();
}

/**
 * @param {Array<{ groupKey?: string }>} items (API order)
 * @returns {Array<{ key: string, items: object[] }>}
 */
export function groupChecklistItemsInApiOrder(items) {
  if (!Array.isArray(items) || !items.length) return [];
  const out = [];
  const keyToIndex = new Map();
  for (const it of items) {
    const k = normalizeGroupKeyForDisplay(it);
    if (!keyToIndex.has(k)) {
      keyToIndex.set(k, out.length);
      out.push({ key: k, items: [] });
    }
    out[keyToIndex.get(k)].items.push(it);
  }
  return out;
}

/** @param {Record<string, { options?: object, validation?: object }> | null} [typeDefaults] */
export function buildEditorStateFromChecklistRender(data, typeDefaults) {
  const raw = (data?.items || []).slice().sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
  const nameOrder = [];
  for (const it of raw) {
    const g = (it.groupKey && String(it.groupKey).trim() !== "" ? it.groupKey : "General").trim();
    if (!nameOrder.includes(g)) nameOrder.push(g);
  }
  if (nameOrder.length === 0) {
    const yesNoDefaults = typeDefaults?.YES_NO ?? { options: { choices: ["YES", "NO"] }, validation: { required: true } };
    const options = yesNoDefaults.options ?? { choices: ["YES", "NO"] };
    const validation = yesNoDefaults.validation ?? { required: true };
    const baseChoices = Array.isArray(options.choices) && options.choices.length ? options.choices : ["YES", "NO"];
    return {
      sections: [{ _id: DEFAULT_CHECKLIST_SECTION_ID, name: "General" }],
      items: [
        {
          _id: newChecklistItemRowId(),
          sectionId: DEFAULT_CHECKLIST_SECTION_ID,
          question: "",
          questionLocalizedText: JSON.stringify({ en: "", am: "" }),
          type: "YES_NO",
          order: 1,
          optionsText: JSON.stringify({ choices: baseChoices, choicesLocalized: { en: baseChoices, am: baseChoices } }),
          validationText: JSON.stringify(validation)
        }
      ]
    };
  }
  const nameToId = new Map();
  for (const g of nameOrder) {
    nameToId.set(g, newChecklistItemRowId());
  }
  const loadedSections = nameOrder.map((n) => ({ _id: nameToId.get(n), name: n }));
  const items = raw.map((it) => {
    const g = (it.groupKey && String(it.groupKey).trim() !== "" ? it.groupKey : "General").trim();
    return {
      _id: newChecklistItemRowId(),
      sectionId: nameToId.get(g) || DEFAULT_CHECKLIST_SECTION_ID,
      question: (it.questionLocalized?.en ?? it.question ?? ""),
      questionLocalizedText: JSON.stringify({
        en: (it.questionLocalized?.en ?? it.question ?? ""),
        am: it.questionLocalized?.am ?? ""
      }),
      type: it.type || "TEXT",
      order: it.order,
      optionsText: JSON.stringify(it.options || {}),
      validationText: JSON.stringify(it.validation || {})
    };
  });
  return {
    sections: loadedSections,
    items: orderItemsToMatchSectionOrder(loadedSections, items)
  };
}
