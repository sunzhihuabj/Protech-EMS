FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
COPY apps/worker/package*.json apps/worker/
RUN npm install --workspaces --include-workspace-root
COPY tsconfig.base.json ./
COPY apps/worker apps/worker
RUN cd apps/worker && npx tsc -p tsconfig.json

FROM node:20-alpine
WORKDIR /app/apps/worker
ENV NODE_ENV=production
COPY --from=build /app/node_modules /app/node_modules
COPY --from=build /app/apps/worker/dist dist
CMD ["node", "dist/main.js"]
