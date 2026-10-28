import { Octokit } from "@octokit/rest";
import { readFile, readdir, stat } from "fs/promises";
import path from "path";

const gh = new Octokit({ auth: process.env.GITHUB_PAT });

async function pushFiles(dir: string, repoPath: string = "") {
  const files = await readdir(dir);
  for (const file of files) {
    if (file === ".git" || file === "node_modules" || file === ".gradle" || file === "dist") continue;

    const fullPath = path.join(dir, file);
    const relativePath = repoPath ? `${repoPath}/${file}` : file;
    const fileStat = await stat(fullPath);

    if (fileStat.isDirectory()) {
      await pushFiles(fullPath, relativePath);
    } else {
      const content = await readFile(fullPath, "base64");
      try {
        // Try to get existing file SHA
        const { data: existingFile } = await gh.rest.repos.getContent({
          owner: "aishervin",
          repo: "codein",
          path: relativePath,
        });
        
        await gh.rest.repos.createOrUpdateFileContents({
          owner: "aishervin",
          repo: "codein",
          path: relativePath,
          message: `chore: update ${relativePath}`,
          content: content,
          sha: (existingFile as any).sha,
        });
      } catch (e) {
        // Create if doesn't exist
        await gh.rest.repos.createOrUpdateFileContents({
          owner: "aishervin",
          repo: "codein",
          path: relativePath,
          message: `chore: create ${relativePath}`,
          content: content,
        });
      }
      console.log(`Pushed: ${relativePath}`);
    }
  }
}

pushFiles(".").catch(console.error);
