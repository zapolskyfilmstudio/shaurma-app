FROM node:22-alpine AS build
WORKDIR /app
ARG VITE_API_URL
ARG VITE_BEARER_TOKEN
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_BEARER_TOKEN=$VITE_BEARER_TOKEN
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
