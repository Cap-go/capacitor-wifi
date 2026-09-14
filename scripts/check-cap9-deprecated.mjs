#!/usr/bin/env node
/**
 * Capacitor 9 deprecated native API guard.
 *
 * Fails when plugin Android/iOS sources still use APIs removed in Capacitor 9.
 * Does not scan Package.swift (Cordova SPM product must remain allowed).
 *
 * Usage:
 *   node scripts/check-cap9-deprecated.mjs
 *   node scripts/check-cap9-deprecated.mjs --dir path
 *   node scripts/check-cap9-deprecated.mjs --self-test
 */

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const SKIP_DIRS = new Set([
  "node_modules",
  "dist",
  "build",
  ".build",
  ".gradle",
  "Pods",
  "DerivedData",
  ".swiftpm",
  ".git",
]);

const PLUGIN_CALL_RECEIVERS = new Set([
  "call",
  "pluginCall",
  "savedCall",
  "capPluginCall",
  "self",
]);

const SCAN_EXTS = [".java", ".kt", ".swift", ".m", ".mm", ".h"];

/** @type {{ label: string; pattern: RegExp; pluginCallReceiver?: boolean }[]} */
const RULES = [
  {
    label: "PluginCall.hasOption / CAPPluginCall.hasOption",
    pattern: /([\w$]+)\s*\.\s*hasOption\s*\(/g,
    pluginCallReceiver: true,
  },
  { label: "Plugin.getConfigValue / CAPPlugin.getConfigValue", pattern: /\bgetConfigValue\s*\(/g },
  { label: "@NativePlugin", pattern: /@NativePlugin\b/g },
  { label: "Plugin.saveCall / Bridge.saveCall", pattern: /\bsaveCall\s*\(/g },
  { label: "Plugin.getSavedCall / Bridge.getSavedCall", pattern: /\bgetSavedCall\s*\(/g },
  { label: "Plugin.freeSavedCall", pattern: /\bfreeSavedCall\s*\(/g },
  { label: "Bridge.releaseCall / releaseCall(callbackId:)", pattern: /\breleaseCall\s*\(/g },
  {
    label: "pluginRequestPermission / pluginRequestPermissions",
    pattern: /\bpluginRequestPermissions?\s*\(/g,
  },
  { label: "Plugin.hasDefinedPermissions", pattern: /\bhasDefinedPermissions\s*\(/g },
  { label: "CAPBridge compatibility API", pattern: /\bCAPBridge\./g },
  { label: "CAPNotifications enum", pattern: /\bCAPNotifications\b/g },
];

const DECLARATION_SKIP = [
  /\bfunc\s+hasOption\s*\(/g,
  /\bfun\s+hasOption\s*\(/g,
  /\bboolean\s+hasOption\s*\(/g,
  /\bbool\s+hasOption\s*\(/g,
];

function readText(p) {
  try {
    return fs.readFileSync(p, "utf8");
  } catch {
    return "";
  }
}

function exists(p) {
  try {
    fs.accessSync(p);
    return true;
  } catch {
    return false;
  }
}

function lineAt(source, index) {
  let line = 1;
  const end = Math.min(index, source.length);
  for (let i = 0; i < end; i++) {
    if (source[i] === "\n") line++;
  }
  return line;
}

function snippetAt(source, index) {
  const start = Math.max(0, source.lastIndexOf("\n", index) + 1);
  let end = source.indexOf("\n", index);
  if (end === -1) end = source.length;
  return source.slice(start, end).trim();
}

/**
 * Blank comments and string literals; preserve length and newlines for line mapping.
 * @param {string} source
 */
function blankCommentsAndStrings(source) {
  const out = [...source];
  let i = 0;
  while (i < out.length) {
    const ch = out[i];
    const next = out[i + 1];

    if (ch === "/" && next === "/") {
      i += 2;
      while (i < out.length && out[i] !== "\n") {
        out[i] = " ";
        i++;
      }
      continue;
    }

    if (ch === "/" && next === "*") {
      i += 2;
      while (i < out.length && !(out[i] === "*" && out[i + 1] === "/")) {
        out[i] = " ";
        i++;
      }
      if (i < out.length) {
        out[i] = " ";
        out[i + 1] = " ";
        i += 2;
      }
      continue;
    }

    if (ch === '"') {
      const triple = out[i + 1] === '"' && out[i + 2] === '"';
      out[i] = " ";
      i++;
      if (triple) {
        out[i] = " ";
        out[i + 1] = " ";
        i += 2;
        while (i < out.length && !(out[i] === '"' && out[i + 1] === '"' && out[i + 2] === '"')) {
          if (out[i] === "\\" && i + 1 < out.length) {
            out[i] = " ";
            i++;
          }
          out[i] = " ";
          i++;
        }
        if (i + 2 < out.length) {
          out[i] = " ";
          out[i + 1] = " ";
          out[i + 2] = " ";
          i += 3;
        }
        continue;
      }
      while (i < out.length) {
        if (out[i] === "\\" && i + 1 < out.length) {
          out[i] = " ";
          i++;
        }
        if (out[i] === '"') {
          out[i] = " ";
          i++;
          break;
        }
        out[i] = " ";
        i++;
      }
      continue;
    }

    if (ch === "'") {
      out[i] = " ";
      i++;
      while (i < out.length) {
        if (out[i] === "\\" && i + 1 < out.length) {
          out[i] = " ";
          i++;
        }
        if (out[i] === "'") {
          out[i] = " ";
          i++;
          break;
        }
        out[i] = " ";
        i++;
      }
      continue;
    }

    i++;
  }
  return out.join("");
}

function buildSearchSurface(codeOnly) {
  const flat = [];
  /** @type {number[]} */
  const flatToCode = [];
  let i = 0;
  while (i < codeOnly.length) {
    const ch = codeOnly[i];
    if (/\s/.test(ch)) {
      if (flat.length === 0 || flat[flat.length - 1] !== " ") {
        flat.push(" ");
        flatToCode.push(i);
      }
      i++;
      continue;
    }
    flat.push(ch);
    flatToCode.push(i);
    i++;
  }
  return { surface: flat.join(""), flatToCode };
}

function isDeclarationAt(surface, matchIndex) {
  const windowStart = Math.max(0, matchIndex - 40);
  const prefix = surface.slice(windowStart, matchIndex);
  for (const re of DECLARATION_SKIP) {
    re.lastIndex = 0;
    if (re.test(prefix + surface.slice(matchIndex, matchIndex + 20))) {
      return true;
    }
  }
  return false;
}

function scanFileContent(source, relPath) {
  if (!source) return [];

  const codeOnly = blankCommentsAndStrings(source);
  const { surface, flatToCode } = buildSearchSurface(codeOnly);
  const hits = [];

  for (const rule of RULES) {
    const re = new RegExp(rule.pattern.source, rule.pattern.flags);
    let m;
    while ((m = re.exec(surface))) {
      const flatIndex = m.index;
      const codeIndex = flatToCode[flatIndex] ?? 0;
      const line = lineAt(source, codeIndex);
      const snippet = snippetAt(source, codeIndex);

      if (rule.pluginCallReceiver) {
        const receiver = m[1] || "";
        if (!PLUGIN_CALL_RECEIVERS.has(receiver)) {
          continue;
        }
      }

      if (rule.label.includes("hasOption") && isDeclarationAt(surface, flatIndex)) {
        continue;
      }

      hits.push({ relPath, line, label: rule.label, snippet });
    }
  }

  return hits;
}

function scanFile(filePath, relPath) {
  return scanFileContent(readText(filePath), relPath);
}

function walkFiles(rootDir, exts) {
  const out = [];
  const stack = [rootDir];
  while (stack.length) {
    const dir = stack.pop();
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const e of entries) {
      if (e.isDirectory()) {
        if (SKIP_DIRS.has(e.name)) continue;
        stack.push(path.join(dir, e.name));
        continue;
      }
      if (!e.isFile()) continue;
      for (const ext of exts) {
        if (e.name.endsWith(ext)) {
          out.push(path.join(dir, e.name));
          break;
        }
      }
    }
  }
  out.sort();
  return out;
}

function parseArgs(argv) {
  const out = { dir: process.cwd(), selfTest: false };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--dir" || a === "--pluginDir") {
      out.dir = path.resolve(argv[++i] || ".");
      continue;
    }
    if (a === "--self-test") {
      out.selfTest = true;
      continue;
    }
  }
  return out;
}

function runSelfTest() {
  const here = path.dirname(fileURLToPath(import.meta.url));
  const root = path.join(here, "fixtures", "cap9-deprecated");
  const passDir = path.join(root, "should-pass");
  const failDir = path.join(root, "should-fail");
  let failed = false;

  for (const file of walkFiles(passDir, SCAN_EXTS)) {
    const rel = path.relative(root, file);
    const hits = scanFileContent(readText(file), rel);
    if (hits.length) {
      failed = true;
      console.error(`[cap9-deprecated] self-test FAIL: expected pass for ${rel}`);
      for (const h of hits) console.error(`  - ${h.line} [${h.label}] ${h.snippet}`);
    }
  }

  for (const file of walkFiles(failDir, SCAN_EXTS)) {
    const rel = path.relative(root, file);
    const hits = scanFileContent(readText(file), rel);
    if (!hits.length) {
      failed = true;
      console.error(`[cap9-deprecated] self-test FAIL: expected violations for ${rel}`);
    }
  }

  if (failed) process.exit(1);
  process.exit(0);
}

const args = parseArgs(process.argv);
if (args.selfTest) {
  runSelfTest();
}

const pluginDir = args.dir;
const pkgPath = path.join(pluginDir, "package.json");

if (!exists(pkgPath)) {
  console.error(`[cap9-deprecated] ERROR: missing package.json in ${pluginDir}`);
  process.exit(2);
}

let pkg;
try {
  pkg = JSON.parse(readText(pkgPath));
} catch (e) {
  console.error(`[cap9-deprecated] ERROR: invalid package.json (${pkgPath}): ${e?.message || e}`);
  process.exit(2);
}

const cap = typeof pkg.capacitor === "object" && pkg.capacitor ? pkg.capacitor : {};
const supportsAndroid = typeof cap.android === "object" && cap.android;
const supportsIos = typeof cap.ios === "object" && cap.ios;

if (!supportsAndroid && !supportsIos) {
  process.exit(0);
}

const scanRoots = [];
if (supportsAndroid) {
  const androidDir = path.join(pluginDir, "android");
  if (exists(androidDir)) scanRoots.push(androidDir);
}
if (supportsIos) {
  for (const sub of ["Sources", "Tests"]) {
    const p = path.join(pluginDir, "ios", sub);
    if (exists(p)) scanRoots.push(p);
  }
}

const allHits = [];
for (const root of scanRoots) {
  for (const file of walkFiles(root, SCAN_EXTS)) {
    const rel = path.relative(pluginDir, file);
    allHits.push(...scanFile(file, rel));
  }
}

if (allHits.length) {
  const relDir = path.relative(process.cwd(), pluginDir) || ".";
  console.error(`[cap9-deprecated] FAIL in ${relDir}`);
  console.error("Remove Capacitor 9 deprecated native APIs from Android/iOS sources.");
  for (const h of allHits) {
    console.error(`- ${h.relPath}:${h.line} [${h.label}] ${h.snippet}`);
  }
  process.exit(1);
}

process.exit(0);
