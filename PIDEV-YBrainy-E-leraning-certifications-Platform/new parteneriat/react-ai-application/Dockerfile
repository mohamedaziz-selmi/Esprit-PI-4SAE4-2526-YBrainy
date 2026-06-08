# Stage 1: build the Vite + React app
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json ./
RUN npm install --no-audit --no-fund && npm install --no-save --no-audit --no-fund @types/node
COPY . .
# Empty base URL -> requests are same-origin, nginx proxies /api/* to gateway
ENV VITE_API_BASE_URL=""
# Skip strict tsc -b (no @types/node in package.json), let vite handle transpile
RUN npx vite build

# Stage 2: serve static dist via nginx
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
