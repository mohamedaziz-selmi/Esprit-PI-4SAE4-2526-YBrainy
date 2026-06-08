# Stage 1: build the Angular app
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund --legacy-peer-deps
COPY . .
RUN npx ng build --configuration production

# Stage 2: serve dist via nginx with /api,/Event,/uploads,/ws proxy to gateway
FROM nginx:1.27-alpine
COPY --from=builder /app/dist/angular-app/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
