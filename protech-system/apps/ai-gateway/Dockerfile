FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
COPY apps/ai-gateway/package*.json apps/ai-gateway/
RUN npm install --workspaces --include-workspace-root
COPY tsconfig.base.json ./
COPY apps/ai-gateway apps/ai-gateway
RUN cd apps/ai-gateway && npx tsc -p tsconfig.json

FROM node:20-alpine
WORKDIR /app/apps/ai-gateway
ENV NODE_ENV=production
COPY --from=build /app/node_modules /app/node_modules
COPY --from=build /app/apps/ai-gateway/dist dist
CMD ["node", "dist/main.js"]
