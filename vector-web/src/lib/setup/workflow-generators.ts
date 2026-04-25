export type TechStack = "nodejs" | "nextjs" | "python" | "docker";

export interface TechStackOption {
  value: TechStack;
  label: string;
}

export const techStackOptions: TechStackOption[] = [
  { value: "nodejs", label: "Node.js" },
  { value: "nextjs", label: "Next.js" },
  { value: "python", label: "Python" },
  { value: "docker", label: "Docker (Custom)" },
];

export function generateWorkflow(
  appName: string,
  branch: string,
  _stack: TechStack,
  port: string = "3000",
): string {
  const safe = appName || "my-app";
  return `name: Deploy ${safe}

on:
  push:
    branches: [${branch}]

jobs:
  build-push:
    runs-on: ubuntu-latest
    outputs:
      image_sha_tag: \${{ steps.meta.outputs.image_sha_tag }}
    steps:
      - uses: actions/checkout@v4

      - id: meta
        run: |
          SHORT_SHA="\${GITHUB_SHA::7}"
          echo "image_sha_tag=\${{ secrets.DOCKERHUB_USERNAME }}/${safe}:git-\${SHORT_SHA}" >> $GITHUB_OUTPUT

      - name: Login to DockerHub
        uses: docker/login-action@v3
        with:
          username: \${{ secrets.DOCKERHUB_USERNAME }}
          password: \${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build and push
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: |
            \${{ secrets.DOCKERHUB_USERNAME }}/${safe}:latest
            \${{ steps.meta.outputs.image_sha_tag }}

  notify-vector:
    runs-on: ubuntu-latest
    needs: build-push
    steps:
      - name: Post to deploy-hook
        env:
          HOOK_URL: \${{ secrets.VECTOR_DEPLOY_HOOK_URL }}
          HOOK_KEY: \${{ secrets.VECTOR_DEPLOY_KEY }}
          IMAGE: \${{ needs.build-push.outputs.image_sha_tag }}
          SHA: \${{ github.sha }}
          BRANCH: \${{ github.ref_name }}
          MSG: \${{ toJSON(github.event.head_commit.message) }}
          AUTHOR: \${{ github.event.head_commit.author.name }}
          REPO: \${{ github.server_url }}/\${{ github.repository }}
        run: |
          PAYLOAD=$(jq -n \\
            --arg app "${safe}" \\
            --arg image "$IMAGE" \\
            --arg repo "$REPO" \\
            --arg branch "$BRANCH" \\
            --arg sha "$SHA" \\
            --argjson msg "$MSG" \\
            --arg author "$AUTHOR" \\
            --arg port "${port}" \\
            --arg ts "$(date +%s)000" \\
            '{app_name:$app, image:$image, repo_url:$repo, branch:$branch, commit_sha:$sha, commit_message:$msg, commit_author:$author, port:($port|tonumber), timestamp:($ts|tonumber)}')
          SIG="sha256=$(printf '%s' "$PAYLOAD" | openssl dgst -sha256 -hmac "$HOOK_KEY" -hex | awk '{print $2}')"
          curl -fsS -X POST "$HOOK_URL" \\
            -H "X-Signature-256: $SIG" \\
            -H "Content-Type: application/json" \\
            -d "$PAYLOAD"
`;
}

export function generateDockerfile(stack: TechStack): string {
  switch (stack) {
    case "nodejs":
      return `FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
EXPOSE 3000
CMD ["node", "index.js"]
`;
    case "nextjs":
      return `FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/public ./public
COPY --from=builder /app/package*.json ./
COPY --from=builder /app/node_modules ./node_modules
EXPOSE 3000
CMD ["npm", "start"]
`;
    case "python":
      return `FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8000
CMD ["python", "main.py"]
`;
    case "docker":
    default:
      return `# Custom Dockerfile — write your own.
FROM alpine:latest
# ...
EXPOSE 8080
CMD ["sh"]
`;
  }
}
