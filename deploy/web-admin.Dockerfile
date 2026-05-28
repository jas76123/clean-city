# deploy/web-admin.Dockerfile
# Двухстейджевый билд: node собирает SPA, nginx-alpine раздаёт статику.

FROM node:20-alpine AS build
WORKDIR /app
COPY web-admin/package.json web-admin/package-lock.json* ./
RUN npm ci
COPY web-admin/ ./
# API_BASE для прода захардкожен через VITE-переменную при билде.
ENV VITE_API_BASE_URL=https://api.clean--city.ru
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
# SPA-роутинг: всё, что не статика, → index.html.
RUN printf 'server {\n  listen 80;\n  root /usr/share/nginx/html;\n  index index.html;\n  location / {\n    try_files $uri $uri/ /index.html;\n  }\n}\n' > /etc/nginx/conf.d/default.conf
