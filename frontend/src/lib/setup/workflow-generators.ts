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
): string {
  const safe = appName || "my-app";
  return `name: Deploy ${safe}

on:
  push:
    branches: [${branch}]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Login to DockerHub
        uses: docker/login-action@v3
        with:
          username: \${{ secrets.DOCKERHUB_USERNAME }}
          password: \${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build and push
        run: |
          docker build -t \${{ secrets.DOCKERHUB_USERNAME }}/${safe}:\${{ github.sha }} .
          docker tag \${{ secrets.DOCKERHUB_USERNAME }}/${safe}:\${{ github.sha }} \\
            \${{ secrets.DOCKERHUB_USERNAME }}/${safe}:latest
          docker push \${{ secrets.DOCKERHUB_USERNAME }}/${safe}:\${{ github.sha }}
          docker push \${{ secrets.DOCKERHUB_USERNAME }}/${safe}:latest

      - name: Notify Launchpad
        env:
          LAUNCHPAD_URL: \${{ secrets.LAUNCHPAD_URL }}
          LAUNCHPAD_SECRET: \${{ secrets.LAUNCHPAD_SECRET }}
        run: |
          BODY=$(jq -n \\
            --arg image "\${{ secrets.DOCKERHUB_USERNAME }}/${safe}:\${{ github.sha }}" \\
            --arg app_name "${safe}" \\
            --arg repo_url "\${{ github.server_url }}/\${{ github.repository }}" \\
            --arg branch "\${{ github.ref_name }}" \\
            --arg commit_sha "\${{ github.sha }}" \\
            --arg commit_message "\${{ github.event.head_commit.message }}" \\
            --arg commit_author "\${{ github.event.head_commit.author.name }}" \\
            --argjson timestamp $(date +%s%3N) \\
            '{image: $image, app_name: $app_name, repo_url: $repo_url, branch: $branch, commit_sha: $commit_sha, commit_message: $commit_message, commit_author: $commit_author, timestamp: $timestamp}')
          SIGNATURE=$(echo -n "$BODY" | openssl dgst -sha256 -hmac "$LAUNCHPAD_SECRET" | sed 's/^.* //')
          curl -X POST "$LAUNCHPAD_URL/deploy-hook" \\
            -H "Content-Type: application/json" \\
            -H "X-Signature-256: sha256=$SIGNATURE" \\
            -d "$BODY"
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
