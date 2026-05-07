FROM node:20-alpine AS builder
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_API_USERNAME=developer
ARG VITE_API_PASSWORD=dev123
ARG VITE_DEV_SERVER_PORT=5173
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_API_USERNAME=$VITE_API_USERNAME
ENV VITE_API_PASSWORD=$VITE_API_PASSWORD
ENV VITE_DEV_SERVER_PORT=$VITE_DEV_SERVER_PORT
RUN npm run build

FROM nginx:1.27-alpine
COPY frontend/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
