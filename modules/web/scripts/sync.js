import { URL } from "node:url";
import fs from "node:fs/promises";
import process from "node:process";

if (!process.env.GITHUB_ENV) {
  console.log("非Github WorkFlows环境，取消文件复制");
  process.exit();
}
const LEGADO_ASSETS_WEB_VUE_DIR = new URL(
  "../../../app/src/main/assets/web/vue",
  import.meta.url,
);
const VUE_DIST_DIR = new URL("../dist", import.meta.url);

console.log("> delete", LEGADO_ASSETS_WEB_VUE_DIR.pathname);
await fs.rm(LEGADO_ASSETS_WEB_VUE_DIR, {
  force: true,
  recursive: true,
});

console.log("> mkdir", LEGADO_ASSETS_WEB_VUE_DIR.pathname);
await fs.mkdir(LEGADO_ASSETS_WEB_VUE_DIR, { recursive: true });

console.log("> cp dist files");
await fs.cp(VUE_DIST_DIR, LEGADO_ASSETS_WEB_VUE_DIR, { recursive: true });
console.log("> cp success");
