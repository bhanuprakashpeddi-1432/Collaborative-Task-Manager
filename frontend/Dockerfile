# ──────────────────────────────────────────────────────────────────────────────
# Stage 1: Build — Install dependencies and produce optimized static assets
# ──────────────────────────────────────────────────────────────────────────────
FROM node:22-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts

COPY . .
RUN npm run build

# ──────────────────────────────────────────────────────────────────────────────
# Stage 2: Serve — NGINX serves static files and reverse-proxies the API
# ──────────────────────────────────────────────────────────────────────────────
FROM nginx:1.27-alpine

# Remove default NGINX config
RUN rm /etc/nginx/conf.d/default.conf

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
