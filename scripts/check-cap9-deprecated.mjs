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
 */

import fs from "node:fs";
import path from "node:path";

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

/** @type {{ label: string; pattern: RegExp }[]} */
const RULES = [
  { label: "PluginCall.hasOption / CAPPluginCall.hasOption", pattern: /\bhasOption\s*\(/ },
  { label: "Plugin.getConfigValue / CAPPlugin.getConfigValue", pattern: /\bgetConfigValue\s*\(/ },
  { label: "@NativePlugin", pattern: /@NativePlugin\b/ },
  { label: "Plugin.saveCall / Bridge.saveCall", pattern: /\bsaveCall\s*\(/ },
  { label: "Plugin.getSavedCall / Bridge.getSavedCall", pattern: /\bgetSavedCall\s*\(/ },
  { label: "Plugin.freeSavedCall", pattern: /\bfreeSavedCall\s*\(/ },
  { label: "Bridge.releaseCall / releaseCall(callbackId:)", pattern: /\breleaseCall\s*\(/ },
  {
    label: "pluginRequestPermission / pluginRequestPermissions",
    pattern: /\bpluginRequestPermissions?\s*\(/,
  },
  { label: "Plugin.hasDefinedPermissions", pattern: /\bhasDefinedPermissions\s*\(/ },
  { label: "CAPBridge compatibility API", pattern: /\bCAPBridge\./ },
  { label: "CAPNotifications enum", pattern: /\bCAPNotifications\b/ },
];

const SCAN_EXTS = [".java", ".kt", ".swift", ".m", ".mm", ".h"];

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
  const out = { dir: process.cwd() };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--dir" || a === "--pluginDir") {
      out.dir = path.resolve(argv[++i] || ".");
      continue;
    }
  }
  return out;
}

function scanFile(filePath, relPath) {
  const text = readText(filePath);
  if (!text) return [];

  const hits = [];
  const lines = text.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    for (const rule of RULES) {
      if (rule.pattern.test(line)) {
        hits.push({ relPath, line: i + 1, label: rule.label, snippet: line.trim() });
      }
    }
  }
  return hits;
}

const args = parseArgs(process.argv);
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
