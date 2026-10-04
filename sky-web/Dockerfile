FROM node:24-alpine AS build
WORKDIR /source
COPY sky-web/package*.json ./
RUN npm ci --no-audit --no-fund
COPY sky-web/ ./
RUN npm run build

FROM nginx:stable-alpine
COPY docker/frontend.conf /etc/nginx/conf.d/default.conf
COPY --from=build /source/dist/ /usr/share/nginx/html/
