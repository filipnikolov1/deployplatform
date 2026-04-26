FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . ./
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup -g 1001 nodejs && adduser -u 1001 -G nodejs -S nextjs
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/public ./public
USER nextjs
EXPOSE 3000
ENV PORT=3000 HOSTNAME=0.0.0.0
ARG VECTOR_GIT_SHA=
ARG VECTOR_GIT_MESSAGE=
ARG VECTOR_GIT_BRANCH=
ENV VECTOR_GIT_SHA=${VECTOR_GIT_SHA}
ENV VECTOR_GIT_MESSAGE=${VECTOR_GIT_MESSAGE}
ENV VECTOR_GIT_BRANCH=${VECTOR_GIT_BRANCH}
CMD ["node", "server.js"]
