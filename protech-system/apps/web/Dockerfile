FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
COPY apps/web/package*.json apps/web/
COPY packages/shared-types/package*.json packages/shared-types/
RUN npm install --workspaces --include-workspace-root
COPY tsconfig.base.json ./
COPY packages/shared-types packages/shared-types
COPY apps/web apps/web
RUN cd packages/shared-types && npm run build \
 && cd ../../apps/web && npx vite build

FROM nginx:alpine
COPY --from=build /app/apps/web/dist /usr/share/nginx/html
COPY deploy/nginx/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
