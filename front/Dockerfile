# Build
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install --legacy-peer-deps
COPY . .
RUN npm run build

# Serve
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html

# ✅ IMPORTANT: copie la conf proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
