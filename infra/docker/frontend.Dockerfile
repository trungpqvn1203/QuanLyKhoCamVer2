# Build context: thư mục gốc dự án (cần cả frontend/ và infra/nginx/)
# --- Giai đoạn 1: build React thành file tĩnh ---
FROM node:22-alpine AS build
WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# --- Giai đoạn 2: Nginx phục vụ file tĩnh + chuyển /api sang backend ---
FROM nginx:1.27-alpine
COPY infra/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
