# Protech API（Node 20 LTS 基线，09 册；开发机 Node 24 向后兼容）
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
COPY apps/api/package*.json apps/api/
COPY packages/shared-types/package*.json packages/shared-types/
RUN npm install --workspaces --include-workspace-root
COPY tsconfig.base.json ./
COPY scripts scripts
COPY prisma prisma
COPY packages/shared-types packages/shared-types
COPY apps/api apps/api
RUN cd packages/shared-types && npm run build
RUN cd apps/api && npx prisma generate --schema ../../prisma/schema.prisma && npx tsc -p tsconfig.build.json

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/node_modules node_modules
COPY --from=build /app/apps/api/dist apps/api/dist
COPY --from=build /app/prisma prisma
COPY --from=build /app/packages/shared-types/dist packages/shared-types/dist
COPY --from=build /app/packages/shared-types/package.json packages/shared-types/package.json
COPY --from=build /app/packages/shared-types/tsconfig.json packages/shared-types/tsconfig.json
WORKDIR /app/apps/api
EXPOSE 9090
CMD ["node", "dist/main.js"]
