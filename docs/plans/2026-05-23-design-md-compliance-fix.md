# DESIGN.md Compliance Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all hardcoded CSS values in 20 Vue files with `--eify-*` design tokens and `.eify-*` / `.text-*` utility classes.

**Architecture:** Four-batch file-by-file approach. Each task edits one file — locate hardcoded values, replace per the mapping rules, verify with `vue-tsc --noEmit`. Commit per batch, full `vitest run` + `mvn test` at the end.

**Tech Stack:** Vue 3 SFC with scoped CSS, CSS custom properties, utility classes from `eify-web/src/styles/utilities.css`.

**Source spec:** `docs/specs/2026-05-23-design-md-compliance-fix.md`

---

## Shared Replacement Rules

Apply these to every file below. Read the file first, identify each violation, then edit.

### Color Replacements

| Hardcoded | Replace with |
|:---|:---|
| `#ffffff` (used as background) | `var(--eify-bg-base)` |
| `#fff` (used as text color) | `var(--eify-text-inverse)` |
| `#f8fafc` | `var(--eify-bg-secondary)` |
| `#f1f5f9` | `var(--eify-bg-surface)` |
| `#fef2f2` | `var(--eify-error-light)` |
| `#fecaca` | `var(--eify-error-200)` |
| `#6366f1` | `var(--eify-primary)` |
| `#8b5cf6` | `var(--eify-primary-400)` |
| `#4f46e5` | `var(--eify-primary-600)` |
| `#7c3aed` | `var(--eify-primary-400)` |
| `linear-gradient(135deg, #6366f1, #8b5cf6)` | `var(--eify-gradient-primary)` |
| `#ef4444` | `var(--eify-error)` |
| `#e11d48` | `var(--eify-error)` |
| `#dc2626` | `var(--eify-error-600)` |
| `#f59e0b` | `var(--eify-warning)` |
| `#fbbf24` | `var(--eify-warning-400)` |
| `#d97706` | `var(--eify-warning-600)` |
| `#22c55e` | `var(--eify-success)` |
| `#059669` | `var(--eify-success-600)` |
| `#3b82f6` | `var(--eify-info-500)` |
| `#0ea5e9` | `var(--eify-info)` |
| `#e2e8f0` / `#e5e7eb` | `var(--eify-border-default)` |
| `#f1f5f9` (as border color) | `var(--eify-border-subtle)` |
| `#0f172a` | `var(--eify-text-primary)` |
| `#1e293b` (background) | `var(--eify-gray-800)` |
| `#1e293b` (text) | `var(--eify-text-primary)` |
| `#334155` | `var(--eify-gray-700)` |
| `#475569` | `var(--eify-text-secondary)` |
| `#64748b` / `#6b7280` | `var(--eify-text-secondary)` |
| `#94a3b8` | `var(--eify-text-tertiary)` |
| `#a5b4fc` | `var(--eify-primary-300)` |
| `#78350f` | `var(--eify-warning-900)` |

### font-size Replacements

| px value | Replace with utility class |
|:---|:---|
| `font-size: 10px` or `11px` or `12px` | Add `.text-xs` to element's class, remove the font-size rule |
| `font-size: 13px` | Add `.text-sm` |
| `font-size: 14px` | Add `.text-base` |
| `font-size: 15px` or `16px` | Add `.text-lg` |
| `font-size: 18px` | Add `.text-xl` |
| `font-size: 20px` or `22px` | Add `.text-2xl` |
| `font-size: 24px` | Add `.text-3xl` |
| `font-size: 28px` and above | Keep as-is |

### padding / margin / border-radius / box-shadow

Replace with nearest `var(--eify-spacing-N)` or `var(--eify-radius-*)` or `var(--eify-shadow-*)` token.

### Do NOT touch

- SVG `<stop>` tags
- Workflow node colors (`--wf-node-color`, `#22c55e`, `#ef4444`, `#8b5cf6`, `#f97316`, `#eab308`, `#3b82f6`, `#06b6d4` in node context)
- MCP Server Catppuccin theme colors (`#a6e3a1`, `#f38ba8`, `#1e1e2e` in McpServerList.vue log viewer)
- `var(--eify-*, fallback)` patterns (Element Plus overrides)
- `LoginView.vue`'s `#0f0f1a` background

---

## Batch 1: Brand Color Epicenter

### Task 1.1: Fix LoginView.vue

**Files:**
- Modify: `eify-web/src/views/LoginView.vue`

- [ ] **Step 1: Read the file and identify all violations**

Read `eify-web/src/views/LoginView.vue`. In the `<style scoped>` section, locate:
- `#0f0f1a` → **KEEP** (exemption: no matching design token)
- `#6366f1` → `var(--eify-primary)`
- `#8b5cf6` → `var(--eify-primary-400)`
- `linear-gradient(135deg, #6366f1, #8b5cf6)` → `var(--eify-gradient-primary)`
- `linear-gradient(135deg, #5558e6, #7c4fea)` → `var(--eify-gradient-primary)`
- `#a5b4fc` → `var(--eify-primary-300)`
- All `font-size: Npx` → corresponding `.text-*` classes (add to `class` attribute on the element, remove the font-size rule)

- [ ] **Step 2: Apply all replacements**

Edit `eify-web/src/views/LoginView.vue`. For each violation found in Step 1:
- Replace color values with design tokens
- Add `.text-*` utility classes to template elements, remove corresponding `font-size` declarations from `<style>`
- Keep `#0f0f1a` as-is

- [ ] **Step 3: Verify type checking**

Run: `cd eify-web && npx vue-tsc --noEmit`
Expected: Exit code 0, no errors.

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/views/LoginView.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in LoginView

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Batch 2: Shared Components

### Task 2.1: Fix EifySearch.vue

**Files:**
- Modify: `eify-web/src/components/EifySearch.vue`

- [ ] **Step 1: Identify violations**

Read the `<style scoped>` section. Locate:
- `#ffffff` backgrounds → `var(--eify-bg-base)`
- `#0f172a` → `var(--eify-text-primary)`
- `#64748b` → `var(--eify-text-secondary)`
- `#334155` → `var(--eify-gray-700)`
- `linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%)` → `var(--eify-gradient-primary)`
- All `font-size: Npx` → `.text-*` utility classes

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/components/EifySearch.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in EifySearch

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 2.2: Fix EifyHeader.vue

**Files:**
- Modify: `eify-web/src/components/EifyHeader.vue`

- [ ] **Step 1: Identify violations**

Locate in `<style scoped>`:
- `color: #fff` → `var(--eify-text-inverse)`
- All `font-size: Npx` → `.text-*` utility classes
- Any hardcoded `padding` / `margin` px values → `var(--eify-spacing-N)`

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/components/EifyHeader.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in EifyHeader

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 2.3: Fix ConfirmDialog.vue

**Files:**
- Modify: `eify-web/src/components/ConfirmDialog.vue`

- [ ] **Step 1: Identify violations**

Locate in `<style scoped>`:
- `color: #ef4444` → `var(--eify-error)`
- `background: #ef4444` → `var(--eify-error)`
- `background: #dc2626` → `var(--eify-error-600)`
- `color: #fbbf24` → `var(--eify-warning-400)`
- `background: #fbbf24` → `var(--eify-warning-400)`
- `color: #78350f` → `var(--eify-warning-900)`
- `background: #f59e0b` → `var(--eify-warning)`
- All `font-size: Npx` → `.text-*`

- [ ] **Step 2: Apply replacements**

**Important:** `ConfirmDialog.vue` is the only component with unit tests (`eify-web/src/__tests__/components/ConfirmDialog.spec.ts`, 11 tests). Do NOT change template structure — only `<style>` section values.

- [ ] **Step 3: Verify type check AND unit tests**

Run: `cd eify-web && npx vue-tsc --noEmit && npx vitest run`
Expected: type check passes, 11 tests pass.

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/components/ConfirmDialog.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in ConfirmDialog

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 2.4: Fix EifyListPage.vue

**Files:**
- Modify: `eify-web/src/components/EifyListPage.vue`

- [ ] **Step 1: Identify violations**

Locate:
- `background: #ffffff` → `var(--eify-bg-base)`
- `background-color: #ffffff` → `var(--eify-bg-base)`
- `linear-gradient(180deg, #ffffff 0%, ...)` → `linear-gradient(180deg, var(--eify-bg-base) 0%, var(--eify-bg-surface) 100%)`
- All `font-size: Npx` → `.text-*`

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/components/EifyListPage.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in EifyListPage

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 2.5: Fix EifyFormDialog.vue

**Files:**
- Modify: `eify-web/src/components/EifyFormDialog.vue`

- [ ] **Step 1: Identify violations**

Locate:
- `background-color: #ffffff` → `var(--eify-bg-base)`
- `color: #ffffff` → `var(--eify-text-inverse)`
- All `font-size: Npx` → `.text-*`

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/components/EifyFormDialog.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in EifyFormDialog

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 2.6: Fix EifyTable.vue

**Files:**
- Modify: `eify-web/src/components/EifyTable.vue`

- [ ] **Step 1: Identify violations**

Locate:
- `background-color: #ffffff` → `var(--eify-bg-base)`
- `color: #ffffff` → `var(--eify-text-inverse)`
- All `font-size: Npx` → `.text-*`

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/components/EifyTable.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in EifyTable

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 2.7: Fix DocumentPreview.vue

**Files:**
- Modify: `eify-web/src/components/DocumentPreview.vue`

- [ ] **Step 1: Identify violations**

Locate:
- `color: #e11d48` → `var(--eify-error)`
- `background: #f8fafc` → `var(--eify-bg-secondary)`
- Hardcoded fallback values in `var()` patterns → **KEEP** the fallback, but ensure the primary value uses the correct `--eify-*` token
- All `font-size: Npx` → `.text-*` (this file has 10+ instances)

- [ ] **Step 2: Apply replacements**

**Careful:** This file has many `var(--eify-*, fallback)` patterns. Do NOT remove fallback values — they are correct practice for this component. Only replace standalone hardcoded values that aren't already behind a `var()`.

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/components/DocumentPreview.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in DocumentPreview

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Batch 3: Business Pages

### Task 3.1: Fix ChatView.vue

**Files:**
- Modify: `eify-web/src/views/ChatView.vue`

- [ ] **Step 1: Identify violations**

Locate:
- `color: #ef4444` → `var(--eify-error)`
- `background: #fef2f2` → `var(--eify-error-light)`
- `border-color: #fecaca` → `var(--eify-error-200)`
- `color: #dc2626` → `var(--eify-error-600)`
- `background: #1e293b` → `var(--eify-gray-800)`
- `color: #e2e8f0` → `var(--eify-border-default)` (text, not border — use `var(--eify-gray-200)`)
- `background: linear-gradient(135deg, var(--eify-primary), #8b5cf6)` → `var(--eify-gradient-primary)`
- All `font-size: Npx` → `.text-*` (30+ instances — this is the largest file)

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/views/ChatView.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in ChatView

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 3.2: Fix AgentList.vue

**Files:**
- Modify: `eify-web/src/views/AgentList.vue`

- [ ] **Step 1: Identify violations**

Locate:
- `background: #ffffff` → `var(--eify-bg-base)`
- `linear-gradient(180deg, var(--eify-bg-surface) 0%, #ffffff 100%)` → `linear-gradient(180deg, var(--eify-bg-surface) 0%, var(--eify-bg-base) 100%)`
- `color: #fff` → `var(--eify-text-inverse)`
- `background: linear-gradient(135deg, #6366f1, #8b5cf6)` → `var(--eify-gradient-primary)`
- `background: #fff` → `var(--eify-bg-base)`
- `background: #fef2f2` → `var(--eify-error-light)`
- `border-color: #fecaca` → `var(--eify-error-200)`
- `background: #f8fafc` → `var(--eify-bg-secondary)`
- `color: #dc2626` → `var(--eify-error-600)`
- `background: #1e293b` → `var(--eify-gray-800)`
- `color: #e2e8f0` → `var(--eify-gray-200)`
- All `font-size: Npx` → `.text-*` (40+ instances)

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/views/AgentList.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in AgentList

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 3.3: Fix ProviderList.vue

**Files:**
- Modify: `eify-web/src/views/ProviderList.vue`

- [ ] **Step 1: Identify violations**

Locate:
- `background: #ffffff` → `var(--eify-bg-base)`
- `linear-gradient(180deg, var(--eify-bg-surface) 0%, #ffffff 100%)` → `var(--eify-bg-base)`
- `color: var(--eify-danger, #ef4444)` → keep `var()` pattern but change fallback to `var(--eify-error)` — actually, change to `var(--eify-error)` (remove the non-existent `--eify-danger`)
- `background: #ffffff !important` → `var(--eify-bg-base) !important`
- All `font-size: Npx` → `.text-*`

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/views/ProviderList.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in ProviderList

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 3.4: Fix McpServerList.vue

**Files:**
- Modify: `eify-web/src/views/McpServerList.vue`

- [ ] **Step 1: Identify violations**

Locate (excluding Catppuccin theme section with `#1e1e2e`, `#a6e3a1`, `#f38ba8`):
- `background: #ffffff` → `var(--eify-bg-base)`
- `linear-gradient(180deg, var(--eify-bg-surface) 0%, #ffffff 100%)` → `var(--eify-bg-base)`
- `background: #ffffff !important` → `var(--eify-bg-base) !important`
- All `font-size: Npx` → `.text-*`

**EXEMPTION:** The log viewer section with `#1e1e2e`, `#a6e3a1`, `#f38ba8` is Catppuccin theme — do NOT touch.

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/views/McpServerList.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in McpServerList

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 3.5: Fix KnowledgeView.vue

**Files:**
- Modify: `eify-web/src/views/KnowledgeView.vue`

- [ ] **Step 1: Identify violations**

Locate:
- `background: #ffffff` → `var(--eify-bg-base)`
- `linear-gradient(180deg, var(--eify-bg-surface) 0%, #ffffff 100%)` → `var(--eify-bg-base)`
- All `font-size: Npx` → `.text-*`

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/views/KnowledgeView.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in KnowledgeView

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 3.6: Fix DocumentView.vue

**Files:**
- Modify: `eify-web/src/views/DocumentView.vue`

- [ ] **Step 1: Identify violations**

Locate:
- `color: #ef4444` → `var(--eify-error)`
- `color: #3b82f6` → `var(--eify-info-500)`
- `color: #22c55e` → `var(--eify-success)`
- `color: #a855f7` → **KEEP** (purple-500, no matching token — this is a document status color)

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/views/DocumentView.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in DocumentView

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 3.7: Fix WorkflowList.vue

**Files:**
- Modify: `eify-web/src/views/WorkflowList.vue`

- [ ] **Step 1: Identify violations**

Locate:
- `background: #ffffff` → `var(--eify-bg-base)`
- `color: #059669` → `var(--eify-success-600)`
- `color: #d97706` → `var(--eify-warning-600)`
- `color: #6b7280` → `var(--eify-text-secondary)`
- All `font-size: Npx` → `.text-*`

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/views/WorkflowList.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in WorkflowList

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 3.8: Fix ProfileView.vue

**Files:**
- Modify: `eify-web/src/views/ProfileView.vue`

- [ ] **Step 1: Identify violations**

Locate:
- `color: #fff` → `var(--eify-text-inverse)`
- `background: var(--eify-bg-tertiary, #f1f5f9)` → `var(--eify-bg-surface)` (`--eify-bg-tertiary` doesn't exist in design tokens)
- All `font-size: Npx` → `.text-*`

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/views/ProfileView.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in ProfileView

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Batch 4: Workflow Files (Partial, with Exemptions)

### Task 4.1: Fix WorkflowEdit.vue

**Files:**
- Modify: `eify-web/src/views/WorkflowEdit.vue`

- [ ] **Step 1: Identify violations**

Locate (EXEMPT: node type colors in `NodePanel` usage):
- `background: #f8fafc` → `var(--eify-bg-secondary)`
- `border-bottom: 2px solid #6366f1` → `var(--eify-primary)` (pass to `border-bottom-color`)
- `color: #f59e0b` → `var(--eify-warning)` (unless it's a workflow status color — read context first)
- `background: var(--eify-bg-surface, #fff)` → `var(--eify-bg-surface)` (remove non-matching fallback)
- `border-bottom: 1px solid var(--eify-border-subtle, #e5e7eb)` → `var(--eify-border-subtle)` (remove non-matching fallback)

**EXEMPTION:** `:stroke="'#6366f1'"` in template (Vue Flow connection line) — this is a Vue Flow prop, not CSS. Keep.

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/views/WorkflowEdit.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in WorkflowEdit

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 4.2: Fix WorkflowCreate.vue

**Files:**
- Modify: `eify-web/src/views/WorkflowCreate.vue`

- [ ] **Step 1: Identify violations**

Locate same patterns as WorkflowEdit.vue:
- Fallback values in `var()` patterns → align primary value, remove mismatched fallback
- `background: var(--eify-bg-surface, #fff)` → `var(--eify-bg-surface)`
- `border-bottom: 1px solid var(--eify-border-subtle, #e5e7eb)` → align
- `color: var(--eify-text-primary, #1f2937)` → `var(--eify-text-primary)`

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/views/WorkflowCreate.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in WorkflowCreate

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

### Task 4.3: Fix NodePanel.vue

**Files:**
- Modify: `eify-web/src/components/workflow/NodePanel.vue`

- [ ] **Step 1: Identify violations**

Locate (EXEMPT: node type colors `#22c55e`, `#ef4444`, `#8b5cf6`, `#f97316`, `#eab308`, `#3b82f6`, `#06b6d4` — these are domain design tokens):
- `background: #fff` → `var(--eify-bg-base)`
- `border-right: 1px solid #e5e7eb` → `var(--eify-border-default)`
- `border-bottom: 1px solid #e5e7eb` → `var(--eify-border-default)`
- `color: #1e293b` → `var(--eify-text-primary)`
- `color: #94a3b8` → `var(--eify-text-tertiary)`
- `background: #f1f5f9` → `var(--eify-bg-surface)`
- `color: #fff` → `var(--eify-text-inverse)`

- [ ] **Step 2: Apply replacements**

- [ ] **Step 3: Verify**

Run: `cd eify-web && npx vue-tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git -C /f/Study/AI/Vibe/Eify add eify-web/src/components/workflow/NodePanel.vue
git -C /f/Study/AI/Vibe/Eify commit -m "fix: replace hardcoded values with design tokens in NodePanel

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Final Verification

### Task 5.1: Full automated test run

- [ ] **Step 1: Run vitest**

Run: `cd eify-web && npx vitest run`
Expected: 1 file, 11 tests PASS.

- [ ] **Step 2: Run backend tests**

Run: `mvn test -q -f /f/Study/AI/Vibe/Eify`
Expected: All backend tests pass.

### Task 5.2: Visual verification

- [ ] **Step 1: Start dev server**

Run: `./start.sh dev` (from repo root)

- [ ] **Step 2: Check all 12 pages per the spec checklist**

Go through each page listed in `docs/specs/2026-05-23-design-md-compliance-fix.md` section "视觉验证", checking:
- Colors look correct (no unexpected changes)
- Text hierarchy is right
- Interactive states work (hover/active/focus)

- [ ] **Step 3: Log any issues found**

If any page looks wrong, note the specific element and the incorrect token. Fix and re-verify.

### Task 5.3: Final self-review

- [ ] **Step 1: Run the compliance scan again**

```bash
grep -rn '#[0-9a-fA-F]\{3,8\}' eify-web/src --include="*.vue" | grep -v 'var(--' | grep -v '//' | grep -v 'stroke='
```

Expected: Only exempted values remain (SVG stops, node colors, Catppuccin theme, #0f0f1a).

- [ ] **Step 2: git diff review**

Run: `git diff main -- eify-web/src`
Skim every change, confirm no exemptions were touched, no wrong token mappings.

- [ ] **Step 3: Commit any final fixups**

---

## Task Summary

| Batch | Tasks | Files | Est. Time |
|:---|:---|:---|:---|
| 1 | Task 1.1 | LoginView.vue | 15 min |
| 2 | Tasks 2.1–2.7 | 7 shared components | 60 min |
| 3 | Tasks 3.1–3.8 | 8 business pages | 90 min |
| 4 | Tasks 4.1–4.3 | 3 workflow files | 25 min |
| 5 | Tasks 5.1–5.3 | Final verification | 30 min |
| **Total** | **20 tasks** | **20 files** | **~3.5 hrs est.** |
